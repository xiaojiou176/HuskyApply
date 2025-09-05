package com.huskyapply.gateway.service;

import com.huskyapply.gateway.config.RateLimitConfig;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Rate limiting service using Redis sliding window approach.
 *
 * <p>This service implements rate limiting using Redis to track request counts for different time
 * windows (minute, hour, day) per user.
 */
@Service
public class RateLimitService {

  private final StringRedisTemplate redisTemplate;
  private final RateLimitConfig rateLimitConfig;

  private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";
  private static final DateTimeFormatter MINUTE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm");
  private static final DateTimeFormatter HOUR_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd:HH");
  private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public RateLimitService(StringRedisTemplate redisTemplate, RateLimitConfig rateLimitConfig) {
    this.redisTemplate = redisTemplate;
    this.rateLimitConfig = rateLimitConfig;
  }

  /**
   * Checks if a request is allowed for the given user.
   *
   * @param userId the user identifier (email or user ID)
   * @return true if request is allowed, false if rate limit exceeded
   */
  public boolean isRequestAllowed(String userId) {
    LocalDateTime now = LocalDateTime.now();

    // Check all time windows
    return isAllowedForMinute(userId, now)
        && isAllowedForHour(userId, now)
        && isAllowedForDay(userId, now);
  }

  /**
   * Records a successful request for the user across all time windows.
   *
   * @param userId the user identifier
   */
  public void recordRequest(String userId) {
    LocalDateTime now = LocalDateTime.now();

    // Increment counters for all time windows
    incrementCounter(getMinuteKey(userId, now), Duration.ofMinutes(2));
    incrementCounter(getHourKey(userId, now), Duration.ofHours(2));
    incrementCounter(getDayKey(userId, now), Duration.ofDays(2));
  }

  /**
   * Gets the current request count for a user in the current minute.
   *
   * @param userId the user identifier
   * @return current request count
   */
  public int getCurrentMinuteCount(String userId) {
    String key = getMinuteKey(userId, LocalDateTime.now());
    String count = redisTemplate.opsForValue().get(key);
    return count != null ? Integer.parseInt(count) : 0;
  }

  /**
   * Gets the current request count for a user in the current hour.
   *
   * @param userId the user identifier
   * @return current request count
   */
  public int getCurrentHourCount(String userId) {
    String key = getHourKey(userId, LocalDateTime.now());
    String count = redisTemplate.opsForValue().get(key);
    return count != null ? Integer.parseInt(count) : 0;
  }

  /**
   * Gets the current request count for a user in the current day.
   *
   * @param userId the user identifier
   * @return current request count
   */
  public int getCurrentDayCount(String userId) {
    String key = getDayKey(userId, LocalDateTime.now());
    String count = redisTemplate.opsForValue().get(key);
    return count != null ? Integer.parseInt(count) : 0;
  }

  private boolean isAllowedForMinute(String userId, LocalDateTime now) {
    String key = getMinuteKey(userId, now);
    return isUnderLimit(key, rateLimitConfig.getRequestsPerMinute());
  }

  private boolean isAllowedForHour(String userId, LocalDateTime now) {
    String key = getHourKey(userId, now);
    return isUnderLimit(key, rateLimitConfig.getRequestsPerHour());
  }

  private boolean isAllowedForDay(String userId, LocalDateTime now) {
    String key = getDayKey(userId, now);
    return isUnderLimit(key, rateLimitConfig.getRequestsPerDay());
  }

  private boolean isUnderLimit(String key, int limit) {
    String currentCount = redisTemplate.opsForValue().get(key);
    int count = currentCount != null ? Integer.parseInt(currentCount) : 0;
    return count < limit;
  }

  private void incrementCounter(String key, Duration expiration) {
    redisTemplate.opsForValue().increment(key);
    redisTemplate.expire(key, expiration);
  }

  private String getMinuteKey(String userId, LocalDateTime dateTime) {
    return RATE_LIMIT_KEY_PREFIX + "minute:" + userId + ":" + dateTime.format(MINUTE_FORMATTER);
  }

  private String getHourKey(String userId, LocalDateTime dateTime) {
    return RATE_LIMIT_KEY_PREFIX + "hour:" + userId + ":" + dateTime.format(HOUR_FORMATTER);
  }

  private String getDayKey(String userId, LocalDateTime dateTime) {
    return RATE_LIMIT_KEY_PREFIX + "day:" + userId + ":" + dateTime.format(DAY_FORMATTER);
  }
}
