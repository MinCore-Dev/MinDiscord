package dev.mindiscord.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

public final class WebhookTransport implements WebhookClient {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

  @Override
  public TransportResponse postJson(String url, String json) {
    try {
      var req = HttpRequest.newBuilder(URI.create(url))
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(5))
          .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
          .build();
      var resp = client.send(req, HttpResponse.BodyHandlers.discarding());
      int status = resp.statusCode();
      Duration retry = parseRetryAfter(resp.headers().firstValue("Retry-After"));
      return new TransportResponse(status >= 200 && status < 300, status, retry, null);
    } catch (Exception e) {
      return new TransportResponse(false, -1, null, e);
    }
  }

  private static Duration parseRetryAfter(Optional<String> header) {
    if (header.isEmpty()) {
      return null;
    }
    String value = header.get().trim();
    if (value.isEmpty()) {
      return null;
    }
    try {
      long seconds = Long.parseLong(value);
      if (seconds < 0) {
        return null;
      }
      return Duration.ofSeconds(seconds);
    } catch (NumberFormatException ignored) {
      try {
        TemporalAccessor parsed = DateTimeFormatter.RFC_1123_DATE_TIME.parse(value);
        long millis = java.time.Instant.from(parsed).toEpochMilli() - System.currentTimeMillis();
        if (millis <= 0) {
          return null;
        }
        return Duration.ofMillis(millis);
      } catch (DateTimeParseException ex) {
        return null;
      }
    }
  }

  public record TransportResponse(boolean success, int statusCode, Duration retryAfter, Throwable error) {}
}
