package com.huskyapply.gateway.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Audit Logger for HuskyApply Gateway Provides structured logging for security, compliance, and
 * operational auditing
 */
@Component
public class AuditLogger {

  private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
  private static final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
  private static final Logger complianceLogger = LoggerFactory.getLogger("COMPLIANCE");

  private final ObjectMapper objectMapper;

  @Value("${audit.logging.enabled:true}")
  private boolean auditLoggingEnabled;

  @Value("${audit.logging.include-headers:false}")
  private boolean includeHeaders;

  @Value("${audit.logging.include-body:false}")
  private boolean includeRequestBody;

  @Value("${spring.application.name:huskyapply-gateway}")
  private String serviceName;

  @Value("${audit.logging.sensitive-fields:password,token,secret,key}")
  private String[] sensitiveFields;

  public AuditLogger(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Log authentication events */
  public void logAuthenticationEvent(
      String userId,
      String email,
      String action,
      boolean success,
      String reason,
      HttpServletRequest request) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("AUTHENTICATION", action);
    event.put("userId", userId != null ? userId : "unknown");
    event.put("email", maskEmail(email));
    event.put("success", success);
    event.put("reason", reason != null ? reason : "");
    event.put("sourceIp", getClientIpAddress(request));
    event.put("userAgent", request.getHeader("User-Agent"));

    if (success) {
      auditLogger.info("Authentication successful: {}", event);
    } else {
      securityLogger.warn("Authentication failed: {}", event);
    }
  }

  /** Log authorization events */
  public void logAuthorizationEvent(
      String userId,
      String resource,
      String action,
      boolean granted,
      String reason,
      HttpServletRequest request) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("AUTHORIZATION", action);
    event.put("userId", userId != null ? userId : "unknown");
    event.put("resource", resource);
    event.put("granted", granted);
    event.put("reason", reason != null ? reason : "");
    event.put("sourceIp", getClientIpAddress(request));

    if (granted) {
      auditLogger.info("Authorization granted: {}", event);
    } else {
      securityLogger.warn("Authorization denied: {}", event);
    }
  }

  /** Log API access events */
  public void logApiAccess(
      HttpServletRequest request, int responseStatus, long processingTimeMs, String userId) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("API_ACCESS", request.getMethod());
    event.put("endpoint", request.getRequestURI());
    event.put("httpMethod", request.getMethod());
    event.put("responseStatus", responseStatus);
    event.put("processingTimeMs", processingTimeMs);
    event.put("userId", userId != null ? userId : "anonymous");
    event.put("sourceIp", getClientIpAddress(request));
    event.put("userAgent", request.getHeader("User-Agent"));

    // Add query parameters (sanitized)
    if (request.getQueryString() != null) {
      event.put("queryString", sanitizeQueryString(request.getQueryString()));
    }

    // Add request headers if enabled
    if (includeHeaders) {
      event.set("headers", getRequestHeaders(request));
    }

    // Log based on response status
    if (responseStatus >= 400) {
      securityLogger.warn("API access with error: {}", event);
    } else {
      auditLogger.info("API access: {}", event);
    }
  }

  /** Log data access events */
  public void logDataAccess(
      String userId,
      String entityType,
      String entityId,
      String operation,
      boolean success,
      String reason) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("DATA_ACCESS", operation);
    event.put("userId", userId != null ? userId : "system");
    event.put("entityType", entityType);
    event.put("entityId", entityId != null ? entityId : "");
    event.put("success", success);
    event.put("reason", reason != null ? reason : "");

    complianceLogger.info("Data access: {}", event);
  }

  /** Log data modification events */
  public void logDataModification(
      String userId,
      String entityType,
      String entityId,
      String operation,
      Map<String, Object> changes) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("DATA_MODIFICATION", operation);
    event.put("userId", userId != null ? userId : "system");
    event.put("entityType", entityType);
    event.put("entityId", entityId != null ? entityId : "");

    // Add sanitized changes
    if (changes != null && !changes.isEmpty()) {
      ObjectNode changesNode = objectMapper.createObjectNode();
      changes.forEach(
          (key, value) -> {
            if (!isSensitiveField(key)) {
              changesNode.put(key, value != null ? value.toString() : null);
            } else {
              changesNode.put(key, "[REDACTED]");
            }
          });
      event.set("changes", changesNode);
    }

    complianceLogger.info("Data modification: {}", event);
  }

  /** Log security events */
  public void logSecurityEvent(
      String eventType,
      String severity,
      String description,
      Map<String, String> details,
      HttpServletRequest request) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("SECURITY", eventType);
    event.put("severity", severity);
    event.put("description", description);
    event.put("sourceIp", getClientIpAddress(request));
    event.put("userAgent", request.getHeader("User-Agent"));

    // Add additional details
    if (details != null && !details.isEmpty()) {
      ObjectNode detailsNode = objectMapper.createObjectNode();
      details.forEach(detailsNode::put);
      event.set("details", detailsNode);
    }

    // Log based on severity
    switch (severity.toUpperCase()) {
      case "CRITICAL":
      case "HIGH":
        securityLogger.error("Security event: {}", event);
        break;
      case "MEDIUM":
        securityLogger.warn("Security event: {}", event);
        break;
      default:
        securityLogger.info("Security event: {}", event);
    }
  }

  /** Log job processing events */
  public void logJobEvent(
      String jobId, String userId, String action, String status, Map<String, Object> metadata) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("JOB_PROCESSING", action);
    event.put("jobId", jobId);
    event.put("userId", userId != null ? userId : "system");
    event.put("status", status);

    // Add job metadata
    if (metadata != null && !metadata.isEmpty()) {
      ObjectNode metadataNode = objectMapper.createObjectNode();
      metadata.forEach(
          (key, value) -> {
            if (value != null) {
              metadataNode.put(key, value.toString());
            }
          });
      event.set("metadata", metadataNode);
    }

    auditLogger.info("Job processing: {}", event);
  }

  /** Log payment and subscription events */
  public void logPaymentEvent(
      String userId,
      String subscriptionId,
      String action,
      String amount,
      String currency,
      boolean success,
      String reason) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("PAYMENT", action);
    event.put("userId", userId);
    event.put("subscriptionId", subscriptionId != null ? subscriptionId : "");
    event.put("amount", amount != null ? amount : "0");
    event.put("currency", currency != null ? currency : "USD");
    event.put("success", success);
    event.put("reason", reason != null ? reason : "");

    complianceLogger.info("Payment event: {}", event);
  }

  /** Log file upload events */
  public void logFileUploadEvent(
      String userId,
      String fileName,
      long fileSize,
      String contentType,
      boolean success,
      String reason) {
    if (!auditLoggingEnabled) return;

    ObjectNode event = createBaseAuditEvent("FILE_UPLOAD", "UPLOAD");
    event.put("userId", userId);
    event.put("fileName", fileName);
    event.put("fileSize", fileSize);
    event.put("contentType", contentType);
    event.put("success", success);
    event.put("reason", reason != null ? reason : "");

    auditLogger.info("File upload: {}", event);
  }

  /** Create base audit event structure */
  private ObjectNode createBaseAuditEvent(String category, String action) {
    ObjectNode event = objectMapper.createObjectNode();

    event.put("timestamp", Instant.now().toString());
    event.put("eventId", UUID.randomUUID().toString());
    event.put("service", serviceName);
    event.put("category", category);
    event.put("action", action);
    event.put("version", "1.0");

    // Add correlation IDs from MDC
    String traceId = MDC.get("traceId");
    String spanId = MDC.get("spanId");
    String userId = MDC.get("userId");

    if (traceId != null) {
      event.put("traceId", traceId);
    }
    if (spanId != null) {
      event.put("spanId", spanId);
    }
    if (userId != null) {
      event.put("correlationUserId", userId);
    }

    return event;
  }

  /** Get client IP address from request */
  private String getClientIpAddress(HttpServletRequest request) {
    String[] headerNames = {
      "X-Forwarded-For",
      "X-Real-IP",
      "X-Original-Forwarded-For",
      "Proxy-Client-IP",
      "WL-Proxy-Client-IP",
      "HTTP_CLIENT_IP",
      "HTTP_X_FORWARDED_FOR"
    };

    for (String header : headerNames) {
      String ip = request.getHeader(header);
      if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        // Get first IP if comma-separated
        return ip.split(",")[0].trim();
      }
    }

    return request.getRemoteAddr();
  }

  /** Get request headers (sanitized) */
  private ObjectNode getRequestHeaders(HttpServletRequest request) {
    ObjectNode headers = objectMapper.createObjectNode();

    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      String headerValue = request.getHeader(headerName);

      // Sanitize sensitive headers
      if (isSensitiveHeader(headerName)) {
        headers.put(headerName, "[REDACTED]");
      } else {
        headers.put(headerName, headerValue);
      }
    }

    return headers;
  }

  /** Check if header contains sensitive information */
  private boolean isSensitiveHeader(String headerName) {
    String lowerName = headerName.toLowerCase();
    return lowerName.contains("authorization")
        || lowerName.contains("cookie")
        || lowerName.contains("token")
        || lowerName.contains("secret")
        || lowerName.contains("key");
  }

  /** Check if field is sensitive */
  private boolean isSensitiveField(String fieldName) {
    String lowerField = fieldName.toLowerCase();
    for (String sensitiveField : sensitiveFields) {
      if (lowerField.contains(sensitiveField.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  /** Mask email address for privacy */
  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return email;
    }

    String[] parts = email.split("@");
    String localPart = parts[0];
    String domain = parts[1];

    if (localPart.length() <= 2) {
      return "*".repeat(localPart.length()) + "@" + domain;
    } else {
      return localPart.charAt(0)
          + "*".repeat(localPart.length() - 2)
          + localPart.charAt(localPart.length() - 1)
          + "@"
          + domain;
    }
  }

  /** Sanitize query string by removing sensitive parameters */
  private String sanitizeQueryString(String queryString) {
    if (queryString == null) return null;

    String[] params = queryString.split("&");
    StringBuilder sanitized = new StringBuilder();

    for (String param : params) {
      String[] keyValue = param.split("=", 2);
      String key = keyValue[0];

      if (sanitized.length() > 0) {
        sanitized.append("&");
      }

      if (isSensitiveField(key)) {
        sanitized.append(key).append("=[REDACTED]");
      } else {
        sanitized.append(param);
      }
    }

    return sanitized.toString();
  }

  /** Get current authenticated user ID */
  public String getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      return authentication.getName();
    }
    return null;
  }

  /** Audit event builder for fluent API */
  public static class AuditEventBuilder {
    private final ObjectNode event;

    public AuditEventBuilder(AuditLogger logger, String category, String action) {
      this.event = logger.createBaseAuditEvent(category, action);
    }

    public AuditEventBuilder userId(String userId) {
      event.put("userId", userId);
      return this;
    }

    public AuditEventBuilder resource(String resource) {
      event.put("resource", resource);
      return this;
    }

    public AuditEventBuilder success(boolean success) {
      event.put("success", success);
      return this;
    }

    public AuditEventBuilder reason(String reason) {
      event.put("reason", reason);
      return this;
    }

    public AuditEventBuilder detail(String key, Object value) {
      if (value != null) {
        event.put(key, value.toString());
      }
      return this;
    }

    public void log() {
      AuditLogger.auditLogger.info("Audit event: {}", event);
    }
  }

  /** Create audit event builder */
  public AuditEventBuilder event(String category, String action) {
    return new AuditEventBuilder(this, category, action);
  }
}
