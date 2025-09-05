package com.huskyapply.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Automated partition management service for time-based and hash-based partitioned tables. Handles
 * creation of future partitions, cleanup of old partitions, and monitoring of partition health and
 * performance.
 */
@Service
public class PartitionManagementService {

  private static final Logger logger = LoggerFactory.getLogger(PartitionManagementService.class);

  private final R2dbcEntityTemplate masterTemplate;
  private final MeterRegistry meterRegistry;

  @Value("${database.partitioning.future-months:6}")
  private int futureMonthsToCreate;

  @Value("${database.partitioning.retention-months:12}")
  private int retentionMonths;

  @Value("${database.partitioning.maintenance-enabled:true}")
  private boolean maintenanceEnabled;

  @Value("${database.partitioning.auto-cleanup:true}")
  private boolean autoCleanupEnabled;

  // Metrics
  private final AtomicLong activePartitionsCount = new AtomicLong(0);
  private final AtomicLong totalPartitionSize = new AtomicLong(0);
  private final AtomicLong lastMaintenanceTimestamp = new AtomicLong(0);

  private Counter partitionsCreated;
  private Counter partitionsDropped;
  private Counter maintenanceExecutions;
  private Timer partitionMaintenanceTimer;

  public PartitionManagementService(
      @Qualifier("masterR2dbcEntityTemplate") R2dbcEntityTemplate masterTemplate) {
    this.masterTemplate = masterTemplate;
    this.meterRegistry = null; // Will be injected if available
  }

  @Autowired(required = false)
  public void setMeterRegistry(MeterRegistry meterRegistry) {
    if (meterRegistry != null) {
      this.meterRegistry = meterRegistry;
      registerMetrics();
    }
  }

  /** Register Micrometer metrics for partition monitoring. */
  private void registerMetrics() {
    partitionsCreated =
        Counter.builder("huskyapply.partitions.created.total")
            .description("Total number of partitions created")
            .register(meterRegistry);

    partitionsDropped =
        Counter.builder("huskyapply.partitions.dropped.total")
            .description("Total number of partitions dropped")
            .register(meterRegistry);

    maintenanceExecutions =
        Counter.builder("huskyapply.partitions.maintenance.executions.total")
            .description("Total number of partition maintenance executions")
            .register(meterRegistry);

    partitionMaintenanceTimer =
        Timer.builder("huskyapply.partitions.maintenance.duration")
            .description("Time taken for partition maintenance operations")
            .register(meterRegistry);

    Gauge.builder("huskyapply.partitions.active.count")
        .description("Number of active partitions")
        .register(meterRegistry, activePartitionsCount, AtomicLong::get);

    Gauge.builder("huskyapply.partitions.total.size.bytes")
        .description("Total size of all partitions in bytes")
        .register(meterRegistry, totalPartitionSize, AtomicLong::get);

    Gauge.builder("huskyapply.partitions.last.maintenance.timestamp")
        .description("Timestamp of last partition maintenance execution")
        .register(meterRegistry, lastMaintenanceTimestamp, AtomicLong::get);
  }

  /**
   * Scheduled partition maintenance job. Runs daily at 2 AM to create future partitions and clean
   * up old ones.
   */
  @Scheduled(cron = "${database.partitioning.maintenance-cron:0 0 2 * * ?}")
  public void performScheduledMaintenance() {
    if (!maintenanceEnabled) {
      logger.debug("Partition maintenance is disabled");
      return;
    }

    logger.info("Starting scheduled partition maintenance");

    Timer.Sample sample = partitionMaintenanceTimer != null ? Timer.start(meterRegistry) : null;

    performPartitionMaintenance()
        .doOnSuccess(
            result -> {
              if (sample != null) sample.stop(partitionMaintenanceTimer);
              if (maintenanceExecutions != null) maintenanceExecutions.increment();
              lastMaintenanceTimestamp.set(System.currentTimeMillis());
              logger.info("Scheduled partition maintenance completed successfully");
            })
        .doOnError(
            error -> {
              if (sample != null) sample.stop(partitionMaintenanceTimer);
              logger.error("Scheduled partition maintenance failed", error);
            })
        .subscribe();
  }

  /** Perform comprehensive partition maintenance including creation and cleanup. */
  public Mono<Void> performPartitionMaintenance() {
    return createFuturePartitions()
        .then(autoCleanupEnabled ? cleanupOldPartitions() : Mono.empty())
        .then(updatePartitionStatistics())
        .then(refreshMaterializedViews())
        .doOnSuccess(result -> logger.info("Partition maintenance completed"))
        .doOnError(error -> logger.error("Partition maintenance failed", error));
  }

  /** Create future partitions for the jobs table based on configuration. */
  public Mono<Void> createFuturePartitions() {
    LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

    return Flux.range(1, futureMonthsToCreate)
        .map(currentMonth::plusMonths)
        .flatMap(this::createMonthlyPartition)
        .doOnNext(partitionName -> logger.debug("Created partition: {}", partitionName))
        .count()
        .doOnSuccess(
            count -> {
              if (partitionsCreated != null) partitionsCreated.increment(count);
              logger.info("Created {} future partitions", count);
            })
        .then();
  }

  /** Create a monthly partition for a specific date. */
  private Mono<String> createMonthlyPartition(LocalDate partitionDate) {
    String partitionName = "jobs_" + partitionDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));
    LocalDate nextMonth = partitionDate.plusMonths(1);

    String createPartitionSql =
        String.format(
            "CREATE TABLE IF NOT EXISTS %s PARTITION OF jobs_partitioned "
                + "FOR VALUES FROM ('%s'::timestamptz) TO ('%s'::timestamptz)",
            partitionName, partitionDate, nextMonth);

    return masterTemplate
        .getDatabaseClient()
        .sql(createPartitionSql)
        .then()
        .then(createPartitionIndexes(partitionName))
        .thenReturn(partitionName)
        .onErrorResume(
            error -> {
              logger.warn("Failed to create partition {}: {}", partitionName, error.getMessage());
              return Mono.empty();
            });
  }

  /** Create optimized indexes for a newly created partition. */
  private Mono<Void> createPartitionIndexes(String partitionName) {
    String[] indexQueries = {
      String.format(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_user_created "
              + "ON %s (user_id, created_at DESC)",
          partitionName, partitionName),
      String.format(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_status_created "
              + "ON %s (status, created_at DESC) WHERE status IN ('PENDING', 'PROCESSING')",
          partitionName, partitionName),
      String.format(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_%s_company "
              + "ON %s (company_name, created_at DESC) WHERE company_name IS NOT NULL",
          partitionName, partitionName)
    };

    return Flux.fromArray(indexQueries)
        .flatMap(
            sql ->
                masterTemplate
                    .getDatabaseClient()
                    .sql(sql)
                    .then()
                    .onErrorResume(
                        error -> {
                          logger.warn("Failed to create index: {}", error.getMessage());
                          return Mono.empty();
                        }))
        .then();
  }

  /** Clean up old partitions beyond the retention period. */
  public Mono<Void> cleanupOldPartitions() {
    LocalDate cutoffDate = LocalDate.now().minusMonths(retentionMonths).withDayOfMonth(1);

    String findOldPartitionsQuery =
        "SELECT tablename FROM pg_tables "
            + "WHERE schemaname = 'public' AND tablename LIKE 'jobs_%' "
            + "AND substring(tablename from 'jobs_(\\d{4}_\\d{2})')::text < $1";

    String cutoffString = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));

    return masterTemplate
        .getDatabaseClient()
        .sql(findOldPartitionsQuery)
        .bind(0, cutoffString)
        .map(row -> (String) row.get("tablename"))
        .all()
        .cast(String.class)
        .flatMap(this::dropPartition)
        .count()
        .doOnSuccess(
            count -> {
              if (partitionsDropped != null) partitionsDropped.increment(count);
              logger.info("Cleaned up {} old partitions", count);
            })
        .then();
  }

  /** Drop a specific partition table. */
  private Mono<Void> dropPartition(String partitionName) {
    String dropSql = String.format("DROP TABLE IF EXISTS %s", partitionName);

    return masterTemplate
        .getDatabaseClient()
        .sql(dropSql)
        .then()
        .doOnSuccess(result -> logger.info("Dropped old partition: {}", partitionName))
        .onErrorResume(
            error -> {
              logger.error("Failed to drop partition {}: {}", partitionName, error.getMessage());
              return Mono.empty();
            });
  }

  /** Update partition statistics for monitoring. */
  private Mono<Void> updatePartitionStatistics() {
    String statsQuery =
        "SELECT COUNT(*) as partition_count, "
            + "COALESCE(SUM(pg_total_relation_size(schemaname||'.'||tablename)), 0) as total_size "
            + "FROM pg_tables "
            + "WHERE tablename LIKE 'jobs_%' OR tablename LIKE 'user_activity_%'";

    return masterTemplate
        .getDatabaseClient()
        .sql(statsQuery)
        .map(
            row -> {
              activePartitionsCount.set(((Number) row.get("partition_count")).longValue());
              totalPartitionSize.set(((Number) row.get("total_size")).longValue());
              return row;
            })
        .one()
        .then();
  }

  /** Refresh materialized views that depend on partitioned tables. */
  private Mono<Void> refreshMaterializedViews() {
    return masterTemplate
        .getDatabaseClient()
        .sql("REFRESH MATERIALIZED VIEW CONCURRENTLY user_dashboard_stats")
        .then()
        .doOnSuccess(result -> logger.debug("Refreshed user_dashboard_stats materialized view"))
        .onErrorResume(
            error -> {
              logger.warn("Failed to refresh materialized views: {}", error.getMessage());
              return Mono.empty();
            });
  }

  /** Get partition statistics for monitoring and alerting. */
  public Mono<PartitionStatistics> getPartitionStatistics() {
    String statsQuery = "SELECT * FROM get_partition_stats()";

    return masterTemplate
        .getDatabaseClient()
        .sql(statsQuery)
        .map(
            row ->
                PartitionStatistics.builder()
                    .tableName((String) row.get("table_name"))
                    .partitionName((String) row.get("partition_name"))
                    .rowCount(((Number) row.get("row_count")).longValue())
                    .sizeBytes(((Number) row.get("size_bytes")).longValue())
                    .sizePretty((String) row.get("size_pretty"))
                    .build())
        .all()
        .collectList()
        .map(
            partitions -> {
              long totalRows =
                  partitions.stream().mapToLong(PartitionStatistics::getRowCount).sum();
              long totalSize =
                  partitions.stream().mapToLong(PartitionStatistics::getSizeBytes).sum();

              return PartitionStatistics.builder()
                  .tableName("SUMMARY")
                  .partitionName("ALL_PARTITIONS")
                  .rowCount(totalRows)
                  .sizeBytes(totalSize)
                  .sizePretty(formatBytes(totalSize))
                  .partitionDetails(partitions)
                  .build();
            });
  }

  /** Get partition performance report for optimization insights. */
  public Mono<PartitionPerformanceReport> getPartitionPerformanceReport() {
    String performanceQuery = "SELECT * FROM partition_performance_report()";

    return masterTemplate
        .getDatabaseClient()
        .sql(performanceQuery)
        .map(
            row ->
                PartitionPerformanceMetric.builder()
                    .metricName((String) row.get("metric_name"))
                    .metricValue(((Number) row.get("metric_value")).doubleValue())
                    .metricUnit((String) row.get("metric_unit"))
                    .recommendation((String) row.get("recommendation"))
                    .build())
        .all()
        .collectList()
        .map(
            metrics ->
                PartitionPerformanceReport.builder()
                    .reportTimestamp(System.currentTimeMillis())
                    .metrics(metrics)
                    .overallHealth(calculateOverallHealth(metrics))
                    .build());
  }

  /** Manually trigger partition creation for a specific month. */
  public Mono<Void> createPartitionForMonth(LocalDate month) {
    return createMonthlyPartition(month.withDayOfMonth(1))
        .doOnSuccess(partitionName -> logger.info("Manually created partition: {}", partitionName))
        .then();
  }

  /** Manually drop a specific partition (with safety checks). */
  public Mono<Void> dropPartitionSafely(String partitionName) {
    // Safety check: ensure partition is old enough
    LocalDate cutoffDate = LocalDate.now().minusMonths(1);
    String cutoffString = cutoffDate.format(DateTimeFormatter.ofPattern("yyyy_MM"));

    if (partitionName.contains(cutoffString)
        || partitionName.compareTo("jobs_" + cutoffString) > 0) {
      return Mono.error(
          new IllegalArgumentException("Cannot drop recent partition: " + partitionName));
    }

    return dropPartition(partitionName);
  }

  // Utility methods
  private String formatBytes(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }

  private String calculateOverallHealth(List<PartitionPerformanceMetric> metrics) {
    boolean hasWarnings =
        metrics.stream().anyMatch(m -> m.getRecommendation().contains("Consider"));
    boolean hasCritical = metrics.stream().anyMatch(m -> m.getRecommendation().contains("uneven"));

    if (hasCritical) return "CRITICAL";
    if (hasWarnings) return "WARNING";
    return "HEALTHY";
  }

  // Data classes for statistics and reporting
  public static class PartitionStatistics {
    private String tableName;
    private String partitionName;
    private long rowCount;
    private long sizeBytes;
    private String sizePretty;
    private List<PartitionStatistics> partitionDetails;

    // Builder pattern implementation
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private PartitionStatistics instance = new PartitionStatistics();

      public Builder tableName(String tableName) {
        instance.tableName = tableName;
        return this;
      }

      public Builder partitionName(String partitionName) {
        instance.partitionName = partitionName;
        return this;
      }

      public Builder rowCount(long rowCount) {
        instance.rowCount = rowCount;
        return this;
      }

      public Builder sizeBytes(long sizeBytes) {
        instance.sizeBytes = sizeBytes;
        return this;
      }

      public Builder sizePretty(String sizePretty) {
        instance.sizePretty = sizePretty;
        return this;
      }

      public Builder partitionDetails(List<PartitionStatistics> partitionDetails) {
        instance.partitionDetails = partitionDetails;
        return this;
      }

      public PartitionStatistics build() {
        return instance;
      }
    }

    // Getters
    public String getTableName() {
      return tableName;
    }

    public String getPartitionName() {
      return partitionName;
    }

    public long getRowCount() {
      return rowCount;
    }

    public long getSizeBytes() {
      return sizeBytes;
    }

    public String getSizePretty() {
      return sizePretty;
    }

    public List<PartitionStatistics> getPartitionDetails() {
      return partitionDetails;
    }
  }

  public static class PartitionPerformanceReport {
    private long reportTimestamp;
    private List<PartitionPerformanceMetric> metrics;
    private String overallHealth;

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private PartitionPerformanceReport instance = new PartitionPerformanceReport();

      public Builder reportTimestamp(long reportTimestamp) {
        instance.reportTimestamp = reportTimestamp;
        return this;
      }

      public Builder metrics(List<PartitionPerformanceMetric> metrics) {
        instance.metrics = metrics;
        return this;
      }

      public Builder overallHealth(String overallHealth) {
        instance.overallHealth = overallHealth;
        return this;
      }

      public PartitionPerformanceReport build() {
        return instance;
      }
    }

    // Getters
    public long getReportTimestamp() {
      return reportTimestamp;
    }

    public List<PartitionPerformanceMetric> getMetrics() {
      return metrics;
    }

    public String getOverallHealth() {
      return overallHealth;
    }
  }

  public static class PartitionPerformanceMetric {
    private String metricName;
    private double metricValue;
    private String metricUnit;
    private String recommendation;

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private PartitionPerformanceMetric instance = new PartitionPerformanceMetric();

      public Builder metricName(String metricName) {
        instance.metricName = metricName;
        return this;
      }

      public Builder metricValue(double metricValue) {
        instance.metricValue = metricValue;
        return this;
      }

      public Builder metricUnit(String metricUnit) {
        instance.metricUnit = metricUnit;
        return this;
      }

      public Builder recommendation(String recommendation) {
        instance.recommendation = recommendation;
        return this;
      }

      public PartitionPerformanceMetric build() {
        return instance;
      }
    }

    // Getters
    public String getMetricName() {
      return metricName;
    }

    public double getMetricValue() {
      return metricValue;
    }

    public String getMetricUnit() {
      return metricUnit;
    }

    public String getRecommendation() {
      return recommendation;
    }
  }
}
