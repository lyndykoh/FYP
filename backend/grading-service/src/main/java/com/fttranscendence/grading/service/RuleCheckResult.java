package com.fttranscendence.grading.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The reproducible outcome of checking an answer against a keyword rubric.
 *
 * <p>The matched and missing lists are intentionally returned with the result so callers can show
 * the exact basis for an automatic score.
 */
public record RuleCheckResult(
    BigDecimal awardedMarks,
    BigDecimal maximumMarks,
    List<String> matchedKeywords,
    List<String> missingKeywords,
    String explanation,
    List<ComponentResult> componentResults) {
  public RuleCheckResult {
    if (awardedMarks == null || maximumMarks == null) {
      throw new IllegalArgumentException("Awarded and maximum marks are required.");
    }
    boolean isBelowZero = awardedMarks.signum() < 0;
    boolean exceedsMaximum = awardedMarks.compareTo(maximumMarks) > 0;
    if (isBelowZero || exceedsMaximum) {
      throw new IllegalArgumentException("Awarded marks must be between zero and the maximum.");
    }
    matchedKeywords = List.copyOf(matchedKeywords);
    missingKeywords = List.copyOf(missingKeywords);
    componentResults = List.copyOf(componentResults);
  }

  /**
   * Evidence for one weighted marking component. It is returned even when no component is matched
   * so a Tutor can see exactly why the deterministic suggestion did not allocate the component's
   * marks.
   */
  public record ComponentResult(
      int position,
      String description,
      BigDecimal maximumMarks,
      boolean matched,
      List<String> matchedTargets,
      List<String> missingTargets,
      String feedback) {
    public ComponentResult {
      matchedTargets = List.copyOf(matchedTargets);
      missingTargets = List.copyOf(missingTargets);
    }
  }
}
