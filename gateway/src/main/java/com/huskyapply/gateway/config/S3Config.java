package com.huskyapply.gateway.config;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Configuration class for AWS S3 client setup.
 *
 * <p>This configuration creates an S3Client bean that can be used throughout the application for S3
 * operations like generating pre-signed URLs. The configuration supports both AWS S3 and
 * S3-compatible services (like MinIO) through endpoint override.
 */
@Configuration
public class S3Config {

  private static final Logger log = LoggerFactory.getLogger(S3Config.class);

  @Value("${aws.s3.region}")
  private String region;

  @Value("${aws.s3.endpoint-override:}")
  private String endpointOverride;

  /**
   * Creates an S3Client bean configured with the appropriate region and endpoint.
   *
   * <p>The client uses environment variable credentials provider to read AWS credentials from
   * environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY).
   *
   * <p>If endpointOverride is provided, it configures the client for S3-compatible services like
   * MinIO for local development.
   *
   * @return configured S3Client instance
   */
  @Bean
  public S3Client s3Client() {
    log.info("Initializing S3Client for region: {}", region);

    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(EnvironmentVariableCredentialsProvider.create());

    // Configure endpoint override for S3-compatible services (e.g., MinIO)
    if (endpointOverride != null && !endpointOverride.trim().isEmpty()) {
      log.info("Using custom S3 endpoint: {}", endpointOverride);
      builder
          .endpointOverride(URI.create(endpointOverride))
          .forcePathStyle(true); // Required for MinIO and other S3-compatible services
    } else {
      log.info("Using default AWS S3 endpoint");
    }

    S3Client s3Client = builder.build();
    log.info("S3Client initialized successfully");
    return s3Client;
  }
}
