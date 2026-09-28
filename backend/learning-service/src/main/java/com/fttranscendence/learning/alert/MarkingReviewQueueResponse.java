package com.fttranscendence.learning.alert;

import java.time.LocalDateTime;

/** A tutor-safe summary of a submission awaiting marking review. */
public record MarkingReviewQueueResponse(
    long submissionId,
    long studentId,
    String studentName,
    long worksheetId,
    LocalDateTime requestedAt) {
  static MarkingReviewQueueResponse from(MarkingReviewStatusProjection review) {
    return new MarkingReviewQueueResponse(
        review.getSourceSubmissionId(),
        review.getStudentProfile().getId(),
        review.getStudentProfile().getFullName(),
        review.getWorksheetId(),
        review.getRequestedAt());
  }
}
