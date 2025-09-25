package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class RouterTest {

  @Test
  void resolvesDefaultWhenRouteNullOrBlank() {
    Config cfg =
        Config.builder().putRoute("default", "http://example/default").build();
    Router r = new Router();
    r.update(cfg);
    assertEquals("http://example/default", r.resolve(null).url());
    assertEquals("http://example/default", r.resolve("").url());
    assertEquals("http://example/default", r.resolve("   ").url());
  }

  @Test
  void fallsBackToDefaultWhenUnknownRoute() {
    Config cfg =
        Config.builder()
            .putRoute("default", "http://example/default")
            .putRoute("known", "http://example/known")
            .build();
    Router r = new Router();
    r.update(cfg);
    assertEquals("http://example/default", r.resolve("unknown-route").url());
    assertEquals("http://example/known", r.resolve("known").url());
    assertEquals(Router.Status.FALLBACK, r.resolve("unknown-route").status());
  }

  @Test
  void returnsNullWhenUnknownAndNoDefault() {
    Config cfg = Config.builder().putRoute("known", "http://example/known").build();
    Router r = new Router();
    r.update(cfg);
    Router.RouteResolution res = r.resolve("unknown");
    assertNull(res.url());
    assertEquals(Router.Status.NO_ROUTE, res.status());
  }
}
