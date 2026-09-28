package com.fttranscendence.learning.syllabus;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SyllabusControllerIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void returnsTheCompleteOrderedTaxonomyTreeToBothApplicationRoles() throws Exception {
    mockMvc
        .perform(
            get("/api/learning/shared/syllabus/tree")
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("SCI"))
        .andExpect(jsonPath("$.items[0].children[0].code").value("SCI_P5"))
        .andExpect(jsonPath("$.items[0].children[0].children[0].code").value("SCI_P5_CYCLES"));

    mockMvc
        .perform(
            get("/api/learning/shared/syllabus/tree")
                .header("Authorization", bearer("STUDENT", 901L)))
        .andExpect(status().isOk());
  }

  @Test
  void returnsValidatedChildrenAndStructuredMissingOrInvalidResponses() throws Exception {
    long p5 =
        jdbcTemplate.queryForObject(
            "SELECT id FROM syllabus_topics WHERE code = ?", Long.class, "SCI_P5");
    mockMvc
        .perform(
            get("/api/learning/shared/syllabus/children?parentId={parentId}&nodeType=THEME", p5)
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].code").value("SCI_P5_CYCLES"));
    mockMvc
        .perform(
            get("/api/learning/shared/syllabus/children?parentId=999999")
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SYLLABUS_NODE_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/shared/syllabus/children?nodeType=UNKNOWN")
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SYLLABUS_QUERY"));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/learning/shared/syllabus/tree")).andExpect(status().isUnauthorized());
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("syllabus@example.com")
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
