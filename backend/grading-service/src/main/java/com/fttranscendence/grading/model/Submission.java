package com.fttranscendence.grading.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "submissions")
public class Submission {

  public enum ReviewStatus {
    DRAFT,
    PENDING_REVIEW,
    FLAGGED,
    APPROVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_document_id")
  @JsonIgnore
  private SubmissionDocument submissionDocument;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "worksheet_id")
  private Long worksheetId;

  @Column(name = "worksheet_question_id")
  private Long worksheetQuestionId;

  @Column(name = "question_bank_id")
  private Long questionBankId;

  /** Immutable syllabus-topic snapshot taken from Learning's protected question response. */
  @Column(name = "syllabus_topic_id")
  private Long syllabusTopicId;

  @Column(name = "syllabus_topic_code", length = 120)
  private String syllabusTopicCode;

  @Column(name = "legacy_question_reference", length = 255)
  private String legacyQuestionReference;

  @Column(name = "extracted_answer", columnDefinition = "TEXT")
  private String extractedAnswer;

  @Column(name = "model_answer_snapshot", columnDefinition = "TEXT")
  private String modelAnswerSnapshot;

  @Column(name = "max_marks", precision = 6, scale = 2)
  private BigDecimal maxMarks;

  @Column(name = "ai_suggested_marks", precision = 6, scale = 2)
  private BigDecimal aiSuggestedMarks;

  @Column(name = "ai_suggested_outcome", length = 255)
  private String aiSuggestedOutcome;

  @Column(name = "ai_error_category", length = 255)
  private String aiErrorCategory;

  @Column(name = "ai_suggested_feedback", columnDefinition = "TEXT")
  private String aiSuggestedFeedback;

  @ElementCollection
  @CollectionTable(
      name = "submission_missing_keywords",
      joinColumns = @JoinColumn(name = "submission_id"))
  @Column(name = "keyword")
  private Set<String> missingKeywords = new LinkedHashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 24)
  private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;

  @Column(name = "approved_marks", precision = 6, scale = 2)
  private BigDecimal approvedMarks;

  @Column(name = "approved_feedback", columnDefinition = "TEXT")
  private String approvedFeedback;

  @Column(name = "reviewed_by_user_id")
  private Long reviewedByUserId;

  @Column(name = "reviewed_at")
  private LocalDateTime reviewedAt;

  @OneToMany(
      mappedBy = "submission",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt ASC, id ASC")
  private List<AnswerReview> reviews = new ArrayList<>();

  @OneToMany(
      mappedBy = "submission",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt ASC, id ASC")
  @JsonIgnore
  private List<MistakeRecord> mistakes = new ArrayList<>();

  @OneToMany(
      mappedBy = "submission",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC, id ASC")
  @JsonIgnore
  private List<ApprovedDiagnosticEvidence> approvedDiagnosticEvidence = new ArrayList<>();

  @Column(name = "mastery_sync_revision", nullable = false)
  private long masterySyncRevision;

  @Column(name = "legacy_record", nullable = false)
  private boolean legacyRecord;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  public Submission() {}

  public static Submission createAnswer(
      SubmissionDocument submissionDocument,
      Long worksheetQuestionId,
      Long questionBankId,
      String extractedAnswer,
      String modelAnswerSnapshot,
      BigDecimal maxMarks,
      Long syllabusTopicId,
      String syllabusTopicCode) {
    if (submissionDocument == null) {
      throw new IllegalArgumentException("Submission document is required");
    }
    if (submissionDocument.getStatus() != SubmissionDocument.Status.READY) {
      throw new IllegalArgumentException("Submission document must be ready");
    }

    Submission submission = new Submission();
    submission.submissionDocument = submissionDocument;
    submission.studentId = requirePositive(submissionDocument.getStudentId(), "Student id");
    submission.worksheetId = requirePositive(submissionDocument.getWorksheetId(), "Worksheet id");
    submission.worksheetQuestionId = requirePositive(worksheetQuestionId, "Worksheet question id");
    submission.questionBankId = requirePositive(questionBankId, "Question bank id");
    submission.extractedAnswer = normalizeAnswer(extractedAnswer);
    submission.modelAnswerSnapshot = requireText(modelAnswerSnapshot, "Model answer snapshot");
    submission.maxMarks = normalizeMaximum(maxMarks);
    submission.setSyllabusTopicSnapshot(syllabusTopicId, syllabusTopicCode);
    return submission;
  }

  /** A typed answer remains private until its owner explicitly submits it. */
  public static Submission createDraftAnswer(
      SubmissionDocument submissionDocument,
      Long worksheetQuestionId,
      Long questionBankId,
      String extractedAnswer,
      String modelAnswerSnapshot,
      BigDecimal maxMarks,
      Long syllabusTopicId,
      String syllabusTopicCode) {
    Submission submission =
        createAnswer(
            submissionDocument,
            worksheetQuestionId,
            questionBankId,
            extractedAnswer,
            modelAnswerSnapshot,
            maxMarks,
            syllabusTopicId,
            syllabusTopicCode);
    submission.reviewStatus = ReviewStatus.DRAFT;
    return submission;
  }

  public void recordAiSuggestion(
      BigDecimal suggestedMarks,
      String suggestedOutcome,
      String errorCategory,
      List<String> missingKeywords,
      String suggestedFeedback) {
    requireCanonicalAnswer();
    if (!isUnreviewed() || !reviews.isEmpty()) {
      throw new IllegalStateException("AI suggestion cannot replace a tutor-reviewed result");
    }
    this.aiSuggestedMarks = normalizeScore(suggestedMarks, "AI suggested marks");
    this.aiSuggestedOutcome = requireText(suggestedOutcome, "AI suggested outcome");
    this.aiErrorCategory = normalizeOptional(errorCategory);
    this.missingKeywords = normalizeKeywords(missingKeywords);
    this.aiSuggestedFeedback = requireText(suggestedFeedback, "AI suggested feedback");
  }

  /**
   * Replaces a draft answer before it has been reviewed by a Tutor. Both OCR confirmation and
   * manual entry persist through this aggregate, so a browser retry/edit must update the existing
   * canonical answer rather than create another row for the same worksheet question.
   */
  public void replacePendingAnswer(String answer) {
    requireCanonicalAnswer();
    if (!isUnreviewed() || !reviews.isEmpty()) {
      throw new IllegalStateException("A tutor-reviewed answer cannot be edited");
    }
    this.extractedAnswer = normalizeAnswer(answer);
    this.aiSuggestedMarks = null;
    this.aiSuggestedOutcome = null;
    this.aiErrorCategory = null;
    this.aiSuggestedFeedback = null;
    this.missingKeywords.clear();
  }

  /** Makes an already-persisted manual draft visible to the Tutor review queue. */
  public void submitForTutorReview() {
    requireCanonicalAnswer();
    if (reviewStatus == ReviewStatus.DRAFT) {
      reviewStatus = ReviewStatus.PENDING_REVIEW;
      return;
    }
    if (reviewStatus != ReviewStatus.PENDING_REVIEW || !reviews.isEmpty()) {
      throw new IllegalStateException("A tutor-reviewed answer cannot be resubmitted");
    }
  }

  public void approve(Long reviewerUserId, BigDecimal marks, String feedback) {
    requireCanonicalAnswer();
    Long reviewer = requirePositive(reviewerUserId, "Reviewer user id");
    BigDecimal normalizedMarks = normalizeScore(marks, "Approved marks");
    String normalizedFeedback = requireText(feedback, "Approved feedback");

    ReviewStatus previousStatus = reviewStatus;
    BigDecimal previousMarks = approvedMarks != null ? approvedMarks : aiSuggestedMarks;
    String previousFeedback = approvedFeedback != null ? approvedFeedback : aiSuggestedFeedback;
    AnswerReview.Action action =
        previousStatus == ReviewStatus.APPROVED
            ? AnswerReview.Action.REVISED
            : AnswerReview.Action.APPROVED;

    reviewStatus = ReviewStatus.APPROVED;
    approvedMarks = normalizedMarks;
    approvedFeedback = normalizedFeedback;
    reviewedByUserId = reviewer;
    reviewedAt = LocalDateTime.now();
    reviews.add(
        new AnswerReview(
            this,
            action,
            reviewer,
            previousStatus,
            reviewStatus,
            previousMarks,
            approvedMarks,
            previousFeedback,
            approvedFeedback));
  }

  public void flagForLater(Long reviewerUserId, String reason) {
    requireCanonicalAnswer();
    Long reviewer = requirePositive(reviewerUserId, "Reviewer user id");
    String normalizedReason = requireText(reason, "Flag reason");
    ReviewStatus previousStatus = reviewStatus;
    BigDecimal previousMarks = approvedMarks != null ? approvedMarks : aiSuggestedMarks;
    String previousFeedback = approvedFeedback != null ? approvedFeedback : aiSuggestedFeedback;

    reviewStatus = ReviewStatus.FLAGGED;
    approvedMarks = null;
    approvedFeedback = null;
    reviewedByUserId = reviewer;
    reviewedAt = LocalDateTime.now();
    reviews.add(
        new AnswerReview(
            this,
            AnswerReview.Action.FLAGGED,
            reviewer,
            previousStatus,
            reviewStatus,
            previousMarks,
            null,
            previousFeedback,
            normalizedReason));
    if (previousStatus == ReviewStatus.APPROVED) {
      clearApprovedDiagnosticEvidence();
    }
  }

  public void resetToAiSuggestion(Long reviewerUserId) {
    requireCanonicalAnswer();
    if (reviewStatus == ReviewStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Answer is already pending tutor review");
    }
    Long reviewer = requirePositive(reviewerUserId, "Reviewer user id");
    ReviewStatus previousStatus = reviewStatus;
    BigDecimal previousMarks = approvedMarks;
    String previousFeedback = approvedFeedback;

    reviewStatus = ReviewStatus.PENDING_REVIEW;
    approvedMarks = null;
    approvedFeedback = null;
    reviewedByUserId = null;
    reviewedAt = null;
    reviews.add(
        new AnswerReview(
            this,
            AnswerReview.Action.RESET_TO_AI,
            reviewer,
            previousStatus,
            reviewStatus,
            previousMarks,
            aiSuggestedMarks,
            previousFeedback,
            aiSuggestedFeedback));
    if (previousStatus == ReviewStatus.APPROVED) {
      clearApprovedDiagnosticEvidence();
    }
  }

  /**
   * Replaces the currently-approved diagnostic projection. Evidence is accepted only after an
   * explicit tutor approval; provider suggestions cannot reach this method.
   */
  public void replaceApprovedDiagnosticEvidence(List<DiagnosticEvidenceInput> inputs) {
    if (reviewStatus != ReviewStatus.APPROVED) {
      throw new IllegalStateException("Diagnostic evidence requires tutor approval");
    }
    if (inputs == null || inputs.isEmpty()) {
      clearApprovedDiagnosticEvidence();
      return;
    }
    Set<MistakeType> uniqueTypes = new LinkedHashSet<>();
    for (DiagnosticEvidenceInput input : inputs) {
      if (input == null)
        throw new IllegalArgumentException("Diagnostic evidence entry is required");
      if (!Objects.equals(syllabusTopicId, input.syllabusTopicId())) {
        throw new IllegalArgumentException(
            "Diagnostic evidence must use the question syllabus topic");
      }
      if (input.mistakeType() == null) {
        throw new IllegalArgumentException("Diagnostic evidence requires a mistake type");
      }
      if (!uniqueTypes.add(input.mistakeType())) {
        throw new IllegalArgumentException("A mistake type may be recorded once per answer");
      }
    }
    clearApprovedDiagnosticEvidence();
    int position = 0;
    for (DiagnosticEvidenceInput input : inputs) {
      approvedDiagnosticEvidence.add(
          ApprovedDiagnosticEvidence.create(
              this,
              position++,
              input.syllabusTopicId(),
              input.mistakeType(),
              input.description(),
              input.missingKeywords()));
      mistakes.add(
          MistakeRecord.create(
              this, input.mistakeType(), syllabusTopicId, syllabusTopicCode, input.description()));
    }
  }

  /** Removes the live diagnostic projection whenever approval is retracted. */
  private void clearApprovedDiagnosticEvidence() {
    approvedDiagnosticEvidence.clear();
    mistakes.clear();
  }

  /** Advances the source revision used by Learning's idempotent projection. */
  public long nextMasterySyncRevision() {
    return ++masterySyncRevision;
  }

  /**
   * Adds one controlled mistake to this answer. A single answer may have multiple different mistake
   * types, but the same type is recorded once; the same type on another answer remains a separate
   * history event.
   */
  public MistakeRecord addMistake(
      MistakeType mistakeType, Long syllabusTopicId, String syllabusTopicCode, String description) {
    requireCanonicalAnswer();
    if (mistakes.stream().anyMatch(mistake -> mistake.getMistakeType() == mistakeType)) {
      throw new IllegalArgumentException("This mistake type is already recorded for the answer");
    }
    MistakeRecord mistake =
        MistakeRecord.create(this, mistakeType, syllabusTopicId, syllabusTopicCode, description);
    mistakes.add(mistake);
    return mistake;
  }

  @PrePersist
  protected void beforeInsert() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    validateAggregate();
  }

  @PreUpdate
  protected void beforeUpdate() {
    updatedAt = LocalDateTime.now();
    validateAggregate();
  }

  private void validateAggregate() {
    requirePositive(studentId, "Student id");
    if (legacyRecord) {
      if (submissionDocument != null
          || legacyQuestionReference == null
          || legacyQuestionReference.isBlank()
          || worksheetId != null
          || worksheetQuestionId != null
          || questionBankId != null
          || modelAnswerSnapshot != null
          || maxMarks != null) {
        throw new IllegalStateException("Legacy diagnostic reference is inconsistent");
      }
    } else {
      requireCanonicalAnswer();
    }
    validateReviewState();
    if (masterySyncRevision < 0) {
      throw new IllegalStateException("Mastery synchronization revision cannot be negative");
    }
  }

  private void requireCanonicalAnswer() {
    if (legacyRecord) {
      throw new IllegalStateException("Legacy diagnostics cannot be tutor-approved");
    }
    if (submissionDocument == null) {
      throw new IllegalStateException("Submission document is required");
    }
    requirePositive(worksheetId, "Worksheet id");
    requirePositive(worksheetQuestionId, "Worksheet question id");
    requirePositive(questionBankId, "Question bank id");
    requirePositive(syllabusTopicId, "Syllabus topic id");
    requireText(syllabusTopicCode, "Syllabus topic code");
    requireText(modelAnswerSnapshot, "Model answer snapshot");
    normalizeMaximum(maxMarks);
    if (!studentId.equals(submissionDocument.getStudentId())) {
      throw new IllegalStateException("Answer student must match its document");
    }
    if (!worksheetId.equals(submissionDocument.getWorksheetId())) {
      throw new IllegalStateException("Answer worksheet must match its document");
    }
  }

  private void validateReviewState() {
    if (reviewStatus == null) {
      throw new IllegalStateException("Review status is required");
    }
    if (aiSuggestedMarks != null) {
      normalizeScore(aiSuggestedMarks, "AI suggested marks");
    }
    switch (reviewStatus) {
      case DRAFT, PENDING_REVIEW -> {
        if (approvedMarks != null
            || approvedFeedback != null
            || reviewedByUserId != null
            || reviewedAt != null) {
          throw new IllegalStateException("Pending answers cannot contain final results");
        }
      }
      case FLAGGED -> {
        if (approvedMarks != null
            || approvedFeedback != null
            || reviewedByUserId == null
            || reviewedAt == null) {
          throw new IllegalStateException("Flagged answer review metadata is incomplete");
        }
      }
      case APPROVED -> {
        normalizeScore(approvedMarks, "Approved marks");
        requireText(approvedFeedback, "Approved feedback");
        requirePositive(reviewedByUserId, "Reviewer user id");
        if (reviewedAt == null) {
          throw new IllegalStateException("Approved answer requires a review time");
        }
      }
    }
  }

  private boolean isUnreviewed() {
    return reviewStatus == ReviewStatus.DRAFT || reviewStatus == ReviewStatus.PENDING_REVIEW;
  }

  private BigDecimal normalizeScore(BigDecimal marks, String fieldName) {
    if (marks == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    BigDecimal normalized = marks.setScale(2);
    if (normalized.signum() < 0 || normalized.compareTo(normalizeMaximum(maxMarks)) > 0) {
      throw new IllegalArgumentException(fieldName + " must be between zero and max marks");
    }
    return normalized;
  }

  private static BigDecimal normalizeMaximum(BigDecimal marks) {
    if (marks == null || marks.signum() <= 0) {
      throw new IllegalArgumentException("Max marks must be positive");
    }
    return marks.setScale(2);
  }

  private static Set<String> normalizeKeywords(List<String> keywords) {
    Set<String> normalized = new LinkedHashSet<>();
    if (keywords == null) {
      return normalized;
    }
    for (String keyword : keywords) {
      if (keyword != null && !keyword.isBlank()) {
        normalized.add(keyword.trim().toLowerCase(java.util.Locale.ROOT));
      }
    }
    return normalized;
  }

  private static String normalizeAnswer(String value) {
    return value == null ? "" : value.trim();
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value.trim();
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  public Long getId() {
    return id;
  }

  public SubmissionDocument getSubmissionDocument() {
    return submissionDocument;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getWorksheetId() {
    return worksheetId;
  }

  public Long getWorksheetQuestionId() {
    return worksheetQuestionId;
  }

  public Long getQuestionBankId() {
    return questionBankId;
  }

  public Long getSyllabusTopicId() {
    return syllabusTopicId;
  }

  public String getSyllabusTopicCode() {
    return syllabusTopicCode;
  }

  public String getExtractedAnswer() {
    return extractedAnswer;
  }

  public String getModelAnswerSnapshot() {
    return modelAnswerSnapshot;
  }

  public BigDecimal getMaxMarks() {
    return maxMarks;
  }

  public BigDecimal getAiSuggestedMarks() {
    return aiSuggestedMarks;
  }

  public String getAiSuggestedOutcome() {
    return aiSuggestedOutcome;
  }

  public String getAiErrorCategory() {
    return aiErrorCategory;
  }

  public String getAiSuggestedFeedback() {
    return aiSuggestedFeedback;
  }

  public ReviewStatus getReviewStatus() {
    return reviewStatus;
  }

  public BigDecimal getApprovedMarks() {
    return approvedMarks;
  }

  public String getApprovedFeedback() {
    return approvedFeedback;
  }

  public Long getReviewedByUserId() {
    return reviewedByUserId;
  }

  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  public List<AnswerReview> getReviews() {
    return reviews.stream()
        .sorted(
            Comparator.comparing(
                    AnswerReview::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    AnswerReview::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  public List<MistakeRecord> getMistakes() {
    return mistakes.stream()
        .sorted(
            Comparator.comparing(
                    MistakeRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    MistakeRecord::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  public List<ApprovedDiagnosticEvidence> getApprovedDiagnosticEvidence() {
    return approvedDiagnosticEvidence.stream()
        .sorted(Comparator.comparing(ApprovedDiagnosticEvidence::getPosition))
        .toList();
  }

  public long getMasterySyncRevision() {
    return masterySyncRevision;
  }

  public List<String> getMissingKeywords() {
    return missingKeywords.stream().sorted().toList();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  // Compatibility accessors keep the existing prototype AI endpoint pending-only.
  public void setStudentId(Long studentId) {
    this.studentId = requirePositive(studentId, "Student id");
  }

  public String getQuestionId() {
    return legacyQuestionReference != null
        ? legacyQuestionReference
        : questionBankId == null ? null : questionBankId.toString();
  }

  public void setQuestionId(String questionId) {
    this.legacyQuestionReference = requireText(questionId, "Question reference");
    this.legacyRecord = true;
  }

  public String getStudentAnswer() {
    return extractedAnswer;
  }

  public void setStudentAnswer(String studentAnswer) {
    this.extractedAnswer = normalizeAnswer(studentAnswer);
  }

  public String getCorrectness() {
    return aiSuggestedOutcome;
  }

  public void setCorrectness(String correctness) {
    this.aiSuggestedOutcome = requireText(correctness, "AI suggested outcome");
  }

  public String getErrorCategory() {
    return aiErrorCategory;
  }

  public void setErrorCategory(String errorCategory) {
    this.aiErrorCategory = normalizeOptional(errorCategory);
  }

  public String getFeedback() {
    return aiSuggestedFeedback;
  }

  public void setFeedback(String feedback) {
    this.aiSuggestedFeedback = requireText(feedback, "AI suggested feedback");
  }

  public void setMissingKeywords(List<String> missingKeywords) {
    this.missingKeywords = normalizeKeywords(missingKeywords);
  }

  private void setSyllabusTopicSnapshot(Long topicId, String topicCode) {
    this.syllabusTopicId = requirePositive(topicId, "Syllabus topic id");
    this.syllabusTopicCode = requireText(topicCode, "Syllabus topic code");
  }

  public record DiagnosticEvidenceInput(
      Long syllabusTopicId,
      MistakeType mistakeType,
      String description,
      List<String> missingKeywords) {}
}
