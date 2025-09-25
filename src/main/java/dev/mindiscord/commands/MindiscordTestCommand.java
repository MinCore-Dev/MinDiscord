package dev.mindiscord.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mindiscord.api.SendResult;
import dev.mindiscord.core.MinDiscordRuntime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class MindiscordTestCommand {
  private MindiscordTestCommand() {}

  public static void register(
      LiteralArgumentBuilder<ServerCommandSource> root, MinDiscordRuntime runtime) {
    root.then(CommandManager.literal("test")
        .then(CommandManager.argument("route", StringArgumentType.string())
            .then(CommandManager.argument("text", StringArgumentType.greedyString())
                .executes(ctx -> execute(ctx.getSource(), runtime,
                    StringArgumentType.getString(ctx, "route"),
                    StringArgumentType.getString(ctx, "text"))))));
  }

  private static int execute(
      ServerCommandSource source, MinDiscordRuntime runtime, String route, String text) {
    if (!CommandRegistrar.tryConsumeCooldown(source)) {
      return 0;
    }
    source.sendFeedback(() -> Text.literal("Dispatching test message…"), false);
    CompletableFuture<SendResult> future = runtime.bus().send(route, text);
    future.whenComplete((result, error) -> {
      if (error != null) {
        source.sendError(Text.literal("Test send failed: " + error.getMessage()));
        CommandRegistrar.logCommand(runtime, "test", false, error.getMessage());
      } else {
        if (result.ok()) {
          source.sendFeedback(
              () -> Text.literal(
                  "Sent (" + result.code() + "): " + Objects.requireNonNullElse(result.message(), "")),
              false);
        } else {
          source.sendError(
              Text.literal(
                  "Send failed (" + result.code() + "): "
                      + Objects.requireNonNullElse(result.message(), "")));
        }
        CommandRegistrar.logCommand(runtime, "test", result.ok(), result.code());
      }
    });
    return 1;
  }
}
