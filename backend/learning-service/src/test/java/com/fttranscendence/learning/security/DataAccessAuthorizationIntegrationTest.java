package com.fttranscendence.learning.security;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Verifies role gates and owner-scoped lookups at the HTTP boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DataAccessAuthorizationIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clear() {
    jdbc.update("DELETE FROM worksheet_assignments");
    jdbc.update("DELETE FROM worksheet_questions");
    jdbc.update("DELETE FROM worksheets");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
    jdbc.update("DELETE FROM class_schedules");
    jdbc.update("DELETE FROM tutor_classes");
  }

  @Test
  void deniesUnauthenticatedUnknownAndWrongRoleRequestsByDefault() throws Exception {
    mvc.perform(get("/api/learning/tutor/students/1/mastery-map"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/learning/tutor/students/1/mastery-map")
                .header("Authorization", bearer("STUDENT", 9001)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/learning/not-an-api").header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isForbidden());
  }

  @Test
  void exposesOnlyTutorOwnedClassAndStudentWhileAStudentCanReadOnlySelf() throws Exception {
    long ownedStudent = student(101, 9001, "Owned Learner");
    long foreignStudent = student(202, 9002, "Foreign Learner");
    long foreignClass = tutorClass(202, "Foreign Class");

    mvc.perform(
            get("/api/learning/tutor/students/{id}/mastery-map", ownedStudent)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(ownedStudent));
    mvc.perform(
            get("/api/learning/tutor/students/{id}/mastery-map", foreignStudent)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MASTERY_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/tutor/students/{id}/mastery-map", 999999L)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MASTERY_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/tutor/classes/{id}", foreignClass)
                .header("Authorization", bearer("TUTOR", 101)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CLASS_ACCESS_FORBIDDEN"));
    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", 9001)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(ownedStudent));
    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", 9002)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(foreignStudent));
  }

  @Test
  void validatesUploadContextAgainstAssignmentAndHidesForeignWorksheetIds() throws Exception {
    long student = student(101, 9001, "Assigned Learner");
    long worksheet = approvedStudentWorksheet(101, student, "ASSIGNED-1");
    String body =
        """
            {"actorUserId":9001,"actorRole":"STUDENT","studentId":%d,"worksheetId":%d}
            """
            .formatted(student, worksheet);

    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(body.replace("9001", "9002")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_CONTEXT_NOT_FOUND"));
    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "wrong-key")
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBMISSION_AUTHORIZATION_FORBIDDEN"));
  }

  @Test
  void returnsPersistedMarkingComponentPositionsInTheServerOnlyMarkingContext() throws Exception {
    long student = student(101, 9001, "Assigned Learner");
    long worksheet = approvedStudentWorksheet(101, student, "MARKING-CONTEXT-1");
    addQuestionWithTwoMarkingComponents(worksheet, "MARKING-CONTEXT-Q1");
    String body =
        """
            {"actorUserId":9001,"actorRole":"STUDENT","studentId":%d,"worksheetId":%d}
            """
            .formatted(student, worksheet);

    mvc.perform(
            post("/api/learning/internal/submission-authorization/marking-context")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tutorUserId").value(101))
        .andExpect(jsonPath("$.questions[0].markingComponents.length()").value(2))
        .andExpect(jsonPath("$.questions[0].markingComponents[0].position").value(0))
        .andExpect(
            jsonPath("$.questions[0].markingComponents[0].description")
                .value("Identifies the process"))
        .andExpect(jsonPath("$.questions[0].markingComponents[1].position").value(1))
        .andExpect(jsonPath("$.questions[0].markingComponents[1].marks").value(2.0));
  }

  @Test
  void validatesTutorUploadAgainstTheExactClassStudentAndWorksheetRelationship() throws Exception {
    long selectedClass = tutorClass(101, "Tutor upload class");
    long otherClass = tutorClass(101, "Other tutor class");
    long student = student(101, 9001, "Assigned learner");
    jdbc.update(
        "INSERT INTO class_memberships (student_profile_id, tutor_id, class_id) VALUES (?, ?, ?)",
        student,
        101,
        selectedClass);
    long worksheet = approvedClassWorksheet(101, selectedClass, "CLASS-ASSIGNED-1");
    long otherWorksheet = approvedClassWorksheet(101, otherClass, "CLASS-ASSIGNED-2");
    String body =
        """
            {"actorUserId":101,"actorRole":"TUTOR","studentId":%d,"worksheetId":%d,"classId":%d}
            """
            .formatted(student, worksheet, selectedClass);

    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(
                    body.replace(
                        "\"classId\":%d".formatted(selectedClass),
                        "\"classId\":%d".formatted(otherClass))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_CONTEXT_NOT_FOUND"));
    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(body.replace(",\"classId\":%d".formatted(selectedClass), "")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_CONTEXT_NOT_FOUND"));
    mvc.perform(
            post("/api/learning/internal/submission-authorization")
                .header("X-Learning-Integration-Key", "test-learning-marking-sync-key")
                .contentType(APPLICATION_JSON)
                .content(
                    body.replace(
                        "\"worksheetId\":%d".formatted(worksheet),
                        "\"worksheetId\":%d".formatted(otherWorksheet))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_CONTEXT_NOT_FOUND"));
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

  private long approvedStudentWorksheet(long tutorId, long studentId, String code) {
    jdbc.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, ?, 'STUDENT', 'APPROVED', CURRENT_TIMESTAMP)",
        tutorId,
        code,
        "Assigned worksheet");
    long worksheetId =
        jdbc.queryForObject(
            "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, student_profile_id) VALUES (?, ?, 'STUDENT', ?, ?)",
        worksheetId,
        tutorId,
        studentId,
        studentId);
    return worksheetId;
  }

  private long approvedClassWorksheet(long tutorId, long classId, String code) {
    jdbc.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, ?, 'CLASS', 'APPROVED', CURRENT_TIMESTAMP)",
        tutorId,
        code,
        "Assigned worksheet");
    long worksheetId =
        jdbc.queryForObject(
            "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
    jdbc.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id) VALUES (?, ?, 'CLASS', ?, ?)",
        worksheetId,
        tutorId,
        classId,
        classId);
    return worksheetId;
  }

  private void addQuestionWithTwoMarkingComponents(long worksheetId, String code) {
    long topicId =
        jdbc.queryForObject(
            "SELECT id FROM syllabus_topics WHERE code = ?",
            Long.class,
            "SCI_P5_CYCLES_MATTER_WATER_WATER");
    jdbc.update(
        "INSERT INTO questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) "
            + "VALUES (?, ?, 'SUBTOPIC', 'OPEN_ENDED', ?, ?, ?, 'ACTIVE')",
        code,
        topicId,
        "Explain the water cycle.",
        new BigDecimal("3.00"),
        "Water evaporates and condenses.");
    long questionId =
        jdbc.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
    jdbc.update(
        "INSERT INTO marking_components (question_id, position, description, marks) VALUES (?, ?, ?, ?)",
        questionId,
        0,
        "Identifies the process",
        BigDecimal.ONE);
    jdbc.update(
        "INSERT INTO marking_components (question_id, position, description, marks) VALUES (?, ?, ?, ?)",
        questionId,
        1,
        "Explains the outcome",
        new BigDecimal("2.00"));
    jdbc.update(
        "INSERT INTO worksheet_questions (worksheet_id, question_id, position) VALUES (?, ?, 0)",
        worksheetId,
        questionId);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("person@example.com")
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
