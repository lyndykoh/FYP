package com.fttranscendence.learning.dashboard;

import com.fttranscendence.learning.mastery.MasteryApprovedResult;
import com.fttranscendence.learning.mastery.MasteryApprovedResultRepository;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.ClassMembership;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetAssignment;
import com.fttranscendence.learning.worksheet.WorksheetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes canonical learning-service records for the authenticated Student only. */
@Service
public class StudentDashboardService {
  private static final BigDecimal FOCUS_THRESHOLD = new BigDecimal("70.00");

  private final StudentProfileRepository students;
  private final MasteryRecordRepository mastery;
  private final MasteryApprovedResultRepository approvedResults;
  private final WorksheetRepository worksheets;
  private final Clock clock;

  public StudentDashboardService(
      StudentProfileRepository students,
      MasteryRecordRepository mastery,
      MasteryApprovedResultRepository approvedResults,
      WorksheetRepository worksheets,
      Clock dashboardClock) {
    this.students = students;
    this.mastery = mastery;
    this.approvedResults = approvedResults;
    this.worksheets = worksheets;
    this.clock = dashboardClock;
  }

  @Transactional(readOnly = true)
  public StudentDashboardResponse dashboard(long loginUserId, ZoneId timeZone) {
    if (loginUserId <= 0) {
      throw new StudentDashboardNotFoundException();
    }
    if (timeZone == null) {
      throw new IllegalArgumentException("Time zone is required.");
    }
    StudentProfile student =
        students.findByLoginUserId(loginUserId).orElseThrow(StudentDashboardNotFoundException::new);
    List<MasteryRecord> records =
        mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(student.getId());
    List<WorksheetAssignment> assignments = effectiveApprovedAssignments(student);
    LocalDateTime localNow = clock.instant().atZone(timeZone).toLocalDateTime();

    return new StudentDashboardResponse(
        student.getFullName(),
        timeZone.getId(),
        localNow.toLocalDate(),
        metrics(records, assignments.size()),
        mostRecentAssignment(assignments),
        assignments.stream()
            .filter(assignment -> assignment.getDueAt() != null)
            .filter(assignment -> assignment.getDueAt().isAfter(localNow))
            .min(
                Comparator.comparing(WorksheetAssignment::getDueAt)
                    .thenComparing(WorksheetAssignment::getId))
            .map(this::assignment)
            .orElse(null),
        strongestTopic(records),
        focusTopic(records),
        approvedResults
            .findFirstByStudentProfileIdAndActiveTrueOrderByReviewedAtDescSourceSubmissionIdDesc(
                student.getId())
            .map(this::approvedTopicResult)
            .orElse(null));
  }

  private StudentDashboardResponse.Assignment mostRecentAssignment(
      List<WorksheetAssignment> assignments) {
    return assignments.stream()
        .max(
            Comparator.comparing(
                    WorksheetAssignment::getAssignedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WorksheetAssignment::getId))
        .map(this::assignment)
        .orElse(null);
  }

  private StudentDashboardResponse.Topic strongestTopic(List<MasteryRecord> records) {
    return records.stream().max(topicScoreComparator()).map(this::topic).orElse(null);
  }

  private StudentDashboardResponse.Topic focusTopic(List<MasteryRecord> records) {
    return records.stream()
        .filter(this::needsFocus)
        .min(topicScoreComparator())
        .map(this::topic)
        .orElse(null);
  }

  private Comparator<MasteryRecord> topicScoreComparator() {
    return Comparator.comparing(MasteryRecord::getScore)
        .thenComparing(record -> record.getSyllabusTopic().getName())
        .thenComparing(MasteryRecord::getId);
  }

  private List<WorksheetAssignment> effectiveApprovedAssignments(StudentProfile student) {
    if (student.getTutorId() == null) {
      return List.of();
    }
    List<WorksheetAssignment> assignments = new ArrayList<>();
    for (Worksheet worksheet :
        worksheets.findApprovedStudentAssignedWorksheetsByTutorId(
            student.getTutorId(), student.getId())) {
      worksheet.getAssignments().stream()
          .filter(item -> item.getAssignmentType() == Worksheet.AudienceType.STUDENT)
          .filter(item -> student.getId().equals(item.getStudentProfileId()))
          .forEach(assignments::add);
    }
    for (Long classId :
        student.getMemberships().stream().map(ClassMembership::getClassId).toList()) {
      for (Worksheet worksheet :
          worksheets.findClassAssignedWorksheetsByTutorId(student.getTutorId(), classId)) {
        if (worksheet.getStatus() != Worksheet.Status.APPROVED) {
          continue;
        }
        worksheet.getAssignments().stream()
            .filter(item -> item.getAssignmentType() == Worksheet.AudienceType.CLASS)
            .filter(item -> classId.equals(item.getClassId()))
            .forEach(assignments::add);
      }
    }
    return assignments;
  }

  private StudentDashboardResponse.Metrics metrics(
      List<MasteryRecord> records, int assignmentCount) {
    BigDecimal total =
        records.stream().map(MasteryRecord::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal overall =
        records.isEmpty()
            ? null
            : total.divide(BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);
    return new StudentDashboardResponse.Metrics(
        overall,
        records.size(),
        records.stream().mapToInt(MasteryRecord::getAttemptCount).sum(),
        assignmentCount);
  }

  private boolean needsFocus(MasteryRecord record) {
    return record.getScore().compareTo(FOCUS_THRESHOLD) < 0
        || record.getMasteryStatus() == MasteryRecord.MasteryStatus.NEEDS_REVISION;
  }

  private StudentDashboardResponse.Assignment assignment(WorksheetAssignment value) {
    return new StudentDashboardResponse.Assignment(
        value.getWorksheetId(), value.getAssignmentType(), value.getAssignedAt(), value.getDueAt());
  }

  private StudentDashboardResponse.Topic topic(MasteryRecord value) {
    return new StudentDashboardResponse.Topic(
        value.getSyllabusTopic().getId(),
        value.getSyllabusTopic().getName(),
        value.getScore(),
        value.getMasteryStatus(),
        value.getAttemptCount(),
        value.getCalculatedAt());
  }

  private StudentDashboardResponse.ApprovedTopicResult approvedTopicResult(
      MasteryApprovedResult value) {
    return new StudentDashboardResponse.ApprovedTopicResult(
        value.getSyllabusTopic().getId(),
        value.getSyllabusTopic().getName(),
        value.getApprovedMarks(),
        value.getAvailableMarks(),
        value.getReviewedAt());
  }

  /** Missing linked profile and invalid identity deliberately share this outcome. */
  public static class StudentDashboardNotFoundException extends RuntimeException {}
}
