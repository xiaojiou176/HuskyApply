package com.huskyapply.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for generating pre-signed URLs for S3 file uploads.
 *
 * <p>This class encapsulates the necessary information required to generate a pre-signed URL that
 * allows clients to upload files directly to S3.
 */
public class PresignedUrlRequest {

  @NotBlank(message = "File name is required")
  private String fileName;

  @NotBlank(message = "Content type is required")
  private String contentType;

  /** Default constructor for JSON deserialization. */
  public PresignedUrlRequest() {}

  /**
   * Constructor with parameters.
   *
   * @param fileName the name of the file to be uploaded
   * @param contentType the MIME content type of the file
   */
  public PresignedUrlRequest(String fileName, String contentType) {
    this.fileName = fileName;
    this.contentType = contentType;
  }

  /**
   * Gets the file name.
   *
   * @return the file name
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * Sets the file name.
   *
   * @param fileName the file name to set
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Gets the content type.
   *
   * @return the content type
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Sets the content type.
   *
   * @param contentType the content type to set
   */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }
}
