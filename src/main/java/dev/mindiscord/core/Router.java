package dev.mindiscord.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Resolves logical route names to webhook targets. */
public final class Router {
  private final AtomicReference<RouteTable> table = new AtomicReference<>(RouteTable.empty());

  public void update(Config config) {
    table.set(RouteTable.from(config));
  }

  public RouteResolution resolve(String requestedRoute) {
    return table.get().resolve(requestedRoute);
  }

  public List<RouteInfo> snapshot() {
    return table.get().snapshot();
  }

  private record RouteTable(
      Map<String, Config.RouteDefinition> routes,
      Config.RouteDefinition defaultRoute,
      boolean allowFallback) {

    static RouteTable empty() {
      return new RouteTable(Map.of(), null, false);
    }

    static RouteTable from(Config config) {
      Map<String, Config.RouteDefinition> copy = new LinkedHashMap<>(config.routes());
      return new RouteTable(copy, copy.get("default"), config.announce().allowFallbackToDefault());
    }

    RouteResolution resolve(String requested) {
      String normalized = requested;
      if (normalized == null || normalized.isBlank()) {
        normalized = "default";
      }
      Config.RouteDefinition direct = routes.get(normalized);
      if (direct != null) {
        String resolved = resolveTarget(direct);
        if (resolved != null && !resolved.isBlank()) {
          return success(normalized, direct, resolved, false);
        }
        if (allowFallback && !"default".equals(normalized)) {
        RouteResolution fallbackResolution = attemptFallback(normalized);
          if (fallbackResolution != null) {
            return fallbackResolution;
          }
        }
        return failure(normalized, direct);
      }
      if (allowFallback && !"default".equals(normalized)) {
        RouteResolution fallbackResolution = attemptFallback(normalized);
        if (fallbackResolution != null) {
          return fallbackResolution;
        }
      }
      return new RouteResolution(
          normalized,
          null,
          null,
          Status.NO_ROUTE,
          false,
          null,
          null,
          false);
    }

    private RouteResolution attemptFallback(String requested) {
      if (defaultRoute == null) {
        return null;
      }
      String resolved = resolveTarget(defaultRoute);
      if (resolved == null || resolved.isBlank()) {
        return null;
      }
      return success(requested, defaultRoute, resolved, true);
    }

    private static RouteResolution success(
        String requested, Config.RouteDefinition definition, String url, boolean fallback) {
      return new RouteResolution(
          requested,
          definition.name(),
          url,
          fallback ? Status.FALLBACK : Status.OK,
          definition.environment(),
          definition.envVariable(),
          definition.rawTarget(),
          fallback);
    }

    private static RouteResolution failure(String requested, Config.RouteDefinition definition) {
      boolean env = definition.environment();
      return new RouteResolution(
          requested,
          definition.name(),
          null,
          env ? Status.ENV_MISSING : Status.NO_ROUTE,
          env,
          definition.envVariable(),
          definition.rawTarget(),
          false);
    }

    private static String resolveTarget(Config.RouteDefinition def) {
      if (!def.environment()) {
        return def.rawTarget();
      }
      return System.getenv(def.envVariable());
    }

    List<RouteInfo> snapshot() {
      List<RouteInfo> list = new ArrayList<>();
      for (Config.RouteDefinition def : routes.values()) {
        String resolved = def.environment() ? System.getenv(def.envVariable()) : def.rawTarget();
        list.add(
            new RouteInfo(
                def.name(),
                def.rawTarget(),
                def.environment(),
                def.envVariable(),
                resolved != null && !resolved.isBlank()));
      }
      return list;
    }
  }

  public record RouteInfo(
      String name, String rawTarget, boolean environment, String envVariable, boolean available) {}

  public record RouteResolution(
      String requestedRoute,
      String resolvedRoute,
      String url,
      Status status,
      boolean environment,
      String envVariable,
      String rawTarget,
      boolean fallback) {
    public boolean ok() {
      return status == Status.OK || status == Status.FALLBACK;
    }
  }

  public enum Status {
    OK,
    FALLBACK,
    NO_ROUTE,
    ENV_MISSING
  }
}
