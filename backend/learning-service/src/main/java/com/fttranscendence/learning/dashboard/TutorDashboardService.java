package com.fttranscendence.learning.dashboard;

import com.fttranscendence.learning.alert.MarkingReviewStatusProjection;
import com.fttranscendence.learning.alert.MarkingReviewStatusProjectionRepository;
import com.fttranscendence.learning.alert.TutorAlert;
import com.fttranscendence.learning.alert.TutorAlertRepository;
import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.report.ProgressReport;
import com.fttranscendence.learning.report.ProgressReportRepository;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetAssignment;
import com.fttranscendence.learning.worksheet.WorksheetRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TutorDashboardService {
  private static final int RECENT_ACTIVITY_LIMIT = 20;
  private static final List<TutorAlert.AlertStatus> ACTIVE_ALERT_STATUSES =
      List.of(TutorAlert.AlertStatus.OPEN, TutorAlert.AlertStatus.ACKNOWLEDGED);

  private final TutorClassRepository classes;
  private final StudentProfileRepository students;
  private final MarkingReviewStatusProjectionRepository reviews;
  private final TutorAlertRepository alerts;
  private final ProgressReportRepository reports;
  private final WorksheetRepository worksheets;
  private final Clock clock;

  public TutorDashboardService(
      TutorClassRepository classes,
      StudentProfileRepository students,
      MarkingReviewStatusProjectionRepository reviews,
      TutorAlertRepository alerts,
      ProgressReportRepository reports,
      WorksheetRepository worksheets,
      Clock dashboardClock) {
    this.classes = classes;
    this.students = students;
    this.reviews = reviews;
    this.alerts = alerts;
    this.reports = reports;
    this.worksheets = worksheets;
    this.clock = dashboardClock;
  }

  @Transactional(readOnly = true)
  public TutorDashboardResponse dashboard(long tutorId, ZoneId timeZone) {
    if (tutorId <= 0) {
      throw new IllegalArgumentException("Tutor id must be positive.");
    }
    if (timeZone == null) {
      throw new IllegalArgumentException("Time zone is required.");
    }

    List<TutorClass> activeClasses =
        classes.findAllByTutorIdAndStatusOrderByClassNameAsc(tutorId, TutorClass.Status.ACTIVE);
    LocalDate today = clock.instant().atZone(timeZone).toLocalDate();
    TutorDashboardResponse.Metrics metrics =
        new TutorDashboardResponse.Metrics(
            activeClasses.size(),
            students.findAllByTutorIdOrderByFullNameAsc(tutorId).size(),
            reviews.countByTutorIdAndState(
                tutorId, MarkingReviewStatusProjection.State.PENDING_REVIEW),
            alerts.countDistinctActiveStudentsByTutorId(tutorId, ACTIVE_ALERT_STATUSES),
            reports.countByTutorIdAndReportStatus(tutorId, ProgressReport.ReportStatus.FINAL));

    return new TutorDashboardResponse(
        timeZone.getId(),
        today,
        metrics,
        todaySchedule(activeClasses, today),
        recentActivity(tutorId));
  }

  private List<TutorDashboardResponse.TodaySchedule> todaySchedule(
      List<TutorClass> activeClasses, LocalDate today) {
    return activeClasses.stream()
        .flatMap(
            tutorClass ->
                tutorClass.getSchedules().stream()
                    .filter(slot -> slot.getDayOfWeek() == today.getDayOfWeek())
                    .map(
                        slot ->
                            new TutorDashboardResponse.TodaySchedule(
                                tutorClass.getId(),
                                tutorClass.getClassName(),
                                tutorClass.getSubject(),
                                tutorClass.getLevel(),
                                slot.getStartTime(),
                                slot.getEndTime())))
        .sorted(
            Comparator.comparing(TutorDashboardResponse.TodaySchedule::startTime)
                .thenComparing(TutorDashboardResponse.TodaySchedule::className)
                .thenComparing(TutorDashboardResponse.TodaySchedule::classId))
        .toList();
  }

  private List<TutorDashboardResponse.Activity> recentActivity(long tutorId) {
    List<TutorDashboardResponse.Activity> activity = new ArrayList<>();
    for (Worksheet worksheet : worksheets.findAllByTutorIdWithAssignments(tutorId)) {
      for (WorksheetAssignment assignment : worksheet.getAssignments()) {
        String target =
            assignment.getAssignmentType() == Worksheet.AudienceType.CLASS
                ? "Assigned to class " + assignment.getClassId()
                : "Assigned to selected student";
        activity.add(
            new TutorDashboardResponse.Activity(
                TutorDashboardResponse.ActivityType.WORKSHEET_ASSIGNED,
                assignment.getId(),
                assignment.getStudentProfileId(),
                null,
                worksheet.getTitle(),
                target,
                assignment.getAssignedAt(),
                null));
      }
    }
    for (MarkingReviewStatusProjection review :
        reviews.findByTutorIdAndStateOrderByRequestedAtDescSourceSubmissionIdAsc(
            tutorId, MarkingReviewStatusProjection.State.PENDING_REVIEW)) {
      activity.add(
          new TutorDashboardResponse.Activity(
              TutorDashboardResponse.ActivityType.REVIEW_REQUESTED,
              review.getSourceSubmissionId(),
              review.getStudentProfile().getId(),
              review.getStudentProfile().getFullName(),
              "Marking review requested",
              "Submission " + review.getSourceSubmissionId() + " is pending tutor review.",
              review.getRequestedAt(),
              null));
    }
    for (TutorAlert alert :
        alerts.findAllByTutorIdAndAlertStatusInOrderByCreatedAtDescIdDesc(
            tutorId, ACTIVE_ALERT_STATUSES)) {
      activity.add(
          new TutorDashboardResponse.Activity(
              TutorDashboardResponse.ActivityType.ALERT_CREATED,
              alert.getId(),
              alert.getStudentProfile().getId(),
              alert.getStudentProfile().getFullName(),
              alert.getTitle(),
              alert.getMessage(),
              alert.getCreatedAt(),
              alert.getSeverity()));
    }
    return activity.stream()
        .sorted(
            Comparator.comparing(
                    TutorDashboardResponse.Activity::occurredAt, Comparator.reverseOrder())
                .thenComparing(item -> item.type().name())
                .thenComparingLong(TutorDashboardResponse.Activity::sourceId))
        .limit(RECENT_ACTIVITY_LIMIT)
        .toList();
  }
}
