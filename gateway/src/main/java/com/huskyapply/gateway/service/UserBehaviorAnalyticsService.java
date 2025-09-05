package com.huskyapply.gateway.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * User Behavior Analytics Service for HuskyApply Gateway.
 *
 * <p>Provides machine learning-based user behavior analysis and predictive caching including: -
 * User access pattern analysis and clustering - Predictive cache preheating based on usage patterns
 * - Session-based cache optimization - Time-based and seasonal caching patterns - User cohort
 * analysis for targeted cache strategies - Machine learning models for optimal TTL prediction
 */
@Service
public class UserBehaviorAnalyticsService {

  private static final Logger logger = LoggerFactory.getLogger(UserBehaviorAnalyticsService.class);

  // User behavior tracking
  private final Map<String, UserBehaviorProfile> userProfiles = new ConcurrentHashMap<>();
  private final Map<String, List<CacheAccessEvent>> recentAccessEvents = new ConcurrentHashMap<>();

  // Machine learning models
  private final SimpleRegression ttlPredictionModel = new SimpleRegression();
  private KMeansPlusPlusClusterer<DoublePoint> userClusterer;
  private List<CentroidCluster<DoublePoint>> userClusters = new ArrayList<>();

  // Configuration
  @Value("${cache.ml.clustering.enabled:true}")
  private boolean clusteringEnabled;

  @Value("${cache.ml.clustering.max-clusters:10}")
  private int maxClusters;

  @Value("${cache.ml.min-data-points:100}")
  private int minDataPointsForPrediction;

  @Value("${cache.behavior.session-timeout:3600}")
  private long sessionTimeoutSeconds;

  @Value("${cache.behavior.tracking-retention-hours:168}")
  private long trackingRetentionHours; // 1 week

  private final MeterRegistry meterRegistry;

  public UserBehaviorAnalyticsService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    // Initialize ML clusterer
    if (clusteringEnabled) {
      this.userClusterer = new KMeansPlusPlusClusterer<>(maxClusters, 100);
    }

    // Initialize metrics
    initializeMetrics();
  }

  /** Record a cache access event for behavioral analysis. */
  public void recordCacheAccess(
      String userId, String cacheKey, boolean hit, Duration responseTime) {
    CacheAccessEvent event =
        new CacheAccessEvent(
            cacheKey, hit, responseTime, LocalDateTime.now(), extractCacheType(cacheKey));

    // Update user behavior profile
    updateUserBehaviorProfile(userId, event);

    // Store recent access events for pattern analysis
    recentAccessEvents.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);

    // Maintain sliding window of recent events
    maintainEventWindow(userId);

    // Record metrics
    meterRegistry
        .counter("cache.behavior.access", "user", userId, "hit", String.valueOf(hit))
        .increment();
  }

  /** Predict optimal TTL for a cache key based on user behavior and access patterns. */
  public Duration predictOptimalTtl(String key, Object value) {
    try {
      // Extract features from the cache key and value
      double[] features = extractCacheFeatures(key, value);

      // Use regression model to predict optimal TTL
      double predictedMinutes = ttlPredictionModel.predict(features[0]);

      // Apply bounds and context-aware adjustments
      predictedMinutes = Math.max(5, Math.min(predictedMinutes, 1440)); // 5 min to 24 hours

      // Adjust based on cache key type
      predictedMinutes = adjustTtlForCacheType(key, predictedMinutes);

      return Duration.ofMinutes((long) predictedMinutes);

    } catch (Exception e) {
      logger.debug("Failed to predict TTL for key {}: {}", key, e.getMessage());
      return getDefaultTtlForCacheType(key);
    }
  }

  /** Get user cohort recommendations for cache optimization. */
  public List<String> getCachePreheatingRecommendations(String userId) {
    List<String> recommendations = new ArrayList<>();

    UserBehaviorProfile profile = userProfiles.get(userId);
    if (profile == null) {
      return recommendations;
    }

    // Find similar users in the same cluster
    List<String> similarUsers = findSimilarUsers(userId);

    // Analyze common access patterns among similar users
    Map<String, Long> commonPatterns =
        similarUsers.stream()
            .flatMap(
                uid ->
                    recentAccessEvents.getOrDefault(uid, new ArrayList<>()).stream()
                        .map(CacheAccessEvent::getCacheKey))
            .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

    // Generate recommendations based on common patterns
    commonPatterns.entrySet().stream()
        .filter(entry -> entry.getValue() >= 3) // At least 3 similar users accessed this
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(10)
        .forEach(
            entry -> {
              if (!hasUserAccessedRecently(userId, entry.getKey())) {
                recommendations.add(entry.getKey());
              }
            });

    return recommendations;
  }

  /** Analyze user session patterns for cache optimization. */
  public UserSessionAnalysis analyzeUserSession(String userId) {
    List<CacheAccessEvent> events = recentAccessEvents.getOrDefault(userId, new ArrayList<>());

    if (events.isEmpty()) {
      return new UserSessionAnalysis(userId, 0, Duration.ZERO, 0.0, new ArrayList<>());
    }

    // Analyze session characteristics
    LocalDateTime sessionStart = events.get(0).getTimestamp();
    LocalDateTime sessionEnd = events.get(events.size() - 1).getTimestamp();
    Duration sessionDuration = Duration.between(sessionStart, sessionEnd);

    // Calculate hit ratio
    long hits = events.stream().mapToLong(e -> e.isHit() ? 1 : 0).sum();
    double hitRatio = (double) hits / events.size();

    // Identify frequent access patterns
    Map<String, Long> accessPatterns =
        events.stream()
            .map(CacheAccessEvent::getCacheKey)
            .collect(Collectors.groupingBy(key -> key, Collectors.counting()));

    List<String> frequentPatterns =
        accessPatterns.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    return new UserSessionAnalysis(
        userId, events.size(), sessionDuration, hitRatio, frequentPatterns);
  }

  /** Perform user clustering for behavioral segmentation. */
  public void performUserClustering() {
    if (!clusteringEnabled || userProfiles.size() < minDataPointsForPrediction) {
      return;
    }

    try {
      logger.info("Performing user clustering with {} user profiles", userProfiles.size());

      // Convert user profiles to feature vectors
      List<DoublePoint> dataPoints = new ArrayList<>();
      List<String> userIds = new ArrayList<>();

      for (Map.Entry<String, UserBehaviorProfile> entry : userProfiles.entrySet()) {
        UserBehaviorProfile profile = entry.getValue();
        double[] features =
            new double[] {
              profile.getAverageSessionDuration(),
              profile.getAverageHitRatio(),
              profile.getAccessFrequency(),
              profile.getPreferredTimeOfDay(),
              profile.getCacheTypePreference()
            };

        dataPoints.add(new DoublePoint(features));
        userIds.add(entry.getKey());
      }

      // Determine optimal number of clusters
      int optimalClusters = Math.min(maxClusters, Math.max(2, dataPoints.size() / 10));
      userClusterer = new KMeansPlusPlusClusterer<>(optimalClusters, 100);

      // Perform clustering
      userClusters = userClusterer.cluster(dataPoints);

      logger.info(
          "User clustering completed: {} clusters created from {} users",
          userClusters.size(),
          dataPoints.size());

      // Update user profiles with cluster assignments
      updateClusterAssignments(dataPoints, userIds);

      // Record metrics
      meterRegistry.gauge("cache.behavior.clusters.count", userClusters.size());
      meterRegistry.counter("cache.behavior.clustering.completed").increment();

    } catch (Exception e) {
      logger.error("Failed to perform user clustering", e);
      meterRegistry.counter("cache.behavior.clustering.failed").increment();
    }
  }

  /** Get cache preheating strategies for different user segments. */
  public Map<String, CachePreheatingStrategy> getPreheatingStrategies() {
    Map<String, CachePreheatingStrategy> strategies = new ConcurrentHashMap<>();

    for (int i = 0; i < userClusters.size(); i++) {
      CentroidCluster<DoublePoint> cluster = userClusters.get(i);
      String clusterName = "cluster_" + i;

      // Analyze cluster characteristics
      double[] centroid = cluster.getCenter().getPoint();

      CachePreheatingStrategy strategy =
          new CachePreheatingStrategy(
              clusterName,
              Duration.ofMinutes((long) centroid[0]), // Average session duration
              centroid[1], // Average hit ratio
              centroid[2], // Access frequency
              determinePreheatTiming(centroid[3]), // Preferred time of day
              determinePreferredCacheTypes(centroid[4]) // Cache type preference
              );

      strategies.put(clusterName, strategy);
    }

    return strategies;
  }

  /** Update user behavior profile with new access event. */
  private void updateUserBehaviorProfile(String userId, CacheAccessEvent event) {
    userProfiles.compute(
        userId,
        (key, existing) -> {
          if (existing == null) {
            UserBehaviorProfile profile = new UserBehaviorProfile(userId);
            profile.addAccessEvent(event);
            return profile;
          } else {
            existing.addAccessEvent(event);
            return existing;
          }
        });
  }

  /** Extract features from cache key and value for ML prediction. */
  private double[] extractCacheFeatures(String key, Object value) {
    double keyComplexity = key.length() + (key.split(":").length - 1) * 2;

    double valueSize = 1.0; // Default
    if (value instanceof String) {
      valueSize = ((String) value).length();
    } else if (value instanceof byte[]) {
      valueSize = ((byte[]) value).length;
    }

    return new double[] {keyComplexity, Math.log(valueSize + 1)};
  }

  /** Adjust TTL based on cache key type and patterns. */
  private double adjustTtlForCacheType(String key, double baseTtlMinutes) {
    if (key.contains("dashboard")) {
      return baseTtlMinutes * 0.8; // Dashboard data changes frequently
    } else if (key.contains("template")) {
      return baseTtlMinutes * 1.5; // Templates are more static
    } else if (key.contains("user_session")) {
      return Math.min(baseTtlMinutes, 60); // Session data expires quickly
    } else if (key.contains("api_response")) {
      return baseTtlMinutes * 0.5; // API responses should be fresh
    }

    return baseTtlMinutes;
  }

  /** Get default TTL for cache type when prediction fails. */
  private Duration getDefaultTtlForCacheType(String key) {
    if (key.contains("dashboard")) {
      return Duration.ofMinutes(5);
    } else if (key.contains("template")) {
      return Duration.ofMinutes(30);
    } else if (key.contains("user_session")) {
      return Duration.ofHours(1);
    } else if (key.contains("api_response")) {
      return Duration.ofMinutes(1);
    }

    return Duration.ofMinutes(10); // Default
  }

  /** Extract cache type from cache key. */
  private String extractCacheType(String key) {
    if (key.contains("dashboard")) return "dashboard";
    if (key.contains("template")) return "template";
    if (key.contains("user_session")) return "session";
    if (key.contains("api_response")) return "api";
    return "other";
  }

  /** Maintain sliding window of recent access events. */
  private void maintainEventWindow(String userId) {
    List<CacheAccessEvent> events = recentAccessEvents.get(userId);
    if (events != null && events.size() > 1000) { // Limit to last 1000 events
      events.subList(0, events.size() - 1000).clear();
    }
  }

  /** Find users similar to the given user based on clustering. */
  private List<String> findSimilarUsers(String userId) {
    UserBehaviorProfile targetProfile = userProfiles.get(userId);
    if (targetProfile == null || targetProfile.getClusterIndex() == -1) {
      return new ArrayList<>();
    }

    return userProfiles.entrySet().stream()
        .filter(
            entry ->
                !entry.getKey().equals(userId)
                    && entry.getValue().getClusterIndex() == targetProfile.getClusterIndex())
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  /** Check if user has accessed a cache key recently. */
  private boolean hasUserAccessedRecently(String userId, String cacheKey) {
    List<CacheAccessEvent> events = recentAccessEvents.get(userId);
    if (events == null) return false;

    LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
    return events.stream()
        .anyMatch(
            event -> event.getCacheKey().equals(cacheKey) && event.getTimestamp().isAfter(cutoff));
  }

  /** Update cluster assignments for user profiles. */
  private void updateClusterAssignments(List<DoublePoint> dataPoints, List<String> userIds) {
    for (int i = 0; i < dataPoints.size(); i++) {
      DoublePoint point = dataPoints.get(i);
      String userId = userIds.get(i);

      // Find which cluster this point belongs to
      for (int clusterIndex = 0; clusterIndex < userClusters.size(); clusterIndex++) {
        if (userClusters.get(clusterIndex).getPoints().contains(point)) {
          UserBehaviorProfile profile = userProfiles.get(userId);
          if (profile != null) {
            profile.setClusterIndex(clusterIndex);
          }
          break;
        }
      }
    }
  }

  /** Determine optimal preheating timing based on user behavior. */
  private List<Integer> determinePreheatTiming(double preferredTimeOfDay) {
    List<Integer> timings = new ArrayList<>();
    int baseHour = (int) preferredTimeOfDay;

    // Add hours around the preferred time
    timings.add(Math.max(0, baseHour - 1));
    timings.add(baseHour);
    timings.add(Math.min(23, baseHour + 1));

    return timings;
  }

  /** Determine preferred cache types for a cluster. */
  private List<String> determinePreferredCacheTypes(double cacheTypePreference) {
    // This is a simplified approach - in reality, you'd analyze cluster patterns
    List<String> types = new ArrayList<>();
    types.add("dashboard");

    if (cacheTypePreference > 0.5) {
      types.add("template");
    }
    if (cacheTypePreference > 0.7) {
      types.add("api");
    }

    return types;
  }

  /** Initialize metrics registration. */
  private void initializeMetrics() {
    meterRegistry.gauge(
        "cache.behavior.users.tracked", this, service -> service.userProfiles.size());
    meterRegistry.gauge(
        "cache.behavior.clusters.count", this, service -> service.userClusters.size());
  }

  /** Periodic cleanup and model training - runs every 4 hours. */
  @Scheduled(fixedRate = 14400000) // 4 hours
  public void performPeriodicMaintenance() {
    logger.info("Starting periodic user behavior analytics maintenance");

    try {
      // Clean up old access events
      cleanupOldAccessEvents();

      // Retrain TTL prediction model
      retrainTtlPredictionModel();

      // Perform user clustering
      performUserClustering();

      logger.info("Periodic maintenance completed successfully");
      meterRegistry.counter("cache.behavior.maintenance.completed").increment();

    } catch (Exception e) {
      logger.error("Failed to perform periodic maintenance", e);
      meterRegistry.counter("cache.behavior.maintenance.failed").increment();
    }
  }

  /** Clean up old access events beyond retention period. */
  private void cleanupOldAccessEvents() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(trackingRetentionHours);

    int totalEventsRemoved = 0;
    for (List<CacheAccessEvent> events : recentAccessEvents.values()) {
      int sizeBefore = events.size();
      events.removeIf(event -> event.getTimestamp().isBefore(cutoff));
      totalEventsRemoved += (sizeBefore - events.size());
    }

    if (totalEventsRemoved > 0) {
      logger.info("Cleaned up {} old access events", totalEventsRemoved);
      meterRegistry.counter("cache.behavior.cleanup.events").increment(totalEventsRemoved);
    }
  }

  /** Retrain TTL prediction model with recent data. */
  private void retrainTtlPredictionModel() {
    // Clear previous model data
    ttlPredictionModel.clear();

    int dataPoints = 0;
    for (List<CacheAccessEvent> events : recentAccessEvents.values()) {
      for (CacheAccessEvent event : events) {
        if (event.getResponseTime() != null) {
          double[] features = extractCacheFeatures(event.getCacheKey(), null);
          double targetTtl = calculateOptimalTtlFromEvent(event);

          ttlPredictionModel.addData(features[0], targetTtl);
          dataPoints++;
        }
      }
    }

    if (dataPoints > minDataPointsForPrediction) {
      logger.info("Retrained TTL prediction model with {} data points", dataPoints);
      meterRegistry.counter("cache.behavior.model.retrained").increment();
    }
  }

  /** Calculate optimal TTL from historical access event. */
  private double calculateOptimalTtlFromEvent(CacheAccessEvent event) {
    // Simple heuristic: faster response times suggest longer TTL is appropriate
    if (event.getResponseTime().toMillis() < 100) {
      return 30.0; // 30 minutes for fast responses
    } else if (event.getResponseTime().toMillis() < 500) {
      return 15.0; // 15 minutes for medium responses
    } else {
      return 5.0; // 5 minutes for slow responses
    }
  }

  /** Cache Access Event holder class. */
  public static class CacheAccessEvent {
    private final String cacheKey;
    private final boolean hit;
    private final Duration responseTime;
    private final LocalDateTime timestamp;
    private final String cacheType;

    public CacheAccessEvent(
        String cacheKey,
        boolean hit,
        Duration responseTime,
        LocalDateTime timestamp,
        String cacheType) {
      this.cacheKey = cacheKey;
      this.hit = hit;
      this.responseTime = responseTime;
      this.timestamp = timestamp;
      this.cacheType = cacheType;
    }

    // Getters
    public String getCacheKey() {
      return cacheKey;
    }

    public boolean isHit() {
      return hit;
    }

    public Duration getResponseTime() {
      return responseTime;
    }

    public LocalDateTime getTimestamp() {
      return timestamp;
    }

    public String getCacheType() {
      return cacheType;
    }
  }

  /** User Behavior Profile holder class. */
  private static class UserBehaviorProfile {
    private final String userId;
    private final List<CacheAccessEvent> accessHistory = new ArrayList<>();
    private double averageSessionDuration = 0.0;
    private double averageHitRatio = 0.0;
    private double accessFrequency = 0.0;
    private double preferredTimeOfDay = 12.0; // Default to noon
    private double cacheTypePreference = 0.5;
    private int clusterIndex = -1;

    public UserBehaviorProfile(String userId) {
      this.userId = userId;
    }

    public void addAccessEvent(CacheAccessEvent event) {
      accessHistory.add(event);

      // Keep only last 100 events to manage memory
      if (accessHistory.size() > 100) {
        accessHistory.remove(0);
      }

      // Update calculated fields
      updateCalculatedFields();
    }

    private void updateCalculatedFields() {
      if (accessHistory.isEmpty()) return;

      // Calculate average hit ratio
      long hits = accessHistory.stream().mapToLong(e -> e.isHit() ? 1 : 0).sum();
      this.averageHitRatio = (double) hits / accessHistory.size();

      // Calculate access frequency (accesses per hour)
      if (accessHistory.size() > 1) {
        LocalDateTime first = accessHistory.get(0).getTimestamp();
        LocalDateTime last = accessHistory.get(accessHistory.size() - 1).getTimestamp();
        long hours = Math.max(1, ChronoUnit.HOURS.between(first, last));
        this.accessFrequency = (double) accessHistory.size() / hours;
      }

      // Calculate preferred time of day (average hour of access)
      this.preferredTimeOfDay =
          accessHistory.stream().mapToInt(e -> e.getTimestamp().getHour()).average().orElse(12.0);

      // Calculate cache type preference (simplified)
      long dashboardAccess =
          accessHistory.stream().mapToLong(e -> e.getCacheType().equals("dashboard") ? 1 : 0).sum();
      this.cacheTypePreference = (double) dashboardAccess / accessHistory.size();
    }

    // Getters and setters
    public String getUserId() {
      return userId;
    }

    public double getAverageSessionDuration() {
      return averageSessionDuration;
    }

    public double getAverageHitRatio() {
      return averageHitRatio;
    }

    public double getAccessFrequency() {
      return accessFrequency;
    }

    public double getPreferredTimeOfDay() {
      return preferredTimeOfDay;
    }

    public double getCacheTypePreference() {
      return cacheTypePreference;
    }

    public int getClusterIndex() {
      return clusterIndex;
    }

    public void setClusterIndex(int clusterIndex) {
      this.clusterIndex = clusterIndex;
    }
  }

  /** User Session Analysis result class. */
  public static class UserSessionAnalysis {
    private final String userId;
    private final int accessCount;
    private final Duration sessionDuration;
    private final double hitRatio;
    private final List<String> frequentPatterns;

    public UserSessionAnalysis(
        String userId,
        int accessCount,
        Duration sessionDuration,
        double hitRatio,
        List<String> frequentPatterns) {
      this.userId = userId;
      this.accessCount = accessCount;
      this.sessionDuration = sessionDuration;
      this.hitRatio = hitRatio;
      this.frequentPatterns = frequentPatterns;
    }

    // Getters
    public String getUserId() {
      return userId;
    }

    public int getAccessCount() {
      return accessCount;
    }

    public Duration getSessionDuration() {
      return sessionDuration;
    }

    public double getHitRatio() {
      return hitRatio;
    }

    public List<String> getFrequentPatterns() {
      return frequentPatterns;
    }
  }

  /** Cache Preheating Strategy class. */
  public static class CachePreheatingStrategy {
    private final String clusterName;
    private final Duration averageSessionDuration;
    private final double expectedHitRatio;
    private final double accessFrequency;
    private final List<Integer> optimalPreheatHours;
    private final List<String> preferredCacheTypes;

    public CachePreheatingStrategy(
        String clusterName,
        Duration averageSessionDuration,
        double expectedHitRatio,
        double accessFrequency,
        List<Integer> optimalPreheatHours,
        List<String> preferredCacheTypes) {
      this.clusterName = clusterName;
      this.averageSessionDuration = averageSessionDuration;
      this.expectedHitRatio = expectedHitRatio;
      this.accessFrequency = accessFrequency;
      this.optimalPreheatHours = optimalPreheatHours;
      this.preferredCacheTypes = preferredCacheTypes;
    }

    // Getters
    public String getClusterName() {
      return clusterName;
    }

    public Duration getAverageSessionDuration() {
      return averageSessionDuration;
    }

    public double getExpectedHitRatio() {
      return expectedHitRatio;
    }

    public double getAccessFrequency() {
      return accessFrequency;
    }

    public List<Integer> getOptimalPreheatHours() {
      return optimalPreheatHours;
    }

    public List<String> getPreferredCacheTypes() {
      return preferredCacheTypes;
    }
  }
}
