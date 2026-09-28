package com.fttranscendence.grading.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.storage.DocumentStorage;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/** Verifies the learner-facing result boundary against persisted review state. */
@SpringBootTest(properties = "learning.service.url=http://localhost/learning")
@AutoConfigureMockMvc
@Transactional
class StudentResultsIntegrationTest {
  private static final String JWT_SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long STUDENT_USER_ID = 101L;
  private static final long STUDENT_PROFILE_ID = 201L;
  private static final long WORKSHEET_ID = 301L;

  @Autowired private MockMvc mockMvc;
  @Autowired private RestTemplate restTemplate;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private SubmissionRepository submissions;

  private MockRestServiceServer learning;
  private long pageSequence;

  @BeforeEach
  void setUp() {
    learning = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void returnsOnlyFinalApprovedDataAndDerivesEveryOutcomeFromPersistedMarks() throws Exception {
    createApproved(
        401L,
        "correct answer",
        "Correct explanation.",
        new BigDecimal("2.00"),
        new BigDecimal("2.00"));
    createApproved(
        402L,
        "partial answer",
        "Add the missing detail.",
        new BigDecimal("1.00"),
        new BigDecimal("2.00"));
    createApproved(
        403L,
        "incorrect answer",
        "Review the central concept.",
        BigDecimal.ZERO,
        new BigDecimal("2.00"));
    createPending(404L, "waiting answer", new BigDecimal("2.00"));
    expectStudentWorksheet(STUDENT_PROFILE_ID, WORKSHEET_ID);

    mockMvc
        .perform(
            get("/api/grading/student/worksheets/{worksheetId}/results", WORKSHEET_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", STUDENT_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.worksheetId").value(WORKSHEET_ID))
        .andExpect(jsonPath("$.results.length()").value(4))
        .andExpect(jsonPath("$.results[?(@.worksheetQuestionId == 401)].outcome").value("CORRECT"))
        .andExpect(jsonPath("$.results[?(@.worksheetQuestionId == 401)].awardedMarks").value(2.0))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 401)].modelAnswer")
                .value("Model answer 401"))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 401)].explanation")
                .value("Correct explanation."))
        .andExpect(jsonPath("$.results[?(@.worksheetQuestionId == 402)].outcome").value("PARTIAL"))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 403)].outcome").value("INCORRECT"))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].reviewStatus")
                .value("PENDING_REVIEW"))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].outcome").value("REVIEW_NEEDED"))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].awardedMarks")
                .value(contains(nullValue())))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].modelAnswer")
                .value(contains(nullValue())))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].explanation")
                .value(contains(nullValue())))
        .andExpect(
            jsonPath("$.results[?(@.worksheetQuestionId == 404)].reviewedAt")
                .value(contains(nullValue())));
    learning.verify();
  }

  @Test
  void exposesTheNewestSubmissionAndPersistsAChangedReviewStateAcrossReloads() throws Exception {
    createApproved(
        410L, "old answer", "Old final.", new BigDecimal("2.00"), new BigDecimal("2.00"));
    Submission current =
        createApproved(
            410L, "new answer", "New final.", new BigDecimal("1.00"), new BigDecimal("2.00"));
    expectStudentWorksheet(STUDENT_PROFILE_ID, WORKSHEET_ID);
    mockMvc
        .perform(
            get("/api/grading/student/worksheets/{worksheetId}/results", WORKSHEET_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", STUDENT_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].submissionId").value(current.getId()))
        .andExpect(jsonPath("$.results[0].answer").value("new answer"))
        .andExpect(jsonPath("$.results[0].outcome").value("PARTIAL"));
    learning.verify();

    current.flagForLater(901L, "Needs a second review.");
    submissions.saveAndFlush(current);
    learning.reset();
    expectStudentWorksheet(STUDENT_PROFILE_ID, WORKSHEET_ID);
    mockMvc
        .perform(
            get("/api/grading/student/worksheets/{worksheetId}/results", WORKSHEET_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", STUDENT_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].submissionId").value(current.getId()))
        .andExpect(jsonPath("$.results[0].reviewStatus").value("FLAGGED"))
        .andExpect(jsonPath("$.results[0].outcome").value("REVIEW_NEEDED"))
        .andExpect(jsonPath("$.results[0].awardedMarks").value(nullValue()))
        .andExpect(jsonPath("$.results[0].explanation").value(nullValue()));
    learning.verify();
  }

  @Test
  void hidesMissingAndForeignWorksheetsAndRejectsNonStudents() throws Exception {
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/profile"))
        .andRespond(withSuccess("{\"id\":201}", MediaType.APPLICATION_JSON));
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/worksheets"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    mockMvc
        .perform(
            get("/api/grading/student/worksheets/999/results")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", STUDENT_USER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_WORKSHEET_NOT_FOUND"));
    learning.verify();

    learning.reset();
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/profile"))
        .andRespond(withSuccess("{\"id\":202}", MediaType.APPLICATION_JSON));
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/worksheets"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    mockMvc
        .perform(
            get("/api/grading/student/worksheets/{worksheetId}/results", WORKSHEET_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", 202L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STUDENT_WORKSHEET_NOT_FOUND"));
    learning.verify();

    mockMvc
        .perform(
            get("/api/grading/student/worksheets/{worksheetId}/results", WORKSHEET_ID)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", 901L)))
        .andExpect(status().isForbidden());
  }

  private Submission createApproved(
      long questionId, String answer, String feedback, BigDecimal marks, BigDecimal maximum) {
    Submission submission = create(questionId, answer, maximum);
    submission.approve(901L, marks, feedback);
    return submissions.saveAndFlush(submission);
  }

  private Submission createPending(long questionId, String answer, BigDecimal maximum) {
    Submission submission = create(questionId, answer, maximum);
    submission.recordAiSuggestion(
        new BigDecimal("1.00"), "Partial", "Missing detail", java.util.List.of(), "AI advice");
    return submissions.saveAndFlush(submission);
  }

  private Submission create(long questionId, String answer, BigDecimal maximum) {
    SubmissionDocument document =
        new SubmissionDocument(
            STUDENT_USER_ID,
            SubmissionDocument.OwnerRole.STUDENT,
            WORKSHEET_ID,
            STUDENT_PROFILE_ID,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(
        new DocumentStorage.StoredFile(
            STUDENT_USER_ID + "/results/" + questionId + "-" + (++pageSequence) + ".png",
            "answer.png",
            "image/png",
            1,
            "a".repeat(64)));
    document.markReady();
    document = documents.saveAndFlush(document);
    return Submission.createAnswer(
        document,
        questionId,
        questionId,
        answer,
        "Model answer " + questionId,
        maximum,
        601L,
        "SCI-601");
  }

  private void expectStudentWorksheet(long profileId, long worksheetId) {
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/profile"))
        .andRespond(withSuccess("{\"id\":" + profileId + "}", MediaType.APPLICATION_JSON));
    learning
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/student/worksheets"))
        .andRespond(withSuccess("[{\"id\":" + worksheetId + "}]", MediaType.APPLICATION_JSON));
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
