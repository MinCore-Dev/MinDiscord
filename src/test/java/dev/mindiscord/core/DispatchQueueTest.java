package dev.mindiscord.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.mindiscord.api.SendResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DispatchQueueTest {
  @Test
  void rejectPolicyReturnsQueueFull() {
    DispatchQueue queue = new DispatchQueue();
    queue.configure(1, Config.QueueOverflowPolicy.REJECT);

    PendingRequest first = request("first");
    assertTrue(queue.enqueue(first).isEnqueued());

    PendingRequest second = request("second");
    var result = queue.enqueue(second);
    assertFalse(result.isEnqueued());
  }

  @Test
  void dropOldestReturnsDroppedRequest() {
    DispatchQueue queue = new DispatchQueue();
    queue.configure(1, Config.QueueOverflowPolicy.DROP_OLDEST);

    PendingRequest first = request("first");
    PendingRequest second = request("second");

    assertTrue(queue.enqueue(first).isEnqueued());
    var result = queue.enqueue(second);
    assertTrue(result.isEnqueued());
    assertSame(first, result.dropped());
    result.dropped().completeQueueFull();
  }

  private PendingRequest request(String route) {
    Router.RouteResolution resolution =
        new Router.RouteResolution(route, route, "https://example", Router.Status.OK, false, null, null, false);
    CompletableFuture<SendResult> future = new CompletableFuture<>();
    return new PendingRequest(
        UUID.randomUUID(), resolution, "{}", 2, 0, future, Instant.EPOCH);
  }
}
