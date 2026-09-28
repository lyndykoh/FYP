package com.fttranscendence.learning.question.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_import_batches")
public class QuestionImportBatch {
  public enum Status {
    QUEUED,
    RUNNING,
    READY_FOR_REVIEW,
    FAILED,
    IMPORTED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  /** The tutor who owns the retained source material and its review drafts. */
  @Column(name = "created_by_tutor_id")
  private Long createdByTutorId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "processing_started_at")
  private LocalDateTime processingStartedAt;

  @Column(name = "next_attempt_at")
  private LocalDateTime nextAttemptAt;

  @Column(name = "last_processing_error", length = 1000)
  private String lastProcessingError;

  protected QuestionImportBatch() {}

  QuestionImportBatch(String originalFilename, long createdByTutorId, Status status) {
    this.originalFilename = originalFilename;
    this.createdByTutorId = createdByTutorId;
    this.status = status;
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

  public String getOriginalFilename() {
    return originalFilename;
  }

  public Long getCreatedByTutorId() {
    return createdByTutorId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  void running() {
    status = Status.RUNNING;
    attemptCount++;
    processingStartedAt = LocalDateTime.now();
    nextAttemptAt = null;
    lastProcessingError = null;
  }

  void retryAt(LocalDateTime nextAttempt, String error) {
    status = Status.QUEUED;
    nextAttemptAt = nextAttempt;
    lastProcessingError = error;
  }

  void failed(String error) {
    status = Status.FAILED;
    lastProcessingError = error;
  }

  void readyForReview() {
    status = Status.READY_FOR_REVIEW;
    lastProcessingError = null;
  }

  void readyForManualReview(String error) {
    status = Status.READY_FOR_REVIEW;
    lastProcessingError = error;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getLastProcessingError() {
    return lastProcessingError;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
