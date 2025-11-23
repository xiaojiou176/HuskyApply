package com.huskyapply.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.huskyapply.gateway.service.CacheEventListener;
import com.huskyapply.gateway.service.CacheMetricsCollector;
import com.huskyapply.gateway.service.CacheWarmupService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Multi-Layer Cache Configuration for HuskyApply
 *
 * <p>Implements a sophisticated 3-tier caching strategy: - L1: Caffeine (Local in-memory cache,
 * ~1ms latency) - L2: Redis (Distributed cache, ~5ms latency) - L3: CDN (Edge cache for static
 * content, ~50ms latency)
 *
 * <p>Cache Hierarchy: 1. Check L1 cache first (fastest) 2. If L1 miss, check L2 cache and populate
 * L1 3. If L2 miss, fetch from database and populate L2 + L1 4. Static assets served from L3 CDN
 */
@Configuration
@EnableCaching
public class MultiLayerCacheConfig {

  @Value("${cache.l1.maximum-size:10000}")
  private long l1MaximumSize;

  @Value("${cache.l1.expire-after-write:300}")
  private long l1ExpireAfterWriteSeconds;

  @Value("${cache.l2.default-ttl:1800}")
  private long l2DefaultTtlSeconds;

  @Value("${cache.metrics.enabled:true}")
  private boolean metricsEnabled;

  @Autowired private RedisConnectionFactory redisConnectionFactory;

  /**
   * L1 Cache: Caffeine-based local cache for ultra-fast access Perfect for frequently accessed data
   * like user sessions and job metadata
   */
  @Bean
  @Primary
  public CacheManager l1CacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();

    Caffeine<Object, Object> caffeineBuilder =
        Caffeine.newBuilder()
            .maximumSize(l1MaximumSize)
            .expireAfterWrite(l1ExpireAfterWriteSeconds, TimeUnit.SECONDS)
            .expireAfterAccess(
                l1ExpireAfterWriteSeconds / 2,
                TimeUnit.SECONDS); // Access-based expiry for LRU behavior

    if (metricsEnabled) {
      caffeineBuilder.recordStats(); // Enable metrics collection
    }

    cacheManager.setCaffeine(caffeineBuilder);
    cacheManager.setAllowNullValues(false); // Prevent null value caching

    return cacheManager;
  }

  /**
   * L2 Cache: Redis-based distributed cache for cross-instance consistency Ideal for user data, job
   * results, and shared application state
   */
  @Bean(name = "l2CacheManager")
  public CacheManager l2CacheManager() {
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(l2DefaultTtlSeconds))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));

    // Define cache-specific configurations
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

    // User sessions - short TTL for security
    cacheConfigurations.put("user-sessions", defaultConfig.entryTtl(Duration.ofMinutes(30)));

    // Job metadata - medium TTL for performance
    cacheConfigurations.put("jobs-metadata", defaultConfig.entryTtl(Duration.ofHours(2)));

    // Dashboard stats - longer TTL as they're expensive to compute
    cacheConfigurations.put("dashboard-stats", defaultConfig.entryTtl(Duration.ofHours(6)));

    // Templates - very long TTL as they rarely change
    cacheConfigurations.put("user-templates", defaultConfig.entryTtl(Duration.ofDays(1)));

    // Subscription data - long TTL with manual invalidation
    cacheConfigurations.put("subscription-data", defaultConfig.entryTtl(Duration.ofHours(12)));

    return RedisCacheManager.builder(redisConnectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(cacheConfigurations)
        .transactionAware() // Enable transaction support
        .build();
  }

  /** Enhanced Redis Template with optimized serialization */
  @Bean
  public RedisTemplate<String, Object> redisTemplate() {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);

    // Optimized serializers
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

    // Enable transaction support
    template.setEnableTransactionSupport(true);

    return template;
  }

  /**
   * Cache warming configuration Pre-loads frequently accessed data to improve initial response
   * times
   */
  @Bean
  public CacheWarmupService cacheWarmupService() {
    return new CacheWarmupService(l1CacheManager(), l2CacheManager());
  }

  /** Cache metrics and monitoring */
  @Bean
  public CacheMetricsCollector cacheMetricsCollector() {
    return new CacheMetricsCollector(l1CacheManager(), l2CacheManager(), metricsEnabled);
  }

  /** Cache event listener for debugging and monitoring */
  @Bean
  public CacheEventListener cacheEventListener() {
    return new CacheEventListener();
  }
}
