package com.fttranscendence.learning.classroom;

import com.fttranscendence.learning.worksheet.Worksheet;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Owner-scoped, read-only class summary assembled from the authoritative learning records already
 * held by this service. AI insight generation is deliberately represented as unavailable until that
 * separate workflow exists.
 */
public record ClassDetailResponse(
    Long id,
    Long tutorId,
    String className,
    String subject,
    String level,
    TutorClass.Status status,
    List<ScheduleResponse> schedules,
    List<StudentResponse> students,
    MasterySummary mastery,
    List<WeakAreaResponse> weakAreas,
    InsightResponse insight,
    List<WorksheetResponse> worksheets) {
  public record ScheduleResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {}

  public record StudentResponse(
      Long id, String fullName, BigDecimal overallMastery, int masteryRecordCount) {}

  public record MasterySummary(BigDecimal averageScore, int recordCount, int studentsWithMastery) {}

  public record WeakAreaResponse(
      Long topicId, String topicName, BigDecimal averageScore, int affectedStudentCount) {}

  public record InsightResponse(InsightStatus status, String message) {}

  public enum InsightStatus {
    FRESH,
    STALE,
    REFRESHING,
    FAILED
  }

  public record WorksheetResponse(
      Long id,
      String title,
      Worksheet.Status status,
      LocalDateTime assignedAt,
      LocalDateTime dueAt) {}
}
