package com.fttranscendence.learning.mastery;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(
    name = "mastery_records",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_mastery_records_student_topic",
            columnNames = {"student_profile_id", "syllabus_topic_id"}))
public class MasteryRecord {

  public enum MasteryStatus {
    NOT_STARTED,
    LEARNING,
    PRACTISING,
    IMPROVING,
    MASTERED,
    NEEDS_REVISION
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "syllabus_topic_id", nullable = false)
  private SyllabusTopic syllabusTopic;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal score = BigDecimal.ZERO.setScale(2);

  @Enumerated(EnumType.STRING)
  @Column(name = "mastery_status", nullable = false, length = 16)
  private MasteryStatus masteryStatus = MasteryStatus.NOT_STARTED;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_source_submission_id")
  private Long lastSourceSubmissionId;

  @Column(name = "calculated_at")
  private LocalDateTime calculatedAt;

  @OneToMany(
      mappedBy = "masteryRecord",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("createdAt ASC, id ASC")
  private List<MasteryHistory> history = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected MasteryRecord() {}

  public MasteryRecord(StudentProfile studentProfile, SyllabusTopic syllabusTopic) {
    this.studentProfile = requireValue(studentProfile, "Student profile");
    this.syllabusTopic = requireValue(syllabusTopic, "Syllabus topic");
  }

  public void updateScore(BigDecimal newScore, Long sourceSubmissionId, String reason) {
    BigDecimal normalizedScore = normalizeScore(newScore);
    Long source = requirePositive(sourceSubmissionId, "Source submission id");
    String normalizedReason = requireText(reason, "Mastery update reason");
    MasteryStatus nextStatus = statusFor(normalizedScore, attemptCount + 1);
    history.add(
        new MasteryHistory(
            this, score, normalizedScore, masteryStatus, nextStatus, source, normalizedReason));
    score = normalizedScore;
    masteryStatus = nextStatus;
    attemptCount++;
    lastSourceSubmissionId = source;
    calculatedAt = LocalDateTime.now();
  }

  public void markNeedsRevision(Long sourceSubmissionId, String reason) {
    Long source = requirePositive(sourceSubmissionId, "Source submission id");
    String normalizedReason = requireText(reason, "Mastery revision reason");
    history.add(
        new MasteryHistory(
            this,
            score,
            score,
            masteryStatus,
            MasteryStatus.NEEDS_REVISION,
            source,
            normalizedReason));
    masteryStatus = MasteryStatus.NEEDS_REVISION;
    lastSourceSubmissionId = source;
    calculatedAt = LocalDateTime.now();
  }

  /** Rebuilds this derived record from the immutable approved-result projection. */
  public void replaceApprovedAttempts(List<ApprovedAttempt> attempts) {
    history.clear();
    score = BigDecimal.ZERO.setScale(2);
    masteryStatus = MasteryStatus.NOT_STARTED;
    attemptCount = 0;
    lastSourceSubmissionId = null;
    calculatedAt = null;
    for (ApprovedAttempt attempt : attempts) {
      updateScore(attempt.score(), attempt.sourceSubmissionId(), attempt.reason());
    }
  }

  public record ApprovedAttempt(BigDecimal score, Long sourceSubmissionId, String reason) {}

  @PrePersist
  void prepareForInsert() {
    validateAggregate();
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    validateAggregate();
    updatedAt = LocalDateTime.now();
  }

  private void validateAggregate() {
    if (studentProfile == null || studentProfile.getId() == null) {
      throw new IllegalStateException("Mastery requires a persisted student profile");
    }
    if (syllabusTopic == null || syllabusTopic.getId() == null || !syllabusTopic.isActive()) {
      throw new IllegalStateException("Mastery requires an active syllabus topic");
    }
    score = normalizeScore(score);
    if (masteryStatus == null) {
      throw new IllegalStateException("Mastery status is required");
    }
    if (attemptCount < 0) {
      throw new IllegalStateException("Attempt count cannot be negative");
    }
    if (lastSourceSubmissionId != null) {
      requirePositive(lastSourceSubmissionId, "Source submission id");
    }
  }

  private static MasteryStatus statusFor(BigDecimal value, int attempts) {
    if (attempts <= 0) return MasteryStatus.NOT_STARTED;
    if (value.signum() == 0) return MasteryStatus.NEEDS_REVISION;
    if (value.compareTo(new BigDecimal("50.00")) < 0) return MasteryStatus.LEARNING;
    if (value.compareTo(new BigDecimal("70.00")) < 0) return MasteryStatus.PRACTISING;
    if (value.compareTo(new BigDecimal("85.00")) < 0) return MasteryStatus.IMPROVING;
    return MasteryStatus.MASTERED;
  }

  private static BigDecimal normalizeScore(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException("Mastery score is required");
    }
    BigDecimal normalized = value.setScale(2);
    if (normalized.signum() < 0 || normalized.compareTo(new BigDecimal("100.00")) > 0) {
      throw new IllegalArgumentException("Mastery score must be between 0 and 100");
    }
    return normalized;
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0)
      throw new IllegalArgumentException(fieldName + " must be positive");
    return value;
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(fieldName + " is required");
    return value.trim();
  }

  private static <T> T requireValue(T value, String fieldName) {
    if (value == null) throw new IllegalArgumentException(fieldName + " is required");
    return value;
  }

  public Long getId() {
    return id;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public SyllabusTopic getSyllabusTopic() {
    return syllabusTopic;
  }

  public BigDecimal getScore() {
    return score;
  }

  public MasteryStatus getMasteryStatus() {
    return masteryStatus;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Long getLastSourceSubmissionId() {
    return lastSourceSubmissionId;
  }

  public LocalDateTime getCalculatedAt() {
    return calculatedAt;
  }

  public List<MasteryHistory> getHistory() {
    return history.stream()
        .sorted(
            Comparator.comparing(
                    MasteryHistory::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(
                    MasteryHistory::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
