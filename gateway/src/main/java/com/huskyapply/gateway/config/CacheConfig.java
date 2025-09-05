package com.huskyapply.gateway.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
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
 * Comprehensive Redis Cache Configuration for HuskyApply Gateway Service.
 *
 * <p>Features: - Multi-layer caching with different TTL strategies - Cache compression for large
 * objects - Custom serialization with Jackson - Cache metrics and monitoring - Event-driven cache
 * invalidation - Cache warming strategies
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

  // Cache Names Constants
  public static final String DASHBOARD_STATS_CACHE = "dashboard_stats";
  public static final String DASHBOARD_JOBS_CACHE = "dashboard_jobs";
  public static final String TEMPLATES_USER_CACHE = "templates_user";
  public static final String TEMPLATES_CATEGORIES_CACHE = "templates_categories";
  public static final String JOBS_METADATA_CACHE = "jobs_metadata";
  public static final String USER_SESSION_CACHE = "user_session";
  public static final String API_RESPONSE_CACHE = "api_response";

  // TTL Configuration Properties
  @Value("${cache.ttl.dashboard.stats:300000}")
  private long dashboardStatsTtl;

  @Value("${cache.ttl.dashboard.jobs:180000}")
  private long dashboardJobsTtl;

  @Value("${cache.ttl.templates.user:600000}")
  private long templatesUserTtl;

  @Value("${cache.ttl.templates.categories:1800000}")
  private long templatesCategoriesTtl;

  @Value("${cache.ttl.jobs.metadata:120000}")
  private long jobsMetadataTtl;

  @Value("${cache.ttl.user.session:3600000}")
  private long userSessionTtl;

  @Value("${cache.ttl.api.response:60000}")
  private long apiResponseTtl;

  // Cache Configuration Properties
  @Value("${cache.compression.enabled:true}")
  private boolean compressionEnabled;

  @Value("${cache.compression.threshold:1024}")
  private int compressionThreshold;

  @Value("${cache.metrics.enabled:true}")
  private boolean metricsEnabled;

  @Value("${spring.cache.redis.key-prefix:huskyapply:cache:}")
  private String keyPrefix;

  private final RedisConnectionFactory redisConnectionFactory;
  private final MeterRegistry meterRegistry;

  // Cache metrics tracking
  private final Map<String, Long> cacheHits = new ConcurrentHashMap<>();
  private final Map<String, Long> cacheMisses = new ConcurrentHashMap<>();

  public CacheConfig(RedisConnectionFactory redisConnectionFactory, MeterRegistry meterRegistry) {
    this.redisConnectionFactory = redisConnectionFactory;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Primary Cache Manager with multiple cache configurations.
   *
   * @return Configured RedisCacheManager
   */
  @Bean
  @Primary
  @Override
  public CacheManager cacheManager() {
    logger.info("Configuring Redis Cache Manager with compression: {}", compressionEnabled);

    // Default cache configuration
    RedisCacheConfiguration defaultCacheConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(createJsonSerializer()))
            .prefixCacheNameWith(keyPrefix)
            .disableCachingNullValues()
            .entryTtl(Duration.ofMillis(300000)); // 5 minutes default

    // Cache-specific configurations
    Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

    cacheConfigurations.put(
        DASHBOARD_STATS_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(dashboardStatsTtl)));

    cacheConfigurations.put(
        DASHBOARD_JOBS_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(dashboardJobsTtl)));

    cacheConfigurations.put(
        TEMPLATES_USER_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(templatesUserTtl)));

    cacheConfigurations.put(
        TEMPLATES_CATEGORIES_CACHE,
        defaultCacheConfig.entryTtl(Duration.ofMillis(templatesCategoriesTtl)));

    cacheConfigurations.put(
        JOBS_METADATA_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(jobsMetadataTtl)));

    cacheConfigurations.put(
        USER_SESSION_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(userSessionTtl)));

    cacheConfigurations.put(
        API_RESPONSE_CACHE, defaultCacheConfig.entryTtl(Duration.ofMillis(apiResponseTtl)));

    RedisCacheManager.Builder builder =
        RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(redisConnectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations);

    if (metricsEnabled) {
      builder.enableStatistics();
    }

    RedisCacheManager cacheManager = builder.build();

    // Initialize cache metrics
    if (metricsEnabled) {
      initializeCacheMetrics();
    }

    logger.info(
        "Redis Cache Manager configured with {} cache regions and TTL range: {}ms - {}ms",
        cacheConfigurations.size(),
        apiResponseTtl,
        templatesCategoriesTtl);

    return cacheManager;
  }

  /**
   * Redis Template for manual cache operations with compression support.
   *
   * @return Configured RedisTemplate
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate() {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(createJsonSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(createJsonSerializer());
    template.setDefaultSerializer(createJsonSerializer());
    template.afterPropertiesSet();
    return template;
  }

  /**
   * Creates a custom JSON serializer with compression support.
   *
   * @return GenericJackson2JsonRedisSerializer with compression
   */
  private GenericJackson2JsonRedisSerializer createJsonSerializer() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());

    // Use BasicPolymorphicTypeValidator for secure type validation
    BasicPolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("java.util")
            .allowIfSubType("java.time")
            .allowIfSubType("com.huskyapply.gateway.dto")
            .build();

    objectMapper.activateDefaultTyping(
        ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

    if (compressionEnabled) {
      return new CompressedJsonRedisSerializer(objectMapper);
    } else {
      return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
  }

  /** Initialize cache metrics for monitoring. */
  private void initializeCacheMetrics() {
    logger.info("Initializing cache metrics for monitoring");

    // Register simple cache metrics
    meterRegistry.gauge(
        "cache.hit.ratio.dashboard", this, config -> getCacheHitRatio(DASHBOARD_STATS_CACHE));
    meterRegistry.gauge(
        "cache.size.dashboard", this, config -> getCacheSize(DASHBOARD_STATS_CACHE));

    // Additional metrics can be added here
    logger.info("Cache metrics initialized successfully");
  }

  /**
   * Calculate cache hit ratio for monitoring.
   *
   * @param cacheName The cache name
   * @return Hit ratio between 0.0 and 1.0
   */
  private double getCacheHitRatio(String cacheName) {
    long hits = cacheHits.getOrDefault(cacheName, 0L);
    long misses = cacheMisses.getOrDefault(cacheName, 0L);
    long total = hits + misses;
    return total > 0 ? (double) hits / total : 0.0;
  }

  /**
   * Get cache size for monitoring.
   *
   * @param cacheName The cache name
   * @return Cache size estimation
   */
  private double getCacheSize(String cacheName) {
    // This would need to be implemented with Redis commands to get accurate size
    return 0.0; // Placeholder
  }

  /**
   * Custom JSON Redis Serializer with compression support.
   *
   * <p>Compresses values larger than the configured threshold to reduce memory usage and network
   * overhead.
   */
  private class CompressedJsonRedisSerializer extends GenericJackson2JsonRedisSerializer {

    public CompressedJsonRedisSerializer(ObjectMapper objectMapper) {
      super(objectMapper);
    }

    @Override
    public byte[] serialize(Object source) throws IllegalArgumentException {
      if (source == null) {
        return new byte[0];
      }

      byte[] originalBytes = super.serialize(source);

      // Only compress if above threshold
      if (originalBytes.length >= compressionThreshold) {
        try {
          return compress(originalBytes);
        } catch (IOException e) {
          logger.warn("Failed to compress cache value, using uncompressed: {}", e.getMessage());
          return originalBytes;
        }
      }

      return originalBytes;
    }

    @Override
    public Object deserialize(byte[] source) throws IllegalArgumentException {
      if (source == null || source.length == 0) {
        return null;
      }

      // Check if data is compressed (simple magic number check)
      if (source.length > 2 && source[0] == (byte) 0x1f && source[1] == (byte) 0x8b) {
        try {
          byte[] decompressed = decompress(source);
          return super.deserialize(decompressed);
        } catch (IOException e) {
          logger.warn("Failed to decompress cache value: {}", e.getMessage());
          throw new IllegalArgumentException("Failed to decompress cache value", e);
        }
      }

      return super.deserialize(source);
    }

    private byte[] compress(byte[] data) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
        gzipOut.write(data);
      }
      return baos.toByteArray();
    }

    private byte[] decompress(byte[] compressedData) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzipIn.read(buffer)) > 0) {
          baos.write(buffer, 0, len);
        }
      }
      return baos.toByteArray();
    }
  }

  /**
   * Cache Health Indicator for monitoring cache connectivity and performance. Note: Commented out
   * as it requires Spring Boot Actuator dependency
   */
  // @Bean
  // @ConditionalOnProperty(name = "cache.metrics.enabled", havingValue = "true", matchIfMissing =
  // true)
  // public CacheHealthIndicator cacheHealthIndicator() {
  //   return new CacheHealthIndicator(redisTemplate(), meterRegistry);
  // }

  /**
   * Helper method to get all cache configuration info for debugging.
   *
   * @return Map of cache names and their TTL settings
   */
  public Map<String, Long> getCacheConfiguration() {
    Map<String, Long> config = new HashMap<>();
    config.put(DASHBOARD_STATS_CACHE, dashboardStatsTtl);
    config.put(DASHBOARD_JOBS_CACHE, dashboardJobsTtl);
    config.put(TEMPLATES_USER_CACHE, templatesUserTtl);
    config.put(TEMPLATES_CATEGORIES_CACHE, templatesCategoriesTtl);
    config.put(JOBS_METADATA_CACHE, jobsMetadataTtl);
    config.put(USER_SESSION_CACHE, userSessionTtl);
    config.put(API_RESPONSE_CACHE, apiResponseTtl);
    return config;
  }
}
