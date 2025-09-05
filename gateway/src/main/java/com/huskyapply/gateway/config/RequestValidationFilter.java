package com.huskyapply.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Request validation filter that sanitizes and validates incoming requests.
 *
 * <p>This filter provides protection against: - SQL injection attempts - XSS attacks through
 * request parameters - Path traversal attacks - Malformed or oversized requests - Suspicious user
 * agents
 */
@Component
@Order(2)
public class RequestValidationFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(RequestValidationFilter.class);

  // Maximum request size (10MB for file uploads)
  private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024;

  // Maximum URL length
  private static final int MAX_URL_LENGTH = 2048;

  // Maximum header value length
  private static final int MAX_HEADER_LENGTH = 4096;

  // Patterns for detecting malicious content
  private static final Pattern SQL_INJECTION_PATTERN =
      Pattern.compile(
          "(?i).*(union|select|insert|delete|update|drop|create|alter|exec|script|javascript|vbscript|onload|onerror).*");

  private static final Pattern XSS_PATTERN =
      Pattern.compile(
          "(?i).*(<script|<iframe|<object|<embed|javascript:|data:|vbscript:|onload=|onerror=|onclick=).*");

  private static final Pattern PATH_TRAVERSAL_PATTERN =
      Pattern.compile(".*(\\.\\.[\\\\/]|[\\\\/]\\.\\.[\\\\/]|[\\\\/]\\.\\.$).*");

  private static final Pattern SUSPICIOUS_USER_AGENT_PATTERN =
      Pattern.compile(
          "(?i).*(sqlmap|nikto|nmap|masscan|nessus|openvas|w3af|skipfish|crawl|bot|scanner).*");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    try {
      // Skip validation for health check endpoints
      if (isHealthCheckEndpoint(request)) {
        filterChain.doFilter(request, response);
        return;
      }

      // Validate request size
      if (!validateRequestSize(request)) {
        logger.warn("Request too large from IP: {}", getClientIP(request));
        sendErrorResponse(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request entity too large");
        return;
      }

      // Validate URL length and content
      if (!validateUrl(request)) {
        logger.warn("Invalid URL from IP: {}", getClientIP(request));
        sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid request URL");
        return;
      }

      // Validate headers
      if (!validateHeaders(request)) {
        logger.warn("Invalid headers from IP: {}", getClientIP(request));
        sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid request headers");
        return;
      }

      // Validate request parameters
      if (!validateParameters(request)) {
        logger.warn("Suspicious request parameters from IP: {}", getClientIP(request));
        sendErrorResponse(response, HttpStatus.BAD_REQUEST, "Invalid request parameters");
        return;
      }

      // Check for suspicious user agents
      if (!validateUserAgent(request)) {
        logger.warn("Suspicious user agent from IP: {}", getClientIP(request));
        sendErrorResponse(response, HttpStatus.FORBIDDEN, "Access forbidden");
        return;
      }

      filterChain.doFilter(request, response);

    } catch (Exception e) {
      logger.error("Error in request validation filter", e);
      sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  /** Validate request size to prevent DoS attacks. */
  private boolean validateRequestSize(HttpServletRequest request) {
    long contentLength = request.getContentLengthLong();
    return contentLength <= MAX_REQUEST_SIZE;
  }

  /** Validate URL for length and malicious patterns. */
  private boolean validateUrl(HttpServletRequest request) {
    String url = request.getRequestURL().toString();
    String queryString = request.getQueryString();

    // Check URL length
    if (url.length() > MAX_URL_LENGTH) {
      return false;
    }

    // Check for path traversal
    if (PATH_TRAVERSAL_PATTERN.matcher(url).matches()) {
      return false;
    }

    // Check query string if present
    if (queryString != null) {
      if (queryString.length() > MAX_URL_LENGTH) {
        return false;
      }

      if (containsMaliciousContent(queryString)) {
        return false;
      }
    }

    return true;
  }

  /** Validate request headers for suspicious content. */
  private boolean validateHeaders(HttpServletRequest request) {
    var headerNames = request.getHeaderNames();

    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      String headerValue = request.getHeader(headerName);

      if (headerValue == null) continue;

      // Check header length
      if (headerValue.length() > MAX_HEADER_LENGTH) {
        return false;
      }

      // Check for malicious content in headers
      if (containsMaliciousContent(headerValue)) {
        return false;
      }
    }

    return true;
  }

  /** Validate request parameters for malicious content. */
  private boolean validateParameters(HttpServletRequest request) {
    var parameterNames = request.getParameterNames();

    while (parameterNames.hasMoreElements()) {
      String paramName = parameterNames.nextElement();
      String[] paramValues = request.getParameterValues(paramName);

      if (paramValues == null) continue;

      for (String paramValue : paramValues) {
        if (paramValue != null && containsMaliciousContent(paramValue)) {
          return false;
        }
      }
    }

    return true;
  }

  /** Validate user agent for suspicious patterns. */
  private boolean validateUserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");

    if (userAgent == null || userAgent.trim().isEmpty()) {
      // Allow requests without user agent (might be legitimate API clients)
      return true;
    }

    return !SUSPICIOUS_USER_AGENT_PATTERN.matcher(userAgent).matches();
  }

  /** Check if content contains malicious patterns. */
  private boolean containsMaliciousContent(String content) {
    if (content == null) return false;

    String decodedContent = decodeUrl(content);

    return SQL_INJECTION_PATTERN.matcher(decodedContent).matches()
        || XSS_PATTERN.matcher(decodedContent).matches();
  }

  /** Simple URL decoding to catch encoded attacks. */
  private String decodeUrl(String content) {
    try {
      // Simple character replacements for common encodings
      return content
          .replace("%27", "'")
          .replace("%22", "\"")
          .replace("%3C", "<")
          .replace("%3E", ">")
          .replace("%28", "(")
          .replace("%29", ")")
          .replace("%3D", "=")
          .replace("+", " ");
    } catch (Exception e) {
      return content;
    }
  }

  /** Check if this is a health check endpoint that should skip validation. */
  private boolean isHealthCheckEndpoint(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.equals("/actuator/health") || path.equals("/healthz") || path.equals("/health");
  }

  /** Get client IP address, considering proxy headers. */
  private String getClientIP(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    String xRealIP = request.getHeader("X-Real-IP");
    if (xRealIP != null && !xRealIP.isEmpty()) {
      return xRealIP;
    }

    return request.getRemoteAddr();
  }

  /** Send error response with proper headers. */
  private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    String jsonResponse =
        String.format(
            "{\"error\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
            message, status.value(), java.time.Instant.now().toString());

    response.getWriter().write(jsonResponse);
    response.getWriter().flush();
  }
}
