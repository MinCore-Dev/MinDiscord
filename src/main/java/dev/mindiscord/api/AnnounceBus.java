package dev.mindiscord.api;

import java.util.concurrent.CompletableFuture;

public interface AnnounceBus {
  CompletableFuture<SendResult> send(String route, String content);
  CompletableFuture<SendResult> send(String route, WebhookMessage msg);
  CompletableFuture<SendResult> send(String route, Embed embed);
}
