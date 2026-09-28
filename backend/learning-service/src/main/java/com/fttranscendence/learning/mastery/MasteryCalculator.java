package com.fttranscendence.learning.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic mastery formula.
 *
 * <p>Each approved result contributes {@code (awarded / available) * 100}, treated as zero when the
 * same marked mistake is repeated. The stored score is the arithmetic mean of that adjusted result
 * and all prior approved attempts for the topic. Values are rounded half-up to two decimal places.
 * Missing score data produces no calculation.
 */
@Component
public final class MasteryCalculator {
  private static final BigDecimal HUNDRED = new BigDecimal("100.00");

  public Optional<Result> calculate(
      BigDecimal previousScore,
      int priorApprovedAttempts,
      BigDecimal awardedMarks,
      BigDecimal availableMarks,
      int repeatedMistakeCount) {
    if (hasMissingScoreData(previousScore, awardedMarks, availableMarks)) {
      return Optional.empty();
    }

    if (hasInvalidCalculationInputs(
        previousScore, priorApprovedAttempts, awardedMarks, availableMarks, repeatedMistakeCount)) {
      throw new IllegalArgumentException("Mastery calculation inputs are invalid");
    }

    BigDecimal unroundedAttemptPercent =
        calculateAdjustedAttemptPercent(awardedMarks, availableMarks, repeatedMistakeCount);
    BigDecimal score =
        calculateAverageScore(previousScore, priorApprovedAttempts, unroundedAttemptPercent);
    BigDecimal adjustedAttemptPercent = unroundedAttemptPercent.setScale(2, RoundingMode.HALF_UP);

    return Optional.of(new Result(score, adjustedAttemptPercent));
  }

  private boolean hasMissingScoreData(
      BigDecimal previousScore, BigDecimal awardedMarks, BigDecimal availableMarks) {
    return previousScore == null || awardedMarks == null || availableMarks == null;
  }

  private boolean hasInvalidCalculationInputs(
      BigDecimal previousScore,
      int priorApprovedAttempts,
      BigDecimal awardedMarks,
      BigDecimal availableMarks,
      int repeatedMistakeCount) {
    return priorApprovedAttempts < 0
        || repeatedMistakeCount < 0
        || availableMarks.signum() <= 0
        || awardedMarks.signum() < 0
        || awardedMarks.compareTo(availableMarks) > 0
        || previousScore.signum() < 0
        || previousScore.compareTo(HUNDRED) > 0;
  }

  private BigDecimal calculateAdjustedAttemptPercent(
      BigDecimal awardedMarks, BigDecimal availableMarks, int repeatedMistakeCount) {
    if (repeatedMistakeCount > 0) {
      return BigDecimal.ZERO;
    }

    return awardedMarks.multiply(HUNDRED).divide(availableMarks, 6, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateAverageScore(
      BigDecimal previousScore, int priorApprovedAttempts, BigDecimal adjustedAttemptPercent) {
    BigDecimal previousTotal = previousScore.multiply(BigDecimal.valueOf(priorApprovedAttempts));
    BigDecimal totalScore = previousTotal.add(adjustedAttemptPercent);
    BigDecimal attemptCount = BigDecimal.valueOf(priorApprovedAttempts + 1L);

    return totalScore.divide(attemptCount, 2, RoundingMode.HALF_UP);
  }

  public record Result(BigDecimal score, BigDecimal adjustedAttemptPercent) {}
}
