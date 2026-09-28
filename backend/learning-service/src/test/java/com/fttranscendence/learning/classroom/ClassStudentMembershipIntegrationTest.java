package com.fttranscendence.learning.classroom;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.learning.student.AuthStudentDirectoryClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP coverage for selecting existing auth Student logins into a Tutor class. */
@SpringBootTest
@AutoConfigureMockMvc
class ClassStudentMembershipIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER = 101L;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private AuthStudentDirectoryClient studentDirectory;

  @BeforeEach
  void clearData() {
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
    jdbc.update("DELETE FROM tutor_classes");
  }

  @Test
  void eligibleDirectoryExcludesEnrolledAndForeignProfiles() throws Exception {
    long classId = classroom(OWNER, "P5 Science");
    long enrolled = student(OWNER, 9001L, "Already Enrolled");
    jdbc.update(
        "INSERT INTO class_memberships (student_profile_id, class_id, tutor_id) VALUES (?, ?, ?)",
        enrolled,
        classId,
        OWNER);
    student(202L, 9002L, "Foreign Learner");
    when(studentDirectory.listStudents(anyString()))
        .thenReturn(
            List.of(
                account(9001L, "Already Enrolled", "already@example.com"),
                account(9002L, "Foreign Learner", "foreign@example.com"),
                account(9003L, "Available Learner", "available@example.com")));

    String token = token("TUTOR", OWNER);
    mvc.perform(
            get("/api/learning/tutor/classes/{classId}/eligible-students", classId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].loginUserId").value(9003))
        .andExpect(jsonPath("$[0].fullName").value("Available Learner"))
        .andExpect(jsonPath("$[0].email").value("available@example.com"))
        .andExpect(jsonPath("$[1]").doesNotExist());
    verify(studentDirectory).listStudents("Bearer " + token);
  }

  @Test
  void addsExistingStudentThenPersistsAndRefreshesTheRoster() throws Exception {
    long classId = classroom(OWNER, "P5 Science");
    long unassignedProfile = unassignedStudent(9003L, "Stale Profile Name");
    when(studentDirectory.listStudents(anyString()))
        .thenReturn(List.of(account(9003L, "Available Learner", "available@example.com")));

    String token = token("TUTOR", OWNER);
    String body =
        mvc.perform(
                post("/api/learning/tutor/classes/{classId}/students", classId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"loginUserId\":9003}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(unassignedProfile))
            .andExpect(jsonPath("$.loginUserId").value(9003))
            .andExpect(jsonPath("$.fullName").value("Available Learner"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(
            get("/api/learning/tutor/classes/{classId}/students", classId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].loginUserId").value(9003))
        .andExpect(jsonPath("$[0].fullName").value("Available Learner"));
    mvc.perform(
            get("/api/learning/tutor/classes/{classId}", classId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students[0].fullName").value("Available Learner"));

    long profileId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    mvc.perform(
            delete("/api/learning/tutor/classes/{classId}/students/{studentId}", classId, profileId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(
            get("/api/learning/tutor/classes/{classId}/students", classId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void rejectsDuplicatesMissingAccountsClassesAndForeignTutors() throws Exception {
    long ownClass = classroom(OWNER, "P5 Science");
    long foreignClass = classroom(202L, "Foreign Class");
    when(studentDirectory.listStudents(anyString()))
        .thenReturn(List.of(account(9003L, "Available Learner", "available@example.com")));
    String ownerToken = token("TUTOR", OWNER);

    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", ownClass)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9003}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", ownClass)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9003}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_MEMBERSHIP"));
    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", ownClass)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9999}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", 999999L)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9003}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", foreignClass)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9003}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
  }

  @Test
  void rejectsUnauthenticatedAndStudentRoleClassMembershipCalls() throws Exception {
    long classId = classroom(OWNER, "P5 Science");
    mvc.perform(get("/api/learning/tutor/classes/{classId}/eligible-students", classId))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/learning/tutor/classes/{classId}/students", classId)
                .header("Authorization", "Bearer " + token("STUDENT", 9003L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginUserId\":9003}"))
        .andExpect(status().isForbidden());
  }

  private long classroom(long tutorId, String name) {
    String normalized = name.toLowerCase().replace(' ', '-');
    jdbc.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        name,
        normalized,
        "Science",
        "P5",
        "ACTIVE");
    return jdbc.queryForObject(
        "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
        Long.class,
        tutorId,
        normalized);
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

  private long unassignedStudent(long loginUserId, String name) {
    jdbc.update(
        "INSERT INTO student_profiles (tutor_id, login_user_id, full_name) VALUES (NULL, ?, ?)",
        loginUserId,
        name);
    return jdbc.queryForObject(
        "SELECT id FROM student_profiles WHERE login_user_id = ?", Long.class, loginUserId);
  }

  private AuthStudentDirectoryClient.StudentAccount account(
      long id, String fullName, String email) {
    return new AuthStudentDirectoryClient.StudentAccount(id, fullName, email);
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
