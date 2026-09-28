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

/** HTTP contract coverage for the Student subject-profile's approved evidence. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubjectProfileIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final String INTEGRATION_KEY = "test-learning-marking-sync-key";
  private static final long OWNER = 101L;

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
  void exposesAllFourDimensionsOnlyWhenTutorConfirmedEvidenceExists() throws Exception {
    long student = student(OWNER, 9001L, "Bella Tan");
    long topic = topic();
    String[] categories = {"CONCEPT", "KEYWORD", "EXPRESSION", "APPLICATION", "APPLICATION"};
    for (int index = 0; index < categories.length; index++) {
      String category = categories[index];
      mvc.perform(sync(approvedEvent(student, topic, 7001L + index, category)))
          .andExpect(status().isNoContent());
    }

    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", student)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimensions.length()").value(4))
        .andExpect(jsonPath("$.dimensions[0].category").value("CONCEPT"))
        .andExpect(jsonPath("$.dimensions[1].category").value("KEYWORD"))
        .andExpect(jsonPath("$.dimensions[2].category").value("EXPRESSION"))
        .andExpect(jsonPath("$.dimensions[3].category").value("APPLICATION"))
        .andExpect(jsonPath("$.dimensions[0].evidenceCount").value(1))
        .andExpect(jsonPath("$.dimensions[1].evidenceCount").value(1))
        .andExpect(jsonPath("$.dimensions[2].evidenceCount").value(1))
        .andExpect(jsonPath("$.dimensions[3].evidenceCount").value(2))
        .andExpect(
            jsonPath("$.dimensions[?(@.category == 'KEYWORD')].evidence[0].sourceReason")
                .value(hasItem("Tutor confirmed KEYWORD evidence.")));
  }

  @Test
  void returnsAccurateEmptyDimensionsAndHidesAProfileFromTheWrongTutor() throws Exception {
    long owned = student(OWNER, 9001L, "Owned Student");
    long foreign = student(202L, 9002L, "Foreign Student");

    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", owned)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.strengths").isEmpty())
        .andExpect(jsonPath("$.growthAreas").isEmpty())
        .andExpect(jsonPath("$.improvements").isEmpty())
        .andExpect(jsonPath("$.dimensions.length()").value(4))
        .andExpect(jsonPath("$.dimensions[0].evidenceCount").value(0))
        .andExpect(jsonPath("$.dimensions[1].evidenceCount").value(0))
        .andExpect(jsonPath("$.dimensions[2].evidenceCount").value(0))
        .andExpect(jsonPath("$.dimensions[3].evidenceCount").value(0));
    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", foreign)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/learning/student/learning-profile")
                .header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(owned));
  }

  @Test
  void requiresTutorAuthorizationForTutorProfiles() throws Exception {
    long owned = student(OWNER, 9001L, "Owned Student");

    mvc.perform(get("/api/learning/tutor/students/{studentId}/learning-profile", owned))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/students/{studentId}/learning-profile", owned)
                .header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isForbidden());
  }

  private String approvedEvent(long student, long topic, long submission, String category) {
    return "{\"eventKey\":\"approved:"
        + submission
        + ":1\",\"state\":\"APPROVED\",\"revision\":1,\"submissionId\":"
        + submission
        + ",\"studentId\":"
        + student
        + ",\"tutorUserId\":"
        + OWNER
        + ",\"worksheetId\":9,\"worksheetQuestionId\":10,\"questionBankId\":11,\"syllabusTopicId\":"
        + topic
        + ",\"syllabusTopicCode\":\"TOPIC\",\"approvedMarks\":0,\"maxMarks\":2,\"approvedAt\":\"2026-08-28T10:00:00\",\"diagnosticEvidence\":[{\"syllabusTopicId\":"
        + topic
        + ",\"category\":\""
        + category
        + "\",\"description\":\"Tutor confirmed "
        + category
        + " evidence.\",\"missingKeywords\":[]}] }";
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
        "SELECT id FROM student_profiles WHERE login_user_id = ?", Long.class, login);
  }

  private long topic() {
    return jdbc.queryForObject(
        "SELECT id FROM syllabus_topics WHERE active = true AND node_type IN ('TOPIC', 'SUBTOPIC') ORDER BY id LIMIT 1",
        Long.class);
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
