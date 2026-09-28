package com.fttranscendence.grading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * A deliberately small, deterministic keyword checker.
 *
 * <p>It does not use an AI provider or fuzzy matching. A caller may supply approved synonyms for an
 * individual rubric target; anything not in that list cannot affect a result. This makes both the
 * score and its evidence reproducible.
 */
@Service
public class RuleBasedAnswerChecker {

  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  public RuleCheckResult check(
      String answer, List<String> rubricKeywords, BigDecimal maximumMarks) {
    return check(answer, rubricKeywords, Map.of(), maximumMarks);
  }

  public RuleCheckResult check(
      String answer,
      List<String> rubricKeywords,
      Map<String, List<String>> approvedSynonyms,
      BigDecimal maximumMarks) {
    List<RubricTarget> rubricTargets = validateAndNormalizeRubric(rubricKeywords, approvedSynonyms);
    validateMaximumMarks(maximumMarks);

    String normalizedAnswer = normalize(answer);
    List<String> matchedTargets = new ArrayList<>();
    List<String> missingTargets = new ArrayList<>();

    for (RubricTarget target : rubricTargets) {
      if (target.matches(normalizedAnswer)) {
        matchedTargets.add(target.displayValue());
      } else {
        missingTargets.add(target.displayValue());
      }
    }

    BigDecimal awardedMarks =
        calculateProportionalScore(maximumMarks, matchedTargets.size(), rubricTargets.size());

    String explanation =
        "Matched " + matchedTargets.size() + " of " + rubricTargets.size() + " rubric targets.";
    return new RuleCheckResult(
        awardedMarks, maximumMarks, matchedTargets, missingTargets, explanation, List.of());
  }

  /**
   * Scores explicit marking components before any unweighted keyword fallback. Component marks are
   * authoritative allocations: their total must exactly equal the question maximum, so no
   * suggestion can silently award more than the question permits.
   *
   * <p>Only the Tutor-approved keywords on a component are candidate answer evidence. A criterion
   * description is prose guidance, never a fallback matcher; legacy components without keywords
   * therefore receive no marks until a Tutor supplies explicit evidence terms.
   */
  public RuleCheckResult checkWeighted(
      String answer, List<WeightedMarkingComponent> components, BigDecimal maximumMarks) {
    validateMaximumMarks(maximumMarks);
    List<WeightedMarkingComponent> validatedComponents =
        validateComponents(components, maximumMarks);
    String normalizedAnswer = normalize(answer);
    BigDecimal awardedMarks = ZERO;
    List<RuleCheckResult.ComponentResult> componentResults = new ArrayList<>();
    List<String> matchedComponents = new ArrayList<>();
    List<String> missingComponents = new ArrayList<>();

    for (WeightedMarkingComponent component : validatedComponents) {
      List<String> componentKeywords = component.keywords();
      boolean componentMatched =
          componentKeywords.stream().anyMatch(keyword -> containsPhrase(normalizedAnswer, keyword));

      if (componentMatched) {
        awardedMarks = awardedMarks.add(component.marks());
        matchedComponents.add(component.description());
      } else {
        missingComponents.add(component.description());
      }

      componentResults.add(createComponentResult(component, componentKeywords, componentMatched));
    }

    BigDecimal boundedAwardedMarks =
        awardedMarks.setScale(2, RoundingMode.HALF_UP).min(maximumMarks).max(BigDecimal.ZERO);
    String explanation =
        "Matched "
            + matchedComponents.size()
            + " of "
            + validatedComponents.size()
            + " weighted marking components.";
    return new RuleCheckResult(
        boundedAwardedMarks,
        maximumMarks,
        matchedComponents,
        missingComponents,
        explanation,
        componentResults);
  }

  private BigDecimal calculateProportionalScore(
      BigDecimal maximumMarks, int matchedTargetCount, int totalTargetCount) {
    return maximumMarks
        .multiply(BigDecimal.valueOf(matchedTargetCount))
        .divide(BigDecimal.valueOf(totalTargetCount), 2, RoundingMode.HALF_UP)
        .min(maximumMarks)
        .max(BigDecimal.ZERO);
  }

  private RuleCheckResult.ComponentResult createComponentResult(
      WeightedMarkingComponent component,
      List<String> componentKeywords,
      boolean componentMatched) {
    List<String> matchedTargets = componentMatched ? componentKeywords : List.of();
    List<String> missingTargets = componentMatched ? List.of() : componentKeywords;

    return new RuleCheckResult.ComponentResult(
        component.position(),
        component.description(),
        component.marks(),
        componentMatched,
        matchedTargets,
        missingTargets,
        componentFeedback(componentMatched, componentKeywords));
  }

  private String componentFeedback(boolean componentMatched, List<String> componentKeywords) {
    if (componentMatched) {
      return "Matched an approved component keyword.";
    }

    if (componentKeywords.isEmpty()) {
      return "This legacy component has no approved keywords yet.";
    }

    return "No approved component keyword was found.";
  }

  private List<WeightedMarkingComponent> validateComponents(
      List<WeightedMarkingComponent> components, BigDecimal maximumMarks) {
    if (components == null || components.isEmpty()) {
      throw new IllegalArgumentException("At least one weighted marking component is required.");
    }
    List<WeightedMarkingComponent> normalizedComponents = new ArrayList<>();
    Set<Integer> positions = new java.util.HashSet<>();
    BigDecimal allocatedMarks = BigDecimal.ZERO;

    for (WeightedMarkingComponent component : components) {
      validateComponentDetails(component);

      if (!positions.add(component.position())) {
        throw new IllegalArgumentException("Weighted marking component positions must be unique.");
      }
      if (component.marks().scale() > 2) {
        throw new IllegalArgumentException(
            "Weighted marking component marks may have at most two decimal places.");
      }

      allocatedMarks = allocatedMarks.add(component.marks());
      List<String> normalizedKeywords = normalizeComponentKeywords(component.keywords());
      normalizedComponents.add(
          new WeightedMarkingComponent(
              component.position(),
              component.description().trim(),
              component.marks(),
              normalizedKeywords));
    }

    if (allocatedMarks.compareTo(maximumMarks) > 0) {
      throw new IllegalArgumentException(
          "Weighted marking component marks cannot exceed the question total.");
    }
    if (allocatedMarks.compareTo(maximumMarks) != 0) {
      throw new IllegalArgumentException(
          "Weighted marking component marks must exactly equal the question total.");
    }

    return normalizedComponents.stream()
        .sorted(java.util.Comparator.comparingInt(WeightedMarkingComponent::position))
        .toList();
  }

  private void validateComponentDetails(WeightedMarkingComponent component) {
    boolean hasInvalidDetails =
        component == null
            || component.position() < 0
            || component.description() == null
            || component.description().isBlank()
            || component.marks() == null
            || component.marks().signum() <= 0;

    if (hasInvalidDetails) {
      throw new IllegalArgumentException(
          "Each weighted marking component needs a position, description, and positive marks.");
    }
  }

  private List<String> normalizeComponentKeywords(List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return List.of();
    }

    Map<String, String> uniqueKeywords = new LinkedHashMap<>();
    for (String keyword : keywords) {
      String normalizedKeyword = normalize(keyword);
      if (normalizedKeyword.isEmpty()) {
        throw new IllegalArgumentException("Component keywords must not be blank.");
      }
      if (uniqueKeywords.putIfAbsent(normalizedKeyword, keyword.trim()) != null) {
        throw new IllegalArgumentException("Component keywords must be unique.");
      }
    }

    return List.copyOf(uniqueKeywords.keySet());
  }

  private List<RubricTarget> validateAndNormalizeRubric(
      List<String> rubricKeywords, Map<String, List<String>> approvedSynonyms) {
    if (rubricKeywords == null || rubricKeywords.isEmpty()) {
      throw new IllegalArgumentException("A rubric must contain at least one keyword.");
    }
    if (approvedSynonyms == null) {
      throw new IllegalArgumentException("Approved synonyms must not be null.");
    }

    Map<String, String> canonicalTargetsByNormalizedValue = new LinkedHashMap<>();
    for (String keyword : rubricKeywords) {
      String normalizedKeyword = normalize(keyword);
      if (normalizedKeyword.isEmpty()) {
        throw new IllegalArgumentException("Rubric keywords must not be blank.");
      }
      if (canonicalTargetsByNormalizedValue.putIfAbsent(normalizedKeyword, keyword.trim())
          != null) {
        throw new IllegalArgumentException("Rubric keywords must be unique.");
      }
    }

    Map<String, List<String>> normalizedSynonyms = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : approvedSynonyms.entrySet()) {
      String normalizedCanonicalTarget = normalize(entry.getKey());
      if (!canonicalTargetsByNormalizedValue.containsKey(normalizedCanonicalTarget)) {
        throw new IllegalArgumentException("Synonyms must belong to a rubric keyword.");
      }
      if (entry.getValue() == null) {
        throw new IllegalArgumentException("A synonym list must not be null.");
      }

      List<String> aliases = new ArrayList<>();
      for (String synonym : entry.getValue()) {
        String normalizedSynonym = normalize(synonym);
        if (normalizedSynonym.isEmpty()) {
          throw new IllegalArgumentException("Synonyms must not be blank.");
        }
        if (!aliases.contains(normalizedSynonym)) {
          aliases.add(normalizedSynonym);
        }
      }
      normalizedSynonyms.put(normalizedCanonicalTarget, aliases);
    }

    return canonicalTargetsByNormalizedValue.entrySet().stream()
        .map(
            entry ->
                new RubricTarget(
                    entry.getValue(),
                    entry.getKey(),
                    normalizedSynonyms.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }

  private void validateMaximumMarks(BigDecimal maximumMarks) {
    if (maximumMarks == null || maximumMarks.signum() < 0) {
      throw new IllegalArgumentException("Maximum marks must be zero or greater.");
    }
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    return decomposed
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^\\p{Alnum}]+", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private record RubricTarget(String displayValue, String canonicalValue, List<String> synonyms) {
    private boolean matches(String normalizedAnswer) {
      return containsPhrase(normalizedAnswer, canonicalValue)
          || synonyms.stream().anyMatch(synonym -> containsPhrase(normalizedAnswer, synonym));
    }

    private static boolean containsPhrase(String normalizedAnswer, String phrase) {
      return RuleBasedAnswerChecker.containsPhrase(normalizedAnswer, phrase);
    }
  }

  private static boolean containsPhrase(String normalizedAnswer, String phrase) {
    return !normalizedAnswer.isEmpty()
        && !phrase.isEmpty()
        && (" " + normalizedAnswer + " ").contains(" " + phrase + " ");
  }

  /** A server-supplied mark allocation, never trusted from a browser request. */
  public record WeightedMarkingComponent(
      int position, String description, BigDecimal marks, List<String> keywords) {
    /** Legacy components must not infer targets from their descriptions. */
    public WeightedMarkingComponent(int position, String description, BigDecimal marks) {
      this(position, description, marks, List.of());
    }
  }
}
