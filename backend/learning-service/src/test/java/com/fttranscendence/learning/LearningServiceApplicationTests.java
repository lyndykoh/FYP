package com.fttranscendence.learning;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LearningServiceApplicationTests {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoadsAndHealthEndpointIsPublic() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void protectedPathsRejectMissingMalformedAndExpiredTokens() throws Exception {
    mockMvc.perform(get("/api/learning/shared/ping")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/learning/shared/ping").header("Authorization", "Bearer malformed"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/shared/ping")
                .header("Authorization", "Bearer " + token("TUTOR", 11L, -60)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void roleBoundariesAreEnforcedBeforeFutureControllersAreAdded() throws Exception {
    mockMvc
        .perform(
            get("/api/learning/tutor/ping")
                .header("Authorization", "Bearer " + token("STUDENT", 12L, 600)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/student/ping")
                .header("Authorization", "Bearer " + token("TUTOR", 11L, 600)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/tutor/ping")
                .header("Authorization", "Bearer " + token("TUTOR", 11L, 600)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/learning/student/ping")
                .header("Authorization", "Bearer " + token("STUDENT", 12L, 600)))
        .andExpect(status().isNotFound());
  }

  @Test
  void tokenRequiresKnownRoleAndPositiveUserId() throws Exception {
    mockMvc
        .perform(
            get("/api/learning/shared/ping")
                .header("Authorization", "Bearer " + token("ADMIN", 11L, 600)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/shared/ping")
                .header("Authorization", "Bearer " + token("TUTOR", 0L, 600)))
        .andExpect(status().isUnauthorized());
  }

  private String token(String role, long userId, long lifetimeSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("person@example.com")
        .claim("role", role)
        .claim("userId", userId)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(lifetimeSeconds)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
