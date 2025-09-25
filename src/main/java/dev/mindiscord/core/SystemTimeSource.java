package dev.mindiscord.core;

import java.time.Instant;

final class SystemTimeSource implements TimeSource {
  @Override public Instant now() { return Instant.now(); }

  @Override public long nanoTime() { return System.nanoTime(); }
}
