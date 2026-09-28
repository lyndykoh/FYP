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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "answer_reviews")
public class AnswerReview {

  public enum Action {
    APPROVED,
    REVISED,
    FLAGGED,
    RESET_TO_AI
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Action action;

  @Column(name = "reviewer_user_id", nullable = false)
  private Long reviewerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", nullable = false, length = 24)
  private Submission.ReviewStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 24)
  private Submission.ReviewStatus newStatus;

  @Column(name = "previous_marks", precision = 6, scale = 2)
  private BigDecimal previousMarks;

  @Column(name = "new_marks", precision = 6, scale = 2)
  private BigDecimal newMarks;

  @Column(name = "previous_feedback", columnDefinition = "TEXT")
  private String previousFeedback;

  @Column(name = "new_feedback", columnDefinition = "TEXT")
  private String newFeedback;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected AnswerReview() {}

  AnswerReview(
      Submission submission,
      Action action,
      Long reviewerUserId,
      Submission.ReviewStatus previousStatus,
      Submission.ReviewStatus newStatus,
      BigDecimal previousMarks,
      BigDecimal newMarks,
      String previousFeedback,
      String newFeedback) {
    this.submission = requireValue(submission, "Submission");
    this.action = requireValue(action, "Review action");
    this.reviewerUserId = requirePositive(reviewerUserId, "Reviewer user id");
    this.previousStatus = requireValue(previousStatus, "Previous status");
    this.newStatus = requireValue(newStatus, "New status");
    this.previousMarks = normalizeMarks(previousMarks);
    this.newMarks = normalizeMarks(newMarks);
    this.previousFeedback = normalizeOptional(previousFeedback);
    this.newFeedback = normalizeOptional(newFeedback);
  }

  @PrePersist
  protected void beforeInsert() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    requireValue(submission, "Submission");
    requireValue(action, "Review action");
    requirePositive(reviewerUserId, "Reviewer user id");
    requireValue(previousStatus, "Previous status");
    requireValue(newStatus, "New status");
  }

  private static BigDecimal normalizeMarks(BigDecimal marks) {
    if (marks == null) {
      return null;
    }
    if (marks.signum() < 0) {
      throw new IllegalArgumentException("Review marks cannot be negative");
    }
    return marks.setScale(2);
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  private static <T> T requireValue(T value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value;
  }

  public Long getId() {
    return id;
  }

  public Action getAction() {
    return action;
  }

  public Long getReviewerUserId() {
    return reviewerUserId;
  }

  public Submission.ReviewStatus getPreviousStatus() {
    return previousStatus;
  }

  public Submission.ReviewStatus getNewStatus() {
    return newStatus;
  }

  public BigDecimal getPreviousMarks() {
    return previousMarks;
  }

  public BigDecimal getNewMarks() {
    return newMarks;
  }

  public String getPreviousFeedback() {
    return previousFeedback;
  }

  public String getNewFeedback() {
    return newFeedback;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
