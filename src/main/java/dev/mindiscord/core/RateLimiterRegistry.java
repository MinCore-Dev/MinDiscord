package dev.mindiscord.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RateLimiterRegistry {
  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
  private volatile Config.RateLimit rateLimit = Config.RateLimit.DEFAULTS;

  void configure(Config.RateLimit rateLimit) {
    this.rateLimit = rateLimit;
    buckets.forEach((route, bucket) -> bucket.configure(rateLimit.ruleFor(route)));
  }

  Duration acquire(String key, TimeSource timeSource) {
    Config.RateLimit.Rule rule = rateLimit.ruleFor(key);
    TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rule, timeSource));
    bucket.configure(rule);
    return bucket.acquire(timeSource);
  }

  private static final class TokenBucket {
    private double capacity;
    private double refillPerSecond;
    private double tokens;
    private long lastCheck;

    TokenBucket(Config.RateLimit.Rule rule, TimeSource timeSource) {
      configure(rule);
      this.tokens = capacity;
      this.lastCheck = timeSource.nanoTime();
    }

    synchronized Duration acquire(TimeSource timeSource) {
      long now = timeSource.nanoTime();
      refill(now);
      if (tokens >= 1.0d) {
        tokens -= 1.0d;
        return Duration.ZERO;
      }
      double needed = 1.0d - tokens;
      double seconds = needed / Math.max(0.0001d, refillPerSecond);
      long nanos = (long) Math.ceil(seconds * 1_000_000_000L);
      tokens = Math.max(-capacity, tokens - 1.0d);
      return Duration.ofNanos(Math.max(0L, nanos));
    }

    synchronized void configure(Config.RateLimit.Rule rule) {
      this.capacity = rule.burst();
      this.refillPerSecond = rule.refillTokensPerSecond();
      if (tokens > capacity) {
        tokens = capacity;
      }
      if (tokens < -capacity) {
        tokens = -capacity;
      }
    }

    private void refill(long now) {
      long elapsed = Math.max(0L, now - lastCheck);
      if (elapsed <= 0L) {
        return;
      }
      double seconds = elapsed / 1_000_000_000d;
      tokens = Math.min(capacity, tokens + seconds * refillPerSecond);
      lastCheck = now;
    }
  }
}
