package com.fttranscendence.grading.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SubmissionRepositoryTest {

  private static final long OWNER_ID = 101L;
  private static final long STUDENT_ID = 201L;
  private static final long WORKSHEET_ID = 301L;

  @Autowired private SubmissionRepository submissionRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void persistsDocumentAndValidatedLearningDomainReferences() {
    SubmissionDocument document = persistReadyDocument();
    Submission answer = answer(document, 401L, 501L);
    answer.recordAiSuggestion(
        new BigDecimal("1.00"),
        "Partially Correct",
        "Missing key point",
        List.of("conduction"),
        "Explain how heat reaches the hand.");

    Submission saved = submissionRepository.saveAndFlush(answer);
    entityManager.clear();

    Submission persisted =
        submissionRepository
            .findBySubmissionDocumentIdAndWorksheetQuestionId(document.getId(), 401L)
            .orElseThrow();
    assertEquals(saved.getId(), persisted.getId());
    assertEquals(document.getId(), persisted.getSubmissionDocument().getId());
    assertEquals(STUDENT_ID, persisted.getStudentId());
    assertEquals(WORKSHEET_ID, persisted.getWorksheetId());
    assertEquals(401L, persisted.getWorksheetQuestionId());
    assertEquals(501L, persisted.getQuestionBankId());
    assertEquals("Heat moves faster through metal.", persisted.getExtractedAnswer());
    assertEquals("Metal is a better conductor.", persisted.getModelAnswerSnapshot());
    assertEquals(new BigDecimal("2.00"), persisted.getMaxMarks());
    assertEquals(List.of("conduction"), persisted.getMissingKeywords());
    assertEquals(
        List.of(saved.getId()),
        submissionRepository
            .findBySubmissionDocumentOwnerUserIdOrderByCreatedAtDesc(OWNER_ID)
            .stream()
            .map(Submission::getId)
            .toList());
  }

  @Test
  void partialAiScoreRemainsASuggestionUntilTutorApproval() {
    Submission answer = answer(persistReadyDocument(), 402L, 502L);
    answer.recordAiSuggestion(
        new BigDecimal("1.50"),
        "Partially Correct",
        "Weak explanation",
        List.of("energy transfer"),
        "Name the direction of heat transfer.");

    Submission saved = submissionRepository.saveAndFlush(answer);

    assertEquals(new BigDecimal("1.50"), saved.getAiSuggestedMarks());
    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, saved.getReviewStatus());
    assertNull(saved.getApprovedMarks());
    assertNull(saved.getApprovedFeedback());
    assertNull(saved.getReviewedByUserId());
    assertTrue(saved.getReviews().isEmpty());
    assertEquals(
        List.of(saved.getId()),
        submissionRepository
            .findByReviewStatusOrderByCreatedAtAsc(Submission.ReviewStatus.PENDING_REVIEW)
            .stream()
            .map(Submission::getId)
            .toList());
  }

  @Test
  void approvalAndRevisionRetainAiAndTutorVersionsInAuditHistory() {
    Submission answer = answer(persistReadyDocument(), 403L, 503L);
    answer.recordAiSuggestion(
        new BigDecimal("1.00"),
        "Partially Correct",
        "Missing key point",
        List.of("conduction"),
        "AI feedback");
    submissionRepository.saveAndFlush(answer);

    answer.approve(701L, new BigDecimal("1.50"), "Tutor-approved feedback");
    submissionRepository.saveAndFlush(answer);
    answer.approve(702L, new BigDecimal("2.00"), "Revised tutor feedback");
    submissionRepository.saveAndFlush(answer);
    Long answerId = answer.getId();
    entityManager.clear();

    Submission persisted = submissionRepository.findById(answerId).orElseThrow();
    assertEquals(new BigDecimal("1.00"), persisted.getAiSuggestedMarks());
    assertEquals("AI feedback", persisted.getAiSuggestedFeedback());
    assertEquals(new BigDecimal("2.00"), persisted.getApprovedMarks());
    assertEquals("Revised tutor feedback", persisted.getApprovedFeedback());
    assertEquals(702L, persisted.getReviewedByUserId());
    assertEquals(Submission.ReviewStatus.APPROVED, persisted.getReviewStatus());

    List<AnswerReview> history = persisted.getReviews();
    assertEquals(2, history.size());
    assertEquals(AnswerReview.Action.APPROVED, history.get(0).getAction());
    assertEquals(new BigDecimal("1.00"), history.get(0).getPreviousMarks());
    assertEquals(new BigDecimal("1.50"), history.get(0).getNewMarks());
    assertEquals(AnswerReview.Action.REVISED, history.get(1).getAction());
    assertEquals(new BigDecimal("1.50"), history.get(1).getPreviousMarks());
    assertEquals(new BigDecimal("2.00"), history.get(1).getNewMarks());
    assertThrows(
        IllegalStateException.class,
        () ->
            persisted.recordAiSuggestion(
                new BigDecimal("2.00"), "Correct", null, List.of(), "Replacement AI feedback"));
  }

  @Test
  void flagAndResetActionsRemainAuditableWithoutCreatingAFinalScore() {
    Submission answer = answer(persistReadyDocument(), 404L, 504L);
    answer.recordAiSuggestion(
        new BigDecimal("1.00"), "Partially Correct", null, List.of(), "AI feedback");
    answer.flagForLater(703L, "Check the handwriting");
    assertEquals(Submission.ReviewStatus.FLAGGED, answer.getReviewStatus());
    assertNull(answer.getApprovedMarks());

    answer.resetToAiSuggestion(704L);
    Submission saved = submissionRepository.saveAndFlush(answer);

    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, saved.getReviewStatus());
    assertNull(saved.getApprovedMarks());
    assertNull(saved.getReviewedByUserId());
    assertEquals(
        List.of(AnswerReview.Action.FLAGGED, AnswerReview.Action.RESET_TO_AI),
        saved.getReviews().stream().map(AnswerReview::getAction).toList());
  }

  @Test
  void duplicateAnswerForTheSameWorksheetQuestionIsRejected() {
    SubmissionDocument document = persistReadyDocument();
    Submission first = answer(document, 405L, 505L);
    Submission duplicate = answer(document, 405L, 505L);
    submissionRepository.saveAndFlush(first);

    assertThrows(
        DataIntegrityViolationException.class, () -> submissionRepository.saveAndFlush(duplicate));
  }

  @Test
  void scoreAndRelationshipConstraintsRejectInvalidAnswers() {
    SubmissionDocument uploadingDocument =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.TUTOR,
            WORKSHEET_ID,
            STUDENT_ID,
            SubmissionDocument.SourceType.PDF);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            Submission.createAnswer(
                uploadingDocument,
                406L,
                506L,
                "Answer",
                "Model answer",
                new BigDecimal("2.00"),
                606L,
                "SCI-606"));

    Submission valid = answer(persistReadyDocument(), 406L, 506L);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            valid.recordAiSuggestion(
                new BigDecimal("2.01"), "Correct", null, List.of(), "Feedback"));
    assertThrows(
        IllegalArgumentException.class,
        () -> valid.approve(0L, new BigDecimal("1.00"), "Feedback"));
    assertThrows(
        IllegalArgumentException.class,
        () -> valid.approve(701L, new BigDecimal("-0.01"), "Feedback"));
    assertFalse(valid.getReviewStatus() == Submission.ReviewStatus.APPROVED);
  }

  private SubmissionDocument persistReadyDocument() {
    SubmissionDocument document =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.TUTOR,
            WORKSHEET_ID,
            STUDENT_ID,
            SubmissionDocument.SourceType.PDF);
    document.addPage(
        new DocumentStorage.StoredFile(
            OWNER_ID + "/answer-source.pdf",
            "answer-source.pdf",
            "application/pdf",
            128,
            "a".repeat(64)));
    document.markReady();
    entityManager.persist(document);
    entityManager.flush();
    return document;
  }

  private Submission answer(
      SubmissionDocument document, long worksheetQuestionId, long questionBankId) {
    return Submission.createAnswer(
        document,
        worksheetQuestionId,
        questionBankId,
        "Heat moves faster through metal.",
        "Metal is a better conductor.",
        new BigDecimal("2.00"),
        606L,
        "SCI-606");
  }
}
