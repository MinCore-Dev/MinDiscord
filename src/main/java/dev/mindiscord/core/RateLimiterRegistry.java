package dev.mindiscord.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RateLimiterRegistry {
  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
  private volatile int burst = 10;
  private volatile int refillPerSec = 5;

  void configure(int burst, int refillPerSec) {
    this.burst = burst;
    this.refillPerSec = refillPerSec;
    buckets.values().forEach(bucket -> bucket.configure(burst, refillPerSec));
  }

  Duration acquire(String key, TimeSource timeSource) {
    TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(burst, refillPerSec, timeSource));
    return bucket.acquire(timeSource);
  }

  private static final class TokenBucket {
    private volatile int capacity;
    private volatile int refillPerSec;
    private double tokens;
    private long lastCheck;

    TokenBucket(int capacity, int refillPerSec, TimeSource timeSource) {
      this.capacity = capacity;
      this.refillPerSec = refillPerSec;
      this.tokens = capacity;
      this.lastCheck = timeSource.nanoTime();
    }

    synchronized Duration acquire(TimeSource timeSource) {
      long now = timeSource.nanoTime();
      refill(now);
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return Duration.ZERO;
      }
      double needed = 1.0 - tokens;
      double seconds = needed / Math.max(1, refillPerSec);
      long nanos = (long) Math.ceil(seconds * 1_000_000_000L);
      tokens = Math.max(-capacity, tokens - 1.0);
      return Duration.ofNanos(Math.max(0, nanos));
    }

    synchronized void configure(int capacity, int refillPerSec) {
      this.capacity = capacity;
      this.refillPerSec = refillPerSec;
      if (tokens > capacity) {
        tokens = capacity;
      }
      if (tokens < -capacity) {
        tokens = -capacity;
      }
    }

    private void refill(long now) {
      long elapsed = Math.max(0, now - lastCheck);
      if (elapsed <= 0) {
        return;
      }
      double seconds = elapsed / 1_000_000_000d;
      tokens = Math.min(capacity, tokens + seconds * refillPerSec);
      lastCheck = now;
    }
  }
}
