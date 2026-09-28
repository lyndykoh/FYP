package com.fttranscendence.learning.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Complete replacement payload for the tutor-maintained, shared question bank. The syllabus node
 * type is deliberately not client controlled: the service resolves it from the supplied topic ID.
 */
public record QuestionRequest(
    @NotBlank @Size(max = 120) String code,
    @NotNull @Positive Long syllabusTopicId,
    @NotNull Question.QuestionType questionType,
    Question.Difficulty difficulty,
    @NotBlank @Size(max = 4000) String prompt,
    @NotNull @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2) BigDecimal totalMarks,
    @NotBlank @Size(max = 4000) String modelAnswer,
    Question.ArchiveState archiveState,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid MarkingComponentRequest> markingComponents,
    @Size(max = 100) List<@NotBlank @Size(max = 80) String> keywords) {
  public record MarkingComponentRequest(
      @NotBlank @Size(max = 1000) String description,
      @NotNull @DecimalMin(value = "0.01") @Digits(integer = 4, fraction = 2) BigDecimal marks,
      @Size(max = 100) List<@NotBlank @Size(max = 80) String> keywords) {
    public MarkingComponentRequest {
      keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
  }
}
