package com.huskyapply.gateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configuration for custom metrics and monitoring.
 *
 * <p>This configuration adds custom metrics for rate limiting, business logic monitoring, and API
 * usage tracking.
 */
@Configuration
public class MetricsConfig {

  @Autowired private MeterRegistry meterRegistry;

  /** Custom metrics filter for detailed request monitoring. */
  @Bean
  public CustomMetricsFilter customMetricsFilter() {
    return new CustomMetricsFilter(meterRegistry);
  }

  /** Filter to collect custom metrics for requests. */
  public static class CustomMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public CustomMetricsFilter(MeterRegistry meterRegistry) {
      this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain)
        throws ServletException, IOException {

      String endpoint = getEndpointName(request.getRequestURI());
      String method = request.getMethod();

      // Start timing the request
      Timer.Sample sample = Timer.start(meterRegistry);

      try {
        filterChain.doFilter(request, response);
      } finally {
        // Record the timer with tags
        sample.stop(
            Timer.builder("http.requests.custom")
                .description("Custom HTTP request timing")
                .tag("method", method)
                .tag("endpoint", endpoint)
                .tag("status", String.valueOf(response.getStatus()))
                .register(meterRegistry));

        // Count rate limit rejections
        if (response.getStatus() == 429) {
          meterRegistry
              .counter("ratelimit.rejections", "endpoint", endpoint, "method", method)
              .increment();
        }

        // Count authentication failures
        if (response.getStatus() == 401 || response.getStatus() == 403) {
          meterRegistry
              .counter(
                  "auth.failures",
                  "endpoint",
                  endpoint,
                  "status",
                  String.valueOf(response.getStatus()))
              .increment();
        }

        // Count successful job submissions
        if (endpoint.equals("applications")
            && method.equals("POST")
            && response.getStatus() == 202) {
          meterRegistry.counter("jobs.submitted").increment();
        }

        // Count SSE connections
        if (endpoint.equals("stream") && method.equals("GET") && response.getStatus() == 200) {
          meterRegistry.counter("sse.connections").increment();
        }
      }
    }

    private String getEndpointName(String uri) {
      if (uri.startsWith("/api/v1/auth/")) return "auth";
      if (uri.startsWith("/api/v1/applications/") && uri.endsWith("/stream")) return "stream";
      if (uri.startsWith("/api/v1/applications/") && uri.endsWith("/artifact")) return "artifact";
      if (uri.startsWith("/api/v1/applications")) return "applications";
      if (uri.startsWith("/api/v1/uploads/")) return "uploads";
      if (uri.startsWith("/api/v1/internal/")) return "internal";
      if (uri.startsWith("/actuator/")) return "actuator";
      return "other";
    }
  }
}
