package dev.mindiscord.core;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class StatsStore {
  private static final Logger LOGGER = LogManager.getLogger("MinDiscord/Stats");

  private final MinCoreBridge bridge;
  private final AtomicBoolean ensured = new AtomicBoolean();

  StatsStore(MinCoreBridge bridge) {
    this.bridge = bridge;
  }

  void record(String route, boolean success) {
    if (!bridge.available()) {
      return;
    }
    bridge.withDatabase(db -> {
      try {
        bridge.ensureStatsTable(db, ensured);
        bridge.updateStats(db, route, success);
      } catch (Exception e) {
        LOGGER.debug("Failed to update stats: {}", e.toString());
      }
    });
  }
}
