package dev.mindiscord.core;

import dev.mindiscord.api.AnnounceBus;
import java.util.List;
import java.util.Objects;
public final class MinDiscordRuntime implements AutoCloseable {
  private static volatile MinDiscordRuntime INSTANCE;

  private final MinCoreBridge bridge = new MinCoreBridge();
  private final ConfigLoader configLoader;
  private final Router router = new Router();
  private final DispatchQueue queue = new DispatchQueue();
  private final WebhookTransport transport = new WebhookTransport();
  private final RateLimiterRegistry rateLimiter = new RateLimiterRegistry();
  private final StatsStore statsStore = new StatsStore(bridge);
  private final TimeSource timeSource = new SystemTimeSource();
  private final Sleeper sleeper = new ThreadSleeper();
  private final AnnounceBusImpl bus;

  private MinDiscordRuntime() {
    this.configLoader = new ConfigLoader(bridge);
    Config initial = configLoader.current();
    this.bus =
        new AnnounceBusImpl(
            router,
            queue,
            transport,
            rateLimiter,
            statsStore,
            bridge,
            timeSource,
            sleeper,
            initial);
    this.configLoader.addListener(bus::applyConfig);
  }

  public static MinDiscordRuntime init() {
    MinDiscordRuntime runtime = new MinDiscordRuntime();
    INSTANCE = runtime;
    return runtime;
  }

  public static MinDiscordRuntime instance() {
    return Objects.requireNonNull(INSTANCE, "MinDiscord runtime not initialized");
  }

  public AnnounceBus bus() { return bus; }

  public AnnounceBusImpl.DiagnosticsSnapshot diagnostics() { return bus.diagnostics(); }

  public List<Router.RouteInfo> routes() { return bus.routes(); }

  public MinCoreBridge bridge() { return bridge; }

  @Override
  public void close() {
    bus.close();
    configLoader.close();
  }

}
