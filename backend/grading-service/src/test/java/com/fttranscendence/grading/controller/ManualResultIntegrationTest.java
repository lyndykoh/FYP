package com.fttranscendence.grading.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.MarkingReviewService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Exercises the controller's page-less manual fallback against real persistence and mocked learning
 * context.
 */
@SpringBootTest(properties = "learning.service.url=http://localhost/learning")
@Transactional
class ManualResultIntegrationTest {
  private static final AuthenticatedUser TUTOR =
      new AuthenticatedUser(101L, "tutor@example.com", "TUTOR");

  @Autowired private MarkingReviewController controller;
  @Autowired private MarkingReviewService service;
  @Autowired private SubmissionRepository submissions;
  @Autowired private RestTemplate restTemplate;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void controllerRecordsValidAndPartialManualScoresThroughTheApprovalPipeline() {
    expectManualContext(true, false);
    var response =
        controller.createManualResult(TUTOR, "Bearer token", manual(new BigDecimal("2.00")));
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    var result = response.getBody();
    assertEquals(Submission.ReviewStatus.APPROVED, result.reviewStatus());
    assertEquals(new BigDecimal("2.00"), result.approvedMarks());
    assertEquals(1, result.history().size());
    Submission saved = submissions.findById(result.id()).orElseThrow();
    assertEquals(
        SubmissionDocument.SourceType.MANUAL, saved.getSubmissionDocument().getSourceType());
    server.verify();
  }

  @Test
  void partialScoreIsAcceptedAndDuplicateManualSubmitIsRejected() {
    expectManualContext(true, false);
    var result = service.createManualResult(TUTOR, "Bearer token", manual(new BigDecimal("1.25")));
    assertEquals(new BigDecimal("1.25"), result.approvedMarks());
    server.verify();

    server.reset();
    expectManualContext(true, false);
    assertThrows(
        MarkingReviewService.ManualResultAlreadyExists.class,
        () -> service.createManualResult(TUTOR, "Bearer token", manual(new BigDecimal("1.25"))));
    server.verify();
  }

  @Test
  void rejectsRequiredAndOutOfRangeValuesBeforePersistingAResult() {
    assertThrows(
        MarkingReviewService.InvalidManualResultRequest.class,
        () ->
            service.createManualResult(
                TUTOR,
                "Bearer token",
                new MarkingReviewService.ManualResultRequest(
                    301L, 201L, 501L, "", new BigDecimal("1.00"), "Checked.")));
    server.reset();

    expectManualContext(true, false);
    assertThrows(
        MarkingReviewService.InvalidManualResultRequest.class,
        () -> service.createManualResult(TUTOR, "Bearer token", manual(new BigDecimal("2.01"))));
    assertEquals(0, submissions.findAll().size());
    server.verify();
  }

  @Test
  void rejectsUnassignedStudentOrNonTutorCaller() {
    server.reset();
    expectManualContext(false, false);
    assertThrows(
        LearningAuthorizationClient.ManualResultContextNotFound.class,
        () -> service.createManualResult(TUTOR, "Bearer token", manual(new BigDecimal("1.00"))));
    server.verify();

    assertThrows(
        LearningAuthorizationClient.Forbidden.class,
        () ->
            service.createManualResult(
                new AuthenticatedUser(201L, "student@example.com", "STUDENT"),
                "Bearer token",
                manual(new BigDecimal("1.00"))));
  }

  @Test
  void batchCreatesMultipleQuestionsAtomicallyAndReturnsPerStudentProgress() {
    expectManualContext(true, true);
    var results =
        service.createManualResults(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.ManualResultBatchRequest(
                301L,
                201L,
                java.util.List.of(
                    new MarkingReviewService.ManualResultEntry(
                        501L,
                        "Metal is a conductor.",
                        new BigDecimal("1.25"),
                        "Central idea is present."),
                    new MarkingReviewService.ManualResultEntry(
                        502L, "Copper.", new BigDecimal("1.00"), "Correct example."))));
    assertEquals(2, results.size());
    assertEquals(2, submissions.findAll().size());
    server.verify();

    server.reset();
    server
        .expect(
            manyTimes(), requestTo("http://localhost/learning/api/learning/tutor/worksheets/301"))
        .andRespond(withSuccess("{\"id\":301}", MediaType.APPLICATION_JSON));
    var progress = service.listManualResults(TUTOR, "Bearer token", 301L);
    assertEquals(301L, progress.worksheetId());
    assertEquals(1, progress.students().size());
    assertEquals(201L, progress.students().get(0).studentId());
    assertEquals(2, progress.students().get(0).completedQuestions());
    server.verify();
  }

  @Test
  void batchRejectsAnInvalidRowBeforeAnyResultIsPersistedAndHidesForeignWorksheet() {
    expectManualContext(true, true);
    assertThrows(
        MarkingReviewService.InvalidManualResultRequest.class,
        () ->
            service.createManualResults(
                TUTOR,
                "Bearer token",
                new MarkingReviewService.ManualResultBatchRequest(
                    301L,
                    201L,
                    java.util.List.of(
                        new MarkingReviewService.ManualResultEntry(
                            501L, "Answer", new BigDecimal("1.00"), "Feedback"),
                        new MarkingReviewService.ManualResultEntry(
                            502L, "Answer", new BigDecimal("1.01"), "Feedback")))));
    assertEquals(0, submissions.findAll().size());
    server.verify();

    server.reset();
    server
        .expect(requestTo("http://localhost/learning/api/learning/tutor/worksheets/999"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertThrows(
        LearningAuthorizationClient.ManualResultContextNotFound.class,
        () -> service.listManualResults(TUTOR, "Bearer token", 999L));
    server.verify();
  }

  private MarkingReviewService.ManualResultRequest manual(BigDecimal marks) {
    return new MarkingReviewService.ManualResultRequest(
        301L,
        201L,
        501L,
        "Metal is a conductor of heat.",
        marks,
        "Shows the central idea; add more detail.");
  }

  private void expectManualContext(boolean assigned, boolean includeSecondQuestion) {
    server
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/tutor/students/201"))
        .andRespond(
            withSuccess("{\"id\":201,\"classes\":[{\"id\":401}]}", MediaType.APPLICATION_JSON));
    String assignment =
        assigned
            ? "{\"assignmentType\":\"CLASS\",\"classId\":401,\"studentProfileId\":null}"
            : "{\"assignmentType\":\"STUDENT\",\"classId\":null,\"studentProfileId\":202}";
    server
        .expect(
            manyTimes(), requestTo("http://localhost/learning/api/learning/tutor/worksheets/301"))
        .andRespond(
            withSuccess(
                """
                {"id":301,"status":"APPROVED","questions":[{"id":501,"totalMarks":2},{"id":502,"totalMarks":1}],"assignments":[%s]}
                """
                    .formatted(assignment),
                MediaType.APPLICATION_JSON));
    if (assigned) {
      server
          .expect(
              manyTimes(), requestTo("http://localhost/learning/api/learning/tutor/questions/501"))
          .andRespond(
              withSuccess(
                  """
                    {"id":501,"prompt":"Why does metal feel hot?","totalMarks":2,"modelAnswer":"Metal conducts heat.","keywords":["conductor"],"syllabusTopic":{"id":601,"code":"SCI-601"},"markingComponents":[{"position":0,"description":"Explains heat conduction","marks":2}]}
                    """,
                  MediaType.APPLICATION_JSON));
      if (includeSecondQuestion) {
        server
            .expect(
                manyTimes(),
                requestTo("http://localhost/learning/api/learning/tutor/questions/502"))
            .andRespond(
                withSuccess(
                    """
                        {"id":502,"prompt":"Name a conductor.","totalMarks":1,"modelAnswer":"Copper is a conductor.","keywords":["copper"],"syllabusTopic":{"id":601,"code":"SCI-601"},"markingComponents":[{"position":0,"description":"Names a conductor","marks":1}]}
                        """,
                    MediaType.APPLICATION_JSON));
      }
    }
  }
}
