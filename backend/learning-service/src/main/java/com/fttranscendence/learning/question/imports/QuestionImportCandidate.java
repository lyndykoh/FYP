package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_import_candidates")
public class QuestionImportCandidate {
  public enum Status {
    READY_FOR_REVIEW,
    UNCERTAIN,
    FAILED,
    IMPORTED,
    REJECTED,
    SUPERSEDED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private QuestionImportBatch batch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_page_id", nullable = false)
  private QuestionImportSourcePage sourcePage;

  @Column(name = "candidate_number", nullable = false)
  private int candidateNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(nullable = false)
  private int confidence;

  @Column(name = "warning_message", length = 1000)
  private String warningMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "suggested_question_type", nullable = false, length = 32)
  private Question.QuestionType suggestedQuestionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "suggested_difficulty", nullable = false, length = 16)
  private Question.Difficulty suggestedDifficulty;

  @Column(name = "suggested_tags", nullable = false, length = 1000)
  private String suggestedTags;

  @Column(name = "suggested_prompt", nullable = false, length = 4000)
  private String suggestedPrompt;

  @Column(name = "suggested_model_answer", nullable = false, length = 4000)
  private String suggestedModelAnswer;

  @Column(name = "suggested_total_marks", nullable = false, precision = 6, scale = 2)
  private BigDecimal suggestedTotalMarks;

  @Enumerated(EnumType.STRING)
  @Column(name = "reviewed_question_type", nullable = false, length = 32)
  private Question.QuestionType reviewedQuestionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "reviewed_difficulty", nullable = false, length = 16)
  private Question.Difficulty reviewedDifficulty;

  @Column(name = "prompt_confidence")
  private Integer promptConfidence;

  @Column(name = "model_answer_confidence")
  private Integer modelAnswerConfidence;

  @Column(name = "classification_confidence")
  private Integer classificationConfidence;

  @Column(name = "marks_confidence")
  private Integer marksConfidence;

  @Column(name = "question_code", length = 120)
  private String questionCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "syllabus_topic_id")
  private SyllabusTopic syllabusTopic;

  @Column(nullable = false, length = 4000)
  private String prompt;

  @Column(name = "model_answer", nullable = false, length = 4000)
  private String modelAnswer;

  @Column(name = "total_marks", nullable = false, precision = 6, scale = 2)
  private BigDecimal totalMarks;

  @Column(name = "include_source_image", nullable = false)
  private boolean includeSourceImage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "imported_question_id")
  private Question importedQuestion;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_candidate_id")
  private QuestionImportCandidate parentCandidate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "superseded_by_candidate_id")
  private QuestionImportCandidate supersededByCandidate;

  @Enumerated(EnumType.STRING)
  @Column(name = "rejected_from_status", length = 24)
  private Status rejectedFromStatus;

  @Column(name = "rejected_reason", length = 500)
  private String rejectedReason;

  @Column(name = "rejected_at")
  private LocalDateTime rejectedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected QuestionImportCandidate() {}

  QuestionImportCandidate(
      QuestionImportBatch batch,
      QuestionImportSourcePage sourcePage,
      int number,
      Status status,
      int confidence,
      String warning,
      Question.QuestionType type,
      Question.Difficulty difficulty,
      String tags,
      String prompt,
      String answer) {
    this.batch = batch;
    this.sourcePage = sourcePage;
    candidateNumber = number;
    this.status = status;
    this.confidence = confidence;
    warningMessage = warning;
    suggestedQuestionType = type;
    suggestedDifficulty = difficulty;
    suggestedTags = tags;
    suggestedPrompt = prompt;
    suggestedModelAnswer = answer;
    suggestedTotalMarks = BigDecimal.ONE;
    this.prompt = prompt;
    modelAnswer = answer;
    totalMarks = BigDecimal.ONE;
    reviewedQuestionType = type;
    reviewedDifficulty = difficulty;
    includeSourceImage = true;
  }

  @PrePersist
  void created() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updated() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public QuestionImportBatch getBatch() {
    return batch;
  }

  public QuestionImportSourcePage getSourcePage() {
    return sourcePage;
  }

  public int getCandidateNumber() {
    return candidateNumber;
  }

  public Status getStatus() {
    return status;
  }

  public int getConfidence() {
    return confidence;
  }

  public String getWarningMessage() {
    return warningMessage;
  }

  public Question.QuestionType getSuggestedQuestionType() {
    return suggestedQuestionType;
  }

  public Question.Difficulty getSuggestedDifficulty() {
    return suggestedDifficulty;
  }

  public String getSuggestedTags() {
    return suggestedTags;
  }

  public String getQuestionCode() {
    return questionCode;
  }

  public String getSuggestedPrompt() {
    return suggestedPrompt;
  }

  public String getSuggestedModelAnswer() {
    return suggestedModelAnswer;
  }

  public BigDecimal getSuggestedTotalMarks() {
    return suggestedTotalMarks;
  }

  public Question.QuestionType getReviewedQuestionType() {
    return reviewedQuestionType;
  }

  public Question.Difficulty getReviewedDifficulty() {
    return reviewedDifficulty;
  }

  public Integer getPromptConfidence() {
    return promptConfidence;
  }

  public Integer getModelAnswerConfidence() {
    return modelAnswerConfidence;
  }

  public Integer getClassificationConfidence() {
    return classificationConfidence;
  }

  public Integer getMarksConfidence() {
    return marksConfidence;
  }

  public SyllabusTopic getSyllabusTopic() {
    return syllabusTopic;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getModelAnswer() {
    return modelAnswer;
  }

  public BigDecimal getTotalMarks() {
    return totalMarks;
  }

  public boolean isIncludeSourceImage() {
    return includeSourceImage;
  }

  public Question getImportedQuestion() {
    return importedQuestion;
  }

  public QuestionImportCandidate getParentCandidate() {
    return parentCandidate;
  }

  public QuestionImportCandidate getSupersededByCandidate() {
    return supersededByCandidate;
  }

  public String getRejectedReason() {
    return rejectedReason;
  }

  public LocalDateTime getRejectedAt() {
    return rejectedAt;
  }

  void revise(
      String code,
      SyllabusTopic topic,
      String prompt,
      String answer,
      BigDecimal marks,
      Question.QuestionType type,
      Question.Difficulty difficulty,
      boolean includeImage) {
    questionCode = code;
    syllabusTopic = topic;
    this.prompt = prompt;
    modelAnswer = answer;
    totalMarks = marks;
    reviewedQuestionType = type;
    reviewedDifficulty = difficulty;
    includeSourceImage = includeImage;
  }

  void imported(Question question) {
    status = Status.IMPORTED;
    importedQuestion = question;
  }

  boolean isReviewable() {
    return status == Status.READY_FOR_REVIEW || status == Status.UNCERTAIN;
  }

  void reject(String reason) {
    if (!isReviewable()) throw new IllegalStateException("Only reviewable drafts can be rejected.");
    rejectedFromStatus = status;
    status = Status.REJECTED;
    rejectedReason = reason;
    rejectedAt = LocalDateTime.now();
  }

  void restore() {
    if (status != Status.REJECTED || rejectedFromStatus == null)
      throw new IllegalStateException("Only rejected drafts can be restored.");
    status = rejectedFromStatus;
    rejectedFromStatus = null;
    rejectedReason = null;
    rejectedAt = null;
  }

  void supersede(QuestionImportCandidate replacement) {
    if (!isReviewable())
      throw new IllegalStateException("Only reviewable drafts can be superseded.");
    status = Status.SUPERSEDED;
    supersededByCandidate = replacement;
  }

  static QuestionImportCandidate splitFrom(
      QuestionImportCandidate source, int number, String prompt, String answer) {
    QuestionImportCandidate split =
        new QuestionImportCandidate(
            source.batch,
            source.sourcePage,
            number,
            source.status,
            source.confidence,
            source.warningMessage,
            source.suggestedQuestionType,
            source.suggestedDifficulty,
            source.suggestedTags,
            source.suggestedPrompt,
            source.suggestedModelAnswer);
    split.parentCandidate = source;
    split.revise(
        source.questionCode,
        source.syllabusTopic,
        prompt,
        answer,
        source.totalMarks,
        source.reviewedQuestionType,
        source.reviewedDifficulty,
        source.includeSourceImage);
    return split;
  }
}
