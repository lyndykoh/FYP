package com.fttranscendence.learning.student;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentManagementIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private AuthStudentDirectoryClient studentDirectory;

  @BeforeEach
  void clearStudentData() {
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
    jdbcTemplate.update("DELETE FROM tutor_classes");
    List<AuthStudentDirectoryClient.StudentAccount> accounts =
        List.of(
            account(9001L, "Ada Learner", "ada.learner@example.com"),
            account(9002L, "Duplicate Membership", "duplicate@example.com"),
            account(9003L, "Foreign Membership", "foreign@example.com"),
            account(9004L, "Stable Student", "stable@example.com"));
    when(studentDirectory.listStudents(anyString())).thenReturn(accounts);
    when(studentDirectory.listStudents(anyString(), any())).thenReturn(accounts);
  }

  @Test
  void tutorCanCreateListFilterDetailAndUpdateOwnedStudents() throws Exception {
    long scienceClass = insertClass(101L, "P5 Science", "p5 science");
    long mathClass = insertClass(101L, "P5 Mathematics", "p5 mathematics");
    String tutorToken = token("TUTOR", 101L);

    mockMvc
        .perform(
            get("/api/learning/tutor/students").header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    String created =
        mockMvc
            .perform(
                post("/api/learning/tutor/students")
                    .header("Authorization", "Bearer " + tutorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(studentJson("  Ada Learner  ", 9001L, scienceClass)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tutorId").value(101))
            .andExpect(jsonPath("$.fullName").value("Ada Learner"))
            .andExpect(jsonPath("$.loginUserId").value(9001))
            .andExpect(jsonPath("$.classes[0].id").value(scienceClass))
            .andExpect(jsonPath("$.classes[0].className").value("P5 Science"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();

    mockMvc
        .perform(
            get("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .param("classId", String.valueOf(scienceClass)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(studentId));

    mockMvc
        .perform(
            put("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Ada Lovelace", null, mathClass)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.loginUserId").doesNotExist())
        .andExpect(jsonPath("$.classes.length()").value(1))
        .andExpect(jsonPath("$.classes[0].id").value(mathClass));

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.classes[0].className").value("P5 Mathematics"));

    mockMvc
        .perform(
            get("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .param("classId", String.valueOf(scienceClass)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void rejectsInvalidDuplicateAndForeignMembershipRequestsWithoutChangingTheStudent()
      throws Exception {
    long ownClass = insertClass(101L, "P5 Science", "p5 science");
    long foreignClass = insertClass(202L, "P5 Science", "p5 science other");
    String tutorToken = token("TUTOR", 101L);

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\" \",\"classIds\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.fullName").exists());

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Duplicate Membership", 9002L, ownClass, ownClass)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_MEMBERSHIP"));

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Foreign Membership", 9003L, foreignClass)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));

    String created =
        mockMvc
            .perform(
                post("/api/learning/tutor/students")
                    .header("Authorization", "Bearer " + tutorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(studentJson("Stable Student", 9004L, ownClass)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();

    mockMvc
        .perform(
            put("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Should Not Persist", null, ownClass, ownClass)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_MEMBERSHIP"));

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Stable Student"))
        .andExpect(jsonPath("$.classes[0].id").value(ownClass));
  }

  @Test
  void hidesCrossOwnerStudentsAndClassesAndRejectsDuplicateLoginIdentities() throws Exception {
    long ownClass = insertClass(101L, "P5 Science", "p5 science");
    String tutorToken = token("TUTOR", 101L);
    String otherTutorToken = token("TUTOR", 202L);
    String created =
        mockMvc
            .perform(
                post("/api/learning/tutor/students")
                    .header("Authorization", "Bearer " + tutorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(studentJson("Login Student", 9001L, ownClass)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long studentId = ((Number) JsonPath.read(created, "$.id")).longValue();

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + otherTutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Linked Elsewhere", 9001L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LOGIN_IDENTITY_CONFLICT"));

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + otherTutorToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));

    mockMvc
        .perform(
            put("/api/learning/tutor/students/{studentId}", studentId)
                .header("Authorization", "Bearer " + otherTutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Hijacked", null)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));

    mockMvc
        .perform(
            get("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + otherTutorToken)
                .param("classId", String.valueOf(ownClass)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
  }

  @Test
  void rejectsUnauthenticatedWrongRoleMalformedAndMissingStudentRequests() throws Exception {
    mockMvc.perform(get("/api/learning/tutor/students")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/learning/tutor/student-accounts"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/tutor/student-accounts")
                .header("Authorization", "Bearer " + token("STUDENT", 201L)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + token("STUDENT", 201L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Blocked", null)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + token("TUTOR", 101L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_REQUEST"));
    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}", 999999L)
                .header("Authorization", "Bearer " + token("TUTOR", 101L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
  }

  @Test
  void searchableStudentDirectoryUsesOnlyAvailableStudentAccounts() throws Exception {
    String tutorToken = token("TUTOR", 101L);

    mockMvc
        .perform(
            get("/api/learning/tutor/student-accounts")
                .header("Authorization", "Bearer " + tutorToken)
                .param("search", "ada"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(9001))
        .andExpect(jsonPath("$[0].fullName").value("Ada Learner"));
    mockMvc
        .perform(
            get("/api/learning/tutor/student-accounts")
                .header("Authorization", "Bearer " + tutorToken)
                .param("search", "stable@example"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(9004));

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Browser supplied name", 9001L)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fullName").value("Ada Learner"));
    mockMvc
        .perform(
            get("/api/learning/tutor/student-accounts")
                .header("Authorization", "Bearer " + tutorToken)
                .param("search", "ada"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Attempt duplicate", 9001L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LOGIN_IDENTITY_CONFLICT"));
    mockMvc
        .perform(
            post("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(studentJson("Non-student", 9999L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
  }

  private long insertClass(long tutorId, String className, String normalizedClassName) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        className,
        normalizedClassName,
        "Science",
        "P5",
        "ACTIVE");
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
            Long.class,
            tutorId,
            normalizedClassName);
    if (id == null) {
      throw new IllegalStateException("Inserted class was not found");
    }
    return id;
  }

  private String studentJson(String fullName, Long loginUserId, long... classIds) {
    String ids =
        java.util.Arrays.stream(classIds)
            .mapToObj(Long::toString)
            .collect(java.util.stream.Collectors.joining(","));
    String login = loginUserId == null ? "null" : loginUserId.toString();
    return "{\"fullName\":\"%s\",\"loginUserId\":%s,\"classIds\":[%s]}"
        .formatted(fullName, login, ids);
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

@SpringBootTest
@AutoConfigureMockMvc
class StudentManagementDatabaseFailureIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private StudentService studentService;

  @Test
  void returnsStructuredDatabaseError() throws Exception {
    when(studentService.listOwnedStudents(101L, null))
        .thenThrow(
            new StudentService.StudentPersistenceException(
                new DataAccessResourceFailureException("database unavailable")));

    mockMvc
        .perform(
            get("/api/learning/tutor/students")
                .header("Authorization", "Bearer " + token("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STUDENT_DATABASE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Student data is temporarily unavailable"));
  }

  @Test
  void returnsStructuredStudentDirectoryError() throws Exception {
    when(studentService.listAvailableStudentAccounts(
            org.mockito.ArgumentMatchers.eq(101L), anyString(), any()))
        .thenThrow(
            new StudentService.StudentDirectoryUnavailableException(
                new IllegalStateException("auth service unavailable")));

    mockMvc
        .perform(
            get("/api/learning/tutor/student-accounts")
                .header("Authorization", "Bearer " + token("TUTOR", 101L)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STUDENT_DIRECTORY_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Student accounts are temporarily unavailable"));
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
