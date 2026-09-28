package com.fttranscendence.learning.dashboard;

import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.worksheet.Worksheet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only, self-scoped dashboard facts for a linked Student account. This response deliberately
 * contains no inferred worksheet result: approved marking evidence is topic-level only and is
 * nullable when none exists.
 */
public record StudentDashboardResponse(
    String studentName,
    String timeZone,
    LocalDate today,
    Metrics metrics,
    Assignment latestAssignment,
    Assignment nextAssignment,
    Topic strongestTopic,
    Topic focusTopic,
    ApprovedTopicResult latestApprovedTopicResult) {
  public record Metrics(
      BigDecimal overallMastery,
      int trackedTopicCount,
      int totalAttempts,
      int approvedAssignmentCount) {}

  /** Assignment metadata only; no worksheet answer or score is implied. */
  public record Assignment(
      Long worksheetId,
      Worksheet.AudienceType assignmentType,
      LocalDateTime assignedAt,
      LocalDateTime dueAt) {}

  /** Current, persisted mastery evidence for one syllabus topic. */
  public record Topic(
      Long topicId,
      String topicName,
      BigDecimal score,
      MasteryRecord.MasteryStatus status,
      int attemptCount,
      LocalDateTime calculatedAt) {}

  /** An approved marking projection, intentionally not labelled a worksheet result. */
  public record ApprovedTopicResult(
      Long topicId,
      String topicName,
      BigDecimal approvedMarks,
      BigDecimal availableMarks,
      LocalDateTime reviewedAt) {}
}
