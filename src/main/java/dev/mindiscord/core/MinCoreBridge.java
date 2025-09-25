package dev.mindiscord.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Reflection-based bridge to MinCore optional APIs. */
public final class MinCoreBridge {
  private static final Logger LOGGER = LogManager.getLogger("MinDiscord/MinCore");

  private final Class<?> apiClass;
  private final Method ledgerMethod;
  private final Method databaseMethod;
  private final Method ledgerLogMethod;
  private final Class<?> extensionDatabaseClass;
  private final Method tryAdvisoryLockMethod;
  private final Method releaseAdvisoryLockMethod;
  private final Method withRetryMethod;
  private final Method borrowConnectionMethod;
  private final Class<?> sqlSupplierClass;

  public MinCoreBridge() {
    Class<?> api;
    Method ledger;
    Method database;
    Method ledgerLog;
    Class<?> extDb;
    Method tryLock;
    Method releaseLock;
    Method withRetry;
    Method borrow;
    Class<?> sqlSupplier;
    try {
      api = Class.forName("dev.mincore.api.MinCoreApi");
      ledger = api.getMethod("ledger");
      database = api.getMethod("database");
      Class<?> ledgerType = Class.forName("dev.mincore.api.Ledger");
      ledgerLog = ledgerType.getMethod(
          "log",
          String.class,
          String.class,
          UUID.class,
          UUID.class,
          long.class,
          String.class,
          boolean.class,
          String.class,
          String.class,
          String.class,
          String.class);
      extDb = Class.forName("dev.mincore.api.storage.ExtensionDatabase");
      tryLock = extDb.getMethod("tryAdvisoryLock", String.class);
      releaseLock = extDb.getMethod("releaseAdvisoryLock", String.class);
      withRetry = extDb.getMethod(
          "withRetry", Class.forName("dev.mincore.api.storage.ExtensionDatabase$SQLSupplier"));
      borrow = extDb.getMethod("borrowConnection");
      sqlSupplier = Class.forName("dev.mincore.api.storage.ExtensionDatabase$SQLSupplier");
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      api = null;
      ledger = null;
      database = null;
      ledgerLog = null;
      extDb = null;
      tryLock = null;
      releaseLock = null;
      withRetry = null;
      borrow = null;
      sqlSupplier = null;
      LOGGER.warn("MinCore API not present; ledger and stats disabled");
    }
    this.apiClass = api;
    this.ledgerMethod = ledger;
    this.databaseMethod = database;
    this.ledgerLogMethod = ledgerLog;
    this.extensionDatabaseClass = extDb;
    this.tryAdvisoryLockMethod = tryLock;
    this.releaseAdvisoryLockMethod = releaseLock;
    this.withRetryMethod = withRetry;
    this.borrowConnectionMethod = borrow;
    this.sqlSupplierClass = sqlSupplier;
  }

  public boolean available() { return apiClass != null; }

  public boolean tryWithAdvisoryLock(String name, Runnable task) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(task, "task");
    if (!available() || databaseMethod == null || tryAdvisoryLockMethod == null) {
      task.run();
      return true;
    }
    Object db = database();
    if (db == null) {
      task.run();
      return true;
    }
    boolean acquired = false;
    try {
      acquired = Boolean.TRUE.equals(tryAdvisoryLockMethod.invoke(db, name));
      if (acquired) {
        task.run();
      }
      return acquired;
    } catch (ReflectiveOperationException e) {
      LOGGER.warn("Failed to use advisory lock {}: {}", name, e.toString());
      task.run();
      return false;
    } finally {
      if (acquired && releaseAdvisoryLockMethod != null) {
        try {
          releaseAdvisoryLockMethod.invoke(db, name);
        } catch (ReflectiveOperationException e) {
          LOGGER.warn("Failed to release advisory lock {}: {}", name, e.toString());
        }
      }
    }
  }

  public void logLedger(
      String reason,
      boolean ok,
      String code,
      String requestId,
      String route,
      String requestedRoute,
      int payloadBytes,
      int embeds) {
    if (ledgerMethod == null || ledgerLogMethod == null) {
      return;
    }
    try {
      Object ledger = ledgerMethod.invoke(null);
      if (ledger == null) {
        return;
      }
      String extra = String.format(
          "{\"route\":\"%s\",\"routeRequested\":%s,\"payloadBytes\":%d,\"embeds\":%d}",
          route,
          requestedRoute != null ? "\"" + requestedRoute + "\"" : "null",
          payloadBytes,
          embeds);
      if (extra.length() > 512) {
        extra = extra.substring(0, 512);
      }
      ledgerLogMethod.invoke(
          ledger,
          "mindiscord",
          "announce",
          null,
          null,
          0L,
          reason,
          ok,
          code,
          "mindiscord",
          requestId != null ? "send:" + requestId : null,
          extra);
    } catch (ReflectiveOperationException e) {
      LOGGER.warn("Failed to log to MinCore ledger: {}", e.toString());
    }
  }

  public void withDatabase(java.util.function.Consumer<Object> consumer) {
    if (databaseMethod == null || extensionDatabaseClass == null) {
      return;
    }
    Object db = database();
    if (db == null) {
      return;
    }
    consumer.accept(db);
  }

  public void ensureStatsTable(Object db, AtomicBoolean ensured) throws Exception {
    if (ensured.get()) {
      return;
    }
    if (borrowConnectionMethod == null) {
      return;
    }
    executeWithRetry(db, () -> {
      try (Connection conn = (Connection) borrowConnectionMethod.invoke(db);
          PreparedStatement ps =
              conn.prepareStatement(
                  "create table if not exists mindiscord_stats ("
                      + "route varchar(64) not null,"
                      + "day date not null,"
                      + "sent int unsigned not null default 0,"
                      + "failed int unsigned not null default 0,"
                      + "primary key(route, day)) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci")) {
        ps.executeUpdate();
      }
      ensured.set(true);
      return null;
    });
  }

  public void updateStats(Object db, String route, boolean success) throws Exception {
    if (borrowConnectionMethod == null) {
      return;
    }
    executeWithRetry(db, () -> {
      LocalDate day = LocalDate.now(ZoneOffset.UTC);
      String sql =
          "insert into mindiscord_stats(route, day, sent, failed) values(?,?,?,?) "
              + "on duplicate key update sent = sent + ?, failed = failed + ?";
      try (Connection conn = (Connection) borrowConnectionMethod.invoke(db);
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, route);
        ps.setObject(2, day);
        if (success) {
          ps.setInt(3, 1);
          ps.setInt(4, 0);
          ps.setInt(5, 1);
          ps.setInt(6, 0);
        } else {
          ps.setInt(3, 0);
          ps.setInt(4, 1);
          ps.setInt(5, 0);
          ps.setInt(6, 1);
        }
        ps.executeUpdate();
      }
      return null;
    });
  }

  private Object database() {
    if (databaseMethod == null) {
      return null;
    }
    try {
      return databaseMethod.invoke(null);
    } catch (ReflectiveOperationException e) {
      LOGGER.warn("MinCoreApi.database() failed: {}", e.toString());
      return null;
    }
  }

  private Object executeWithRetry(Object db, SqlCallable action) throws Exception {
    if (withRetryMethod == null || sqlSupplierClass == null) {
      return action.call();
    }
    InvocationHandler handler = (proxy, method, args) -> {
      if ("get".equals(method.getName())) {
        return action.call();
      }
      throw new UnsupportedOperationException(method.getName());
    };
    Object supplier = Proxy.newProxyInstance(
        sqlSupplierClass.getClassLoader(), new Class[] {sqlSupplierClass}, handler);
    try {
      return withRetryMethod.invoke(db, supplier);
    } catch (ReflectiveOperationException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception ex) {
        throw ex;
      }
      throw e;
    }
  }

  @FunctionalInterface
  interface SqlCallable {
    Object call() throws Exception;
  }
}
