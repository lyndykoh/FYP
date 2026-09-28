package com.fttranscendence.learning.insight;

import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidence;
import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidenceRepository;
import com.fttranscendence.learning.mastery.MasteryHistory;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produces an evidence-first individual learning profile from approved mastery. AI output is
 * deliberately not authoritative: this service always has a deterministic result and never writes
 * mastery, ranking, or tutor feedback.
 */
@Service
public class LearningProfileService {

  private static final BigDecimal STRENGTH_SCORE = new BigDecimal("85.00");
  private static final BigDecimal FOCUS_SCORE = new BigDecimal("70.00");

  private final StudentProfileRepository students;
  private final MasteryRecordRepository mastery;
  private final MasteryDiagnosticEvidenceRepository diagnostics;

  public LearningProfileService(
      StudentProfileRepository students,
      MasteryRecordRepository mastery,
      MasteryDiagnosticEvidenceRepository diagnostics) {
    this.students = students;
    this.mastery = mastery;
    this.diagnostics = diagnostics;
  }

  @Transactional(readOnly = true)
  public LearningProfileResponse forTutor(long tutorId, long studentId) {
    StudentProfile student =
        students.findByIdAndTutorId(studentId, tutorId).orElseThrow(ProfileNotFoundException::new);
    return profile(student);
  }

  @Transactional(readOnly = true)
  public LearningProfileResponse forStudent(long loginUserId) {
    StudentProfile student =
        students.findByLoginUserId(loginUserId).orElseThrow(ProfileNotFoundException::new);
    return profile(student);
  }

  private LearningProfileResponse profile(StudentProfile student) {
    List<MasteryRecord> records =
        mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(student.getId());
    List<TopicSummary> strengths =
        records.stream()
            .filter(record -> record.getScore().compareTo(STRENGTH_SCORE) >= 0)
            .sorted(
                Comparator.comparing(MasteryRecord::getScore)
                    .reversed()
                    .thenComparing(record -> record.getSyllabusTopic().getName()))
            .map(this::topic)
            .limit(3)
            .toList();
    List<TopicSummary> growthAreas =
        records.stream()
            .filter(this::needsPractice)
            .sorted(
                Comparator.comparing(MasteryRecord::getScore)
                    .thenComparing(record -> record.getSyllabusTopic().getName()))
            .map(this::topic)
            .limit(3)
            .toList();
    List<TopicSummary> improvements =
        records.stream()
            .filter(record -> record.getMasteryStatus() == MasteryRecord.MasteryStatus.IMPROVING)
            .sorted(
                Comparator.comparing(MasteryRecord::getScore)
                    .reversed()
                    .thenComparing(record -> record.getSyllabusTopic().getName()))
            .map(this::topic)
            .limit(3)
            .toList();
    List<MasteryDiagnosticEvidence> confirmedDiagnostics =
        diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(student.getId());
    List<Finding> findings = findings(records, confirmedDiagnostics);
    List<LearningDimension> dimensions = dimensions(confirmedDiagnostics);
    LocalDateTime dataAsOf =
        Stream.concat(
                records.stream().map(MasteryRecord::getCalculatedAt),
                confirmedDiagnostics.stream().map(MasteryDiagnosticEvidence::getCreatedAt))
            .filter(java.util.Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);
    return new LearningProfileResponse(
        student.getId(),
        strengths,
        growthAreas,
        improvements,
        dimensions,
        findings,
        dataAsOf,
        Source.DETERMINISTIC);
  }

  /**
   * These four categories are the confirmed learning dimensions. A zero count is intentional: it
   * means no tutor-confirmed diagnostic evidence exists for that dimension, rather than that the
   * student has no weakness.
   */
  private List<LearningDimension> dimensions(List<MasteryDiagnosticEvidence> confirmedDiagnostics) {
    return Stream.of(MasteryDiagnosticEvidence.Category.values())
        .map(
            category -> {
              List<MasteryDiagnosticEvidence> matching =
                  confirmedDiagnostics.stream()
                      .filter(item -> item.getCategory() == category)
                      .toList();
              return new LearningDimension(
                  category,
                  matching.size(),
                  matching.stream().map(item -> evidence(item.getMasteryRecord(), item)).toList());
            })
        .toList();
  }

  private List<Finding> findings(
      List<MasteryRecord> records, List<MasteryDiagnosticEvidence> approvedDiagnostics) {
    Map<String, Finding> unique = new LinkedHashMap<>();
    for (MasteryRecord record : records) {
      Evidence base = evidence(record, (MasteryHistory) null);
      if (needsPractice(record)) {
        put(
            unique,
            new Finding(
                FindingType.MASTERY_GAP,
                record.getSyllabusTopic().getName() + " needs focused practice",
                "%s mastery across %d approved attempt%s."
                    .formatted(
                        percent(record.getScore()),
                        record.getAttemptCount(),
                        record.getAttemptCount() == 1 ? "" : "s"),
                "Review the latest worked example, then complete a short targeted practice set.",
                List.of(base)));
      }
      if (record.getAttemptCount() >= 2 && needsPractice(record)) {
        put(
            unique,
            new Finding(
                FindingType.REPEATED_WEAKNESS,
                "Repeated difficulty in " + record.getSyllabusTopic().getName(),
                "This topic remains below the practice threshold after %d approved attempts."
                    .formatted(record.getAttemptCount()),
                "Use tutor-led correction before assigning more independent practice.",
                List.of(base)));
      }
      for (MasteryDiagnosticEvidence diagnostic :
          approvedDiagnostics.stream()
              .filter(item -> item.getMasteryRecord().getId().equals(record.getId()))
              .toList()) {
        FindingType tagged = categoryFrom(diagnostic.getCategory());
        put(
            unique,
            new Finding(
                tagged,
                label(tagged) + " evidence in " + record.getSyllabusTopic().getName(),
                diagnosticSummary(diagnostic),
                action(tagged),
                List.of(evidence(record, diagnostic))));
      }
      for (MasteryHistory history : record.getHistory()) {
        if (history.getNewScore().compareTo(history.getPreviousScore()) < 0) {
          put(
              unique,
              new Finding(
                  FindingType.REGRESSED,
                  record.getSyllabusTopic().getName() + " has regressed",
                  "The latest approved update changed mastery from %s to %s."
                      .formatted(
                          percent(history.getPreviousScore()), percent(history.getNewScore())),
                  "Revisit the earlier successful method before the next worksheet.",
                  List.of(evidence(record, history))));
        }
      }
    }
    return unique.values().stream()
        .sorted(Comparator.comparingInt(this::priority).thenComparing(Finding::title))
        .limit(3)
        .toList();
  }

  private void put(Map<String, Finding> target, Finding finding) {
    Long topicId = finding.evidence().isEmpty() ? null : finding.evidence().get(0).topicId();
    target.putIfAbsent(finding.type() + ":" + topicId, finding);
  }

  private boolean needsPractice(MasteryRecord record) {
    return record.getScore().compareTo(FOCUS_SCORE) < 0
        || record.getMasteryStatus() == MasteryRecord.MasteryStatus.NEEDS_REVISION;
  }

  private TopicSummary topic(MasteryRecord record) {
    return new TopicSummary(
        record.getSyllabusTopic().getId(),
        record.getSyllabusTopic().getName(),
        record.getScore(),
        record.getMasteryStatus(),
        record.getAttemptCount());
  }

  private Evidence evidence(MasteryRecord record, MasteryHistory history) {
    return new Evidence(
        record.getSyllabusTopic().getId(),
        record.getSyllabusTopic().getName(),
        record.getScore(),
        record.getMasteryStatus(),
        record.getAttemptCount(),
        history == null ? null : history.getReason(),
        history == null ? record.getCalculatedAt() : history.getCreatedAt());
  }

  private Evidence evidence(MasteryRecord record, MasteryDiagnosticEvidence diagnostic) {
    return new Evidence(
        record.getSyllabusTopic().getId(),
        record.getSyllabusTopic().getName(),
        record.getScore(),
        record.getMasteryStatus(),
        record.getAttemptCount(),
        diagnostic.getTutorRationale(),
        diagnostic.getCreatedAt());
  }

  private FindingType categoryFrom(MasteryDiagnosticEvidence.Category category) {
    return switch (category) {
      case CONCEPT -> FindingType.CONCEPT_WEAKNESS;
      case KEYWORD -> FindingType.KEYWORD_WEAKNESS;
      case EXPRESSION -> FindingType.EXPRESSION_WEAKNESS;
      case APPLICATION -> FindingType.APPLICATION_WEAKNESS;
    };
  }

  private String diagnosticSummary(MasteryDiagnosticEvidence diagnostic) {
    if (diagnostic.getCategory() == MasteryDiagnosticEvidence.Category.KEYWORD
        && !diagnostic.getMissingKeywords().isEmpty()) {
      return "Tutor-confirmed missing keyword%s: %s."
          .formatted(
              diagnostic.getMissingKeywords().size() == 1 ? "" : "s",
              String.join(", ", diagnostic.getMissingKeywords()));
    }
    return "Tutor-confirmed diagnostic evidence: " + diagnostic.getTutorRationale();
  }

  private int priority(Finding finding) {
    return switch (finding.type()) {
      case REPEATED_WEAKNESS, REGRESSED -> 0;
      case CONCEPT_WEAKNESS, KEYWORD_WEAKNESS, EXPRESSION_WEAKNESS, APPLICATION_WEAKNESS -> 1;
      case MASTERY_GAP -> 2;
    };
  }

  private String label(FindingType type) {
    return switch (type) {
      case CONCEPT_WEAKNESS -> "Concept weakness";
      case KEYWORD_WEAKNESS -> "Keyword weakness";
      case EXPRESSION_WEAKNESS -> "Expression weakness";
      case APPLICATION_WEAKNESS -> "Application weakness";
      case REPEATED_WEAKNESS -> "Repeated weakness";
      case REGRESSED -> "Regression";
      case MASTERY_GAP -> "Mastery gap";
    };
  }

  private String action(FindingType type) {
    return switch (type) {
      case KEYWORD_WEAKNESS -> "Practise using the required keyword in a complete answer.";
      case EXPRESSION_WEAKNESS ->
          "Practise turning the idea into a complete scientific explanation.";
      case APPLICATION_WEAKNESS -> "Apply the idea to a new question before moving on.";
      case CONCEPT_WEAKNESS -> "Revisit the underlying concept with a worked example.";
      default -> "Review this evidence with the tutor before the next worksheet.";
    };
  }

  private String percent(BigDecimal score) {
    return score.stripTrailingZeros().toPlainString() + "%";
  }

  public enum Source {
    DETERMINISTIC
  }

  public enum FindingType {
    CONCEPT_WEAKNESS,
    KEYWORD_WEAKNESS,
    EXPRESSION_WEAKNESS,
    APPLICATION_WEAKNESS,
    REPEATED_WEAKNESS,
    REGRESSED,
    MASTERY_GAP
  }

  /** Additive profile fields preserve the existing strengths/growth/findings API contract. */
  public record LearningProfileResponse(
      Long studentId,
      List<TopicSummary> strengths,
      List<TopicSummary> growthAreas,
      List<TopicSummary> improvements,
      List<LearningDimension> dimensions,
      List<Finding> findings,
      LocalDateTime dataAsOf,
      Source source) {}

  public record TopicSummary(
      Long topicId,
      String topicName,
      BigDecimal score,
      MasteryRecord.MasteryStatus status,
      int attemptCount) {}

  public record LearningDimension(
      MasteryDiagnosticEvidence.Category category, int evidenceCount, List<Evidence> evidence) {}

  public record Finding(
      FindingType type,
      String title,
      String summary,
      String suggestedAction,
      List<Evidence> evidence) {}

  public record Evidence(
      Long topicId,
      String topicName,
      BigDecimal score,
      MasteryRecord.MasteryStatus status,
      int attemptCount,
      String sourceReason,
      LocalDateTime occurredAt) {}

  public static class ProfileNotFoundException extends RuntimeException {}
}
