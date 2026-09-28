package com.fttranscendence.grading.repository;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
  @Override
  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  List<Submission> findAll();

  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  List<Submission> findByStudentIdOrderByCreatedAtDesc(Long studentId);

  /** Newest submission per worksheet question is the learner's current result. */
  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  List<Submission> findByStudentIdAndWorksheetIdOrderByCreatedAtDescIdDesc(
      Long studentId, Long worksheetId);

  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  List<Submission> findBySubmissionDocumentOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  List<Submission> findByReviewStatusOrderByCreatedAtAsc(Submission.ReviewStatus reviewStatus);

  /**
   * The Tutor queue is constrained in SQL by Learning's current student directory, so other Tutors'
   * pending answers are never loaded first.
   */
  @EntityGraph(attributePaths = {"submissionDocument", "submissionDocument.pages"})
  List<Submission> findByReviewStatusAndStudentIdInAndSubmissionDocumentStatusOrderByCreatedAtAsc(
      Submission.ReviewStatus reviewStatus,
      Collection<Long> studentIds,
      SubmissionDocument.Status submissionDocumentStatus);

  @EntityGraph(attributePaths = {"missingKeywords", "reviews"})
  Optional<Submission> findBySubmissionDocumentIdAndWorksheetQuestionId(
      Long submissionDocumentId, Long worksheetQuestionId);

  List<Submission> findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(
      Long submissionDocumentId);

  /**
   * Manual marks are deliberately scoped through their Tutor-owned document. This prevents a
   * worksheet identifier alone from exposing another Tutor's result history.
   */
  @EntityGraph(attributePaths = {"missingKeywords", "reviews", "submissionDocument"})
  List<Submission>
      findByWorksheetIdAndSubmissionDocumentOwnerUserIdAndSubmissionDocumentOwnerRoleAndSubmissionDocumentSourceTypeOrderByCreatedAtAsc(
          Long worksheetId,
          Long ownerUserId,
          SubmissionDocument.OwnerRole ownerRole,
          SubmissionDocument.SourceType sourceType);
}
