package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.Question;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable page-by-page OCR worker. Provider calls intentionally happen after a short claim
 * transaction commits, so database locks are never held by a slow network request. Source rows
 * remain available for every retry and failure.
 */
@Service
class QuestionImportProcessingWorker {
  private static final int MAX_PAGES_PER_RUN = 100;
  private static final int MAX_DIAGRAMS_PER_PAGE = 50;
  private final QuestionImportBatchRepository batches;
  private final QuestionImportSourcePageRepository pages;
  private final QuestionImportCandidateRepository candidates;
  private final QuestionImportDiagramCropRepository diagramCrops;
  private final QuestionImportVisionService vision;
  private final EntityManager entityManager;
  private final int maximumAttempts;
  private final long retryDelayMillis;
  private final long staleRunMillis;
  private final TransactionTemplate transactions;

  QuestionImportProcessingWorker(
      QuestionImportBatchRepository batches,
      QuestionImportSourcePageRepository pages,
      QuestionImportCandidateRepository candidates,
      QuestionImportDiagramCropRepository diagramCrops,
      QuestionImportVisionService vision,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager,
      @Value("${ai.vision.max-attempts:3}") int maximumAttempts,
      @Value("${ai.vision.retry-delay-ms:60000}") long retryDelayMillis,
      @Value("${ai.vision.stale-run-ms:180000}") long staleRunMillis) {
    this.batches = batches;
    this.pages = pages;
    this.candidates = candidates;
    this.diagramCrops = diagramCrops;
    this.vision = vision;
    this.entityManager = entityManager;
    this.maximumAttempts = maximumAttempts;
    this.retryDelayMillis = retryDelayMillis;
    this.staleRunMillis = staleRunMillis;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Scheduled(fixedDelayString = "${ai.vision.worker-delay-ms:5000}")
  void processQueuedBatches() {
    ProcessBatch batch = transactions.execute(status -> claimNextBatch());
    if (batch == null) return;

    for (PageWork page : batch.pages()) {
      if (page.alreadyProcessed()) continue;
      QuestionVisionAnalyzer.PageAnalysis result;
      try {
        result = vision.analyze(page.image());
      } catch (RuntimeException exception) {
        transactions.executeWithoutResult(
            status ->
                recordFailure(
                    batch.id(), page.id(), "Vision OCR failed before returning a result."));
        return;
      }
      if (result.failed()) {
        transactions.executeWithoutResult(
            status -> recordFailure(batch.id(), page.id(), result.failureMessage()));
        return;
      }
      try {
        transactions.executeWithoutResult(status -> storeResult(batch.id(), page.id(), result));
      } catch (QuestionImportDiagramCropper.InvalidCropException exception) {
        transactions.executeWithoutResult(
            status -> recordFailure(batch.id(), page.id(), exception.getMessage()));
        return;
      }
    }
    transactions.executeWithoutResult(status -> finishIfComplete(batch.id()));
  }

  @Transactional
  ProcessBatch claimNextBatch() {
    validateWorkerConfiguration();
    // Recover one abandoned claim per scheduled run. Do not immediately
    // claim it again: that would consume another attempt in the same
    // worker tick and make a crash look like two provider failures.
    if (recoverOneStaleBatch()) return null;
    List<QuestionImportBatch> ready =
        batches.findReadyToClaim(
            QuestionImportBatch.Status.QUEUED, LocalDateTime.now(), PageRequest.of(0, 1));
    if (ready.isEmpty()) return null;
    QuestionImportBatch batch = ready.get(0);
    batch.running();
    entityManager.flush();
    List<PageWork> pageWork =
        pages.findAllForBatch(batch.getId()).stream()
            .limit(MAX_PAGES_PER_RUN)
            .map(
                page ->
                    new PageWork(
                        page.getId(),
                        new QuestionVisionAnalyzer.PageImage(
                            page.getPageImageBytes(),
                            page.getContentType(),
                            page.getWidth(),
                            page.getHeight()),
                        candidates.existsBySourcePage_Id(page.getId())))
            .toList();
    return new ProcessBatch(batch.getId(), pageWork);
  }

  @Transactional
  void storeResult(long batchId, long pageId, QuestionVisionAnalyzer.PageAnalysis result) {
    if (candidates.existsBySourcePage_Id(pageId)) return;
    QuestionImportBatch batch = batches.findById(batchId).orElseThrow();
    QuestionImportSourcePage page = pages.findByIdAndBatch_Id(pageId, batchId).orElseThrow();
    if (result.candidates().isEmpty()) {
      candidates.save(
          new QuestionImportCandidate(
              batch,
              page,
              1,
              QuestionImportCandidate.Status.UNCERTAIN,
              0,
              "Vision OCR did not identify a question. Transcribe and review this source image before importing.",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.FOUNDATION,
              "image-import",
              "",
              ""));
      return;
    }
    int diagramCount =
        result.candidates().stream().mapToInt(candidate -> candidate.diagrams().size()).sum();
    if (diagramCount > MAX_DIAGRAMS_PER_PAGE) {
      throw new QuestionImportDiagramCropper.InvalidCropException(
          "Too many diagram regions were returned for one page.");
    }
    for (QuestionVisionAnalyzer.Candidate candidate : result.candidates()) {
      boolean hasAnswer = !candidate.modelAnswer().isBlank();
      String warning = candidate.warningMessage().isBlank() ? null : candidate.warningMessage();
      QuestionImportCandidate storedCandidate =
          candidates.save(
              new QuestionImportCandidate(
                  batch,
                  page,
                  candidate.number(),
                  hasAnswer && candidate.confidence() >= 70
                      ? QuestionImportCandidate.Status.READY_FOR_REVIEW
                      : QuestionImportCandidate.Status.UNCERTAIN,
                  candidate.confidence(),
                  warning,
                  candidate.questionType(),
                  candidate.difficulty(),
                  String.join(", ", candidate.tags()),
                  candidate.prompt(),
                  candidate.modelAnswer()));
      persistDiagramCrops(batch, page, storedCandidate, candidate.diagrams());
    }
  }

  private void persistDiagramCrops(
      QuestionImportBatch batch,
      QuestionImportSourcePage page,
      QuestionImportCandidate candidate,
      List<QuestionVisionAnalyzer.Diagram> diagrams) {
    if (diagrams.size() > 10) {
      throw new QuestionImportDiagramCropper.InvalidCropException(
          "Too many diagram regions were returned.");
    }
    Set<String> regionIds = new HashSet<>();
    for (QuestionVisionAnalyzer.Diagram diagram : diagrams) {
      validateDiagramIdentifier("diagram region ID", diagram.regionId(), regionIds);
      if (diagram.subQuestionId() != null) {
        validateDiagramIdentifier("sub-question ID", diagram.subQuestionId(), null);
      }
      if (diagramCrops.existsByCandidate_IdAndDiagramRegionId(
          candidate.getId(), diagram.regionId())) {
        continue;
      }
      QuestionImportDiagramCropper.Crop crop =
          QuestionImportDiagramCropper.crop(page, diagram.boundingBox());
      diagramCrops.save(
          new QuestionImportDiagramCrop(
              batch,
              page,
              candidate,
              diagram.regionId(),
              diagram.subQuestionId(),
              diagram.boundingBox(),
              crop.bytes(),
              crop.width(),
              crop.height()));
    }
  }

  private void validateDiagramIdentifier(String label, String value, Set<String> uniqueValues) {
    if (value == null
        || value.length() > 64
        || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
        || uniqueValues != null && !uniqueValues.add(value)) {
      throw new QuestionImportDiagramCropper.InvalidCropException(
          "Invalid " + label + " in OCR result.");
    }
  }

  @Transactional
  void recordFailure(long batchId, long pageId, String error) {
    QuestionImportBatch batch = batches.findById(batchId).orElseThrow();
    QuestionImportSourcePage page = pages.findByIdAndBatch_Id(pageId, batchId).orElseThrow();
    String safeError =
        error == null ? "Vision OCR failed." : error.substring(0, Math.min(error.length(), 1000));
    page.setProcessingError(safeError);
    if (batch.getAttemptCount() >= maximumAttempts) {
      if (!candidates.existsBySourcePage_Id(pageId)) {
        candidates.save(
            new QuestionImportCandidate(
                batch,
                page,
                1,
                QuestionImportCandidate.Status.UNCERTAIN,
                0,
                safeError,
                Question.QuestionType.OPEN_ENDED,
                Question.Difficulty.FOUNDATION,
                "image-import",
                "",
                ""));
      }
      // The retained page and uncertain draft are the manual-review
      // fallback; a terminal provider failure must not lock a tutor out.
      batch.readyForManualReview(safeError);
      return;
    }
    batch.retryAt(LocalDateTime.now().plusNanos(retryDelayMillis * 1_000_000), safeError);
  }

  @Transactional
  void finishIfComplete(long batchId) {
    QuestionImportBatch batch = batches.findById(batchId).orElseThrow();
    boolean allPagesHaveReviewData =
        pages.findAllForBatch(batchId).stream()
            .allMatch(page -> candidates.existsBySourcePage_Id(page.getId()));
    if (allPagesHaveReviewData) batch.readyForReview();
  }

  private boolean recoverOneStaleBatch() {
    LocalDateTime before = LocalDateTime.now().minusNanos(staleRunMillis * 1_000_000);
    List<QuestionImportBatch> stale =
        batches.findStaleRunning(QuestionImportBatch.Status.RUNNING, before, PageRequest.of(0, 1));
    if (stale.isEmpty()) return false;
    QuestionImportBatch batch = stale.get(0);
    if (batch.getAttemptCount() >= maximumAttempts) {
      batch.failed("Vision OCR worker stopped before the batch completed.");
    } else {
      batch.retryAt(
          LocalDateTime.now(), "Vision OCR worker stopped before the batch completed; retrying.");
    }
    return true;
  }

  private void validateWorkerConfiguration() {
    if (maximumAttempts < 1
        || maximumAttempts > 10
        || retryDelayMillis < 1
        || retryDelayMillis > 3_600_000
        || staleRunMillis < 1_000
        || staleRunMillis > 3_600_000) {
      throw new IllegalArgumentException("Invalid AI vision worker retry configuration.");
    }
  }

  record ProcessBatch(long id, List<PageWork> pages) {}

  record PageWork(long id, QuestionVisionAnalyzer.PageImage image, boolean alreadyProcessed) {}
}
