package com.fttranscendence.learning.question;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionListIntegrationTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long TUTOR_ID = 101L;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearQuestions() {
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM question_keywords");
    jdbcTemplate.update("DELETE FROM marking_components");
    jdbcTemplate.update("DELETE FROM questions");
  }

  @Test
  void listsActiveQuestionsWithStablePaginationAndASelectionSafeShallowPayload() throws Exception {
    long water = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    insertQuestion("SCI-Q-020", water, "SUBTOPIC", "OPEN_ENDED", "ACTIVE", "Explain evaporation.");
    insertQuestion(
        "SCI-Q-010",
        water,
        "SUBTOPIC",
        "MULTIPLE_CHOICE",
        "ACTIVE",
        "Choose the correct change of state.");
    insertQuestion(
        "SCI-Q-030", water, "SUBTOPIC", "SHORT_ANSWER", "ARCHIVED", "Archived question.");

    mockMvc
        .perform(
            get("/api/learning/tutor/questions?page=0&size=1")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("SCI-Q-010"))
        .andExpect(jsonPath("$.items[0].syllabusTopic.id").value(water))
        .andExpect(jsonPath("$.items[0].syllabusTopic.nodeType").value("SUBTOPIC"))
        .andExpect(jsonPath("$.items[0].totalMarks").value(2.00))
        .andExpect(jsonPath("$.items[0].modelAnswer").doesNotExist())
        .andExpect(jsonPath("$.items[0].markingComponents").doesNotExist())
        .andExpect(jsonPath("$.items[0].keywords").doesNotExist())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.hasNext").value(true));

    mockMvc
        .perform(
            get("/api/learning/tutor/questions?page=1&size=1")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].code").value("SCI-Q-020"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void combinesTopicTypeAndArchiveFiltersAndReturnsAnEmptyPagePastTheEnd() throws Exception {
    long water = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long energy = topicId("SCI_P6_ENERGY_CONVERSION");
    insertQuestion(
        "SCI-WATER-MCQ", water, "SUBTOPIC", "MULTIPLE_CHOICE", "ACTIVE", "Water choice.");
    insertQuestion(
        "SCI-WATER-OEQ", water, "SUBTOPIC", "OPEN_ENDED", "ACTIVE", "Water explanation.");
    insertQuestion(
        "SCI-ENERGY-MCQ", energy, "TOPIC", "MULTIPLE_CHOICE", "ACTIVE", "Energy choice.");
    insertQuestion(
        "SCI-WATER-ARCH",
        water,
        "SUBTOPIC",
        "MULTIPLE_CHOICE",
        "ARCHIVED",
        "Archived water choice.");

    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/questions?topicId={topicId}&questionType=MULTIPLE_CHOICE&archiveState=ARCHIVED&page=0&size=25",
                    water)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("SCI-WATER-ARCH"))
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/questions?topicId={topicId}&questionType=MULTIPLE_CHOICE&page=4&size=1",
                    water)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.page").value(4))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void searchesCodePromptAndKeywordsCaseAndAccentInsensitivelyAlongsideFilters() throws Exception {
    long water = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    long energy = topicId("SCI_P6_ENERGY_CONVERSION");
    long codeMatch =
        insertQuestion(
            "SCI-CAFÉ-001",
            water,
            "SUBTOPIC",
            "MULTIPLE_CHOICE",
            "ACTIVE",
            "Choose the correct state change.");
    insertQuestion(
        "SCI-PROMPT-001",
        water,
        "SUBTOPIC",
        "OPEN_ENDED",
        "ACTIVE",
        "Explain condensación in a sealed container.");
    long keywordMatch =
        insertQuestion(
            "SCI-KEYWORD-001", energy, "TOPIC", "OPEN_ENDED", "ACTIVE", "Describe the process.");
    jdbcTemplate.update(
        "INSERT INTO question_keywords (question_id, position, keyword) VALUES (?, ?, ?)",
        keywordMatch,
        0,
        "évaporation");
    insertQuestion(
        "SCI-CAFÉ-ARCHIVED", water, "SUBTOPIC", "MULTIPLE_CHOICE", "ARCHIVED", "Archived match.");

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "CAFE")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(codeMatch));

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "condensacion")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("SCI-PROMPT-001"));

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "EVAPORATION")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(keywordMatch));

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "cafe")
                .param("topicId", String.valueOf(water))
                .param("questionType", "MULTIPLE_CHOICE")
                .param("archiveState", "ACTIVE")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(codeMatch));
  }

  @Test
  void treatsSearchWildcardsLiterallyAndRejectsOversizedSearches() throws Exception {
    long water = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    insertQuestion(
        "SCI_100%_MATCH", water, "SUBTOPIC", "OPEN_ENDED", "ACTIVE", "Exact literal code.");
    insertQuestion(
        "SCI-100-OTHER",
        water,
        "SUBTOPIC",
        "OPEN_ENDED",
        "ACTIVE",
        "Should not be a wildcard match.");

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "SCI_100%")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("SCI_100%_MATCH"));

    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("search", "x".repeat(121))
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void rejectsInvalidQueriesAndEnforcesTutorRole() throws Exception {
    mockMvc.perform(get("/api/learning/tutor/questions")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/learning/tutor/questions").header("Authorization", bearer("STUDENT", 9001L)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/learning/tutor/questions?topicId=0")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mockMvc
        .perform(
            get("/api/learning/tutor/questions?page=-1&size=101")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mockMvc
        .perform(
            get("/api/learning/tutor/questions?questionType=NOT_A_TYPE")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_QUERY"));
    mockMvc
        .perform(
            get("/api/learning/tutor/questions?archiveState=REMOVED")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_QUERY"));
  }

  private long topicId(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM syllabus_topics WHERE code = ?", Long.class, code);
  }

  private long insertQuestion(
      String code,
      long topicId,
      String nodeType,
      String questionType,
      String state,
      String prompt) {
    jdbcTemplate.update(
        "INSERT INTO questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        code,
        topicId,
        nodeType,
        questionType,
        prompt,
        new BigDecimal("2.00"),
        "A complete model answer.",
        state);
    return jdbcTemplate.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
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

@SpringBootTest
@AutoConfigureMockMvc
class QuestionListDatabaseFailureIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long TUTOR_ID = 101L;
  @Autowired private MockMvc mockMvc;
  @MockitoBean private QuestionService questionService;

  @Test
  void returnsARecoverableStructuredDatabaseError() throws Exception {
    when(questionService.list(any()))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    mockMvc
        .perform(
            get("/api/learning/tutor/questions").header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("QUESTION_DATABASE_UNAVAILABLE"));
  }

  @Test
  void returnsARecoverableStructuredDatabaseErrorForQuestionMutations() throws Exception {
    when(questionService.create(any()))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"code":"SCI-DB-001","syllabusTopicId":1,"questionType":"OPEN_ENDED","prompt":"Prompt","totalMarks":1.00,"modelAnswer":"Answer","archiveState":"ACTIVE","markingComponents":[{"description":"Criterion","marks":1.00}],"keywords":[]}
                    """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("QUESTION_DATABASE_UNAVAILABLE"));
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
