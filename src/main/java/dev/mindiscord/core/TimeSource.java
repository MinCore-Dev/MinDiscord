package dev.mindiscord.core;

import java.time.Instant;

interface TimeSource {
  Instant now();

  long nanoTime();
}
