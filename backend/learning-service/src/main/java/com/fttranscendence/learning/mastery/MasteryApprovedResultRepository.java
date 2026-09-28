package com.fttranscendence.learning.mastery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasteryApprovedResultRepository
    extends JpaRepository<MasteryApprovedResult, Long> {
  Optional<MasteryApprovedResult> findBySourceSubmissionId(Long sourceSubmissionId);

  List<MasteryApprovedResult>
      findByStudentProfileIdAndSyllabusTopicIdAndActiveTrueOrderByReviewedAtAscSourceSubmissionIdAsc(
          Long studentProfileId, Long syllabusTopicId);

  Optional<MasteryApprovedResult>
      findFirstByStudentProfileIdAndActiveTrueOrderByReviewedAtDescSourceSubmissionIdDesc(
          Long studentProfileId);

  List<MasteryApprovedResult>
      findByStudentProfileIdAndWorksheetIdAndActiveTrueOrderByReviewedAtAscSourceSubmissionIdAsc(
          Long studentProfileId, Long worksheetId);

  /**
   * A worksheet becomes visible as having an approved result only after an active, Tutor-approved
   * projection is stored locally. The tutor filter is intentional: a profile response must never
   * aggregate another Tutor's data, even if an invalid cross-owner projection were present.
   */
  @Query(
      """
        SELECT COUNT(DISTINCT result.worksheetId)
        FROM MasteryApprovedResult result
        WHERE result.tutorId = :tutorId
          AND result.studentProfile.id = :studentProfileId
          AND result.active = true
          AND result.worksheetId IS NOT NULL
        """)
  long countDistinctActiveWorksheetIdsByTutorAndStudent(
      @Param("tutorId") Long tutorId, @Param("studentProfileId") Long studentProfileId);
}
