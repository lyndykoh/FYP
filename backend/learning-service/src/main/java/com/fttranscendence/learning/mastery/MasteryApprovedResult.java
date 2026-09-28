package com.fttranscendence.learning.mastery;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Idempotent local projection of one Tutor-approved marking decision. */
@Entity
@Table(name = "mastery_approved_results")
public class MasteryApprovedResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "source_submission_id", nullable = false, unique = true)
  private Long sourceSubmissionId;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  /**
   * Worksheet provenance is retained locally so learner-facing results never query grading data.
   */
  @Column(name = "worksheet_id")
  private Long worksheetId;

  @Column(name = "worksheet_question_id")
  private Long worksheetQuestionId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_topic_id", nullable = false)
  private SyllabusTopic syllabusTopic;

  @Column(name = "approved_marks", nullable = false, precision = 6, scale = 2)
  private BigDecimal approvedMarks;

  @Column(name = "available_marks", nullable = false, precision = 6, scale = 2)
  private BigDecimal availableMarks;

  @Column(name = "repeated_mistake_count", nullable = false)
  private int repeatedMistakeCount;

  @Column(nullable = false)
  private int revision;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "reviewed_at", nullable = false)
  private LocalDateTime reviewedAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected MasteryApprovedResult() {}

  public MasteryApprovedResult(
      long sourceSubmissionId,
      long tutorId,
      Long worksheetId,
      Long worksheetQuestionId,
      StudentProfile student,
      SyllabusTopic topic,
      BigDecimal approvedMarks,
      BigDecimal availableMarks,
      int repeatedMistakeCount,
      int revision,
      boolean active,
      LocalDateTime reviewedAt) {
    this.sourceSubmissionId = positive(sourceSubmissionId, "Source submission id");
    this.tutorId = positive(tutorId, "Tutor id");
    this.worksheetId = optionalPositive(worksheetId, "Worksheet id");
    this.worksheetQuestionId = optionalPositive(worksheetQuestionId, "Worksheet question id");
    this.studentProfile = required(student, "Student profile");
    this.syllabusTopic = required(topic, "Syllabus topic");
    replace(
        worksheetId,
        worksheetQuestionId,
        approvedMarks,
        availableMarks,
        repeatedMistakeCount,
        revision,
        active,
        reviewedAt);
  }

  public boolean replace(
      Long nextWorksheetId,
      Long nextWorksheetQuestionId,
      BigDecimal marks,
      BigDecimal total,
      int repeated,
      int nextRevision,
      boolean nextActive,
      LocalDateTime at) {
    validateScore(marks, total);
    optionalPositive(nextWorksheetId, "Worksheet id");
    optionalPositive(nextWorksheetQuestionId, "Worksheet question id");
    if (nextRevision < revision) return false;
    if (nextRevision == revision
        && active == nextActive
        && approvedMarks != null
        && approvedMarks.compareTo(marks) == 0
        && availableMarks.compareTo(total) == 0
        && repeatedMistakeCount == repeated) return false;
    if ((worksheetId != null && !worksheetId.equals(nextWorksheetId))
        || (worksheetQuestionId != null && !worksheetQuestionId.equals(nextWorksheetQuestionId))) {
      throw new IllegalArgumentException("An approved result cannot change worksheet provenance.");
    }
    this.worksheetId = nextWorksheetId;
    this.worksheetQuestionId = nextWorksheetQuestionId;
    this.approvedMarks = marks.setScale(2);
    this.availableMarks = total.setScale(2);
    this.repeatedMistakeCount = nonNegative(repeated);
    this.revision = positiveInt(nextRevision, "Revision");
    this.active = nextActive;
    this.reviewedAt = required(at, "Reviewed at");
    return true;
  }

  /**
   * A retraction retains the immutable approved-mark snapshot for audit and out-of-order event
   * protection, but removes it from mastery derivation.
   */
  public boolean retract(int nextRevision) {
    if (nextRevision < revision || (nextRevision == revision && !active)) return false;
    revision = positiveInt(nextRevision, "Revision");
    active = false;
    return true;
  }

  @PrePersist
  @PreUpdate
  void validate() {
    positive(sourceSubmissionId, "Source submission id");
    positive(tutorId, "Tutor id");
    required(studentProfile, "Student profile");
    if (studentProfile.getTutorId() == null || !studentProfile.getTutorId().equals(tutorId))
      throw new IllegalStateException("Approved result tutor must own the student profile");
    required(syllabusTopic, "Syllabus topic");
    validateScore(approvedMarks, availableMarks);
    nonNegative(repeatedMistakeCount);
    positiveInt(revision, "Revision");
    required(reviewedAt, "Reviewed at");
    updatedAt = LocalDateTime.now();
  }

  private static void validateScore(BigDecimal marks, BigDecimal total) {
    if (marks == null
        || total == null
        || marks.signum() < 0
        || total.signum() <= 0
        || marks.compareTo(total) > 0
        || marks.scale() > 2
        || total.scale() > 2)
      throw new IllegalArgumentException("Approved marks must be between zero and available marks");
  }

  private static long positive(Long value, String field) {
    if (value == null || value <= 0)
      throw new IllegalArgumentException(field + " must be positive");
    return value;
  }

  private static Long optionalPositive(Long value, String field) {
    if (value != null) positive(value, field);
    return value;
  }

  private static int positiveInt(int value, String field) {
    if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
    return value;
  }

  private static int nonNegative(int value) {
    if (value < 0) throw new IllegalArgumentException("Repeated mistake count cannot be negative");
    return value;
  }

  private static <T> T required(T value, String field) {
    if (value == null) throw new IllegalArgumentException(field + " is required");
    return value;
  }

  public Long getId() {
    return id;
  }

  public Long getSourceSubmissionId() {
    return sourceSubmissionId;
  }

  public Long getWorksheetId() {
    return worksheetId;
  }

  public Long getWorksheetQuestionId() {
    return worksheetQuestionId;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public SyllabusTopic getSyllabusTopic() {
    return syllabusTopic;
  }

  public BigDecimal getApprovedMarks() {
    return approvedMarks;
  }

  public BigDecimal getAvailableMarks() {
    return availableMarks;
  }

  public int getRepeatedMistakeCount() {
    return repeatedMistakeCount;
  }

  public int getRevision() {
    return revision;
  }

  public boolean isActive() {
    return active;
  }

  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }
}
