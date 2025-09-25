package dev.mindiscord.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mindiscord.core.Router;
import org.junit.jupiter.api.Test;

class MindiscordRoutesCommandTest {
  @Test
  void formatRouteRespectsRedactionToggle() {
    Router.RouteInfo info =
        new Router.RouteInfo(
            "default",
            "https://discord.com/api/webhooks/AAA/BBB",
            false,
            null,
            true);

    String redacted = MindiscordRoutesCommand.formatRoute(info, true);
    String plain = MindiscordRoutesCommand.formatRoute(info, false);

    assertEquals("https://discord.com/api/webhooks/…", redacted);
    assertEquals("https://discord.com/api/webhooks/AAA/BBB", plain);
  }
}
