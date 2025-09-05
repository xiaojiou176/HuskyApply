package com.huskyapply.gateway.dto;

/**
 * Data Transfer Object for user login requests.
 *
 * <p>Contains the credentials required for user authentication in the HuskyApply system.
 */
public class LoginRequest {

  /** User's email address (username). */
  private String email;

  /** User's plain text password for authentication. */
  private String password;

  // Default constructor
  public LoginRequest() {}

  // Constructor with parameters
  public LoginRequest(String email, String password) {
    this.email = email;
    this.password = password;
  }

  // Getters and setters
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
