package com.huskyapply.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huskyapply.gateway.service.JwtService;
import com.huskyapply.gateway.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting filter that applies rate limits to authenticated API requests.
 *
 * <p>This filter checks rate limits for authenticated users and returns HTTP 429 (Too Many
 * Requests) when limits are exceeded.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimitService rateLimitService;
  private final JwtService jwtService;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(
      RateLimitService rateLimitService, JwtService jwtService, ObjectMapper objectMapper) {
    this.rateLimitService = rateLimitService;
    this.jwtService = jwtService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String requestURI = request.getRequestURI();

    // Skip rate limiting for certain endpoints
    if (shouldSkipRateLimit(requestURI)) {
      filterChain.doFilter(request, response);
      return;
    }

    // Extract user from JWT token
    String userId = extractUserId(request);
    if (userId == null) {
      // No user identified, skip rate limiting (let security handle auth)
      filterChain.doFilter(request, response);
      return;
    }

    // Check rate limit
    if (!rateLimitService.isRequestAllowed(userId)) {
      logger.warn("Rate limit exceeded for user: {} on endpoint: {}", userId, requestURI);

      // Get current counts for response headers
      int minuteCount = rateLimitService.getCurrentMinuteCount(userId);
      int hourCount = rateLimitService.getCurrentHourCount(userId);
      int dayCount = rateLimitService.getCurrentDayCount(userId);

      // Return 429 Too Many Requests
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setHeader("X-RateLimit-Minute", String.valueOf(minuteCount));
      response.setHeader("X-RateLimit-Hour", String.valueOf(hourCount));
      response.setHeader("X-RateLimit-Day", String.valueOf(dayCount));
      response.setHeader("Retry-After", "60"); // Suggest retry after 60 seconds

      Map<String, Object> errorResponse =
          Map.of(
              "error", "Too Many Requests",
              "message", "Rate limit exceeded. Please try again later.",
              "details",
                  Map.of(
                      "requestsThisMinute", minuteCount,
                      "requestsThisHour", hourCount,
                      "requestsThisDay", dayCount));

      response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
      return;
    }

    // Record the request
    rateLimitService.recordRequest(userId);

    // Add rate limit info to response headers
    response.setHeader(
        "X-RateLimit-Minute", String.valueOf(rateLimitService.getCurrentMinuteCount(userId)));
    response.setHeader(
        "X-RateLimit-Hour", String.valueOf(rateLimitService.getCurrentHourCount(userId)));
    response.setHeader(
        "X-RateLimit-Day", String.valueOf(rateLimitService.getCurrentDayCount(userId)));

    filterChain.doFilter(request, response);
  }

  private boolean shouldSkipRateLimit(String requestURI) {
    return requestURI.startsWith("/api/v1/auth/")
        || // Authentication endpoints
        requestURI.startsWith("/api/v1/internal/")
        || // Internal APIs
        requestURI.equals("/health")
        || // Health check
        requestURI.equals("/actuator/health")
        || // Actuator health
        requestURI.startsWith("/actuator/"); // Other actuator endpoints
  }

  private String extractUserId(HttpServletRequest request) {
    try {
      String authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        return jwtService.extractUsername(token);
      }
    } catch (Exception e) {
      logger.debug("Could not extract user ID from request: {}", e.getMessage());
    }
    return null;
  }
}
