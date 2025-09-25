
# MinDiscord

A tiny, ops-friendly Discord **webhook** announcer for Fabric servers. Plugins call MinDiscord’s API to post messages; no Discord libs, secrets, or HTTP code in your plugins.

## Features
- Webhook-only transport (no JDA), tiny runtime footprint
- Shared **AnnounceBus** API for every plugin – no token sharing or HTTP boilerplate
- Hot-reloadable routing via `mindiscord.json5`, including `env:` secrets and per-feature toggles
- Bounded queue with selectable overflow policy + token-bucket rate limiting per route
- Exponential retries with 429 `Retry-After` handling and jittered backoff
- **MinCore** ledger integration (`mindiscord` addon/op=`announce`) and optional per-route stats table
- Operator commands: `/mindiscord routes`, `/mindiscord test`, `/mindiscord diag`
- LuckPerms-first permission gateway (MinCore → LuckPerms → Fabric Permissions → vanilla OP)

## Requirements
- Fabric 1.21.8 (server), Java 21
- **MinCore >= 0.2.0**

## Install
1. Drop MinDiscord and MinCore into `mods/`.
2. Start once to generate `config/mindiscord.json5.example`.
3. Fill in webhooks in `routes { ... }` (you can use `env:MINDISCORD_WEBHOOK_*`).
4. `/mindiscord test eventAnnouncements "Hello from MinDiscord"`.

## Config (example)
```json5
{
  core: {
    enabled: true,
    redactUrlsInCommands: true,
    hotReload: true
  },
  routes: {
    default: "env:DISCORD_WEBHOOK_DEFAULT",
    eventAnnouncements: "env:DISCORD_WEBHOOK_EVENTS",
    rareDrops: "https://discord.com/api/webhooks/RARE/DROPS"
  },
  announce: {
    enabled: true,
    allowFallbackToDefault: true,
    allowedRoutes: ["default", "eventAnnouncements", "rareDrops"]
  },
  rateLimit: {
    perRoute: {
      default: { tokensPerMinute: 20, burst: 10 },
      rareDrops: { tokensPerMinute: 6, burst: 3 }
    },
    overflowPolicy: "dropOldest"
  },
  queue: { capacity: 512, workerThreads: 1 },
  transport: { connectTimeoutMs: 3000, readTimeoutMs: 5000, maxAttempts: 4 },
  commands: {
    routes: { enabled: true },
    test: { enabled: true },
    diag: { enabled: true }
  },
  permissions: { admin: "mindiscord.admin" }
}
```

### Routing, toggles & hot reload
- `mindiscord.json5` is watched at runtime – edits are applied without rebooting unless
  both the previous and new configs set `core.hotReload=false`.
- `core.enabled=false` returns `DISABLED` for all commands and sends.
- `announce.enabled=false` blocks announcement sends; `announce.allowedRoutes` restricts the set of
  valid route names (others return `ROUTE_DISABLED`). When `allowFallbackToDefault=true`, unknown but
  allowed routes fall back to `default` and surface `BAD_ROUTE_FALLBACK`.
- Values starting with `env:` (e.g. `env:DISCORD_WEBHOOK_EVENTS`) are resolved from the server
  environment; missing variables result in `BAD_ROUTE` responses.
- `/mindiscord routes` respects `core.redactUrlsInCommands` when showing webhook URLs.

### Queue, workers & retries
- A single worker thread drains a bounded queue; overflow policy is configurable (`dropOldest`,
  `dropNewest`, `reject`).
- Rate limits are enforced per **resolved** route using a token bucket (`perRouteBurst` / `perRouteRefillPerSec`).
- HTTP 429 honours `Retry-After`; 5xx and network errors use exponential backoff with optional jitter.
- After `maxAttempts` the send completes with `GIVE_UP`. Every accepted send produces a MinCore ledger
  entry and optionally increments the `mindiscord_stats` table (if MinCore’s DB is available).

### Commands
| Command | Description |
| --- | --- |
| `/mindiscord routes` | Lists configured routes, showing `env:` status and redacting URLs when configured. |
| `/mindiscord test <route> <text>` | Asynchronously sends a one-line test message via the route (subject to toggles). |
| `/mindiscord diag` | Shows queue depth and the last success/failure per route. |

All commands are rate-limited (2 s per sender) and log to the MinCore ledger with reason `command`.

### Result codes
`SendResult.code` (and the ledger code) may be one of:

`OK`, `BAD_ROUTE_FALLBACK`, `BAD_ROUTE`, `ROUTE_DISABLED`, `BAD_PAYLOAD`, `QUEUE_FULL`, `DISCORD_429`,
`DISCORD_5XX`, `NETWORK_IO`, `DISABLED`, `GIVE_UP`.

### Ledger & optional stats
- Every accepted send logs to MinCore with addon `mindiscord`, op `announce`, and a compact
  `extraJson` payload describing the resolved route, requested route, payload size, and embed count.
- When MinCore’s extension database is available, `mindiscord_stats` (route/day counters) is
  auto-created and updated off-thread. Failures are silently ignored so Discord delivery is never
  blocked by the database.

## For Plugin Authors
See **DEVELOPER_GUIDE.md** for API usage, examples, and result codes.

## License
MIT (or your preferred license).


## Dependency on MinCore (Do NOT bundle)
MinDiscord **requires MinCore** at runtime, but you must **not** ship or bundle the MinCore JAR with MinDiscord.
- On Modrinth, declare MinCore as a dependency; Modrinth and modpacks will auto-download it.
- In `fabric.mod.json`, MinCore is specified under `depends` so the loader enforces presence.
- For development, you may use a Maven dependency (preferred) or a local flatDir repo, **but never publish a release containing MinCore**.


## Modrinth publishing (template)
This repo includes a Gradle configuration for the **Modrinth Minotaur** plugin.
- Set secrets in CI or `~/.gradle/gradle.properties`:
  - `modrinthToken=YOUR_TOKEN`
  - `modrinthProjectId=YOUR_MINDISCORD_PROJECT_ID`
  - `modrinthMinCoreId=YOUR_MINCORE_PROJECT_ID`
- Run `./gradlew build modrinth` to publish.
- MinCore is listed as a **required dependency** so Modrinth auto-resolves it. Do **not** bundle MinCore in this jar.
