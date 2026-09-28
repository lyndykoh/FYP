package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.student.StudentProfile;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "tutor_alerts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_tutor_alerts_deduplication",
            columnNames = {"tutor_id", "deduplication_key"}))
public class TutorAlert {

  public enum AlertType {
    WEAK_TOPIC,
    REPEATED_MISTAKE,
    PENDING_REVIEW,
    REPORT_READY
  }

  public enum Severity {
    INFO,
    WARNING,
    CRITICAL
  }

  public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mastery_record_id")
  private MasteryRecord masteryRecord;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_type", nullable = false, length = 32)
  private AlertType alertType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_status", nullable = false, length = 16)
  private AlertStatus alertStatus = AlertStatus.OPEN;

  @Column(name = "deduplication_key", nullable = false, length = 200)
  private String deduplicationKey;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(name = "acknowledged_at")
  private LocalDateTime acknowledgedAt;

  @Column(name = "resolved_at")
  private LocalDateTime resolvedAt;

  @Column(name = "resolved_by_user_id")
  private Long resolvedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected TutorAlert() {}

  public TutorAlert(
      Long tutorId,
      StudentProfile studentProfile,
      MasteryRecord masteryRecord,
      AlertType alertType,
      Severity severity,
      String deduplicationKey,
      String title,
      String message) {
    this.tutorId = requirePositive(tutorId, "Tutor id");
    this.studentProfile = requireValue(studentProfile, "Student profile");
    if (studentProfile.getTutorId() == null || !this.tutorId.equals(studentProfile.getTutorId())) {
      throw new IllegalArgumentException("Alert tutor must own the student profile");
    }
    this.masteryRecord = masteryRecord;
    this.alertType = requireValue(alertType, "Alert type");
    this.severity = requireValue(severity, "Alert severity");
    this.deduplicationKey = requireText(deduplicationKey, "Deduplication key");
    this.title = requireText(title, "Alert title");
    this.message = requireText(message, "Alert message");
  }

  public void acknowledge() {
    if (alertStatus != AlertStatus.OPEN)
      throw new IllegalStateException("Only open alerts can be acknowledged");
    alertStatus = AlertStatus.ACKNOWLEDGED;
    acknowledgedAt = LocalDateTime.now();
  }

  public void resolve(Long resolverUserId) {
    if (alertStatus != AlertStatus.OPEN && alertStatus != AlertStatus.ACKNOWLEDGED) {
      throw new IllegalStateException("Only active alerts can be resolved");
    }
    alertStatus = AlertStatus.RESOLVED;
    resolvedAt = LocalDateTime.now();
    resolvedByUserId = requirePositive(resolverUserId, "Resolver user id");
  }

  public void dismiss(Long resolverUserId) {
    if (alertStatus != AlertStatus.OPEN && alertStatus != AlertStatus.ACKNOWLEDGED) {
      throw new IllegalStateException("Only active alerts can be dismissed");
    }
    alertStatus = AlertStatus.DISMISSED;
    resolvedAt = LocalDateTime.now();
    resolvedByUserId = requirePositive(resolverUserId, "Resolver user id");
  }

  @PrePersist
  void prepareForInsert() {
    validateAggregate();
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    validateAggregate();
    updatedAt = LocalDateTime.now();
  }

  private void validateAggregate() {
    requirePositive(tutorId, "Tutor id");
    requireValue(studentProfile, "Student profile");
    if (studentProfile.getTutorId() == null || !tutorId.equals(studentProfile.getTutorId())) {
      throw new IllegalStateException("Alert tutor must own the student profile");
    }
    requireValue(alertType, "Alert type");
    requireValue(severity, "Alert severity");
    requireValue(alertStatus, "Alert status");
    requireText(deduplicationKey, "Deduplication key");
    requireText(title, "Alert title");
    requireText(message, "Alert message");
    if (alertStatus == AlertStatus.OPEN) {
      if (acknowledgedAt != null || resolvedAt != null || resolvedByUserId != null) {
        throw new IllegalStateException("Open alerts cannot have lifecycle timestamps");
      }
    } else if (alertStatus == AlertStatus.ACKNOWLEDGED) {
      if (acknowledgedAt == null || resolvedAt != null || resolvedByUserId != null) {
        throw new IllegalStateException("Acknowledged alert lifecycle is incomplete");
      }
    } else {
      if (resolvedAt == null || resolvedByUserId == null) {
        throw new IllegalStateException("Resolved alert lifecycle is incomplete");
      }
    }
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

  public Long getTutorId() {
    return tutorId;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public MasteryRecord getMasteryRecord() {
    return masteryRecord;
  }

  public AlertType getAlertType() {
    return alertType;
  }

  public Severity getSeverity() {
    return severity;
  }

  public AlertStatus getAlertStatus() {
    return alertStatus;
  }

  public String getDeduplicationKey() {
    return deduplicationKey;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public LocalDateTime getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  public Long getResolvedByUserId() {
    return resolvedByUserId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
