package dev.mindiscord.api;

import java.util.Optional;

public final class MinDiscordApi {
  private static volatile AnnounceBus BUS;

  private MinDiscordApi(){}

  public static Optional<AnnounceBus> bus() {
    return Optional.ofNullable(BUS);
  }

  // Called by the mod on bootstrap
  public static void install(AnnounceBus bus) {
    BUS = bus;
  }
}
