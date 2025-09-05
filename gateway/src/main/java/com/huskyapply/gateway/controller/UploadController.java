package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.PresignedUrlRequest;
import com.huskyapply.gateway.dto.PresignedUrlResponse;
import com.huskyapply.gateway.service.FileStorageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling file upload operations.
 *
 * <p>This controller provides endpoints for generating pre-signed URLs that allow clients to upload
 * files directly to S3. All endpoints are protected by JWT authentication as configured in the
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

  private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

  private final FileStorageService fileStorageService;

  /**
   * Constructor for dependency injection.
   *
   * @param fileStorageService the service for handling file storage operations
   */
  public UploadController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  /**
   * Generates a pre-signed URL for file upload to S3.
   *
   * <p>This endpoint accepts a request containing file information and returns a pre-signed URL
   * that the client can use to upload the file directly to S3. The URL expires after 5 minutes for
   * security.
   *
   * <p>This endpoint is protected by JWT authentication - users must be authenticated to generate
   * upload URLs.
   *
   * @param request the request containing file name and content type
   * @return response containing the pre-signed URL
   */
  @PostMapping("/presigned-url")
  public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
      @Valid @RequestBody PresignedUrlRequest request) {
    logger.info(
        "Received request for pre-signed URL generation for file: {}", request.getFileName());

    try {
      PresignedUrlResponse response = fileStorageService.generatePresignedUploadUrl(request);

      logger.info("Successfully generated pre-signed URL for file: {}", request.getFileName());
      return ResponseEntity.ok(response);

    } catch (Exception e) {
      logger.error("Failed to generate pre-signed URL for file: {}", request.getFileName(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
