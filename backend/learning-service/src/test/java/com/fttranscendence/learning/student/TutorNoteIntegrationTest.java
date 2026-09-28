package com.fttranscendence.learning.student;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
class TutorNoteIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 101L;
  private static final long OTHER_TUTOR_ID = 202L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearData() {
    jdbcTemplate.update("DELETE FROM tutor_notes");
    jdbcTemplate.update("DELETE FROM tutor_alerts");
    jdbcTemplate.update("DELETE FROM progress_reports");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
  }

  @Test
  void createsEditsDeletesAndReturnsPrivateNotesNewestFirst() throws Exception {
    long studentId = insertStudent(OWNER_ID, 9001L, "Ada Learner");
    String ownerToken = bearer("TUTOR", OWNER_ID);

    mockMvc
        .perform(
            post(notesPath(studentId))
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\"  Call home after revision.  \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.studentId").value(studentId))
        .andExpect(jsonPath("$.content").value("Call home after revision."))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
    long createdId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tutor_notes WHERE student_profile_id = ?", Long.class, studentId);

    mockMvc
        .perform(
            put(notesPath(studentId) + "/" + createdId)
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\"Updated follow-up\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdId))
        .andExpect(jsonPath("$.content").value("Updated follow-up"));
    mockMvc
        .perform(delete(notesPath(studentId) + "/" + createdId).header("Authorization", ownerToken))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get(notesPath(studentId)).header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    insertNote(OWNER_ID, studentId, "Earlier observation", LocalDateTime.of(2026, 8, 1, 10, 0));
    insertNote(OWNER_ID, studentId, "Latest observation", LocalDateTime.of(2026, 9, 1, 10, 0));
    mockMvc
        .perform(get(notesPath(studentId)).header("Authorization", ownerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("Latest observation"))
        .andExpect(jsonPath("$[1].content").value("Earlier observation"));
  }

  @Test
  void retainsXssPayloadAsPlainNoteTextAndRejectsEmptyOrMalformedRequests() throws Exception {
    long studentId = insertStudent(OWNER_ID, 9001L, "Ada Learner");
    String ownerToken = bearer("TUTOR", OWNER_ID);
    String xss = "<img src=x onerror=alert(1)> <script>alert(1)</script>";

    mockMvc
        .perform(
            post(notesPath(studentId))
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content(
                    "{\"content\":\"<img src=x onerror=alert(1)> <script>alert(1)</script>\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value(xss));
    mockMvc
        .perform(
            post(notesPath(studentId))
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields").isMap());
    mockMvc
        .perform(
            put(notesPath(studentId) + "/1")
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mockMvc
        .perform(
            post(notesPath(studentId))
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_REQUEST"));
  }

  @Test
  void hidesForeignStudentsAndNotesAndNeverLetsStudentsAccessTutorNotes() throws Exception {
    long ownedStudent = insertStudent(OWNER_ID, 9001L, "Ada Learner");
    long foreignStudent = insertStudent(OTHER_TUTOR_ID, 9002L, "Other Learner");
    long foreignNote =
        insertNote(OTHER_TUTOR_ID, foreignStudent, "Private to another tutor", LocalDateTime.now());
    String ownerToken = bearer("TUTOR", OWNER_ID);

    mockMvc
        .perform(get(notesPath(foreignStudent)).header("Authorization", ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    mockMvc
        .perform(
            post(notesPath(foreignStudent))
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\"Try create\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    mockMvc
        .perform(
            delete(notesPath(foreignStudent) + "/" + foreignNote)
                .header("Authorization", ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    mockMvc
        .perform(
            put(notesPath(ownedStudent) + "/" + foreignNote)
                .header("Authorization", ownerToken)
                .contentType("application/json")
                .content("{\"content\":\"Try edit\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TUTOR_NOTE_NOT_FOUND"));
    mockMvc
        .perform(delete(notesPath(ownedStudent) + "/999999").header("Authorization", ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TUTOR_NOTE_NOT_FOUND"));
    mockMvc.perform(get(notesPath(ownedStudent))).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(notesPath(ownedStudent)).header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/student/profile").header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tutorOnly").value(nullValue()));
  }

  private String notesPath(long studentId) {
    return "/api/learning/tutor/students/" + studentId + "/notes";
  }

  private long insertStudent(long tutorId, Long loginUserId, String name) {
    jdbcTemplate.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (?, ?, ?)",
        tutorId,
        loginUserId,
        name);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM student_profiles WHERE tutor_id = ? AND full_name = ?",
        Long.class,
        tutorId,
        name);
  }

  private long insertNote(long tutorId, long studentId, String content, LocalDateTime updatedAt) {
    jdbcTemplate.update(
        "INSERT INTO tutor_notes (tutor_id, student_profile_id, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        tutorId,
        studentId,
        content,
        updatedAt,
        updatedAt);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM tutor_notes WHERE tutor_id = ? AND student_profile_id = ? AND content = ?",
        Long.class,
        tutorId,
        studentId,
        content);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .setSubject("person@example.com")
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

@SpringBootTest
@AutoConfigureMockMvc
class TutorNoteDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired private MockMvc mockMvc;
  @MockitoBean private TutorNoteService tutorNoteService;

  @Test
  void returnsStructuredDatabaseError() throws Exception {
    when(tutorNoteService.list(101L, 1L))
        .thenThrow(
            new TutorNoteService.TutorNotePersistenceException(
                new DataAccessResourceFailureException("database unavailable")));
    mockMvc
        .perform(
            get("/api/learning/tutor/students/1/notes")
                .header("Authorization", bearer("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("TUTOR_NOTE_DATABASE_UNAVAILABLE"));
    when(tutorNoteService.create(
            org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any()))
        .thenThrow(
            new TutorNoteService.TutorNotePersistenceException(
                new DataAccessResourceFailureException("database unavailable")));
    mockMvc
        .perform(
            post("/api/learning/tutor/students/1/notes")
                .header("Authorization", bearer("TUTOR", 101L))
                .contentType("application/json")
                .content("{\"content\":\"Follow up\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("TUTOR_NOTE_DATABASE_UNAVAILABLE"));
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .setSubject("person@example.com")
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
