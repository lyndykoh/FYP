package com.fttranscendence.grading.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.repository.MasterySyncOutboxRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.MarkingReviewService;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    properties = {
      "learning.service.url=http://localhost/learning",
      "learning.service.sync-key=test-sync-key"
    })
@Transactional
class ManualAnswerCaptureIntegrationTest {
  private static final AuthenticatedUser TUTOR =
      new AuthenticatedUser(101L, "tutor@example.test", "TUTOR");
  private static final AuthenticatedUser STUDENT =
      new AuthenticatedUser(701L, "student@example.test", "STUDENT");

  @Autowired private MarkingReviewService service;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private SubmissionRepository submissions;
  @Autowired private MasterySyncOutboxRepository outbox;
  @Autowired private RestTemplate restTemplate;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void tutorDraftIsUpdatedInPlaceThenSubmittedToTheCanonicalReviewQueue() {
    expectMarkingContext(TUTOR, 201L, 301L, 401L);
    expectAi();
    MarkingReviewService.ManualAnswerResponse draft =
        service.saveManualAnswers(
            TUTOR, request(201L, 301L, 401L, "The metal conducts heat.", false));
    assertEquals("DRAFT", draft.status());
    assertEquals("MANUAL", draft.inputMethod());
    assertEquals(1, draft.answers().size());
    assertEquals(1, draft.submissionIds().size());
    assertEquals(
        Submission.ReviewStatus.DRAFT,
        submissions.findById(draft.submissionIds().get(0)).orElseThrow().getReviewStatus());
    assertTrue(
        submissions
            .findByReviewStatusOrderByCreatedAtAsc(Submission.ReviewStatus.PENDING_REVIEW)
            .stream()
            .noneMatch(submission -> draft.submissionIds().contains(submission.getId())));
    server.verify();

    server.reset();
    expectMarkingContext(TUTOR, 201L, 301L, 401L);
    MarkingReviewService.ManualAnswerResponse reopened =
        service.loadManualAnswers(TUTOR, 201L, 301L, 401L);
    assertEquals(draft.submissionDocumentId(), reopened.submissionDocumentId());
    assertEquals("The metal conducts heat.", reopened.answers().get(0).answer());
    server.verify();

    server.reset();
    expectMarkingContext(TUTOR, 201L, 301L, 401L);
    expectAi();
    MarkingReviewService.ManualAnswerResponse edited =
        service.saveManualAnswers(
            TUTOR, request(201L, 301L, 401L, "Metal is a conductor of heat.", false));
    assertEquals(draft.submissionDocumentId(), edited.submissionDocumentId());
    assertEquals("Metal is a conductor of heat.", edited.answers().get(0).answer());
    assertEquals(
        1,
        submissions
            .findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(edited.submissionDocumentId())
            .size());
    server.verify();

    server.reset();
    expectMarkingContext(TUTOR, 201L, 301L, 401L);
    MarkingReviewService.ManualAnswerResponse submitted =
        service.saveManualAnswers(
            TUTOR, new MarkingReviewService.ManualAnswerRequest(201L, 301L, 401L, List.of(), true));
    assertEquals("PENDING_REVIEW", submitted.status());
    assertEquals(draft.submissionIds(), submitted.submissionIds());
    Submission answer =
        submissions
            .findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(
                submitted.submissionDocumentId())
            .get(0);
    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, answer.getReviewStatus());
    assertTrue(
        submissions
            .findByReviewStatusOrderByCreatedAtAsc(Submission.ReviewStatus.PENDING_REVIEW)
            .stream()
            .anyMatch(submission -> submitted.submissionIds().contains(submission.getId())));
    assertEquals("Metal is a conductor of heat.", answer.getExtractedAnswer());
    assertEquals(
        SubmissionDocument.Status.SUBMITTED_FOR_REVIEW,
        documents.findById(submitted.submissionDocumentId()).orElseThrow().getStatus());
    assertEquals(
        1,
        outbox.findAll().stream()
            .filter(event -> event.getPayload().contains("\"reviewState\":\"PENDING_REVIEW\""))
            .count());
    server.verify();

    server.reset();
    expectMarkingContext(TUTOR, 201L, 301L, 401L);
    MarkingReviewService.ManualAnswerResponse retry =
        service.saveManualAnswers(TUTOR, request(201L, 301L, 401L, "ignored after submit", true));
    assertEquals(submitted.submissionDocumentId(), retry.submissionDocumentId());
    assertEquals(
        1,
        submissions
            .findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(
                submitted.submissionDocumentId())
            .size());
    server.verify();
  }

  @Test
  void studentUsesTheSameManualModelWhileNonDomainRolesAreRejected() {
    expectMarkingContext(STUDENT, 201L, 301L, null);
    expectAi();
    MarkingReviewService.ManualAnswerResponse saved =
        service.saveManualAnswers(
            STUDENT, request(201L, 301L, null, "I entered this answer myself.", false));
    assertEquals("DRAFT", saved.status());
    assertEquals(
        SubmissionDocument.OwnerRole.STUDENT,
        documents.findById(saved.submissionDocumentId()).orElseThrow().getOwnerRole());
    server.verify();

    server.reset();
    server
        .expect(
            once(),
            requestTo(
                "http://localhost/learning/api/learning/internal/submission-authorization/marking-context"))
        .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
    assertThrows(
        com.fttranscendence.grading.service.LearningAuthorizationClient
            .SubmissionMarkingContextNotFound.class,
        () ->
            service.saveManualAnswers(
                STUDENT, request(202L, 301L, null, "Another student's answer", false)));
    server.verify();

    assertThrows(
        com.fttranscendence.grading.service.LearningAuthorizationClient.Forbidden.class,
        () ->
            service.saveManualAnswers(
                new AuthenticatedUser(88L, "admin@example.test", "ADMIN"),
                request(201L, 301L, null, "not permitted", false)));
  }

  private MarkingReviewService.ManualAnswerRequest request(
      long studentId, long worksheetId, Long classId, String answer, boolean submit) {
    return new MarkingReviewService.ManualAnswerRequest(
        studentId,
        worksheetId,
        classId,
        List.of(new MarkingReviewService.ManualAnswerEntry(501L, answer)),
        submit);
  }

  private void expectMarkingContext(
      AuthenticatedUser actor, long studentId, long worksheetId, Long classId) {
    server
        .expect(
            once(),
            requestTo(
                "http://localhost/learning/api/learning/internal/submission-authorization/marking-context"))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.actorUserId").value(actor.userId()))
        .andExpect(jsonPath("$.actorRole").value(actor.role()))
        .andExpect(jsonPath("$.studentId").value(studentId))
        .andExpect(jsonPath("$.worksheetId").value(worksheetId))
        .andRespond(withSuccess(markingContext(), MediaType.APPLICATION_JSON));
  }

  private void expectAi() {
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
  }

  private String markingContext() {
    return """
            {"tutorUserId":101,"questions":[{"questionBankId":501,"prompt":"Why does metal feel hot?","modelAnswer":"Metal conducts heat.","totalMarks":2,"keywords":["conductor"],"syllabusTopicId":601,"syllabusTopicCode":"SCI-601","markingComponents":[{"position":0,"description":"Explains heat conduction","marks":2,"keywords":[]}]}]}
            """;
  }

  private String providerResult() {
    return """
            {"choices":[{"message":{"role":"assistant","content":"{\\"suggested_marks\\":1.5,\\"correctness\\":\\"Partially correct\\",\\"error_category\\":\\"Missing detail\\",\\"missing_keywords\\":[\\"heat transfer\\"],\\"feedback\\":\\"Explain heat transfer.\\"}"}}]}
            """;
  }
}
