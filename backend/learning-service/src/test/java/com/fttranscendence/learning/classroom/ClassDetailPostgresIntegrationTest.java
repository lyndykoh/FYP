package com.fttranscendence.learning.classroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression coverage for PostgreSQL's enforced read-only transactions. A detail request may
 * enqueue an insight refresh, but that write must be committed in its own transaction without
 * turning the enclosing detail projection into a writer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClassDetailPostgresIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 701L;

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    // Testcontainers adds loggerLevel as an existing query parameter, so this
    // must be joined with '&' rather than a second '?' for PostgreSQL to apply
    // the search path to JdbcTemplate as well as Hibernate.
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=learning");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "learning");
    registry.add("spring.flyway.default-schema", () -> "learning");
    registry.add("spring.flyway.schemas", () -> "learning");
    registry.add("learning.insights.refresh-delay-ms", () -> "3600000");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void clearData() {
    jdbcTemplate.execute("TRUNCATE TABLE tutor_classes CASCADE");
  }

  @Test
  void firstAndSecondDetailRequestsEnqueueOneRefreshAndRemainSuccessful() throws Exception {
    long classId = insertClass();
    String authorization = "Bearer " + tutorToken();

    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", classId)
                .header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(classId))
        .andExpect(jsonPath("$.insight.status").value("REFRESHING"));

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM class_insight_refresh_queue WHERE class_id = ? AND tutor_id = ?",
            Integer.class,
            classId,
            OWNER_ID));
    assertEquals(
        "QUEUED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM class_insight_refresh_queue WHERE class_id = ?",
            String.class,
            classId));

    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", classId)
                .header("Authorization", authorization))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(classId))
        .andExpect(jsonPath("$.insight.status").value("REFRESHING"));

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM class_insight_refresh_queue WHERE class_id = ? AND tutor_id = ?",
            Integer.class,
            classId,
            OWNER_ID));
  }

  private long insertClass() {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, ?, ?, ?)",
        OWNER_ID,
        "PostgreSQL detail class",
        "postgresql detail class",
        "Science",
        "P5",
        "ACTIVE");
    return jdbcTemplate.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
        Long.class,
        OWNER_ID,
        "postgresql detail class");
  }

  private String tutorToken() {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("tutor@example.com")
        .claim("role", "TUTOR")
        .claim("userId", OWNER_ID)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(600)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
