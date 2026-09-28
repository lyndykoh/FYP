package com.fttranscendence.learning.insight;

import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LearningProfileIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final String INTEGRATION_KEY = "test-learning-marking-sync-key";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_approved_results");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM tutor_alerts");
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
  }

  @Test
  void appliesTypedApprovedEvidenceAtomicallyAndExposesItToOwnerAndLinkedStudent()
      throws Exception {
    long student = student(101, 9001, "Bella Tan");
    long topic = topic();
    String approved =
        "{\"eventKey\":\"approved:7001:1\",\"state\":\"APPROVED\",\"revision\":1,\"submissionId\":7001,\"studentId\":"
            + student
            + ",\"tutorUserId\":101,\"worksheetId\":9,\"worksheetQuestionId\":10,\"questionBankId\":11,\"syllabusTopicId\":"
            + topic
            + ",\"syllabusTopicCode\":\"TOPIC\",\"approvedMarks\":0,\"maxMarks\":2,\"approvedAt\":\"2026-08-28T10:00:00\",\"diagnosticEvidence\":[{\"syllabusTopicId\":"
            + topic
            + ",\"category\":\"KEYWORD\",\"description\":\"Tutor confirmed the required term was omitted.\",\"missingKeywords\":[\"adaptation\"]}]}";
    mvc.perform(sync(approved)).andExpect(status().isNoContent());
    String concept =
        approved
            .replace("approved:7001:1", "approved:7003:1")
            .replace("\"submissionId\":7001", "\"submissionId\":7003")
            .replace("\"category\":\"KEYWORD\"", "\"category\":\"CONCEPT\"")
            .replace("\"adaptation\"", "\"heat transfer\"");
    mvc.perform(sync(concept)).andExpect(status().isNoContent());
    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", student)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.findings[*].type", hasItem("KEYWORD_WEAKNESS")))
        .andExpect(jsonPath("$.findings[*].type", hasItem("CONCEPT_WEAKNESS")))
        .andExpect(
            jsonPath("$.findings[?(@.type == 'KEYWORD_WEAKNESS')].evidence[0].sourceReason")
                .value(hasItem("Tutor confirmed the required term was omitted.")));
    mvc.perform(
            get("/api/learning/student/learning-profile")
                .header("Authorization", bearer("STUDENT", 9001)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(student));
    mvc.perform(sync(approved)).andExpect(status().isNoContent());
    org.junit.jupiter.api.Assertions.assertEquals(
        2, jdbc.queryForObject("SELECT COUNT(*) FROM mastery_diagnostic_evidence", Integer.class));
    org.junit.jupiter.api.Assertions.assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT attempt_count FROM mastery_records WHERE student_profile_id = ?",
            Integer.class,
            student));
  }

  @Test
  void hidesForeignProfilesAndEnforcesTutorStudentAndIntegrationBoundaries() throws Exception {
    long owned = student(101, 9001, "Owner Student");
    long foreign = student(202, 9002, "Foreign Student");
    long topic = topic();
    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", foreign)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/learning/student/learning-profile")
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/learning/tutor/students/{studentId}/learning-profile", owned))
        .andExpect(status().isUnauthorized());
    String payload =
        "{\"eventKey\":\"approved:7002:1\",\"state\":\"APPROVED\",\"revision\":1,\"submissionId\":7002,\"studentId\":"
            + owned
            + ",\"tutorUserId\":101,\"worksheetId\":9,\"worksheetQuestionId\":10,\"questionBankId\":11,\"syllabusTopicId\":"
            + topic
            + ",\"syllabusTopicCode\":\"TOPIC\",\"approvedMarks\":1,\"maxMarks\":2,\"approvedAt\":\"2026-08-28T10:00:00\",\"diagnosticEvidence\":[]}";
    mvc.perform(
            post("/api/learning/internal/approved-marking-evidence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MARKING_SYNC_FORBIDDEN"));
    mvc.perform(
            post("/api/learning/internal/approved-marking-evidence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header("X-Learning-Integration-Key", INTEGRATION_KEY))
        .andExpect(status().isNoContent());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sync(
      String payload) {
    return post("/api/learning/internal/approved-marking-evidence")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload)
        .header("X-Learning-Integration-Key", INTEGRATION_KEY);
  }

  private long student(long tutor, long login, String name) {
    jdbc.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (?, ?, ?)",
        tutor,
        login,
        name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE full_name = ?", Long.class, name);
  }

  private long topic() {
    return jdbc.queryForObject(
        "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT 1", Long.class);
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
