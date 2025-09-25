package dev.mindiscord.perms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PermsTest {
  @AfterEach
  void reset() {
    Perms.resetFallbackForTesting();
  }

  @Test
  void fallsBackToOpLevelWhenGatewaysUnavailable() {
    AtomicInteger levelSeen = new AtomicInteger();
    Perms.setFallbackForTesting(
        (player, level) -> {
          levelSeen.set(level);
          return true;
        });

    boolean result = Perms.check(null, "mindiscord.admin", 2);

    assertTrue(result);
    assertEquals(2, levelSeen.get());
  }
}
