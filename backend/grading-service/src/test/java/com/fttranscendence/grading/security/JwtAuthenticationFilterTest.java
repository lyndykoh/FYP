package com.fttranscendence.grading.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validBearerTokenReplacesSpringSecurityAnonymousAuthentication() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/grading/mistakes/me");
    request.addHeader("Authorization", bearer("STUDENT", 17L));

    new JwtAuthenticationFilter(SECRET)
        .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    AuthenticatedUser user =
        assertInstanceOf(
            AuthenticatedUser.class,
            SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    assertEquals(17L, user.userId());
    assertEquals("STUDENT", user.role());
  }

  private static String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("student@example.test")
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
  }
}
