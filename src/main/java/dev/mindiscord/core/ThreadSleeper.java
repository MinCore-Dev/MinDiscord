package dev.mindiscord.core;

import java.time.Duration;

final class ThreadSleeper implements Sleeper {
  @Override
  public void sleep(Duration duration) throws InterruptedException {
    if (duration.isZero() || duration.isNegative()) {
      return;
    }
    long millis = duration.toMillis();
    int nanos = (int) (duration.toNanos() - millis * 1_000_000L);
    Thread.sleep(millis, Math.max(0, nanos));
  }
}
