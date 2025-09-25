package dev.mindiscord.core;

import dev.mindiscord.api.SendResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PendingRequest {
  final UUID requestId;
  final String requestedRoute;
  final String resolvedRoute;
  final String url;
  final String jsonPayload;
  final int payloadBytes;
  final int embedCount;
  final boolean fallback;
  final CompletableFuture<SendResult> future;
  final Router.RouteResolution resolution;
  final Instant enqueuedAt;

  PendingRequest(
      UUID requestId,
      Router.RouteResolution resolution,
      String jsonPayload,
      int payloadBytes,
      int embedCount,
      CompletableFuture<SendResult> future,
      Instant enqueuedAt) {
    this.requestId = requestId;
    this.requestedRoute = resolution.requestedRoute();
    this.resolvedRoute = resolution.resolvedRoute();
    this.url = resolution.url();
    this.jsonPayload = jsonPayload;
    this.payloadBytes = payloadBytes;
    this.embedCount = embedCount;
    this.fallback = resolution.status() == Router.Status.FALLBACK;
    this.future = future;
    this.resolution = resolution;
    this.enqueuedAt = enqueuedAt;
  }

  void completeQueueFull() {
    future.complete(new SendResult(false, "QUEUE_FULL", "Queue full", requestId.toString()));
  }
}
