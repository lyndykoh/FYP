package com.fttranscendence.learning.student;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class StudentProfileIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 101L;
  private static final long STUDENT_LOGIN_ID = 9001L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearData() {
    jdbcTemplate.update("DELETE FROM tutor_alerts");
    jdbcTemplate.update("DELETE FROM progress_reports");
    jdbcTemplate.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbcTemplate.update("DELETE FROM mastery_diagnostic_evidence");
    jdbcTemplate.update("DELETE FROM mastery_approved_results");
    jdbcTemplate.update("DELETE FROM mastery_history");
    jdbcTemplate.update("DELETE FROM mastery_records");
    jdbcTemplate.update("DELETE FROM worksheet_assignments");
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM worksheets");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void returnsCanonicalCompleteProfileForOwnerAndRestrictsTutorOnlyFieldsForLinkedStudent()
      throws Exception {
    long classId = insertClass(OWNER_ID, "P5 Science", "p5 science");
    long studentId = insertStudent(OWNER_ID, STUDENT_LOGIN_ID, "Ada Learner");
    insertMembership(studentId, classId, OWNER_ID);
    long waterTopic = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long plantTopic = topicId("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION");
    long waterMastery = insertMastery(studentId, waterTopic, 92, "MASTERED", 3);
    insertMastery(studentId, plantTopic, 55, "PRACTISING", 2);
    jdbcTemplate.update(
        "INSERT INTO mastery_history (mastery_record_id, previous_score, new_score, previous_status, new_status, source_submission_id, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
        waterMastery,
        70,
        92,
        "IMPROVING",
        "MASTERED",
        7001L,
        "Consistent revision");
    long classWorksheetId =
        insertWorksheet(
            "P5-CLASS-01",
            "Class revision",
            OWNER_ID,
            "CLASS",
            classId,
            classId,
            LocalDateTime.of(2026, 9, 15, 17, 0));
    long directWorksheetId =
        insertWorksheet(
            "P5-DIRECT-01",
            "Direct revision",
            OWNER_ID,
            "STUDENT",
            studentId,
            null,
            LocalDateTime.of(2026, 9, 16, 17, 0));
    insertApprovedProjection(8101L, OWNER_ID, studentId, waterTopic, classWorksheetId, true);
    // Two approved questions on one worksheet are one worksheet metric.
    insertApprovedProjection(8102L, OWNER_ID, studentId, plantTopic, classWorksheetId, true);
    // A retracted result never counts as an approved worksheet outcome.
    insertApprovedProjection(8103L, OWNER_ID, studentId, plantTopic, directWorksheetId, false);
    // The aggregate is owner-scoped even if a malformed external write
    // created an otherwise matching projection.
    insertApprovedProjection(8104L, 202L, studentId, plantTopic, directWorksheetId, true);
    insertAlert(OWNER_ID, studentId, "WEAK_TOPIC", "WARNING", "Water follow-up");
    insertReport(
        OWNER_ID, studentId, "P5-TERM-3", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(studentId))
        .andExpect(jsonPath("$.fullName").value("Ada Learner"))
        .andExpect(jsonPath("$.classes[0].className").value("P5 Science"))
        .andExpect(jsonPath("$.metrics.averageMastery").value(73.50))
        .andExpect(jsonPath("$.metrics.topicCount").value(2))
        .andExpect(jsonPath("$.metrics.totalAttempts").value(5))
        .andExpect(jsonPath("$.mastery[0].topicName").exists())
        .andExpect(jsonPath("$.learningProfile.strengths[0].score").value(92.00))
        .andExpect(jsonPath("$.learningProfile.focusAreas[0].score").value(55.00))
        .andExpect(jsonPath("$.history[0].reason").value("Consistent revision"))
        .andExpect(jsonPath("$.history[0].sourceSubmissionId").doesNotExist())
        .andExpect(jsonPath("$.worksheets.length()").value(2))
        .andExpect(jsonPath("$.worksheets[0].assignmentType").exists())
        .andExpect(jsonPath("$.tutorOnly.activeAlerts[0].title").value("Water follow-up"))
        .andExpect(jsonPath("$.tutorOnly.reports[0].reportCode").value("P5-TERM-3"))
        .andExpect(jsonPath("$.tutorOnly.approvedWorksheetCount").value(1))
        .andExpect(jsonPath("$.tutorOnly.reports[0].snapshot").doesNotExist());

    mockMvc
        .perform(
            get("/api/learning/student/profile")
                .header("Authorization", "Bearer " + token("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(studentId))
        .andExpect(jsonPath("$.metrics.averageMastery").value(73.50))
        .andExpect(jsonPath("$.worksheets.length()").value(2))
        .andExpect(jsonPath("$.tutorOnly").value(nullValue()));
  }

  @Test
  void returnsPartialAndNewProfilesWithExplicitNullableMetricsAndEmptyCollections()
      throws Exception {
    long studentId = insertStudent(OWNER_ID, null, "New Student");

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classes").isEmpty())
        .andExpect(jsonPath("$.metrics.averageMastery").value(nullValue()))
        .andExpect(jsonPath("$.metrics.topicCount").value(0))
        .andExpect(jsonPath("$.metrics.totalAttempts").value(0))
        .andExpect(jsonPath("$.metrics.lastCalculatedAt").value(nullValue()))
        .andExpect(jsonPath("$.mastery").isEmpty())
        .andExpect(jsonPath("$.learningProfile.strengths").isEmpty())
        .andExpect(jsonPath("$.learningProfile.focusAreas").isEmpty())
        .andExpect(jsonPath("$.history").isEmpty())
        .andExpect(jsonPath("$.worksheets").isEmpty())
        .andExpect(jsonPath("$.tutorOnly.activeAlerts").isEmpty())
        .andExpect(jsonPath("$.tutorOnly.reports").isEmpty())
        .andExpect(jsonPath("$.tutorOnly.approvedWorksheetCount").value(0));
  }

  @Test
  void exposesNotStartedMasteryRecordsInTheCanonicalProfile() throws Exception {
    long studentId = insertStudent(OWNER_ID, null, "Legacy Student");
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    insertMastery(studentId, topicId, 0, "NOT_STARTED", 0);

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics.averageMastery").value(0))
        .andExpect(jsonPath("$.metrics.totalAttempts").value(0))
        .andExpect(jsonPath("$.mastery[0].status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.mastery[0].attemptCount").value(0));
  }

  @Test
  void hidesMissingAndForeignProfilesWhileProvisioningTheAuthenticatedStudent() throws Exception {
    long foreignStudent = insertStudent(202L, 9002L, "Other Student");
    String ownerToken = token("TUTOR", OWNER_ID);

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", foreignStudent)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", 999999L)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_PROFILE_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/student/profile")
                .header("Authorization", "Bearer " + token("STUDENT", 9003L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("person@example.com"))
        .andExpect(jsonPath("$.classes").isEmpty());
    org.junit.jupiter.api.Assertions.assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM student_profiles WHERE login_user_id = ? AND tutor_id IS NULL",
            Integer.class,
            9003L));
    mockMvc
        .perform(get("/api/learning/tutor/students/{studentId}/profile", foreignStudent))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/student/profile").header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", foreignStudent)
                .header("Authorization", "Bearer " + token("STUDENT", 9002L)))
        .andExpect(status().isForbidden());
  }

  private long insertClass(long tutorId, String name, String normalizedName) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        name,
        normalizedName,
        "Science",
        "P5",
        "ACTIVE");
    return jdbcTemplate.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
        Long.class,
        tutorId,
        normalizedName);
  }

  private long insertStudent(long tutorId, Long loginId, String name) {
    jdbcTemplate.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (?, ?, ?)",
        tutorId,
        loginId,
        name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM student_profiles WHERE tutor_id = ? AND full_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private void insertMembership(long studentId, long classId, long tutorId) {
    jdbcTemplate.update(
        "INSERT INTO class_memberships (student_profile_id, class_id, tutor_id) VALUES (?, ?, ?)",
        studentId,
        classId,
        tutorId);
  }

  private long topicId(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM syllabus_topics WHERE code = ?", Long.class, code);
  }

  private long insertMastery(long studentId, long topicId, int score, String status, int attempts) {
    jdbcTemplate.update(
        "INSERT INTO mastery_records (student_profile_id, syllabus_topic_id, score, mastery_status, attempt_count, calculated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        studentId,
        topicId,
        score,
        status,
        attempts);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM mastery_records WHERE student_profile_id = ? AND syllabus_topic_id = ?",
        Long.class,
        studentId,
        topicId);
  }

  private long insertWorksheet(
      String code,
      String title,
      long tutorId,
      String audienceType,
      long targetId,
      Long classId,
      LocalDateTime dueAt) {
    jdbcTemplate.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        tutorId,
        code,
        title,
        audienceType,
        "APPROVED");
    Long worksheetId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
    if ("CLASS".equals(audienceType)) {
      jdbcTemplate.update(
          "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at, due_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          worksheetId,
          tutorId,
          audienceType,
          targetId,
          classId,
          dueAt.minusDays(7),
          dueAt);
    } else {
      jdbcTemplate.update(
          "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, student_profile_id, assigned_at, due_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          worksheetId,
          tutorId,
          audienceType,
          targetId,
          targetId,
          dueAt.minusDays(7),
          dueAt);
    }
    return worksheetId;
  }

  private void insertApprovedProjection(
      long sourceSubmissionId,
      long tutorId,
      long studentId,
      long topicId,
      long worksheetId,
      boolean active) {
    jdbcTemplate.update(
        """
            INSERT INTO mastery_approved_results (
                source_submission_id, tutor_id, worksheet_id, student_profile_id, syllabus_topic_id,
                approved_marks, available_marks, repeated_mistake_count, revision, active, reviewed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
        sourceSubmissionId,
        tutorId,
        worksheetId,
        studentId,
        topicId,
        1,
        2,
        0,
        1,
        active);
  }

  private void insertAlert(
      long tutorId, long studentId, String type, String severity, String title) {
    jdbcTemplate.update(
        "INSERT INTO tutor_alerts (tutor_id, student_profile_id, alert_type, severity, alert_status, deduplication_key, title, message) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        tutorId,
        studentId,
        type,
        severity,
        "OPEN",
        "profile-" + title,
        title,
        "Needs attention");
  }

  private void insertReport(
      long tutorId, long studentId, String code, LocalDate start, LocalDate end) {
    jdbcTemplate.update(
        "INSERT INTO progress_reports (tutor_id, student_profile_id, report_code, period_start, period_end, report_status, snapshot) VALUES (?, ?, ?, ?, ?, ?, ?)",
        tutorId,
        studentId,
        code,
        start,
        end,
        "DRAFT",
        "{} ");
  }

  private String token(String role, long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("person@example.com")
        .claim("role", role)
        .claim("userId", userId)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(600)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}

@SpringBootTest
@AutoConfigureMockMvc
class StudentProfileDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired private MockMvc mockMvc;
  @MockitoBean private StudentService studentService;

  @Test
  void returnsStructuredDatabaseError() throws Exception {
    when(studentService.getOwnedStudentProfile(101L, 1L))
        .thenThrow(
            new StudentService.StudentPersistenceException(
                new DataAccessResourceFailureException("database unavailable")));
    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/profile", 1L)
                .header("Authorization", "Bearer " + token("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STUDENT_DATABASE_UNAVAILABLE"));
  }

  private String token(String role, long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("person@example.com")
        .claim("role", role)
        .claim("userId", userId)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(600)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
