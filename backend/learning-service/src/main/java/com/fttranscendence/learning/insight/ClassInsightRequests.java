package com.fttranscendence.learning.insight;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class ClassInsightRequests {
  private ClassInsightRequests() {}

  public record CoveredTopicsRequest(@NotNull List<@NotNull Long> topicIds) {}

  public record SettingsRequest(
      @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weakAverageMasteryPercent,
      @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weakStudentRatioPercent,
      @Min(1) @Max(10000) int minimumActiveStudents) {}

  public record FeedbackRequest(@NotBlank @Size(max = 1000) String feedback) {}

  public record RankingRequest(@Min(1) int rank, @Size(max = 500) String note) {}
}
