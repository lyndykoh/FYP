package com.fttranscendence.grading.controller;

/**
 * Retained as a source-compatible marker for the former prototype endpoint.
 *
 * <p>The old endpoint accepted arbitrary student/question values and used a hard-coded rubric.
 * Marking is now exposed only by {@link MarkingReviewController}, which resolves the authoritative
 * question data and records an advisory review.
 */
@Deprecated(forRemoval = false)
public final class SubmissionController {
  private SubmissionController() {}
}
