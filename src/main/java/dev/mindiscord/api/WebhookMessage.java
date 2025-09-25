package dev.mindiscord.api;

import java.util.List;

public final class WebhookMessage {
  public String username;
  public String avatarUrl;
  public String content;
  public List<Embed> embeds;
  public AllowedMentions allowedMentions;
}
