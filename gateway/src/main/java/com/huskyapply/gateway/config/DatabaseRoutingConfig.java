package com.huskyapply.gateway.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;

/**
 * Database routing configuration for read-write splitting with automatic failover. Implements
 * intelligent routing between master (write) and replica (read) databases.
 */
@Configuration
public class DatabaseRoutingConfig {

  @Value("${spring.r2dbc.master.url:r2dbc:postgresql://localhost:5432/huskyapply}")
  private String masterUrl;

  @Value("${spring.r2dbc.read1.url:r2dbc:postgresql://localhost:5433/huskyapply}")
  private String read1Url;

  @Value("${spring.r2dbc.read2.url:r2dbc:postgresql://localhost:5434/huskyapply}")
  private String read2Url;

  @Value("${spring.r2dbc.master.username:husky}")
  private String username;

  @Value("${spring.r2dbc.master.password:husky}")
  private String password;

  @Value("${database.routing.enabled:true}")
  private boolean routingEnabled;

  @Value("${database.routing.load-balancer.strategy:round-robin}")
  private String loadBalancerStrategy;

  /**
   * Master database connection factory for write operations. Optimized for ACID transactions and
   * data consistency.
   */
  @Bean(name = "masterConnectionFactory")
  public ConnectionFactory masterConnectionFactory() {
    PostgresqlConnectionConfiguration masterConfig =
        PostgresqlConnectionConfiguration.builder()
            .host(extractHost(masterUrl))
            .port(extractPort(masterUrl))
            .database(extractDatabase(masterUrl))
            .username(username)
            .password(password)
            .schema("public")
            .applicationName("huskyapply-gateway-master")
            .connectTimeout(Duration.ofSeconds(30))
            .statementTimeout(Duration.ofSeconds(60))
            .lockWaitTimeout(Duration.ofSeconds(30))
            .build();

    PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(masterConfig);

    ConnectionPoolConfiguration poolConfig =
        ConnectionPoolConfiguration.builder(connectionFactory)
            .name("master-pool")
            .initialSize(5)
            .maxSize(15)
            .maxIdleTime(Duration.ofMinutes(30))
            .maxAcquireTime(Duration.ofSeconds(60))
            .maxCreateConnectionTime(Duration.ofSeconds(30))
            .validationQuery("SELECT 1")
            .build();

    return new ConnectionPool(poolConfig);
  }

  /**
   * Read replica 1 connection factory for read operations. Optimized for query performance and
   * connection pooling.
   */
  @Bean(name = "read1ConnectionFactory")
  public ConnectionFactory read1ConnectionFactory() {
    PostgresqlConnectionConfiguration read1Config =
        PostgresqlConnectionConfiguration.builder()
            .host(extractHost(read1Url))
            .port(extractPort(read1Url))
            .database(extractDatabase(read1Url))
            .username(username)
            .password(password)
            .schema("public")
            .applicationName("huskyapply-gateway-read1")
            .connectTimeout(Duration.ofSeconds(20))
            .statementTimeout(Duration.ofSeconds(120))
            .options(
                org.springframework.util.CollectionUtils.toMultiValueMap(
                    org.springframework.util.StringUtils.commaDelimitedListToStringArray(
                        "default_transaction_isolation=read committed,"
                            + "application_name=huskyapply-read-replica-1,"
                            + "tcp_keepalives_idle=300,"
                            + "tcp_keepalives_interval=30,"
                            + "tcp_keepalives_count=3")))
            .build();

    PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(read1Config);

    ConnectionPoolConfiguration poolConfig =
        ConnectionPoolConfiguration.builder(connectionFactory)
            .name("read1-pool")
            .initialSize(8)
            .maxSize(25)
            .maxIdleTime(Duration.ofMinutes(45))
            .maxAcquireTime(Duration.ofSeconds(30))
            .maxCreateConnectionTime(Duration.ofSeconds(20))
            .validationQuery("SELECT 1")
            .build();

    return new ConnectionPool(poolConfig);
  }

  /** Read replica 2 connection factory for load balancing read operations. */
  @Bean(name = "read2ConnectionFactory")
  public ConnectionFactory read2ConnectionFactory() {
    PostgresqlConnectionConfiguration read2Config =
        PostgresqlConnectionConfiguration.builder()
            .host(extractHost(read2Url))
            .port(extractPort(read2Url))
            .database(extractDatabase(read2Url))
            .username(username)
            .password(password)
            .schema("public")
            .applicationName("huskyapply-gateway-read2")
            .connectTimeout(Duration.ofSeconds(20))
            .statementTimeout(Duration.ofSeconds(120))
            .build();

    PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(read2Config);

    ConnectionPoolConfiguration poolConfig =
        ConnectionPoolConfiguration.builder(connectionFactory)
            .name("read2-pool")
            .initialSize(8)
            .maxSize(25)
            .maxIdleTime(Duration.ofMinutes(45))
            .maxAcquireTime(Duration.ofSeconds(30))
            .maxCreateConnectionTime(Duration.ofSeconds(20))
            .validationQuery("SELECT 1")
            .build();

    return new ConnectionPool(poolConfig);
  }

  /** Database routing service for intelligent read-write splitting. */
  @Bean
  @Primary
  public DatabaseRoutingService databaseRoutingService(
      ConnectionFactory masterConnectionFactory,
      ConnectionFactory read1ConnectionFactory,
      ConnectionFactory read2ConnectionFactory) {

    return new DatabaseRoutingService(
        masterConnectionFactory,
        List.of(read1ConnectionFactory, read2ConnectionFactory),
        loadBalancerStrategy);
  }

  /** Primary R2DBC template using the routing service. */
  @Bean(name = "routingR2dbcEntityTemplate")
  @Primary
  public R2dbcEntityTemplate routingR2dbcEntityTemplate(DatabaseRoutingService routingService) {
    return new R2dbcEntityTemplate(routingService);
  }

  /** Master-only R2DBC template for explicit write operations. */
  @Bean(name = "masterR2dbcEntityTemplate")
  public R2dbcEntityTemplate masterR2dbcEntityTemplate(ConnectionFactory masterConnectionFactory) {
    return new R2dbcEntityTemplate(masterConnectionFactory);
  }

  /** Read-only R2DBC template for explicit read operations. */
  @Bean(name = "readR2dbcEntityTemplate")
  public R2dbcEntityTemplate readR2dbcEntityTemplate(DatabaseRoutingService routingService) {
    return new R2dbcEntityTemplate(routingService.getReadConnectionFactory());
  }

  /** Database health monitoring service. */
  @Bean
  public DatabaseHealthMonitor databaseHealthMonitor(DatabaseRoutingService routingService) {
    return new DatabaseHealthMonitor(routingService);
  }

  // Utility methods for URL parsing
  private String extractHost(String url) {
    // Extract host from r2dbc:postgresql://host:port/database
    String[] parts = url.split("//")[1].split(":");
    return parts[0];
  }

  private int extractPort(String url) {
    try {
      String[] parts = url.split("//")[1].split("/")[0].split(":");
      return parts.length > 1 ? Integer.parseInt(parts[1]) : 5432;
    } catch (Exception e) {
      return 5432;
    }
  }

  private String extractDatabase(String url) {
    String[] parts = url.split("//")[1].split("/");
    return parts.length > 1 ? parts[1] : "huskyapply";
  }

  /** Custom connection factory that routes based on operation type. */
  public static class DatabaseRoutingService implements ConnectionFactory {

    private final ConnectionFactory masterConnectionFactory;
    private final List<ConnectionFactory> readConnectionFactories;
    private final String loadBalancerStrategy;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public DatabaseRoutingService(
        ConnectionFactory masterConnectionFactory,
        List<ConnectionFactory> readConnectionFactories,
        String loadBalancerStrategy) {
      this.masterConnectionFactory = masterConnectionFactory;
      this.readConnectionFactories = readConnectionFactories;
      this.loadBalancerStrategy = loadBalancerStrategy;
    }

    @Override
    public Mono<io.r2dbc.spi.Connection> create() {
      // Default to read operations
      return getReadConnectionFactory().create();
    }

    public ConnectionFactory getMasterConnectionFactory() {
      return masterConnectionFactory;
    }

    public ConnectionFactory getReadConnectionFactory() {
      if (readConnectionFactories.isEmpty()) {
        return masterConnectionFactory; // Fallback to master
      }

      return switch (loadBalancerStrategy) {
        case "round-robin" -> {
          int index = roundRobinCounter.getAndIncrement() % readConnectionFactories.size();
          yield readConnectionFactories.get(index);
        }
        case "random" -> {
          int index = (int) (Math.random() * readConnectionFactories.size());
          yield readConnectionFactories.get(index);
        }
        default -> readConnectionFactories.get(0); // First available
      };
    }

    @Override
    public io.r2dbc.spi.ConnectionFactoryMetadata getMetadata() {
      return masterConnectionFactory.getMetadata();
    }
  }
}
