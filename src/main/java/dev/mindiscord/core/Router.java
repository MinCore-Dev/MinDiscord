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

  public List<RouteInfo> snapshot() { return table.get().snapshot(); }

  private record RouteTable(
      Map<String, Config.RouteDefinition> routes,
      Config.RouteDefinition defaultRoute) {

    static RouteTable empty() {
      return new RouteTable(Map.of(), null);
    }

    static RouteTable from(Config config) {
      Map<String, Config.RouteDefinition> copy = new LinkedHashMap<>(config.routes());
      return new RouteTable(copy, copy.get("default"));
    }

    RouteResolution resolve(String requested) {
      String normalized = requested;
      if (normalized == null || normalized.isBlank()) {
        normalized = "default";
      }
      Config.RouteDefinition target = routes.get(normalized);
      boolean fallback = false;
      if (target == null && defaultRoute != null) {
        target = defaultRoute;
        fallback = !"default".equals(normalized);
      }
      if (target == null) {
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
      String resolved = resolveTarget(target);
      if (resolved == null || resolved.isBlank()) {
        Status status = target.environment() ? Status.ENV_MISSING : Status.NO_ROUTE;
        return new RouteResolution(
            normalized,
            target.name(),
            null,
            status,
            target.environment(),
            target.envVariable(),
            target.rawTarget(),
            fallback);
      }
      return new RouteResolution(
          normalized,
          target.name(),
          resolved,
          fallback ? Status.FALLBACK : Status.OK,
          target.environment(),
          target.envVariable(),
          target.rawTarget(),
          fallback);
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
    public boolean ok() { return status == Status.OK || status == Status.FALLBACK; }
  }

  public enum Status { OK, FALLBACK, NO_ROUTE, ENV_MISSING }
}
