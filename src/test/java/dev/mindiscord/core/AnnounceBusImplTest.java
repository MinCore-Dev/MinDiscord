package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.mindiscord.api.SendResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AnnounceBusImplTest {
  private AnnounceBusImpl bus;

  @AfterEach
  void cleanup() {
    if (bus != null) {
      bus.close();
    }
  }

  @Test
  void unknownRouteWithoutDefaultReturnsBadRoute() throws Exception {
    Config cfg =
        Config.builder()
            .announce(new Config.Announce(true, false, List.of("missing", "known")))
            .putRoute("known", "https://example/known")
            .build();
    bus = buildBus(cfg, new SuccessTransport());
    SendResult result = bus.send("missing", "hello").get(1, TimeUnit.SECONDS);
    assertFalse(result.ok());
    assertEquals("BAD_ROUTE", result.code());
  }

  @Test
  void fallbackToDefaultReturnsBadRouteFallback() throws Exception {
    Config cfg =
        Config.builder()
            .announce(new Config.Announce(true, true, List.of("default", "missing")))
            .putRoute("default", "https://example/default")
            .build();
    RecordingTransport transport = new RecordingTransport();
    bus = buildBus(cfg, transport);
    SendResult result = bus.send("missing", "hello").get(1, TimeUnit.SECONDS);
    assertTrue(result.ok());
    assertEquals("BAD_ROUTE_FALLBACK", result.code());
    assertEquals("https://example/default", transport.lastUrl);
  }

  @Test
  void retriesGiveUpAfterFailures() throws Exception {
    Config cfg =
        Config.builder()
            .putRoute("default", "https://example/default")
            .transport(new Config.Transport(3000, 5000, 2))
            .build();
    FailingTransport transport = new FailingTransport();
    FakeTimeSource time = new FakeTimeSource();
    FakeSleeper sleeper = new FakeSleeper(time);
    MinCoreBridge bridge = new MinCoreBridge();
    bus =
        new AnnounceBusImpl(
            new Router(),
            new DispatchQueue(),
            transport,
            new RateLimiterRegistry(),
            new StatsStore(bridge),
            bridge,
            time,
            sleeper,
            cfg);
    SendResult result = bus.send("default", "msg").get(1, TimeUnit.SECONDS);
    assertFalse(result.ok());
    assertEquals("GIVE_UP", result.code());
  }

  @Test
  void announceDisabledBlocksSend() throws Exception {
    Config cfg =
        Config.builder()
            .announce(new Config.Announce(false, true, List.of("default")))
            .putRoute("default", "https://example/default")
            .build();
    bus = buildBus(cfg, new SuccessTransport());
    SendResult result = bus.send("default", "test").get(1, TimeUnit.SECONDS);
    assertFalse(result.ok());
    assertEquals("DISABLED", result.code());
  }

  @Test
  void fallbackDisabledReturnsBadRoute() throws Exception {
    Config cfg =
        Config.builder()
            .announce(new Config.Announce(true, false, List.of("default", "missing")))
            .putRoute("default", "https://example/default")
            .build();
    bus = buildBus(cfg, new SuccessTransport());
    SendResult result = bus.send("missing", "test").get(1, TimeUnit.SECONDS);
    assertFalse(result.ok());
    assertEquals("BAD_ROUTE", result.code());
  }

  @Test
  void disallowedRouteReturnsRouteDisabled() throws Exception {
    Config cfg =
        Config.builder()
            .announce(new Config.Announce(true, true, List.of("default")))
            .putRoute("default", "https://example/default")
            .build();
    bus = buildBus(cfg, new SuccessTransport());
    SendResult result = bus.send("blocked", "test").get(1, TimeUnit.SECONDS);
    assertFalse(result.ok());
    assertEquals("ROUTE_DISABLED", result.code());
  }

  private AnnounceBusImpl buildBus(Config config, WebhookClient transport) {
    FakeTimeSource time = new FakeTimeSource();
    FakeSleeper sleeper = new FakeSleeper(time);
    Router router = new Router();
    MinCoreBridge bridge = new MinCoreBridge();
    AnnounceBusImpl instance =
        new AnnounceBusImpl(
            router,
            new DispatchQueue(),
            transport,
            new RateLimiterRegistry(),
            new StatsStore(bridge),
            bridge,
            time,
            sleeper,
            config);
    return instance;
  }

  private static final class SuccessTransport implements WebhookClient {
    @Override
    public WebhookTransport.TransportResponse postJson(String url, String json) {
      return new WebhookTransport.TransportResponse(true, 204, null, null);
    }
  }

  private static final class RecordingTransport implements WebhookClient {
    volatile String lastUrl;

    @Override
    public WebhookTransport.TransportResponse postJson(String url, String json) {
      this.lastUrl = url;
      return new WebhookTransport.TransportResponse(true, 204, null, null);
    }
  }

  private static final class BlockingTransport implements WebhookClient {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public WebhookTransport.TransportResponse postJson(String url, String json) {
      started.countDown();
      try {
        release.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return new WebhookTransport.TransportResponse(true, 204, null, null);
    }
  }

  private static final class FailingTransport implements WebhookClient {
    @Override
    public WebhookTransport.TransportResponse postJson(String url, String json) {
      return new WebhookTransport.TransportResponse(false, 500, null, null);
    }
  }

  private static final class FakeTimeSource implements TimeSource {
    private Instant instant = Instant.EPOCH;
    private long nanos;

    @Override public Instant now() { return instant; }

    @Override public long nanoTime() { return nanos; }

    void advance(Duration duration) {
      instant = instant.plus(duration);
      nanos += duration.toNanos();
    }
  }

  private static final class FakeSleeper implements Sleeper {
    private final FakeTimeSource time;

    FakeSleeper(FakeTimeSource time) { this.time = time; }

    @Override
    public void sleep(Duration duration) throws InterruptedException {
      time.advance(duration);
    }
  }
}
