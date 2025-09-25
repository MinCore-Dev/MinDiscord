package dev.mindiscord.core;

import java.util.Map;
import java.util.Objects;

public final class Router {
  private volatile Map<String,String> routes;
  public Router(Config cfg) { this.routes = cfg.routes; }
  public String resolve(String route) {
    if (route == null || route.isBlank()) route = "default";
    String url = routes.get(route);
    if (url == null) {
      // Fallback to default if present
      return routes.get("default");
    }
    if (url.startsWith("env:")) {
      String env = url.substring(4);
      String v = System.getenv(env);
      return v;
    }
    return url;
  }
}
