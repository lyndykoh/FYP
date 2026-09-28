package com.fttranscendence.learning.question.imports;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface QuestionImportBatchRepository extends Repository<QuestionImportBatch, Long> {
  <S extends QuestionImportBatch> S save(S batch);

  Optional<QuestionImportBatch> findById(Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select batch from QuestionImportBatch batch where batch.id = :batchId")
  Optional<QuestionImportBatch> findByIdForReviewUpdate(Long batchId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
        select batch from QuestionImportBatch batch
        where batch.status = :status
          and (batch.nextAttemptAt is null or batch.nextAttemptAt <= :now)
        order by batch.createdAt asc, batch.id asc
        """)
  List<QuestionImportBatch> findReadyToClaim(
      QuestionImportBatch.Status status,
      LocalDateTime now,
      org.springframework.data.domain.Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
        select batch from QuestionImportBatch batch
        where batch.status = :status and batch.processingStartedAt <= :before
        order by batch.processingStartedAt asc, batch.id asc
        """)
  List<QuestionImportBatch> findStaleRunning(
      QuestionImportBatch.Status status,
      LocalDateTime before,
      org.springframework.data.domain.Pageable pageable);
}

interface QuestionImportSourcePageRepository extends Repository<QuestionImportSourcePage, Long> {
  <S extends QuestionImportSourcePage> S save(S page);

  Optional<QuestionImportSourcePage> findByIdAndBatch_Id(Long id, Long batchId);

  @Query(
      """
        select page from QuestionImportSourcePage page
        where page.batch.id = :batchId order by page.sourceFilename, page.sourcePageNumber
        """)
  List<QuestionImportSourcePage> findAllForBatch(Long batchId);
}

interface QuestionImportCandidateRepository extends Repository<QuestionImportCandidate, Long> {
  <S extends QuestionImportCandidate> S save(S candidate);

  @Query(
      """
        select candidate from QuestionImportCandidate candidate
        join fetch candidate.sourcePage page
        left join fetch candidate.syllabusTopic topic
        where candidate.batch.id = :batchId
        order by page.sourceFilename asc, page.sourcePageNumber asc, candidate.candidateNumber asc
        """)
  List<QuestionImportCandidate> findAllForBatch(Long batchId);

  Optional<QuestionImportCandidate> findByIdAndBatch_Id(Long id, Long batchId);

  @Query(
      "select coalesce(max(candidate.candidateNumber), 0) from QuestionImportCandidate candidate where candidate.sourcePage.id = :sourcePageId")
  int findHighestNumberForSourcePage(Long sourcePageId);

  boolean existsBySourcePage_Id(Long sourcePageId);

  @Query(
      """
        select candidate from QuestionImportCandidate candidate
        join fetch candidate.sourcePage page
        left join fetch candidate.syllabusTopic topic
        left join fetch candidate.importedQuestion importedQuestion
        where candidate.status = com.fttranscendence.learning.question.imports.QuestionImportCandidate.Status.IMPORTED
        """)
  List<QuestionImportCandidate> findAllImportedForDuplicateComparison();
}

interface QuestionImportDiagramCropRepository extends Repository<QuestionImportDiagramCrop, Long> {
  <S extends QuestionImportDiagramCrop> S save(S crop);

  boolean existsByCandidate_IdAndDiagramRegionId(Long candidateId, String diagramRegionId);

  Optional<QuestionImportDiagramCrop> findByIdAndBatch_Id(Long id, Long batchId);

  @Query(
      """
        select crop from QuestionImportDiagramCrop crop
        join fetch crop.sourcePage page
        where crop.candidate.id = :candidateId
        order by crop.diagramRegionId asc
        """)
  List<QuestionImportDiagramCrop> findAllForCandidate(Long candidateId);

  @Query(
      """
        select crop from QuestionImportDiagramCrop crop
        join fetch crop.candidate candidate
        join fetch crop.sourcePage page
        where crop.batch.id = :batchId
        order by candidate.candidateNumber asc, crop.diagramRegionId asc
        """)
  List<QuestionImportDiagramCrop> findAllForBatch(Long batchId);
}
