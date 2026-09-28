package com.fttranscendence.learning.question;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionDetailIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long TUTOR_ID = 101L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearQuestionBank() {
    jdbcTemplate.update("DELETE FROM worksheet_assignments");
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM worksheets");
    jdbcTemplate.update("DELETE FROM question_keywords");
    jdbcTemplate.update("DELETE FROM marking_components");
    jdbcTemplate.update("DELETE FROM questions");
  }

  @Test
  void returnsTheCompleteTutorQuestionDetailIncludingSyllabusAndMarkingMetadata() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long questionId = createQuestion("SCI-WATER-DETAIL-001", topicId, "ACTIVE");

    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(questionId))
        .andExpect(jsonPath("$.code").value("SCI-WATER-DETAIL-001"))
        .andExpect(jsonPath("$.syllabusTopic.id").value(topicId))
        .andExpect(jsonPath("$.syllabusTopic.code").value("SCI_P5_CYCLES_MATTER_WATER_WATER"))
        .andExpect(jsonPath("$.questionType").value("OPEN_ENDED"))
        .andExpect(jsonPath("$.prompt").value("Explain evaporation."))
        .andExpect(jsonPath("$.totalMarks").value(3.0))
        .andExpect(jsonPath("$.modelAnswer").value("Water gains energy and becomes vapour."))
        .andExpect(jsonPath("$.archiveState").value("ACTIVE"))
        .andExpect(jsonPath("$.markingComponents.length()").value(2))
        .andExpect(jsonPath("$.markingComponents[0].position").value(0))
        .andExpect(jsonPath("$.markingComponents[0].description").value("States energy gain"))
        .andExpect(jsonPath("$.markingComponents[1].marks").value(2.0))
        .andExpect(jsonPath("$.keywords[0]").value("evaporation"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void returnsArchivedQuestionsForTutorReviewButKeepsThemRoleProtected() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long questionId = createQuestion("SCI-WATER-DETAIL-ARCHIVED", topicId, "ARCHIVED");

    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", 202L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archiveState").value("ARCHIVED"));

    mockMvc
        .perform(get("/api/learning/tutor/questions/{questionId}", questionId))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("STUDENT", 700L)))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsStructuredNotFoundForMissingQuestion() throws Exception {
    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}", 999_999L)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Question was not found"));
  }

  private long createQuestion(String code, long topicId, String archiveState) throws Exception {
    String request =
        """
            {"code":"%s","syllabusTopicId":%d,"questionType":"OPEN_ENDED","prompt":"Explain evaporation.","totalMarks":3.00,"modelAnswer":"Water gains energy and becomes vapour.","archiveState":"%s","markingComponents":[{"description":"States energy gain","marks":1.00},{"description":"States water becomes vapour","marks":2.00}],"keywords":["Evaporation","energy"]}
            """
            .formatted(code, topicId, archiveState);
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated());
    return jdbcTemplate.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
  }

  private long topicId(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM syllabus_topics WHERE code = ?", Long.class, code);
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
