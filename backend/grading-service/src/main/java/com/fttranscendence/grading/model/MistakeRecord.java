package com.fttranscendence.grading.model;

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
import java.time.LocalDateTime;

/** A durable, controlled learning-history entry for one submitted answer. */
@Entity
@Table(name = "mistake_records")
public class MistakeRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "worksheet_id", nullable = false)
  private Long worksheetId;

  @Column(name = "worksheet_question_id", nullable = false)
  private Long worksheetQuestionId;

  @Column(name = "question_bank_id", nullable = false)
  private Long questionBankId;

  @Column(name = "syllabus_topic_id")
  private Long syllabusTopicId;

  @Column(name = "syllabus_topic_code", length = 120)
  private String syllabusTopicCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "mistake_type", nullable = false, length = 40)
  private MistakeType mistakeType;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @Column(name = "tutor_note", columnDefinition = "TEXT")
  private String tutorNote;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected MistakeRecord() {}

  private MistakeRecord(
      Submission submission,
      MistakeType mistakeType,
      Long syllabusTopicId,
      String syllabusTopicCode,
      String description) {
    if (submission == null) {
      throw new IllegalArgumentException("Submission is required");
    }
    if (submission.getSubmissionDocument() == null) {
      throw new IllegalArgumentException("A canonical submission is required");
    }
    if (submission.getReviewStatus() != Submission.ReviewStatus.APPROVED) {
      throw new IllegalStateException("Mistakes can only be recorded after tutor approval");
    }
    this.submission = submission;
    this.studentId = requirePositive(submission.getStudentId(), "Student id");
    this.worksheetId = requirePositive(submission.getWorksheetId(), "Worksheet id");
    this.worksheetQuestionId =
        requirePositive(submission.getWorksheetQuestionId(), "Worksheet question id");
    this.questionBankId = requirePositive(submission.getQuestionBankId(), "Question bank id");
    this.mistakeType = requireValue(mistakeType, "Mistake type");
    setTopic(syllabusTopicId, syllabusTopicCode);
    this.description = requireText(description, "Mistake description");
  }

  public static MistakeRecord create(
      Submission submission,
      MistakeType mistakeType,
      Long syllabusTopicId,
      String syllabusTopicCode,
      String description) {
    return new MistakeRecord(
        submission, mistakeType, syllabusTopicId, syllabusTopicCode, description);
  }

  public void setTutorNote(String tutorNote) {
    this.tutorNote = normalizeOptional(tutorNote);
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
    if (submission == null || submission.getSubmissionDocument() == null) {
      throw new IllegalStateException("Mistake must belong to a canonical submission");
    }
    if (submission.getReviewStatus() != Submission.ReviewStatus.APPROVED) {
      throw new IllegalStateException("Mistakes can only be recorded after tutor approval");
    }
    if (!equals(studentId, submission.getStudentId())
        || !equals(worksheetId, submission.getWorksheetId())
        || !equals(worksheetQuestionId, submission.getWorksheetQuestionId())
        || !equals(questionBankId, submission.getQuestionBankId())) {
      throw new IllegalStateException("Mistake provenance does not match its answer");
    }
    requirePositive(studentId, "Student id");
    requirePositive(worksheetId, "Worksheet id");
    requirePositive(worksheetQuestionId, "Worksheet question id");
    requirePositive(questionBankId, "Question bank id");
    requireValue(mistakeType, "Mistake type");
    validateTopic();
    requireText(description, "Mistake description");
  }

  private void setTopic(Long topicId, String topicCode) {
    if (topicId == null && (topicCode == null || topicCode.isBlank())) {
      syllabusTopicId = null;
      syllabusTopicCode = null;
      return;
    }
    syllabusTopicId = requirePositive(topicId, "Syllabus topic id");
    syllabusTopicCode = requireText(topicCode, "Syllabus topic code");
  }

  private void validateTopic() {
    if (syllabusTopicId == null && syllabusTopicCode == null) {
      return;
    }
    requirePositive(syllabusTopicId, "Syllabus topic id");
    requireText(syllabusTopicCode, "Syllabus topic code");
  }

  private static boolean equals(Object left, Object right) {
    return left != null && left.equals(right);
  }

  private static <T> T requireValue(T value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value;
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public Long getId() {
    return id;
  }

  public Submission getSubmission() {
    return submission;
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

  public MistakeType getMistakeType() {
    return mistakeType;
  }

  public String getDescription() {
    return description;
  }

  public String getTutorNote() {
    return tutorNote;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
