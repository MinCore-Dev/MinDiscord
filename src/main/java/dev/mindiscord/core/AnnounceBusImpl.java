package dev.mindiscord.core;

import dev.mindiscord.api.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AnnounceBusImpl implements AnnounceBus {
  private final Router router;
  private final WebhookTransport transport;
  @SuppressWarnings("unused") private final Config cfg;

  public AnnounceBusImpl(Router router, WebhookTransport transport, Config cfg) {
    this.router = router; this.transport = transport; this.cfg = cfg;
  }

  @Override public CompletableFuture<SendResult> send(String route, String content) {
    return send(route, messageFor(content));
  }

  @Override public CompletableFuture<SendResult> send(String route, WebhookMessage msg) {
    String url = router.resolve(route);
    String json = toJson(msg);
    String reqId = UUID.randomUUID().toString();
    boolean ok = url != null && transport.postJson(url, json);
    return CompletableFuture.completedFuture(new SendResult(ok, ok ? "OK" : "NETWORK_IO", ok ? "sent" : "failed", reqId));
  }

  @Override public CompletableFuture<SendResult> send(String route, Embed embed) {
    WebhookMessage m = new WebhookMessage();
    m.embeds = java.util.List.of(embed);
    return send(route, m);
  }

  private static WebhookMessage messageFor(String content) {
    WebhookMessage m = new WebhookMessage(); m.content = content; return m;
  }

  // Trivial JSON builder to avoid dependencies; plugins can send complex embeds if needed
  private static String toJson(WebhookMessage m) {
    StringBuilder sb = new StringBuilder(); sb.append('{');
    if (m.content != null) {
      sb.append(""content":").append(quote(m.content));
    }
    sb.append('}');
    return sb.toString();
  }

  private static String quote(String s) {
    return """ + s.replace("\", "\\").replace(""", "\"") + """;
    }
}
