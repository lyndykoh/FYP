package com.fttranscendence.learning.mastery;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasteryControllerTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_ID = 101L;
  private static final long STUDENT_LOGIN_ID = 9001L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearData() {
    jdbcTemplate.update("DELETE FROM mastery_history");
    jdbcTemplate.update("DELETE FROM mastery_records");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
  }

  @Test
  void returnsEveryActiveSyllabusNodeAndMergesApprovedMasteryForTheOwningTutor() throws Exception {
    long studentId = insertStudent(OWNER_ID, STUDENT_LOGIN_ID, "Ada Learner");
    long topicId = activeTopicId();
    long recordId = insertMastery(studentId, topicId, 86, "MASTERED", 2);
    jdbcTemplate.update(
        "INSERT INTO mastery_history (mastery_record_id, previous_score, new_score, previous_status, new_status, source_submission_id, reason) VALUES (?, ?, ?, ?, ?, ?, ?)",
        recordId,
        72,
        86,
        "IMPROVING",
        "MASTERED",
        7001L,
        "Approved result");

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/mastery-map", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(studentId))
        .andExpect(jsonPath("$.overallScore").value(86.00))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].status").value(hasItem("MASTERED")))
        .andExpect(jsonPath("$.nodes[?(@.topicId == " + topicId + ")].score").value(hasItem(86.00)))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId != " + topicId + ")].status")
                .value(hasItem("NOT_STARTED")));

    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/students/{studentId}/mastery-map/topics/{topicId}",
                    studentId,
                    topicId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.node.topicId").value(topicId))
        .andExpect(jsonPath("$.history[0].reason").value("Approved result"))
        .andExpect(jsonPath("$.history[0].previousScore").value(72.00));
  }

  @Test
  void returnsNotStartedTopicAndNoOverallScoreForNewLinkedStudent() throws Exception {
    insertStudent(OWNER_ID, STUDENT_LOGIN_ID, "New Learner");
    long topicId = activeTopicId();

    mockMvc
        .perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", "Bearer " + token("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overallScore").doesNotExist())
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].status")
                .value(hasItem("NOT_STARTED")))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].attemptCount").value(hasItem(0)));

    mockMvc
        .perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", topicId)
                .header("Authorization", "Bearer " + token("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.node.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.history").isEmpty());
  }

  @Test
  void exposesEveryPersistedMasteryStatusAlongsideTheSyllabusHierarchy() throws Exception {
    long studentId = insertStudent(OWNER_ID, STUDENT_LOGIN_ID, "Status Learner");
    List<Long> topicIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT 5", Long.class);
    String[] statuses = {"LEARNING", "PRACTISING", "IMPROVING", "MASTERED", "NEEDS_REVISION"};
    int[] scores = {25, 60, 76, 90, 0};
    for (int index = 0; index < statuses.length; index++) {
      insertMastery(studentId, topicIds.get(index), scores[index], statuses[index], 1);
    }

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/mastery-map", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.nodes[*].status")
                .value(
                    hasItems(
                        "NOT_STARTED",
                        "LEARNING",
                        "PRACTISING",
                        "IMPROVING",
                        "MASTERED",
                        "NEEDS_REVISION")))
        .andExpect(jsonPath("$.nodes[0].nodeType").exists())
        .andExpect(jsonPath("$.nodes[0].depth").exists());
  }

  @Test
  void excludesInactiveTaxonomyRecordsFromBothNodesAndOverallScore() throws Exception {
    long studentId = insertStudent(OWNER_ID, STUDENT_LOGIN_ID, "Current Syllabus Learner");
    List<Long> topicIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT 2", Long.class);
    insertMastery(studentId, topicIds.get(0), 80, "IMPROVING", 1);
    insertMastery(studentId, topicIds.get(1), 20, "LEARNING", 1);
    jdbcTemplate.update("UPDATE syllabus_topics SET active = false WHERE id = ?", topicIds.get(1));

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/mastery-map", studentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overallScore").value(80.00))
        .andExpect(jsonPath("$.nodes[?(@.topicId == " + topicIds.get(1) + ")]").isEmpty());
  }

  @Test
  void hidesForeignAndMissingMasteryAndEnforcesRoleBoundary() throws Exception {
    long foreignStudentId = insertStudent(202L, 9002L, "Other Learner");

    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/mastery-map", foreignStudentId)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MASTERY_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/learning/tutor/students/{studentId}/mastery-map", 999999L)
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", "Bearer " + token("TUTOR", OWNER_ID)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/learning/tutor/students/{studentId}/mastery-map", foreignStudentId))
        .andExpect(status().isUnauthorized());
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

  private long activeTopicId() {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT 1", Long.class);
  }

  private long insertMastery(long studentId, long topicId, int score, String status, int attempts) {
    jdbcTemplate.update(
        "INSERT INTO mastery_records (student_profile_id, syllabus_topic_id, score, mastery_status, attempt_count, calculated_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        studentId,
        topicId,
        score,
        status,
        attempts);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM mastery_records WHERE student_profile_id = ? AND syllabus_topic_id = ?",
        Long.class,
        studentId,
        topicId);
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
