package com.fttranscendence.grading.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {
  private static final String LOCAL_ORIGIN = "http://localhost:3000";
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;

  @Test
  void healthIsPublicAndCarriesStandardSecurityHeaders() throws Exception {
    mockMvc
        .perform(get("/actuator/health").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().exists("Permissions-Policy"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"));
  }

  @Test
  void corsAcceptsOnlyConfiguredOrigins() throws Exception {
    mockMvc
        .perform(
            options("/api/grading/student/mistakes")
                .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Idempotency-Key"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    org.hamcrest.Matchers.containsString("Idempotency-Key")));

    mockMvc
        .perform(
            options("/api/grading/student/mistakes")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void studentRouteRejectsUnauthenticatedAndWrongRoleRequests() throws Exception {
    mockMvc.perform(get("/api/grading/student/mistakes")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/grading/student/mistakes")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 1001L)))
        .andExpect(status().isForbidden());
  }

  private static String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("security@example.com")
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
