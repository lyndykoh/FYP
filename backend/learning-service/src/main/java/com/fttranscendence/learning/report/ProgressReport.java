package com.fttranscendence.learning.report;

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
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "progress_reports",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_progress_reports_student_code",
            columnNames = {"student_profile_id", "report_code"}))
public class ProgressReport {

  public enum ReportStatus {
    DRAFT,
    FINAL
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @Column(name = "report_code", nullable = false, length = 120)
  private String reportCode;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_status", nullable = false, length = 16)
  private ReportStatus reportStatus = ReportStatus.DRAFT;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String snapshot;

  @Column(name = "generated_at", nullable = false, updatable = false)
  private LocalDateTime generatedAt;

  @Column(name = "finalized_at")
  private LocalDateTime finalizedAt;

  @Column(name = "finalized_by_user_id")
  private Long finalizedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @jakarta.persistence.Transient private ReportStatus loadedStatus;

  protected ProgressReport() {}

  public ProgressReport(
      Long tutorId,
      StudentProfile studentProfile,
      String reportCode,
      LocalDate periodStart,
      LocalDate periodEnd,
      String snapshot) {
    this.tutorId = requirePositive(tutorId, "Tutor id");
    this.studentProfile = requireValue(studentProfile, "Student profile");
    this.reportCode = normalizeCode(reportCode);
    this.periodStart = requireValue(periodStart, "Report period start");
    this.periodEnd = requireValue(periodEnd, "Report period end");
    if (this.periodStart.isAfter(this.periodEnd)) {
      throw new IllegalArgumentException("Report period start must not be after its end");
    }
    this.snapshot = requireText(snapshot, "Report snapshot");
  }

  public void updateSnapshot(String snapshot) {
    ensureDraft();
    this.snapshot = requireText(snapshot, "Report snapshot");
  }

  public void finalizeReport(Long finalizerUserId) {
    ensureDraft();
    finalizedByUserId = requirePositive(finalizerUserId, "Finalizer user id");
    finalizedAt = LocalDateTime.now();
    reportStatus = ReportStatus.FINAL;
  }

  @PostLoad
  void captureLoadedStatus() {
    loadedStatus = reportStatus;
  }

  @PrePersist
  void prepareForInsert() {
    validateAggregate();
    LocalDateTime now = LocalDateTime.now();
    if (generatedAt == null) generatedAt = now;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
    loadedStatus = reportStatus;
  }

  @PreUpdate
  void prepareForUpdate() {
    if (loadedStatus == ReportStatus.FINAL) {
      throw new IllegalStateException("Final progress reports are immutable");
    }
    validateAggregate();
    updatedAt = LocalDateTime.now();
    loadedStatus = reportStatus;
  }

  private void validateAggregate() {
    requirePositive(tutorId, "Tutor id");
    requireValue(studentProfile, "Student profile");
    if (studentProfile.getTutorId() == null || !tutorId.equals(studentProfile.getTutorId())) {
      throw new IllegalStateException("Report tutor must own the student profile");
    }
    reportCode = normalizeCode(reportCode);
    requireValue(periodStart, "Report period start");
    requireValue(periodEnd, "Report period end");
    if (periodStart.isAfter(periodEnd)) {
      throw new IllegalStateException("Report period start must not be after its end");
    }
    requireValue(reportStatus, "Report status");
    requireText(snapshot, "Report snapshot");
    if (reportStatus == ReportStatus.DRAFT) {
      if (finalizedAt != null || finalizedByUserId != null) {
        throw new IllegalStateException("Draft reports cannot have finalisation metadata");
      }
    } else {
      if (finalizedAt == null || finalizedByUserId == null) {
        throw new IllegalStateException("Final reports require finalisation metadata");
      }
    }
  }

  private void ensureDraft() {
    if (reportStatus != ReportStatus.DRAFT) {
      throw new IllegalStateException("Final progress reports are immutable");
    }
  }

  private static String normalizeCode(String code) {
    return requireText(code, "Report code").toUpperCase(java.util.Locale.ROOT);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(fieldName + " is required");
    return value.trim();
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0)
      throw new IllegalArgumentException(fieldName + " must be positive");
    return value;
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

  public String getReportCode() {
    return reportCode;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public ReportStatus getReportStatus() {
    return reportStatus;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public LocalDateTime getGeneratedAt() {
    return generatedAt;
  }

  public LocalDateTime getFinalizedAt() {
    return finalizedAt;
  }

  public Long getFinalizedByUserId() {
    return finalizedByUserId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
