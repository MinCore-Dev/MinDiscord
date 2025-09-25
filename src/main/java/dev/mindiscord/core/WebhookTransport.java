package dev.mindiscord.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WebhookTransport {
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  public boolean postJson(String url, String json) {
    try {
      var req = HttpRequest.newBuilder(URI.create(url))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(5))
          .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
          .build();
      var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
      int sc = resp.statusCode();
      return sc >= 200 && sc < 300;
    } catch (Exception e) {
      return false;
    }
  }
}
