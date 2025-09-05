package com.huskyapply.gateway.controller;

import com.huskyapply.gateway.dto.AuthResponse;
import com.huskyapply.gateway.dto.LoginRequest;
import com.huskyapply.gateway.dto.RegistrationRequest;
import com.huskyapply.gateway.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - "The Gatehouse"
 *
 * <p>Handles user authentication endpoints for the HuskyApply system. This controller manages user
 * registration and login operations, providing JWT tokens for successful authentication.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Registers a new user in the system.
   *
   * <p>Creates a new user account with the provided credentials and returns a JWT token for
   * immediate authentication.
   *
   * @param request the registration request containing email and password
   * @return ResponseEntity containing AuthResponse with JWT token
   */
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody RegistrationRequest request) {
    try {
      AuthResponse response = authService.register(request);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (RuntimeException e) {
      // Return 409 Conflict if email is already in use
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  /**
   * Authenticates a user and provides a JWT token.
   *
   * <p>Validates the provided credentials and returns a JWT token for successful authentication.
   *
   * @param request the login request containing email and password
   * @return ResponseEntity containing AuthResponse with JWT token
   */
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    try {
      AuthResponse response = authService.login(request);
      return ResponseEntity.ok(response);
    } catch (RuntimeException e) {
      // Return 401 Unauthorized for invalid credentials
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }
}
