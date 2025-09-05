package com.huskyapply.gateway.dto;

/**
 * Response DTO containing a pre-signed URL for S3 file uploads.
 *
 * <p>This class encapsulates the generated pre-signed URL that clients can use to upload files
 * directly to S3 without going through the Gateway service.
 */
public class PresignedUrlResponse {

  private String url;

  /** Default constructor for JSON serialization. */
  public PresignedUrlResponse() {}

  /**
   * Constructor with URL parameter.
   *
   * @param url the pre-signed URL for file upload
   */
  public PresignedUrlResponse(String url) {
    this.url = url;
  }

  /**
   * Gets the pre-signed URL.
   *
   * @return the pre-signed URL
   */
  public String getUrl() {
    return url;
  }

  /**
   * Sets the pre-signed URL.
   *
   * @param url the pre-signed URL to set
   */
  public void setUrl(String url) {
    this.url = url;
  }
}
