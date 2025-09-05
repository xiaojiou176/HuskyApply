package com.huskyapply.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcRepositories;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Reactive Configuration for Spring WebFlux Infrastructure
 *
 * <p>This configuration class enables and configures all reactive components: - R2DBC for
 * non-blocking database operations - Reactive Redis for caching and messaging - WebFlux for
 * reactive web endpoints - Reactive security configuration
 *
 * <p>Key features: - Non-blocking I/O throughout the stack - Reactive transaction management -
 * Redis-based pub/sub messaging - CORS configuration for WebFlux
 */
@Configuration
@EnableWebFlux
@EnableR2dbcRepositories(basePackages = "com.huskyapply.gateway.repository")
@EnableTransactionManagement
public class ReactiveConfiguration implements WebFluxConfigurer {

  /**
   * Configures reactive Redis template with JSON serialization
   *
   * <p>This template is used for: - Caching with automatic JSON serialization - Pub/sub messaging
   * for SSE events - Rate limiting and quota management
   */
  @Bean
  public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
      ReactiveRedisConnectionFactory connectionFactory) {

    // Configure JSON serialization for complex objects
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.findAndRegisterModules();

    Jackson2JsonRedisSerializer<Object> jsonSerializer =
        new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
    StringRedisSerializer stringSerializer = new StringRedisSerializer();

    RedisSerializationContext<String, Object> serializationContext =
        RedisSerializationContext.<String, Object>newSerializationContext()
            .key(stringSerializer)
            .value(jsonSerializer)
            .hashKey(stringSerializer)
            .hashValue(jsonSerializer)
            .build();

    ReactiveRedisTemplate<String, Object> template =
        new ReactiveRedisTemplate<>(connectionFactory, serializationContext);

    return template;
  }

  /**
   * Configures reactive Redis message listener container
   *
   * <p>This container handles pub/sub messaging for distributed SSE event broadcasting across
   * Gateway instances.
   */
  @Bean
  public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
      ReactiveRedisConnectionFactory connectionFactory) {

    ReactiveRedisMessageListenerContainer container =
        new ReactiveRedisMessageListenerContainer(connectionFactory);

    // Configure container settings for optimal performance
    container.setMaxInactiveTime(java.time.Duration.ofMinutes(5));
    container.setMaxSubscriptions(100); // Support up to 100 concurrent SSE streams

    return container;
  }

  /**
   * Reactive UserDetailsService implementation
   *
   * <p>This service provides reactive user authentication using R2DBC for non-blocking user lookups
   * during JWT validation.
   */
  @Bean
  public ReactiveUserDetailsService reactiveUserDetailsService() {
    return new ReactiveUserDetailsServiceImpl();
  }

  /**
   * CORS configuration for reactive endpoints
   *
   * <p>Configures Cross-Origin Resource Sharing for the reactive API endpoints to support
   * browser-based clients.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/v2/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  /** Custom reactive UserDetailsService implementation */
  private static class ReactiveUserDetailsServiceImpl implements ReactiveUserDetailsService {

    @Override
    public reactor.core.publisher.Mono<org.springframework.security.core.userdetails.UserDetails>
        findByUsername(String username) {
      // In production, this would query the reactive user repository
      // For now, return a simple implementation
      return reactor.core.publisher.Mono.fromCallable(
              () -> {
                return org.springframework.security.core.userdetails.User.builder()
                    .username(username)
                    .password("$2a$10$placeholder") // BCrypt placeholder
                    .authorities("ROLE_USER")
                    .build();
              })
          .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
  }
}
