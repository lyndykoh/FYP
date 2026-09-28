package com.fttranscendence.learning.insight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Read model for persisted aggregate evidence. Mutations stay in ClassInsightService. */
@Entity
@Table(name = "class_insight_snapshots")
public class ClassInsightSnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "class_id", nullable = false)
  private Long classId;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "data_as_of", nullable = false)
  private LocalDateTime dataAsOf;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "failure_message", length = 500)
  private String failureMessage;

  protected ClassInsightSnapshot() {}

  public Long getId() {
    return id;
  }

  public Long getClassId() {
    return classId;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getDataAsOf() {
    return dataAsOf;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
