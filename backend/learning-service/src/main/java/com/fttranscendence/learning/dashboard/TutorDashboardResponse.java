package com.fttranscendence.learning.dashboard;

import com.fttranscendence.learning.alert.TutorAlert;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Owner-scoped, read-only dashboard data. Schedule times are class-local wall times: {@code
 * timeZone} chooses the tutor's current day, it never converts a stored class time into another
 * time zone.
 */
public record TutorDashboardResponse(
    String timeZone,
    LocalDate today,
    Metrics metrics,
    List<TodaySchedule> todaySchedule,
    List<Activity> recentActivity) {
  public record Metrics(
      long activeClassCount,
      long studentCount,
      long pendingReviewCount,
      long needsAttentionStudentCount,
      long reportsReadyCount) {}

  public record TodaySchedule(
      Long classId,
      String className,
      String subject,
      String level,
      LocalTime startTime,
      LocalTime endTime) {}

  public enum ActivityType {
    WORKSHEET_ASSIGNED,
    REVIEW_REQUESTED,
    ALERT_CREATED
  }

  /** The activity stream deliberately excludes raw submission events. */
  public record Activity(
      ActivityType type,
      long sourceId,
      Long studentId,
      String studentName,
      String title,
      String detail,
      LocalDateTime occurredAt,
      TutorAlert.Severity severity) {}
}
