package dev.mindiscord.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Config {
  public Map<String,String> routes = new LinkedHashMap<>();
  public Defaults defaults = new Defaults();
  public Queue queue = new Queue();
  public Retry retry = new Retry();
  public RateLimit ratelimit = new RateLimit();
  public Log log = new Log();

  public static final class Defaults { public String username = "MinDiscord"; public String avatarUrl = ""; }
  public static final class Queue { public int maxSize = 2000; public String onOverflow = "dropOldest"; }
  public static final class Retry { public int maxAttempts = 6; public int baseDelayMs = 500; public int maxDelayMs = 15000; public boolean jitter = true; }
  public static final class RateLimit { public int perRouteBurst = 10; public int perRouteRefillPerSec = 5; }
  public static final class Log { public String level = "INFO"; public boolean json = false; }
}
