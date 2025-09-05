package com.huskyapply.gateway.service;

import com.huskyapply.gateway.dto.AuthResponse;
import com.huskyapply.gateway.dto.LoginRequest;
import com.huskyapply.gateway.dto.RegistrationRequest;
import com.huskyapply.gateway.model.User;
import com.huskyapply.gateway.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Authentication Service - "The Gatekeeper"
 *
 * <p>Handles user registration and login operations for the HuskyApply system. This service manages
 * user credential validation, password hashing, and JWT token generation for authenticated users.
 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      AuthenticationManager authenticationManager) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
  }

  /**
   * Registers a new user in the system.
   *
   * <p>Creates a new user account with the provided credentials, hashes the password using BCrypt,
   * and generates a JWT token for immediate authentication.
   *
   * @param request the registration request containing email and password
   * @return AuthResponse containing the JWT access token
   * @throws RuntimeException if email is already in use
   */
  public AuthResponse register(RegistrationRequest request) {
    // Check if user already exists
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
      throw new RuntimeException("Email already in use");
    }

    // Create new user with hashed password
    var user = new User();
    user.setEmail(request.getEmail());
    user.setPassword(passwordEncoder.encode(request.getPassword()));

    // Save user to database
    userRepository.save(user);

    // Generate JWT token
    var jwtToken = jwtService.generateToken(user);

    return new AuthResponse(jwtToken);
  }

  /**
   * Authenticates a user and provides a JWT token.
   *
   * <p>Validates the provided credentials using Spring Security's AuthenticationManager and
   * generates a JWT token for the authenticated user.
   *
   * @param request the login request containing email and password
   * @return AuthResponse containing the JWT access token
   * @throws RuntimeException if authentication fails
   */
  public AuthResponse login(LoginRequest request) {
    // Authenticate user credentials
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

    // Get user details from authentication
    User user = (User) authentication.getPrincipal();

    // Generate JWT token
    var jwtToken = jwtService.generateToken(user);

    return new AuthResponse(jwtToken);
  }
}
