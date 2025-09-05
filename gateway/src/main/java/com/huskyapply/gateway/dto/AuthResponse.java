package com.huskyapply.gateway.dto;

/**
 * Data Transfer Object for authentication responses.
 *
 * <p>Contains the JWT access token returned after successful user registration or login.
 */
public class AuthResponse {

  /**
   * JWT access token for authenticated requests. This token should be included in the Authorization
   * header of subsequent API requests.
   */
  private String accessToken;

  // Default constructor
  public AuthResponse() {}

  // Constructor with parameter
  public AuthResponse(String accessToken) {
    this.accessToken = accessToken;
  }

  // Getter and setter
  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
