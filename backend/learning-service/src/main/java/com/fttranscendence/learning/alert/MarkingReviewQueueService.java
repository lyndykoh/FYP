package com.fttranscendence.learning.alert;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkingReviewQueueService {
  private final MarkingReviewStatusProjectionRepository reviews;

  public MarkingReviewQueueService(MarkingReviewStatusProjectionRepository reviews) {
    this.reviews = reviews;
  }

  @Transactional(readOnly = true)
  public List<MarkingReviewQueueResponse> pendingForTutor(long tutorId) {
    if (tutorId <= 0) {
      throw new IllegalArgumentException("Tutor id must be positive.");
    }
    return reviews
        .findByTutorIdAndStateOrderByRequestedAtDescSourceSubmissionIdAsc(
            tutorId, MarkingReviewStatusProjection.State.PENDING_REVIEW)
        .stream()
        .map(MarkingReviewQueueResponse::from)
        .toList();
  }
}
