package dev.mindiscord.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable runtime configuration for MinDiscord. */
public final class Config {
  private final String mode;
  private final Map<String, RouteDefinition> routes;
  private final Defaults defaults;
  private final Queue queue;
  private final Retry retry;
  private final RateLimit ratelimit;
  private final Log log;

  private Config(
      String mode,
      Map<String, RouteDefinition> routes,
      Defaults defaults,
      Queue queue,
      Retry retry,
      RateLimit ratelimit,
      Log log) {
    this.mode = mode;
    this.routes = routes;
    this.defaults = defaults;
    this.queue = queue;
    this.retry = retry;
    this.ratelimit = ratelimit;
    this.log = log;
  }

  public String mode() { return mode; }

  public Map<String, RouteDefinition> routes() { return routes; }

  public Defaults defaults() { return defaults; }

  public Queue queue() { return queue; }

  public Retry retry() { return retry; }

  public RateLimit ratelimit() { return ratelimit; }

  public Log log() { return log; }

  public static Builder builder() { return new Builder(); }

  public static Config defaultConfig() { return builder().build(); }

  public static final class Builder {
    private String mode = "webhook";
    private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();
    private Defaults defaults = Defaults.DEFAULTS;
    private Queue queue = Queue.DEFAULTS;
    private Retry retry = Retry.DEFAULTS;
    private RateLimit ratelimit = RateLimit.DEFAULTS;
    private Log log = Log.DEFAULTS;

    public Builder mode(String mode) {
      this.mode = Objects.requireNonNull(mode, "mode");
      return this;
    }

    public Builder putRoute(String name, String target) {
      routes.put(name, RouteDefinition.of(name, target));
      return this;
    }

    public Builder routes(Map<String, String> map) {
      routes.clear();
      map.forEach(this::putRoute);
      return this;
    }

    public Builder defaults(Defaults defaults) {
      this.defaults = Objects.requireNonNull(defaults, "defaults");
      return this;
    }

    public Builder queue(Queue queue) {
      this.queue = Objects.requireNonNull(queue, "queue");
      return this;
    }

    public Builder retry(Retry retry) {
      this.retry = Objects.requireNonNull(retry, "retry");
      return this;
    }

    public Builder ratelimit(RateLimit ratelimit) {
      this.ratelimit = Objects.requireNonNull(ratelimit, "ratelimit");
      return this;
    }

    public Builder log(Log log) {
      this.log = Objects.requireNonNull(log, "log");
      return this;
    }

    public Config build() {
      String normalizedMode = Objects.requireNonNullElse(mode, "webhook");
      if (!"webhook".equalsIgnoreCase(normalizedMode)) {
        throw new IllegalArgumentException("Only webhook mode is supported");
      }
      return new Config(
          normalizedMode.toLowerCase(Locale.ROOT),
          Map.copyOf(routes),
          defaults,
          queue,
          retry,
          ratelimit,
          log);
    }
  }

  public static final class RouteDefinition {
    private final String name;
    private final String rawTarget;
    private final boolean environment;
    private final String envVariable;

    private RouteDefinition(String name, String rawTarget, boolean environment, String envVariable) {
      this.name = name;
      this.rawTarget = rawTarget;
      this.environment = environment;
      this.envVariable = envVariable;
    }

    public String name() { return name; }

    public String rawTarget() { return rawTarget; }

    public boolean environment() { return environment; }

    public String envVariable() { return envVariable; }

    public static RouteDefinition of(String name, String target) {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(target, "target");
      String trimmed = target.trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException("Route target may not be blank");
      }
      if (trimmed.regionMatches(true, 0, "env:", 0, 4)) {
        String env = trimmed.substring(4).trim();
        if (env.isEmpty()) {
          throw new IllegalArgumentException("env: routes must specify a variable name");
        }
        return new RouteDefinition(name, trimmed, true, env);
      }
      return new RouteDefinition(name, trimmed, false, null);
    }
  }

  public static final class Defaults {
    static final Defaults DEFAULTS = new Defaults("MinDiscord", "");
    private final String username;
    private final String avatarUrl;

    public Defaults(String username, String avatarUrl) {
      this.username = Objects.requireNonNullElse(username, "MinDiscord");
      this.avatarUrl = Objects.requireNonNullElse(avatarUrl, "");
    }

    public String username() { return username; }

    public String avatarUrl() { return avatarUrl; }
  }

  public enum QueueOverflowPolicy {
    DROP_OLDEST,
    DROP_NEWEST,
    REJECT;

    static QueueOverflowPolicy from(String raw) {
      if (raw == null) {
        return DROP_OLDEST;
      }
      return switch (raw.toLowerCase(Locale.ROOT)) {
        case "dropoldest" -> DROP_OLDEST;
        case "dropnewest" -> DROP_NEWEST;
        case "reject" -> REJECT;
        default -> throw new IllegalArgumentException("Unknown queue policy: " + raw);
      };
    }
  }

  public static final class Queue {
    static final Queue DEFAULTS = new Queue(2000, QueueOverflowPolicy.DROP_OLDEST);
    private final int maxSize;
    private final QueueOverflowPolicy onOverflow;

    public Queue(int maxSize, QueueOverflowPolicy onOverflow) {
      if (maxSize <= 0) {
        throw new IllegalArgumentException("queue.maxSize must be > 0");
      }
      this.maxSize = maxSize;
      this.onOverflow = Objects.requireNonNull(onOverflow, "onOverflow");
    }

    public int maxSize() { return maxSize; }

    public QueueOverflowPolicy onOverflow() { return onOverflow; }
  }

  public static final class Retry {
    static final Retry DEFAULTS =
        new Retry(6, Duration.ofMillis(500), Duration.ofMillis(15_000), true);
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final boolean jitter;

    public Retry(int maxAttempts, Duration baseDelay, Duration maxDelay, boolean jitter) {
      if (maxAttempts <= 0) {
        throw new IllegalArgumentException("retry.maxAttempts must be > 0");
      }
      this.maxAttempts = maxAttempts;
      this.baseDelay = Objects.requireNonNull(baseDelay, "baseDelay");
      this.maxDelay = Objects.requireNonNull(maxDelay, "maxDelay");
      this.jitter = jitter;
    }

    public int maxAttempts() { return maxAttempts; }

    public Duration baseDelay() { return baseDelay; }

    public Duration maxDelay() { return maxDelay; }

    public boolean jitter() { return jitter; }
  }

  public static final class RateLimit {
    static final RateLimit DEFAULTS = new RateLimit(10, 5);
    private final int perRouteBurst;
    private final int perRouteRefillPerSec;

    public RateLimit(int perRouteBurst, int perRouteRefillPerSec) {
      if (perRouteBurst <= 0) {
        throw new IllegalArgumentException("ratelimit.perRouteBurst must be > 0");
      }
      if (perRouteRefillPerSec <= 0) {
        throw new IllegalArgumentException("ratelimit.perRouteRefillPerSec must be > 0");
      }
      this.perRouteBurst = perRouteBurst;
      this.perRouteRefillPerSec = perRouteRefillPerSec;
    }

    public int perRouteBurst() { return perRouteBurst; }

    public int perRouteRefillPerSec() { return perRouteRefillPerSec; }
  }

  public static final class Log {
    static final Log DEFAULTS = new Log("INFO", false);
    private final String level;
    private final boolean json;

    public Log(String level, boolean json) {
      this.level = Objects.requireNonNullElse(level, "INFO");
      this.json = json;
    }

    public String level() { return level; }

    public boolean json() { return json; }
  }

  public static Config fromRaw(Raw raw) {
    if (raw == null) {
      return defaultConfig();
    }
    Builder builder = builder();
    if (raw.mode != null) {
      builder.mode(raw.mode);
    }
    if (raw.routes != null) {
      for (Map.Entry<String, String> entry : raw.routes.entrySet()) {
        builder.putRoute(entry.getKey(), entry.getValue());
      }
    }
    builder.defaults(raw.defaults != null ? raw.defaults.toDefaults() : Defaults.DEFAULTS);
    builder.queue(raw.queue != null ? raw.queue.toQueue() : Queue.DEFAULTS);
    builder.retry(raw.retry != null ? raw.retry.toRetry() : Retry.DEFAULTS);
    builder.ratelimit(raw.ratelimit != null ? raw.ratelimit.toRateLimit() : RateLimit.DEFAULTS);
    builder.log(raw.log != null ? raw.log.toLog() : Log.DEFAULTS);
    return builder.build();
  }

  public static final class Raw {
    public String mode;
    public Map<String, String> routes;
    public RawDefaults defaults;
    public RawQueue queue;
    public RawRetry retry;
    public RawRateLimit ratelimit;
    public RawLog log;
  }

  public static final class RawDefaults {
    public String username;
    public String avatarUrl;

    Defaults toDefaults() { return new Defaults(username, avatarUrl); }
  }

  public static final class RawQueue {
    public Integer maxSize;
    public String onOverflow;

    Queue toQueue() {
      int size = maxSize != null ? maxSize : Queue.DEFAULTS.maxSize();
      QueueOverflowPolicy policy = QueueOverflowPolicy.from(onOverflow);
      return new Queue(size, policy);
    }
  }

  public static final class RawRetry {
    public Integer maxAttempts;
    public Integer baseDelayMs;
    public Integer maxDelayMs;
    public Boolean jitter;

    Retry toRetry() {
      int attempts = maxAttempts != null ? maxAttempts : Retry.DEFAULTS.maxAttempts();
      Duration base =
          Duration.ofMillis(baseDelayMs != null ? baseDelayMs : Retry.DEFAULTS.baseDelay().toMillis());
      Duration max =
          Duration.ofMillis(maxDelayMs != null ? maxDelayMs : Retry.DEFAULTS.maxDelay().toMillis());
      boolean useJitter = jitter != null ? jitter : Retry.DEFAULTS.jitter();
      if (base.isZero() || base.isNegative()) {
        throw new IllegalArgumentException("retry.baseDelayMs must be > 0");
      }
      if (max.compareTo(base) < 0) {
        throw new IllegalArgumentException("retry.maxDelayMs must be >= baseDelayMs");
      }
      return new Retry(attempts, base, max, useJitter);
    }
  }

  public static final class RawRateLimit {
    public Integer perRouteBurst;
    public Integer perRouteRefillPerSec;

    RateLimit toRateLimit() {
      int burst =
          perRouteBurst != null ? perRouteBurst : RateLimit.DEFAULTS.perRouteBurst();
      int refill =
          perRouteRefillPerSec != null
              ? perRouteRefillPerSec
              : RateLimit.DEFAULTS.perRouteRefillPerSec();
      return new RateLimit(burst, refill);
    }
  }

  public static final class RawLog {
    public String level;
    public Boolean json;

    Log toLog() {
      return new Log(level, json != null ? json : Log.DEFAULTS.json());
    }
  }

  public List<RouteDefinition> orderedRoutes() {
    return new ArrayList<>(routes.values());
  }
}
