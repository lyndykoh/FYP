package com.fttranscendence.learning.security;

import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central owner-scoped lookup boundary for protected Learning resources.
 *
 * <p>Foreign and absent resources deliberately produce the same exception so caller-facing APIs do
 * not reveal whether an identifier belongs to another Tutor or Student.
 */
@Service
public class DomainAuthorizationService {
  private final StudentProfileRepository students;
  private final TutorClassRepository classes;
  private final WorksheetRepository worksheets;

  public DomainAuthorizationService(
      StudentProfileRepository students,
      TutorClassRepository classes,
      WorksheetRepository worksheets) {
    this.students = students;
    this.classes = classes;
    this.worksheets = worksheets;
  }

  @Transactional(readOnly = true)
  public StudentProfile requireTutorOwnedStudent(long tutorUserId, long studentId) {
    requirePositive(tutorUserId, "Tutor id");
    requirePositive(studentId, "Student id");
    return students
        .findByIdAndTutorId(studentId, tutorUserId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public StudentProfile requireStudentSelf(long loginUserId) {
    requirePositive(loginUserId, "Student login id");
    return students.findByLoginUserId(loginUserId).orElseThrow(ResourceNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public TutorClass requireTutorOwnedClass(long tutorUserId, long classId) {
    requirePositive(tutorUserId, "Tutor id");
    requirePositive(classId, "Class id");
    return classes
        .findByIdAndTutorId(classId, tutorUserId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /**
   * The grading service asks this backend-only check before it stores source material. Missing and
   * foreign resources deliberately resolve to the same outcome so upload attempts cannot enumerate
   * identifiers.
   */
  @Transactional(readOnly = true)
  public void requireSubmissionContext(
      long actorUserId,
      ActorRole actorRole,
      long studentId,
      long worksheetId,
      Long worksheetQuestionId,
      Long classId) {
    requirePositive(actorUserId, "Actor id");
    requirePositive(studentId, "Student id");
    requirePositive(worksheetId, "Worksheet id");
    StudentProfile student =
        students.findById(studentId).orElseThrow(ResourceNotFoundException::new);
    if (actorRole == ActorRole.STUDENT) {
      if (!Long.valueOf(actorUserId).equals(student.getLoginUserId()))
        throw new ResourceNotFoundException();
    } else if (actorRole == ActorRole.TUTOR) {
      if (!Long.valueOf(actorUserId).equals(student.getTutorId()))
        throw new ResourceNotFoundException();
      if (classId == null
          || classId <= 0
          || classes.findByIdAndTutorId(classId, actorUserId).isEmpty()
          || student.getMemberships().stream()
              .noneMatch(membership -> classId.equals(membership.getClassId()))) {
        throw new ResourceNotFoundException();
      }
    } else {
      throw new ResourceNotFoundException();
    }
    Worksheet worksheet =
        worksheets.findById(worksheetId).orElseThrow(ResourceNotFoundException::new);
    if (worksheet.getStatus() != Worksheet.Status.APPROVED
        || !student.getTutorId().equals(worksheet.getTutorId())
        || !isAssignedTo(worksheet, student, classId)
        || !containsQuestion(worksheet, worksheetQuestionId)) {
      throw new ResourceNotFoundException();
    }
  }

  /**
   * Resolves the authoritative, assigned worksheet rubric for the grading service. This is
   * intentionally an internal contract: model answers and marking criteria must never be sent to
   * the Student browser.
   */
  @Transactional(readOnly = true)
  public SubmissionMarkingContext requireSubmissionMarkingContext(
      long actorUserId, ActorRole actorRole, long studentId, long worksheetId, Long classId) {
    requireSubmissionContext(actorUserId, actorRole, studentId, worksheetId, null, classId);
    Worksheet worksheet =
        worksheets.findById(worksheetId).orElseThrow(ResourceNotFoundException::new);
    if (worksheet.getQuestions().isEmpty()) {
      throw new ResourceNotFoundException();
    }
    return new SubmissionMarkingContext(
        worksheet.getTutorId(),
        worksheet.getQuestions().stream()
            .map(
                worksheetQuestion -> {
                  Question question = worksheetQuestion.getQuestion();
                  return new MarkingQuestion(
                      question.getId(),
                      question.getPrompt(),
                      question.getModelAnswer(),
                      question.getTotalMarks(),
                      question.getKeywords(),
                      question.getMarkingComponents().stream()
                          .map(
                              component ->
                                  new MarkingComponent(
                                      component.getPosition(),
                                      component.getDescription(),
                                      component.getMarks(),
                                      component.getKeywords()))
                          .toList(),
                      question.getSyllabusTopic().getId(),
                      question.getSyllabusTopic().getCode());
                })
            .toList());
  }

  private boolean isAssignedTo(Worksheet worksheet, StudentProfile student, Long classId) {
    if (classId != null) {
      boolean classAssignment =
          worksheet.getAssignments().stream()
              .anyMatch(
                  assignment ->
                      assignment.getAssignmentType() == Worksheet.AudienceType.CLASS
                          && classId.equals(assignment.getClassId()));
      boolean directAssignment =
          worksheet.getAssignments().stream()
              .anyMatch(
                  assignment ->
                      assignment.getAssignmentType() == Worksheet.AudienceType.STUDENT
                          && student.getId().equals(assignment.getStudentProfileId()));
      return classAssignment || (directAssignment && classId.equals(worksheet.getSourceClassId()));
    }
    return worksheet.getAssignments().stream()
        .anyMatch(
            assignment ->
                (assignment.getAssignmentType() == Worksheet.AudienceType.STUDENT
                        && student.getId().equals(assignment.getStudentProfileId()))
                    || (assignment.getAssignmentType() == Worksheet.AudienceType.CLASS
                        && student.getMemberships().stream()
                            .anyMatch(
                                membership ->
                                    assignment.getClassId().equals(membership.getClassId()))));
  }

  private boolean containsQuestion(Worksheet worksheet, Long worksheetQuestionId) {
    return worksheetQuestionId == null
        || worksheet.getQuestions().stream()
            .anyMatch(question -> worksheetQuestionId.equals(question.getId()));
  }

  private static void requirePositive(long value, String name) {
    if (value <= 0) throw new IllegalArgumentException(name + " must be positive.");
  }

  public static class ResourceNotFoundException extends RuntimeException {}

  public enum ActorRole {
    TUTOR,
    STUDENT
  }

  public record SubmissionMarkingContext(
      Long tutorUserId, java.util.List<MarkingQuestion> questions) {}

  public record MarkingQuestion(
      Long questionBankId,
      String prompt,
      String modelAnswer,
      java.math.BigDecimal totalMarks,
      java.util.List<String> keywords,
      java.util.List<MarkingComponent> markingComponents,
      Long syllabusTopicId,
      String syllabusTopicCode) {}

  /**
   * Server-to-server rubric criterion. Position is the persisted criterion identity used by Grading
   * for deterministic per-component marks.
   */
  public record MarkingComponent(
      int position,
      String description,
      java.math.BigDecimal marks,
      java.util.List<String> keywords) {}
}
