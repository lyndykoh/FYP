package com.fttranscendence.grading.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.repository.MasterySyncOutboxRepository;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.MarkingReviewService;
import com.fttranscendence.grading.service.MasterySyncDispatcher;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.transaction.Transactional;
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
class OcrSubmissionFinalizationIntegrationTest {
  private static final AuthenticatedUser STUDENT =
      new AuthenticatedUser(701L, "student@example.test", "STUDENT");
  private static final AuthenticatedUser TUTOR =
      new AuthenticatedUser(101L, "tutor@example.test", "TUTOR");

  @Autowired private MarkingReviewService service;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private OcrExtractionRepository extractions;
  @Autowired private SubmissionRepository submissions;
  @Autowired private MasterySyncOutboxRepository outbox;
  @Autowired private MasterySyncDispatcher dispatcher;
  @Autowired private RestTemplate restTemplate;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void studentConfirmationCreatesPendingAnswersQueuesTutorReviewAndIsIdempotent() {
    SubmissionDocument document = readyStudentDocument();
    OcrExtraction extraction =
        extractions.saveAndFlush(
            new OcrExtraction(
                document.getPages().get(0), null, "Metal conducts heat.", .98, "mock"));
    expectMarkingContext();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));

    MarkingReviewService.SubmissionForTutorReviewResponse result =
        service.submitOcrForTutorReview(
            STUDENT,
            document.getId(),
            new MarkingReviewService.OcrSubmissionRequest(
                java.util.List.of(
                    new MarkingReviewService.OcrAnswerMapping(extraction.getId(), 501L))));

    assertEquals("PENDING_REVIEW", result.status());
    assertEquals(1, result.submissionIds().size());
    Submission saved = submissions.findById(result.submissionIds().get(0)).orElseThrow();
    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, saved.getReviewStatus());
    assertEquals(501L, saved.getQuestionBankId());
    assertEquals("Metal conducts heat.", saved.getExtractedAnswer());
    assertEquals(
        SubmissionDocument.Status.SUBMITTED_FOR_REVIEW,
        documents.findById(document.getId()).orElseThrow().getStatus());
    assertEquals(
        1,
        outbox.findAll().stream()
            .filter(event -> event.getPayload().contains("\"reviewState\":\"PENDING_REVIEW\""))
            .count());
    assertEquals(
        1,
        outbox.findAll().stream()
            .filter(event -> event.getPayload().contains("\"tutorUserId\":101"))
            .count());
    server.verify();

    server.reset();
    server
        .expect(
            once(),
            requestTo("http://localhost/learning/api/learning/internal/marking-review-state"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.tutorUserId").value(101))
        .andExpect(jsonPath("$.reviewState").value("PENDING_REVIEW"))
        .andRespond(withSuccess());
    dispatcher.dispatchPending();
    server.verify();

    MarkingReviewService.SubmissionForTutorReviewResponse retry =
        service.submitOcrForTutorReview(
            STUDENT,
            document.getId(),
            new MarkingReviewService.OcrSubmissionRequest(
                java.util.List.of(
                    new MarkingReviewService.OcrAnswerMapping(extraction.getId(), 999L))));
    assertEquals(result.submissionIds(), retry.submissionIds());
    assertEquals(
        1,
        submissions
            .findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(document.getId())
            .size());
    assertEquals(
        1,
        outbox.findAll().stream()
            .filter(event -> event.getPayload().contains("\"reviewState\":\"PENDING_REVIEW\""))
            .count());
  }

  @Test
  void confirmationRejectsUncorrectedOcrAndForeignStudent() {
    SubmissionDocument document = readyStudentDocument();
    OcrExtraction unreadable =
        extractions.saveAndFlush(
            new OcrExtraction(document.getPages().get(0), null, "", .2, "mock"));
    MarkingReviewService.OcrSubmissionRequest request =
        new MarkingReviewService.OcrSubmissionRequest(
            java.util.List.of(new MarkingReviewService.OcrAnswerMapping(unreadable.getId(), 501L)));

    assertThrows(
        MarkingReviewService.InvalidReviewRequest.class,
        () -> service.submitOcrForTutorReview(STUDENT, document.getId(), request));
    assertThrows(
        MarkingReviewService.ReviewNotFound.class,
        () ->
            service.submitOcrForTutorReview(
                new AuthenticatedUser(702L, "other@example.test", "STUDENT"),
                document.getId(),
                request));
  }

  @Test
  void tutorOwnedOcrDocumentUsesTheSameCanonicalTutorQueue() {
    SubmissionDocument document =
        new SubmissionDocument(
            TUTOR.userId(),
            SubmissionDocument.OwnerRole.TUTOR,
            301L,
            201L,
            401L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(
        new DocumentStorage.StoredFile(
            "101/tutor-page.png", "tutor-page.png", "image/png", 32, "c".repeat(64)));
    document.markReady();
    document = documents.saveAndFlush(document);
    OcrExtraction extraction =
        extractions.saveAndFlush(
            new OcrExtraction(
                document.getPages().get(0), null, "Metal conducts heat.", .98, "mock"));

    server
        .expect(
            once(),
            requestTo(
                "http://localhost/learning/api/learning/internal/submission-authorization/marking-context"))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.actorUserId").value(101))
        .andExpect(jsonPath("$.actorRole").value("TUTOR"))
        .andExpect(jsonPath("$.studentId").value(201))
        .andExpect(jsonPath("$.worksheetId").value(301))
        .andExpect(jsonPath("$.classId").value(401))
        .andRespond(withSuccess(markingContext(), MediaType.APPLICATION_JSON));
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));

    MarkingReviewService.SubmissionForTutorReviewResponse result =
        service.submitOcrForTutorReview(
            TUTOR,
            document.getId(),
            new MarkingReviewService.OcrSubmissionRequest(
                java.util.List.of(
                    new MarkingReviewService.OcrAnswerMapping(extraction.getId(), 501L))));

    assertEquals("PENDING_REVIEW", result.status());
    assertEquals(1, result.submissionIds().size());
    assertEquals(
        Submission.ReviewStatus.PENDING_REVIEW,
        submissions.findById(result.submissionIds().get(0)).orElseThrow().getReviewStatus());
    assertEquals(
        SubmissionDocument.Status.SUBMITTED_FOR_REVIEW,
        documents.findById(document.getId()).orElseThrow().getStatus());
    server.verify();
  }

  private SubmissionDocument readyStudentDocument() {
    SubmissionDocument document =
        new SubmissionDocument(
            STUDENT.userId(),
            SubmissionDocument.OwnerRole.STUDENT,
            301L,
            201L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(
        new DocumentStorage.StoredFile(
            "701/page.png", "page.png", "image/png", 32, "b".repeat(64)));
    document.markReady();
    return documents.saveAndFlush(document);
  }

  private void expectMarkingContext() {
    server
        .expect(
            once(),
            requestTo(
                "http://localhost/learning/api/learning/internal/submission-authorization/marking-context"))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.actorUserId").value(701))
        .andExpect(jsonPath("$.actorRole").value("STUDENT"))
        .andExpect(jsonPath("$.studentId").value(201))
        .andExpect(jsonPath("$.worksheetId").value(301))
        .andRespond(withSuccess(markingContext(), MediaType.APPLICATION_JSON));
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
