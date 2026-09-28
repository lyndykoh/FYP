package com.fttranscendence.learning.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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
class P6ScienceQuestionBankSeedIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";

  @Autowired private P6ScienceQuestionBankSeed seed;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;

  @Test
  void seedsAllP6ScienceLeavesRepeatablyWithValidTaxonomyReferences() throws Exception {
    seed.seed();
    int firstCount = countSeededQuestions();
    seed.seed();

    assertEquals(18, firstCount);
    assertEquals(firstCount, countSeededQuestions());
    assertEquals(
        6,
        jdbc.queryForObject(
            """
            select count(distinct q.syllabus_topic_id)
            from questions q
            join syllabus_topics t on t.id = q.syllabus_topic_id and t.node_type = q.syllabus_topic_type
            where q.code like 'P6SCI-%' and t.active = true
            """,
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            """
            select count(*) from questions q left join syllabus_topics t
            on t.id = q.syllabus_topic_id and t.node_type = q.syllabus_topic_type
            where q.code like 'P6SCI-%' and t.id is null
            """,
            Integer.class));
    assertEquals(
        3,
        jdbc.queryForObject(
            "select count(distinct difficulty) from questions where code like 'P6SCI-%'",
            Integer.class));

    long photosynthesis = topicId("SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS");
    mockMvc
        .perform(
            get("/api/learning/tutor/questions")
                .param("topicId", String.valueOf(photosynthesis))
                .param("questionType", "OPEN_ENDED")
                .param("difficulty", "CHALLENGE")
                .header("Authorization", bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].code").value("P6SCI-PHOTO-003"))
        .andExpect(jsonPath("$.items[0].difficulty").value("CHALLENGE"));

    assertTrue(
        jdbc.queryForObject(
            """
            select count(*) >= 1 from questions q
            join syllabus_topics t on t.id = q.syllabus_topic_id
            where t.code = 'SCI_P6_INTERACTIONS_FORCES_FRICTIONAL'
              and q.question_type = 'MULTIPLE_CHOICE' and q.difficulty = 'FOUNDATION'
            """,
            Boolean.class));
  }

  private int countSeededQuestions() {
    return jdbc.queryForObject(
        "select count(*) from questions where code like 'P6SCI-%'", Integer.class);
  }

  private long topicId(String code) {
    return jdbc.queryForObject("select id from syllabus_topics where code = ?", Long.class, code);
  }

  private String bearer() {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("tutor@example.test")
            .claim("role", "TUTOR")
            .claim("userId", 101L)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
  }
}
