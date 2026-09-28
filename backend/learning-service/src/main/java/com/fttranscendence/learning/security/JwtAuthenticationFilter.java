package com.fttranscendence.learning.security;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_TOKEN_PREFIX = "Bearer ";
  private static final Set<String> ALLOWED_ROLES = Set.of("TUTOR", "STUDENT");

  private final byte[] signingKey;
  private final StudentProfileProvisioningService profiles;

  public JwtAuthenticationFilter(
      @Value("${jwt.secret}") String secret, StudentProfileProvisioningService profiles) {
    if (isMissingOrPlaceholderSecret(secret)) {
      throw new IllegalArgumentException(
          "JWT_SECRET must contain at least 32 non-placeholder bytes");
    }

    this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    this.profiles = profiles;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
    if (!hasBearerToken(authorizationHeader)) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      authenticateRequest(authorizationHeader);
    } catch (RuntimeException ex) {
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }

  private boolean isMissingOrPlaceholderSecret(String secret) {
    return secret == null
        || secret.getBytes(StandardCharsets.UTF_8).length < 32
        || secret.toLowerCase(Locale.ROOT).contains("change-me");
  }

  private boolean hasBearerToken(String authorizationHeader) {
    return StringUtils.hasText(authorizationHeader)
        && authorizationHeader.startsWith(BEARER_TOKEN_PREFIX);
  }

  private void authenticateRequest(String authorizationHeader) {
    Claims claims = parseClaims(authorizationHeader);
    String email = claims.getSubject();
    String role = claims.get("role", String.class);
    String fullName = claims.get("fullName", String.class);
    Number userIdClaim = claims.get("userId", Number.class);

    if (!hasValidAuthenticationClaims(email, role, userIdClaim)) {
      return;
    }

    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      return;
    }

    AuthenticatedUser principal =
        new AuthenticatedUser(
            userIdClaim.longValue(), email, role, StringUtils.hasText(fullName) ? fullName : email);
    var authentication =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

    SecurityContextHolder.getContext().setAuthentication(authentication);

    if ("STUDENT".equals(role)) {
      profiles.ensureProfile(principal);
    }
  }

  private Claims parseClaims(String authorizationHeader) {
    String token = authorizationHeader.substring(BEARER_TOKEN_PREFIX.length());

    return Jwts.parserBuilder()
        .setSigningKey(Keys.hmacShaKeyFor(signingKey))
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  private boolean hasValidAuthenticationClaims(String email, String role, Number userIdClaim) {
    return StringUtils.hasText(email)
        && ALLOWED_ROLES.contains(role)
        && userIdClaim != null
        && userIdClaim.longValue() > 0;
  }
}
