package dev.mindiscord.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mindiscord.core.MinDiscordRuntime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class CommandRegistrar {
  private static final long COOLDOWN_MS = 2000;
  private static final Map<Object, Long> COOLDOWNS = new ConcurrentHashMap<>();

  private CommandRegistrar() {}

  public static void registerAll(MinDiscordRuntime runtime) {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      LiteralArgumentBuilder<ServerCommandSource> root =
          CommandManager.literal("mindiscord").requires(src -> src.hasPermissionLevel(2));
      MindiscordRoutesCommand.register(root, runtime);
      MindiscordTestCommand.register(root, runtime);
      MindiscordDiagCommand.register(root, runtime);
      dispatcher.register(root);
    });
  }

  static boolean tryConsumeCooldown(ServerCommandSource source) {
    Object key = source.getEntity() != null ? source.getEntity().getUuid() : CommandRegistrar.class;
    long now = System.currentTimeMillis();
    Long last = COOLDOWNS.get(key);
    if (last != null && now - last < COOLDOWN_MS) {
      long remaining = COOLDOWN_MS - (now - last);
      source.sendError(
          Text.literal(String.format("Mindiscord command cooldown %.1fs", remaining / 1000.0)));
      return false;
    }
    COOLDOWNS.put(key, now);
    return true;
  }

  static void logCommand(MinDiscordRuntime runtime, String command, boolean ok, String message) {
    runtime.bridge().logLedger(
        "command",
        ok,
        ok ? "OK" : "ERROR",
        UUID.randomUUID().toString(),
        "command",
        command,
        0,
        0);
  }
}
