package dev.mindiscord;

import dev.mindiscord.api.MinDiscordApi;
import dev.mindiscord.core.AnnounceBusImpl;
import dev.mindiscord.core.Router;
import dev.mindiscord.core.WebhookTransport;
import dev.mindiscord.core.Config;
import dev.mindiscord.core.ConfigLoader;
import net.fabricmc.api.ModInitializer;

public final class MinDiscordMod implements ModInitializer {
  @Override public void onInitialize() {
    // Load config (creates example file if missing)
    Config cfg = ConfigLoader.loadOrCreate();
    Router router = new Router(cfg);
    WebhookTransport transport = new WebhookTransport();
    AnnounceBusImpl bus = new AnnounceBusImpl(router, transport, cfg);

    // Install for discovery
    MinDiscordApi.install(bus);

    dev.mindiscord.commands.CommandRegistrar.registerAll(); // stub registration
    System.out.println("[MinDiscord] Initialized (skeleton)");
  }
}
