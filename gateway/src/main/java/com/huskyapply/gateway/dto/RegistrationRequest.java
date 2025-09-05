package com.huskyapply.gateway.dto;

/**
 * Data Transfer Object for user registration requests.
 *
 * <p>Contains the necessary information required to register a new user in the HuskyApply system.
 */
public class RegistrationRequest {

  /** User's email address, which will serve as their username. Must be unique and valid. */
  private String email;

  /** User's plain text password. Will be hashed using BCrypt before storage. */
  private String password;

  // Default constructor
  public RegistrationRequest() {}

  // Constructor with parameters
  public RegistrationRequest(String email, String password) {
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
