package com.huskyapply.gateway.config;

import com.huskyapply.brain.proto.JobProcessingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuration for gRPC client to communicate with Brain service. */
@Configuration
@ConditionalOnProperty(name = "huskyapply.migration.grpc-enabled", havingValue = "true")
public class BrainGrpcClientConfig {

  private static final Logger logger = LoggerFactory.getLogger(BrainGrpcClientConfig.class);

  @Value("${grpc.client.brain.host:brain}")
  private String brainHost;

  @Value("${grpc.client.brain.port:9090}")
  private int brainPort;

  @Value("${grpc.client.brain.max-inbound-message-size:4194304}")
  private int maxInboundMessageSize;

  @Value("${grpc.client.brain.keepalive-time:30s}")
  private String keepaliveTime;

  @Value("${grpc.client.brain.keepalive-timeout:5s}")
  private String keepaliveTimeout;

  @Value("${grpc.client.brain.idle-timeout:60s}")
  private String idleTimeout;

  @Value("${grpc.pool.max-connections-per-endpoint:10}")
  private int maxConnectionsPerEndpoint;

  @Value("${grpc.security.mtls-enabled:false}")
  private boolean mtlsEnabled;

  /** Creates a managed channel to Brain service. */
  @Bean
  public ManagedChannel brainGrpcChannel() {
    logger.info("Creating gRPC channel to Brain service at {}:{}", brainHost, brainPort);

    NettyChannelBuilder channelBuilder =
        NettyChannelBuilder.forAddress(brainHost, brainPort)
            .maxInboundMessageSize(maxInboundMessageSize)
            .keepAliveTime(parseDuration(keepaliveTime), TimeUnit.SECONDS)
            .keepAliveTimeout(parseDuration(keepaliveTimeout), TimeUnit.SECONDS)
            .idleTimeout(parseDuration(idleTimeout), TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true);

    // Configure TLS/mTLS if enabled
    if (mtlsEnabled) {
      logger.info("mTLS enabled for gRPC channel");
      // TLS configuration will be added here
      // channelBuilder.sslContext(sslContext);
    } else {
      logger.warn("Using plaintext gRPC connection (not recommended for production)");
      channelBuilder.usePlaintext();
    }

    ManagedChannel channel = channelBuilder.build();

    logger.info("gRPC channel to Brain service created successfully");
    return channel;
  }

  /** Creates a stub for JobProcessingService. */
  @Bean
  public JobProcessingServiceGrpc.JobProcessingServiceStub jobProcessingStub(
      ManagedChannel brainGrpcChannel) {
    logger.info("Creating JobProcessingService stub");
    return JobProcessingServiceGrpc.newStub(brainGrpcChannel);
  }

  /** Creates a blocking stub for synchronous calls. */
  @Bean
  public JobProcessingServiceGrpc.JobProcessingServiceBlockingStub jobProcessingBlockingStub(
      ManagedChannel brainGrpcChannel) {
    logger.info("Creating JobProcessingService blocking stub");
    return JobProcessingServiceGrpc.newBlockingStub(brainGrpcChannel);
  }

  /** Parses duration string (e.g., "30s", "5m") to seconds. */
  private long parseDuration(String duration) {
    if (duration.endsWith("s")) {
      return Long.parseLong(duration.substring(0, duration.length() - 1));
    } else if (duration.endsWith("m")) {
      return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60;
    }
    return Long.parseLong(duration);
  }

  /** Register shutdown hook for graceful channel shutdown. */
  @Bean
  public GrpcChannelShutdownHook grpcChannelShutdownHook(ManagedChannel brainGrpcChannel) {
    return new GrpcChannelShutdownHook(brainGrpcChannel);
  }

  /** Shutdown hook to gracefully close gRPC channels. */
  public static class GrpcChannelShutdownHook {
    private final ManagedChannel channel;

    public GrpcChannelShutdownHook(ManagedChannel channel) {
      this.channel = channel;
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    logger.info("Shutting down gRPC channel");
                    try {
                      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                      logger.info("gRPC channel shut down successfully");
                    } catch (InterruptedException e) {
                      logger.error("Error shutting down gRPC channel", e);
                      Thread.currentThread().interrupt();
                    }
                  }));
    }
  }
}
