package dev.mindiscord.perms;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.network.ServerPlayerEntity;

/** Permission gateway that prefers MinCore when available, falling back to LuckPerms/Fabric/OP. */
public final class Perms {
  @FunctionalInterface
  interface OpFallback {
    boolean check(ServerPlayerEntity player, int level);
  }

  private static final AtomicReference<Method> MINCORE_METHOD = new AtomicReference<>();
  private static final AtomicBoolean MINCORE_UNAVAILABLE = new AtomicBoolean();
  private static final OpFallback DEFAULT_FALLBACK =
      (player, level) -> player != null && player.hasPermissionLevel(level);
  private static volatile OpFallback opFallback = DEFAULT_FALLBACK;

  private Perms() {}

  public static boolean check(ServerPlayerEntity player, String node, int opLevelFallback) {
    Method mincore = resolveMinCore();
    if (mincore != null) {
      try {
        Object result = mincore.invoke(null, player, node, opLevelFallback);
        if (result instanceof Boolean bool) {
          return bool;
        }
      } catch (ReflectiveOperationException e) {
        // fall back to local gateway when MinCore invocation fails
      }
    }
    Boolean luck = luckPermsCheck(player, node);
    if (luck != null) {
      return luck.booleanValue();
    }
    Boolean fabric = fabricPermissionsCheck(player, node, opLevelFallback);
    if (fabric != null) {
      return fabric.booleanValue();
    }
    return opFallback.check(player, opLevelFallback);
  }

  private static Method resolveMinCore() {
    Method cached = MINCORE_METHOD.get();
    if (cached != null || MINCORE_UNAVAILABLE.get()) {
      return cached;
    }
    try {
      Class<?> gateway = Class.forName("dev.mincore.perms.Perms");
      Method method = gateway.getMethod("check", ServerPlayerEntity.class, String.class, int.class);
      MINCORE_METHOD.compareAndSet(null, method);
      return method;
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      MINCORE_UNAVAILABLE.set(true);
      return null;
    }
  }

  private static Boolean luckPermsCheck(ServerPlayerEntity player, String node) {
    try {
      LuckPerms api = LuckPermsProvider.get();
      if (api == null) {
        return null;
      }
      User user = api.getUserManager().getUser(player.getUuid());
      if (user == null) {
        return null;
      }
      ContextManager contextManager = api.getContextManager();
      QueryOptions options = contextManager.getQueryOptions(player);
      if (options == null) {
        options = contextManager.getStaticQueryOptions();
      }
      if (options == null) {
        return null;
      }
      return user.getCachedData().getPermissionData(options).checkPermission(node).asBoolean();
    } catch (IllegalStateException | NoClassDefFoundError ignored) {
      return null;
    }
  }

  private static Boolean fabricPermissionsCheck(
      ServerPlayerEntity player, String node, int opLevelFallback) {
    try {
      Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
      Class<?> sourceClass =
          Class.forName("net.minecraft.server.command.ServerCommandSource", false, player.getClass().getClassLoader());
      Method method = permsClass.getMethod("check", sourceClass, String.class, int.class);
      Object source = player.getCommandSource();
      if (source == null) {
        return null;
      }
      Object result = method.invoke(null, source, node, opLevelFallback);
      return result instanceof Boolean ? (Boolean) result : null;
    } catch (ReflectiveOperationException | NoClassDefFoundError ignored) {
      return null;
    }
  }

  static void setFallbackForTesting(OpFallback fallback) {
    opFallback = fallback;
  }

  static void resetFallbackForTesting() {
    opFallback = DEFAULT_FALLBACK;
  }
}
