
# MinDiscord — AGENTS.md (Master Spec for Codex) — v0.3.1

This is the **single source of truth** for implementing **MinDiscord**. It’s written for autonomous agents and contractors.

- Build a tiny Fabric **server-side** mod that lets other plugins post **Discord webhook** messages through a shared service.
- **MinCore is required**. Use it for pooled DB access (optional stats), advisory locks, and ledger logging.
- **No JDA**; webhooks-only transport.
- Provide a **clean public API** (`AnnounceBus`) so other plugins pass raw content/embeds. Plugins do **not** hold secrets or HTTP clients.
- Include **hot-reloadable routing** via `mindiscord.json5`, per-route rate limiting, and safe fallbacks.
- Ship a **README.md** and a **DEVELOPER_GUIDE.md** so other agents can consume MinDiscord easily (see §9 Deliverables).

If any other doc disagrees with this file, **this file wins**.

---

## 1) Compatibility, Packaging & Declared Deps

- Minecraft (Fabric) **1.21.8**, server-only
- Java **21 LTS**
- Loom **1.11.x**
- Discord: **webhooks only** (no gateway, no JDA)
- DB: uses MinCore `ExtensionDatabase` only if optional stats enabled; otherwise stateless
- Mod id: `mindiscord`
- Public API pkg: `dev.mindiscord.api`

### fabric.mod.json (must declare MinCore hard dependency)
```json
{
  "schemaVersion": 1,
  "id": "mindiscord",
  "version": "0.3.1",
  "name": "MinDiscord",
  "environment": "server",
  "entrypoints": { "main": [ "dev.mindiscord.MinDiscordMod" ] },
  "depends": {
    "fabricloader": ">=0.17.2",
    "minecraft": "1.21.8",
    "fabric-api": "*",
    "mincore": ">=0.2.0"
  }
}
```

### Gradle (sketch)
```groovy
plugins { id 'java'; id 'fabric-loom' version '1.11-SNAPSHOT' }
group='dev.mindiscord'; version='0.3.1'
sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21
repositories { mavenCentral() }
dependencies {
  minecraft "com.mojang:minecraft:1.21.8"
  mappings "net.fabricmc:yarn:1.21.8+build.1:v2"
  modImplementation "net.fabricmc:fabric-loader:0.17.2"
  modImplementation "net.fabricmc.fabric-api:fabric-api:0.133.4+1.21.8"
  // MinCore provided at runtime (published or local JAR)
}
```

---

## 2) MinCore Integration (exhaustive)

Use `dev.mincore.api.MinCoreApi`:

```java
public final class MinCoreApi {
  public static dev.mincore.api.storage.ExtensionDatabase database();
  public static dev.mincore.api.Ledger ledger();
  // Players/Wallets/Attributes/Events exist but are not required here
}
```

**ExtensionDatabase APIs used**
```java
interface ExtensionDatabase {
  java.sql.Connection borrowConnection() throws java.sql.SQLException;
  boolean tryAdvisoryLock(String name);
  void releaseAdvisoryLock(String name);
  <T> T withRetry(SQLSupplier<T> action) throws java.sql.SQLException;
}
```

**Ledger usage for each accepted send**
- addonId: `"mindiscord"`
- op: `"announce"`
- from/to/amount: `null, null, 0`
- reason: **resolved route name** (e.g. `eventWinners` or `default`)
- ok: true/false
- code: result code (see §8)
- idemScope: `"mindiscord"`
- idemKey: `"send:" + requestId` (UUID string per send)
- extraJson (≤512B): `{"route":"eventWinners","routeRequested":"<if fallback>","payloadBytes":1234,"embeds":1}`

Acquire advisory lock `"mindiscord:init"` only for one-time setup (creating example config). Setup is idempotent; continue if not acquired.

All JDBC work occurs off the main thread.

---

## 3) Public API (what other plugins call)

**Discovery**
```java
package dev.mindiscord.api;
public final class MinDiscordApi {
  public static java.util.Optional<AnnounceBus> bus();
}
```

**Facade**
```java
package dev.mindiscord.api;

import java.util.concurrent.CompletableFuture;

public interface AnnounceBus {
  CompletableFuture<SendResult> send(String route, String content);
  CompletableFuture<SendResult> send(String route, WebhookMessage msg);
  CompletableFuture<SendResult> send(String route, Embed embed);
}
public record SendResult(boolean ok, String code, String message, String requestId) {}
```

**Payload model (validate to Discord limits)**
```java
public final class WebhookMessage {
  public String username;     // optional
  public String avatarUrl;    // optional
  public String content;      // <= 2000 chars
  public java.util.List<Embed> embeds; // up to 10
  public AllowedMentions allowedMentions; // optional
}
public final class AllowedMentions {
  public boolean parseRoles, parseUsers, parseEveryone;
  public java.util.List<String> roles, users;
}
public final class Embed {
  public String title, description, url;
  public Integer color; // 0xRRGGBB
  public Author author; public Footer footer; public Thumbnail thumbnail; public Image image;
  public java.util.List<Field> fields;
  public static final class Author { public String name, url, iconUrl; }
  public static final class Footer { public String text, iconUrl; }
  public static final class Thumbnail { public String url; }
  public static final class Image { public String url; }
  public static final class Field { public String name, value; public boolean inline; }
}
```

Validation (first violation → `BAD_PAYLOAD`): content ≤ 2000; ≤10 embeds; title ≤256; description ≤4096; ≤25 fields; field name ≤256; field value ≤1024; footer ≤2048; author ≤256.

All API methods return `CompletableFuture` and must not block the server main thread.

---

## 4) Configuration, Routing & Hot Reload

**Files**
- Live: `config/mindiscord.json5`
- Example: `config/mindiscord.json5.example` (generate on first run)

**Schema**
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
  queue: { maxSize: 2000, onOverflow: "dropOldest" },  // also: dropNewest | reject
  retry: { maxAttempts: 6, baseDelayMs: 500, maxDelayMs: 15000, jitter: true },
  ratelimit: { perRouteBurst: 10, perRouteRefillPerSec: 5 },
  log: { level: "INFO", json: false }
}
```

**Env overrides**
- If a route value starts with `env:NAME`, resolve `NAME` from environment; if missing → runtime `BAD_ROUTE`.

**Router behavior**
- If `route == null || route.isBlank()` → use `routes.default`.
- If a non-blank `route` is missing:
  - If `routes.default` exists → send via default and return `BAD_ROUTE_FALLBACK`.
  - If no default → reject with `BAD_ROUTE` (no send).

**Hot reload**
- Watch the config file; on change, parse and **atomically swap** the in-memory config.
- **In-flight** queue items keep their previously resolved URL; **new** enqueues use the latest config.
- Rate-limit buckets & stats are **per resolved route**.

---

## 5) Runtime, Queue, Worker, Transport

- Single worker thread drains a bounded queue; HTTP happens off-thread.
- Queue policies: `dropOldest` (recommended), `dropNewest`, `reject`.
- Retries: 429 honor `Retry-After`; 5xx/network IO use exponential backoff + jitter; 4xx (not 429) do not retry.
- Give up after `maxAttempts` → `GIVE_UP`.
- Rate limiting: token bucket **per resolved route**.
- Transport: Java 21 `java.net.http.HttpClient` (TLS, keep-alive). POST `application/json`. Timeouts: connect 3s, read 5s.

---

## 6) Optional Stats Table (via MinCore)
```sql
create table if not exists mindiscord_stats (
  route varchar(64) not null,
  day date not null,
  sent int unsigned not null default 0,
  failed int unsigned not null default 0,
  primary key(route, day)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
```
Update counters after each attempt with `ExtensionDatabase.withRetry`. Skip silently if DB down.

---

## 7) Result Codes
`SendResult.code` and MinCore ledger `code` must be one of:

- `OK`
- `BAD_ROUTE_FALLBACK` (unknown route; sent via `default`)
- `QUEUE_FULL`
- `BAD_PAYLOAD`
- `BAD_ROUTE`
- `DISCORD_429`
- `DISCORD_5XX`
- `NETWORK_IO`
- `GIVE_UP`

`SendResult.requestId` is a UUID; echo into ledger `idemKey` as `"send:" + requestId`.

---

## 8) Commands (optional but recommended)
- `/mindiscord routes` — list route names (URLs redacted; show whether `env:`)
- `/mindiscord test <route> <text>` — one-line test send
- `/mindiscord diag` — mode, queue size, last success/error per route
Cooldown 2s per sender; ledger log admin ops with reason `"command"`.

---

## 9) Deliverables (what this agent must output)
1. **Production code** implementing everything above.
2. **README.md** (server owner/operator focus): features, requirements, install, config with routing & `env:` example, commands, examples, troubleshooting.
3. **DEVELOPER_GUIDE.md** (plugin author focus): how to depend on MinDiscord, discover `AnnounceBus`, send text/embeds, handle `SendResult`, route usage patterns, thread-safety, rate-limit etiquette, example snippets.
4. **AGENTS.md** (this file) kept up to date.
5. **Tests**: unit tests (validation, router, queue policy); **Webhook transport test** with stub HTTP server (see sample below).
6. `mindiscord.json5.example` generated on first run.
7. Optional SQL DDL file for `mindiscord_stats` if stats enabled.

> If another agent is asked to write a plugin that uses MinDiscord, they should be able to do it using **DEVELOPER_GUIDE.md** alone.

---

## 10) Implementer Checklist (step-by-step)

- [ ] Scaffold Fabric mod `mindiscord` (server-only). Declare MinCore dependency.
- [ ] Implement config loader + file watcher (JSON5). On change → atomic swap.
- [ ] Implement `Router` (route name → URL) with `env:` resolution and fallback rules.
- [ ] Implement `Queue` with `maxSize` and `onOverflow` policies.
- [ ] Implement token-bucket rate limiter **per resolved route**.
- [ ] Implement `WebhookTransport` using Java 21 `HttpClient` with timeouts & keep-alive.
- [ ] Implement `AnnounceBusImpl` (validation, enqueue, `SendResult`, no main-thread blocking).
- [ ] Integrate **MinCore ledger** per §2 (one line per accepted send, include fallback info).
- [ ] Optional **StatsStore** writing `mindiscord_stats` via `ExtensionDatabase.withRetry`.
- [ ] Commands: `/mindiscord routes|test|diag` with cooldown and redacted secrets.
- [ ] Tests: router, payload validation, queue overflow policies, retry behavior, **WebhookTransportTest** (see below).
- [ ] Generate `mindiscord.json5.example` on first run.
- [ ] Produce **README.md** and **DEVELOPER_GUIDE.md** (see §9).

---

## 11) Sample CI Test: WebhookTransportTest (stub HTTP)

> This is a JUnit 5 example using `com.sun.net.httpserver.HttpServer` to stub a Discord webhook endpoint. It verifies that JSON content is delivered and that a 2xx is treated as success.

```java
package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class WebhookTransportTest {

  static HttpServer server;
  static volatile String lastBody;
  static volatile String lastPath;

  @BeforeAll
  static void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/webhooks/test", new HttpHandler() {
      @Override public void handle(HttpExchange ex) throws IOException {
        lastPath = ex.getRequestURI().getPath();
        try (InputStream in = ex.getRequestBody()) {
          lastBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // emulate Discord webhook success (204 No Content)
        ex.sendResponseHeaders(204, -1);
        ex.close();
      }
    });
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
  }

  @AfterAll
  static void stop() { server.stop(0); }

  @Test
  void sendsJsonToWebhook_andTreats2xxAsSuccess() throws Exception {
    var port = server.getAddress().getPort();
    var url = "http://127.0.0.1:" + port + "/api/webhooks/test";

    var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    var json = "{"content":"Hello from MinDiscord"}";
    var req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
    assertTrue(resp.statusCode() >= 200 && resp.statusCode() < 300, "Expected 2xx status");
    assertEquals("/api/webhooks/test", lastPath);
    assertNotNull(lastBody);
    assertTrue(lastBody.contains(""content""));
  }
}
```

> For retry/429 tests, add a handler variant that returns 429 with a `Retry-After` header and assert exponential backoff attempts (you can inject a clock/strategy to make the retry loop testable without real sleeping).

---

## 12) Notes & Quality Bars

- Single `HttpClient` instance; no per-request client creation.
- No logging of secrets; redact webhook URLs in logs as `https://discord.com/api/webhooks/…`.
- Keep ledger `extraJson` ≤ 512 bytes.
- No server-time decorations; callers own message formatting.
- Hot reload swaps config atomically; in-flight items keep their resolved URL.

---

## 13) Acceptance Criteria (must pass)

- Starts with MinDiscord + MinCore; example config generated; no JDA on classpath.
- `send("eventAnnouncements", "hello")` reaches configured webhook.
- Unknown route falls back to default with `BAD_ROUTE_FALLBACK`.
- Oversized content → `BAD_PAYLOAD`.
- 429/5xx cause retries; either success or `GIVE_UP` after max attempts.
- Queue overflow behaves per policy.
- Ledger line per accepted send: addonId=`mindiscord`, op=`announce`, reason=`<resolved route>`.

---


### Packaging rule (critical)
- **DO NOT bundle or ship MinCore** inside MinDiscord or its release assets.
- Rely on `fabric.mod.json` `depends` + Modrinth project dependency metadata so MinCore is auto-fetched.
- For development only, you may add a Maven coordinate for MinCore; remove any flatDir/file deps before release.


## Modrinth publishing (agent instructions)
- Use the included Gradle **com.modrinth.minotaur** plugin block.
- Read Modrinth credentials from `modrinthToken` (Gradle property) or `MODRINTH_TOKEN` env var.
- Set `modrinthProjectId` and `modrinthMinCoreId` (Gradle properties) to the correct Modrinth project IDs.
- Ensure MinCore is listed as **required** in both Gradle config and `fabric.mod.json`. Do **not** ship MinCore.
