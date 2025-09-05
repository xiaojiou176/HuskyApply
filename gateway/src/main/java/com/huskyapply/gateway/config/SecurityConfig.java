package com.huskyapply.gateway.config;

import com.huskyapply.gateway.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Gateway service.
 *
 * <p>This configuration establishes the foundational security layer for JWT authentication. It
 * configures HTTP security rules, session management, and password encoding.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final UserRepository userRepository;
  private final JwtAuthenticationFilter jwtAuthFilter;
  private final InternalApiFilter internalApiFilter;
  private final RateLimitFilter rateLimitFilter;
  private final MetricsConfig.CustomMetricsFilter customMetricsFilter;
  private final TracingConfig.TracingFilter tracingFilter;

  public SecurityConfig(
      UserRepository userRepository,
      @Lazy JwtAuthenticationFilter jwtAuthFilter,
      InternalApiFilter internalApiFilter,
      RateLimitFilter rateLimitFilter,
      MetricsConfig.CustomMetricsFilter customMetricsFilter,
      TracingConfig.TracingFilter tracingFilter) {
    this.userRepository = userRepository;
    this.jwtAuthFilter = jwtAuthFilter;
    this.internalApiFilter = internalApiFilter;
    this.rateLimitFilter = rateLimitFilter;
    this.customMetricsFilter = customMetricsFilter;
    this.tracingFilter = tracingFilter;
  }

  /**
   * Configures the password encoder for secure password hashing.
   *
   * @return BCryptPasswordEncoder instance for password hashing
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Configures the security filter chain for HTTP requests.
   *
   * <p>Sets up: - CSRF protection disabled (stateless JWT authentication) - Stateless session
   * management - JWT authentication filter integration - Authorization rules for authentication
   * endpoints and protected resources
   *
   * @param http HttpSecurity configuration
   * @param authenticationProvider the authentication provider to use
   * @return SecurityFilterChain configured for JWT authentication
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/api/v1/internal/**")
                    .hasRole("INTERNAL_SERVICE")
                    .anyRequest()
                    .authenticated())
        .authenticationProvider(authenticationProvider)
        .addFilterBefore(tracingFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(internalApiFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
        .addFilterAfter(customMetricsFilter, RateLimitFilter.class);

    return http.build();
  }

  /**
   * Configures the UserDetailsService for loading user-specific data.
   *
   * <p>This service is used by Spring Security to load user details during authentication. It looks
   * up users by email from the database.
   *
   * @return UserDetailsService implementation
   */
  @Bean
  public UserDetailsService userDetailsService() {
    return username ->
        userRepository
            .findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }

  /**
   * Configures the authentication provider.
   *
   * <p>Sets up the DAO authentication provider with the custom UserDetailsService and password
   * encoder for proper authentication handling.
   *
   * @return AuthenticationProvider configured with UserDetailsService and PasswordEncoder
   */
  @Bean
  public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService());
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
  }

  /**
   * Exposes the AuthenticationManager as a Bean.
   *
   * <p>This is required for the AuthService to perform authentication operations programmatically.
   *
   * @param config AuthenticationConfiguration
   * @return AuthenticationManager
   * @throws Exception if configuration fails
   */
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
