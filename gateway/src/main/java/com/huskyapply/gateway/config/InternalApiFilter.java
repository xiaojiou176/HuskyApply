package com.huskyapply.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to authenticate internal service API calls.
 *
 * <p>This filter checks for a specific header (X-Internal-API-Key) on internal API endpoints and
 * validates it against a configured secret. If valid, it grants the request INTERNAL_SERVICE role
 * authorities.
 */
@Component
public class InternalApiFilter extends OncePerRequestFilter {

  private static final String INTERNAL_API_HEADER = "X-Internal-API-Key";

  @Value("${internal.api.key:husky-internal-secret}")
  private String internalApiKey;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String requestURI = request.getRequestURI();

    // Only apply this filter to internal API endpoints
    if (requestURI.startsWith("/api/v1/internal/")) {
      String providedApiKey = request.getHeader(INTERNAL_API_HEADER);

      // Check if the internal API key is present and valid
      if (internalApiKey.equals(providedApiKey)) {
        // Create authentication with INTERNAL_SERVICE role
        List<GrantedAuthority> authorities =
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"));

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("internal-service", null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
      } else {
        // Invalid or missing API key - let Spring Security handle the 403
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("Forbidden: Invalid internal API key");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
