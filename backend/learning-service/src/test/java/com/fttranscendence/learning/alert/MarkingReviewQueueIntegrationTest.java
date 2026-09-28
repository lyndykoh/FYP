package com.fttranscendence.learning.alert;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class MarkingReviewQueueIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM student_profiles");
  }

  @Test
  void listsOnlyTheTutorsPendingReviewsNewestFirst() throws Exception {
    long ownerStudent = student(OWNER, "Owner Learner");
    long foreignStudent = student(202L, "Foreign Learner");
    pendingReview(1101L, OWNER, 701L, ownerStudent, "2026-08-24 10:00:00");
    pendingReview(1102L, OWNER, 702L, ownerStudent, "2026-08-24 12:00:00");
    pendingReview(1100L, OWNER, 703L, ownerStudent, "2026-08-24 12:00:00");
    resolvedReview(1103L, OWNER, 704L, ownerStudent, "2026-08-24 13:00:00");
    pendingReview(2101L, 202L, 801L, foreignStudent, "2026-08-24 14:00:00");

    mvc.perform(
            get("/api/learning/tutor/marking-reviews")
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].submissionId").value(1100))
        .andExpect(jsonPath("$[0].studentId").value(ownerStudent))
        .andExpect(jsonPath("$[0].studentName").value("Owner Learner"))
        .andExpect(jsonPath("$[0].worksheetId").value(703))
        .andExpect(jsonPath("$[0].requestedAt").value("2026-08-24T12:00:00"))
        .andExpect(jsonPath("$[1].submissionId").value(1102))
        .andExpect(jsonPath("$[2].submissionId").value(1101));
  }

  @Test
  void rejectsUnauthenticatedAndStudentRequests() throws Exception {
    mvc.perform(get("/api/learning/tutor/marking-reviews")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/marking-reviews")
                .header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isForbidden());
  }

  private long student(long tutorId, String name) {
    jdbc.update("INSERT INTO student_profiles (tutor_id, full_name) VALUES (?, ?)", tutorId, name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE tutor_id = ? AND full_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private void pendingReview(
      long submissionId, long tutorId, long worksheetId, long studentId, String requestedAt) {
    review(submissionId, tutorId, worksheetId, studentId, "PENDING_REVIEW", requestedAt);
  }

  private void resolvedReview(
      long submissionId, long tutorId, long worksheetId, long studentId, String requestedAt) {
    review(submissionId, tutorId, worksheetId, studentId, "RESOLVED", requestedAt);
  }

  private void review(
      long submissionId,
      long tutorId,
      long worksheetId,
      long studentId,
      String state,
      String requestedAt) {
    java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(requestedAt);
    jdbc.update(
        "INSERT INTO marking_review_status_projection (source_submission_id, tutor_id, worksheet_id, student_profile_id, revision, review_state, requested_at, updated_at) VALUES (?, ?, ?, ?, 1, ?, ?, ?)",
        submissionId,
        tutorId,
        worksheetId,
        studentId,
        state,
        timestamp,
        timestamp);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("queue@example.com")
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
