package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.model.SubmissionPage;
import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.ocr.OcrReviewService;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.MarkingReviewService;
import com.fttranscendence.grading.storage.DocumentStorage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Stores source pages only after learning-service authorizes the target student. */
@RestController
@RequestMapping("/api/grading/submission-documents")
public class SubmissionDocumentController {
  private final SubmissionDocumentRepository documents;
  private final DocumentStorage storage;
  private final OcrReviewService review;
  private final LearningAuthorizationClient authorization;
  private final OcrExtractionRepository extractions;
  private final MarkingReviewService markingReviews;

  public SubmissionDocumentController(
      SubmissionDocumentRepository documents,
      DocumentStorage storage,
      OcrReviewService review,
      LearningAuthorizationClient authorization,
      OcrExtractionRepository extractions,
      MarkingReviewService markingReviews) {
    this.documents = documents;
    this.storage = storage;
    this.review = review;
    this.authorization = authorization;
    this.extractions = extractions;
    this.markingReviews = markingReviews;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional(rollbackFor = Exception.class)
  public ResponseEntity<DocumentResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam long studentId,
      @RequestParam long worksheetId,
      @RequestParam(required = false) Long worksheetQuestionId,
      @RequestParam(required = false) Long classId,
      @RequestParam("files") List<MultipartFile> files)
      throws Exception {
    // Students must resolve to themselves; Tutors must resolve to a student
    // in their own learning-service scope.  The grading service never trusts
    // a client-supplied studentId without this check.
    validateTutorClassSelection(user, classId);
    authorization.assertCanSubmit(user, studentId, worksheetId, worksheetQuestionId, classId);
    SubmissionDocument.SourceType sourceType = validateAndGetSourceType(files);
    SubmissionDocument document =
        createDocument(user.userId(), user.role(), worksheetId, studentId, classId, sourceType);
    // Repository.save may merge and return a different managed aggregate.
    // Keep that instance so subsequent OCR extractions always reference
    // persisted SubmissionPage records rather than detached transient pages.
    document = documents.saveAndFlush(document);
    List<String> storedKeys = new ArrayList<>();
    try {
      storeUploadedPages(document, user.userId(), files, storedKeys);
      document = documents.saveAndFlush(document);

      List<OcrExtraction> documentExtractions =
          extractDocumentText(document, user.userId(), worksheetQuestionId);
      document.markReady();
      documents.saveAndFlush(document);
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(DocumentResponse.of(document, documentExtractions));
    } catch (Exception exception) {
      // Database changes are rolled back by @Transactional; remove any
      // already-written bytes too so a failed OCR attempt leaves no
      // orphaned upload data behind.
      for (String storageKey : storedKeys) {
        try {
          storage.delete(user.userId(), storageKey);
        } catch (RuntimeException ignored) {
          // Preserve the original validation/storage/OCR failure.
        }
      }
      throw exception;
    }
  }

  private static void validateTutorClassSelection(AuthenticatedUser user, Long classId) {
    boolean isTutor = "TUTOR".equals(user.role());
    boolean hasSelectedClass = classId != null && classId > 0;

    if (isTutor && !hasSelectedClass) {
      throw new IllegalArgumentException("Tutors must select the student's class.");
    }
  }

  private static SubmissionDocument.SourceType validateAndGetSourceType(List<MultipartFile> files) {
    if (files.isEmpty()) {
      throw new IllegalArgumentException("At least one page is required.");
    }

    boolean containsPdf =
        files.stream().anyMatch(file -> "application/pdf".equals(file.getContentType()));
    if (containsPdf && files.size() != 1) {
      throw new IllegalArgumentException("A PDF submission must be uploaded alone.");
    }

    return containsPdf ? SubmissionDocument.SourceType.PDF : SubmissionDocument.SourceType.IMAGES;
  }

  private static SubmissionDocument createDocument(
      long ownerUserId,
      String ownerRole,
      long worksheetId,
      long studentId,
      Long classId,
      SubmissionDocument.SourceType sourceType) {
    return new SubmissionDocument(
        ownerUserId,
        SubmissionDocument.OwnerRole.valueOf(ownerRole),
        worksheetId,
        studentId,
        classId,
        sourceType);
  }

  private void storeUploadedPages(
      SubmissionDocument document,
      long ownerUserId,
      List<MultipartFile> files,
      List<String> storedKeys)
      throws Exception {
    for (MultipartFile file : files) {
      DocumentStorage.StoredFile storedFile =
          storage.store(
              ownerUserId,
              Objects.requireNonNullElse(file.getOriginalFilename(), "page"),
              file.getContentType(),
              file.getBytes());
      storedKeys.add(storedFile.storageKey());
      document.addPage(storedFile);
    }
  }

  private List<OcrExtraction> extractDocumentText(
      SubmissionDocument document, long ownerUserId, Long worksheetQuestionId) {
    List<OcrExtraction> documentExtractions = new ArrayList<>();
    for (SubmissionPage page : document.getPages()) {
      byte[] pageContents = storage.read(ownerUserId, page.getStorageKey());
      OcrExtraction extraction = review.extract(page, worksheetQuestionId, pageContents);
      documentExtractions.add(extraction);
    }

    return documentExtractions;
  }

  /**
   * OCR review is resumed from durable server-side document/page state, not from browser memory or
   * a client-supplied worksheet context.
   */
  @GetMapping("/{documentId}")
  @Transactional(readOnly = true)
  public DocumentResponse get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long documentId) {
    SubmissionDocument.OwnerRole ownerRole = SubmissionDocument.OwnerRole.valueOf(user.role());
    SubmissionDocument document =
        documents
            .findByIdAndOwnerUserIdAndOwnerRole(documentId, user.userId(), ownerRole)
            .orElseThrow(DocumentNotFound::new);
    return DocumentResponse.of(
        document, extractions.findByPageDocumentIdOrderByPagePageNumberAsc(documentId));
  }

  /** A Student explicitly confirms corrected OCR and sends it to the assigned Tutor queue. */
  @PostMapping(
      value = "/{documentId}/submit-for-review",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MarkingReviewService.SubmissionForTutorReviewResponse> submitForReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long documentId,
      @RequestBody MarkingReviewService.OcrSubmissionRequest request) {
    var reviewResponse = markingReviews.submitOcrForTutorReview(user, documentId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(reviewResponse);
  }

  /**
   * Captures typed answers using the same Submission records as OCR. The selected student and
   * worksheet are independently authorized by Learning before a draft is read or written.
   */
  @PostMapping(value = "/manual-answers", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<MarkingReviewService.ManualAnswerResponse> saveManualAnswers(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody MarkingReviewService.ManualAnswerRequest request) {
    var answerResponse = markingReviews.saveManualAnswers(user, request);
    return ResponseEntity.ok(answerResponse);
  }

  @GetMapping("/manual-answers")
  public MarkingReviewService.ManualAnswerResponse loadManualAnswers(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam long studentId,
      @RequestParam long worksheetId,
      @RequestParam(required = false) Long classId) {
    return markingReviews.loadManualAnswers(user, studentId, worksheetId, classId);
  }

  public record PageResponse(
      long id,
      int pageNumber,
      String originalFilename,
      String mediaType,
      long extractionId,
      String text,
      double confidence,
      String status) {}

  public record DocumentResponse(
      long id,
      Long classId,
      long studentId,
      long worksheetId,
      Long uploadedByTutorId,
      String status,
      LocalDateTime createdAt,
      List<PageResponse> pages) {
    static DocumentResponse of(SubmissionDocument document, List<OcrExtraction> extractions) {
      Map<Long, OcrExtraction> extractionsByPageId =
          extractions.stream()
              .collect(
                  Collectors.toMap(
                      extraction -> extraction.getPage().getId(), Function.identity()));
      return new DocumentResponse(
          document.getId(),
          document.getClassId(),
          document.getStudentId(),
          document.getWorksheetId(),
          document.getOwnerRole() == SubmissionDocument.OwnerRole.TUTOR
              ? document.getOwnerUserId()
              : null,
          document.getStatus().name(),
          document.getCreatedAt(),
          document.getPages().stream()
              .map(
                  page -> {
                    OcrExtraction extraction = extractionsByPageId.get(page.getId());
                    return pageResponse(page, extraction);
                  })
              .toList());
    }
  }

  private static PageResponse pageResponse(SubmissionPage page, OcrExtraction extraction) {
    if (extraction == null) {
      throw new IllegalStateException("Submission page is missing its OCR extraction");
    }

    String displayedText =
        extraction.getCorrectedText() == null
            ? extraction.getExtractedText()
            : extraction.getCorrectedText();
    return new PageResponse(
        page.getId(),
        page.getPageNumber(),
        page.getOriginalFilename(),
        page.getMediaType(),
        extraction.getId(),
        displayedText,
        extraction.getConfidence(),
        extraction.getStatus().name());
  }

  private static final class DocumentNotFound extends RuntimeException {}

  @ExceptionHandler(LearningAuthorizationClient.Forbidden.class)
  ResponseEntity<Map<String, String>> forbidden() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            Map.of(
                "code",
                "SUBMISSION_FORBIDDEN",
                "error",
                "You are not allowed to submit for this student."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> bad(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_SUBMISSION_DOCUMENT", "error", exception.getMessage()));
  }

  @ExceptionHandler(DocumentNotFound.class)
  ResponseEntity<Map<String, String>> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            Map.of(
                "code",
                "SUBMISSION_DOCUMENT_NOT_FOUND",
                "error",
                "Submission document was not found."));
  }

  @ExceptionHandler(MarkingReviewService.ReviewNotFound.class)
  ResponseEntity<Map<String, String>> reviewDocumentNotFound() {
    return notFound();
  }

  @ExceptionHandler(LearningAuthorizationClient.SubmissionMarkingContextUnavailable.class)
  ResponseEntity<Map<String, String>> markingContextUnavailable() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            Map.of(
                "code",
                "SUBMISSION_MARKING_CONTEXT_UNAVAILABLE",
                "error",
                "The worksheet marking context is temporarily unavailable."));
  }

  @ExceptionHandler(LearningAuthorizationClient.SubmissionMarkingContextNotFound.class)
  ResponseEntity<Map<String, String>> markingContextNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            Map.of(
                "code",
                "SUBMISSION_MARKING_CONTEXT_NOT_FOUND",
                "error",
                "This worksheet submission could not be loaded. Please try again."));
  }

  @ExceptionHandler(MarkingReviewService.InvalidReviewRequest.class)
  ResponseEntity<Map<String, String>> invalidReviewRequest(
      MarkingReviewService.InvalidReviewRequest exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_OCR_SUBMISSION", "error", exception.getMessage()));
  }
}
