package dev.mindiscord.core;

import java.time.Duration;

interface Sleeper {
  void sleep(Duration duration) throws InterruptedException;
}
