package dev.mindiscord.core;

import dev.mindiscord.core.Config.QueueOverflowPolicy;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded queue with configurable overflow policy. */
final class DispatchQueue {
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition notEmpty = lock.newCondition();
  private final ArrayDeque<PendingRequest> deque = new ArrayDeque<>();

  private volatile int maxSize = 2000;
  private volatile QueueOverflowPolicy overflowPolicy = QueueOverflowPolicy.DROP_OLDEST;
  private volatile boolean closed;

  void configure(int maxSize, QueueOverflowPolicy policy) {
    Objects.requireNonNull(policy, "policy");
    lock.lock();
    try {
      this.maxSize = maxSize;
      this.overflowPolicy = policy;
      while (deque.size() > maxSize) {
        PendingRequest dropped = deque.poll();
        if (dropped != null) {
          dropped.completeQueueFull();
        }
      }
      if (!deque.isEmpty()) {
        notEmpty.signalAll();
      }
    } finally {
      lock.unlock();
    }
  }

  QueuePushResult enqueue(PendingRequest request) {
    lock.lock();
    try {
      if (closed) {
        return QueuePushResult.rejected();
      }
      if (deque.size() >= maxSize) {
        return switch (overflowPolicy) {
          case DROP_OLDEST -> {
            PendingRequest dropped = deque.poll();
            deque.add(request);
            notEmpty.signal();
            yield QueuePushResult.enqueuedWithDrop(dropped);
          }
          case DROP_NEWEST, REJECT -> QueuePushResult.rejected();
        };
      }
      deque.add(request);
      notEmpty.signal();
      return QueuePushResult.enqueued();
    } finally {
      lock.unlock();
    }
  }

  PendingRequest take() throws InterruptedException {
    lock.lock();
    try {
      while (deque.isEmpty()) {
        if (closed) {
          return null;
        }
        notEmpty.await();
      }
      return deque.poll();
    } finally {
      lock.unlock();
    }
  }

  int size() {
    lock.lock();
    try {
      return deque.size();
    } finally {
      lock.unlock();
    }
  }

  void close() {
    lock.lock();
    try {
      closed = true;
      for (PendingRequest pending : deque) {
        pending.completeQueueFull();
      }
      deque.clear();
      notEmpty.signalAll();
    } finally {
      lock.unlock();
    }
  }

  static final class QueuePushResult {
    enum State { ENQUEUED, REJECTED }

    private final State state;
    private final PendingRequest dropped;

    private QueuePushResult(State state, PendingRequest dropped) {
      this.state = state;
      this.dropped = dropped;
    }

    static QueuePushResult enqueued() { return new QueuePushResult(State.ENQUEUED, null); }

    static QueuePushResult enqueuedWithDrop(PendingRequest dropped) {
      return new QueuePushResult(State.ENQUEUED, dropped);
    }

    static QueuePushResult rejected() { return new QueuePushResult(State.REJECTED, null); }

    boolean isEnqueued() { return state == State.ENQUEUED; }

    PendingRequest dropped() { return dropped; }
  }
}
