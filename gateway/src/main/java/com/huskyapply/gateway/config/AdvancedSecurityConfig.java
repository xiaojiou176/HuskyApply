package com.huskyapply.gateway.config;

import com.huskyapply.gateway.security.AdvancedJwtAuthenticationFilter;
import com.huskyapply.gateway.security.ApiRequestSignatureFilter;
import com.huskyapply.gateway.security.BruteForceProtectionFilter;
import com.huskyapply.gateway.security.ContentSecurityPolicyFilter;
import com.huskyapply.gateway.security.DeviceTrackingFilter;
import com.huskyapply.gateway.security.ThreatDetectionFilter;
import com.huskyapply.gateway.service.SecurityAuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Advanced Security Configuration with Enterprise-Grade Features
 *
 * <p>Features: - Advanced threat detection and prevention - Brute force protection with intelligent
 * blocking - Device tracking and anomaly detection - Content Security Policy (CSP) headers - API
 * request signing and validation - Advanced JWT security with refresh tokens - Security audit
 * logging and metrics - Zero-trust security model
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class AdvancedSecurityConfig {

  @Value("${security.jwt.secret}")
  private String jwtSecret;

  @Value("${security.jwt.expiration:86400}")
  private long jwtExpirationSeconds;

  @Value("${security.brute-force.max-attempts:5}")
  private int maxLoginAttempts;

  @Value("${security.brute-force.lockout-duration:900}")
  private long lockoutDurationSeconds;

  @Value("${security.device-tracking.enabled:true}")
  private boolean deviceTrackingEnabled;

  @Value("${security.threat-detection.enabled:true}")
  private boolean threatDetectionEnabled;

  @Value("${security.api-signing.enabled:true}")
  private boolean apiSigningEnabled;

  @Autowired private SecurityAuditService securityAuditService;

  @Autowired private MeterRegistry meterRegistry;

  // Security metrics
  private final Counter authenticationAttempts;
  private final Counter authenticationFailures;
  private final Counter threatDetections;
  private final Counter bruteForceBlocks;
  private final Timer requestProcessingTime;

  public AdvancedSecurityConfig(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    // Initialize security metrics
    this.authenticationAttempts =
        Counter.builder("security.authentication.attempts")
            .description("Total authentication attempts")
            .register(meterRegistry);

    this.authenticationFailures =
        Counter.builder("security.authentication.failures")
            .description("Failed authentication attempts")
            .register(meterRegistry);

    this.threatDetections =
        Counter.builder("security.threat.detections")
            .description("Threats detected and blocked")
            .register(meterRegistry);

    this.bruteForceBlocks =
        Counter.builder("security.brute_force.blocks")
            .description("Brute force attacks blocked")
            .register(meterRegistry);

    this.requestProcessingTime =
        Timer.builder("security.request.processing_time")
            .description("Time spent processing security checks")
            .register(meterRegistry);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    Timer.Sample sample = Timer.start(meterRegistry);

    http
        // Disable CSRF for API usage (using JWT tokens)
        .csrf(AbstractHttpConfigurer::disable)

        // Configure session management for stateless authentication
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .maximumSessions(3) // Allow max 3 concurrent sessions per user
                    .maxSessionsPreventsLogin(false))

        // Configure CORS with strict policy
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // Configure security headers
        .headers(
            headers ->
                headers
                    .frameOptions()
                    .deny() // Prevent clickjacking
                    .contentTypeOptions()
                    .and() // Prevent MIME sniffing
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.maxAgeInSeconds(31536000) // 1 year
                                .includeSubdomains(true)
                                .preload(true))
                    .referrerPolicy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                    .and()
                    .addHeaderWriter(
                        (request, response) -> {
                          // Advanced security headers
                          response.addHeader("X-Content-Type-Options", "nosniff");
                          response.addHeader("X-Frame-Options", "DENY");
                          response.addHeader("X-XSS-Protection", "1; mode=block");
                          response.addHeader(
                              "Strict-Transport-Security",
                              "max-age=31536000; includeSubDomains; preload");
                          response.addHeader(
                              "Permissions-Policy", "geolocation=(), microphone=(), camera=()");

                          // Content Security Policy
                          response.addHeader(
                              "Content-Security-Policy",
                              "default-src 'self'; "
                                  + "script-src 'self' 'unsafe-inline' https://js.stripe.com; "
                                  + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                  + "font-src 'self' https://fonts.gstatic.com; "
                                  + "img-src 'self' data: https:; "
                                  + "connect-src 'self' https://api.stripe.com; "
                                  + "frame-src https://js.stripe.com; "
                                  + "object-src 'none'; "
                                  + "base-uri 'self'; "
                                  + "form-action 'self';");
                        }))

        // Configure authorization rules
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers("/api/v1/subscriptions/plans")
                    .permitAll()
                    .requestMatchers("/healthz", "/metrics", "/actuator/**")
                    .permitAll()

                    // Admin-only endpoints
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/internal/**")
                    .hasRole("SYSTEM")

                    // Protected endpoints require authentication
                    .requestMatchers("/api/v1/**")
                    .authenticated()

                    // All other requests require authentication
                    .anyRequest()
                    .authenticated());

    // Add custom security filters in correct order
    if (threatDetectionEnabled) {
      http.addFilterBefore(threatDetectionFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    if (deviceTrackingEnabled) {
      http.addFilterBefore(deviceTrackingFilter(), UsernamePasswordAuthenticationFilter.class);
    }

    http.addFilterBefore(bruteForceProtectionFilter(), UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(
        advancedJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(contentSecurityPolicyFilter(), UsernamePasswordAuthenticationFilter.class);

    if (apiSigningEnabled) {
      http.addFilterAfter(apiRequestSignatureFilter(), AdvancedJwtAuthenticationFilter.class);
    }

    sample.stop(requestProcessingTime);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Use Argon2 for maximum security (OWASP recommended)
    return new Argon2PasswordEncoder(
        16, // Salt length
        32, // Hash length
        1, // Parallelism
        4096, // Memory cost (4MB)
        3 // Time cost (iterations)
        );
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // Strict CORS configuration
    configuration.setAllowedOriginPatterns(
        List.of(
            "https://huskyapply.com",
            "https://*.huskyapply.com",
            "http://localhost:3000", // Development only
            "http://localhost:8080" // Development only
            ));

    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

    configuration.setAllowedHeaders(
        Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-API-Key",
            "X-Request-ID",
            "X-Device-ID"));

    configuration.setExposedHeaders(
        Arrays.asList(
            "X-Request-ID", "X-Rate-Limit-Remaining",
            "X-Rate-Limit-Reset", "Content-Length"));

    configuration.setAllowCredentials(true);
    configuration.setMaxAge(Duration.ofHours(1)); // Cache preflight for 1 hour

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public ThreatDetectionFilter threatDetectionFilter() {
    return new ThreatDetectionFilter(securityAuditService, threatDetections);
  }

  @Bean
  public DeviceTrackingFilter deviceTrackingFilter() {
    return new DeviceTrackingFilter(securityAuditService, meterRegistry);
  }

  @Bean
  public BruteForceProtectionFilter bruteForceProtectionFilter() {
    return new BruteForceProtectionFilter(
        maxLoginAttempts, lockoutDurationSeconds, securityAuditService, bruteForceBlocks);
  }

  @Bean
  public AdvancedJwtAuthenticationFilter advancedJwtAuthenticationFilter() {
    return new AdvancedJwtAuthenticationFilter(
        jwtSecret,
        jwtExpirationSeconds,
        securityAuditService,
        authenticationAttempts,
        authenticationFailures);
  }

  @Bean
  public ContentSecurityPolicyFilter contentSecurityPolicyFilter() {
    return new ContentSecurityPolicyFilter(securityAuditService);
  }

  @Bean
  public ApiRequestSignatureFilter apiRequestSignatureFilter() {
    return new ApiRequestSignatureFilter(securityAuditService, meterRegistry);
  }
}
