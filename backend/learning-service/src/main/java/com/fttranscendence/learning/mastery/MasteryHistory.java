package com.fttranscendence.learning.mastery;

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
@Table(name = "mastery_history")
public class MasteryHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mastery_record_id", nullable = false)
  private MasteryRecord masteryRecord;

  @Column(name = "previous_score", nullable = false, precision = 5, scale = 2)
  private BigDecimal previousScore;

  @Column(name = "new_score", nullable = false, precision = 5, scale = 2)
  private BigDecimal newScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", nullable = false, length = 16)
  private MasteryRecord.MasteryStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 16)
  private MasteryRecord.MasteryStatus newStatus;

  @Column(name = "source_submission_id", nullable = false)
  private Long sourceSubmissionId;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected MasteryHistory() {}

  MasteryHistory(
      MasteryRecord masteryRecord,
      BigDecimal previousScore,
      BigDecimal newScore,
      MasteryRecord.MasteryStatus previousStatus,
      MasteryRecord.MasteryStatus newStatus,
      Long sourceSubmissionId,
      String reason) {
    this.masteryRecord = masteryRecord;
    this.previousScore = previousScore;
    this.newScore = newScore;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.sourceSubmissionId = sourceSubmissionId;
    this.reason = reason;
  }

  @PrePersist
  void prepareForInsert() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public Long getId() {
    return id;
  }

  public BigDecimal getPreviousScore() {
    return previousScore;
  }

  public BigDecimal getNewScore() {
    return newScore;
  }

  public MasteryRecord.MasteryStatus getPreviousStatus() {
    return previousStatus;
  }

  public MasteryRecord.MasteryStatus getNewStatus() {
    return newStatus;
  }

  public Long getSourceSubmissionId() {
    return sourceSubmissionId;
  }

  public String getReason() {
    return reason;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
