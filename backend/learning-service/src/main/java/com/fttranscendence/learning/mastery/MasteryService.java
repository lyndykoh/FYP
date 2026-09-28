package com.fttranscendence.learning.mastery;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Applies only authoritative Tutor-approved marking results to persisted topic mastery. */
@Service
public class MasteryService {
  private final MasteryRecordRepository records;
  private final StudentProfileRepository students;
  private final SyllabusTopicRepository topics;
  private final MasteryCalculator calculator;
  private final MasteryApprovedResultRepository approvedResults;
  private final MasteryDiagnosticEvidenceRepository evidence;

  public MasteryService(
      MasteryRecordRepository records,
      StudentProfileRepository students,
      SyllabusTopicRepository topics,
      MasteryCalculator calculator,
      MasteryApprovedResultRepository approvedResults,
      MasteryDiagnosticEvidenceRepository evidence) {
    this.records = records;
    this.students = students;
    this.topics = topics;
    this.calculator = calculator;
    this.approvedResults = approvedResults;
    this.evidence = evidence;
  }

  @Transactional
  public MasteryRecord applyApprovedResult(ApprovedResult result) {
    if (result == null || !result.approved()) {
      throw new UnapprovedResultException();
    }

    return applyApprovedMarking(
        new ApprovedMarking(
            result.submissionId(),
            result.tutorId(),
            null,
            null,
            result.studentId(),
            result.syllabusTopicId(),
            result.approvedMarks(),
            result.availableMarks(),
            1,
            State.APPROVED,
            LocalDateTime.now(),
            List.of()));
  }

  /**
   * Applies a backend-authenticated marking projection. Approved-result, typed evidence and rebuilt
   * topic mastery are committed in one learning transaction; repeated deliveries and older
   * revisions are harmless.
   */
  @Transactional
  public MasteryRecord applyApprovedMarking(ApprovedMarking input) {
    validate(input);

    StudentProfile student = findTutorOwnedStudent(input);
    SyllabusTopic topic = findActiveTopic(input);
    MasteryRecord record = findOrCreateRecord(student, topic);
    MasteryApprovedResult projection = findExistingProjection(input);

    // Grading emits immutable, monotonically revised events.  A repeated
    // delivery of the same revision must not recalculate derived evidence
    // counts or recreate rows; that is the local idempotency boundary.
    if (isDuplicateOrOlderRevision(projection, input)) {
      return record;
    }

    if (projectionChangesOwnership(projection, student, topic)) {
      throw new InvalidResultException(
          "An approved result cannot change student or syllabus topic.");
    }

    boolean isApproved = input.state() == State.APPROVED;
    if (projection == null) {
      projection = createProjection(input, student, topic, isApproved);
    } else if (!isApproved) {
      if (!projection.retract(input.revision())) {
        return record;
      }
    } else if (!projection.replace(
        input.worksheetId(),
        input.worksheetQuestionId(),
        input.approvedMarks(),
        input.availableMarks(),
        0,
        input.revision(),
        true,
        input.reviewedAt())) {
      return record;
    }

    replaceDiagnosticEvidence(input, record, student, isApproved);
    approvedResults.saveAndFlush(projection);
    rebuild(record, student.getId(), topic.getId());
    return records.save(record);
  }

  private StudentProfile findTutorOwnedStudent(ApprovedMarking input) {
    return students
        .findByIdAndTutorId(input.studentId(), input.tutorId())
        .orElseThrow(StudentNotFoundException::new);
  }

  private SyllabusTopic findActiveTopic(ApprovedMarking input) {
    return topics
        .findById(input.syllabusTopicId())
        .filter(SyllabusTopic::isActive)
        .orElseThrow(TopicNotFoundException::new);
  }

  private MasteryRecord findOrCreateRecord(StudentProfile student, SyllabusTopic topic) {
    return records
        .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic.getId())
        .orElseGet(() -> records.save(new MasteryRecord(student, topic)));
  }

  private MasteryApprovedResult findExistingProjection(ApprovedMarking input) {
    return approvedResults.findBySourceSubmissionId(input.submissionId()).orElse(null);
  }

  private boolean isDuplicateOrOlderRevision(
      MasteryApprovedResult projection, ApprovedMarking input) {
    return projection != null && projection.getRevision() >= input.revision();
  }

  private boolean projectionChangesOwnership(
      MasteryApprovedResult projection, StudentProfile student, SyllabusTopic topic) {
    return projection != null
        && (!projection.getStudentProfile().getId().equals(student.getId())
            || !projection.getSyllabusTopic().getId().equals(topic.getId()));
  }

  private MasteryApprovedResult createProjection(
      ApprovedMarking input, StudentProfile student, SyllabusTopic topic, boolean isApproved) {
    return new MasteryApprovedResult(
        input.submissionId(),
        input.tutorId(),
        input.worksheetId(),
        input.worksheetQuestionId(),
        student,
        topic,
        input.approvedMarks(),
        input.availableMarks(),
        0,
        input.revision(),
        isApproved,
        input.reviewedAt());
  }

  private void replaceDiagnosticEvidence(
      ApprovedMarking input, MasteryRecord record, StudentProfile student, boolean isApproved) {
    evidence.deleteBySourceSubmissionId(input.submissionId());
    // The evidence table has a uniqueness invariant per submission and
    // mistake type. Force deletes out before a higher revision recreates
    // the same type, rather than relying on provider-specific flush order.
    evidence.flush();

    if (!isApproved) {
      return;
    }

    for (DiagnosticEvidence diagnostic : diagnosticsFor(input)) {
      MasteryDiagnosticEvidence.MistakeType mistakeType = diagnostic.canonicalMistakeType();
      evidence.save(
          new MasteryDiagnosticEvidence(
              record,
              student,
              input.tutorId(),
              input.submissionId(),
              mistakeType,
              diagnostic.category(),
              diagnostic.tutorRationale(),
              diagnostic.missingKeywords()));
    }
  }

  private void rebuild(MasteryRecord record, long studentId, long topicId) {
    List<MasteryRecord.ApprovedAttempt> attempts = new ArrayList<>();
    Map<Long, Set<MasteryDiagnosticEvidence.MistakeType>> diagnosticTypesBySubmission =
        findDiagnosticTypesBySubmission(record);
    Map<MasteryDiagnosticEvidence.MistakeType, Integer> priorOccurrences =
        new java.util.EnumMap<>(MasteryDiagnosticEvidence.MistakeType.class);
    BigDecimal currentScore = BigDecimal.ZERO.setScale(2);
    int priorAttemptCount = 0;

    List<MasteryApprovedResult> activeResults =
        approvedResults
            .findByStudentProfileIdAndSyllabusTopicIdAndActiveTrueOrderByReviewedAtAscSourceSubmissionIdAsc(
                studentId, topicId);

    for (MasteryApprovedResult result : activeResults) {
      Set<MasteryDiagnosticEvidence.MistakeType> mistakeTypes =
          diagnosticTypesBySubmission.getOrDefault(result.getSourceSubmissionId(), Set.of());
      int repeatedMistakeCount =
          mistakeTypes.stream()
              .mapToInt(type -> priorOccurrences.getOrDefault(type, 0))
              .max()
              .orElse(0);

      updateRepeatedMistakeCount(result, repeatedMistakeCount);
      MasteryCalculator.Result calculated =
          calculateMasteryResult(currentScore, priorAttemptCount, result, repeatedMistakeCount);

      attempts.add(
          new MasteryRecord.ApprovedAttempt(
              calculated.score(),
              result.getSourceSubmissionId(),
              "Tutor-approved result: "
                  + calculated.adjustedAttemptPercent()
                  + "% attempt evidence"));
      currentScore = calculated.score();
      priorAttemptCount++;
      mistakeTypes.forEach(type -> priorOccurrences.merge(type, 1, Integer::sum));
    }

    record.replaceApprovedAttempts(attempts);
  }

  private Map<Long, Set<MasteryDiagnosticEvidence.MistakeType>> findDiagnosticTypesBySubmission(
      MasteryRecord record) {
    Map<Long, Set<MasteryDiagnosticEvidence.MistakeType>> typesBySubmission = new HashMap<>();

    for (MasteryDiagnosticEvidence diagnostic : evidence.findByMasteryRecordId(record.getId())) {
      typesBySubmission
          .computeIfAbsent(
              diagnostic.getSourceSubmissionId(),
              ignored -> java.util.EnumSet.noneOf(MasteryDiagnosticEvidence.MistakeType.class))
          .add(diagnostic.getMistakeType());
    }

    return typesBySubmission;
  }

  private void updateRepeatedMistakeCount(MasteryApprovedResult result, int repeatedMistakeCount) {
    if (result.getRepeatedMistakeCount() == repeatedMistakeCount) {
      return;
    }

    result.replace(
        result.getWorksheetId(),
        result.getWorksheetQuestionId(),
        result.getApprovedMarks(),
        result.getAvailableMarks(),
        repeatedMistakeCount,
        result.getRevision(),
        true,
        result.getReviewedAt());
  }

  private MasteryCalculator.Result calculateMasteryResult(
      BigDecimal currentScore,
      int priorAttemptCount,
      MasteryApprovedResult result,
      int repeatedMistakeCount) {
    return calculator
        .calculate(
            currentScore,
            priorAttemptCount,
            result.getApprovedMarks(),
            result.getAvailableMarks(),
            repeatedMistakeCount)
        .orElseThrow(
            () -> new InvalidResultException("Approved marks and available marks are required."));
  }

  private void validate(ApprovedMarking input) {
    if (input == null) {
      throw new InvalidResultException("Approved marking is required.");
    }

    requireIdentityFields(input);
    requireValidState(input);
    requireValidMarks(input);
    requireReviewTime(input);

    List<DiagnosticEvidence> diagnostics = diagnosticsFor(input);
    if (input.state() == State.RETRACTED && !diagnostics.isEmpty()) {
      throw new InvalidResultException("Retracted results cannot include diagnostic evidence.");
    }

    diagnostics.forEach(this::validateDiagnosticEvidence);
  }

  private void requireIdentityFields(ApprovedMarking input) {
    requirePositive(input.submissionId(), "Submission id");
    requirePositive(input.tutorId(), "Tutor id");
    requirePositive(input.studentId(), "Student id");
    requirePositive(input.syllabusTopicId(), "Syllabus topic id");
  }

  private void requireValidState(ApprovedMarking input) {
    if (input.revision() <= 0 || input.state() == null) {
      throw new InvalidResultException("Approved marking revision and state are required.");
    }
  }

  private void requireValidMarks(ApprovedMarking input) {
    boolean hasInvalidMarks =
        input.approvedMarks() == null
            || input.availableMarks() == null
            || input.approvedMarks().signum() < 0
            || input.availableMarks().signum() <= 0
            || input.approvedMarks().compareTo(input.availableMarks()) > 0
            || input.approvedMarks().scale() > 2
            || input.availableMarks().scale() > 2;

    if (hasInvalidMarks) {
      throw new InvalidResultException("Approved marks must be between zero and available marks.");
    }
  }

  private void requireReviewTime(ApprovedMarking input) {
    if (input.reviewedAt() == null) {
      throw new InvalidResultException("Approved marking time is required.");
    }
  }

  private List<DiagnosticEvidence> diagnosticsFor(ApprovedMarking input) {
    return input.diagnostics() == null ? List.of() : input.diagnostics();
  }

  private void validateDiagnosticEvidence(DiagnosticEvidence diagnostic) {
    if (diagnostic == null
        || diagnostic.tutorRationale() == null
        || diagnostic.tutorRationale().isBlank()) {
      throw new InvalidResultException("Tutor-confirmed diagnostic evidence is invalid.");
    }

    try {
      diagnostic.canonicalMistakeType();
    } catch (IllegalArgumentException exception) {
      throw new InvalidResultException(exception.getMessage());
    }
    // A keyword list is supporting evidence, not a diagnostic type.  For
    // example, a tutor can record a concept weakness and cite the words
    // or phrases the student omitted.  Keep the bounded category while
    // permitting that useful, tutor-confirmed context for every type.
  }

  private static void requirePositive(Long value, String field) {
    if (value == null || value <= 0) {
      throw new InvalidResultException(field + " must be positive.");
    }
  }

  public record ApprovedResult(
      Long submissionId,
      Long tutorId,
      Long studentId,
      Long syllabusTopicId,
      BigDecimal approvedMarks,
      BigDecimal availableMarks,
      int repeatedMistakeCount,
      boolean approved) {}

  public enum State {
    APPROVED,
    RETRACTED
  }

  public record ApprovedMarking(
      Long submissionId,
      Long tutorId,
      Long worksheetId,
      Long worksheetQuestionId,
      Long studentId,
      Long syllabusTopicId,
      BigDecimal approvedMarks,
      BigDecimal availableMarks,
      int revision,
      State state,
      LocalDateTime reviewedAt,
      List<DiagnosticEvidence> diagnostics) {}

  /**
   * `mistakeType` is authoritative. `category` is accepted solely for wire compatibility and must
   * agree with the type's derived category. Old producers without a type receive the documented
   * category default.
   */
  public record DiagnosticEvidence(
      MasteryDiagnosticEvidence.MistakeType mistakeType,
      MasteryDiagnosticEvidence.Category category,
      String tutorRationale,
      List<String> missingKeywords) {
    public DiagnosticEvidence(
        MasteryDiagnosticEvidence.Category category,
        String tutorRationale,
        List<String> missingKeywords) {
      this(null, category, tutorRationale, missingKeywords);
    }

    public MasteryDiagnosticEvidence.MistakeType canonicalMistakeType() {
      if (mistakeType == null) return MasteryDiagnosticEvidence.MistakeType.legacyDefault(category);
      if (category != null && category != mistakeType.category()) {
        throw new IllegalArgumentException(
            "Diagnostic category must match the canonical mistake type.");
      }
      return mistakeType;
    }
  }

  public static class UnapprovedResultException extends RuntimeException {}

  public static class StudentNotFoundException extends RuntimeException {}

  public static class TopicNotFoundException extends RuntimeException {}

  public static class InvalidResultException extends RuntimeException {
    public InvalidResultException(String message) {
      super(message);
    }
  }
}
