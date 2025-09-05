package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.PresignedUrlRequest;
import com.huskyapply.gateway.dto.PresignedUrlResponse;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Service responsible for file storage operations using AWS S3.
 *
 * <p>This service provides functionality to generate pre-signed URLs that allow clients to upload
 * files directly to S3 without routing through the Gateway service. This approach reduces server
 * load and provides better performance for file uploads.
 */
@Service
public class FileStorageService {

  private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

  private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(5);
  private static final String UPLOADS_PREFIX = "uploads/";

  private final S3Client s3Client;
  private final String bucketName;

  /**
   * Constructor that injects dependencies.
   *
   * @param s3Client the S3 client for AWS operations
   * @param bucketName the S3 bucket name for file storage
   */
  public FileStorageService(S3Client s3Client, @Value("${aws.s3.bucket-name}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
    logger.info("FileStorageService initialized with bucket: {}", bucketName);
  }

  /**
   * Generates a pre-signed URL for uploading a file to S3.
   *
   * <p>The pre-signed URL allows clients to upload files directly to S3 without authenticating
   * through AWS credentials. The URL expires after 5 minutes for security purposes.
   *
   * @param request the request containing file name and content type
   * @return response containing the pre-signed URL
   * @throws RuntimeException if URL generation fails
   */
  public PresignedUrlResponse generatePresignedUploadUrl(PresignedUrlRequest request) {
    logger.info(
        "Generating pre-signed URL for file: {} with content type: {}",
        request.getFileName(),
        request.getContentType());

    try {
      // Generate a unique key for the file to prevent conflicts
      String uniqueFileName = generateUniqueFileName(request.getFileName());
      String objectKey = UPLOADS_PREFIX + uniqueFileName;

      // Create the presigner
      try (S3Presigner presigner = S3Presigner.builder().s3Client(s3Client).build()) {

        // Build the PutObjectRequest
        PutObjectRequest putObjectRequest =
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(request.getContentType())
                .build();

        // Create presign request
        PutObjectPresignRequest presignRequest =
            PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        // Generate the presigned URL
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        String presignedUrl = presignedRequest.url().toString();

        logger.info(
            "Successfully generated pre-signed URL for object key: {}, expires in {} minutes",
            objectKey,
            PRESIGNED_URL_EXPIRATION.toMinutes());

        return new PresignedUrlResponse(presignedUrl);
      }

    } catch (Exception e) {
      logger.error("Failed to generate pre-signed URL for file: {}", request.getFileName(), e);
      throw new RuntimeException("Failed to generate pre-signed URL: " + e.getMessage(), e);
    }
  }

  /**
   * Generates a unique file name to prevent conflicts in S3.
   *
   * @param originalFileName the original file name
   * @return unique file name with UUID prefix
   */
  private String generateUniqueFileName(String originalFileName) {
    String uniqueId = UUID.randomUUID().toString();
    return uniqueId + "_" + originalFileName;
  }
}
