package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.insight.ClassInsightService;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns persisted mastery and tutor-managed class coverage into explainable diagnostic suggestions.
 * It only creates DRAFT worksheets through the normal, idempotent generation boundary; it never
 * assigns work or changes mastery.
 */
@Service
public class DiagnosticWorksheetService {
  private final TutorClassRepository classes;
  private final StudentProfileRepository students;
  private final MasteryRecordRepository mastery;
  private final ClassInsightService classInsights;
  private final WorksheetService worksheets;

  public DiagnosticWorksheetService(
      TutorClassRepository classes,
      StudentProfileRepository students,
      MasteryRecordRepository mastery,
      ClassInsightService classInsights,
      WorksheetService worksheets) {
    this.classes = classes;
    this.students = students;
    this.mastery = mastery;
    this.classInsights = classInsights;
    this.worksheets = worksheets;
  }

  @Transactional(readOnly = true)
  public Recommendations recommendations(long tutorId, long classId) {
    if (classes.findByIdAndTutorId(classId, tutorId).isEmpty())
      throw new WorksheetService.ClassNotFoundException();
    List<StudentProfile> members =
        students.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId);
    if (members.isEmpty())
      return Recommendations.insufficient(
          "No active class members are available for a diagnostic worksheet.");
    List<MasteryRecord> evidence =
        mastery.findAllByStudentProfileIdInWithTopic(
            members.stream().map(StudentProfile::getId).toList());
    List<MasteryRecord> attempted =
        evidence.stream().filter(record -> record.getAttemptCount() > 0).toList();
    List<ClassInsightService.CoveredTopic> covered = classInsights.coveredTopics(tutorId, classId);
    if (attempted.isEmpty() && covered.isEmpty()) {
      return Recommendations.insufficient(
          "Choose covered topics first, or approve a marked response before requesting a diagnostic worksheet.");
    }
    List<Recommendation> items = new ArrayList<>();
    attempted.stream()
        .sorted(
            Comparator.comparing(MasteryRecord::getScore)
                .thenComparing(record -> record.getStudentProfile().getId())
                .thenComparing(record -> record.getSyllabusTopic().getName())
                .thenComparing(record -> record.getSyllabusTopic().getId()))
        .forEach(
            record ->
                items.add(
                    new Recommendation(
                        record.getStudentProfile().getId(),
                        record.getStudentProfile().getFullName(),
                        record.getSyllabusTopic().getId(),
                        record.getSyllabusTopic().getName(),
                        record.getScore(),
                        record.getAttemptCount(),
                        record.getScore().compareTo(BigDecimal.valueOf(70)) < 0
                            ? Reason.LOW_MASTERY
                            : Reason.CONSOLIDATE)));
    Set<Long> attemptedTopics =
        attempted.stream()
            .map(record -> record.getSyllabusTopic().getId())
            .collect(java.util.stream.Collectors.toSet());
    covered.stream()
        .filter(topic -> !attemptedTopics.contains(topic.id()))
        .sorted(
            Comparator.comparing(ClassInsightService.CoveredTopic::name)
                .thenComparing(ClassInsightService.CoveredTopic::id))
        .forEach(
            topic ->
                items.add(
                    new Recommendation(
                        null, null, topic.id(), topic.name(), null, 0, Reason.NEW_TOPIC)));
    return new Recommendations(
        Status.READY,
        "Suggestions use approved mastery attempts and tutor-selected coverage. They are not assigned until you approve the draft.",
        List.copyOf(items));
  }

  @Transactional
  public WorksheetRequests.GenerationRequestResponse generate(
      long tutorId,
      long classId,
      String idempotencyKey,
      WorksheetRequests.GenerateDiagnosticWorksheetRequest input) {
    Recommendations available = recommendations(tutorId, classId);
    if (available.status() != Status.READY)
      throw new WorksheetService.InvalidWorksheetRequestException(available.message());
    Set<Long> requestedStudents =
        input.studentIds() == null ? Set.of() : Set.copyOf(input.studentIds());
    Map<Long, List<Recommendation>> byTopic = new LinkedHashMap<>();
    for (Recommendation recommendation : available.recommendations()) {
      if (input.targetMode() == WorksheetGenerationRequest.TargetMode.CLASS
          || recommendation.studentId() == null
          || requestedStudents.contains(recommendation.studentId())) {
        byTopic
            .computeIfAbsent(recommendation.topicId(), ignored -> new ArrayList<>())
            .add(recommendation);
      }
    }
    List<Long> selectedTopics = input.topicIds().stream().distinct().sorted().toList();
    if (selectedTopics.size() != input.topicIds().size()
        || selectedTopics.stream().anyMatch(topic -> !byTopic.containsKey(topic))) {
      throw new WorksheetService.InvalidWorksheetRequestException(
          "Diagnostic topics must come from the current evidence recommendation.");
    }
    String evidence =
        selectedTopics.stream()
            .map(topic -> diagnosticReason(byTopic.get(topic)))
            .collect(java.util.stream.Collectors.joining(" "));
    return worksheets.generate(
        tutorId,
        classId,
        idempotencyKey,
        new WorksheetRequests.GenerateWorksheetRequest(
            input.targetMode(),
            selectedTopics,
            input.questionCount(),
            input.questionType(),
            input.difficulty(),
            input.dueAt(),
            input.title(),
            joinInstructions(input.instructions(), evidence),
            input.studentIds(),
            Worksheet.WorksheetType.DIAGNOSTIC));
  }

  private String diagnosticReason(List<Recommendation> evidence) {
    Recommendation first = evidence.get(0);
    String prefix = "Diagnostic focus: " + first.topicName() + " — ";
    return switch (first.reason()) {
      case LOW_MASTERY ->
          prefix
              + "approved mastery is "
              + first.masteryPercent().stripTrailingZeros().toPlainString()
              + "% after "
              + first.attemptCount()
              + " attempt(s).";
      case CONSOLIDATE ->
          prefix
              + "include a retrieval check after "
              + first.attemptCount()
              + " approved attempt(s).";
      case NEW_TOPIC ->
          prefix
              + "the tutor marked this topic as covered, but the class has no approved attempt yet.";
    };
  }

  private String joinInstructions(String supplied, String evidence) {
    String prefix = supplied == null || supplied.isBlank() ? "" : supplied.trim() + "\n\n";
    String value = prefix + evidence;
    return value.length() <= 2000 ? value : value.substring(0, 2000);
  }

  public enum Status {
    READY,
    INSUFFICIENT_EVIDENCE
  }

  public enum Reason {
    LOW_MASTERY,
    CONSOLIDATE,
    NEW_TOPIC
  }

  public record Recommendation(
      Long studentId,
      String studentName,
      Long topicId,
      String topicName,
      BigDecimal masteryPercent,
      int attemptCount,
      Reason reason) {}

  public record Recommendations(
      Status status, String message, List<Recommendation> recommendations) {
    static Recommendations insufficient(String message) {
      return new Recommendations(Status.INSUFFICIENT_EVIDENCE, message, List.of());
    }
  }
}
