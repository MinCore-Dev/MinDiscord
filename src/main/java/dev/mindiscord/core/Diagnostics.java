package dev.mindiscord.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Diagnostics {
  private final ConcurrentHashMap<String, RouteState> states = new ConcurrentHashMap<>();

  void recordSuccess(String route, Instant when, String message) {
    states.compute(route, (key, state) -> {
      RouteState s = state != null ? state : new RouteState();
      s.lastSuccess = when;
      s.lastMessage = message;
      s.lastFailure = null;
      s.lastFailureCode = null;
      return s;
    });
  }

  void recordFailure(String route, Instant when, String code, String message) {
    states.compute(route, (key, state) -> {
      RouteState s = state != null ? state : new RouteState();
      s.lastFailure = when;
      s.lastFailureCode = code;
      s.lastMessage = message;
      return s;
    });
  }

  Map<String, RouteSnapshot> snapshot() {
    Map<String, RouteSnapshot> copy = new LinkedHashMap<>();
    states.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> {
          RouteState state = entry.getValue();
          copy.put(
              entry.getKey(),
              new RouteSnapshot(state.lastSuccess, state.lastFailure, state.lastFailureCode, state.lastMessage));
        });
    return copy;
  }

  static final class RouteState {
    volatile Instant lastSuccess;
    volatile Instant lastFailure;
    volatile String lastFailureCode;
    volatile String lastMessage;
  }

  public record RouteSnapshot(
      Instant lastSuccess, Instant lastFailure, String lastFailureCode, String lastMessage) {}
}
