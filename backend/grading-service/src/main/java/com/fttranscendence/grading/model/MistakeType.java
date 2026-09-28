package com.fttranscendence.grading.model;

import java.util.Locale;

/**
 * Canonical, controlled mistake types used for tutor-approved learning history.
 *
 * <p>The AI error category on {@link Submission} remains a free-form advisory value for backwards
 * compatibility. Persisted {@link MistakeRecord}s use this enum so analytics cannot be fragmented
 * by spelling variations.
 */
public enum MistakeType {
  CONCEPT_MISUNDERSTANDING("Concept misunderstanding", DiagnosticCategory.CONCEPT),
  CALCULATION_ERROR("Calculation error", DiagnosticCategory.APPLICATION),
  MISREAD_QUESTION("Misread question", DiagnosticCategory.APPLICATION),
  INCOMPLETE_WORKING("Incomplete working", DiagnosticCategory.APPLICATION),
  INCORRECT_FORMULA("Incorrect formula", DiagnosticCategory.APPLICATION),
  CARELESS_MISTAKE("Careless mistake", DiagnosticCategory.APPLICATION),
  WEAK_EXPLANATION("Weak explanation", DiagnosticCategory.EXPRESSION),
  MISSING_KEY_POINT("Missing key point", DiagnosticCategory.KEYWORD),
  WRONG_UNITS("Wrong units", DiagnosticCategory.EXPRESSION),
  ANSWER_FORMAT_ISSUE("Answer format issue", DiagnosticCategory.EXPRESSION);

  private final String label;
  private final DiagnosticCategory diagnosticCategory;

  MistakeType(String label, DiagnosticCategory diagnosticCategory) {
    this.label = label;
    this.diagnosticCategory = diagnosticCategory;
  }

  public String getLabel() {
    return label;
  }

  /**
   * Compatibility grouping for learning analytics. It is always derived from this canonical type
   * and is never selected independently.
   */
  public DiagnosticCategory getDiagnosticCategory() {
    return diagnosticCategory;
  }

  public static MistakeType fromLabel(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Mistake type is required");
    }
    String normalized = value.trim();
    for (MistakeType type : values()) {
      if (type.label.equalsIgnoreCase(normalized) || type.name().equalsIgnoreCase(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unsupported mistake type: " + value);
  }

  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }
}
