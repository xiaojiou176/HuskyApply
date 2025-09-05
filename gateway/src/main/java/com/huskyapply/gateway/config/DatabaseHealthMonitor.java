package com.huskyapply.gateway.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Database health monitoring service for read-write splitting architecture. Monitors connection
 * health, replication lag, and performance metrics for master and replica databases.
 */
@Component
public class DatabaseHealthMonitor implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthMonitor.class);

  private final DatabaseRoutingConfig.DatabaseRoutingService routingService;
  private final MeterRegistry meterRegistry;

  @Value("${database.routing.lag-threshold.warning:5}")
  private long warningLagThresholdSeconds;

  @Value("${database.routing.lag-threshold.critical:15}")
  private long criticalLagThresholdSeconds;

  @Value("${database.routing.health-check.enabled:true}")
  private boolean healthCheckEnabled;

  // Health metrics
  private final Map<String, DatabaseHealth> databaseHealthMap = new ConcurrentHashMap<>();
  private final AtomicLong masterConnectionCount = new AtomicLong(0);
  private final AtomicLong replicaConnectionCount = new AtomicLong(0);
  private final AtomicLong replicationLagSeconds = new AtomicLong(0);

  public DatabaseHealthMonitor(DatabaseRoutingConfig.DatabaseRoutingService routingService) {
    this.routingService = routingService;
    this.meterRegistry = null; // Will be injected by Spring if available
    initializeHealthMetrics();
  }

  @Autowired(required = false)
  public void setMeterRegistry(MeterRegistry meterRegistry) {
    if (meterRegistry != null) {
      registerMetrics(meterRegistry);
    }
  }

  /** Initialize health metrics with default values. */
  private void initializeHealthMetrics() {
    databaseHealthMap.put("master", new DatabaseHealth("master", true, 0, Duration.ZERO));
    databaseHealthMap.put("read1", new DatabaseHealth("read1", true, 0, Duration.ZERO));
    databaseHealthMap.put("read2", new DatabaseHealth("read2", true, 0, Duration.ZERO));
  }

  /** Register Micrometer metrics for database monitoring. */
  private void registerMetrics(MeterRegistry meterRegistry) {
    Gauge.builder("huskyapply.database.connections.master")
        .description("Number of active connections to master database")
        .register(meterRegistry, masterConnectionCount, AtomicLong::get);

    Gauge.builder("huskyapply.database.connections.replica")
        .description("Number of active connections to replica databases")
        .register(meterRegistry, replicaConnectionCount, AtomicLong::get);

    Gauge.builder("huskyapply.database.replication.lag.seconds")
        .description("Replication lag in seconds")
        .register(meterRegistry, replicationLagSeconds, AtomicLong::get);

    Gauge.builder("huskyapply.database.health.master")
        .description("Master database health status (1 = healthy, 0 = unhealthy)")
        .register(
            meterRegistry,
            this,
            monitor -> databaseHealthMap.get("master").isHealthy() ? 1.0 : 0.0);

    Gauge.builder("huskyapply.database.health.read1")
        .description("Read replica 1 health status (1 = healthy, 0 = unhealthy)")
        .register(
            meterRegistry, this, monitor -> databaseHealthMap.get("read1").isHealthy() ? 1.0 : 0.0);

    Gauge.builder("huskyapply.database.health.read2")
        .description("Read replica 2 health status (1 = healthy, 0 = unhealthy)")
        .register(
            meterRegistry, this, monitor -> databaseHealthMap.get("read2").isHealthy() ? 1.0 : 0.0);
  }

  /** Spring Boot Health Indicator implementation. */
  @Override
  public Health health() {
    if (!healthCheckEnabled) {
      return Health.up().withDetail("monitoring", "disabled").build();
    }

    Health.Builder healthBuilder = Health.up();

    // Check master database health
    DatabaseHealth masterHealth = databaseHealthMap.get("master");
    if (!masterHealth.isHealthy()) {
      healthBuilder.down();
    }

    healthBuilder
        .withDetail("master", masterHealth.toMap())
        .withDetail("read1", databaseHealthMap.get("read1").toMap())
        .withDetail("read2", databaseHealthMap.get("read2").toMap())
        .withDetail("replication_lag_seconds", replicationLagSeconds.get())
        .withDetail("warning_threshold_seconds", warningLagThresholdSeconds)
        .withDetail("critical_threshold_seconds", criticalLagThresholdSeconds);

    // Determine overall health based on replication lag
    long currentLag = replicationLagSeconds.get();
    if (currentLag > criticalLagThresholdSeconds) {
      healthBuilder.down().withDetail("status", "CRITICAL: Replication lag exceeded threshold");
    } else if (currentLag > warningLagThresholdSeconds) {
      healthBuilder
          .withDetail("status", "WARNING: Replication lag approaching threshold")
          .withDetail("recommended_action", "Monitor closely, consider read-only mode");
    } else {
      healthBuilder.withDetail("status", "OK: All databases healthy");
    }

    return healthBuilder.build();
  }

  /** Scheduled health check for all database connections. */
  @Scheduled(fixedRateString = "${database.routing.health-check.interval:30000}")
  public void performHealthCheck() {
    if (!healthCheckEnabled) {
      return;
    }

    logger.debug("Starting database health check");

    // Check master database
    checkDatabaseHealth("master", routingService.getMasterConnectionFactory());

    // Check read replicas
    checkDatabaseHealth("read1", routingService.getReadConnectionFactory());
    // Note: In a real implementation, we'd check each replica individually

    // Check replication lag
    checkReplicationLag();

    logger.debug("Database health check completed");
  }

  /** Check health of a specific database connection. */
  private void checkDatabaseHealth(String databaseName, ConnectionFactory connectionFactory) {
    Instant startTime = Instant.now();

    connectionFactory
        .create()
        .flatMap(
            connection -> {
              Statement statement = connection.createStatement("SELECT 1 as health_check");
              return statement
                  .execute()
                  .flatMap(result -> result.map((row, metadata) -> row.get("health_check")))
                  .collectList()
                  .then(Mono.fromRunnable(() -> connection.close()))
                  .then(Mono.just(connection));
            })
        .timeout(Duration.ofSeconds(10))
        .subscribe(
            connection -> {
              Duration responseTime = Duration.between(startTime, Instant.now());
              databaseHealthMap.put(
                  databaseName, new DatabaseHealth(databaseName, true, 1, responseTime));
              logger.debug(
                  "Database {} health check passed in {}ms", databaseName, responseTime.toMillis());
            },
            error -> {
              Duration responseTime = Duration.between(startTime, Instant.now());
              databaseHealthMap.put(
                  databaseName, new DatabaseHealth(databaseName, false, 0, responseTime));
              logger.warn("Database {} health check failed: {}", databaseName, error.getMessage());
            });
  }

  /** Check replication lag by querying the master database. */
  private void checkReplicationLag() {
    routingService
        .getMasterConnectionFactory()
        .create()
        .flatMap(
            connection -> {
              Statement statement =
                  connection.createStatement(
                      "SELECT COALESCE(EXTRACT(EPOCH FROM MAX(replay_lag)), 0) as max_lag_seconds "
                          + "FROM pg_stat_replication");
              return statement
                  .execute()
                  .flatMap(
                      result ->
                          result.map(
                              (row, metadata) -> {
                                Object lagValue = row.get("max_lag_seconds");
                                return lagValue != null ? ((Number) lagValue).longValue() : 0L;
                              }))
                  .collectList()
                  .doFinally(signalType -> connection.close());
            })
        .timeout(Duration.ofSeconds(5))
        .subscribe(
            lagList -> {
              if (!lagList.isEmpty()) {
                long lag = lagList.get(0);
                replicationLagSeconds.set(lag);
                logger.debug("Replication lag: {} seconds", lag);

                if (lag > criticalLagThresholdSeconds) {
                  logger.error(
                      "CRITICAL: Replication lag ({} seconds) exceeded critical threshold ({} seconds)",
                      lag,
                      criticalLagThresholdSeconds);
                } else if (lag > warningLagThresholdSeconds) {
                  logger.warn(
                      "WARNING: Replication lag ({} seconds) exceeded warning threshold ({} seconds)",
                      lag,
                      warningLagThresholdSeconds);
                }
              }
            },
            error -> {
              logger.error("Failed to check replication lag: {}", error.getMessage());
              // Don't update the lag value if the check failed
            });
  }

  /** Get current database health status. */
  public Map<String, DatabaseHealth> getDatabaseHealth() {
    return Map.copyOf(databaseHealthMap);
  }

  /** Get current replication lag in seconds. */
  public long getReplicationLagSeconds() {
    return replicationLagSeconds.get();
  }

  /** Check if a specific database is healthy. */
  public boolean isDatabaseHealthy(String databaseName) {
    DatabaseHealth health = databaseHealthMap.get(databaseName);
    return health != null && health.isHealthy();
  }

  /** Database health information container. */
  public static class DatabaseHealth {
    private final String name;
    private final boolean healthy;
    private final int connectionCount;
    private final Duration responseTime;
    private final Instant lastChecked;

    public DatabaseHealth(
        String name, boolean healthy, int connectionCount, Duration responseTime) {
      this.name = name;
      this.healthy = healthy;
      this.connectionCount = connectionCount;
      this.responseTime = responseTime;
      this.lastChecked = Instant.now();
    }

    public String getName() {
      return name;
    }

    public boolean isHealthy() {
      return healthy;
    }

    public int getConnectionCount() {
      return connectionCount;
    }

    public Duration getResponseTime() {
      return responseTime;
    }

    public Instant getLastChecked() {
      return lastChecked;
    }

    public Map<String, Object> toMap() {
      return Map.of(
          "name", name,
          "healthy", healthy,
          "connection_count", connectionCount,
          "response_time_ms", responseTime.toMillis(),
          "last_checked", lastChecked.toString());
    }
  }
}
