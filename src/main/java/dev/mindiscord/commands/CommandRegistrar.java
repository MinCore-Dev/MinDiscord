package dev.mindiscord.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.mindiscord.core.Config;
import dev.mindiscord.core.MinDiscordRuntime;
import dev.mindiscord.perms.Perms;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class CommandRegistrar {
  private static final long COOLDOWN_MS = 2000;
  private static final Map<Object, Long> COOLDOWNS = new ConcurrentHashMap<>();

  private CommandRegistrar() {}

  public static void registerAll(MinDiscordRuntime runtime) {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      LiteralArgumentBuilder<ServerCommandSource> root =
          CommandManager.literal("mindiscord")
              .requires(src -> hasAdminPermission(runtime, src));
      MindiscordRoutesCommand.register(root, runtime);
      MindiscordTestCommand.register(root, runtime);
      MindiscordDiagCommand.register(root, runtime);
      dispatcher.register(root);
    });
  }

  private static boolean hasAdminPermission(MinDiscordRuntime runtime, ServerCommandSource source) {
    Config config = runtime.config();
    String node = config.permissions().admin();
    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
      return source.hasPermissionLevel(2);
    }
    return Perms.check(player, node, 2);
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
