package com.fttranscendence.learning.report;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;
  private static final long FOREIGN_TUTOR = 202L;
  private static final long LINKED_STUDENT_LOGIN = 9001L;
  private static final long UNRELATED_STUDENT_LOGIN = 9002L;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProgressReportRepository reports;

  @BeforeEach
  void clearData() {
    jdbc.update("DELETE FROM progress_reports");
    jdbc.update("DELETE FROM tutor_notes");
    jdbc.update("DELETE FROM tutor_alerts");
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM worksheet_assignments");
    jdbc.update("DELETE FROM worksheet_questions");
    jdbc.update("DELETE FROM worksheets");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
    jdbc.update("DELETE FROM tutor_classes");
  }

  @Test
  void ownerReadsDraftAndFinalEvidenceSnapshotsWithoutRecalculation() throws Exception {
    long student = student(OWNER, LINKED_STUDENT_LOGIN, "Ada Learner");
    long finalReport =
        report(
            OWNER,
            student,
            "ADA-FINAL",
            "FINAL",
            "{\"summary\":\"Strong progress\",\"evidence\":[{\"topic\":\"Water cycle\",\"score\":82}]}");
    long draftReport = report(OWNER, student, "ADA-DRAFT", "DRAFT", "{}");

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", finalReport)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(finalReport))
        .andExpect(jsonPath("$.studentId").value(student))
        .andExpect(jsonPath("$.studentName").value("Ada Learner"))
        .andExpect(jsonPath("$.reportCode").value("ADA-FINAL"))
        .andExpect(jsonPath("$.status").value("FINAL"))
        .andExpect(jsonPath("$.snapshot.summary").value("Strong progress"))
        .andExpect(jsonPath("$.snapshot.evidence[0].topic").value("Water cycle"));

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", draftReport)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.snapshot").isEmpty());
  }

  @Test
  void recipientCanReadOnlyTheirFinalSnapshotAndInaccessibleReportsDoNotEnumerate()
      throws Exception {
    long linked = student(OWNER, LINKED_STUDENT_LOGIN, "Linked Learner");
    long unrelated = student(OWNER, UNRELATED_STUDENT_LOGIN, "Unrelated Learner");
    long finalReport = report(OWNER, linked, "LINKED-FINAL", "FINAL", "{\"summary\":\"Final\"}");
    long emptyFinalReport = report(OWNER, linked, "LINKED-EMPTY", "FINAL", "{}");
    long draftReport = report(OWNER, linked, "LINKED-DRAFT", "DRAFT", "{\"summary\":\"Draft\"}");
    long unrelatedReport =
        report(OWNER, unrelated, "OTHER-FINAL", "FINAL", "{\"summary\":\"Other\"}");

    mvc.perform(
            get("/api/learning/student/reports/{reportId}", finalReport)
                .header("Authorization", bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINAL"))
        .andExpect(jsonPath("$.snapshot.summary").value("Final"));

    mvc.perform(
            get("/api/learning/student/reports/{reportId}", emptyFinalReport)
                .header("Authorization", bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINAL"))
        .andExpect(jsonPath("$.snapshot").isEmpty());

    for (long inaccessible : new long[] {draftReport, unrelatedReport, 999_999L}) {
      mvc.perform(
              get("/api/learning/student/reports/{reportId}", inaccessible)
                  .header("Authorization", bearer("STUDENT", LINKED_STUDENT_LOGIN)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }
  }

  @Test
  void returnsPartialEvidenceSnapshotWithoutInventingMissingSections() throws Exception {
    long student = student(OWNER, LINKED_STUDENT_LOGIN, "Partial Learner");
    long reportId =
        report(
            OWNER, student, "PARTIAL", "FINAL", "{\"summary\":\"A short evidence-based update\"}");

    mvc.perform(
            get("/api/learning/student/reports/{reportId}", reportId)
                .header("Authorization", bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.summary").value("A short evidence-based update"))
        .andExpect(jsonPath("$.snapshot.evidence").doesNotExist())
        .andExpect(jsonPath("$.snapshot.mastery").doesNotExist());
  }

  @Test
  void hidesForeignTutorReportsAndEnforcesRoleAndAuthenticationBoundaries() throws Exception {
    long foreignStudent = student(FOREIGN_TUTOR, 9003L, "Foreign Learner");
    long foreignReport =
        report(
            FOREIGN_TUTOR, foreignStudent, "FOREIGN-FINAL", "FINAL", "{\"summary\":\"Private\"}");

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", foreignReport)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", 999_999L)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));

    mvc.perform(get("/api/learning/tutor/reports/{reportId}", foreignReport))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", foreignReport)
                .header("Authorization", bearer("STUDENT", 9003L)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/learning/student/reports/{reportId}", foreignReport)
                .header("Authorization", bearer("TUTOR", FOREIGN_TUTOR)))
        .andExpect(status().isForbidden());
  }

  @Test
  void finalSnapshotCannotBeMutatedAndTheApiStillReturnsItsOriginalEvidence() throws Exception {
    long student = student(OWNER, LINKED_STUDENT_LOGIN, "Immutable Learner");
    long reportId =
        report(OWNER, student, "IMMUTABLE", "FINAL", "{\"summary\":\"Original evidence\"}");
    ProgressReport finalReport = reports.findByIdAndTutorId(reportId, OWNER).orElseThrow();

    assertEquals(ProgressReport.ReportStatus.FINAL, finalReport.getReportStatus());
    assertThrows(
        IllegalStateException.class, () -> finalReport.updateSnapshot("{\"summary\":\"Changed\"}"));

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", reportId)
                .header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.snapshot.summary").value("Original evidence"));
  }

  @Test
  void preventsRawMalformedAndNonObjectStoredSnapshotsFromEscaping() throws Exception {
    long student = student(OWNER, LINKED_STUDENT_LOGIN, "Malformed Learner");
    long malformedReport = report(OWNER, student, "MALFORMED", "FINAL", "not-json");
    long arrayReport =
        report(OWNER, student, "ARRAY-SNAPSHOT", "FINAL", "[{\"summary\":\"not an object\"}]");

    for (long reportId : new long[] {malformedReport, arrayReport}) {
      mvc.perform(
              get("/api/learning/tutor/reports/{reportId}", reportId)
                  .header("Authorization", bearer("TUTOR", OWNER)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("REPORT_SNAPSHOT_UNAVAILABLE"));
    }
  }

  @Test
  void exportsTheSameOwnerScopedSnapshotAsAPdfForTutorAndFinalRecipientOnly() throws Exception {
    long student = student(OWNER, LINKED_STUDENT_LOGIN, "PDF Learner");
    student(OWNER, UNRELATED_STUDENT_LOGIN, "Unrelated PDF Learner");
    long finalReport =
        report(
            OWNER,
            student,
            "PDF-FINAL",
            "FINAL",
            "{\"mastery\":[{\"topic\":\"Fractions\",\"score\":82}],\"mistakes\":[\"Place value error\"]}");
    long draftReport =
        report(OWNER, student, "PDF-DRAFT", "DRAFT", "{\"summary\":\"Tutor draft\"}");

    byte[] pdf =
        mvc.perform(
                get("/api/learning/tutor/reports/{reportId}/pdf", finalReport)
                    .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("progress-report-PDF-FINAL.pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertTrue(pdf.length > 0);
    assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(pdf, 5));

    mvc.perform(get("/api/learning/tutor/reports/{reportId}/pdf", finalReport))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}/pdf", finalReport)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}/pdf", 999_999L)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/student/reports/{reportId}/pdf", finalReport)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    mvc.perform(
            get("/api/learning/student/reports/{reportId}/pdf", finalReport)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", UNRELATED_STUDENT_LOGIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/student/reports/{reportId}/pdf", draftReport)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LINKED_STUDENT_LOGIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}/pdf", finalReport)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", FOREIGN_TUTOR)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
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

  private long report(
      long tutorId, long studentId, String code, String reportStatus, String snapshot) {
    boolean finalReport = "FINAL".equals(reportStatus);
    jdbc.update(
        "INSERT INTO progress_reports (tutor_id, student_profile_id, report_code, period_start, period_end, report_status, snapshot, finalized_at, finalized_by_user_id) "
            + "VALUES (?, ?, ?, DATE '2026-08-01', DATE '2026-08-31', ?, ?, ?, ?)",
        tutorId,
        studentId,
        code,
        reportStatus,
        snapshot,
        finalReport ? java.sql.Timestamp.valueOf("2026-08-31 12:00:00") : null,
        finalReport ? tutorId : null);
    return jdbc.queryForObject(
        "SELECT id FROM progress_reports WHERE student_profile_id = ? AND report_code = ?",
        Long.class,
        studentId,
        code);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("report@example.com")
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
class ReportDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired MockMvc mvc;
  @MockitoBean ProgressReportRepository reports;

  @Test
  void returnsStructuredDatabaseFailure() throws Exception {
    when(reports.findByIdAndTutorId(anyLong(), anyLong()))
        .thenThrow(new DataAccessResourceFailureException("database offline"));

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}", 1L)
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("REPORT_DATABASE_UNAVAILABLE"));
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("report@example.com")
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
class ReportPdfFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired MockMvc mvc;
  @MockitoBean ReportPdfService reportPdfs;

  @Test
  void returnsStructuredPdfFailureWithoutLeakingRendererDetails() throws Exception {
    when(reportPdfs.tutorExport(101L, 1L))
        .thenThrow(
            new ReportPdfService.ReportPdfUnavailableException(
                new IllegalStateException("renderer offline")));

    mvc.perform(
            get("/api/learning/tutor/reports/{reportId}/pdf", 1L)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("REPORT_PDF_UNAVAILABLE"));
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("report@example.com")
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
