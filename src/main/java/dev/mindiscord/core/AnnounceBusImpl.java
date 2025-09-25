package dev.mindiscord.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.mindiscord.api.AllowedMentions;
import dev.mindiscord.api.AnnounceBus;
import dev.mindiscord.api.Embed;
import dev.mindiscord.api.SendResult;
import dev.mindiscord.api.WebhookMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AnnounceBusImpl implements AnnounceBus, AutoCloseable {
  private static final Logger LOGGER = LogManager.getLogger("MinDiscord/AnnounceBus");
  private static final ObjectMapper JSON =
      new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private final Router router;
  private final DispatchQueue queue;
  private final WebhookClient transport;
  private final RateLimiterRegistry rateLimiter;
  private final StatsStore statsStore;
  private final MinCoreBridge bridge;
  private final TimeSource timeSource;
  private final Sleeper sleeper;
  private final Diagnostics diagnostics = new Diagnostics();
  private final SendWorker worker;
  private final Thread workerThread;
  private final AtomicBoolean closed = new AtomicBoolean();

  private volatile Config config;

  public AnnounceBusImpl(
      Router router,
      DispatchQueue queue,
      WebhookClient transport,
      RateLimiterRegistry rateLimiter,
      StatsStore statsStore,
      MinCoreBridge bridge,
      TimeSource timeSource,
      Sleeper sleeper,
      Config initialConfig) {
    this.router = router;
    this.queue = queue;
    this.transport = transport;
    this.rateLimiter = rateLimiter;
    this.statsStore = statsStore;
    this.bridge = bridge;
    this.timeSource = timeSource;
    this.sleeper = sleeper;
    this.worker = new SendWorker();
    applyConfig(initialConfig);
    this.workerThread = new Thread(worker, "MinDiscord-Worker");
    this.workerThread.setDaemon(true);
    this.workerThread.start();
  }

  public void applyConfig(Config config) {
    Objects.requireNonNull(config, "config");
    this.config = config;
    router.update(config);
    queue.configure(config.queue().maxSize(), config.queue().onOverflow());
    rateLimiter.configure(config.ratelimit().perRouteBurst(), config.ratelimit().perRouteRefillPerSec());
    worker.updateRetry(config.retry());
  }

  @Override
  public CompletableFuture<SendResult> send(String route, String content) {
    WebhookMessage msg = new WebhookMessage();
    msg.content = content;
    return send(route, msg);
  }

  @Override
  public CompletableFuture<SendResult> send(String route, WebhookMessage message) {
    Objects.requireNonNull(message, "message");
    if (closed.get()) {
      return CompletableFuture.completedFuture(
          new SendResult(false, "GIVE_UP", "MinDiscord shutting down", UUID.randomUUID().toString()));
    }
    Config cfg = this.config;
    WebhookMessage normalized = normalize(message, cfg.defaults());
    String validationError = validate(normalized);
    UUID requestId = UUID.randomUUID();
    if (validationError != null) {
      return CompletableFuture.completedFuture(
          new SendResult(false, "BAD_PAYLOAD", validationError, requestId.toString()));
    }
    Router.RouteResolution resolution = router.resolve(route);
    if (!resolution.ok()) {
      String code = resolution.status() == Router.Status.ENV_MISSING ? "BAD_ROUTE" : "BAD_ROUTE";
      String messageText =
          resolution.status() == Router.Status.ENV_MISSING
              ? "Route " + resolution.resolvedRoute() + " missing env:" + resolution.envVariable()
              : "Unknown route: " + resolution.requestedRoute();
      return CompletableFuture.completedFuture(
          new SendResult(false, code, messageText, requestId.toString()));
    }
    String json;
    int payloadBytes;
    int embedCount = normalized.embeds != null ? normalized.embeds.size() : 0;
    try {
      json = buildPayload(normalized);
      payloadBytes = json.getBytes(StandardCharsets.UTF_8).length;
    } catch (Exception e) {
      return CompletableFuture.completedFuture(
          new SendResult(false, "BAD_PAYLOAD", "Failed to encode payload", requestId.toString()));
    }
    CompletableFuture<SendResult> future = new CompletableFuture<>();
    PendingRequest pending =
        new PendingRequest(
            requestId,
            resolution,
            json,
            payloadBytes,
            embedCount,
            future,
            timeSource.now());
    DispatchQueue.QueuePushResult push = queue.enqueue(pending);
    if (!push.isEnqueued()) {
      future.complete(new SendResult(false, "QUEUE_FULL", "Queue full", requestId.toString()));
      return future;
    }
    PendingRequest dropped = push.dropped();
    if (dropped != null) {
      dropped.completeQueueFull();
    }
    return future;
  }

  @Override
  public CompletableFuture<SendResult> send(String route, Embed embed) {
    Objects.requireNonNull(embed, "embed");
    WebhookMessage msg = new WebhookMessage();
    msg.embeds = List.of(embed);
    return send(route, msg);
  }

  public DiagnosticsSnapshot diagnostics() {
    Config cfg = this.config;
    return new DiagnosticsSnapshot(queue.size(), cfg.queue().maxSize(), Map.copyOf(diagnostics.snapshot()));
  }

  public List<Router.RouteInfo> routes() { return new ArrayList<>(router.snapshot()); }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      worker.stop();
      queue.close();
      workerThread.interrupt();
      try {
        workerThread.join(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static WebhookMessage normalize(WebhookMessage original, Config.Defaults defaults) {
    WebhookMessage copy = copyOf(original);
    if (copy.username == null || copy.username.isBlank()) {
      copy.username = defaults.username();
    }
    if ((copy.avatarUrl == null || copy.avatarUrl.isBlank()) && !defaults.avatarUrl().isBlank()) {
      copy.avatarUrl = defaults.avatarUrl();
    }
    return copy;
  }

  private static WebhookMessage copyOf(WebhookMessage original) {
    WebhookMessage copy = new WebhookMessage();
    copy.username = original.username;
    copy.avatarUrl = original.avatarUrl;
    copy.content = original.content;
    if (original.embeds != null) {
      copy.embeds = new ArrayList<>(original.embeds);
    }
    copy.allowedMentions = original.allowedMentions;
    return copy;
  }

  private static String validate(WebhookMessage message) {
    if ((message.content == null || message.content.isBlank())
        && (message.embeds == null || message.embeds.isEmpty())) {
      return "Content or embeds required";
    }
    if (message.content != null && message.content.length() > 2000) {
      return "Content exceeds 2000 characters";
    }
    if (message.username != null && message.username.length() > 80) {
      return "Username exceeds 80 characters";
    }
    if (message.embeds != null) {
      if (message.embeds.size() > 10) {
        return "Too many embeds (max 10)";
      }
      for (Embed embed : message.embeds) {
        String err = validateEmbed(embed);
        if (err != null) {
          return err;
        }
      }
    }
    return null;
  }

  private static String validateEmbed(Embed embed) {
    if (embed == null) {
      return "Embed cannot be null";
    }
    if (embed.title != null && embed.title.length() > 256) {
      return "Embed title too long";
    }
    if (embed.description != null && embed.description.length() > 4096) {
      return "Embed description too long";
    }
    if (embed.footer != null && embed.footer.text != null && embed.footer.text.length() > 2048) {
      return "Embed footer text too long";
    }
    if (embed.author != null && embed.author.name != null && embed.author.name.length() > 256) {
      return "Embed author name too long";
    }
    if (embed.fields != null) {
      if (embed.fields.size() > 25) {
        return "Embed has too many fields";
      }
      for (Embed.Field field : embed.fields) {
        if (field.name != null && field.name.length() > 256) {
          return "Embed field name too long";
        }
        if (field.value != null && field.value.length() > 1024) {
          return "Embed field value too long";
        }
      }
    }
    return null;
  }

  private static String buildPayload(WebhookMessage message) throws Exception {
    ObjectNode root = JSON.createObjectNode();
    if (message.username != null && !message.username.isBlank()) {
      root.put("username", message.username);
    }
    if (message.avatarUrl != null && !message.avatarUrl.isBlank()) {
      root.put("avatar_url", message.avatarUrl);
    }
    if (message.content != null) {
      root.put("content", message.content);
    }
    if (message.embeds != null && !message.embeds.isEmpty()) {
      ArrayNode arr = root.putArray("embeds");
      for (Embed embed : message.embeds) {
        arr.add(serializeEmbed(embed));
      }
    }
    if (message.allowedMentions != null) {
      ObjectNode allowed = serializeAllowedMentions(message.allowedMentions);
      if (allowed != null) {
        root.set("allowed_mentions", allowed);
      }
    }
    return JSON.writeValueAsString(root);
  }

  private static ObjectNode serializeEmbed(Embed embed) {
    ObjectNode node = JSON.createObjectNode();
    if (embed.title != null) node.put("title", embed.title);
    if (embed.description != null) node.put("description", embed.description);
    if (embed.url != null) node.put("url", embed.url);
    if (embed.color != null) node.put("color", embed.color);
    if (embed.author != null) {
      ObjectNode author = JSON.createObjectNode();
      if (embed.author.name != null) author.put("name", embed.author.name);
      if (embed.author.url != null) author.put("url", embed.author.url);
      if (embed.author.iconUrl != null) author.put("icon_url", embed.author.iconUrl);
      node.set("author", author);
    }
    if (embed.footer != null) {
      ObjectNode footer = JSON.createObjectNode();
      if (embed.footer.text != null) footer.put("text", embed.footer.text);
      if (embed.footer.iconUrl != null) footer.put("icon_url", embed.footer.iconUrl);
      node.set("footer", footer);
    }
    if (embed.thumbnail != null && embed.thumbnail.url != null) {
      ObjectNode thumb = JSON.createObjectNode();
      thumb.put("url", embed.thumbnail.url);
      node.set("thumbnail", thumb);
    }
    if (embed.image != null && embed.image.url != null) {
      ObjectNode img = JSON.createObjectNode();
      img.put("url", embed.image.url);
      node.set("image", img);
    }
    if (embed.fields != null && !embed.fields.isEmpty()) {
      ArrayNode fields = node.putArray("fields");
      for (Embed.Field field : embed.fields) {
        ObjectNode fn = JSON.createObjectNode();
        if (field.name != null) fn.put("name", field.name);
        if (field.value != null) fn.put("value", field.value);
        fn.put("inline", field.inline);
        fields.add(fn);
      }
    }
    return node;
  }

  private static ObjectNode serializeAllowedMentions(AllowedMentions mentions) {
    ObjectNode node = JSON.createObjectNode();
    ArrayNode parse = JSON.createArrayNode();
    if (mentions.parseEveryone) parse.add("everyone");
    if (mentions.parseRoles) parse.add("roles");
    if (mentions.parseUsers) parse.add("users");
    if (!parse.isEmpty()) {
      node.set("parse", parse);
    }
    if (mentions.roles != null && !mentions.roles.isEmpty()) {
      ArrayNode roles = node.putArray("roles");
      mentions.roles.forEach(roles::add);
    }
    if (mentions.users != null && !mentions.users.isEmpty()) {
      ArrayNode users = node.putArray("users");
      mentions.users.forEach(users::add);
    }
    return node.isEmpty() ? null : node;
  }

  public record DiagnosticsSnapshot(
      int queueSize, int queueCapacity, Map<String, Diagnostics.RouteSnapshot> routes) {}

  private final class SendWorker implements Runnable {
    private volatile boolean running = true;
    private volatile Config.Retry retry = Config.Retry.DEFAULTS;

    void updateRetry(Config.Retry retry) { this.retry = retry; }

    void stop() { running = false; }

    @Override
    public void run() {
      while (running && !Thread.currentThread().isInterrupted()) {
        PendingRequest request;
        try {
          request = queue.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
        if (request == null) {
          continue;
        }
        process(request);
      }
    }

    private void process(PendingRequest request) {
      try {
        String rateKey = request.resolvedRoute != null ? request.resolvedRoute : "default";
        Duration wait = rateLimiter.acquire(rateKey, timeSource);
        if (!wait.isZero()) {
          sleeper.sleep(wait);
        }
        DeliveryResult result = deliver(request, retry);
        request.future.complete(result.result());
        if (result.success()) {
          diagnostics.recordSuccess(request.resolvedRoute, timeSource.now(), result.result().message());
        } else {
          diagnostics.recordFailure(
              request.resolvedRoute != null ? request.resolvedRoute : "unknown",
              timeSource.now(),
              result.result().code(),
              result.result().message());
        }
        if (request.resolvedRoute != null) {
          statsStore.record(request.resolvedRoute, result.success());
        }
        String requested = request.requestedRoute;
        String extraRequested = Objects.equals(request.resolvedRoute, requested) ? null : requested;
        bridge.logLedger(
            request.resolvedRoute != null ? request.resolvedRoute : "unknown",
            result.success(),
            result.result().code(),
            request.requestId.toString(),
            request.resolvedRoute,
            extraRequested,
            request.payloadBytes,
            request.embedCount);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        request.future.complete(
            new SendResult(false, "GIVE_UP", "Interrupted", request.requestId.toString()));
      } catch (Exception e) {
        LOGGER.error("Worker failed: {}", e.toString());
        request.future.complete(
            new SendResult(false, "GIVE_UP", "Worker failure", request.requestId.toString()));
      }
    }

    private DeliveryResult deliver(PendingRequest request, Config.Retry retry) throws InterruptedException {
      Duration delay = retry.baseDelay();
      int attempts = retry.maxAttempts();
      String lastCode = null;
      String lastMessage = null;
      for (int attempt = 1; attempt <= attempts; attempt++) {
        WebhookTransport.TransportResponse response = transport.postJson(request.url, request.jsonPayload);
        if (response.success()) {
          String code = request.fallback ? "BAD_ROUTE_FALLBACK" : "OK";
          String msg =
              request.fallback
                  ? "Sent via " + request.resolvedRoute + " (fallback)"
                  : "Sent";
          return DeliveryResult.success(new SendResult(true, code, msg, request.requestId.toString()));
        }
        int status = response.statusCode();
        if (status == 429) {
          lastCode = "DISCORD_429";
          lastMessage = "Discord returned 429";
          if (attempt >= attempts) {
            break;
          }
          Duration wait = response.retryAfter();
          if (wait == null || wait.isZero() || wait.isNegative()) {
            wait = applyJitter(delay, retry);
          }
          sleeper.sleep(wait);
          delay = nextDelay(delay, retry);
          continue;
        }
        if (status >= 500 || status == -1) {
          lastCode = status >= 500 ? "DISCORD_5XX" : "NETWORK_IO";
          lastMessage =
              status >= 500
                  ? "Discord returned " + status
                  : (response.error() != null ? response.error().getMessage() : "Network error");
          if (attempt >= attempts) {
            break;
          }
          sleeper.sleep(applyJitter(delay, retry));
          delay = nextDelay(delay, retry);
          continue;
        }
        if (status >= 400) {
          lastCode = "BAD_PAYLOAD";
          lastMessage = "Discord rejected payload (HTTP " + status + ")";
          return DeliveryResult.failure(
              new SendResult(false, lastCode, lastMessage, request.requestId.toString()));
        }
        lastCode = "NETWORK_IO";
        lastMessage = "Unexpected transport failure";
      }
      String message =
          String.format(
              "Retries exhausted after %d attempts (last=%s)",
              retry.maxAttempts(),
              lastCode != null ? lastCode : "unknown");
      return DeliveryResult.failure(new SendResult(false, "GIVE_UP", message, request.requestId.toString()));
    }

    private Duration nextDelay(Duration current, Config.Retry retry) {
      long currentMs = Math.max(1, current.toMillis());
      long doubled = Math.min(retry.maxDelay().toMillis(), currentMs * 2);
      return Duration.ofMillis(doubled);
    }

    private Duration applyJitter(Duration delay, Config.Retry retry) {
      if (!retry.jitter()) {
        return delay;
      }
      double factor = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
      long millis = Math.max(1, Math.round(delay.toMillis() * factor));
      long clamped = Math.min(retry.maxDelay().toMillis(), millis);
      return Duration.ofMillis(clamped);
    }
  }

  private record DeliveryResult(SendResult result, boolean success) {
    static DeliveryResult success(SendResult result) { return new DeliveryResult(result, true); }

    static DeliveryResult failure(SendResult result) { return new DeliveryResult(result, false); }
  }
}
