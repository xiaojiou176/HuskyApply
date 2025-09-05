package com.huskyapply.gateway.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

/**
 * Database performance monitoring configuration for read-write splitting architecture. Provides
 * comprehensive metrics for query performance, connection utilization, and partition efficiency to
 * enable proactive performance optimization.
 */
@Configuration
public class DatabaseMetricsConfig {

  private final R2dbcEntityTemplate masterTemplate;
  private final R2dbcEntityTemplate readTemplate;
  private final DatabaseHealthMonitor healthMonitor;
  private final MeterRegistry meterRegistry;

  // Performance metrics
  private final AtomicLong readQueryCount = new AtomicLong(0);
  private final AtomicLong writeQueryCount = new AtomicLong(0);
  private final AtomicLong partitionPruningCount = new AtomicLong(0);
  private final AtomicLong connectionFailoverCount = new AtomicLong(0);

  // Query performance tracking
  private final AtomicReference<Double> avgReadQueryTime = new AtomicReference<>(0.0);
  private final AtomicReference<Double> avgWriteQueryTime = new AtomicReference<>(0.0);
  private final AtomicReference<Double> partitionEfficiency = new AtomicReference<>(100.0);

  // Connection pool metrics
  private final AtomicLong masterActiveConnections = new AtomicLong(0);
  private final AtomicLong replicaActiveConnections = new AtomicLong(0);
  private final AtomicLong connectionPoolExhausted = new AtomicLong(0);

  // Partition metrics
  private final AtomicLong partitionCount = new AtomicLong(0);
  private final AtomicLong avgPartitionSize = new AtomicLong(0);
  private final AtomicLong slowQueryCount = new AtomicLong(0);

  public DatabaseMetricsConfig(
      @Qualifier("masterR2dbcEntityTemplate") R2dbcEntityTemplate masterTemplate,
      @Qualifier("readR2dbcEntityTemplate") R2dbcEntityTemplate readTemplate,
      DatabaseHealthMonitor healthMonitor,
      MeterRegistry meterRegistry) {
    this.masterTemplate = masterTemplate;
    this.readTemplate = readTemplate;
    this.healthMonitor = healthMonitor;
    this.meterRegistry = meterRegistry;
    registerMetrics();
  }

  /** Register all database performance metrics with Micrometer. */
  private void registerMetrics() {
    // Query operation counters
    Counter.builder("huskyapply.database.queries.read.total")
        .description("Total number of read queries executed")
        .register(meterRegistry, readQueryCount, AtomicLong::get);

    Counter.builder("huskyapply.database.queries.write.total")
        .description("Total number of write queries executed")
        .register(meterRegistry, writeQueryCount, AtomicLong::get);

    Counter.builder("huskyapply.database.partition.pruning.total")
        .description("Total number of queries benefiting from partition pruning")
        .register(meterRegistry, partitionPruningCount, AtomicLong::get);

    Counter.builder("huskyapply.database.failover.total")
        .description("Total number of database failover events")
        .register(meterRegistry, connectionFailoverCount, AtomicLong::get);

    // Query performance gauges
    Gauge.builder("huskyapply.database.query.read.avg.duration.ms")
        .description("Average read query duration in milliseconds")
        .register(meterRegistry, avgReadQueryTime, AtomicReference::get);

    Gauge.builder("huskyapply.database.query.write.avg.duration.ms")
        .description("Average write query duration in milliseconds")
        .register(meterRegistry, avgWriteQueryTime, AtomicReference::get);

    // Connection pool gauges
    Gauge.builder("huskyapply.database.connections.master.active")
        .description("Number of active connections to master database")
        .register(meterRegistry, masterActiveConnections, AtomicLong::get);

    Gauge.builder("huskyapply.database.connections.replica.active")
        .description("Number of active connections to replica databases")
        .register(meterRegistry, replicaActiveConnections, AtomicLong::get);

    Gauge.builder("huskyapply.database.connection.pool.exhausted.total")
        .description("Total number of connection pool exhausted events")
        .register(meterRegistry, connectionPoolExhausted, AtomicLong::get);

    // Partition efficiency gauges
    Gauge.builder("huskyapply.database.partitions.count")
        .description("Total number of active database partitions")
        .register(meterRegistry, partitionCount, AtomicLong::get);

    Gauge.builder("huskyapply.database.partition.size.avg.mb")
        .description("Average partition size in megabytes")
        .register(meterRegistry, avgPartitionSize, AtomicLong::get);

    Gauge.builder("huskyapply.database.partition.efficiency.percent")
        .description("Partition pruning efficiency percentage")
        .register(meterRegistry, partitionEfficiency, AtomicReference::get);

    Gauge.builder("huskyapply.database.queries.slow.count")
        .description("Number of slow queries (>1s) in last collection period")
        .register(meterRegistry, slowQueryCount, AtomicLong::get);

    // Register query timers for detailed performance tracking
    Timer.builder("huskyapply.database.query.execution.time")
        .description("Database query execution time")
        .tag("operation", "read")
        .register(meterRegistry);

    Timer.builder("huskyapply.database.query.execution.time")
        .description("Database query execution time")
        .tag("operation", "write")
        .register(meterRegistry);

    Timer.builder("huskyapply.database.connection.acquisition.time")
        .description("Time to acquire database connection from pool")
        .register(meterRegistry);
  }

  /** Collect database performance metrics every 30 seconds. */
  @Scheduled(fixedRate = 30000)
  public void collectDatabaseMetrics() {
    collectQueryPerformanceMetrics()
        .then(collectConnectionPoolMetrics())
        .then(collectPartitionMetrics())
        .subscribe(
            result -> {},
            error -> logger.warn("Failed to collect database metrics: {}", error.getMessage()));
  }

  /** Collect query performance statistics from pg_stat_statements. */
  private Mono<Void> collectQueryPerformanceMetrics() {
    String queryStatsQuery =
        """
            SELECT
                CASE
                    WHEN query LIKE 'SELECT%' OR query LIKE 'select%' THEN 'read'
                    ELSE 'write'
                END as operation_type,
                AVG(mean_exec_time) as avg_time,
                SUM(calls) as total_calls,
                COUNT(*) FILTER (WHERE mean_exec_time > 1000) as slow_queries
            FROM pg_stat_statements
            WHERE query NOT LIKE '%pg_stat_statements%'
            GROUP BY operation_type
            """;

    return masterTemplate
        .getDatabaseClient()
        .sql(queryStatsQuery)
        .map(
            row -> {
              String operationType = (String) row.get("operation_type");
              Double avgTime = ((Number) row.get("avg_time")).doubleValue();
              Long totalCalls = ((Number) row.get("total_calls")).longValue();
              Long slowQueries = ((Number) row.get("slow_queries")).longValue();

              if ("read".equals(operationType)) {
                avgReadQueryTime.set(avgTime);
                readQueryCount.addAndGet(totalCalls);
              } else {
                avgWriteQueryTime.set(avgTime);
                writeQueryCount.addAndGet(totalCalls);
              }

              slowQueryCount.addAndGet(slowQueries);
              return row;
            })
        .all()
        .then()
        .onErrorResume(
            error -> {
              logger.debug("Query performance metrics collection failed: {}", error.getMessage());
              return Mono.empty();
            });
  }

  /** Collect connection pool statistics. */
  private Mono<Void> collectConnectionPoolMetrics() {
    String connectionStatsQuery =
        """
            SELECT
                state,
                COUNT(*) as connection_count
            FROM pg_stat_activity
            WHERE application_name LIKE 'huskyapply-gateway%'
            GROUP BY state
            """;

    return masterTemplate
        .getDatabaseClient()
        .sql(connectionStatsQuery)
        .map(
            row -> {
              String state = (String) row.get("state");
              Long count = ((Number) row.get("connection_count")).longValue();

              if ("active".equals(state)) {
                if (row.toString().contains("master")) {
                  masterActiveConnections.set(count);
                } else {
                  replicaActiveConnections.addAndGet(count);
                }
              }
              return row;
            })
        .all()
        .then()
        .onErrorResume(
            error -> {
              logger.debug("Connection pool metrics collection failed: {}", error.getMessage());
              return Mono.empty();
            });
  }

  /** Collect partition-specific performance metrics. */
  private Mono<Void> collectPartitionMetrics() {
    String partitionStatsQuery =
        """
            SELECT
                COUNT(*) as partition_count,
                AVG(pg_total_relation_size(schemaname||'.'||tablename)) / 1024 / 1024 as avg_size_mb,
                COUNT(*) FILTER (WHERE last_vacuum IS NOT NULL) as maintained_partitions
            FROM pg_tables
            LEFT JOIN pg_stat_user_tables ON pg_tables.tablename = pg_stat_user_tables.relname
            WHERE pg_tables.tablename LIKE 'jobs_%'
            """;

    return masterTemplate
        .getDatabaseClient()
        .sql(partitionStatsQuery)
        .map(
            row -> {
              Long count = ((Number) row.get("partition_count")).longValue();
              Long avgSize = ((Number) row.get("avg_size_mb")).longValue();
              Long maintained = ((Number) row.get("maintained_partitions")).longValue();

              partitionCount.set(count);
              avgPartitionSize.set(avgSize);

              // Calculate partition efficiency (maintained vs total)
              double efficiency =
                  count > 0 ? (maintained.doubleValue() / count.doubleValue()) * 100 : 100;
              partitionEfficiency.set(efficiency);

              return row;
            })
        .one()
        .then()
        .onErrorResume(
            error -> {
              logger.debug("Partition metrics collection failed: {}", error.getMessage());
              return Mono.empty();
            });
  }

  /** Generate a comprehensive database performance report. */
  public Mono<DatabasePerformanceReport> generatePerformanceReport() {
    return collectQueryPerformanceMetrics()
        .then(collectConnectionPoolMetrics())
        .then(collectPartitionMetrics())
        .then(
            Mono.fromSupplier(
                () ->
                    DatabasePerformanceReport.builder()
                        .timestamp(System.currentTimeMillis())
                        .readQueryCount(readQueryCount.get())
                        .writeQueryCount(writeQueryCount.get())
                        .avgReadQueryTime(avgReadQueryTime.get())
                        .avgWriteQueryTime(avgWriteQueryTime.get())
                        .masterActiveConnections(masterActiveConnections.get())
                        .replicaActiveConnections(replicaActiveConnections.get())
                        .partitionCount(partitionCount.get())
                        .avgPartitionSize(avgPartitionSize.get())
                        .partitionEfficiency(partitionEfficiency.get())
                        .slowQueryCount(slowQueryCount.get())
                        .connectionFailoverCount(connectionFailoverCount.get())
                        .partitionPruningCount(partitionPruningCount.get())
                        .overallHealth(calculateOverallHealth())
                        .recommendations(generateRecommendations())
                        .build()));
  }

  /** Calculate overall database health score based on metrics. */
  private String calculateOverallHealth() {
    double readAvg = avgReadQueryTime.get();
    double writeAvg = avgWriteQueryTime.get();
    long slowQueries = slowQueryCount.get();
    double efficiency = partitionEfficiency.get();

    if (readAvg > 2000 || writeAvg > 5000 || slowQueries > 100 || efficiency < 50) {
      return "CRITICAL";
    } else if (readAvg > 1000 || writeAvg > 2000 || slowQueries > 50 || efficiency < 80) {
      return "WARNING";
    } else {
      return "HEALTHY";
    }
  }

  /** Generate performance recommendations based on current metrics. */
  private String generateRecommendations() {
    StringBuilder recommendations = new StringBuilder();

    if (avgReadQueryTime.get() > 1000) {
      recommendations.append("Consider adding indexes for slow read queries. ");
    }
    if (avgWriteQueryTime.get() > 2000) {
      recommendations.append("Optimize write operations or consider write batching. ");
    }
    if (slowQueryCount.get() > 50) {
      recommendations.append("Review and optimize slow queries using EXPLAIN ANALYZE. ");
    }
    if (partitionEfficiency.get() < 80) {
      recommendations.append("Schedule partition maintenance to improve efficiency. ");
    }
    if (masterActiveConnections.get() + replicaActiveConnections.get() > 80) {
      recommendations.append("Monitor connection pool usage - approaching limits. ");
    }

    return recommendations.length() > 0
        ? recommendations.toString().trim()
        : "Performance is optimal";
  }

  // Metric increment methods for use by other services
  public void incrementReadQueryCount() {
    readQueryCount.incrementAndGet();
  }

  public void incrementWriteQueryCount() {
    writeQueryCount.incrementAndGet();
  }

  public void incrementPartitionPruningCount() {
    partitionPruningCount.incrementAndGet();
  }

  public void incrementConnectionFailoverCount() {
    connectionFailoverCount.incrementAndGet();
  }

  public void incrementConnectionPoolExhausted() {
    connectionPoolExhausted.incrementAndGet();
  }

  /** Data class for comprehensive database performance report. */
  public static class DatabasePerformanceReport {
    private long timestamp;
    private long readQueryCount;
    private long writeQueryCount;
    private double avgReadQueryTime;
    private double avgWriteQueryTime;
    private long masterActiveConnections;
    private long replicaActiveConnections;
    private long partitionCount;
    private long avgPartitionSize;
    private double partitionEfficiency;
    private long slowQueryCount;
    private long connectionFailoverCount;
    private long partitionPruningCount;
    private String overallHealth;
    private String recommendations;

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private DatabasePerformanceReport instance = new DatabasePerformanceReport();

      public Builder timestamp(long timestamp) {
        instance.timestamp = timestamp;
        return this;
      }

      public Builder readQueryCount(long readQueryCount) {
        instance.readQueryCount = readQueryCount;
        return this;
      }

      public Builder writeQueryCount(long writeQueryCount) {
        instance.writeQueryCount = writeQueryCount;
        return this;
      }

      public Builder avgReadQueryTime(double avgReadQueryTime) {
        instance.avgReadQueryTime = avgReadQueryTime;
        return this;
      }

      public Builder avgWriteQueryTime(double avgWriteQueryTime) {
        instance.avgWriteQueryTime = avgWriteQueryTime;
        return this;
      }

      public Builder masterActiveConnections(long masterActiveConnections) {
        instance.masterActiveConnections = masterActiveConnections;
        return this;
      }

      public Builder replicaActiveConnections(long replicaActiveConnections) {
        instance.replicaActiveConnections = replicaActiveConnections;
        return this;
      }

      public Builder partitionCount(long partitionCount) {
        instance.partitionCount = partitionCount;
        return this;
      }

      public Builder avgPartitionSize(long avgPartitionSize) {
        instance.avgPartitionSize = avgPartitionSize;
        return this;
      }

      public Builder partitionEfficiency(double partitionEfficiency) {
        instance.partitionEfficiency = partitionEfficiency;
        return this;
      }

      public Builder slowQueryCount(long slowQueryCount) {
        instance.slowQueryCount = slowQueryCount;
        return this;
      }

      public Builder connectionFailoverCount(long connectionFailoverCount) {
        instance.connectionFailoverCount = connectionFailoverCount;
        return this;
      }

      public Builder partitionPruningCount(long partitionPruningCount) {
        instance.partitionPruningCount = partitionPruningCount;
        return this;
      }

      public Builder overallHealth(String overallHealth) {
        instance.overallHealth = overallHealth;
        return this;
      }

      public Builder recommendations(String recommendations) {
        instance.recommendations = recommendations;
        return this;
      }

      public DatabasePerformanceReport build() {
        return instance;
      }
    }

    // Getters
    public long getTimestamp() {
      return timestamp;
    }

    public long getReadQueryCount() {
      return readQueryCount;
    }

    public long getWriteQueryCount() {
      return writeQueryCount;
    }

    public double getAvgReadQueryTime() {
      return avgReadQueryTime;
    }

    public double getAvgWriteQueryTime() {
      return avgWriteQueryTime;
    }

    public long getMasterActiveConnections() {
      return masterActiveConnections;
    }

    public long getReplicaActiveConnections() {
      return replicaActiveConnections;
    }

    public long getPartitionCount() {
      return partitionCount;
    }

    public long getAvgPartitionSize() {
      return avgPartitionSize;
    }

    public double getPartitionEfficiency() {
      return partitionEfficiency;
    }

    public long getSlowQueryCount() {
      return slowQueryCount;
    }

    public long getConnectionFailoverCount() {
      return connectionFailoverCount;
    }

    public long getPartitionPruningCount() {
      return partitionPruningCount;
    }

    public String getOverallHealth() {
      return overallHealth;
    }

    public String getRecommendations() {
      return recommendations;
    }
  }

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(DatabaseMetricsConfig.class);
}
