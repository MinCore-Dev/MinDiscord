package dev.mindiscord.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class ConfigLoader {
  private static final Path LIVE = Path.of("config/mindiscord.json5");
  private static final Path EXAMPLE = Path.of("config/mindiscord.json5.example");

  public static Config loadOrCreate() {
    try {
      if (!Files.exists(EXAMPLE)) {
        Files.createDirectories(EXAMPLE.getParent());
        String ex = """{
  mode: "webhook",
  routes: {
    default:            "https://discord.com/api/webhooks/GGGG/HHH",
    eventAnnouncements: "env:MINDISCORD_WEBHOOK_ANNOUNCE",
    eventStarts:        "https://discord.com/api/webhooks/AAAA/BBB",
    eventWinners:       "https://discord.com/api/webhooks/CCCC/DDD",
    rareDrops:          "https://discord.com/api/webhooks/EEEE/FFF"
  },
  defaults: { username: "MinDiscord", avatarUrl: "" },
  queue: { maxSize: 2000, onOverflow: "dropOldest" },
  retry: { maxAttempts: 6, baseDelayMs: 500, maxDelayMs: 15000, jitter: true },
  ratelimit: { perRouteBurst: 10, perRouteRefillPerSec: 5 },
  log: { level: "INFO", json: false }
}
""";
        Files.writeString(EXAMPLE, ex, StandardCharsets.UTF_8);
      }
      Config cfg = new Config();
      cfg.routes = new LinkedHashMap<>();
      cfg.routes.put("default", "https://discord.com/api/webhooks/GGGG/HHH");
      return cfg;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
