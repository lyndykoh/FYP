package com.fttranscendence.learning.worksheet;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorksheetDetailIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 101L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearWorksheets() {
    jdbcTemplate.update("DELETE FROM worksheet_assignments");
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM worksheets");
  }

  @Test
  void downloadsAnOwnedApprovedWorksheetAsAnAttachment() throws Exception {
    long worksheetId = worksheet(OWNER_ID, "WS-PDF-HTTP-1", "APPROVED");
    addQuestion(worksheetId, "PDF-QUESTION-1", "What is 2 + 2? Explain & show your work.", 0);

    byte[] pdf =
        mockMvc
            .perform(
                get("/api/learning/tutor/worksheets/{worksheetId}/pdf", worksheetId)
                    .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("worksheet-WS-PDF-HTTP-1.pdf")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertTrue(pdf.length > 0);
    assertArrayEquals("%PDF-".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(pdf, 5));
  }

  @Test
  void returnsOwnerScopedDetailWithQuestionsAndAssignmentMetadata() throws Exception {
    long classId = tutorClass(OWNER_ID);
    long worksheetId = worksheet(OWNER_ID, "WS-DETAIL-1", "APPROVED");
    addQuestion(worksheetId, "DETAIL-QUESTION-1", "Explain evaporation.", 0);
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at, due_at) VALUES (?, ?, 'CLASS', ?, ?, CURRENT_TIMESTAMP, DATEADD('DAY', 7, CURRENT_TIMESTAMP))",
        worksheetId,
        OWNER_ID,
        classId,
        classId);

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}", worksheetId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.audienceType").value("CLASS"))
        .andExpect(jsonPath("$.questions[0].code").value("DETAIL-QUESTION-1"))
        .andExpect(jsonPath("$.questions[0].totalMarks").value(1))
        .andExpect(jsonPath("$.assignments[0].assignmentType").value("CLASS"))
        .andExpect(jsonPath("$.assignments[0].classId").value(classId));

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}", worksheetId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 202L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSHEET_RESOURCE_NOT_FOUND"));
  }

  @Test
  void returnsDraftProvenanceSubjectTypeAndImmutableQuestionSnapshots() throws Exception {
    long worksheetId = worksheet(OWNER_ID, "WS-DETAIL-SNAPSHOT", "DRAFT");
    jdbcTemplate.update(
        "UPDATE worksheets SET subject = ?, worksheet_type = ? WHERE id = ?",
        "Science",
        "DIAGNOSTIC",
        worksheetId);
    addQuestion(worksheetId, "DETAIL-SNAPSHOT-LIVE", "The current bank prompt.", 0);
    jdbcTemplate.update(
        "UPDATE worksheet_questions SET question_code_snapshot = ?, prompt_snapshot = ?, question_type_snapshot = ?, total_marks_snapshot = ? WHERE worksheet_id = ?",
        "DETAIL-SNAPSHOT-AT-GENERATION",
        "The prompt retained with this worksheet.",
        "SHORT_ANSWER",
        new BigDecimal("2.50"),
        worksheetId);

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}", worksheetId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.subject").value("Science"))
        .andExpect(jsonPath("$.worksheetType").value("DIAGNOSTIC"))
        .andExpect(jsonPath("$.generationRequestId").isEmpty())
        .andExpect(jsonPath("$.assignments.length()").value(0))
        .andExpect(jsonPath("$.questions[0].code").value("DETAIL-SNAPSHOT-AT-GENERATION"))
        .andExpect(
            jsonPath("$.questions[0].prompt").value("The prompt retained with this worksheet."))
        .andExpect(jsonPath("$.questions[0].questionType").value("SHORT_ANSWER"))
        .andExpect(jsonPath("$.questions[0].totalMarks").value(2.5));

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}", worksheetId)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", 700L)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listsOnlyTheOwnersWorksheetsAndHonoursAnOwnedClassFilter() throws Exception {
    long classId = tutorClass(OWNER_ID);
    long otherClassId = tutorClass(OWNER_ID, "Another class");
    long included = worksheet(OWNER_ID, "WS-LIST-INCLUDED", "APPROVED");
    long excluded = worksheet(OWNER_ID, "WS-LIST-EXCLUDED", "APPROVED");
    worksheet(202L, "WS-LIST-FOREIGN", "APPROVED");
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at) VALUES (?, ?, 'CLASS', ?, ?, CURRENT_TIMESTAMP)",
        included,
        OWNER_ID,
        classId,
        classId);
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at) VALUES (?, ?, 'CLASS', ?, ?, CURRENT_TIMESTAMP)",
        excluded,
        OWNER_ID,
        otherClassId,
        otherClassId);

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets?classId={classId}", classId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("WS-LIST-INCLUDED"));

    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets?classId={classId}", classId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 202L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSHEET_RESOURCE_NOT_FOUND"));
  }

  @Test
  void listsOnlyWorksheetsSubmittableForTheExactTutorClassAndStudent() throws Exception {
    long selectedClass = tutorClass(OWNER_ID, "Submission class");
    long otherClass = tutorClass(OWNER_ID, "Other submission class");
    long enrolledStudent = student(OWNER_ID, 7001L, "Enrolled learner");
    long otherStudent = student(OWNER_ID, 7002L, "Other learner");
    membership(enrolledStudent, OWNER_ID, selectedClass);
    membership(otherStudent, OWNER_ID, otherClass);
    long included = worksheet(OWNER_ID, "WS-SUBMISSION-INCLUDED", "APPROVED");
    long excluded = worksheet(OWNER_ID, "WS-SUBMISSION-EXCLUDED", "APPROVED");
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at) VALUES (?, ?, 'CLASS', ?, ?, CURRENT_TIMESTAMP)",
        included,
        OWNER_ID,
        selectedClass,
        selectedClass);
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at) VALUES (?, ?, 'CLASS', ?, ?, CURRENT_TIMESTAMP)",
        excluded,
        OWNER_ID,
        otherClass,
        otherClass);

    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/classes/{classId}/students/{studentId}/submission-worksheets",
                    selectedClass,
                    enrolledStudent)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("WS-SUBMISSION-INCLUDED"));

    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/classes/{classId}/students/{studentId}/submission-worksheets",
                    selectedClass,
                    otherStudent)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSHEET_RESOURCE_NOT_FOUND"));
  }

  @Test
  void protectsPdfDownloadsByRoleOwnershipAndApprovedState() throws Exception {
    long approved = worksheet(OWNER_ID, "WS-PDF-HTTP-2", "APPROVED");
    addQuestion(approved, "PDF-QUESTION-2", "Explain condensation.", 0);
    long draft = worksheet(OWNER_ID, "WS-PDF-DRAFT", "DRAFT");

    mockMvc
        .perform(get("/api/learning/tutor/worksheets/{worksheetId}/pdf", approved))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}/pdf", approved)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", 700L)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}/pdf", approved)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 202L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSHEET_RESOURCE_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/tutor/worksheets/{worksheetId}/pdf", draft)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORKSHEET_NOT_APPROVED"));
  }

  private long worksheet(long tutorId, String code, String status) {
    jdbcTemplate.update(
        "INSERT INTO worksheets (tutor_id, code, title, instructions, audience_type, status, approved_at) VALUES (?, ?, ?, ?, 'CLASS', ?, CASE WHEN ? = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE NULL END)",
        tutorId,
        code,
        "Worksheet " + code,
        "Answer every question.",
        status,
        status);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
  }

  private void addQuestion(long worksheetId, String code, String prompt, int position) {
    long topicId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM syllabus_topics WHERE code = ?",
            Long.class,
            "SCI_P5_CYCLES_MATTER_WATER_WATER");
    jdbcTemplate.update(
        "INSERT INTO questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) VALUES (?, ?, 'SUBTOPIC', 'OPEN_ENDED', ?, ?, 'Answer', 'ACTIVE')",
        code,
        topicId,
        prompt,
        BigDecimal.ONE);
    long questionId =
        jdbcTemplate.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
    jdbcTemplate.update(
        "INSERT INTO marking_components (question_id, position, description, marks) VALUES (?, 0, 'Criterion', ?)",
        questionId,
        BigDecimal.ONE);
    jdbcTemplate.update(
        "INSERT INTO worksheet_questions (worksheet_id, question_id, position) VALUES (?, ?, ?)",
        worksheetId,
        questionId,
        position);
  }

  private long tutorClass(long tutorId) {
    return tutorClass(tutorId, "Detail class");
  }

  private long tutorClass(long tutorId, String name) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
        tutorId,
        name,
        name.toLowerCase(),
        "Science",
        "P5");
    return jdbcTemplate.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
        Long.class,
        tutorId,
        name.toLowerCase());
  }

  private long student(long tutorId, long loginUserId, String name) {
    jdbcTemplate.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (?, ?, ?)",
        tutorId,
        loginUserId,
        name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM student_profiles WHERE login_user_id = ?", Long.class, loginUserId);
  }

  private void membership(long studentId, long tutorId, long classId) {
    jdbcTemplate.update(
        "INSERT INTO class_memberships (student_profile_id, tutor_id, class_id) VALUES (?, ?, ?)",
        studentId,
        tutorId,
        classId);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .setSubject("tutor@example.com")
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
    return "Bearer " + token;
  }
}
