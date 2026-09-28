package com.fttranscendence.grading.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GradingSecurityIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;

  @Test
  void tutorCanReachReviewApiButStudentCannot() throws Exception {
    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("TUTOR", 3_600_000)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT", 3_600_000)))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingMalformedExpiredAndUnsupportedRoleTokensAreRejected() throws Exception {
    mockMvc.perform(get("/api/grading/tutor/reviews/1")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("TUTOR", -1)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("PARENT", 3_600_000)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/1")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("TUTOR", 3_600_000, null)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/submissions")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("TUTOR", 3_600_000, 0L)))
        .andExpect(status().isUnauthorized());
  }

  private String bearerToken(String role, long lifetimeMs) {
    return bearerToken(role, lifetimeMs, 101L);
  }

  private String bearerToken(String role, long lifetimeMs, Long userId) {
    var builder =
        Jwts.builder()
            .setSubject(role.toLowerCase() + "@example.com")
            .claim("role", role)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + lifetimeMs));
    if (userId != null) {
      builder.claim("userId", userId);
    }
    String token =
        builder
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
    return "Bearer " + token;
  }
}
