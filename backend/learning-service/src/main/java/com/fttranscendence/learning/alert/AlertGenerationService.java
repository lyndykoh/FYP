package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidence;
import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidenceRepository;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Deterministic, explainable, owner-scoped tutor alerts. */
@Service
public class AlertGenerationService {
  static final BigDecimal LOW_MASTERY_THRESHOLD = new BigDecimal("70.00");
  static final int REPEATED_DIAGNOSTIC_THRESHOLD = 2;
  static final java.time.Duration REVIEW_OVERDUE_AFTER = java.time.Duration.ofHours(48);

  private final TutorAlertRepository alerts;
  private final StudentProfileRepository students;
  private final MasteryRecordRepository mastery;
  private final MasteryDiagnosticEvidenceRepository diagnostics;
  private final MarkingReviewStatusProjectionRepository reviews;

  public AlertGenerationService(
      TutorAlertRepository alerts,
      StudentProfileRepository students,
      MasteryRecordRepository mastery,
      MasteryDiagnosticEvidenceRepository diagnostics,
      MarkingReviewStatusProjectionRepository reviews) {
    this.alerts = alerts;
    this.students = students;
    this.mastery = mastery;
    this.diagnostics = diagnostics;
    this.reviews = reviews;
  }

  @Transactional
  public List<AlertResponse> refresh(long tutorId) {
    requirePositive(tutorId);
    for (StudentProfile student : students.findAllByTutorIdOrderByFullNameAsc(tutorId)) {
      refreshStudent(tutorId, student);
    }

    refreshOverdueReviews(tutorId, LocalDateTime.now());
    return list(tutorId, activeAlertStatuses());
  }

  @Transactional
  public void refreshOverdueReviews(long tutorId, LocalDateTime now) {
    LocalDateTime overdueBefore = now.minus(REVIEW_OVERDUE_AFTER);
    List<MarkingReviewStatusProjection> overdueReviews =
        reviews.findByTutorIdAndStateAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
            tutorId, MarkingReviewStatusProjection.State.PENDING_REVIEW, overdueBefore);

    for (MarkingReviewStatusProjection review : overdueReviews) {
      StudentProfile student = review.getStudentProfile();
      createIfAbsent(
          tutorId,
          student,
          null,
          TutorAlert.AlertType.PENDING_REVIEW,
          TutorAlert.Severity.WARNING,
          overdueReviewKey(review),
          "Marking review is overdue",
          "This submission has awaited tutor review since " + review.getRequestedAt() + ".");
    }
  }

  @Transactional
  public void refreshStudent(long tutorId, StudentProfile student) {
    if (student == null || student.getTutorId() == null || !student.getTutorId().equals(tutorId)) {
      throw new AlertNotFoundException();
    }

    List<MasteryRecord> records =
        mastery.findAllByStudentProfileIdOrderByScoreDesc(student.getId());
    for (MasteryRecord record : records) {
      if (hasWeakTopicMastery(record)) {
        createWeakTopicAlert(tutorId, student, record);
      }
    }

    createRepeatedDiagnosticAlerts(tutorId, student);
  }

  private List<TutorAlert.AlertStatus> activeAlertStatuses() {
    return List.of(TutorAlert.AlertStatus.OPEN, TutorAlert.AlertStatus.ACKNOWLEDGED);
  }

  private String overdueReviewKey(MarkingReviewStatusProjection review) {
    return "overdue-review:" + review.getSourceSubmissionId() + ":" + review.getRevision();
  }

  private boolean hasWeakTopicMastery(MasteryRecord record) {
    return record.getScore().compareTo(LOW_MASTERY_THRESHOLD) < 0 && record.getAttemptCount() > 0;
  }

  private void createWeakTopicAlert(long tutorId, StudentProfile student, MasteryRecord record) {
    int attemptCount = record.getAttemptCount();
    String deduplicationKey =
        "weak-topic:"
            + student.getId()
            + ":"
            + record.getSyllabusTopic().getId()
            + ":"
            + record.getCalculatedAt();
    String message =
        ("%s mastery after %d approved attempt%s; " + "the alert threshold is below 70%%.")
            .formatted(percent(record.getScore()), attemptCount, attemptCount == 1 ? "" : "s");

    createIfAbsent(
        tutorId,
        student,
        record,
        TutorAlert.AlertType.WEAK_TOPIC,
        TutorAlert.Severity.WARNING,
        deduplicationKey,
        record.getSyllabusTopic().getName() + " needs practice",
        message);
  }

  private void createRepeatedDiagnosticAlerts(long tutorId, StudentProfile student) {
    Map<String, List<MasteryDiagnosticEvidence>> diagnosticsByRecordAndType =
        diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(student.getId()).stream()
            .collect(
                Collectors.groupingBy(
                    diagnostic ->
                        diagnostic.getMasteryRecord().getId() + ":" + diagnostic.getMistakeType()));

    for (List<MasteryDiagnosticEvidence> occurrences : diagnosticsByRecordAndType.values()) {
      if (occurrences.size() < REPEATED_DIAGNOSTIC_THRESHOLD) {
        continue;
      }

      createRepeatedDiagnosticAlert(tutorId, student, occurrences);
    }
  }

  private void createRepeatedDiagnosticAlert(
      long tutorId, StudentProfile student, List<MasteryDiagnosticEvidence> occurrences) {
    MasteryDiagnosticEvidence latestOccurrence = occurrences.get(0);
    MasteryRecord record = latestOccurrence.getMasteryRecord();
    String mistakeLabel = label(latestOccurrence.getMistakeType());
    String deduplicationKey =
        "repeated-diagnostic:"
            + student.getId()
            + ":"
            + record.getSyllabusTopic().getId()
            + ":"
            + latestOccurrence.getMistakeType()
            + ":"
            + latestOccurrence.getId();
    String message =
        "%d tutor-confirmed %s records were captured for this topic."
            .formatted(occurrences.size(), mistakeLabel.toLowerCase());

    createIfAbsent(
        tutorId,
        student,
        record,
        TutorAlert.AlertType.REPEATED_MISTAKE,
        TutorAlert.Severity.CRITICAL,
        deduplicationKey,
        "Repeated " + mistakeLabel + " in " + record.getSyllabusTopic().getName(),
        message);
  }

  @Transactional
  public List<AlertResponse> list(long tutorId, List<TutorAlert.AlertStatus> statuses) {
    requirePositive(tutorId);
    return alerts
        .findAllByTutorIdAndAlertStatusInOrderByCreatedAtDescIdDesc(tutorId, statuses)
        .stream()
        .map(AlertResponse::from)
        .toList();
  }

  @Transactional
  public AlertResponse resolve(long tutorId, long alertId) {
    return mutate(tutorId, alertId, true);
  }

  @Transactional
  public AlertResponse dismiss(long tutorId, long alertId) {
    return mutate(tutorId, alertId, false);
  }

  private AlertResponse mutate(long tutorId, long alertId, boolean resolve) {
    TutorAlert alert =
        alerts.findByIdAndTutorId(alertId, tutorId).orElseThrow(AlertNotFoundException::new);
    if (resolve) {
      alert.resolve(tutorId);
    } else {
      alert.dismiss(tutorId);
    }

    return AlertResponse.from(alerts.save(alert));
  }

  private void createIfAbsent(
      long tutorId,
      StudentProfile student,
      MasteryRecord record,
      TutorAlert.AlertType type,
      TutorAlert.Severity severity,
      String key,
      String title,
      String message) {
    if (alerts.findByTutorIdAndDeduplicationKey(tutorId, key).isPresent()) {
      return;
    }

    try {
      alerts.save(new TutorAlert(tutorId, student, record, type, severity, key, title, message));
    } catch (DataAccessException ignored) {
      // A unique key means a concurrent refresh created the same alert.
    }
  }

  private static String percent(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString() + "%";
  }

  private static String label(MasteryDiagnosticEvidence.MistakeType type) {
    return switch (type) {
      case CONCEPT_MISUNDERSTANDING -> "concept misunderstanding";
      case CALCULATION_ERROR -> "calculation error";
      case MISREAD_QUESTION -> "misread question";
      case INCOMPLETE_WORKING -> "incomplete working";
      case INCORRECT_FORMULA -> "incorrect formula";
      case CARELESS_MISTAKE -> "careless mistake";
      case WEAK_EXPLANATION -> "weak explanation";
      case MISSING_KEY_POINT -> "missing key point";
      case WRONG_UNITS -> "wrong units";
      case ANSWER_FORMAT_ISSUE -> "answer format issue";
    };
  }

  private static void requirePositive(long value) {
    if (value <= 0) {
      throw new IllegalArgumentException("Tutor id must be positive.");
    }
  }

  public record AlertResponse(
      Long id,
      Long studentId,
      String studentName,
      TutorAlert.AlertType type,
      TutorAlert.Severity severity,
      TutorAlert.AlertStatus status,
      String title,
      String message,
      java.time.LocalDateTime createdAt) {
    static AlertResponse from(TutorAlert alert) {
      return new AlertResponse(
          alert.getId(),
          alert.getStudentProfile().getId(),
          alert.getStudentProfile().getFullName(),
          alert.getAlertType(),
          alert.getSeverity(),
          alert.getAlertStatus(),
          alert.getTitle(),
          alert.getMessage(),
          alert.getCreatedAt());
    }
  }

  public static class AlertNotFoundException extends RuntimeException {}
}
