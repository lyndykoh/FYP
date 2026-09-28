package com.fttranscendence.learning.mastery;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MasteryCalculatorTest {
  private final MasteryCalculator calculator = new MasteryCalculator();

  @Test
  void calculatesWeightedApprovedEvidenceAndBoundaries() {
    assertEquals(
        new BigDecimal("75.00"),
        calculator
            .calculate(BigDecimal.ZERO, 0, new BigDecimal("3"), new BigDecimal("4"), 0)
            .orElseThrow()
            .score());
    assertEquals(
        new BigDecimal("62.50"),
        calculator
            .calculate(new BigDecimal("75"), 1, new BigDecimal("2"), new BigDecimal("4"), 0)
            .orElseThrow()
            .score());
    assertEquals(
        new BigDecimal("100.00"),
        calculator
            .calculate(BigDecimal.ZERO, 0, BigDecimal.ONE, BigDecimal.ONE, 0)
            .orElseThrow()
            .score());
  }

  @Test
  void handlesMissingDataAndRepeatedMistakesDeterministically() {
    assertTrue(calculator.calculate(null, 0, BigDecimal.ONE, BigDecimal.ONE, 0).isEmpty());
    assertEquals(
        new BigDecimal("37.50"),
        calculator
            .calculate(new BigDecimal("75"), 1, BigDecimal.ONE, BigDecimal.ONE, 1)
            .orElseThrow()
            .score());
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(BigDecimal.ZERO, 0, new BigDecimal("2"), BigDecimal.ONE, 0));
  }

  @Test
  void usesTheUnroundedAttemptPercentageForTheRunningAverage() {
    MasteryCalculator.Result result =
        calculator
            .calculate(BigDecimal.ZERO, 1, new BigDecimal("2"), new BigDecimal("3"), 0)
            .orElseThrow();

    assertEquals(new BigDecimal("33.33"), result.score());
    assertEquals(new BigDecimal("66.67"), result.adjustedAttemptPercent());
  }
}
