package com.huskyapply.gateway.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * JWT Service - "The Key Smithy"
 *
 * <p>Handles JWT token generation, parsing, and validation for the HuskyApply system. This service
 * encapsulates all JWT-related operations including signing tokens with a secret key and extracting
 * claims for authentication purposes.
 */
@Service
public class JwtService {

  /**
   * JWT secret key loaded from application properties. Used for signing and verifying JWT tokens.
   */
  @Value("${jwt.secret.key}")
  private String secretKey;

  /** Token expiration time in milliseconds (24 hours). */
  private static final long JWT_EXPIRATION = 86400000;

  /**
   * Generates a JWT token for the given user details.
   *
   * <p>Creates a signed JWT token containing the user's email as the subject and sets appropriate
   * expiration time.
   *
   * @param userDetails the user details to generate token for
   * @return signed JWT token string
   */
  public String generateToken(UserDetails userDetails) {
    return generateToken(new HashMap<>(), userDetails);
  }

  /**
   * Generates a JWT token with additional claims.
   *
   * @param extraClaims additional claims to include in the token
   * @param userDetails the user details to generate token for
   * @return signed JWT token string
   */
  public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
    return Jwts.builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
        .signWith(getSignInKey())
        .compact();
  }

  /**
   * Extracts the username (email) from the JWT token.
   *
   * @param token the JWT token
   * @return the username/email extracted from the token
   */
  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  /**
   * Extracts a specific claim from the JWT token.
   *
   * @param token the JWT token
   * @param claimsResolver function to resolve the desired claim
   * @param <T> the type of the claim
   * @return the extracted claim
   */
  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  /**
   * Validates if the JWT token is valid for the given user.
   *
   * <p>Checks if the token's username matches the user's username and if the token has not expired.
   *
   * @param token the JWT token to validate
   * @param userDetails the user details to validate against
   * @return true if token is valid, false otherwise
   */
  public boolean isTokenValid(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
  }

  /**
   * Checks if the JWT token has expired.
   *
   * @param token the JWT token
   * @return true if token is expired, false otherwise
   */
  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  /**
   * Extracts the expiration date from the JWT token.
   *
   * @param token the JWT token
   * @return the expiration date
   */
  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  /**
   * Extracts all claims from the JWT token.
   *
   * @param token the JWT token
   * @return all claims in the token
   */
  private Claims extractAllClaims(String token) {
    return Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token).getPayload();
  }

  /**
   * Gets the signing key for JWT operations.
   *
   * <p>Converts the secret key string to a SecretKey instance used for signing and verifying
   * tokens.
   *
   * @return the secret key for JWT operations
   */
  private SecretKey getSignInKey() {
    byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
