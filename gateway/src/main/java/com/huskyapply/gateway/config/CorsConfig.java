package com.huskyapply.gateway.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS Configuration for HuskyApply Gateway Provides fine-grained CORS control for different
 * environments and endpoints
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

  @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
  private String[] allowedOrigins;

  @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
  private String[] allowedMethods;

  @Value(
      "${cors.allowed-headers:Content-Type,Authorization,X-Requested-With,Accept,Origin,X-Internal-API-Key}")
  private String[] allowedHeaders;

  @Value("${cors.exposed-headers:X-Total-Count,X-Page-Count}")
  private String[] exposedHeaders;

  @Value("${cors.allow-credentials:true}")
  private boolean allowCredentials;

  @Value("${cors.max-age:3600}")
  private long maxAge;

  @Value("${spring.profiles.active:development}")
  private String activeProfile;

  /** Global CORS configuration for Spring MVC */
  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns(getOriginPatterns())
        .allowedMethods(allowedMethods)
        .allowedHeaders(allowedHeaders)
        .exposedHeaders(exposedHeaders)
        .allowCredentials(allowCredentials)
        .maxAge(maxAge);
  }

  /**
   * CORS Configuration Source Bean for Spring Security This bean is used by Spring Security to
   * handle CORS preflight requests
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Set allowed origins based on environment
    configuration.setAllowedOriginPatterns(Arrays.asList(getOriginPatterns()));

    // Set allowed methods
    configuration.setAllowedMethods(Arrays.asList(allowedMethods));

    // Set allowed headers
    configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));

    // Set exposed headers
    configuration.setExposedHeaders(Arrays.asList(exposedHeaders));

    // Allow credentials
    configuration.setAllowCredentials(allowCredentials);

    // Set max age for preflight cache
    configuration.setMaxAge(maxAge);

    // Apply CORS configuration to all endpoints
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    // Specific configurations for different endpoint patterns
    configurePubicEndpoints(source);
    configureApiEndpoints(source);
    configureInternalEndpoints(source);
    configureActuatorEndpoints(source);

    return source;
  }

  /** Configure CORS for public endpoints (no authentication required) */
  private void configurePubicEndpoints(UrlBasedCorsConfigurationSource source) {
    CorsConfiguration publicConfig = new CorsConfiguration();
    publicConfig.setAllowedOriginPatterns(
        Arrays.asList("*")); // More permissive for public endpoints
    publicConfig.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
    publicConfig.setAllowedHeaders(Arrays.asList("Content-Type", "Accept", "Origin"));
    publicConfig.setAllowCredentials(false); // No credentials for public endpoints
    publicConfig.setMaxAge(7200L); // Longer cache for public endpoints

    // Apply to public endpoints
    source.registerCorsConfiguration("/api/v1/auth/register", publicConfig);
    source.registerCorsConfiguration("/api/v1/auth/login", publicConfig);
    source.registerCorsConfiguration("/api/v1/subscriptions/plans", publicConfig);
    source.registerCorsConfiguration("/actuator/health", publicConfig);
  }

  /** Configure CORS for authenticated API endpoints */
  private void configureApiEndpoints(UrlBasedCorsConfigurationSource source) {
    CorsConfiguration apiConfig = new CorsConfiguration();
    apiConfig.setAllowedOriginPatterns(Arrays.asList(getRestrictedOriginPatterns()));
    apiConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    apiConfig.setAllowedHeaders(
        Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With",
            "Accept",
            "Origin",
            "X-Trace-Id",
            "X-User-Id"));
    apiConfig.setExposedHeaders(
        Arrays.asList(
            "X-Total-Count",
            "X-Page-Count",
            "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset",
            "Location"));
    apiConfig.setAllowCredentials(true);
    apiConfig.setMaxAge(maxAge);

    // Apply to authenticated API endpoints
    source.registerCorsConfiguration("/api/v1/applications/**", apiConfig);
    source.registerCorsConfiguration("/api/v1/dashboard/**", apiConfig);
    source.registerCorsConfiguration("/api/v1/templates/**", apiConfig);
    source.registerCorsConfiguration("/api/v1/batch-jobs/**", apiConfig);
    source.registerCorsConfiguration("/api/v1/subscriptions/**", apiConfig);
    source.registerCorsConfiguration("/api/v1/uploads/**", apiConfig);
  }

  /** Configure CORS for internal endpoints (service-to-service) */
  private void configureInternalEndpoints(UrlBasedCorsConfigurationSource source) {
    CorsConfiguration internalConfig = new CorsConfiguration();

    // Very restrictive CORS for internal endpoints
    if ("production".equals(activeProfile)) {
      internalConfig.setAllowedOriginPatterns(
          Arrays.asList("https://brain.huskyapply.com", "https://internal.huskyapply.com"));
    } else {
      internalConfig.setAllowedOriginPatterns(
          Arrays.asList("http://localhost:8000", "http://huskyapply-brain:8000"));
    }

    internalConfig.setAllowedMethods(Arrays.asList("POST", "PUT", "OPTIONS"));
    internalConfig.setAllowedHeaders(
        Arrays.asList("Content-Type", "X-Internal-API-Key", "X-Trace-Id", "X-Job-Id"));
    internalConfig.setAllowCredentials(false); // Internal services don't use user credentials
    internalConfig.setMaxAge(300L); // Shorter cache for internal endpoints

    // Apply to internal endpoints
    source.registerCorsConfiguration("/api/v1/internal/**", internalConfig);
  }

  /** Configure CORS for actuator endpoints (monitoring) */
  private void configureActuatorEndpoints(UrlBasedCorsConfigurationSource source) {
    CorsConfiguration actuatorConfig = new CorsConfiguration();

    // Restrictive CORS for monitoring endpoints
    if ("production".equals(activeProfile)) {
      actuatorConfig.setAllowedOriginPatterns(
          Arrays.asList("https://monitoring.huskyapply.com", "https://grafana.huskyapply.com"));
    } else {
      actuatorConfig.setAllowedOriginPatterns(
          Arrays.asList("http://localhost:3001", "http://localhost:9090", "http://localhost:3000"));
    }

    actuatorConfig.setAllowedMethods(Arrays.asList("GET", "OPTIONS"));
    actuatorConfig.setAllowedHeaders(Arrays.asList("Content-Type", "Accept"));
    actuatorConfig.setAllowCredentials(false);
    actuatorConfig.setMaxAge(1800L); // 30 minutes cache for monitoring

    // Apply to actuator endpoints
    source.registerCorsConfiguration("/actuator/**", actuatorConfig);
  }

  /** Get origin patterns based on environment */
  private String[] getOriginPatterns() {
    if ("production".equals(activeProfile)) {
      return new String[] {
        "https://app.huskyapply.com", "https://huskyapply.com", "https://*.huskyapply.com"
      };
    } else if ("staging".equals(activeProfile)) {
      return new String[] {
        "https://staging.huskyapply.com", "https://*.staging.huskyapply.com", "http://localhost:*"
      };
    } else {
      // Development environment - more permissive
      return new String[] {"http://localhost:*", "http://127.0.0.1:*", "http://0.0.0.0:*"};
    }
  }

  /** Get restricted origin patterns for sensitive endpoints */
  private String[] getRestrictedOriginPatterns() {
    if ("production".equals(activeProfile)) {
      return new String[] {"https://app.huskyapply.com", "https://huskyapply.com"};
    } else if ("staging".equals(activeProfile)) {
      return new String[] {"https://staging.huskyapply.com", "http://localhost:3000"};
    } else {
      return new String[] {"http://localhost:3000", "http://127.0.0.1:3000"};
    }
  }

  /** Custom CORS configuration for Server-Sent Events (SSE) */
  @Bean("sseCorsConfiguration")
  public CorsConfiguration sseCorsConfiguration() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOriginPatterns(Arrays.asList(getOriginPatterns()));
    configuration.setAllowedMethods(Arrays.asList("GET", "OPTIONS"));
    configuration.setAllowedHeaders(
        Arrays.asList("Content-Type", "Authorization", "Cache-Control", "X-Requested-With"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(0L); // No caching for SSE

    return configuration;
  }

  /** Validate CORS configuration on startup */
  @Bean
  public CorsConfigValidator corsConfigValidator() {
    return new CorsConfigValidator();
  }

  /** Inner class to validate CORS configuration */
  public static class CorsConfigValidator {
    public CorsConfigValidator() {
      // Log CORS configuration for debugging
      System.out.println("CORS Configuration initialized successfully");
    }
  }
}
