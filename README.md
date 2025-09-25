
# MinDiscord

A tiny, ops-friendly Discord **webhook** announcer for Fabric servers. Plugins call MinDiscord’s API to post messages; no Discord libs, secrets, or HTTP code in your plugins.

## Features
- Webhook-only transport (no JDA), minimal footprint
- Shared **AnnounceBus** API for all plugins
- **Routing** via `mindiscord.json5` with hot reload
- Per-route rate limits and simple retries
- **MinCore** integration: ledger audit lines, optional stats
- Commands: `/mindiscord routes|test|diag`

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
  mode: "webhook",
  routes: {
    default:            "https://discord.com/api/webhooks/GGGG/HHH",
    eventAnnouncements: "env:MINDISCORD_WEBHOOK_ANNOUNCE",
    eventStarts:        "https://discord.com/api/webhooks/AAAA/BBB",
    eventWinners:       "https://discord.com/api/webhooks/CCCC/DDD",
    rareDrops:          "https://discord.com/api/webhooks/EEEE/FFF"
  },
  defaults: { username: "MinDiscord", avatarUrl: "" },
  queue: { maxSize: 2000, onOverflow: "dropOldest" },
  retry: { maxAttempts: 6, baseDelayMs: 500, maxDelayMs: 15000, jitter: true },
  ratelimit: { perRouteBurst: 10, perRouteRefillPerSec: 5 },
  log: { level: "INFO", json: false }
}
```

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
