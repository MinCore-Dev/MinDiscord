
# MinDiscord Developer Guide (for Plugin Authors)

This guide shows how your plugin can post messages to Discord through **MinDiscord** without handling secrets or HTTP.

## 1) Add a dependency

Declare a dependency on `mindiscord` in your `fabric.mod.json` (hard dependency) **or** check availability at runtime.

Hard dependency:
```json
"depends": { "mindiscord": ">=0.3.1" }
```

Optional at runtime:
```java
var busOpt = dev.mindiscord.api.MinDiscordApi.bus();
if (busOpt.isEmpty()) { /* degrade gracefully */ }
```

## 2) Get the bus and send messages

### Simple text
```java
dev.mindiscord.api.MinDiscordApi.bus().ifPresent(bus ->
  bus.send("eventAnnouncements", "🗓️ New event: **PvP Cup** — 1v1 bracket. Best of 3.")
);
```

### Full embed
```java
var embed = new dev.mindiscord.api.Embed();
embed.title = "🏆 Winners — PvP Cup";
embed.description = "**#1 Notch** (+5000)\n**#2 Alex** (+3000)\n**#3 Steve** (+1500)";
embed.color = 0xFFAA00;
dev.mindiscord.api.MinDiscordApi.bus().ifPresent(bus -> bus.send("eventWinners", embed));
```

### Custom webhook payload
```java
var msg = new dev.mindiscord.api.WebhookMessage();
msg.content = "✨ Rare drop: **Alex** got **Totem of True RNG** (mythic)";
dev.mindiscord.api.MinDiscordApi.bus().ifPresent(bus -> bus.send("rareDrops", msg));
```

## 3) Routes, fallbacks & rate limits

- Ask the server owner which routes exist (e.g., `eventAnnouncements`, `eventStarts`, `eventWinners`, `rareDrops`).
- Routes may point at fixed URLs or `env:VARNAME` placeholders. If the environment variable is missing you will receive
  `BAD_ROUTE` (nothing is sent).
- Unknown routes fall back to `default` (when configured) and produce `BAD_ROUTE_FALLBACK`. The payload is delivered via the
  default route so you can log and notify ops.
- MinDiscord maintains a per-route token bucket. Bursts over the configured capacity are queued until tokens refill; if the
  queue is full your send completes with `QUEUE_FULL`.

## 4) Handling results & retries

```java
bus.send("eventAnnouncements", "hello").thenAccept(result -> {
  if (!result.ok()) {
    logger.warn("Discord send failed: {} - {}", result.code(), result.message());
  }
});
```

Possible codes: `OK`, `BAD_ROUTE_FALLBACK`, `BAD_ROUTE`, `BAD_PAYLOAD`, `QUEUE_FULL`, `DISCORD_429`, `DISCORD_5XX`,
`NETWORK_IO`, `GIVE_UP`.

- `SendResult.requestId()` is a UUID; ops can correlate it with MinCore ledger entries (`idemKey = "send:" + requestId`).
- Retries are handled for you. After `maxAttempts` MinDiscord gives up with `GIVE_UP` and includes the last failure reason in
  the message.

## 5) Threading

All sends are asynchronous and off the server main thread. You can call MinDiscord from anywhere; do not block waiting for the future unless you’re on a worker thread.

## 6) Payload limits & tips

- Keep `content` ≤ 2000 chars.
- Use embeds for rich formatting; stay within Discord’s limits.
- Avoid `@everyone`/role pings unless the server owner requested it. Configure mentions in MinDiscord routing if needed.
- Max 10 embeds per message, 25 fields per embed, field names ≤256 chars, values ≤1024 chars, footer text ≤2048 chars,
  author names ≤256 chars. MinDiscord validates these limits and returns `BAD_PAYLOAD` if exceeded.


## Dependency Notes
Your plugin depends on **MinDiscord** only. **Do NOT include MinCore** in your plugin; server owners or modpacks will supply MinCore automatically via Modrinth and the loader dependency metadata.
