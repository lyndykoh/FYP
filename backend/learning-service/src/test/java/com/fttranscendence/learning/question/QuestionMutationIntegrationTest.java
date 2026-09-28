package com.fttranscendence.learning.question;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionMutationIntegrationTest {

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
  void createsReadsAndReplacesAllTutorOnlyQuestionMetadata() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    String create =
        request(
            " sci-water-901 ",
            topicId,
            "OPEN_ENDED",
            "Explain evaporation.",
            "3.00",
            "Water changes state after gaining energy.",
            "ACTIVE",
            "States the change",
            "1.00",
            "Explains energy",
            "2.00",
            "Heat",
            "Evaporation");

    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(create))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SCI-WATER-901"))
        .andExpect(jsonPath("$.syllabusTopic.id").value(topicId))
        .andExpect(jsonPath("$.modelAnswer").value("Water changes state after gaining energy."))
        .andExpect(jsonPath("$.markingComponents.length()").value(2))
        .andExpect(jsonPath("$.markingComponents[1].position").value(1))
        .andExpect(jsonPath("$.keywords[0]").value("heat"));

    long questionId = questionId("SCI-WATER-901");
    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", 202L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.modelAnswer").value("Water changes state after gaining energy."));

    String replacement =
        request(
            "SCI-WATER-901",
            topicId,
            "SHORT_ANSWER",
            "Name the state change.",
            "2.00",
            "Evaporation.",
            "ACTIVE",
            "Names evaporation",
            "2.00",
            null,
            null,
            "phase change");
    mockMvc
        .perform(
            put("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", 202L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(replacement))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.questionType").value("SHORT_ANSWER"))
        .andExpect(jsonPath("$.markingComponents.length()").value(1))
        .andExpect(jsonPath("$.keywords[0]").value("phase change"));
  }

  @Test
  void rejectsInvalidTaxonomyMarksAndDuplicateCode() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    String valid =
        request(
            "SCI-WATER-902",
            topicId,
            "OPEN_ENDED",
            "Explain condensation.",
            "2.00",
            "Cooling forms liquid water.",
            "ACTIVE",
            "States cooling",
            "1.00",
            "States liquid",
            "1.00",
            "cooling");
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid.replace("SCI-WATER-902", " sci-water-902 ")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUESTION_CODE_EXISTS"));

    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "SCI-BAD-MARKS",
                        topicId,
                        "OPEN_ENDED",
                        "Prompt",
                        "3.00",
                        "Answer",
                        "ACTIVE",
                        "Only two",
                        "2.00",
                        null,
                        null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_REQUEST"))
        .andExpect(jsonPath("$.fields.markingComponents").exists());

    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "SCI-BAD-TOPIC",
                        999_999L,
                        "OPEN_ENDED",
                        "Prompt",
                        "1.00",
                        "Answer",
                        "ACTIVE",
                        "Valid criterion",
                        "1.00",
                        null,
                        null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_REQUEST"))
        .andExpect(jsonPath("$.fields.syllabusTopicId").exists());
  }

  @Test
  void protectsQuestionsUsedByWorksheetsWhilePermittingArchiveOnlyUpdates() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    String original =
        request(
            "SCI-WATER-903",
            topicId,
            "OPEN_ENDED",
            "Explain melting.",
            "1.00",
            "Solid to liquid.",
            "ACTIVE",
            "States melting",
            "1.00",
            null,
            null);
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(original))
        .andExpect(status().isCreated());
    long questionId = questionId("SCI-WATER-903");
    jdbcTemplate.update(
        "INSERT INTO worksheets (tutor_id, code, title, audience_type, status) VALUES (101, 'WS-903', 'Worksheet', 'CLASS', 'DRAFT')");
    long worksheetId =
        jdbcTemplate.queryForObject("SELECT id FROM worksheets WHERE code = 'WS-903'", Long.class);
    jdbcTemplate.update(
        "INSERT INTO worksheet_questions (worksheet_id, question_id, position) VALUES (?, ?, 0)",
        worksheetId,
        questionId);

    mockMvc
        .perform(
            put("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(original.replace("Explain melting.", "Explain freezing.")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));
    mockMvc
        .perform(
            put("/api/learning/tutor/questions/{questionId}", questionId)
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(original.replace("\"ACTIVE\"", "\"ARCHIVED\"")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archiveState").value("ARCHIVED"));
  }

  @Test
  void storesValidPngImagesPrivatelyAndRejectsOtherContent() throws Exception {
    long topicId = topicId("SCI_P5_CYCLES_MATTER_WATER_WATER");
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    request(
                        "SCI-WATER-IMAGE",
                        topicId,
                        "DIAGRAM",
                        "Name this diagram.",
                        "1.00",
                        "Water cycle.",
                        "ACTIVE",
                        "Names the cycle",
                        "1.00",
                        null,
                        null)))
        .andExpect(status().isCreated());
    long questionId = questionId("SCI-WATER-IMAGE");
    MockMultipartFile png =
        new MockMultipartFile("file", "water-cycle.png", "image/png", onePixelPng());
    String uploaded =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/questions/{questionId}/images", questionId)
                    .file(png)
                    .header("Authorization", bearer("TUTOR", TUTOR_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentType").value("image/png"))
            .andExpect(jsonPath("$.width").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long imageId = Long.parseLong(uploaded.replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));
    mockMvc
        .perform(
            get("/api/learning/tutor/questions/{questionId}/images/{imageId}", questionId, imageId)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType("image/png"));
    mockMvc
        .perform(
            delete(
                    "/api/learning/tutor/questions/{questionId}/images/{imageId}",
                    questionId,
                    imageId)
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            multipart("/api/learning/tutor/questions/{questionId}/images", questionId)
                .file(
                    new MockMultipartFile(
                        "file",
                        "not-an-image.png",
                        "image/png",
                        "not a png".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_IMAGE"));
  }

  @Test
  void rejectsMissingMalformedAndUnauthorisedRequests() throws Exception {
    mockMvc
        .perform(
            get("/api/learning/tutor/questions/99999")
                .header("Authorization", bearer("TUTOR", TUTOR_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    mockMvc.perform(get("/api/learning/tutor/questions/1")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/learning/tutor/questions/1")
                .header("Authorization", bearer("STUDENT", 700L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/learning/tutor/questions")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("STUDENT", 700L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionType\":\"NOT_A_TYPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_QUESTION_REQUEST"));
    mockMvc
        .perform(
            post("/api/learning/tutor/questions")
                .header("Authorization", bearer("TUTOR", TUTOR_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  private long topicId(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM syllabus_topics WHERE code = ?", Long.class, code);
  }

  private long questionId(String code) {
    return jdbcTemplate.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
  }

  private String request(
      String code,
      long syllabusTopicId,
      String type,
      String prompt,
      String totalMarks,
      String modelAnswer,
      String archiveState,
      String firstDescription,
      String firstMarks,
      String secondDescription,
      String secondMarks,
      String... keywords) {
    String second =
        secondDescription == null
            ? ""
            : ",{\"description\":\"%s\",\"marks\":%s}".formatted(secondDescription, secondMarks);
    String keywordJson =
        java.util.Arrays.stream(keywords)
            .map(keyword -> "\"%s\"".formatted(keyword))
            .collect(java.util.stream.Collectors.joining(","));
    return """
            {"code":"%s","syllabusTopicId":%d,"questionType":"%s","prompt":"%s","totalMarks":%s,"modelAnswer":"%s","archiveState":"%s","markingComponents":[{"description":"%s","marks":%s}%s],"keywords":[%s]}
            """
        .formatted(
            code,
            syllabusTopicId,
            type,
            prompt,
            totalMarks,
            modelAnswer,
            archiveState,
            firstDescription,
            firstMarks,
            second,
            keywordJson);
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

  private byte[] onePixelPng() throws java.io.IOException {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }
}
