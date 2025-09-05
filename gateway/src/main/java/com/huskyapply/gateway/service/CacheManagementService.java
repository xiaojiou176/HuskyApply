package com.huskyapply.gateway.service;

import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cache Management Service for HuskyApply Gateway.
 *
 * <p>Provides advanced cache management features including: - Cache warming strategies - Cache
 * eviction policies - Cache performance monitoring - Administrative cache operations - Cache health
 * monitoring
 */
@Service
public class CacheManagementService {

  private static final Logger logger = LoggerFactory.getLogger(CacheManagementService.class);

  private final CacheManager cacheManager;
  private final RedisTemplate<String, Object> redisTemplate;
  private final UserRepository userRepository;
  private final DashboardService dashboardService;
  private final TemplateService templateService;
  private final MeterRegistry meterRegistry;

  // Thread pool for async cache operations
  private final Executor cacheExecutor =
      Executors.newFixedThreadPool(
          5,
          r -> {
            Thread t = new Thread(r, "cache-management-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
          });

  public CacheManagementService(
      CacheManager cacheManager,
      RedisTemplate<String, Object> redisTemplate,
      UserRepository userRepository,
      DashboardService dashboardService,
      TemplateService templateService,
      MeterRegistry meterRegistry) {
    this.cacheManager = cacheManager;
    this.redisTemplate = redisTemplate;
    this.userRepository = userRepository;
    this.dashboardService = dashboardService;
    this.templateService = templateService;
    this.meterRegistry = meterRegistry;
  }

  /** Cache warming on application startup. */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    logger.info("Application ready - starting cache warming process");
    warmCriticalCaches();
  }

  /** Warm critical caches asynchronously. */
  @Async
  public void warmCriticalCaches() {
    logger.info("Starting cache warming for critical data");

    try {
      // Find users for cache warming (get a reasonable sample)
      List<User> activeUsers = userRepository.findAll().stream().limit(50).toList();
      logger.info("Found {} users for cache warming", activeUsers.size());

      // Warm dashboard caches for active users
      CompletableFuture<Void> dashboardWarming =
          CompletableFuture.runAsync(
              () -> {
                warmDashboardCaches(activeUsers);
              },
              cacheExecutor);

      // Warm template caches for active users
      CompletableFuture<Void> templateWarming =
          CompletableFuture.runAsync(
              () -> {
                warmTemplateCaches(activeUsers);
              },
              cacheExecutor);

      // Wait for warming to complete
      CompletableFuture.allOf(dashboardWarming, templateWarming)
          .thenRun(
              () -> {
                logger.info("Cache warming completed successfully");
                recordCacheWarmingMetrics(activeUsers.size());
              })
          .exceptionally(
              throwable -> {
                logger.error("Cache warming failed", throwable);
                meterRegistry.counter("cache.warming.failures").increment();
                return null;
              });

    } catch (Exception e) {
      logger.error("Error during cache warming", e);
      meterRegistry.counter("cache.warming.errors").increment();
    }
  }

  /** Warm dashboard caches for active users. */
  private void warmDashboardCaches(List<User> users) {
    logger.info("Warming dashboard caches for {} users", users.size());

    int warmed = 0;
    for (User user : users) {
      try {
        // Pre-load dashboard stats
        dashboardService.getUserStats(user);

        // Pre-load recent jobs
        dashboardService.getRecentJobs(user);

        warmed++;

        if (warmed % 10 == 0) {
          logger.debug("Warmed dashboard cache for {} users", warmed);
        }

        // Small delay to avoid overwhelming the system
        Thread.sleep(50);

      } catch (Exception e) {
        logger.warn("Failed to warm dashboard cache for user {}: {}", user.getId(), e.getMessage());
      }
    }

    logger.info("Dashboard cache warming completed for {} users", warmed);
  }

  /** Warm template caches for active users. */
  private void warmTemplateCaches(List<User> users) {
    logger.info("Warming template caches for {} users", users.size());

    int warmed = 0;
    for (User user : users) {
      try {
        // Pre-load user templates (first page)
        org.springframework.data.domain.PageRequest pageable =
            org.springframework.data.domain.PageRequest.of(0, 20);
        templateService.getTemplates(user, null, pageable);

        warmed++;

        if (warmed % 10 == 0) {
          logger.debug("Warmed template cache for {} users", warmed);
        }

        // Small delay to avoid overwhelming the system
        Thread.sleep(50);

      } catch (Exception e) {
        logger.warn("Failed to warm template cache for user {}: {}", user.getId(), e.getMessage());
      }
    }

    logger.info("Template cache warming completed for {} users", warmed);
  }

  /** Scheduled cache maintenance - runs every 30 minutes. */
  @Scheduled(fixedRate = 1800000) // 30 minutes
  public void scheduledCacheMaintenance() {
    logger.debug("Starting scheduled cache maintenance");

    try {
      // Record cache statistics
      recordCacheStatistics();

      // Check cache health
      checkCacheHealth();

    } catch (Exception e) {
      logger.error("Error during scheduled cache maintenance", e);
    }
  }

  /** Record cache statistics for monitoring. */
  private void recordCacheStatistics() {
    for (String cacheName : cacheManager.getCacheNames()) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        // Record basic cache metrics
        try {
          double size = getCacheSize(cacheName);
          meterRegistry.gauge("cache.size." + cacheName.replace(":", "."), size);
        } catch (Exception e) {
          logger.debug("Could not record cache size metric for {}: {}", cacheName, e.getMessage());
        }
      }
    }
  }

  /** Check overall cache health. */
  private void checkCacheHealth() {
    try {
      // Test Redis connectivity
      String testKey = "health-check-" + System.currentTimeMillis();
      redisTemplate.opsForValue().set(testKey, "OK");
      String result = (String) redisTemplate.opsForValue().get(testKey);
      redisTemplate.delete(testKey);

      boolean healthy = "OK".equals(result);
      meterRegistry.gauge("cache.health", healthy ? 1.0 : 0.0);

      if (!healthy) {
        logger.warn("Cache health check failed");
      }

    } catch (Exception e) {
      logger.error("Cache health check failed", e);
      meterRegistry.gauge("cache.health", 0.0);
    }
  }

  /** Get approximate cache size. */
  private double getCacheSize(String cacheName) {
    try {
      // This is a simplified size estimation
      // In production, you might want to use Redis INFO commands for accurate metrics
      return redisTemplate.getConnectionFactory().getConnection().dbSize().doubleValue();
    } catch (Exception e) {
      logger.debug("Could not get cache size for {}: {}", cacheName, e.getMessage());
      return 0.0;
    }
  }

  /** Record cache warming metrics. */
  private void recordCacheWarmingMetrics(int usersWarmed) {
    meterRegistry.counter("cache.warming.completed").increment();
    meterRegistry.gauge("cache.warming.users.last", usersWarmed);
  }

  /** Administrative operations for cache management. */

  /** Clear specific cache by name. */
  public void clearCache(String cacheName) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.clear();
      logger.info("Cleared cache: {}", cacheName);
      meterRegistry.counter("cache.admin.clear", "cache", cacheName).increment();
    } else {
      logger.warn("Cache not found: {}", cacheName);
    }
  }

  /** Clear all application caches. */
  public void clearAllCaches() {
    for (String cacheName : cacheManager.getCacheNames()) {
      clearCache(cacheName);
    }
    logger.warn("Cleared ALL application caches - administrative operation");
    meterRegistry.counter("cache.admin.clear.all").increment();
  }

  /** Get cache statistics summary. */
  public String getCacheStatisticsSummary() {
    StringBuilder summary = new StringBuilder();
    summary.append("Cache Statistics Summary:\n");

    for (String cacheName : cacheManager.getCacheNames()) {
      Cache cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        summary.append(String.format("- %s: active\n", cacheName));
      }
    }

    return summary.toString();
  }

  /** Force cache warming for specific user. */
  @Async
  public CompletableFuture<Void> warmUserCache(User user) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            logger.info("Warming cache for user: {}", user.getId());

            // Warm dashboard cache
            dashboardService.getUserStats(user);
            dashboardService.getRecentJobs(user);

            // Warm template cache
            org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(0, 20);
            templateService.getTemplates(user, null, pageable);

            logger.info("Cache warming completed for user: {}", user.getId());

          } catch (Exception e) {
            logger.error("Failed to warm cache for user {}: {}", user.getId(), e.getMessage());
            throw new RuntimeException(e);
          }
        },
        cacheExecutor);
  }

  /** Evict all caches for a specific user. */
  public void evictUserCaches(User user) {
    logger.info("Evicting all caches for user: {}", user.getId());

    try {
      dashboardService.evictUserDashboardCache(user);
      // Template cache is evicted automatically when templates are modified
      logger.info("Successfully evicted all caches for user: {}", user.getId());
    } catch (Exception e) {
      logger.error("Failed to evict caches for user {}: {}", user.getId(), e.getMessage());
    }
  }
}
