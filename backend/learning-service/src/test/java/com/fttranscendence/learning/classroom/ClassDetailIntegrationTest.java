package com.fttranscendence.learning.classroom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ClassDetailIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 101L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearData() {
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
  void returnsFullDetailFromOwnedAuthoritativeRecords() throws Exception {
    long classId = insertClass(OWNER_ID, "P5 Science", "p5 science");
    jdbcTemplate.update(
        "INSERT INTO class_schedules (class_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
        classId,
        "MONDAY",
        "15:00",
        "16:30");
    long firstStudent = insertStudent(OWNER_ID, "Amelia Tan");
    long secondStudent = insertStudent(OWNER_ID, "Ben Lim");
    long otherClassId = insertClass(OWNER_ID, "P5 Science extension", "p5 science extension");
    long otherClassStudent = insertStudent(OWNER_ID, "Other Class Learner");
    insertMembership(firstStudent, classId, OWNER_ID);
    insertMembership(secondStudent, classId, OWNER_ID);
    insertMembership(otherClassStudent, otherClassId, OWNER_ID);
    long waterTopic = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long plantTopic = topicId("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION");
    insertMastery(firstStudent, waterTopic, 40);
    insertMastery(secondStudent, waterTopic, 80);
    insertMastery(firstStudent, plantTopic, 60);
    insertWorksheet(
        classId, OWNER_ID, "P5-WATER-001", "Water revision", LocalDateTime.of(2026, 9, 15, 17, 0));

    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", classId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.className").value("P5 Science"))
        .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("MONDAY"))
        .andExpect(jsonPath("$.students.length()").value(2))
        .andExpect(jsonPath("$.students[0].fullName").value("Amelia Tan"))
        .andExpect(jsonPath("$.students[?(@.fullName == 'Other Class Learner')]").isEmpty())
        .andExpect(jsonPath("$.students[0].overallMastery").value(50.00))
        .andExpect(jsonPath("$.mastery.recordCount").value(3))
        .andExpect(jsonPath("$.mastery.studentsWithMastery").value(2))
        .andExpect(jsonPath("$.mastery.averageScore").value(60.00))
        .andExpect(jsonPath("$.weakAreas.length()").value(2))
        .andExpect(jsonPath("$.weakAreas[0].topicName").value("Reproduction"))
        .andExpect(jsonPath("$.insight.status").value("REFRESHING"))
        .andExpect(jsonPath("$.worksheets[0].title").value("Water revision"))
        .andExpect(jsonPath("$.worksheets[0].dueAt").value("2026-09-15T17:00:00"));
  }

  @Test
  void returnsPartialAndEmptyDetailsWithoutInventingMasteryOrInsights() throws Exception {
    long partialClassId = insertClass(OWNER_ID, "P5 Science partial", "p5 science partial");
    long studentId = insertStudent(OWNER_ID, "No Records");
    insertMembership(studentId, partialClassId, OWNER_ID);

    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", partialClassId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students[0].overallMastery").doesNotExist())
        .andExpect(jsonPath("$.students[0].masteryRecordCount").value(0))
        .andExpect(jsonPath("$.mastery.averageScore").doesNotExist())
        .andExpect(jsonPath("$.weakAreas").isEmpty())
        .andExpect(jsonPath("$.worksheets").isEmpty());

    long emptyClassId = insertClass(OWNER_ID, "P5 Science empty", "p5 science empty");
    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", emptyClassId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.students").isEmpty())
        .andExpect(jsonPath("$.mastery.recordCount").value(0))
        .andExpect(jsonPath("$.insight.message").value("Insights are being refreshed"));
  }

  @Test
  void distinguishesMissingAndForeignClassesAndRequiresTutorAuthentication() throws Exception {
    long foreignClassId = insertClass(202L, "Other Tutor", "other tutor");
    String ownerToken = token("TUTOR", OWNER_ID);

    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", 999999L)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", foreignClassId)
                .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CLASS_ACCESS_FORBIDDEN"));
    mockMvc
        .perform(get("/api/learning/tutor/classes/{classId}", foreignClassId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/tutor/classes/{classId}", foreignClassId)
                .header("Authorization", "Bearer " + token("STUDENT", 301L)))
        .andExpect(status().isForbidden());
  }

  private long insertClass(long tutorId, String className, String normalizedName) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes (tutor_id, class_name, normalized_class_name, subject, class_level, status) VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        className,
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

  private long insertStudent(long tutorId, String name) {
    jdbcTemplate.update(
        "INSERT INTO student_profiles (tutor_id, full_name) VALUES (?, ?)", tutorId, name);
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

  private void insertMastery(long studentId, long topicId, int score) {
    jdbcTemplate.update(
        "INSERT INTO mastery_records (student_profile_id, syllabus_topic_id, score, mastery_status, attempt_count) VALUES (?, ?, ?, ?, ?)",
        studentId,
        topicId,
        score,
        "PRACTISING",
        1);
  }

  private void insertWorksheet(
      long classId, long tutorId, String code, String title, LocalDateTime dueAt) {
    jdbcTemplate.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status, approved_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        tutorId,
        code,
        title,
        "CLASS",
        "APPROVED");
    Long worksheetId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM worksheets WHERE tutor_id = ? AND code = ?", Long.class, tutorId, code);
    jdbcTemplate.update(
        "INSERT INTO worksheet_assignments (worksheet_id, tutor_id, assignment_type, target_id, class_id, assigned_at, due_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        worksheetId,
        tutorId,
        "CLASS",
        classId,
        classId,
        dueAt.minusDays(7),
        dueAt);
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
