package com.fttranscendence.learning.dashboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.learning.classroom.TutorClassRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TutorDashboardIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;
  private static final Instant MONDAY_UTC = Instant.parse("2026-08-24T16:30:00Z");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @MockitoBean(name = "dashboardClock")
  Clock clock;

  @BeforeEach
  void clearAndFixClock() {
    jdbc.update("DELETE FROM tutor_alerts");
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM progress_reports");
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
  void returnsOwnerScopedMetricsTodayScheduleAndLimitedExplainableActivity() throws Exception {
    long mondayClass = tutorClass(OWNER, "Monday Science", "ACTIVE");
    long tuesdayClass = tutorClass(OWNER, "Tuesday Science", "ACTIVE");
    tutorClass(OWNER, "Inactive Science", "INACTIVE");
    schedule(mondayClass, "MONDAY", "09:00:00", "10:00:00");
    schedule(tuesdayClass, "TUESDAY", "10:00:00", "11:00:00");
    long sam = student(OWNER, "Sam Learner");
    long alex = student(OWNER, "Alex Learner");
    student(OWNER, "No Class Learner");
    long foreign = student(202L, "Foreign Learner");
    long foreignClass = tutorClass(202L, "Foreign Science", "ACTIVE");

    assignment(mondayClass, "2026-08-24 10:00:00");
    assignment(202L, foreignClass, "FOREIGN-ONE", "2026-08-24 15:00:00");
    pendingReview(3001L, OWNER, sam, "2026-08-24 11:00:00");
    pendingReview(3002L, OWNER, alex, "2026-08-24 13:00:00");
    pendingReview(3003L, 202L, foreign, "2026-08-24 14:00:00");
    alert(
        OWNER, sam, "OPEN", "open-sam", "Open alert", "Sam needs attention", "2026-08-24 12:00:00");
    alert(
        OWNER,
        sam,
        "ACKNOWLEDGED",
        "ack-sam",
        "Acknowledged alert",
        "Already acknowledged",
        "2026-08-24 13:00:00");
    alert(
        OWNER,
        alex,
        "RESOLVED",
        "resolved-alex",
        "Resolved alert",
        "Not active",
        "2026-08-24 14:00:00");
    alert(
        202L,
        foreign,
        "OPEN",
        "foreign-open",
        "Foreign alert",
        "Must not be visible",
        "2026-08-24 15:00:00");
    report(OWNER, sam, "SAM-FINAL", "FINAL");
    report(OWNER, alex, "ALEX-DRAFT", "DRAFT");
    report(202L, foreign, "FOREIGN-FINAL", "FINAL");

    mvc.perform(
            get("/api/learning/tutor/dashboard")
                .header("Authorization", bearer("TUTOR", OWNER))
                .param("timeZone", "UTC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("UTC"))
        .andExpect(jsonPath("$.today").value("2026-08-24"))
        .andExpect(jsonPath("$.metrics.activeClassCount").value(2))
        .andExpect(jsonPath("$.metrics.studentCount").value(3))
        .andExpect(jsonPath("$.metrics.pendingReviewCount").value(2))
        .andExpect(jsonPath("$.metrics.needsAttentionStudentCount").value(1))
        .andExpect(jsonPath("$.metrics.reportsReadyCount").value(1))
        .andExpect(jsonPath("$.todaySchedule.length()").value(1))
        .andExpect(jsonPath("$.todaySchedule[0].className").value("Monday Science"))
        .andExpect(jsonPath("$.todaySchedule[0].startTime").value("09:00:00"))
        .andExpect(jsonPath("$.recentActivity.length()").value(5))
        .andExpect(jsonPath("$.recentActivity[0].type").value("ALERT_CREATED"))
        .andExpect(jsonPath("$.recentActivity[1].type").value("REVIEW_REQUESTED"))
        .andExpect(jsonPath("$.recentActivity[2].type").value("ALERT_CREATED"))
        .andExpect(jsonPath("$.recentActivity[3].type").value("REVIEW_REQUESTED"))
        .andExpect(jsonPath("$.recentActivity[4].type").value("WORKSHEET_ASSIGNED"));
  }

  @Test
  void selectsTodaysScheduleFromTheRequestedIanaTimezoneAndReturnsEmptyTutorState()
      throws Exception {
    long mondayClass = tutorClass(OWNER, "Monday Science", "ACTIVE");
    long mondaySecondClass = tutorClass(OWNER, "Alpha Monday", "ACTIVE");
    long tuesdayClass = tutorClass(OWNER, "Tuesday Science", "ACTIVE");
    schedule(mondayClass, "MONDAY", "09:00:00", "10:00:00");
    schedule(mondaySecondClass, "MONDAY", "09:00:00", "10:00:00");
    schedule(tuesdayClass, "TUESDAY", "10:00:00", "11:00:00");

    mvc.perform(
            get("/api/learning/tutor/dashboard")
                .header("Authorization", bearer("TUTOR", OWNER))
                .param("timeZone", "Asia/Singapore"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today").value("2026-08-25"))
        .andExpect(jsonPath("$.todaySchedule[0].className").value("Tuesday Science"));

    mvc.perform(
            get("/api/learning/tutor/dashboard")
                .header("Authorization", bearer("TUTOR", OWNER))
                .param("timeZone", "UTC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.todaySchedule[0].className").value("Alpha Monday"))
        .andExpect(jsonPath("$.todaySchedule[1].className").value("Monday Science"));

    mvc.perform(get("/api/learning/tutor/dashboard").header("Authorization", bearer("TUTOR", 202L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeZone").value("UTC"))
        .andExpect(jsonPath("$.metrics.activeClassCount").value(0))
        .andExpect(jsonPath("$.metrics.studentCount").value(0))
        .andExpect(jsonPath("$.metrics.pendingReviewCount").value(0))
        .andExpect(jsonPath("$.metrics.needsAttentionStudentCount").value(0))
        .andExpect(jsonPath("$.metrics.reportsReadyCount").value(0))
        .andExpect(jsonPath("$.todaySchedule").isEmpty())
        .andExpect(jsonPath("$.recentActivity").isEmpty());
  }

  @Test
  void deniesNonTutorsAndReturnsStructuredInvalidTimezoneError() throws Exception {
    mvc.perform(get("/api/learning/tutor/dashboard")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/dashboard").header("Authorization", bearer("STUDENT", 7001L)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/learning/tutor/dashboard")
                .header("Authorization", bearer("TUTOR", OWNER))
                .param("timeZone", "Mars/Olympus"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
  }

  @Test
  void limitsRecentActivityToTheNewestTwentyEvents() throws Exception {
    long student = student(OWNER, "Activity Learner");
    for (int index = 0; index < 21; index++) {
      pendingReview(5000L + index, OWNER, student, String.format("2026-08-24 10:%02d:00", index));
    }
    mvc.perform(
            get("/api/learning/tutor/dashboard").header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recentActivity.length()").value(20))
        .andExpect(jsonPath("$.recentActivity[0].sourceId").value(5020))
        .andExpect(jsonPath("$.recentActivity[19].sourceId").value(5001));
  }

  private long tutorClass(long tutorId, String name, String status) {
    jdbc.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, 'Science', 'P5', ?)",
        tutorId,
        name,
        name.toLowerCase(),
        status);
    return jdbc.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND class_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private void schedule(long classId, String day, String start, String end) {
    jdbc.update(
        "INSERT INTO class_schedules (class_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
        classId,
        day,
        start,
        end);
  }

  private long student(long tutorId, String name) {
    jdbc.update("INSERT INTO student_profiles (tutor_id, full_name) VALUES (?, ?)", tutorId, name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE tutor_id = ? AND full_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private void assignment(long classId, String assignedAt) {
    assignment(OWNER, classId, "DASHBOARD-ONE", assignedAt);
  }

  private void assignment(long tutorId, long classId, String code, String assignedAt) {
    jdbc.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status) VALUES (?, ?, 'Dashboard worksheet', 'CLASS', 'DRAFT')",
        tutorId,
        code);
    long worksheet =
        jdbc.queryForObject(
            "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at) VALUES (?, ?, 'CLASS', ?, ?, ?)",
        worksheet,
        tutorId,
        classId,
        classId,
        java.sql.Timestamp.valueOf(assignedAt));
  }

  private void pendingReview(long source, long tutor, long student, String requestedAt) {
    jdbc.update(
        "INSERT INTO marking_review_status_projection (source_submission_id, tutor_id, student_profile_id, revision, review_state, requested_at, updated_at) VALUES (?, ?, ?, 1, 'PENDING_REVIEW', ?, ?)",
        source,
        tutor,
        student,
        java.sql.Timestamp.valueOf(requestedAt),
        java.sql.Timestamp.valueOf(requestedAt));
  }

  private void alert(
      long tutor,
      long student,
      String status,
      String key,
      String title,
      String message,
      String createdAt) {
    String acknowledgedAt = "ACKNOWLEDGED".equals(status) ? createdAt : null;
    boolean resolved = "RESOLVED".equals(status) || "DISMISSED".equals(status);
    jdbc.update(
        "INSERT INTO tutor_alerts (tutor_id, student_profile_id, alert_type, severity, alert_status, deduplication_key, title, message, acknowledged_at, resolved_at, resolved_by_user_id, created_at, updated_at) VALUES (?, ?, 'WEAK_TOPIC', 'WARNING', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        tutor,
        student,
        status,
        key,
        title,
        message,
        acknowledgedAt == null ? null : java.sql.Timestamp.valueOf(acknowledgedAt),
        resolved ? java.sql.Timestamp.valueOf(createdAt) : null,
        resolved ? tutor : null,
        java.sql.Timestamp.valueOf(createdAt),
        java.sql.Timestamp.valueOf(createdAt));
  }

  private void report(long tutor, long student, String code, String status) {
    boolean finalReport = "FINAL".equals(status);
    java.sql.Timestamp finalizedAt =
        finalReport ? java.sql.Timestamp.valueOf("2026-08-24 12:00:00") : null;
    jdbc.update(
        "INSERT INTO progress_reports (tutor_id, student_profile_id, report_code, period_start, period_end, report_status, snapshot, finalized_at, finalized_by_user_id) VALUES (?, ?, ?, DATE '2026-08-01', DATE '2026-08-24', ?, 'Report snapshot', ?, ?)",
        tutor,
        student,
        code,
        status,
        finalizedAt,
        finalReport ? tutor : null);
  }

  private String bearer(String role, long user) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("dashboard@example.com")
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

@SpringBootTest
@AutoConfigureMockMvc
class TutorDashboardDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired MockMvc mvc;
  @MockitoBean TutorClassRepository classes;

  @Test
  void returnsStructuredDatabaseFailure() throws Exception {
    when(classes.findAllByTutorIdAndStatusOrderByClassNameAsc(
            org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.any()))
        .thenThrow(new DataAccessResourceFailureException("database offline"));
    mvc.perform(
            get("/api/learning/tutor/dashboard")
                .header("Authorization", bearer("TUTOR", 101L))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DASHBOARD_DATABASE_UNAVAILABLE"));
  }

  private String bearer(String role, long user) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("dashboard@example.com")
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
