package com.huskyapply.gateway.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * User entity representing a registered user in the HuskyApply system.
 *
 * <p>This entity implements Spring Security's UserDetails interface to integrate seamlessly with
 * the authentication framework. It stores essential user information including credentials and
 * provides the necessary methods for Spring Security authentication.
 */
@Entity
@jakarta.persistence.Table(name = "users")
public class User implements UserDetails {

  /** Unique identifier for the user. */
  @Id
  @jakarta.persistence.Column(name = "id")
  private UUID id;

  /** User's email address, which serves as the username. Must be unique across all users. */
  @jakarta.persistence.Column(name = "email")
  private String email;

  /** User's hashed password. Stored using BCrypt hashing for security. */
  @jakarta.persistence.Column(name = "password")
  private String password;

  // Constructors
  public User() {}

  public User(String email, String password) {
    this.email = email;
    this.password = password;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  // UserDetails interface implementation

  /**
   * Returns the authorities granted to the user. Currently returns an empty list as role-based
   * authorization is not implemented.
   *
   * @return empty collection of GrantedAuthority
   */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return new ArrayList<>();
  }

  /**
   * Returns the password used to authenticate the user.
   *
   * @return the user's hashed password
   */
  @Override
  public String getPassword() {
    return password;
  }

  /**
   * Returns the username used to authenticate the user. In this system, the email serves as the
   * username.
   *
   * @return the user's email address
   */
  @Override
  public String getUsername() {
    return email;
  }

  /**
   * Indicates whether the user's account has expired.
   *
   * @return true as accounts do not expire in this implementation
   */
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  /**
   * Indicates whether the user is locked or unlocked.
   *
   * @return true as account locking is not implemented
   */
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  /**
   * Indicates whether the user's credentials (password) has expired.
   *
   * @return true as credential expiration is not implemented
   */
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /**
   * Indicates whether the user is enabled or disabled.
   *
   * @return true as user disabling is not implemented
   */
  @Override
  public boolean isEnabled() {
    return true;
  }
}
