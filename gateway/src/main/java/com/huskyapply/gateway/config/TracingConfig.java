package com.huskyapply.gateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for distributed tracing and correlation IDs.
 *
 * <p>This configuration integrates OpenTelemetry for distributed tracing across microservices, with
 * support for Jaeger and OTLP exporters.
 */
@Configuration
public class TracingConfig implements WebMvcConfigurer {

  public static final String TRACE_ID_HEADER = "X-Trace-ID";
  public static final String SPAN_ID_HEADER = "X-Span-ID";
  public static final String USER_ID_HEADER = "X-User-ID";

  @Value("${spring.application.name:huskyapply-gateway}")
  private String serviceName;

  @Value("${spring.application.version:0.0.1-SNAPSHOT}")
  private String serviceVersion;

  @Value("${management.tracing.jaeger.endpoint:http://localhost:14250}")
  private String jaegerEndpoint;

  @Value("${management.tracing.enabled:true}")
  private boolean tracingEnabled;

  @Value("${management.tracing.sampling.probability:0.1}")
  private double samplingProbability;

  @Bean
  public OpenTelemetry openTelemetry() {
    if (!tracingEnabled) {
      return OpenTelemetry.noop();
    }

    Resource resource =
        Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
            .put(ResourceAttributes.SERVICE_INSTANCE_ID, generateInstanceId())
            .build();

    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(createSpanExporter()).build())
            .setResource(resource)
            .setSampler(createSampler())
            .build();

    return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
  }

  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer(serviceName, serviceVersion);
  }

  private io.opentelemetry.sdk.trace.export.SpanExporter createSpanExporter() {
    if (jaegerEndpoint.contains("jaeger")) {
      return JaegerGrpcSpanExporter.builder()
          .setEndpoint(jaegerEndpoint)
          .setTimeout(Duration.ofSeconds(2))
          .build();
    } else {
      return OtlpGrpcSpanExporter.builder()
          .setEndpoint(jaegerEndpoint)
          .setTimeout(Duration.ofSeconds(2))
          .build();
    }
  }

  private Sampler createSampler() {
    if (samplingProbability >= 1.0) {
      return Sampler.traceIdRatioBased(1.0);
    } else if (samplingProbability <= 0.0) {
      return Sampler.traceIdRatioBased(0.01);
    } else {
      return Sampler.traceIdRatioBased(samplingProbability);
    }
  }

  private String generateInstanceId() {
    return System.getProperty("HOSTNAME", "gateway-" + System.currentTimeMillis());
  }

  /** Filter to add trace ID to all requests. */
  @Bean
  public TracingFilter tracingFilter() {
    return new TracingFilter();
  }

  /** Filter that adds tracing information to requests and MDC. */
  public static class TracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain)
        throws ServletException, IOException {

      try {
        // Get or generate trace ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
          traceId = UUID.randomUUID().toString();
        }

        // Generate span ID for this service
        String spanId = UUID.randomUUID().toString().substring(0, 8);

        // Get user ID if available (from JWT token)
        String userId = extractUserId(request);

        // Add to MDC for logging
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        MDC.put("service", "gateway");
        if (userId != null) {
          MDC.put("userId", userId);
        }

        // Add to response headers for downstream services
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);
        if (userId != null) {
          response.setHeader(USER_ID_HEADER, userId);
        }

        // Add request info to MDC
        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        MDC.put("remoteAddr", getClientIpAddress(request));

        filterChain.doFilter(request, response);

      } finally {
        // Clean up MDC
        MDC.clear();
      }
    }

    private String extractUserId(HttpServletRequest request) {
      String authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        try {
          // This would normally parse the JWT token
          // For now, we'll extract it from a custom header if available
          return request.getHeader("X-User-Email");
        } catch (Exception e) {
          // Ignore JWT parsing errors
        }
      }
      return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
      String xForwardedFor = request.getHeader("X-Forwarded-For");
      if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
        return xForwardedFor.split(",")[0].trim();
      }

      String xRealIp = request.getHeader("X-Real-IP");
      if (xRealIp != null && !xRealIp.isEmpty()) {
        return xRealIp;
      }

      return request.getRemoteAddr();
    }
  }
}
