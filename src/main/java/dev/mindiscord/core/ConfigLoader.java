package dev.mindiscord.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hjson.JsonValue;
import org.hjson.Stringify;

/** Loads {@link Config} from disk and watches for changes. */
public final class ConfigLoader implements AutoCloseable {
  private static final Logger LOGGER = LogManager.getLogger("MinDiscord/Config");

  private static final Path LIVE = Path.of("config/mindiscord.json5");
  private static final Path EXAMPLE = Path.of("config/mindiscord.json5.example");

  private final ObjectMapper mapper;
  private final ExecutorService watcherExecutor;
  private final CopyOnWriteArrayList<Consumer<Config>> listeners = new CopyOnWriteArrayList<>();
  private final MinCoreBridge bridge;

  private volatile Config current;
  private volatile boolean closed;

  public ConfigLoader(MinCoreBridge bridge) {
    this.bridge = bridge;
    this.mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.watcherExecutor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "MinDiscord-ConfigWatcher");
      t.setDaemon(true);
      return t;
    });
    ensureExampleConfig();
    this.current = loadConfig();
    startWatcher();
  }

  public Config current() { return current; }

  public void addListener(Consumer<Config> listener) {
    Objects.requireNonNull(listener, "listener");
    listeners.add(listener);
    listener.accept(current);
  }

  private void ensureExampleConfig() {
    try {
      if (Files.exists(EXAMPLE)) {
        return;
      }
      Files.createDirectories(EXAMPLE.getParent());
      Runnable writeExample = () -> {
        try {
          Files.writeString(EXAMPLE, exampleContents(), StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      };
      if (!bridge.tryWithAdvisoryLock("mindiscord:init", writeExample)) {
        // Could not acquire lock (maybe another node); best-effort write without lock.
        writeExample.run();
      }
      if (!Files.exists(LIVE)) {
        Files.copy(EXAMPLE, LIVE, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException | UncheckedIOException e) {
      throw new RuntimeException("Failed to create example config", e);
    }
  }

  private Config loadConfig() {
    try {
      if (!Files.exists(LIVE)) {
        LOGGER.warn("mindiscord.json5 missing; using defaults");
        return Config.defaultConfig();
      }
      String raw = Files.readString(LIVE, StandardCharsets.UTF_8);
      String json = JsonValue.readHjson(raw).toString(Stringify.PLAIN);
      Config.Raw parsed = mapper.readValue(json, Config.Raw.class);
      Config cfg = Config.fromRaw(parsed);
      if (cfg.routes().isEmpty()) {
        LOGGER.warn("No routes configured; Discord sends will fail until configured");
      }
      return cfg;
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Failed to load mindiscord.json5: {}", e.toString());
      if (current != null) {
        LOGGER.warn("Retaining previous configuration due to load failure");
        return current;
      }
      throw new RuntimeException("No valid configuration available", e);
    }
  }

  private void startWatcher() {
    watcherExecutor.execute(() -> {
      try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
        Path dir = LIVE.getParent();
        if (dir == null) {
          return;
        }
        dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        Instant lastReload = Instant.EPOCH;
        while (!closed) {
          WatchKey key = watcher.poll(500, TimeUnit.MILLISECONDS);
          if (key == null) {
            continue;
          }
          boolean relevant = false;
          for (WatchEvent<?> event : key.pollEvents()) {
            Object ctx = event.context();
            if (ctx instanceof Path path && path.getFileName().equals(LIVE.getFileName())) {
              relevant = true;
            }
          }
          key.reset();
          if (relevant) {
            Instant now = Instant.now();
            if (now.minusMillis(150).isAfter(lastReload)) {
              reload();
              lastReload = now;
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        LOGGER.error("Config watcher failed: {}", e.toString());
      }
    });
  }

  private void reload() {
    Config cfg = loadConfig();
    if (cfg == current) {
      return;
    }
    current = cfg;
    for (Consumer<Config> listener : listeners) {
      try {
        listener.accept(cfg);
      } catch (RuntimeException e) {
        LOGGER.error("Config listener threw: {}", e.toString());
      }
    }
    LOGGER.info("Reloaded mindiscord.json5 ({} routes)", cfg.routes().size());
  }

  private static String exampleContents() {
    return """
        {
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
  }

  @Override
  public void close() {
    closed = true;
    watcherExecutor.shutdownNow();
  }
}
