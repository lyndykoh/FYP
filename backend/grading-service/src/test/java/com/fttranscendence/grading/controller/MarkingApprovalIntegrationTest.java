package com.fttranscendence.grading.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fttranscendence.grading.model.DiagnosticCategory;
import com.fttranscendence.grading.model.MistakeType;
import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.repository.MasterySyncOutboxRepository;
import com.fttranscendence.grading.repository.MistakeRecordRepository;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.MarkingReviewService;
import com.fttranscendence.grading.service.MasterySyncDispatcher;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
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
class MarkingApprovalIntegrationTest {
  private static final AuthenticatedUser TUTOR =
      new AuthenticatedUser(101L, "tutor@example.com", "TUTOR");

  @Autowired private MarkingReviewService service;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private OcrExtractionRepository extractions;
  @Autowired private SubmissionRepository submissions;
  @Autowired private MasterySyncOutboxRepository syncOutbox;
  @Autowired private MistakeRecordRepository mistakes;
  @Autowired private RestTemplate restTemplate;
  @Autowired private MasterySyncDispatcher syncDispatcher;

  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
  }

  @Test
  void aiSuggestionStaysPendingUntilTutorApprovesAndDuplicateApprovalIsIdempotent() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 401L, "Metal is a conductor.", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));

    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 401L, 501L));

    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, pending.reviewStatus());
    assertEquals(new BigDecimal("1.50"), pending.aiSuggestedMarks());
    assertNull(pending.approvedMarks());
    assertEquals(0, pending.history().size());
    server.verify();

    server.reset();
    expectTutorOnly();
    MarkingReviewService.MarkingReview approved =
        service.approve(
            TUTOR,
            "Bearer token",
            pending.id(),
            new MarkingReviewService.ApprovalRequest(
                new BigDecimal("2.00"), "Tutor checked the explanation."));
    assertEquals(Submission.ReviewStatus.APPROVED, approved.reviewStatus());
    assertEquals(new BigDecimal("2.00"), approved.approvedMarks());
    assertEquals(1, approved.history().size());
    server.verify();

    server.reset();
    expectTutorOnly();
    MarkingReviewService.MarkingReview duplicate =
        service.approve(
            TUTOR,
            "Bearer token",
            pending.id(),
            new MarkingReviewService.ApprovalRequest(
                new BigDecimal("2.00"), "Tutor checked the explanation."));
    assertEquals(1, duplicate.history().size());
    assertEquals(1, submissions.findById(pending.id()).orElseThrow().getReviews().size());
    server.verify();
  }

  @Test
  void flagsAndResetsWithoutCreatingFinalMarksAndRejectsNonTutorReviewers() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 402L, "Need more work", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 402L, 501L));
    server.verify();

    server.reset();
    expectTutorOnly();
    MarkingReviewService.MarkingReview flagged =
        service.flag(
            TUTOR,
            "Bearer token",
            pending.id(),
            new MarkingReviewService.FlagRequest("Handwriting is unclear."));
    assertEquals(Submission.ReviewStatus.FLAGGED, flagged.reviewStatus());
    assertNull(flagged.approvedMarks());
    server.verify();

    server.reset();
    expectTutorOnly();
    MarkingReviewService.MarkingReview reset = service.reset(TUTOR, "Bearer token", pending.id());
    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, reset.reviewStatus());
    assertNull(reset.approvedMarks());
    server.verify();

    assertThrows(
        RuntimeException.class,
        () ->
            service.get(
                new AuthenticatedUser(201L, "student@example.com", "STUDENT"),
                "Bearer token",
                pending.id()));
  }

  @Test
  void persistsOnlyTutorConfirmedDiagnosticsAndQueuesOneEventPerRevision() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 403L, "Metal gets hot", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 403L, 501L));
    server.verify();
    assertEquals(1, syncOutbox.count(), "pending review event is durable");

    server.reset();
    expectTutorOnly();
    var evidence =
        new MarkingReviewService.DiagnosticEvidenceRequest(
            MistakeType.CONCEPT_MISUNDERSTANDING,
            DiagnosticCategory.CONCEPT,
            "Does not explain heat transfer.",
            java.util.List.of("heat transfer"));
    MarkingReviewService.MarkingReview approved =
        service.approve(
            TUTOR,
            "Bearer token",
            pending.id(),
            new MarkingReviewService.ApprovalRequest(
                new BigDecimal("1.00"), "Tutor confirmed.", java.util.List.of(evidence)));
    assertEquals(1, approved.diagnosticEvidence().size());
    assertEquals("CONCEPT_MISUNDERSTANDING", approved.diagnosticEvidence().get(0).mistakeType());
    assertEquals("CONCEPT", approved.diagnosticEvidence().get(0).category());
    assertEquals(1, mistakes.findBySubmissionIdOrderByCreatedAtAscIdAsc(pending.id()).size());
    assertEquals(3, syncOutbox.count(), "approval queues mastery and resolved-review events");
    assertTrue(
        syncOutbox.findAll().stream()
            .anyMatch(event -> event.getPayload().contains("Does not explain heat transfer.")));
    assertTrue(
        syncOutbox.findAll().stream()
            .noneMatch(event -> event.getPayload().contains("Metal gets hot")),
        "raw OCR answers must never leave the grading service");
    server.verify();

    server.reset();
    expectTutorOnly();
    service.approve(
        TUTOR,
        "Bearer token",
        pending.id(),
        new MarkingReviewService.ApprovalRequest(
            new BigDecimal("1.00"), "Tutor confirmed.", java.util.List.of(evidence)));
    assertEquals(3, syncOutbox.count(), "idempotent retry creates no extra evidence or events");
    assertEquals(
        1,
        mistakes.findBySubmissionIdOrderByCreatedAtAscIdAsc(pending.id()).size(),
        "idempotent approval does not duplicate canonical mistake history");
    server.verify();
  }

  @Test
  void rejectsMismatchedCompatibilityCategoryBeforeMutatingApproval() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 406L, "Metal gets hot", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 406L, 501L));
    server.verify();

    server.reset();
    expectTutorOnly();
    assertThrows(
        MarkingReviewService.InvalidReviewRequest.class,
        () ->
            service.approve(
                TUTOR,
                "Bearer token",
                pending.id(),
                new MarkingReviewService.ApprovalRequest(
                    new BigDecimal("1.00"),
                    "Tutor confirmed.",
                    java.util.List.of(
                        new MarkingReviewService.DiagnosticEvidenceRequest(
                            MistakeType.MISSING_KEY_POINT,
                            DiagnosticCategory.CONCEPT,
                            "The required phrase is absent.",
                            java.util.List.of("conduction"))))));
    assertEquals(
        Submission.ReviewStatus.PENDING_REVIEW,
        submissions.findById(pending.id()).orElseThrow().getReviewStatus());
    assertEquals(0, mistakes.findBySubmissionIdOrderByCreatedAtAscIdAsc(pending.id()).size());
    assertEquals(1, syncOutbox.count(), "a rejected decision emits no mastery event");
    server.verify();
  }

  @Test
  void dispatchesOnlyDurableEventsWithTheBackendIntegrationKey() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 404L, "Metal gets hot", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 404L, 501L));
    server.verify();

    server.reset();
    expectTutorOnly();
    service.approve(
        TUTOR,
        "Bearer token",
        pending.id(),
        new MarkingReviewService.ApprovalRequest(
            new BigDecimal("1.00"),
            "Tutor confirmed.",
            java.util.List.of(
                new MarkingReviewService.DiagnosticEvidenceRequest(
                    MistakeType.CONCEPT_MISUNDERSTANDING,
                    DiagnosticCategory.CONCEPT,
                    "Tutor confirmed a heat-transfer concept gap.",
                    java.util.List.of("heat transfer")))));
    server.verify();

    server.reset();
    server
        .expect(
            once(),
            requestTo("http://localhost/learning/api/learning/internal/marking-review-state"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andRespond(withSuccess());
    server
        .expect(
            once(),
            requestTo("http://localhost/learning/api/learning/internal/approved-marking-evidence"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.state").value("APPROVED"))
        .andExpect(jsonPath("$.submissionId").value(pending.id()))
        .andExpect(jsonPath("$.studentId").value(201))
        .andExpect(jsonPath("$.syllabusTopicId").value(601))
        .andExpect(
            jsonPath("$.diagnosticEvidence[0].mistakeType").value("CONCEPT_MISUNDERSTANDING"))
        .andExpect(jsonPath("$.diagnosticEvidence[0].category").value("CONCEPT"))
        .andExpect(jsonPath("$.diagnosticEvidence[0].missingKeywords[0]").value("heat transfer"))
        .andRespond(withSuccess());
    server
        .expect(
            once(),
            requestTo("http://localhost/learning/api/learning/internal/marking-review-state"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andRespond(withSuccess());

    syncDispatcher.dispatchPending();
    assertTrue(syncOutbox.findAll().stream().allMatch(event -> event.getDeliveredAt() != null));
    server.verify();
  }

  @Test
  void retractionQueuesThePriorTutorApprovedSnapshotAndDispatchesItOnce() {
    SubmissionDocument document = readyDocument();
    extractions.saveAndFlush(
        new OcrExtraction(document.getPages().get(0), 405L, "Metal gets hot", 0.95, "mock"));
    expectTutorAndQuestion();
    server
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(withSuccess(providerResult(), MediaType.APPLICATION_JSON));
    MarkingReviewService.MarkingReview pending =
        service.createAdvisoryReview(
            TUTOR,
            "Bearer token",
            new MarkingReviewService.CreateRequest(document.getId(), 405L, 501L));
    server.verify();

    server.reset();
    expectTutorOnly();
    service.approve(
        TUTOR,
        "Bearer token",
        pending.id(),
        new MarkingReviewService.ApprovalRequest(
            new BigDecimal("1.00"),
            "Tutor confirmed.",
            java.util.List.of(
                new MarkingReviewService.DiagnosticEvidenceRequest(
                    MistakeType.CONCEPT_MISUNDERSTANDING,
                    DiagnosticCategory.CONCEPT,
                    "Tutor confirmed a concept gap.",
                    java.util.List.of("heat transfer")))));
    server.verify();

    server.reset();
    expectTutorOnly();
    service.reset(TUTOR, "Bearer token", pending.id());
    server.verify();

    assertEquals(
        0,
        mistakes.findBySubmissionIdOrderByCreatedAtAscIdAsc(pending.id()).size(),
        "retraction removes canonical mistake history from active grading state");

    var retraction =
        syncOutbox.findAll().stream()
            .filter(event -> event.getPayload().contains("\"state\":\"RETRACTED\""))
            .findFirst()
            .orElseThrow();
    assertTrue(
        retraction.getPayload().contains("\"approvedMarks\":1.00"),
        "The historical approved mark survives clearing the live review fields.");
    assertTrue(
        retraction.getPayload().contains("\"diagnosticEvidence\":[]"),
        "Retracted evidence must no longer influence learning insights.");

    server.reset();
    server
        .expect(
            once(),
            requestTo("http://localhost/learning/api/learning/internal/approved-marking-evidence"))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andExpect(header("X-Learning-Integration-Key", "test-sync-key"))
        .andExpect(jsonPath("$.state").value("RETRACTED"))
        .andExpect(jsonPath("$.approvedMarks").value(1.00))
        .andExpect(jsonPath("$.diagnosticEvidence").isEmpty())
        .andRespond(withSuccess());
    syncDispatcher.dispatchOne(retraction);
    assertTrue(retraction.getDeliveredAt() != null);
    server.verify();
  }

  private SubmissionDocument readyDocument() {
    SubmissionDocument document =
        new SubmissionDocument(
            101L,
            SubmissionDocument.OwnerRole.TUTOR,
            301L,
            201L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(
        new DocumentStorage.StoredFile(
            "101/page.png", "page.png", "image/png", 32, "b".repeat(64)));
    document.markReady();
    return documents.saveAndFlush(document);
  }

  private void expectTutorAndQuestion() {
    expectTutorOnly();
    server
        .expect(once(), requestTo("http://localhost/learning/api/learning/tutor/questions/501"))
        .andRespond(withSuccess(question(), MediaType.APPLICATION_JSON));
  }

  private void expectTutorOnly() {
    server
        .expect(manyTimes(), requestTo("http://localhost/learning/api/learning/tutor/students/201"))
        .andRespond(withSuccess("{\"id\":201}", MediaType.APPLICATION_JSON));
  }

  private String question() {
    return """
            {"id":501,"prompt":"Why does metal feel hot?","totalMarks":2,"modelAnswer":"Metal conducts heat.","keywords":["conductor"],"syllabusTopic":{"id":601,"code":"SCI-601"},"markingComponents":[{"position":0,"description":"Explains heat conduction","marks":2}]}
            """;
  }

  private String providerResult() {
    return """
            {"choices":[{"message":{"role":"assistant","content":"{\\\"suggested_marks\\\":1.5,\\\"correctness\\\":\\\"Partially correct\\\",\\\"error_category\\\":\\\"Missing detail\\\",\\\"missing_keywords\\\":[\\\"heat transfer\\\"],\\\"feedback\\\":\\\"Explain heat transfer.\\\"}"}}]}
            """;
  }
}
