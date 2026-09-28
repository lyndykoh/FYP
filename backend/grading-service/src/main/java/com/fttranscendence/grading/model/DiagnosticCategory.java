package com.fttranscendence.grading.model;

/**
 * Derived compatibility groupings that may be shared with Learning.
 *
 * <p>These values deliberately do not mirror the free-form advisory category returned by an AI
 * provider. A tutor selects a canonical {@link MistakeType}; the server derives this bounded
 * grouping before it becomes learning evidence.
 */
public enum DiagnosticCategory {
  CONCEPT,
  KEYWORD,
  EXPRESSION,
  APPLICATION
}
