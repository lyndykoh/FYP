package com.fttranscendence.learning.alert;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertWorkflowTest {
  static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM tutor_alerts");
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_approved_results");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
  }

  @Test
  void generatesExplainableLowRepeatedAndOverdueAlertsAtStrictBoundariesWithoutDuplicates()
      throws Exception {
    long bella = student(101, "Bella Tan");
    long exactBoundary = student(101, "Jayden Lim");
    long topic = topic();
    long lowRecord = mastery(bella, topic, 69, 2);
    mastery(exactBoundary, topic, 70, 1);
    diagnostic(lowRecord, bella, 101, 8001, "KEYWORD");
    diagnostic(lowRecord, bella, 101, 8002, "KEYWORD");
    jdbc.update(
        "INSERT INTO marking_review_status_projection (source_submission_id, tutor_id, student_profile_id, revision, review_state, requested_at, updated_at) VALUES (9001, 101, ?, 1, 'PENDING_REVIEW', DATEADD('HOUR', -49, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)",
        bella);
    mvc.perform(get("/api/learning/tutor/alerts").header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].type", hasItems("WEAK_TOPIC", "REPEATED_MISTAKE", "PENDING_REVIEW")))
        .andExpect(
            jsonPath("$[?(@.type == 'WEAK_TOPIC')].message")
                .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("69%"))));
    mvc.perform(get("/api/learning/tutor/alerts").header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk());
    org.junit.jupiter.api.Assertions.assertEquals(
        3,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM tutor_alerts WHERE tutor_id = 101", Integer.class));
  }

  @Test
  void resolvesAndDismissesOnlyTheOwningTutorsAlertsAndKeepsRoleBoundaries() throws Exception {
    long owner = student(101, "Owner Student");
    long foreign = student(202, "Foreign Student");
    long topic = topic();
    long record = mastery(owner, topic, 20, 1);
    mastery(foreign, topic, 20, 1);
    mvc.perform(get("/api/learning/tutor/alerts").header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk());
    long alert =
        jdbc.queryForObject("SELECT id FROM tutor_alerts WHERE tutor_id = 101", Long.class);
    mvc.perform(
            post("/api/learning/tutor/alerts/{id}/resolve", alert)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    mvc.perform(
            post("/api/learning/tutor/alerts/{id}/dismiss", alert)
                .header("Authorization", bearer("TUTOR", 202)))
        .andExpect(status().isNotFound());
    jdbc.update(
        "INSERT INTO tutor_alerts (tutor_id, student_profile_id, mastery_record_id, alert_type, severity, alert_status, deduplication_key, title, message, created_at, updated_at) VALUES (101, ?, ?, 'WEAK_TOPIC', 'WARNING', 'OPEN', 'manual-dismiss', 'Needs review', 'Explainable alert', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        owner,
        record);
    long second =
        jdbc.queryForObject(
            "SELECT id FROM tutor_alerts WHERE tutor_id = 101 AND deduplication_key = 'manual-dismiss'",
            Long.class);
    mvc.perform(
            post("/api/learning/tutor/alerts/{id}/dismiss", second)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
    mvc.perform(get("/api/learning/tutor/alerts").header("Authorization", bearer("STUDENT", 11)))
        .andExpect(status().isForbidden());
  }

  private long student(long tutor, String name) {
    jdbc.update("INSERT INTO student_profiles (tutor_id, full_name) VALUES (?, ?)", tutor, name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE full_name = ?", Long.class, name);
  }

  private long topic() {
    return jdbc.queryForObject(
        "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT 1", Long.class);
  }

  private long mastery(long student, long topic, int score, int attempts) {
    jdbc.update(
        "INSERT INTO mastery_records (student_profile_id, syllabus_topic_id, score, mastery_status, attempt_count, calculated_at) VALUES (?, ?, ?, 'PRACTISING', ?, CURRENT_TIMESTAMP)",
        student,
        topic,
        score,
        attempts);
    return jdbc.queryForObject(
        "SELECT id FROM mastery_records WHERE student_profile_id = ? AND syllabus_topic_id = ?",
        Long.class,
        student,
        topic);
  }

  private void diagnostic(long record, long student, long tutor, long submission, String category) {
    String mistakeType =
        switch (category) {
          case "CONCEPT" -> "CONCEPT_MISUNDERSTANDING";
          case "KEYWORD" -> "MISSING_KEY_POINT";
          case "EXPRESSION" -> "WEAK_EXPLANATION";
          case "APPLICATION" -> "INCOMPLETE_WORKING";
          default ->
              throw new IllegalArgumentException("Unsupported diagnostic category: " + category);
        };
    jdbc.update(
        "INSERT INTO mastery_diagnostic_evidence (mastery_record_id, student_profile_id, tutor_id, source_submission_id, diagnostic_category, mistake_type, tutor_rationale) VALUES (?, ?, ?, ?, ?, ?, 'Tutor confirmed this evidence')",
        record,
        student,
        tutor,
        submission,
        category,
        mistakeType);
  }

  private String bearer(String role, long user) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("person@example.com")
            .claim("role", role)
            .claim("userId", user)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
  }
}
