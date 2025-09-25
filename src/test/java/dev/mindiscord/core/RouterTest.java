package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

public class RouterTest {

  @Test
  void resolvesDefaultWhenRouteNullOrBlank() {
    Config cfg = new Config();
    cfg.routes = new LinkedHashMap<>();
    cfg.routes.put("default", "http://example/default");
    Router r = new Router(cfg);
    assertEquals("http://example/default", r.resolve(null));
    assertEquals("http://example/default", r.resolve(""));
    assertEquals("http://example/default", r.resolve("   "));
  }

  @Test
  void fallsBackToDefaultWhenUnknownRoute() {
    Config cfg = new Config();
    cfg.routes = new LinkedHashMap<>();
    cfg.routes.put("default", "http://example/default");
    cfg.routes.put("known", "http://example/known");
    Router r = new Router(cfg);
    assertEquals("http://example/default", r.resolve("unknown-route"));
    assertEquals("http://example/known", r.resolve("known"));
  }

  @Test
  void returnsNullWhenUnknownAndNoDefault() {
    Config cfg = new Config();
    cfg.routes = new LinkedHashMap<>();
    cfg.routes.put("known", "http://example/known");
    Router r = new Router(cfg);
    assertNull(r.resolve("unknown"));
  }
}
