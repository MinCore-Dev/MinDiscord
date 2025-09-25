package dev.mindiscord;

import dev.mindiscord.api.MinDiscordApi;
import dev.mindiscord.core.MinDiscordRuntime;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MinDiscordMod implements ModInitializer {
  private static final Logger LOGGER = LogManager.getLogger("MinDiscord");

  @Override public void onInitialize() {
    MinDiscordRuntime runtime = MinDiscordRuntime.init();
    MinDiscordApi.install(runtime.bus());
    dev.mindiscord.commands.CommandRegistrar.registerAll(runtime);
    LOGGER.info("MinDiscord initialized");
  }
}
