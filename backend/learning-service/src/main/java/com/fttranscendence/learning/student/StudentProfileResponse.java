package com.fttranscendence.learning.student;

import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.report.ProgressReport;
import com.fttranscendence.learning.worksheet.Worksheet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Canonical, read-only view of a student. {@code tutorOnly} is deliberately null for student
 * self-access so tutor workflow data cannot leak through a shared response shape.
 */
public record StudentProfileResponse(
    Long id,
    String fullName,
    List<ClassSummary> classes,
    Metrics metrics,
    List<MasteryTopic> mastery,
    LearningProfile learningProfile,
    List<HistoryItem> history,
    List<WorksheetAssignmentSummary> worksheets,
    TutorOnly tutorOnly) {
  public record ClassSummary(
      Long id, String className, String subject, String level, TutorClass.Status status) {}

  public record Metrics(
      BigDecimal averageMastery,
      int topicCount,
      int totalAttempts,
      LocalDateTime lastCalculatedAt) {}

  public record MasteryTopic(
      Long topicId,
      String topicCode,
      String topicName,
      BigDecimal score,
      MasteryRecord.MasteryStatus status,
      int attemptCount,
      LocalDateTime calculatedAt) {}

  public record TopicSummary(
      Long topicId, String topicName, BigDecimal score, MasteryRecord.MasteryStatus status) {}

  /** Derived from mastery records; it is not tutor-entered or AI-inferred. */
  public record LearningProfile(List<TopicSummary> strengths, List<TopicSummary> focusAreas) {}

  /** Deliberately omits sourceSubmissionId and other assessment identifiers. */
  public record HistoryItem(
      Long topicId,
      String topicName,
      BigDecimal previousScore,
      BigDecimal newScore,
      MasteryRecord.MasteryStatus previousStatus,
      MasteryRecord.MasteryStatus newStatus,
      String reason,
      LocalDateTime occurredAt) {}

  public record WorksheetAssignmentSummary(
      Long worksheetId,
      String title,
      Worksheet.AudienceType assignmentType,
      Long classId,
      LocalDateTime assignedAt,
      LocalDateTime dueAt) {}

  public record TutorOnly(
      List<AlertSummary> activeAlerts, List<ReportMetadata> reports, long approvedWorksheetCount) {}

  public record AlertSummary(
      Long id,
      String type,
      String severity,
      String status,
      String title,
      LocalDateTime createdAt) {}

  /** Report snapshot content and finalizer identifiers are intentionally excluded. */
  public record ReportMetadata(
      Long id,
      String reportCode,
      ProgressReport.ReportStatus status,
      LocalDate periodStart,
      LocalDate periodEnd,
      LocalDateTime generatedAt,
      LocalDateTime finalizedAt) {}
}
