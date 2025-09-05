package com.huskyapply.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API Request Signature Filter for enhanced security Validates request signatures for critical API
 * endpoints to prevent replay attacks and ensure request integrity.
 */
@Component
public class ApiRequestSignatureFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(ApiRequestSignatureFilter.class);

  private static final String SIGNATURE_HEADER = "X-Signature";
  private static final String TIMESTAMP_HEADER = "X-Timestamp";
  private static final String NONCE_HEADER = "X-Nonce";
  private static final String SIGNATURE_VERSION_HEADER = "X-Signature-Version";

  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String CURRENT_SIGNATURE_VERSION = "v1";
  private static final long REQUEST_TIMEOUT_MINUTES = 5; // 5 minutes window

  @Value("${security.api.signature.secret:}")
  private String signatureSecret;

  @Value("${security.api.signature.enabled:false}")
  private boolean signatureEnabled;

  @Value("${security.api.signature.required-endpoints:/api/v1/applications,/api/v1/internal/**}")
  private String[] requiredEndpoints;

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Simple in-memory nonce store (in production, use Redis or database)
  private final Map<String, Long> nonceStore = new HashMap<>();
  private final Object nonceLock = new Object();

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    // Skip if signature validation is disabled
    if (!signatureEnabled) {
      filterChain.doFilter(request, response);
      return;
    }

    // Check if this endpoint requires signature validation
    if (!requiresSignatureValidation(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    // Skip OPTIONS requests (CORS preflight)
    if ("OPTIONS".equals(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      // Validate signature
      if (!validateSignature(request)) {
        sendErrorResponse(response, "Invalid or missing API signature", HttpStatus.UNAUTHORIZED);
        return;
      }

      filterChain.doFilter(request, response);

    } catch (Exception e) {
      logger.error("Error during signature validation", e);
      sendErrorResponse(response, "Signature validation failed", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /** Check if the endpoint requires signature validation */
  private boolean requiresSignatureValidation(HttpServletRequest request) {
    String requestPath = request.getRequestURI();

    for (String endpoint : requiredEndpoints) {
      if (endpoint.endsWith("/**")) {
        // Pattern matching for /** endpoints
        String basePath = endpoint.substring(0, endpoint.length() - 3);
        if (requestPath.startsWith(basePath)) {
          return true;
        }
      } else if (requestPath.equals(endpoint)) {
        return true;
      }
    }

    return false;
  }

  /** Validate the request signature */
  private boolean validateSignature(HttpServletRequest request)
      throws IOException, NoSuchAlgorithmException, InvalidKeyException {
    // Get required headers
    String signature = request.getHeader(SIGNATURE_HEADER);
    String timestamp = request.getHeader(TIMESTAMP_HEADER);
    String nonce = request.getHeader(NONCE_HEADER);
    String signatureVersion = request.getHeader(SIGNATURE_VERSION_HEADER);

    // Check required headers
    if (signature == null || timestamp == null || nonce == null) {
      logger.warn("Missing required signature headers in request to {}", request.getRequestURI());
      return false;
    }

    // Check signature version
    if (!CURRENT_SIGNATURE_VERSION.equals(signatureVersion)) {
      logger.warn(
          "Invalid signature version: {} (expected: {})",
          signatureVersion,
          CURRENT_SIGNATURE_VERSION);
      return false;
    }

    // Validate timestamp (prevent replay attacks)
    if (!validateTimestamp(timestamp)) {
      logger.warn("Invalid or expired timestamp in request: {}", timestamp);
      return false;
    }

    // Validate nonce (prevent duplicate requests)
    if (!validateNonce(nonce, timestamp)) {
      logger.warn("Invalid or duplicate nonce in request: {}", nonce);
      return false;
    }

    // Calculate expected signature
    String expectedSignature = calculateSignature(request, timestamp, nonce);

    // Compare signatures
    boolean valid = expectedSignature.equals(signature);

    if (!valid) {
      logger.warn(
          "Signature mismatch for request to {}. Expected: {}, Got: {}",
          request.getRequestURI(),
          expectedSignature,
          signature);
    }

    return valid;
  }

  /** Validate request timestamp */
  private boolean validateTimestamp(String timestampStr) {
    try {
      long timestamp = Long.parseLong(timestampStr);
      long currentTime = Instant.now().getEpochSecond();
      long timeDiff = Math.abs(currentTime - timestamp);

      // Allow requests within the time window
      return timeDiff <= (REQUEST_TIMEOUT_MINUTES * 60);

    } catch (NumberFormatException e) {
      logger.warn("Invalid timestamp format: {}", timestampStr);
      return false;
    }
  }

  /** Validate and store nonce to prevent replay attacks */
  private boolean validateNonce(String nonce, String timestamp) {
    if (nonce == null || nonce.trim().isEmpty()) {
      return false;
    }

    synchronized (nonceLock) {
      // Check if nonce already exists
      if (nonceStore.containsKey(nonce)) {
        return false; // Duplicate nonce
      }

      // Store nonce with timestamp
      try {
        long timestampValue = Long.parseLong(timestamp);
        nonceStore.put(nonce, timestampValue);

        // Clean up old nonces (older than request timeout)
        cleanupOldNonces();

        return true;

      } catch (NumberFormatException e) {
        logger.warn("Invalid timestamp for nonce validation: {}", timestamp);
        return false;
      }
    }
  }

  /** Clean up old nonces to prevent memory leaks */
  private void cleanupOldNonces() {
    long cutoffTime =
        Instant.now().minus(REQUEST_TIMEOUT_MINUTES * 2, ChronoUnit.MINUTES).getEpochSecond();
    nonceStore.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
  }

  /** Calculate the expected signature for the request */
  private String calculateSignature(HttpServletRequest request, String timestamp, String nonce)
      throws IOException, NoSuchAlgorithmException, InvalidKeyException {

    // Create signature payload
    StringBuilder payload = new StringBuilder();
    payload.append(request.getMethod()).append("\n");
    payload.append(request.getRequestURI()).append("\n");

    // Add query parameters in sorted order
    String queryString = request.getQueryString();
    if (queryString != null) {
      payload.append(queryString);
    }
    payload.append("\n");

    // Add request body for POST/PUT/PATCH requests
    String requestBody = "";
    if ("POST".equals(request.getMethod())
        || "PUT".equals(request.getMethod())
        || "PATCH".equals(request.getMethod())) {

      // Read request body
      CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
      byte[] bodyBytes = StreamUtils.copyToByteArray(cachedRequest.getInputStream());
      requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
    }
    payload.append(requestBody).append("\n");

    // Add timestamp and nonce
    payload.append(timestamp).append("\n");
    payload.append(nonce);

    // Calculate HMAC-SHA256 signature
    Mac mac = Mac.getInstance(HMAC_SHA256);
    SecretKeySpec secretKeySpec =
        new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    mac.init(secretKeySpec);

    byte[] signatureBytes = mac.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signatureBytes);
  }

  /** Send error response */
  private void sendErrorResponse(HttpServletResponse response, String message, HttpStatus status)
      throws IOException {

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", "signature_validation_failed");
    errorResponse.put("message", message);
    errorResponse.put("timestamp", Instant.now().toString());
    errorResponse.put("status", status.value());

    String jsonResponse = objectMapper.writeValueAsString(errorResponse);
    response.getWriter().write(jsonResponse);
    response.getWriter().flush();
  }

  /** Cached body HTTP servlet request wrapper to allow multiple reads of request body */
  private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
      super(request);
      this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
      return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(new InputStreamReader(getInputStream()));
    }
  }

  /** Cached body servlet input stream */
  private static class CachedBodyServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream inputStream;

    public CachedBodyServletInputStream(byte[] body) {
      this.inputStream = new ByteArrayInputStream(body);
    }

    @Override
    public boolean isFinished() {
      return inputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      // Not implemented for synchronous reading
    }

    @Override
    public int read() {
      return inputStream.read();
    }
  }
}
