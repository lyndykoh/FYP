package com.fttranscendence.learning.alert;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkingReviewStatusProjectionRepository
    extends JpaRepository<MarkingReviewStatusProjection, Long> {
  List<MarkingReviewStatusProjection>
      findByTutorIdAndStateAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
          Long tutorId, MarkingReviewStatusProjection.State state, LocalDateTime requestedAt);

  long countByTutorIdAndState(Long tutorId, MarkingReviewStatusProjection.State state);

  List<MarkingReviewStatusProjection>
      findByTutorIdAndStateOrderByRequestedAtDescSourceSubmissionIdAsc(
          Long tutorId, MarkingReviewStatusProjection.State state);

  List<MarkingReviewStatusProjection>
      findByStudentProfileIdAndWorksheetIdOrderByRequestedAtAscSourceSubmissionIdAsc(
          Long studentProfileId, Long worksheetId);
}
