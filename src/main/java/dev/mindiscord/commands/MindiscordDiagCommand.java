package dev.mindiscord.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mindiscord.core.AnnounceBusImpl;
import dev.mindiscord.core.MinDiscordRuntime;
import java.time.Instant;
import java.util.Map;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class MindiscordDiagCommand {
  private MindiscordDiagCommand() {}

  public static void register(
      LiteralArgumentBuilder<ServerCommandSource> root, MinDiscordRuntime runtime) {
    root.then(CommandManager.literal("diag").executes(ctx -> execute(ctx.getSource(), runtime)));
  }

  private static int execute(ServerCommandSource source, MinDiscordRuntime runtime) {
    if (!CommandRegistrar.tryConsumeCooldown(source)) {
      return 0;
    }
    AnnounceBusImpl.DiagnosticsSnapshot snapshot = runtime.diagnostics();
    source.sendFeedback(
        () ->
            Text.literal(
                String.format(
                    "Queue: %d/%d",
                    snapshot.queueSize(), snapshot.queueCapacity())),
        false);
    if (snapshot.routes().isEmpty()) {
      source.sendFeedback(() -> Text.literal("  (no route history yet)"), false);
    } else {
      for (Map.Entry<String, dev.mindiscord.core.Diagnostics.RouteSnapshot> entry :
          snapshot.routes().entrySet()) {
        var info = entry.getValue();
        source.sendFeedback(
            () ->
                Text.literal(
                    String.format(
                        "  - %s | lastSuccess=%s | lastFailure=%s (%s)",
                        entry.getKey(),
                        formatInstant(info.lastSuccess()),
                        formatInstant(info.lastFailure()),
                        info.lastFailureCode() != null ? info.lastFailureCode() : "-")),
            false);
      }
    }
    CommandRegistrar.logCommand(runtime, "diag", true, null);
    return snapshot.routes().size();
  }

  private static String formatInstant(Instant instant) {
    return instant != null ? instant.toString() : "never";
  }
}
