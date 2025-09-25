package dev.mindiscord.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mindiscord.core.MinDiscordRuntime;
import dev.mindiscord.core.Router;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class MindiscordRoutesCommand {
  private MindiscordRoutesCommand() {}

  public static void register(
      LiteralArgumentBuilder<ServerCommandSource> root, MinDiscordRuntime runtime) {
    root.then(CommandManager.literal("routes").executes(ctx -> execute(ctx.getSource(), runtime)));
  }

  private static int execute(ServerCommandSource source, MinDiscordRuntime runtime) {
    if (!CommandRegistrar.tryConsumeCooldown(source)) {
      return 0;
    }
    var routes = runtime.routes();
    source.sendFeedback(
        () -> Text.literal("MinDiscord routes (" + routes.size() + ")"),
        false);
    if (routes.isEmpty()) {
      source.sendFeedback(() -> Text.literal("  (no routes configured)"), false);
    } else {
      for (Router.RouteInfo info : routes) {
        String value;
        if (info.environment()) {
          value =
              "env:" + info.envVariable() + (info.available() ? " (set)" : " (missing)");
        } else {
          value = redact(info.rawTarget());
        }
        source.sendFeedback(
            () -> Text.literal("  - " + info.name() + ": " + value), false);
      }
    }
    CommandRegistrar.logCommand(runtime, "routes", true, null);
    return routes.size();
  }

  private static String redact(String url) {
    if (url == null || url.isBlank()) {
      return "unset";
    }
    int idx = url.indexOf("/api/webhooks/");
    if (idx > 0) {
      return url.substring(0, idx + "/api/webhooks/".length()) + "…";
    }
    return url;
  }
}
