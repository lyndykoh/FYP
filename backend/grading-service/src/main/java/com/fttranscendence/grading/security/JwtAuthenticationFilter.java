package com.fttranscendence.grading.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final int MINIMUM_SIGNING_KEY_BYTES = 32;
  private static final Set<String> ALLOWED_ROLES = Set.of("TUTOR", "STUDENT");
  private final byte[] signingKey;

  public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
    if (isInvalidSigningSecret(secret)) {
      throw new IllegalArgumentException(
          "JWT_SECRET must contain at least 32 non-placeholder bytes");
    }

    this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String bearerToken = extractBearerToken(request);
    if (bearerToken == null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      authenticateBearerToken(bearerToken);
    } catch (RuntimeException ex) {
      // Invalid or expired tokens never partially authenticate a request.
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }

  private static boolean isInvalidSigningSecret(String secret) {
    if (secret == null) {
      return true;
    }

    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    return secretBytes.length < MINIMUM_SIGNING_KEY_BYTES
        || secret.toLowerCase(Locale.ROOT).contains("change-me");
  }

  private static String extractBearerToken(HttpServletRequest request) {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (!StringUtils.hasText(authorizationHeader)
        || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return null;
    }

    return authorizationHeader.substring(BEARER_PREFIX.length());
  }

  private void authenticateBearerToken(String bearerToken) {
    Claims claims = parseClaims(bearerToken);
    String email = claims.getSubject();
    String role = claims.get("role", String.class);
    Number userIdClaim = claims.get("userId", Number.class);

    if (!hasValidIdentity(email, role, userIdClaim) || hasPriorAuthenticatedPrincipal()) {
      return;
    }

    AuthenticatedUser principal = new AuthenticatedUser(userIdClaim.longValue(), email, role);
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private Claims parseClaims(String bearerToken) {
    return Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(signingKey))
        .build()
        .parseClaimsJws(bearerToken)
        .getBody();
  }

  private static boolean hasValidIdentity(String email, String role, Number userIdClaim) {
    return StringUtils.hasText(email)
        && ALLOWED_ROLES.contains(role)
        && userIdClaim != null
        && userIdClaim.longValue() > 0;
  }

  private static boolean hasPriorAuthenticatedPrincipal() {
    Authentication existingAuthentication = SecurityContextHolder.getContext().getAuthentication();

    // Spring Security 7 may install an anonymous principal before this filter.
    // A valid bearer token replaces it, while a real prior authentication wins.
    return existingAuthentication != null
        && !(existingAuthentication instanceof AnonymousAuthenticationToken);
  }
}
