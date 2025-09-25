package dev.mindiscord.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable runtime configuration for MinDiscord. */
public final class Config {
  private final Core core;
  private final Map<String, RouteDefinition> routes;
  private final Defaults defaults;
  private final Announce announce;
  private final Queue queue;
  private final Transport transport;
  private final RateLimit rateLimit;
  private final Commands commands;
  private final Permissions permissions;

  private Config(
      Core core,
      Map<String, RouteDefinition> routes,
      Defaults defaults,
      Announce announce,
      Queue queue,
      Transport transport,
      RateLimit rateLimit,
      Commands commands,
      Permissions permissions) {
    this.core = core;
    this.routes = routes;
    this.defaults = defaults;
    this.announce = announce;
    this.queue = queue;
    this.transport = transport;
    this.rateLimit = rateLimit;
    this.commands = commands;
    this.permissions = permissions;
  }

  public Core core() {
    return core;
  }

  public Map<String, RouteDefinition> routes() {
    return routes;
  }

  public Defaults defaults() {
    return defaults;
  }

  public Announce announce() {
    return announce;
  }

  public Queue queue() {
    return queue;
  }

  public Transport transport() {
    return transport;
  }

  public RateLimit rateLimit() {
    return rateLimit;
  }

  public Commands commands() {
    return commands;
  }

  public Permissions permissions() {
    return permissions;
  }

  public List<RouteDefinition> orderedRoutes() {
    return new ArrayList<>(routes.values());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Config defaultConfig() {
    return builder().build();
  }

  public static final class Builder {
    private Core core = Core.DEFAULTS;
    private final Map<String, RouteDefinition> routes = new LinkedHashMap<>();
    private Defaults defaults = Defaults.DEFAULTS;
    private Announce announce = Announce.DEFAULTS;
    private Queue queue = Queue.DEFAULTS;
    private Transport transport = Transport.DEFAULTS;
    private RateLimit rateLimit = RateLimit.DEFAULTS;
    private Commands commands = Commands.DEFAULTS;
    private Permissions permissions = Permissions.DEFAULTS;

    public Builder core(Core core) {
      this.core = Objects.requireNonNull(core, "core");
      return this;
    }

    public Builder putRoute(String name, String target) {
      routes.put(name, RouteDefinition.of(name, target));
      return this;
    }

    public Builder routes(Map<String, String> definitions) {
      routes.clear();
      definitions.forEach(this::putRoute);
      return this;
    }

    public Builder defaults(Defaults defaults) {
      this.defaults = Objects.requireNonNull(defaults, "defaults");
      return this;
    }

    public Builder announce(Announce announce) {
      this.announce = Objects.requireNonNull(announce, "announce");
      return this;
    }

    public Builder queue(Queue queue) {
      this.queue = Objects.requireNonNull(queue, "queue");
      return this;
    }

    public Builder transport(Transport transport) {
      this.transport = Objects.requireNonNull(transport, "transport");
      return this;
    }

    public Builder rateLimit(RateLimit rateLimit) {
      this.rateLimit = Objects.requireNonNull(rateLimit, "rateLimit");
      return this;
    }

    public Builder commands(Commands commands) {
      this.commands = Objects.requireNonNull(commands, "commands");
      return this;
    }

    public Builder permissions(Permissions permissions) {
      this.permissions = Objects.requireNonNull(permissions, "permissions");
      return this;
    }

    public Config build() {
      Map<String, RouteDefinition> copy = Map.copyOf(routes);
      return new Config(
          core,
          copy,
          defaults,
          announce.ensureDefaultsPresent(copy.keySet()),
          queue,
          transport,
          rateLimit.ensureDefaultsPresent(copy.keySet()),
          commands,
          permissions);
    }
  }

  public static final class Core {
    static final Core DEFAULTS = new Core(true, true, true);
    private final boolean enabled;
    private final boolean redactUrlsInCommands;
    private final boolean hotReload;

    public Core(boolean enabled, boolean redactUrlsInCommands, boolean hotReload) {
      this.enabled = enabled;
      this.redactUrlsInCommands = redactUrlsInCommands;
      this.hotReload = hotReload;
    }

    public boolean enabled() {
      return enabled;
    }

    public boolean redactUrlsInCommands() {
      return redactUrlsInCommands;
    }

    public boolean hotReload() {
      return hotReload;
    }
  }

  public static final class RouteDefinition {
    private final String name;
    private final String rawTarget;
    private final boolean environment;
    private final String envVariable;

    private RouteDefinition(String name, String rawTarget, boolean environment, String envVariable) {
      this.name = name;
      this.rawTarget = rawTarget;
      this.environment = environment;
      this.envVariable = envVariable;
    }

    public String name() {
      return name;
    }

    public String rawTarget() {
      return rawTarget;
    }

    public boolean environment() {
      return environment;
    }

    public String envVariable() {
      return envVariable;
    }

    public static RouteDefinition of(String name, String target) {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(target, "target");
      String trimmed = target.trim();
      if (trimmed.isEmpty()) {
        throw new IllegalArgumentException("Route target may not be blank");
      }
      if (trimmed.regionMatches(true, 0, "env:", 0, 4)) {
        String env = trimmed.substring(4).trim();
        if (env.isEmpty()) {
          throw new IllegalArgumentException("env: routes must specify a variable name");
        }
        return new RouteDefinition(name, trimmed, true, env);
      }
      return new RouteDefinition(name, trimmed, false, null);
    }
  }

  public static final class Defaults {
    static final Defaults DEFAULTS = new Defaults("MinDiscord", "");
    private final String username;
    private final String avatarUrl;

    public Defaults(String username, String avatarUrl) {
      this.username = Objects.requireNonNullElse(username, "MinDiscord");
      this.avatarUrl = Objects.requireNonNullElse(avatarUrl, "");
    }

    public String username() {
      return username;
    }

    public String avatarUrl() {
      return avatarUrl;
    }
  }

  public static final class Announce {
    static final Announce DEFAULTS = new Announce(true, true, List.of("default"));
    private final boolean enabled;
    private final boolean allowFallbackToDefault;
    private final List<String> allowedRoutes;

    public Announce(boolean enabled, boolean allowFallbackToDefault, List<String> allowedRoutes) {
      this.enabled = enabled;
      this.allowFallbackToDefault = allowFallbackToDefault;
      this.allowedRoutes = List.copyOf(Objects.requireNonNullElseGet(allowedRoutes, List::of));
    }

    private Announce ensureDefaultsPresent(Set<String> routes) {
      List<String> normalized = new ArrayList<>();
      for (String entry : allowedRoutes) {
        if (entry == null) {
          continue;
        }
        String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          normalized.add(trimmed);
        }
      }
      if (normalized.isEmpty()) {
        normalized.add("default");
      }
      if (!normalized.contains("default") && routes.contains("default")) {
        normalized.add("default");
      }
      return new Announce(enabled, allowFallbackToDefault, List.copyOf(normalized));
    }

    public boolean enabled() {
      return enabled;
    }

    public boolean allowFallbackToDefault() {
      return allowFallbackToDefault;
    }

    public List<String> allowedRoutes() {
      return allowedRoutes;
    }

    public boolean isRouteAllowed(String route) {
      if (allowedRoutes.isEmpty()) {
        return true;
      }
      String normalized = route == null || route.isBlank() ? "default" : route;
      return allowedRoutes.contains(normalized);
    }
  }

  public enum QueueOverflowPolicy {
    DROP_OLDEST,
    DROP_NEWEST,
    REJECT;

    static QueueOverflowPolicy from(String raw) {
      if (raw == null || raw.isBlank()) {
        return DROP_OLDEST;
      }
      return switch (raw.toLowerCase(Locale.ROOT)) {
        case "dropoldest" -> DROP_OLDEST;
        case "dropnewest" -> DROP_NEWEST;
        case "reject" -> REJECT;
        default -> throw new IllegalArgumentException("Unknown queue policy: " + raw);
      };
    }
  }

  public static final class Queue {
    static final Queue DEFAULTS = new Queue(512, 1, QueueOverflowPolicy.DROP_OLDEST);
    private final int capacity;
    private final int workerThreads;
    private final QueueOverflowPolicy overflowPolicy;

    public Queue(int capacity, int workerThreads, QueueOverflowPolicy overflowPolicy) {
      if (capacity <= 0) {
        throw new IllegalArgumentException("queue.capacity must be > 0");
      }
      if (workerThreads <= 0) {
        throw new IllegalArgumentException("queue.workerThreads must be > 0");
      }
      this.capacity = capacity;
      this.workerThreads = workerThreads;
      this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy");
    }

    public int capacity() {
      return capacity;
    }

    public int workerThreads() {
      return workerThreads;
    }

    public QueueOverflowPolicy overflowPolicy() {
      return overflowPolicy;
    }

    public Queue withOverflowPolicy(QueueOverflowPolicy policy) {
      return new Queue(capacity, workerThreads, policy);
    }
  }

  public static final class Transport {
    static final Transport DEFAULTS = new Transport(3000, 5000, 4);
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int maxAttempts;

    public Transport(int connectTimeoutMs, int readTimeoutMs, int maxAttempts) {
      if (connectTimeoutMs <= 0) {
        throw new IllegalArgumentException("transport.connectTimeoutMs must be > 0");
      }
      if (readTimeoutMs <= 0) {
        throw new IllegalArgumentException("transport.readTimeoutMs must be > 0");
      }
      if (maxAttempts <= 0) {
        throw new IllegalArgumentException("transport.maxAttempts must be > 0");
      }
      this.connectTimeoutMs = connectTimeoutMs;
      this.readTimeoutMs = readTimeoutMs;
      this.maxAttempts = maxAttempts;
    }

    public int connectTimeoutMs() {
      return connectTimeoutMs;
    }

    public int readTimeoutMs() {
      return readTimeoutMs;
    }

    public int maxAttempts() {
      return maxAttempts;
    }
  }

  public static final class RateLimit {
    static final RateLimit DEFAULTS =
        new RateLimit(Map.of("default", Rule.DEFAULT), QueueOverflowPolicy.DROP_OLDEST);
    private final Map<String, Rule> perRoute;
    private final QueueOverflowPolicy overflowPolicy;

    public RateLimit(Map<String, Rule> perRoute, QueueOverflowPolicy overflowPolicy) {
      if (perRoute == null || perRoute.isEmpty()) {
        throw new IllegalArgumentException("rateLimit.perRoute must contain at least one route");
      }
      this.perRoute = Map.copyOf(perRoute);
      this.overflowPolicy = Objects.requireNonNull(overflowPolicy, "overflowPolicy");
    }

    private RateLimit ensureDefaultsPresent(Set<String> routes) {
      Map<String, Rule> copy = new LinkedHashMap<>(perRoute);
      copy.putIfAbsent("default", Rule.DEFAULT);
      for (String route : routes) {
        copy.putIfAbsent(route, Rule.DEFAULT);
      }
      return new RateLimit(copy, overflowPolicy);
    }

    public Map<String, Rule> perRoute() {
      return perRoute;
    }

    public QueueOverflowPolicy overflowPolicy() {
      return overflowPolicy;
    }

    public Rule ruleFor(String route) {
      String normalized = route == null || route.isBlank() ? "default" : route;
      Rule rule = perRoute.get(normalized);
      if (rule == null) {
        rule = perRoute.get("default");
      }
      return rule != null ? rule : Rule.DEFAULT;
    }

    public record Rule(int tokensPerMinute, int burst) {
      static final Rule DEFAULT = new Rule(20, 10);

      public Rule {
        if (tokensPerMinute <= 0) {
          throw new IllegalArgumentException("tokensPerMinute must be > 0");
        }
        if (burst <= 0) {
          throw new IllegalArgumentException("burst must be > 0");
        }
      }

      public double refillTokensPerSecond() {
        return tokensPerMinute / 60.0;
      }
    }
  }

  public static final class Commands {
    static final Commands DEFAULTS = new Commands(true, true, true);
    private final boolean routesEnabled;
    private final boolean testEnabled;
    private final boolean diagEnabled;

    public Commands(boolean routesEnabled, boolean testEnabled, boolean diagEnabled) {
      this.routesEnabled = routesEnabled;
      this.testEnabled = testEnabled;
      this.diagEnabled = diagEnabled;
    }

    public boolean routesEnabled() {
      return routesEnabled;
    }

    public boolean testEnabled() {
      return testEnabled;
    }

    public boolean diagEnabled() {
      return diagEnabled;
    }
  }

  public static final class Permissions {
    static final Permissions DEFAULTS = new Permissions("mindiscord.admin");
    private final String admin;

    public Permissions(String admin) {
      this.admin = admin == null || admin.isBlank() ? "mindiscord.admin" : admin;
    }

    public String admin() {
      return admin;
    }
  }

  public static Config fromRaw(Raw raw) {
    if (raw == null) {
      return defaultConfig();
    }
    Builder builder = builder();
    builder.core(raw.core != null ? raw.core.toCore() : Core.DEFAULTS);
    builder.defaults(raw.defaults != null ? raw.defaults.toDefaults() : Defaults.DEFAULTS);
    Announce announce = raw.announce != null ? raw.announce.toAnnounce() : Announce.DEFAULTS;
    if (raw.routes != null) {
      raw.routes.forEach(builder::putRoute);
    }
    builder.announce(announce);
    Queue queue = raw.queue != null ? raw.queue.toQueue() : Queue.DEFAULTS;
    RateLimit rateLimit = raw.rateLimit != null ? raw.rateLimit.toRateLimit() : RateLimit.DEFAULTS;
    QueueOverflowPolicy overflowPolicy = rateLimit.overflowPolicy();
    if (raw.rateLimit != null && raw.rateLimit.overflowPolicy != null) {
      overflowPolicy = QueueOverflowPolicy.from(raw.rateLimit.overflowPolicy);
    } else if (raw.queue != null && raw.queue.overflowPolicy != null) {
      overflowPolicy = QueueOverflowPolicy.from(raw.queue.overflowPolicy);
    }
    builder.queue(queue.withOverflowPolicy(overflowPolicy));
    builder.rateLimit(rateLimit);
    builder.transport(raw.transport != null ? raw.transport.toTransport() : Transport.DEFAULTS);
    builder.commands(raw.commands != null ? raw.commands.toCommands() : Commands.DEFAULTS);
    builder.permissions(raw.permissions != null ? raw.permissions.toPermissions() : Permissions.DEFAULTS);
    return builder.build();
  }

  public static final class Raw {
    public RawCore core;
    public Map<String, String> routes;
    public RawDefaults defaults;
    public RawAnnounce announce;
    public RawQueue queue;
    public RawTransport transport;
    public RawRateLimit rateLimit;
    public RawCommands commands;
    public RawPermissions permissions;
  }

  public static final class RawCore {
    public Boolean enabled;
    public Boolean redactUrlsInCommands;
    public Boolean hotReload;

    Core toCore() {
      boolean enabledValue = enabled != null ? enabled : Core.DEFAULTS.enabled();
      boolean redact =
          redactUrlsInCommands != null ? redactUrlsInCommands : Core.DEFAULTS.redactUrlsInCommands();
      boolean hotReloadValue = hotReload != null ? hotReload : Core.DEFAULTS.hotReload();
      return new Core(enabledValue, redact, hotReloadValue);
    }
  }

  public static final class RawDefaults {
    public String username;
    public String avatarUrl;

    Defaults toDefaults() {
      return new Defaults(username, avatarUrl);
    }
  }

  public static final class RawAnnounce {
    public Boolean enabled;
    public Boolean allowFallbackToDefault;
    public List<String> allowedRoutes;

    Announce toAnnounce() {
      boolean enabledValue = enabled != null ? enabled : Announce.DEFAULTS.enabled();
      boolean fallback =
          allowFallbackToDefault != null
              ? allowFallbackToDefault
              : Announce.DEFAULTS.allowFallbackToDefault();
      List<String> routesList = allowedRoutes != null ? allowedRoutes : Announce.DEFAULTS.allowedRoutes();
      return new Announce(enabledValue, fallback, routesList);
    }
  }

  public static final class RawQueue {
    public Integer capacity;
    public Integer workerThreads;
    public String overflowPolicy;

    Queue toQueue() {
      int cap = capacity != null ? capacity : Queue.DEFAULTS.capacity();
      int workers = workerThreads != null ? workerThreads : Queue.DEFAULTS.workerThreads();
      QueueOverflowPolicy policy =
          overflowPolicy != null
              ? QueueOverflowPolicy.from(overflowPolicy)
              : Queue.DEFAULTS.overflowPolicy();
      return new Queue(cap, workers, policy);
    }
  }

  public static final class RawTransport {
    public Integer connectTimeoutMs;
    public Integer readTimeoutMs;
    public Integer maxAttempts;

    Transport toTransport() {
      int connect =
          connectTimeoutMs != null ? connectTimeoutMs : Transport.DEFAULTS.connectTimeoutMs();
      int read = readTimeoutMs != null ? readTimeoutMs : Transport.DEFAULTS.readTimeoutMs();
      int attempts = maxAttempts != null ? maxAttempts : Transport.DEFAULTS.maxAttempts();
      return new Transport(connect, read, attempts);
    }
  }

  public static final class RawRateLimit {
    public Map<String, RawRateLimitRule> perRoute;
    public String overflowPolicy;

    RateLimit toRateLimit() {
      Map<String, RateLimit.Rule> map = new LinkedHashMap<>();
      if (perRoute != null) {
        for (Map.Entry<String, RawRateLimitRule> entry : perRoute.entrySet()) {
          map.put(entry.getKey(), entry.getValue().toRule(entry.getKey()));
        }
      }
      if (!map.containsKey("default")) {
        map.put("default", RateLimit.Rule.DEFAULT);
      }
      QueueOverflowPolicy policy =
          overflowPolicy != null
              ? QueueOverflowPolicy.from(overflowPolicy)
              : RateLimit.DEFAULTS.overflowPolicy();
      return new RateLimit(map, policy);
    }
  }

  public static final class RawRateLimitRule {
    public Integer tokensPerMinute;
    public Integer burst;

    RateLimit.Rule toRule(String name) {
      int tokens =
          tokensPerMinute != null ? tokensPerMinute : RateLimit.Rule.DEFAULT.tokensPerMinute();
      int burstValue = burst != null ? burst : RateLimit.Rule.DEFAULT.burst();
      try {
        return new RateLimit.Rule(tokens, burstValue);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid rate limit for route " + name + ": " + e.getMessage(), e);
      }
    }
  }

  public static final class RawCommands {
    public RawToggle routes;
    public RawToggle test;
    public RawToggle diag;

    Commands toCommands() {
      boolean routesEnabled = routes != null ? routes.enabled() : Commands.DEFAULTS.routesEnabled();
      boolean testEnabled = test != null ? test.enabled() : Commands.DEFAULTS.testEnabled();
      boolean diagEnabled = diag != null ? diag.enabled() : Commands.DEFAULTS.diagEnabled();
      return new Commands(routesEnabled, testEnabled, diagEnabled);
    }
  }

  public static final class RawToggle {
    public Boolean enabled;

    boolean enabled() {
      return enabled != null ? enabled : true;
    }
  }

  public static final class RawPermissions {
    public String admin;

    Permissions toPermissions() {
      return new Permissions(admin);
    }
  }
}
