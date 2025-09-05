package com.huskyapply.gateway.service;

import com.google.common.hash.BloomFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cache Analytics Service for HuskyApply Gateway.
 *
 * <p>Provides comprehensive cache performance analytics and insights including: - Real-time access
 * pattern tracking - Cache hit/miss ratio analytics - Performance trend analysis - Intelligent
 * cache sizing recommendations - Usage frequency analysis - Cache optimization suggestions
 */
@Service
public class CacheAnalyticsService {

  private static final Logger logger = LoggerFactory.getLogger(CacheAnalyticsService.class);

  // Analytics data structures
  private final Map<String, CacheKeyStats> keyStatistics = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> cacheHits = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> cacheMisses = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> accessCounts = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> lastAccessTimes = new ConcurrentHashMap<>();

  // Statistical analysis
  private final DescriptiveStatistics hitRatioStats = new DescriptiveStatistics(100);
  private final DescriptiveStatistics accessFrequencyStats = new DescriptiveStatistics(1000);
  private final DescriptiveStatistics responseTimes = new DescriptiveStatistics(1000);

  private final MeterRegistry meterRegistry;
  private final BloomFilter<String> cacheBloomFilter;

  // Cache performance thresholds
  private static final double HIGH_FREQUENCY_THRESHOLD = 0.8;
  private static final double LOW_FREQUENCY_THRESHOLD = 0.2;
  private static final long CACHE_KEY_STATS_RETENTION_HOURS = 24;

  @Autowired
  public CacheAnalyticsService(MeterRegistry meterRegistry, BloomFilter<String> cacheBloomFilter) {
    this.meterRegistry = meterRegistry;
    this.cacheBloomFilter = cacheBloomFilter;

    // Initialize metrics
    initializeMetrics();
  }

  /** Record a cache hit for the given key. */
  public void recordCacheHit(String key, Duration responseTime) {
    cacheHits.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    accessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    lastAccessTimes.put(key, LocalDateTime.now());

    // Update key statistics
    updateKeyStatistics(key, true, responseTime);

    // Record metrics
    meterRegistry.counter("cache.hits", "key", key).increment();

    if (responseTime != null) {
      Timer.Sample sample = Timer.start(meterRegistry);
      sample.stop(Timer.builder("cache.response.time").tag("type", "hit").register(meterRegistry));
      responseTimes.addValue(responseTime.toMillis());
    }

    // Update bloom filter
    cacheBloomFilter.put(key);
  }

  /** Record a cache miss for the given key. */
  public void recordCacheMiss(String key, Duration responseTime) {
    cacheMisses.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    accessCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    lastAccessTimes.put(key, LocalDateTime.now());

    // Update key statistics
    updateKeyStatistics(key, false, responseTime);

    // Record metrics
    meterRegistry.counter("cache.misses", "key", key).increment();

    if (responseTime != null) {
      Timer.Sample sample = Timer.start(meterRegistry);
      sample.stop(Timer.builder("cache.response.time").tag("type", "miss").register(meterRegistry));
      responseTimes.addValue(responseTime.toMillis());
    }
  }

  /**
   * Get the access frequency for a specific cache key.
   *
   * @param key The cache key
   * @return Access frequency between 0.0 and 1.0
   */
  public double getAccessFrequency(String key) {
    CacheKeyStats stats = keyStatistics.get(key);
    if (stats == null) {
      return 0.0;
    }

    long totalAccesses = stats.getHits() + stats.getMisses();
    if (totalAccesses == 0) {
      return 0.0;
    }

    // Calculate frequency based on access rate over time
    LocalDateTime firstAccess = stats.getFirstAccessTime();
    LocalDateTime lastAccess = stats.getLastAccessTime();

    if (firstAccess == null || lastAccess == null) {
      return 0.0;
    }

    long minutesBetween = ChronoUnit.MINUTES.between(firstAccess, lastAccess);
    if (minutesBetween <= 0) {
      minutesBetween = 1;
    }

    double accessRate = (double) totalAccesses / minutesBetween;

    // Normalize to 0.0-1.0 range (assuming max reasonable rate is 100 accesses per minute)
    return Math.min(accessRate / 100.0, 1.0);
  }

  /**
   * Get the time since last access for a specific cache key.
   *
   * @param key The cache key
   * @return Time in milliseconds since last access, or Long.MAX_VALUE if never accessed
   */
  public long getTimeSinceLastAccess(String key) {
    LocalDateTime lastAccess = lastAccessTimes.get(key);
    if (lastAccess == null) {
      return Long.MAX_VALUE;
    }
    return ChronoUnit.MILLIS.between(lastAccess, LocalDateTime.now());
  }

  /**
   * Get cache hit ratio for a specific key.
   *
   * @param key The cache key
   * @return Hit ratio between 0.0 and 1.0
   */
  public double getCacheHitRatio(String key) {
    long hits = cacheHits.getOrDefault(key, new AtomicLong(0)).get();
    long misses = cacheMisses.getOrDefault(key, new AtomicLong(0)).get();
    long total = hits + misses;

    return total > 0 ? (double) hits / total : 0.0;
  }

  /**
   * Get overall cache hit ratio across all keys.
   *
   * @return Overall hit ratio between 0.0 and 1.0
   */
  public double getOverallHitRatio() {
    long totalHits = cacheHits.values().stream().mapToLong(AtomicLong::get).sum();
    long totalMisses = cacheMisses.values().stream().mapToLong(AtomicLong::get).sum();
    long total = totalHits + totalMisses;

    return total > 0 ? (double) totalHits / total : 0.0;
  }

  /** Get cache analytics summary. */
  public CacheAnalyticsSummary getAnalyticsSummary() {
    return CacheAnalyticsSummary.builder()
        .overallHitRatio(getOverallHitRatio())
        .totalCacheKeys(keyStatistics.size())
        .highFrequencyKeys(getHighFrequencyKeyCount())
        .lowFrequencyKeys(getLowFrequencyKeyCount())
        .averageResponseTime(responseTimes.getMean())
        .medianResponseTime(responseTimes.getPercentile(50))
        .p95ResponseTime(responseTimes.getPercentile(95))
        .totalAccessCount(getTotalAccessCount())
        .cacheEfficiencyScore(calculateCacheEfficiencyScore())
        .build();
  }

  /** Check if a key might exist in cache using bloom filter. */
  public boolean mightExistInCache(String key) {
    return cacheBloomFilter.mightContain(key);
  }

  /** Get recommendations for cache optimization. */
  public CacheOptimizationRecommendations getOptimizationRecommendations() {
    CacheOptimizationRecommendations.Builder recommendations =
        CacheOptimizationRecommendations.builder();

    double overallHitRatio = getOverallHitRatio();

    if (overallHitRatio < 0.7) {
      recommendations.addRecommendation(
          "Consider increasing cache size or TTL - current hit ratio: "
              + String.format("%.2f%%", overallHitRatio * 100));
    }

    int highFreqKeys = getHighFrequencyKeyCount();
    int totalKeys = keyStatistics.size();

    if (totalKeys > 0 && (double) highFreqKeys / totalKeys > 0.3) {
      recommendations.addRecommendation(
          "Consider increasing L1 cache size for high-frequency keys");
    }

    double avgResponseTime = responseTimes.getMean();
    if (avgResponseTime > 100) { // 100ms threshold
      recommendations.addRecommendation(
          "High average response time detected: " + String.format("%.2fms", avgResponseTime));
    }

    return recommendations.build();
  }

  /** Update key statistics. */
  private void updateKeyStatistics(String key, boolean isHit, Duration responseTime) {
    keyStatistics.compute(
        key,
        (k, existing) -> {
          LocalDateTime now = LocalDateTime.now();

          if (existing == null) {
            CacheKeyStats newStats = new CacheKeyStats();
            newStats.setFirstAccessTime(now);
            newStats.setLastAccessTime(now);
            if (isHit) {
              newStats.setHits(1);
            } else {
              newStats.setMisses(1);
            }
            if (responseTime != null) {
              newStats.addResponseTime(responseTime.toMillis());
            }
            return newStats;
          } else {
            existing.setLastAccessTime(now);
            if (isHit) {
              existing.incrementHits();
            } else {
              existing.incrementMisses();
            }
            if (responseTime != null) {
              existing.addResponseTime(responseTime.toMillis());
            }
            return existing;
          }
        });
  }

  /** Get count of high-frequency keys. */
  private int getHighFrequencyKeyCount() {
    return (int)
        keyStatistics.keySet().stream()
            .mapToDouble(this::getAccessFrequency)
            .filter(freq -> freq >= HIGH_FREQUENCY_THRESHOLD)
            .count();
  }

  /** Get count of low-frequency keys. */
  private int getLowFrequencyKeyCount() {
    return (int)
        keyStatistics.keySet().stream()
            .mapToDouble(this::getAccessFrequency)
            .filter(freq -> freq <= LOW_FREQUENCY_THRESHOLD)
            .count();
  }

  /** Get total access count across all keys. */
  private long getTotalAccessCount() {
    return accessCounts.values().stream().mapToLong(AtomicLong::get).sum();
  }

  /** Calculate cache efficiency score (0-100). */
  private double calculateCacheEfficiencyScore() {
    double hitRatio = getOverallHitRatio();
    double avgResponseTime = responseTimes.getMean();

    // Base score from hit ratio (0-80 points)
    double hitScore = hitRatio * 80;

    // Response time bonus/penalty (0-20 points)
    double responseScore = 20;
    if (avgResponseTime > 50) {
      responseScore = Math.max(0, 20 - (avgResponseTime - 50) / 10);
    }

    return Math.min(100, hitScore + responseScore);
  }

  /** Initialize metrics registration. */
  private void initializeMetrics() {
    // Register gauge metrics
    meterRegistry.gauge(
        "cache.analytics.overall.hit.ratio", this, service -> service.getOverallHitRatio());
    meterRegistry.gauge(
        "cache.analytics.total.keys", this, service -> service.keyStatistics.size());
    meterRegistry.gauge(
        "cache.analytics.efficiency.score",
        this,
        service -> service.calculateCacheEfficiencyScore());
    meterRegistry.gauge(
        "cache.analytics.high.frequency.keys", this, service -> service.getHighFrequencyKeyCount());
    meterRegistry.gauge(
        "cache.analytics.low.frequency.keys", this, service -> service.getLowFrequencyKeyCount());
  }

  /** Periodic cleanup of old statistics - runs every hour. */
  @Scheduled(fixedRate = 3600000) // 1 hour
  public void cleanupOldStatistics() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(CACHE_KEY_STATS_RETENTION_HOURS);

    int removedCount = 0;
    for (Map.Entry<String, CacheKeyStats> entry : keyStatistics.entrySet()) {
      if (entry.getValue().getLastAccessTime().isBefore(cutoff)) {
        String key = entry.getKey();
        keyStatistics.remove(key);
        cacheHits.remove(key);
        cacheMisses.remove(key);
        accessCounts.remove(key);
        lastAccessTimes.remove(key);
        removedCount++;
      }
    }

    if (removedCount > 0) {
      logger.info("Cleaned up {} old cache statistics entries", removedCount);
      meterRegistry.counter("cache.analytics.cleanup.count").increment(removedCount);
    }
  }

  /** Cache Key Statistics holder class. */
  private static class CacheKeyStats {
    private long hits = 0;
    private long misses = 0;
    private LocalDateTime firstAccessTime;
    private LocalDateTime lastAccessTime;
    private final DescriptiveStatistics responseTimes = new DescriptiveStatistics(100);

    // Getters and setters
    public long getHits() {
      return hits;
    }

    public void setHits(long hits) {
      this.hits = hits;
    }

    public void incrementHits() {
      this.hits++;
    }

    public long getMisses() {
      return misses;
    }

    public void setMisses(long misses) {
      this.misses = misses;
    }

    public void incrementMisses() {
      this.misses++;
    }

    public LocalDateTime getFirstAccessTime() {
      return firstAccessTime;
    }

    public void setFirstAccessTime(LocalDateTime firstAccessTime) {
      this.firstAccessTime = firstAccessTime;
    }

    public LocalDateTime getLastAccessTime() {
      return lastAccessTime;
    }

    public void setLastAccessTime(LocalDateTime lastAccessTime) {
      this.lastAccessTime = lastAccessTime;
    }

    public void addResponseTime(double responseTime) {
      this.responseTimes.addValue(responseTime);
    }

    public double getAverageResponseTime() {
      return responseTimes.getMean();
    }
  }

  /** Cache Analytics Summary. */
  public static class CacheAnalyticsSummary {
    private final double overallHitRatio;
    private final int totalCacheKeys;
    private final int highFrequencyKeys;
    private final int lowFrequencyKeys;
    private final double averageResponseTime;
    private final double medianResponseTime;
    private final double p95ResponseTime;
    private final long totalAccessCount;
    private final double cacheEfficiencyScore;

    private CacheAnalyticsSummary(Builder builder) {
      this.overallHitRatio = builder.overallHitRatio;
      this.totalCacheKeys = builder.totalCacheKeys;
      this.highFrequencyKeys = builder.highFrequencyKeys;
      this.lowFrequencyKeys = builder.lowFrequencyKeys;
      this.averageResponseTime = builder.averageResponseTime;
      this.medianResponseTime = builder.medianResponseTime;
      this.p95ResponseTime = builder.p95ResponseTime;
      this.totalAccessCount = builder.totalAccessCount;
      this.cacheEfficiencyScore = builder.cacheEfficiencyScore;
    }

    public static Builder builder() {
      return new Builder();
    }

    // Getters
    public double getOverallHitRatio() {
      return overallHitRatio;
    }

    public int getTotalCacheKeys() {
      return totalCacheKeys;
    }

    public int getHighFrequencyKeys() {
      return highFrequencyKeys;
    }

    public int getLowFrequencyKeys() {
      return lowFrequencyKeys;
    }

    public double getAverageResponseTime() {
      return averageResponseTime;
    }

    public double getMedianResponseTime() {
      return medianResponseTime;
    }

    public double getP95ResponseTime() {
      return p95ResponseTime;
    }

    public long getTotalAccessCount() {
      return totalAccessCount;
    }

    public double getCacheEfficiencyScore() {
      return cacheEfficiencyScore;
    }

    public static class Builder {
      private double overallHitRatio;
      private int totalCacheKeys;
      private int highFrequencyKeys;
      private int lowFrequencyKeys;
      private double averageResponseTime;
      private double medianResponseTime;
      private double p95ResponseTime;
      private long totalAccessCount;
      private double cacheEfficiencyScore;

      public Builder overallHitRatio(double overallHitRatio) {
        this.overallHitRatio = overallHitRatio;
        return this;
      }

      public Builder totalCacheKeys(int totalCacheKeys) {
        this.totalCacheKeys = totalCacheKeys;
        return this;
      }

      public Builder highFrequencyKeys(int highFrequencyKeys) {
        this.highFrequencyKeys = highFrequencyKeys;
        return this;
      }

      public Builder lowFrequencyKeys(int lowFrequencyKeys) {
        this.lowFrequencyKeys = lowFrequencyKeys;
        return this;
      }

      public Builder averageResponseTime(double averageResponseTime) {
        this.averageResponseTime = averageResponseTime;
        return this;
      }

      public Builder medianResponseTime(double medianResponseTime) {
        this.medianResponseTime = medianResponseTime;
        return this;
      }

      public Builder p95ResponseTime(double p95ResponseTime) {
        this.p95ResponseTime = p95ResponseTime;
        return this;
      }

      public Builder totalAccessCount(long totalAccessCount) {
        this.totalAccessCount = totalAccessCount;
        return this;
      }

      public Builder cacheEfficiencyScore(double cacheEfficiencyScore) {
        this.cacheEfficiencyScore = cacheEfficiencyScore;
        return this;
      }

      public CacheAnalyticsSummary build() {
        return new CacheAnalyticsSummary(this);
      }
    }
  }

  /** Cache Optimization Recommendations. */
  public static class CacheOptimizationRecommendations {
    private final java.util.List<String> recommendations;

    private CacheOptimizationRecommendations(Builder builder) {
      this.recommendations = new java.util.ArrayList<>(builder.recommendations);
    }

    public static Builder builder() {
      return new Builder();
    }

    public java.util.List<String> getRecommendations() {
      return java.util.Collections.unmodifiableList(recommendations);
    }

    public static class Builder {
      private final java.util.List<String> recommendations = new java.util.ArrayList<>();

      public Builder addRecommendation(String recommendation) {
        this.recommendations.add(recommendation);
        return this;
      }

      public CacheOptimizationRecommendations build() {
        return new CacheOptimizationRecommendations(this);
      }
    }
  }
}
