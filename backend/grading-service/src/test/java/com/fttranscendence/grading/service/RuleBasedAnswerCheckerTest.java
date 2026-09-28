package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedAnswerCheckerTest {

  private final RuleBasedAnswerChecker checker = new RuleBasedAnswerChecker();

  @Test
  void awardsAllMarksWhenEveryRubricKeywordIsPresent() {
    RuleCheckResult result =
        checker.check(
            "A metal conductor transfers heat energy.",
            List.of("metal conductor", "heat energy"),
            new BigDecimal("4.00"));

    assertEquals(new BigDecimal("4.00"), result.awardedMarks());
    assertEquals(List.of("metal conductor", "heat energy"), result.matchedKeywords());
    assertEquals(List.of(), result.missingKeywords());
    assertEquals("Matched 2 of 2 rubric targets.", result.explanation());
  }

  @Test
  void awardsAProportionalScoreForPartialKeywords() {
    RuleCheckResult result =
        checker.check(
            "Plants use sunlight.", List.of("sunlight", "carbon dioxide"), new BigDecimal("6.00"));

    assertEquals(new BigDecimal("3.00"), result.awardedMarks());
    assertEquals(List.of("sunlight"), result.matchedKeywords());
    assertEquals(List.of("carbon dioxide"), result.missingKeywords());
  }

  @Test
  void matchesApprovedSynonymsRegardlessOfCaseAndPunctuation() {
    RuleCheckResult result =
        checker.check(
            "METAL is conductive; it TRANSFERS heat!",
            List.of("conductor", "heat transfer"),
            Map.of(
                "conductor", List.of("conductive"),
                "heat transfer", List.of("transfers heat")),
            new BigDecimal("2.00"));

    assertEquals(new BigDecimal("2.00"), result.awardedMarks());
    assertEquals(List.of("conductor", "heat transfer"), result.matchedKeywords());
  }

  @Test
  void givesNoMarksForABlankAnswer() {
    RuleCheckResult result = checker.check("  ", List.of("gravity"), new BigDecimal("5.00"));

    assertEquals(new BigDecimal("0.00"), result.awardedMarks());
    assertEquals(List.of("gravity"), result.missingKeywords());
  }

  @Test
  void repeatedKeywordsOnlyCountOnce() {
    RuleCheckResult result =
        checker.check("Heat, heat, HEAT!", List.of("heat", "energy"), new BigDecimal("5.00"));

    assertEquals(new BigDecimal("2.50"), result.awardedMarks());
    assertEquals(List.of("heat"), result.matchedKeywords());
  }

  @Test
  void rejectsMalformedRubrics() {
    assertThrows(
        IllegalArgumentException.class,
        () -> checker.check("answer", List.of(" "), new BigDecimal("1.00")));
    assertThrows(
        IllegalArgumentException.class,
        () -> checker.check("answer", List.of("Heat", "heat"), new BigDecimal("1.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            checker.check(
                "answer",
                List.of("heat"),
                Map.of("energy", List.of("power")),
                new BigDecimal("1.00")));
  }

  @Test
  void scoreNeverExceedsTheAllocatedMarksAtBoundaries() {
    RuleCheckResult result =
        checker.check(
            "energy heat light", List.of("energy", "heat", "light"), new BigDecimal("1.00"));

    assertEquals(new BigDecimal("1.00"), result.awardedMarks());
    assertEquals(0, result.awardedMarks().compareTo(result.maximumMarks()));
  }

  @Test
  void evaluatesWeightedComponentsBeforeKeywordFallbackAndReturnsPerComponentEvidence() {
    RuleCheckResult result =
        checker.checkWeighted(
            "The heat conduction moves energy through the metal.",
            List.of(
                new RuleBasedAnswerChecker.WeightedMarkingComponent(
                    0, "Explains heat conduction", new BigDecimal("2.00")),
                new RuleBasedAnswerChecker.WeightedMarkingComponent(
                    1,
                    "States energy transfer",
                    new BigDecimal("1.00"),
                    List.of("energy transfer"))),
            new BigDecimal("3.00"));

    assertEquals(new BigDecimal("0.00"), result.awardedMarks());
    assertEquals(List.of(), result.matchedKeywords());
    assertEquals(
        List.of("Explains heat conduction", "States energy transfer"), result.missingKeywords());
    assertEquals(2, result.componentResults().size());
    assertEquals(false, result.componentResults().get(0).matched());
    assertEquals(false, result.componentResults().get(1).matched());
    assertEquals(
        "This legacy component has no approved keywords yet.",
        result.componentResults().get(0).feedback());
    assertEquals(
        "No approved component keyword was found.", result.componentResults().get(1).feedback());
  }

  @Test
  void scoresOnlyExplicitComponentKeywordsAndNeverTheCriterionDescription() {
    RuleCheckResult result =
        checker.checkWeighted(
            "The metal is conductive and transfers heat.",
            List.of(
                new RuleBasedAnswerChecker.WeightedMarkingComponent(
                    0,
                    "Explains heat conduction",
                    new BigDecimal("2.00"),
                    List.of("conductive", "transfers heat"))),
            new BigDecimal("2.00"));

    assertEquals(new BigDecimal("2.00"), result.awardedMarks());
    assertEquals(
        List.of("conductive", "transfers heat"), result.componentResults().get(0).matchedTargets());
  }

  @Test
  void rejectsComponentMarksThatDoNotExactlyAndSafelyAllocateQuestionTotal() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            checker.checkWeighted(
                "answer",
                List.of(
                    new RuleBasedAnswerChecker.WeightedMarkingComponent(
                        0, "Explains answer", new BigDecimal("2.00"))),
                new BigDecimal("1.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            checker.checkWeighted(
                "answer",
                List.of(
                    new RuleBasedAnswerChecker.WeightedMarkingComponent(
                        0, "Explains answer", new BigDecimal("1.00"))),
                new BigDecimal("2.00")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            checker.checkWeighted(
                "answer",
                List.of(
                    new RuleBasedAnswerChecker.WeightedMarkingComponent(
                        0, "A", new BigDecimal("1.00")),
                    new RuleBasedAnswerChecker.WeightedMarkingComponent(
                        0, "B", new BigDecimal("1.00"))),
                new BigDecimal("2.00")));
  }
}
