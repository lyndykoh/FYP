package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.ocr.OcrReviewService;
import com.fttranscendence.grading.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grading")
public class OcrController {

  private final OcrReviewService reviews;

  public OcrController(OcrReviewService reviews) {
    this.reviews = reviews;
  }

  /**
   * OCR is deliberately not exposed as a context-free endpoint. Source pages are authorized and
   * persisted by {@link SubmissionDocumentController}; the review service runs OCR only after that
   * protected upload flow.
   */
  @PatchMapping("/ocr-extractions/{extractionId}")
  public ResponseEntity<Map<String, Object>> correct(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable long extractionId,
      @RequestBody Map<String, String> request) {
    String correctedText = request.get("correctedText");
    OcrExtraction extraction =
        reviews.correct(user.userId(), user.role(), extractionId, correctedText);

    Map<String, Object> response =
        Map.of(
            "id", extraction.getId(),
            "text", extraction.getCorrectedText(),
            "confidence", extraction.getConfidence(),
            "status", extraction.getStatus().name());
    return ResponseEntity.ok(response);
  }

  /**
   * Treat a foreign extraction exactly like a missing one so extraction IDs cannot be used to
   * enumerate another submitter's pages.
   */
  @ExceptionHandler(OcrReviewService.NotFound.class)
  ResponseEntity<Map<String, String>> extractionNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            Map.of(
                "code", "OCR_EXTRACTION_NOT_FOUND",
                "error", "OCR extraction was not found."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalidCorrection(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_OCR_CORRECTION", "error", exception.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<Map<String, String>> invalidState(IllegalStateException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "OCR_EXTRACTION_STATE_CONFLICT", "error", exception.getMessage()));
  }
}
