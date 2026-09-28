package com.fttranscendence.learning.mastery;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the student progress read API from the approved-marking pipeline, rather than inserting
 * derived mastery rows as fixtures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentProgressIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long TUTOR_ID = 101L;
  private static final long STUDENT_LOGIN_ID = 9001L;

  @Autowired MockMvc mvc;
  @Autowired MasteryService mastery;
  @Autowired StudentProfileRepository students;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;

  @BeforeEach
  void clearLearningFacts() {
    jdbc.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbc.update("DELETE FROM mastery_diagnostic_evidence");
    jdbc.update("DELETE FROM mastery_history");
    jdbc.update("DELETE FROM mastery_records");
    jdbc.update("DELETE FROM mastery_approved_results");
    jdbc.update("DELETE FROM class_memberships");
    jdbc.update("DELETE FROM student_profiles");
  }

  @Test
  void exposesOnlyTheLinkedStudentsApprovedResultsWithCanonicalMetricsAndHistory()
      throws Exception {
    StudentProfile learner = student(TUTOR_ID, STUDENT_LOGIN_ID, "Ari Learner");
    StudentProfile otherLearner = student(202L, 9002L, "Other Learner");
    List<Long> topics = activeTopics(6);

    // These records must remain pipeline-derived: the response is a view of
    // approved results, not a page-specific mastery fixture.
    approve(8101L, learner.getId(), topics.get(0), "1.00", 0); // LEARNING
    approve(8102L, learner.getId(), topics.get(1), "5.00", 1); // PRACTISING
    approve(8103L, learner.getId(), topics.get(2), "7.00", 2); // IMPROVING
    approve(8104L, learner.getId(), topics.get(3), "8.50", 3); // MASTERED
    approve(8105L, learner.getId(), topics.get(4), "0.00", 4); // NEEDS_REVISION
    approve(8201L, 202L, otherLearner.getId(), topics.get(0), "10.00", 5);
    entityManager.flush();
    entityManager.clear();

    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(learner.getId()))
        .andExpect(jsonPath("$.overallScore").value(43.00))
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
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topics.get(0) + ")].score").value(hasItem(10.00)))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topics.get(0) + ")].attemptCount")
                .value(hasItem(1)))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topics.get(3) + ")].status")
                .value(hasItem("MASTERED")));

    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", topics.get(3))
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(learner.getId()))
        .andExpect(jsonPath("$.node.topicId").value(topics.get(3)))
        .andExpect(jsonPath("$.node.score").value(85.00))
        .andExpect(jsonPath("$.node.status").value("MASTERED"))
        .andExpect(jsonPath("$.history[0].previousScore").value(0.00))
        .andExpect(jsonPath("$.history[0].newScore").value(85.00))
        .andExpect(
            jsonPath("$.history[0].reason")
                .value("Tutor-approved result: 85.00% attempt evidence"));
  }

  @Test
  void givesANewLinkedStudentCanonicalNotStartedTopicsWithoutInventingProgress() throws Exception {
    StudentProfile learner = student(TUTOR_ID, STUDENT_LOGIN_ID, "New Learner");
    long topicId = activeTopics(1).get(0);

    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentId").value(learner.getId()))
        .andExpect(jsonPath("$.overallScore").doesNotExist())
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].status")
                .value(hasItem("NOT_STARTED")))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].attemptCount").value(hasItem(0)));

    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", topicId)
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.node.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.history").isEmpty());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesApprovedHistoryWhenTheHttpRequestHasNoOpenPersistenceContext() throws Exception {
    StudentProfile learner = student(TUTOR_ID, STUDENT_LOGIN_ID, "Detached History Learner");
    long topicId = activeTopics(1).get(0);
    approve(8241L, learner.getId(), topicId, "8.00", 0);

    // This test deliberately opts out of the class transaction. It catches
    // lazy collection access in the MVC controller when OSIV is disabled.
    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", topicId)
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.node.status").value("IMPROVING"))
        .andExpect(jsonPath("$.history[0].previousScore").value(0.00))
        .andExpect(jsonPath("$.history[0].newScore").value(80.00))
        .andExpect(
            jsonPath("$.history[0].reason")
                .value("Tutor-approved result: 80.00% attempt evidence"));
  }

  @Test
  void excludesRetractedMarkingFromEveryStudentProgressMetricAndHistory() throws Exception {
    StudentProfile learner = student(TUTOR_ID, STUDENT_LOGIN_ID, "Retracted Learner");
    long topicId = activeTopics(1).get(0);
    approve(8251L, learner.getId(), topicId, "10.00", 0);
    retract(8251L, learner.getId(), topicId, 1);
    entityManager.flush();
    entityManager.clear();

    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overallScore").doesNotExist())
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].status")
                .value(hasItem("NOT_STARTED")))
        .andExpect(
            jsonPath("$.nodes[?(@.topicId == " + topicId + ")].attemptCount").value(hasItem(0)));

    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", topicId)
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.node.status").value("NOT_STARTED"))
        .andExpect(jsonPath("$.node.attemptCount").value(0))
        .andExpect(jsonPath("$.history").isEmpty());
  }

  @Test
  void hidesMissingOrInactiveTopicsAndEnforcesTheStudentRoleBoundary() throws Exception {
    StudentProfile learner = student(TUTOR_ID, STUDENT_LOGIN_ID, "Ari Learner");
    long inactiveTopic = activeTopics(1).get(0);
    approve(8301L, learner.getId(), inactiveTopic, "10.00", 0);
    entityManager.flush();
    entityManager.clear();
    jdbc.update("UPDATE syllabus_topics SET active = false WHERE id = ?", inactiveTopic);

    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", 999999L)
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MASTERY_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/student/mastery-map/topics/{topicId}", inactiveTopic)
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MASTERY_NOT_FOUND"));
    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("STUDENT", STUDENT_LOGIN_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nodes[?(@.topicId == " + inactiveTopic + ")]").isEmpty())
        .andExpect(jsonPath("$.overallScore").doesNotExist());
    mvc.perform(
            get("/api/learning/student/mastery-map")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/learning/student/mastery-map")).andExpect(status().isUnauthorized());
  }

  private void approve(
      long submissionId, long studentId, long topicId, String marks, int minuteOffset) {
    approve(submissionId, TUTOR_ID, studentId, topicId, marks, minuteOffset);
  }

  private void approve(
      long submissionId,
      long tutorId,
      long studentId,
      long topicId,
      String marks,
      int minuteOffset) {
    mastery.applyApprovedMarking(
        new MasteryService.ApprovedMarking(
            submissionId,
            tutorId,
            submissionId * 10,
            submissionId * 100,
            studentId,
            topicId,
            new BigDecimal(marks),
            new BigDecimal("10.00"),
            1,
            MasteryService.State.APPROVED,
            LocalDateTime.of(2026, 8, 29, 9, 0).plusMinutes(minuteOffset),
            List.of()));
  }

  private void retract(long submissionId, long studentId, long topicId, int minuteOffset) {
    mastery.applyApprovedMarking(
        new MasteryService.ApprovedMarking(
            submissionId,
            TUTOR_ID,
            submissionId * 10,
            submissionId * 100,
            studentId,
            topicId,
            new BigDecimal("10.00"),
            new BigDecimal("10.00"),
            2,
            MasteryService.State.RETRACTED,
            LocalDateTime.of(2026, 8, 29, 9, 0).plusMinutes(minuteOffset),
            List.of()));
  }

  private StudentProfile student(long tutorId, long loginUserId, String name) {
    StudentProfile student = new StudentProfile();
    student.setTutorId(tutorId);
    student.setLoginUserId(loginUserId);
    student.setFullName(name);
    return students.save(student);
  }

  private List<Long> activeTopics(int count) {
    return jdbc.queryForList(
        "SELECT id FROM syllabus_topics WHERE active = true ORDER BY id LIMIT ?",
        Long.class,
        count);
  }

  private String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("student@example.com")
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
