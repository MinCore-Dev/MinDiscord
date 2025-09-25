package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterRegistryTest {

  @Test
  void refillsTokensAfterReportedDelay() {
    RateLimiterRegistry registry = new RateLimiterRegistry();
    FakeTimeSource time = new FakeTimeSource();
    registry.configure(2, 2);

    Duration[] waits = new Duration[4];
    for (int i = 0; i < waits.length; i++) {
      waits[i] = registry.acquire("route", time);
      time.advance(waits[i]);
    }

    assertEquals(Duration.ZERO, waits[0]);
    assertEquals(Duration.ZERO, waits[1]);
    assertEquals(Duration.ofMillis(500), waits[2]);
    assertEquals(Duration.ofMillis(500), waits[3]);

    time.advance(Duration.ofSeconds(2));

    assertEquals(Duration.ZERO, registry.acquire("route", time));
    assertEquals(Duration.ZERO, registry.acquire("route", time));
  }

  private static final class FakeTimeSource implements TimeSource {
    private Instant instant = Instant.EPOCH;
    private long nanos;

    @Override
    public Instant now() {
      return instant;
    }

    @Override
    public long nanoTime() {
      return nanos;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
      nanos += duration.toNanos();
    }
  }
}
