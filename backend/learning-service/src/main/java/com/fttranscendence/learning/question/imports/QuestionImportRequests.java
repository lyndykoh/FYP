package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.Question;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class QuestionImportRequests {
  private QuestionImportRequests() {}

  public record CandidateUpdate(
      @Size(max = 120) String code,
      @Positive Long syllabusTopicId,
      @Size(max = 4000) String prompt,
      @Size(max = 4000) String modelAnswer,
      @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal totalMarks,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      @NotNull Boolean includeSourceImage) {}

  public record ImportCandidates(
      @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> candidateIds) {}

  public record RejectCandidate(@Size(max = 500) String reason) {}

  public record MoveCrops(
      @NotEmpty @Size(max = 10) List<@NotNull @Positive Long> cropIds,
      @NotNull @Positive Long targetCandidateId) {}

  public record MergeCandidates(
      @NotNull @Positive Long targetCandidateId,
      @NotEmpty @Size(min = 2, max = 10) List<@NotNull @Positive Long> candidateIds) {}

  public record SplitCandidate(@NotEmpty @Size(max = 10) List<@NotNull @Valid SplitDraft> drafts) {}

  public record SplitDraft(@Size(max = 4000) String prompt, @Size(max = 4000) String modelAnswer) {}
}
