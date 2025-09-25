
package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Sample CI test that stubs a webhook endpoint to validate the HTTP transport.
 * Adapt into your build with JUnit 5.
 */
public class WebhookTransportTest {

  static HttpServer server;
  static volatile String lastBody;
  static volatile String lastPath;

  @BeforeAll
  static void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/webhooks/test", new HttpHandler() {
      @Override public void handle(HttpExchange ex) throws IOException {
        lastPath = ex.getRequestURI().getPath();
        try (InputStream in = ex.getRequestBody()) {
          lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // emulate Discord webhook success (204 No Content)
        ex.sendResponseHeaders(204, -1);
        ex.close();
      }
    });
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
  }

  @AfterAll
  static void stop() { server.stop(0); }

  @Test
  void sendsJsonToWebhook_andTreats2xxAsSuccess() throws Exception {
    var port = server.getAddress().getPort();
    var url = "http://127.0.0.1:" + port + "/api/webhooks/test";

    var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    var json = "{\"content\":\"Hello from MinDiscord\"}";
    var req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
    assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 300, "Expected 2xx status");
    assertEquals("/api/webhooks/test", lastPath);
    assertNotNull(lastBody);
    assertTrue(lastBody.contains("\"content\""));
  }
}
