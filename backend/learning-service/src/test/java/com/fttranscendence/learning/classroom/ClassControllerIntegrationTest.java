package com.fttranscendence.learning.classroom;

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
class ClassControllerIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearClasses() {
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void tutorCanListCreateAndUpdateOnlyTheirClasses() throws Exception {
    String tutorToken = token("TUTOR", 101L, 600);
    String body = classJson("P5 Science", "Science", "P5");

    mockMvc
        .perform(get("/api/learning/tutor/classes").header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    String response =
        mockMvc
            .perform(
                post("/api/learning/tutor/classes")
                    .header("Authorization", "Bearer " + tutorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tutorId").value(101))
            .andExpect(jsonPath("$.className").value("P5 Science"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("MONDAY"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Number createdId = JsonPath.read(response, "$.id");
    long classId = createdId.longValue();

    mockMvc
        .perform(
            put("/api/learning/tutor/classes/{id}", classId)
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("P5 Advanced Science", "Science", "P5")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.className").value("P5 Advanced Science"));

    mockMvc
        .perform(get("/api/learning/tutor/classes").header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].className").value("P5 Advanced Science"));

    mockMvc
        .perform(
            get("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + token("TUTOR", 202L, 600)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());

    mockMvc
        .perform(
            put("/api/learning/tutor/classes/{id}", classId)
                .header("Authorization", "Bearer " + token("TUTOR", 202L, 600))
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("Hijacked", "Science", "P5")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
  }

  @Test
  void rejectsUnauthenticatedAndWrongRoleRequests() throws Exception {
    mockMvc.perform(get("/api/learning/tutor/classes")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + token("STUDENT", 201L, 600))
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("Blocked", "Science", "P5")))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsStructuredValidationDuplicateMissingAndInvalidScheduleErrors() throws Exception {
    String tutorToken = token("TUTOR", 101L, 600);

    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"className\":\" \",\"subject\":\"\",\"level\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.className").exists());

    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("P5 Science", "Science", "P5")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("  p5 SCIENCE  ", "Science", "P5")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CLASS_ALREADY_EXISTS"));

    mockMvc
        .perform(
            put("/api/learning/tutor/classes/{id}", 999999L)
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("Missing", "Science", "P5")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/learning/tutor/classes/{id}", -1L)
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(classJson("Invalid id", "Science", "P5")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.classId").exists());

    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CLASS_REQUEST"));

    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"className":"Bad schedule","subject":"Science","level":"P5",
                     "schedules":[{"dayOfWeek":"MONDAY","startTime":"16:00","endTime":"15:00"}]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CLASS_REQUEST"));
  }

  @Test
  void supportsInactiveStatusAndRejectsDuplicateSchedules() throws Exception {
    String tutorToken = token("TUTOR", 101L, 600);
    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"className":"Inactive","subject":"Science","level":"P6","status":"INACTIVE",
                     "schedules":[]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("INACTIVE"));

    mockMvc
        .perform(
            post("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + tutorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"className":"Duplicate schedule","subject":"Science","level":"P6",
                     "schedules":[
                       {"dayOfWeek":"TUESDAY","startTime":"15:00","endTime":"16:00"},
                       {"dayOfWeek":"TUESDAY","startTime":"15:00","endTime":"16:00"}
                     ]}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CLASS_REQUEST"));
  }

  private String classJson(String name, String subject, String level) {
    return """
            {
              "className": "%s",
              "subject": "%s",
              "level": "%s",
              "schedules": [
                {"dayOfWeek":"MONDAY","startTime":"15:00","endTime":"16:30"}
              ]
            }
            """
        .formatted(name, subject, level);
  }

  private String token(String role, long userId, long lifetimeSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("person@example.com")
        .claim("role", role)
        .claim("userId", userId)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(lifetimeSeconds)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}

@SpringBootTest
@AutoConfigureMockMvc
class ClassControllerDatabaseFailureIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ClassService classService;

  @Test
  void returnsStructuredDatabaseError() throws Exception {
    when(classService.listOwnedClasses(101L))
        .thenThrow(
            new ClassService.ClassPersistenceException(
                new DataAccessResourceFailureException("database unavailable")));

    mockMvc
        .perform(
            get("/api/learning/tutor/classes")
                .header("Authorization", "Bearer " + token("TUTOR", 101L, 600)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CLASS_DATABASE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Class data is temporarily unavailable"));
  }

  private String token(String role, long userId, long lifetimeSeconds) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject("person@example.com")
        .claim("role", role)
        .claim("userId", userId)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(lifetimeSeconds)))
        .signWith(
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
        .compact();
  }
}
