package com.fttranscendence.learning.dashboard;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentDashboardIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;
  private static final long LINKED_LOGIN = 9001L;
  private static final Instant MONDAY_UTC = Instant.parse("2026-08-24T16:30:00Z");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean(name = "dashboardClock")
  Clock clock;

  @BeforeEach
  void clearAndFixClock() {
    jdbc.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM mastery_approved_results");
    jdbc.update("DELETE FROM worksheet_assignments");
    jdbc.update("DELETE FROM worksheet_questions");
    jdbc.update("DELETE FROM worksheets");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
    jdbc.update("DELETE FROM class_schedules");
    jdbc.update("DELETE FROM tutor_classes");
    when(clock.instant()).thenReturn(MONDAY_UTC);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  }

  @Test
  void returnsOnlyTheLinkedStudentsApprovedLearningFacts() throws Exception {
    long learner = student(OWNER, LINKED_LOGIN, "Ari Learner");
    long foreign = student(202L, 9002L, "Other Learner");
    long weakTopic = activeTopic(0);
    long strongTopic = activeTopic(1);
    mastery(learner, weakTopic, 45, "LEARNING", 2);
    mastery(learner, strongTopic, 90, "MASTERED", 3);
    mastery(foreign, weakTopic, 99, "MASTERED", 8);
    approvedTopicResult(learner, weakTopic, 9, 20, "2026-08-24 12:00:00");
    long classId = tutorClass(OWNER, "Ari Class");
    jdbc.update(
        "INSERT INTO class_memberships (student_profile_id, class_id, tutor_id) VALUES (?, ?, ?)",
        learner,
        classId,
        OWNER);
    long classWorksheet = worksheet(OWNER, "CLASS-DASH", "CLASS");
    classAssignment(classWorksheet, classId, "2026-08-23 09:00:00", "2026-08-27 09:00:00");
    long studentWorksheet = worksheet(OWNER, "STUDENT-DASH", "STUDENT");
    studentAssignment(studentWorksheet, learner, "2026-08-24 10:00:00", "2026-08-26 10:00:00");
    long undatedWorksheet = worksheet(OWNER, "STUDENT-NO-DEADLINE", "STUDENT");
    studentAssignment(undatedWorksheet, learner, "2026-08-24 13:00:00", null);
    long foreignWorksheet = worksheet(202L, "FOREIGN-DASH", "STUDENT");
    studentAssignment(foreignWorksheet, foreign, "2026-08-24 11:00:00", "2026-08-25 11:00:00");

    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", LINKED_LOGIN))
                .param("timeZone", "Asia/Singapore"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentName").value("Ari Learner"))
        .andExpect(jsonPath("$.timeZone").value("Asia/Singapore"))
        .andExpect(jsonPath("$.today").value("2026-08-25"))
        .andExpect(jsonPath("$.metrics.overallMastery").value(67.50))
        .andExpect(jsonPath("$.metrics.trackedTopicCount").value(2))
        .andExpect(jsonPath("$.metrics.totalAttempts").value(5))
        .andExpect(jsonPath("$.metrics.approvedAssignmentCount").value(3))
        .andExpect(jsonPath("$.latestAssignment.worksheetId").value(undatedWorksheet))
        .andExpect(jsonPath("$.nextAssignment.worksheetId").value(studentWorksheet))
        .andExpect(jsonPath("$.strongestTopic.topicId").value(strongTopic))
        .andExpect(jsonPath("$.strongestTopic.score").value(90.00))
        .andExpect(jsonPath("$.focusTopic.topicId").value(weakTopic))
        .andExpect(jsonPath("$.latestApprovedTopicResult.topicId").value(weakTopic))
        .andExpect(jsonPath("$.latestApprovedTopicResult.approvedMarks").value(9.00))
        .andExpect(jsonPath("$.latestApprovedTopicResult.repeatedMistakeCount").doesNotExist())
        .andExpect(jsonPath("$.latestApprovedTopicResult.sourceSubmissionId").doesNotExist());

    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", 9002L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentName").value("Other Learner"))
        .andExpect(jsonPath("$.metrics.overallMastery").value(99.00))
        .andExpect(jsonPath("$.metrics.approvedAssignmentCount").value(1))
        .andExpect(jsonPath("$.latestAssignment.worksheetId").value(foreignWorksheet))
        .andExpect(jsonPath("$.strongestTopic.score").value(99.00))
        .andExpect(jsonPath("$.focusTopic").doesNotExist())
        .andExpect(jsonPath("$.latestApprovedTopicResult").doesNotExist());
  }

  @Test
  void returnsAValidEmptyDashboardForNewLinkedStudent() throws Exception {
    student(OWNER, LINKED_LOGIN, "New Learner");

    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", LINKED_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("UTC"))
        .andExpect(jsonPath("$.today").value("2026-08-24"))
        .andExpect(jsonPath("$.metrics.overallMastery").doesNotExist())
        .andExpect(jsonPath("$.metrics.trackedTopicCount").value(0))
        .andExpect(jsonPath("$.metrics.totalAttempts").value(0))
        .andExpect(jsonPath("$.metrics.approvedAssignmentCount").value(0))
        .andExpect(jsonPath("$.latestAssignment").doesNotExist())
        .andExpect(jsonPath("$.nextAssignment").doesNotExist())
        .andExpect(jsonPath("$.strongestTopic").doesNotExist())
        .andExpect(jsonPath("$.focusTopic").doesNotExist())
        .andExpect(jsonPath("$.latestApprovedTopicResult").doesNotExist());
  }

  @Test
  void deniesWrongRolesAndProvisionsAStudentWithoutAnExistingProfile() throws Exception {
    mvc.perform(get("/api/learning/student/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/student/dashboard").header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", 9999L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics.totalAttempts").value(0));
    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", 9999L))
                .param("timeZone", "Mars/Olympus"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
  }

  private long student(long tutorId, long loginUserId, String name) {
    jdbc.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (?, ?, ?)",
        tutorId,
        loginUserId,
        name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE login_user_id = ?", Long.class, loginUserId);
  }

  private long activeTopic(int offset) {
    return jdbc.queryForList(
            "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id", Long.class)
        .get(offset);
  }

  private void mastery(long studentId, long topicId, int score, String status, int attempts) {
    jdbc.update(
        "INSERT INTO mastery_records (student_profile_id, syllabus_topic_id, score, mastery_status, attempt_count, calculated_at) VALUES (?, ?, ?, ?, ?, ?)",
        studentId,
        topicId,
        score,
        status,
        attempts,
        java.sql.Timestamp.valueOf("2026-08-24 10:00:00"));
  }

  private void approvedTopicResult(
      long studentId, long topicId, int marks, int available, String reviewedAt) {
    jdbc.update(
        "INSERT INTO mastery_approved_results (source_submission_id, tutor_id, student_profile_id, syllabus_topic_id, approved_marks, available_marks, repeated_mistake_count, revision, active, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, 0, 1, true, ?)",
        5001L,
        OWNER,
        studentId,
        topicId,
        marks,
        available,
        java.sql.Timestamp.valueOf(reviewedAt));
  }

  private long tutorClass(long tutorId, String name) {
    jdbc.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, 'Science', 'P5', 'ACTIVE')",
        tutorId,
        name,
        name.toLowerCase());
    return jdbc.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND class_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private long worksheet(long tutorId, String code, String audience) {
    jdbc.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, 'Dashboard assignment', ?, 'APPROVED', ?)",
        tutorId,
        code,
        audience,
        java.sql.Timestamp.valueOf("2026-08-20 09:00:00"));
    return jdbc.queryForObject(
        "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
  }

  private void classAssignment(long worksheetId, long classId, String assignedAt, String dueAt) {
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at, due_at) VALUES (?, ?, 'CLASS', ?, ?, ?, ?)",
        worksheetId,
        OWNER,
        classId,
        classId,
        java.sql.Timestamp.valueOf(assignedAt),
        java.sql.Timestamp.valueOf(dueAt));
  }

  private void studentAssignment(
      long worksheetId, long studentId, String assignedAt, String dueAt) {
    long tutorId =
        jdbc.queryForObject(
            "SELECT tutor_id FROM worksheets WHERE id = ?", Long.class, worksheetId);
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, student_profile_id, assigned_at, due_at) VALUES (?, ?, 'STUDENT', ?, ?, ?, ?)",
        worksheetId,
        tutorId,
        studentId,
        studentId,
        java.sql.Timestamp.valueOf(assignedAt),
        dueAt == null ? null : java.sql.Timestamp.valueOf(dueAt));
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("student@example.com")
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

@SpringBootTest
@AutoConfigureMockMvc
class StudentDashboardDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired MockMvc mvc;
  @MockitoBean StudentProfileRepository students;

  @Test
  void returnsStructuredDatabaseFailure() throws Exception {
    StudentProfile existing = new StudentProfile();
    existing.setLoginUserId(9001L);
    existing.setFullName("Student");
    when(students.findByLoginUserId(eq(9001L)))
        .thenReturn(java.util.Optional.of(existing))
        .thenThrow(new DataAccessResourceFailureException("database offline"));
    mvc.perform(
            get("/api/learning/student/dashboard")
                .header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STUDENT_DASHBOARD_DATABASE_UNAVAILABLE"));
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("student@example.com")
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
