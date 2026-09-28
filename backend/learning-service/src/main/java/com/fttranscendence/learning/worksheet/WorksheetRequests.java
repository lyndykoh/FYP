package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.question.Question;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorksheetRequests {
  private WorksheetRequests() {}

  public record GenerateWorksheetRequest(
      @NotNull WorksheetGenerationRequest.TargetMode targetMode,
      @Size(max = 100) List<@Positive Long> topicIds,
      @NotNull @Positive @Max(100) Integer questionCount,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      @Future LocalDateTime dueAt,
      @Size(max = 200) String title,
      @Size(max = 2000) String instructions,
      @Size(max = 100) List<@Positive Long> studentIds,
      Worksheet.WorksheetType worksheetType) {
    /** Keeps the established standard-generation wire shape source compatible. */
    public GenerateWorksheetRequest(
        WorksheetGenerationRequest.TargetMode targetMode,
        List<Long> topicIds,
        Integer questionCount,
        Question.QuestionType questionType,
        LocalDateTime dueAt,
        String title,
        String instructions,
        List<Long> studentIds) {
      this(
          targetMode,
          topicIds,
          questionCount,
          questionType,
          null,
          dueAt,
          title,
          instructions,
          studentIds,
          Worksheet.WorksheetType.STANDARD);
    }

    public GenerateWorksheetRequest(
        WorksheetGenerationRequest.TargetMode targetMode,
        List<Long> topicIds,
        Integer questionCount,
        Question.QuestionType questionType,
        LocalDateTime dueAt,
        String title,
        String instructions,
        List<Long> studentIds,
        Worksheet.WorksheetType worksheetType) {
      this(
          targetMode,
          topicIds,
          questionCount,
          questionType,
          null,
          dueAt,
          title,
          instructions,
          studentIds,
          worksheetType);
    }
  }

  /**
   * Diagnostics derive their eligible topics and their explanation from persisted mastery evidence.
   * A tutor still selects the audience and explicitly starts generation; this request can never
   * approve or assign a worksheet.
   */
  public record GenerateDiagnosticWorksheetRequest(
      @NotNull WorksheetGenerationRequest.TargetMode targetMode,
      @NotEmpty @Size(max = 100) List<@Positive Long> topicIds,
      @NotNull @Positive @Max(100) Integer questionCount,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      @Future LocalDateTime dueAt,
      @Size(max = 200) String title,
      @Size(max = 2000) String instructions,
      @Size(max = 100) List<@Positive Long> studentIds) {
    public GenerateDiagnosticWorksheetRequest(
        WorksheetGenerationRequest.TargetMode targetMode,
        List<Long> topicIds,
        Integer questionCount,
        Question.QuestionType questionType,
        LocalDateTime dueAt,
        String title,
        String instructions,
        List<Long> studentIds) {
      this(
          targetMode,
          topicIds,
          questionCount,
          questionType,
          null,
          dueAt,
          title,
          instructions,
          studentIds);
    }
  }

  public record UpdateWorksheetRequest(
      @Size(max = 200) String title,
      @Size(max = 2000) String instructions,
      @Size(min = 1, max = 100) List<@Positive Long> questionIds) {}

  public record ApproveWorksheetRequest(@Future LocalDateTime dueAt) {}

  public record ImageSummary(Long id, String filename, String contentType, int width, int height) {}

  public record QuestionSummary(
      Long id,
      String code,
      String prompt,
      Question.QuestionType questionType,
      BigDecimal totalMarks,
      Long syllabusTopicId,
      String syllabusTopicName,
      List<ImageSummary> images) {}

  /** Assignment data is only exposed through the owner-scoped Tutor worksheet endpoint. */
  public record AssignmentSummary(
      Long id,
      Worksheet.AudienceType assignmentType,
      Long classId,
      Long studentProfileId,
      LocalDateTime assignedAt,
      LocalDateTime dueAt) {}

  public record WorksheetResponse(
      Long id,
      String code,
      String title,
      String instructions,
      String subject,
      Worksheet.WorksheetType worksheetType,
      Worksheet.AudienceType audienceType,
      Worksheet.Status status,
      Long generationRequestId,
      Long sourceClassId,
      List<QuestionSummary> questions,
      List<AssignmentSummary> assignments) {
    static WorksheetResponse from(Worksheet worksheet) {
      return new WorksheetResponse(
          worksheet.getId(),
          worksheet.getCode(),
          worksheet.getTitle(),
          worksheet.getInstructions(),
          worksheet.getSubject(),
          worksheet.getWorksheetType(),
          worksheet.getAudienceType(),
          worksheet.getStatus(),
          worksheet.getGenerationRequestId(),
          worksheet.getSourceClassId(),
          worksheet.getQuestions().stream()
              .map(
                  item -> {
                    Question question = item.getQuestion();
                    return new QuestionSummary(
                        question.getId(),
                        item.getQuestionCodeSnapshot() == null
                            ? question.getCode()
                            : item.getQuestionCodeSnapshot(),
                        item.getPromptSnapshot() == null
                            ? question.getPrompt()
                            : item.getPromptSnapshot(),
                        item.getQuestionTypeSnapshot() == null
                            ? question.getQuestionType()
                            : item.getQuestionTypeSnapshot(),
                        item.getTotalMarksSnapshot() == null
                            ? question.getTotalMarks()
                            : item.getTotalMarksSnapshot(),
                        question.getSyllabusTopic().getId(),
                        question.getSyllabusTopic().getName(),
                        imageSummaries(item));
                  })
              .toList(),
          worksheet.getAssignments().stream()
              .map(
                  assignment ->
                      new AssignmentSummary(
                          assignment.getId(),
                          assignment.getAssignmentType(),
                          assignment.getClassId(),
                          assignment.getStudentProfileId(),
                          assignment.getAssignedAt(),
                          assignment.getDueAt()))
              .toList());
    }
  }

  public record GenerationRequestResponse(
      Long id,
      Long classId,
      WorksheetGenerationRequest.TargetMode targetMode,
      List<Long> topicIds,
      List<Long> studentIds,
      int questionCount,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      LocalDateTime dueAt,
      WorksheetGenerationRequest.Status status,
      String failureCode,
      String failureMessage,
      WorksheetResponse worksheet) {}

  /**
   * Learner-safe worksheet summary: it excludes answer text, AI output and tutor-only assignment
   * details.
   */
  public record StudentWorksheetLibraryItem(
      Long id,
      String code,
      String title,
      List<TopicSummary> subjects,
      List<TopicSummary> topics,
      LocalDateTime assignedAt,
      LocalDateTime dueAt,
      StudentWorksheetStatus status,
      LocalDateTime submittedAt,
      LocalDateTime reviewedAt,
      ScoreSummary score) {}

  /**
   * Learner-safe worksheet content. Prompts and marks are intentionally available so an assigned
   * learner can complete the work; model answers, required keywords, tutor ownership and the full
   * assignment roster are never included in this response.
   */
  public record StudentWorksheetDetail(
      Long id,
      String code,
      String title,
      String instructions,
      String subject,
      List<QuestionSummary> questions,
      LocalDateTime assignedAt,
      LocalDateTime dueAt) {
    static StudentWorksheetDetail from(Worksheet worksheet, WorksheetAssignment assignment) {
      return new StudentWorksheetDetail(
          worksheet.getId(),
          worksheet.getCode(),
          worksheet.getTitle(),
          worksheet.getInstructions(),
          worksheet.getSubject(),
          worksheet.getQuestions().stream()
              .map(
                  item -> {
                    Question question = item.getQuestion();
                    return new QuestionSummary(
                        question.getId(),
                        item.getQuestionCodeSnapshot() == null
                            ? question.getCode()
                            : item.getQuestionCodeSnapshot(),
                        item.getPromptSnapshot() == null
                            ? question.getPrompt()
                            : item.getPromptSnapshot(),
                        item.getQuestionTypeSnapshot() == null
                            ? question.getQuestionType()
                            : item.getQuestionTypeSnapshot(),
                        item.getTotalMarksSnapshot() == null
                            ? question.getTotalMarks()
                            : item.getTotalMarksSnapshot(),
                        question.getSyllabusTopic().getId(),
                        question.getSyllabusTopic().getName(),
                        imageSummaries(item));
                  })
              .toList(),
          assignment.getAssignedAt(),
          assignment.getDueAt());
    }
  }

  public record TopicSummary(Long id, String name) {}

  /**
   * Marks and percent are kept together so the client never mistakes a percentage for raw marks.
   */
  public record ScoreSummary(BigDecimal earned, BigDecimal available, BigDecimal percent) {}

  public enum StudentWorksheetStatus {
    ASSIGNED,
    SUBMITTED,
    MARKED
  }

  private static List<ImageSummary> imageSummaries(WorksheetQuestion item) {
    return item.getImages().stream()
        .map(
            image ->
                new ImageSummary(
                    image.getId(),
                    image.getOriginalFilename(),
                    image.getContentType(),
                    image.getWidth(),
                    image.getHeight()))
        .toList();
  }
}
