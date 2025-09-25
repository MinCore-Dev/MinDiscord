
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

## 3) Routes & fallbacks

- Ask the server owner which routes exist (e.g., `eventAnnouncements`, `eventStarts`, `eventWinners`, `rareDrops`).  
- If you pass an unknown route and the server has a `default`, MinDiscord will **fallback** to `default` and your `SendResult.code` will be `BAD_ROUTE_FALLBACK`.

## 4) Handling results

```java
bus.send("eventAnnouncements", "hello").thenAccept(result -> {
  if (!result.ok()) {
    logger.warn("Discord send failed: {} - {}", result.code(), result.message());
  }
});
```

Possible codes: `OK`, `BAD_ROUTE_FALLBACK`, `QUEUE_FULL`, `BAD_PAYLOAD`, `BAD_ROUTE`, `DISCORD_429`, `DISCORD_5XX`, `NETWORK_IO`, `GIVE_UP`.

## 5) Threading

All sends are asynchronous and off the server main thread. You can call MinDiscord from anywhere; do not block waiting for the future unless you’re on a worker thread.

## 6) Tips

- Keep `content` ≤ 2000 chars.
- Use embeds for rich formatting; stay within Discord’s limits.
- Avoid `@everyone`/role pings unless the server owner requested it. Configure mentions in MinDiscord routing if needed.


## Dependency Notes
Your plugin depends on **MinDiscord** only. **Do NOT include MinCore** in your plugin; server owners or modpacks will supply MinCore automatically via Modrinth and the loader dependency metadata.
