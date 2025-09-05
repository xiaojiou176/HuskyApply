package com.huskyapply.gateway.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.huskyapply.gateway.dto.LoginRequest;
import com.huskyapply.gateway.dto.RegistrationRequest;
import org.junit.jupiter.api.Test;

/**
 * Basic unit tests for AuthController DTOs and request validation.
 *
 * <p>These tests verify that the DTOs are properly constructed and can handle the basic
 * authentication data transfer operations.
 */
public class AuthControllerTest {

  @Test
  public void testRegistrationRequestCreation() {
    // Test default constructor
    RegistrationRequest request1 = new RegistrationRequest();
    assertNotNull(request1);

    // Test parameterized constructor
    RegistrationRequest request2 = new RegistrationRequest("test@example.com", "password123");
    assertEquals("test@example.com", request2.getEmail());
    assertEquals("password123", request2.getPassword());

    // Test setters
    request1.setEmail("user@test.com");
    request1.setPassword("secret");
    assertEquals("user@test.com", request1.getEmail());
    assertEquals("secret", request1.getPassword());
  }

  @Test
  public void testLoginRequestCreation() {
    // Test default constructor
    LoginRequest request1 = new LoginRequest();
    assertNotNull(request1);

    // Test parameterized constructor
    LoginRequest request2 = new LoginRequest("test@example.com", "password123");
    assertEquals("test@example.com", request2.getEmail());
    assertEquals("password123", request2.getPassword());

    // Test setters
    request1.setEmail("user@test.com");
    request1.setPassword("secret");
    assertEquals("user@test.com", request1.getEmail());
    assertEquals("secret", request1.getPassword());
  }
}
