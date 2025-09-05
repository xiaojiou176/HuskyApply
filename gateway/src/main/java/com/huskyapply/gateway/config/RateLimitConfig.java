package com.huskyapply.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Rate Limiting using Redis.
 *
 * <p>This configuration sets up Redis templates for managing rate limiting counters and provides
 * centralized rate limiting configuration.
 */
@Configuration
public class RateLimitConfig {

  /** Rate limiting configuration properties */
  @Value("${ratelimit.requests.per.minute:60}")
  private int requestsPerMinute;

  @Value("${ratelimit.requests.per.hour:1000}")
  private int requestsPerHour;

  @Value("${ratelimit.requests.per.day:5000}")
  private int requestsPerDay;

  /**
   * Redis template configured for rate limiting operations.
   *
   * @param connectionFactory Redis connection factory
   * @return configured RedisTemplate
   */
  @Bean
  public RedisTemplate<String, String> rateLimitRedisTemplate(
      RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Use String serializers for both keys and values
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setValueSerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);
    template.setHashValueSerializer(stringSerializer);

    template.afterPropertiesSet();
    return template;
  }

  /**
   * String Redis template for simple operations.
   *
   * @param connectionFactory Redis connection factory
   * @return configured StringRedisTemplate
   */
  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }

  // Getters for rate limit values
  public int getRequestsPerMinute() {
    return requestsPerMinute;
  }

  public int getRequestsPerHour() {
    return requestsPerHour;
  }

  public int getRequestsPerDay() {
    return requestsPerDay;
  }
}
