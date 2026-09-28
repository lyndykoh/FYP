package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.MarkingReviewService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading")
public class MarkingReviewController {
  private final MarkingReviewService reviews;

  public MarkingReviewController(MarkingReviewService reviews) {
    this.reviews = reviews;
  }

  /** The Tutor's authoritative queue, filtered by Learning's current directory. */
  @GetMapping("/tutor/reviews")
  public java.util.List<MarkingReviewService.TutorReviewQueueItem> listPending(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer) {
    return reviews.listPendingReviews(user, bearer);
  }

  /** Opens source metadata for a canonical submitted answer without exposing drafts. */
  @GetMapping("/tutor/reviews/{submissionId}/source")
  public MarkingReviewService.SubmittedSource source(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId) {
    return reviews.sourceForReview(user, bearer, submissionId);
  }

  /** Streams an original student image or PDF page after review-scope authorization. */
  @GetMapping("/tutor/reviews/{submissionId}/source/pages/{pageId}")
  public ResponseEntity<byte[]> sourcePage(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId,
      @PathVariable long pageId) {
    MarkingReviewService.SourcePageContent source =
        reviews.sourcePageForReview(user, bearer, submissionId, pageId);
    ContentDisposition disposition =
        ContentDisposition.inline().filename(source.filename(), StandardCharsets.UTF_8).build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(source.mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(source.content());
  }

  @PostMapping("/tutor/reviews")
  public ResponseEntity<MarkingReviewService.MarkingReview> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @RequestBody MarkingReviewService.CreateRequest request) {
    MarkingReviewService.MarkingReview review = reviews.createAdvisoryReview(user, bearer, request);
    return created(review);
  }

  @PostMapping("/tutor/reviews/manual")
  public ResponseEntity<MarkingReviewService.MarkingReview> createManualResult(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @RequestBody MarkingReviewService.ManualResultRequest request) {
    MarkingReviewService.MarkingReview review = reviews.createManualResult(user, bearer, request);
    return created(review);
  }

  @PostMapping("/tutor/reviews/manual/batch")
  public ResponseEntity<java.util.List<MarkingReviewService.MarkingReview>> createManualResults(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @RequestBody MarkingReviewService.ManualResultBatchRequest request) {
    var reviewsForWorksheet = reviews.createManualResults(user, bearer, request);
    return created(reviewsForWorksheet);
  }

  @GetMapping("/tutor/reviews/manual/worksheets/{worksheetId}")
  public MarkingReviewService.ManualResultsResponse listManualResults(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long worksheetId) {
    return reviews.listManualResults(user, bearer, worksheetId);
  }

  @GetMapping("/tutor/reviews/{submissionId}")
  public MarkingReviewService.MarkingReview get(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId) {
    return reviews.get(user, bearer, submissionId);
  }

  @PostMapping("/tutor/reviews/{submissionId}/approve")
  public MarkingReviewService.MarkingReview approve(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId,
      @RequestBody MarkingReviewService.ApprovalRequest request) {
    return reviews.approve(user, bearer, submissionId, request);
  }

  @PostMapping("/tutor/reviews/{submissionId}/flag")
  public MarkingReviewService.MarkingReview flag(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId,
      @RequestBody MarkingReviewService.FlagRequest request) {
    return reviews.flag(user, bearer, submissionId, request);
  }

  @PostMapping("/tutor/reviews/{submissionId}/reset")
  public MarkingReviewService.MarkingReview reset(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long submissionId) {
    return reviews.reset(user, bearer, submissionId);
  }

  /**
   * A learner receives only their own final, Tutor-approved result data. Pending and flagged
   * answers deliberately remain visible as review-needed without exposing provisional AI feedback
   * or suggested marks.
   */
  @GetMapping("/student/worksheets/{worksheetId}/results")
  public MarkingReviewService.StudentResultsResponse studentResults(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long worksheetId) {
    return reviews.studentResults(user, bearer, worksheetId);
  }

  @ExceptionHandler(MarkingReviewService.ReviewNotFound.class)
  ResponseEntity<Map<String, String>> notFound() {
    return error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND", "Review was not found.");
  }

  @ExceptionHandler(LearningAuthorizationClient.Forbidden.class)
  ResponseEntity<Map<String, String>> forbidden() {
    return error(
        HttpStatus.FORBIDDEN, "REVIEW_FORBIDDEN", "You are not allowed to review this submission.");
  }

  @ExceptionHandler(LearningAuthorizationClient.QuestionUnavailable.class)
  ResponseEntity<Map<String, String>> unavailableQuestion() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "QUESTION_CONTEXT_UNAVAILABLE",
        "Question context is temporarily unavailable.");
  }

  @ExceptionHandler(LearningAuthorizationClient.TutorStudentDirectoryUnavailable.class)
  ResponseEntity<Map<String, String>> tutorDirectoryUnavailable() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "TUTOR_STUDENT_DIRECTORY_UNAVAILABLE",
        "The Tutor student directory is temporarily unavailable.");
  }

  @ExceptionHandler(LearningAuthorizationClient.ManualResultContextNotFound.class)
  ResponseEntity<Map<String, String>> manualContextNotFound() {
    return error(
        HttpStatus.NOT_FOUND,
        "MANUAL_RESULT_CONTEXT_NOT_FOUND",
        "Worksheet result context was not found.");
  }

  @ExceptionHandler(LearningAuthorizationClient.StudentWorksheetNotFound.class)
  ResponseEntity<Map<String, String>> studentWorksheetNotFound() {
    return error(
        HttpStatus.NOT_FOUND, "STUDENT_WORKSHEET_NOT_FOUND", "Worksheet results were not found.");
  }

  @ExceptionHandler(MarkingReviewService.ManualResultAlreadyExists.class)
  ResponseEntity<Map<String, String>> duplicateManualResult() {
    return error(
        HttpStatus.CONFLICT,
        "MANUAL_RESULT_EXISTS",
        "A manual result already exists for this student and question.");
  }

  @ExceptionHandler(MarkingReviewService.InvalidManualResultRequest.class)
  ResponseEntity<Map<String, String>> invalidManualResult(
      MarkingReviewService.InvalidManualResultRequest exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_MANUAL_RESULT", exception.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> malformedRequest() {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_REVIEW_REQUEST",
        "Review request contains invalid JSON or values.");
  }

  @ExceptionHandler({
    MarkingReviewService.InvalidReviewRequest.class,
    IllegalArgumentException.class
  })
  ResponseEntity<Map<String, String>> invalidRequest(RuntimeException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REVIEW_REQUEST", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Map<String, String>> invalidState(IllegalStateException exception) {
    return error(HttpStatus.CONFLICT, "INVALID_REVIEW_STATE", exception.getMessage());
  }

  private ResponseEntity<Map<String, String>> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "error", message));
  }

  private static <T> ResponseEntity<T> created(T responseBody) {
    return ResponseEntity.status(HttpStatus.CREATED).body(responseBody);
  }
}
