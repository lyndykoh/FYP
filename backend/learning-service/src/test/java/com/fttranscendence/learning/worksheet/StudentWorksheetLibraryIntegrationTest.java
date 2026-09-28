package com.fttranscendence.learning.worksheet;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Covers the public, self-scoped Student worksheet library contract. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentWorksheetLibraryIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;
  private static final long LEARNER_LOGIN = 9001L;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM marking_review_status_projection");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_approved_results");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM worksheet_assignments");
    jdbc.update("DELETE FROM worksheet_questions");
    jdbc.update("DELETE FROM worksheets");
    jdbc.update("DELETE FROM marking_components");
    jdbc.update("DELETE FROM questions");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
    jdbc.update("DELETE FROM class_schedules");
    jdbc.update("DELETE FROM tutor_classes");
  }

  @Test
  void returnsOnlyLinkedAssignmentsAndCombinesSubjectTopicDateAndStatusFilters() throws Exception {
    long learner = student(OWNER, LEARNER_LOGIN, "Ari Learner");
    long foreign = student(202L, 9002L, "Foreign Learner");
    long subject = subject();
    long topic = topic();
    long classId = tutorClass(OWNER, "Ari Class");
    jdbc.update(
        "INSERT INTO class_memberships (student_profile_id, class_id, tutor_id) VALUES (?, ?, ?)",
        learner,
        classId,
        OWNER);

    long marked = worksheet(OWNER, "LIB-MARKED", "Marked science", "CLASS");
    QuestionReference markedQ1 =
        question(marked, topic, "LIB-MARKED-Q1", 0, new BigDecimal("2.00"));
    QuestionReference markedQ2 =
        question(marked, topic, "LIB-MARKED-Q2", 1, new BigDecimal("2.00"));
    assertNotEquals(markedQ1.questionBankId(), markedQ1.worksheetQuestionId());
    assertNotEquals(markedQ2.questionBankId(), markedQ2.worksheetQuestionId());
    classAssignment(marked, classId, "2026-08-10 09:00:00", "2026-08-25 17:00:00");
    approved(
        marked,
        markedQ1.questionBankId(),
        learner,
        topic,
        5001L,
        "1.00",
        "2.00",
        "2026-08-15 09:00:00");
    approved(
        marked,
        markedQ2.questionBankId(),
        learner,
        topic,
        5002L,
        "2.00",
        "2.00",
        "2026-08-15 10:00:00");
    review(marked, learner, 5001L, "RESOLVED", "2026-08-12 08:00:00");

    long submitted = worksheet(OWNER, "LIB-SUBMITTED", "Waiting for review", "STUDENT");
    question(submitted, topic, "LIB-SUBMITTED-Q1", 0, BigDecimal.ONE);
    studentAssignment(submitted, learner, "2026-08-11 09:00:00", null);
    review(submitted, learner, 5003L, "PENDING_REVIEW", "2026-08-16 08:00:00");

    long assigned = worksheet(OWNER, "LIB-ASSIGNED", "To complete", "STUDENT");
    question(assigned, topic, "LIB-ASSIGNED-Q1", 0, BigDecimal.ONE);
    studentAssignment(assigned, learner, "2026-08-12 09:00:00", "2026-08-30 17:00:00");

    long foreignWorksheet = worksheet(202L, "LIB-FOREIGN", "Foreign data", "STUDENT");
    question(foreignWorksheet, topic, "LIB-FOREIGN-Q1", 0, BigDecimal.ONE);
    studentAssignment(foreignWorksheet, foreign, "2026-08-10 09:00:00", null);

    mvc.perform(
            get("/api/learning/student/worksheets")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.code == 'LIB-FOREIGN')]").isEmpty())
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].status").value("MARKED"))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].score.earned").value(3.00))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].score.available").value(4.00))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].score.percent").value(75.00))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].assignedAt").value("2026-08-10T09:00:00"))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].dueAt").value("2026-08-25T17:00:00"))
        .andExpect(
            jsonPath("$[?(@.code == 'LIB-MARKED')].submittedAt").value("2026-08-12T08:00:00"))
        .andExpect(jsonPath("$[?(@.code == 'LIB-MARKED')].reviewedAt").value("2026-08-15T10:00:00"))
        .andExpect(jsonPath("$[?(@.code == 'LIB-SUBMITTED')].status").value("SUBMITTED"))
        .andExpect(jsonPath("$[?(@.code == 'LIB-SUBMITTED')].score").value(contains(nullValue())))
        .andExpect(jsonPath("$[?(@.code == 'LIB-ASSIGNED')].status").value("ASSIGNED"));

    mvc.perform(
            get("/api/learning/student/worksheets")
                .param("subjectId", String.valueOf(subject))
                .param("topicId", String.valueOf(topic))
                .param("status", "MARKED")
                .param("assignedFrom", "2026-08-10")
                .param("assignedTo", "2026-08-10")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("LIB-MARKED"))
        .andExpect(jsonPath("$[0].subjects[0].id").value(subject))
        .andExpect(jsonPath("$[0].topics[0].id").value(topic));
  }

  @Test
  void neverScoresPartialOrUnapprovedDataAndReturnsValidEmptyLibrary() throws Exception {
    long learner = student(OWNER, LEARNER_LOGIN, "Ari Learner");
    long topic = topic();
    long worksheet = worksheet(OWNER, "LIB-PARTIAL", "Partially marked", "STUDENT");
    QuestionReference q1 = question(worksheet, topic, "LIB-PARTIAL-Q1", 0, BigDecimal.ONE);
    question(worksheet, topic, "LIB-PARTIAL-Q2", 1, BigDecimal.ONE);
    studentAssignment(worksheet, learner, "2026-08-10 09:00:00", null);
    approved(
        worksheet,
        q1.questionBankId(),
        learner,
        topic,
        5101L,
        "1.00",
        "1.00",
        "2026-08-12 09:00:00");
    review(worksheet, learner, 5101L, "RESOLVED", "2026-08-11 09:00:00");

    mvc.perform(
            get("/api/learning/student/worksheets")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
        .andExpect(jsonPath("$[0].score").value(nullValue()));

    long newLogin = 9003L;
    student(OWNER, newLogin, "New Learner");
    mvc.perform(
            get("/api/learning/student/worksheets")
                .header("Authorization", bearer("STUDENT", newLogin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void returnsStructuredInvalidFiltersAndDoesNotEnumerateProfiles() throws Exception {
    student(OWNER, LEARNER_LOGIN, "Ari Learner");
    long topic = topic();
    mvc.perform(
            get("/api/learning/student/worksheets")
                .param("status", "COMPLETE")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_WORKSHEET_FILTER"));
    mvc.perform(
            get("/api/learning/student/worksheets")
                .param("subjectId", String.valueOf(topic))
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_WORKSHEET_FILTER"));
    mvc.perform(
            get("/api/learning/student/worksheets")
                .param("assignedFrom", "2026-09-01")
                .param("assignedTo", "2026-08-01")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_WORKSHEET_FILTER"));
    mvc.perform(
            get("/api/learning/student/worksheets")
                .param("assignedFrom", "not-a-date")
                .header("Authorization", bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_WORKSHEET_FILTER"));
    mvc.perform(
            get("/api/learning/student/worksheets")
                .header("Authorization", bearer("STUDENT", 9999L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/learning/student/worksheets")).andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/student/worksheets").header("Authorization", bearer("TUTOR", OWNER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void opensAndExportsOnlyTheAuthenticatedStudentsAssignedWorksheet() throws Exception {
    long learner = student(OWNER, LEARNER_LOGIN, "Ari Learner");
    long foreignLearner = student(202L, 9002L, "Foreign Learner");
    long topic = topic();
    long assigned = worksheet(OWNER, "LIB-DETAIL", "Water cycle practice", "STUDENT");
    question(assigned, topic, "LIB-DETAIL-Q1", 0, new BigDecimal("2.00"));
    studentAssignment(assigned, learner, "2026-08-12 09:00:00", "2026-08-30 17:00:00");
    long foreign = worksheet(202L, "LIB-DETAIL-FOREIGN", "Foreign practice", "STUDENT");
    question(foreign, topic, "LIB-DETAIL-FOREIGN-Q1", 0, BigDecimal.ONE);
    studentAssignment(foreign, foreignLearner, "2026-08-12 09:00:00", null);

    mvc.perform(
            get("/api/learning/student/worksheets/{worksheetId}", assigned)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("LIB-DETAIL"))
        .andExpect(jsonPath("$.instructions").value(nullValue()))
        .andExpect(jsonPath("$.questions[0].code").value("LIB-DETAIL-Q1"))
        .andExpect(jsonPath("$.questions[0].prompt").value("LIB-DETAIL-Q1 prompt"))
        .andExpect(jsonPath("$.questions[0].modelAnswer").doesNotExist())
        .andExpect(jsonPath("$.assignments").doesNotExist());

    mvc.perform(
            get("/api/learning/student/worksheets/{worksheetId}/pdf", assigned)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));

    mvc.perform(
            get("/api/learning/student/worksheets/{worksheetId}", foreign)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", LEARNER_LOGIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSHEET_RESOURCE_NOT_FOUND"));
    mvc.perform(get("/api/learning/student/worksheets/{worksheetId}/pdf", assigned))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/student/worksheets/{worksheetId}", assigned)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER)))
        .andExpect(status().isForbidden());
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

  private long subject() {
    return jdbc.queryForObject(
        "SELECT id FROM syllabus_topics WHERE node_type = 'SUBJECT' AND active = true ORDER BY id LIMIT 1",
        Long.class);
  }

  private long topic() {
    return jdbc.queryForObject(
        "SELECT id FROM syllabus_topics WHERE node_type = 'SUBTOPIC' AND active = true ORDER BY id LIMIT 1",
        Long.class);
  }

  private long tutorClass(long tutor, String name) {
    jdbc.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, 'Science', 'P5', 'ACTIVE')",
        tutor,
        name,
        name.toLowerCase());
    return jdbc.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND class_name = ?",
        Long.class,
        tutor,
        name);
  }

  private long worksheet(long tutor, String code, String title, String audience) {
    jdbc.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, ?, ?, 'APPROVED', ?)",
        tutor,
        code,
        title,
        audience,
        java.sql.Timestamp.valueOf("2026-08-01 09:00:00"));
    return jdbc.queryForObject(
        "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutor, code);
  }

  private QuestionReference question(
      long worksheet, long topic, String code, int position, BigDecimal marks) {
    jdbc.update(
        "INSERT INTO questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) VALUES (?, ?, 'SUBTOPIC', 'OPEN_ENDED', ?, ?, 'Answer', 'ACTIVE')",
        code,
        topic,
        code + " prompt",
        marks);
    long questionBankId =
        jdbc.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
    jdbc.update(
        "INSERT INTO marking_components (question_id, position, description, marks) VALUES (?, 0, 'Criterion', ?)",
        questionBankId,
        marks);
    jdbc.update(
        "INSERT INTO worksheet_questions (worksheet_id, question_id, position) VALUES (?, ?, ?)",
        worksheet,
        questionBankId,
        position);
    long worksheetQuestionId =
        jdbc.queryForObject(
            "SELECT id FROM worksheet_questions WHERE worksheet_id = ? AND question_id = ?",
            Long.class,
            worksheet,
            questionBankId);
    // The real producers send question-bank ids. Force the fixture's join-row
    // identity into a different namespace so a mistaken comparison cannot pass.
    if (worksheetQuestionId == questionBankId) {
      long distinctWorksheetQuestionId = worksheetQuestionId + 1_000_000L;
      jdbc.update(
          "UPDATE worksheet_questions SET id = ? WHERE id = ?",
          distinctWorksheetQuestionId,
          worksheetQuestionId);
      worksheetQuestionId = distinctWorksheetQuestionId;
    }
    return new QuestionReference(questionBankId, worksheetQuestionId);
  }

  private void classAssignment(long worksheet, long classId, String assigned, String due) {
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at, due_at) VALUES (?, ?, 'CLASS', ?, ?, ?, ?)",
        worksheet,
        OWNER,
        classId,
        classId,
        java.sql.Timestamp.valueOf(assigned),
        java.sql.Timestamp.valueOf(due));
  }

  private void studentAssignment(long worksheet, long student, String assigned, String due) {
    long tutor =
        jdbc.queryForObject("SELECT tutor_id FROM worksheets WHERE id = ?", Long.class, worksheet);
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, student_profile_id, assigned_at, due_at) VALUES (?, ?, 'STUDENT', ?, ?, ?, ?)",
        worksheet,
        tutor,
        student,
        student,
        java.sql.Timestamp.valueOf(assigned),
        due == null ? null : java.sql.Timestamp.valueOf(due));
  }

  private void approved(
      long worksheet,
      long questionBankId,
      long student,
      long topic,
      long submission,
      String marks,
      String max,
      String at) {
    jdbc.update(
        "INSERT INTO mastery_approved_results (source_submission_id, tutor_id, worksheet_id, worksheet_question_id, student_profile_id, syllabus_topic_id, approved_marks, available_marks, repeated_mistake_count, revision, active, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 1, true, ?)",
        submission,
        OWNER,
        worksheet,
        questionBankId,
        student,
        topic,
        new BigDecimal(marks),
        new BigDecimal(max),
        java.sql.Timestamp.valueOf(at));
  }

  private void review(long worksheet, long student, long submission, String state, String at) {
    jdbc.update(
        "INSERT INTO marking_review_status_projection (source_submission_id, tutor_id, worksheet_id, student_profile_id, revision, review_state, requested_at) VALUES (?, ?, ?, ?, 1, ?, ?)",
        submission,
        OWNER,
        worksheet,
        student,
        state,
        java.sql.Timestamp.valueOf(at));
  }

  private String bearer(String role, long user) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("student@example.com")
            .claim("role", role)
            .claim("userId", user)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
  }

  private record QuestionReference(long questionBankId, long worksheetQuestionId) {}
}
