package com.fttranscendence.grading.controller;

import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.grading.model.MistakeRecord;
import com.fttranscendence.grading.model.MistakeType;
import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.repository.MistakeRecordRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.storage.DocumentStorage;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/** Exercises the learner-owned, persisted mistake history API end to end. */
@SpringBootTest(properties = "learning.service.url=http://localhost/learning")
@AutoConfigureMockMvc
@Transactional
class MistakeControllerTest {
  private static final String JWT_SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long STUDENT_USER_ID = 101L;
  private static final long STUDENT_PROFILE_ID = 201L;

  @Autowired private MockMvc mockMvc;
  @Autowired private RestTemplate restTemplate;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private SubmissionRepository submissions;
  @Autowired private MistakeRecordRepository mistakes;

  private MockRestServiceServer learning;
  private long pageSequence;

  @BeforeEach
  void setUp() {
    learning = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void filtersPersistedHistoryAndCountsRepeatedErrorsAcrossTheFullHistory() throws Exception {
    createMistake(
        301L,
        401L,
        601L,
        "SCI-601",
        MistakeType.CONCEPT_MISUNDERSTANDING,
        "Heat transfer is missing.",
        LocalDateTime.of(2026, 1, 10, 9, 0));
    createMistake(
        302L,
        402L,
        601L,
        "SCI-601",
        MistakeType.CONCEPT_MISUNDERSTANDING,
        "The conduction concept is missing.",
        LocalDateTime.of(2026, 2, 10, 9, 0));
    createMistake(
        301L,
        403L,
        601L,
        "SCI-601",
        MistakeType.MISSING_KEY_POINT,
        "The key term is absent.",
        LocalDateTime.of(2026, 2, 15, 9, 0));
    createMistake(
        303L,
        404L,
        602L,
        "SCI-602",
        MistakeType.CONCEPT_MISUNDERSTANDING,
        "Force and mass are confused.",
        LocalDateTime.of(2026, 3, 2, 9, 0));
    expectStudent(STUDENT_PROFILE_ID);

    mockMvc
        .perform(studentGet())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[?(@.worksheetQuestionId == 401)].subjectId").value(100))
        .andExpect(jsonPath("$[?(@.worksheetQuestionId == 401)].subjectName").value("Science"))
        .andExpect(jsonPath("$[?(@.worksheetQuestionId == 401)].topicName").value("Heat transfer"))
        .andExpect(jsonPath("$[?(@.worksheetQuestionId == 401)].occurrenceCount").value(2))
        .andExpect(jsonPath("$[?(@.worksheetQuestionId == 401)].status").value("CONFIRMED"));

    mockMvc
        .perform(studentGet().param("subjectId", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
    mockMvc
        .perform(studentGet().param("topicId", "601"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    mockMvc
        .perform(studentGet().param("mistakeType", "CONCEPT_MISUNDERSTANDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    mockMvc
        .perform(studentGet().param("worksheetId", "302"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].worksheetQuestionId").value(402));
    mockMvc
        .perform(studentGet().param("from", "2026-02-01").param("to", "2026-02-28"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    learning.verify();
  }

  @Test
  void returnsEmptyHistoryForAnotherStudentWithoutExposingTheOwnerRecords() throws Exception {
    createMistake(
        301L,
        401L,
        601L,
        "SCI-601",
        MistakeType.CONCEPT_MISUNDERSTANDING,
        "Heat transfer is missing.",
        LocalDateTime.of(2026, 1, 10, 9, 0));
    expectStudent(202L);

    mockMvc
        .perform(studentGet())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    learning.verify();
  }

  @Test
  void returnsNonEnumeratingNotFoundWhenTheAuthenticatedStudentHasNoLearningProfile()
      throws Exception {
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/profile"))
        .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

    mockMvc
        .perform(studentGet())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_MISTAKES_NOT_FOUND"));
    learning.verify();
  }

  @Test
  void rejectsWrongRolesAndInvalidFilters() throws Exception {
    mockMvc
        .perform(
            get("/api/grading/student/mistakes")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 901L)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(studentGet().param("subjectId", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_STUDENT_MISTAKES_REQUEST"));
    mockMvc
        .perform(studentGet().param("mistakeType", "not-a-type"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(studentGet().param("from", "2026-02-30")).andExpect(status().isBadRequest());
    mockMvc
        .perform(studentGet().param("from", "2026-03-02").param("to", "2026-03-01"))
        .andExpect(status().isBadRequest());
  }

  private MistakeRecord createMistake(
      long worksheetId,
      long questionId,
      long topicId,
      String topicCode,
      MistakeType type,
      String description,
      LocalDateTime createdAt) {
    SubmissionDocument document =
        new SubmissionDocument(
            STUDENT_USER_ID,
            SubmissionDocument.OwnerRole.STUDENT,
            worksheetId,
            STUDENT_PROFILE_ID,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(
        new DocumentStorage.StoredFile(
            STUDENT_USER_ID + "/mistakes/" + (++pageSequence) + ".png",
            "answer.png",
            "image/png",
            1,
            "a".repeat(64)));
    document.markReady();
    document = documents.saveAndFlush(document);
    Submission submission =
        Submission.createAnswer(
            document,
            questionId,
            questionId,
            "student answer",
            "model",
            new BigDecimal("2.00"),
            topicId,
            topicCode);
    submission.approve(901L, BigDecimal.ZERO, "Tutor confirmed.");
    submission.addMistake(type, topicId, topicCode, description);
    submission = submissions.saveAndFlush(submission);
    MistakeRecord record = submission.getMistakes().get(0);
    ReflectionTestUtils.setField(record, "createdAt", createdAt);
    return mistakes.saveAndFlush(record);
  }

  private void expectStudent(long profileId) {
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/profile"))
        .andRespond(withSuccess("{\"id\":" + profileId + "}", MediaType.APPLICATION_JSON));
    learning
        .expect(
            manyTimes(), requestTo("http://localhost/learning/api/learning/shared/syllabus/tree"))
        .andRespond(withSuccess(syllabus(), MediaType.APPLICATION_JSON));
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      studentGet() {
    return get("/api/grading/student/mistakes")
        .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", STUDENT_USER_ID));
  }

  private static String syllabus() {
    return """
            {"items":[{"id":100,"code":"SCI","name":"Science","nodeType":"SUBJECT","parentId":null,"children":[
              {"id":601,"code":"SCI-601","name":"Heat transfer","nodeType":"TOPIC","parentId":100,"children":[]},
              {"id":602,"code":"SCI-602","name":"Forces","nodeType":"TOPIC","parentId":100,"children":[]}
            ]}]}
            """;
  }

  private static String bearer(String role, long userId) {
    String jwt =
        Jwts.builder()
            .setSubject(role.toLowerCase() + "@example.com")
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(
                Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
    return "Bearer " + jwt;
  }
}
