package com.huskyapply.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers filter that adds essential security headers to all HTTP responses.
 *
 * <p>This filter implements OWASP security best practices by adding headers that: - Prevent
 * clickjacking attacks (X-Frame-Options) - Protect against XSS attacks (X-XSS-Protection,
 * X-Content-Type-Options) - Enforce HTTPS (Strict-Transport-Security) - Control referrer
 * information (Referrer-Policy) - Implement Content Security Policy (CSP)
 */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    // X-Frame-Options: Prevent clickjacking by controlling iframe embedding
    response.setHeader("X-Frame-Options", "DENY");

    // X-Content-Type-Options: Prevent MIME type sniffing
    response.setHeader("X-Content-Type-Options", "nosniff");

    // X-XSS-Protection: Enable browser XSS filtering (legacy browsers)
    response.setHeader("X-XSS-Protection", "1; mode=block");

    // Strict-Transport-Security: Enforce HTTPS for 1 year
    // Only add if request is HTTPS to avoid issues in development
    if (request.isSecure()) {
      response.setHeader(
          "Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
    }

    // Referrer-Policy: Control referrer information sent
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

    // Content-Security-Policy: Comprehensive CSP for XSS protection
    String csp = buildContentSecurityPolicy(request);
    response.setHeader("Content-Security-Policy", csp);

    // Permissions-Policy: Control browser features
    response.setHeader(
        "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");

    // Cache-Control for sensitive endpoints
    if (isSensitiveEndpoint(request)) {
      response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
      response.setHeader("Pragma", "no-cache");
      response.setHeader("Expires", "0");
    }

    // Remove server information
    response.setHeader("Server", "HuskyApply");

    filterChain.doFilter(request, response);
  }

  /**
   * Build Content Security Policy based on the request context.
   *
   * @param request the HTTP request
   * @return CSP header value
   */
  private String buildContentSecurityPolicy(HttpServletRequest request) {
    StringBuilder csp = new StringBuilder();

    // Default source policy
    csp.append("default-src 'self'; ");

    // Script sources - allow self and specific trusted domains
    csp.append("script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; ");

    // Style sources - allow self and inline styles for development
    csp.append("style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; ");

    // Font sources
    csp.append("font-src 'self' https://fonts.gstatic.com data:; ");

    // Image sources - allow self, data URIs, and trusted CDNs
    csp.append("img-src 'self' data: https: blob:; ");

    // Connect sources - API endpoints and WebSocket connections
    csp.append("connect-src 'self' https://api.huskyapply.com wss://api.huskyapply.com; ");

    // Media sources
    csp.append("media-src 'self'; ");

    // Object and embed sources (disable for security)
    csp.append("object-src 'none'; ");
    csp.append("embed-src 'none'; ");

    // Base URI restriction
    csp.append("base-uri 'self'; ");

    // Form action restriction
    csp.append("form-action 'self'; ");

    // Frame ancestors (additional clickjacking protection)
    csp.append("frame-ancestors 'none'; ");

    // Upgrade insecure requests in production
    if (request.isSecure()) {
      csp.append("upgrade-insecure-requests; ");
    }

    return csp.toString().trim();
  }

  /**
   * Check if the endpoint contains sensitive information that should not be cached.
   *
   * @param request the HTTP request
   * @return true if the endpoint is sensitive
   */
  private boolean isSensitiveEndpoint(HttpServletRequest request) {
    String path = request.getRequestURI();

    // API endpoints that contain sensitive data
    return path.startsWith("/api/v1/auth/")
        || path.startsWith("/api/v1/dashboard/")
        || path.startsWith("/api/v1/applications/")
        || path.startsWith("/api/v1/templates/")
        || path.startsWith("/api/v1/subscriptions/")
        || path.contains("/internal/");
  }
}
