package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.classroom.ClassController;
import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Tutor-only, review-gated ingestion. This controller never creates a Question during upload. */
@RestController
@RequestMapping(
    value = "/api/learning/tutor/question-imports",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class QuestionImportController {
  private final QuestionImportService imports;

  public QuestionImportController(QuestionImportService imports) {
    this.imports = imports;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<QuestionImportService.BatchDetail> upload(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestPart("files") List<MultipartFile> files) {
    return ResponseEntity.status(HttpStatus.CREATED).body(imports.upload(user.userId(), files));
  }

  @GetMapping("/{batchId}")
  public QuestionImportService.BatchDetail get(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long batchId) {
    return imports.get(user.userId(), batchId);
  }

  @GetMapping("/{batchId}/source-pages/{pageId}/image")
  public ResponseEntity<byte[]> sourceImage(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long pageId) {
    QuestionImportService.ImageContent image =
        imports.sourcePageImage(user.userId(), batchId, pageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @GetMapping("/{batchId}/diagram-crops/{cropId}/image")
  public ResponseEntity<byte[]> diagramCropImage(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long cropId) {
    QuestionImportService.ImageContent image =
        imports.diagramCropImage(user.userId(), batchId, cropId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PutMapping(
      value = "/{batchId}/candidates/{candidateId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.CandidateDetail update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long candidateId,
      @Valid @RequestBody QuestionImportRequests.CandidateUpdate request) {
    return imports.update(user.userId(), batchId, candidateId, request);
  }

  @PostMapping(
      value = "/{batchId}/candidates/{candidateId}/reject",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.BatchDetail reject(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long candidateId,
      @Valid @RequestBody QuestionImportRequests.RejectCandidate request) {
    return imports.reject(user.userId(), batchId, candidateId, request);
  }

  @PostMapping("/{batchId}/candidates/{candidateId}/restore")
  public QuestionImportService.BatchDetail restore(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long candidateId) {
    return imports.restore(user.userId(), batchId, candidateId);
  }

  @PostMapping(value = "/{batchId}/diagram-crops/move", consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.BatchDetail moveCrops(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @Valid @RequestBody QuestionImportRequests.MoveCrops request) {
    return imports.moveCrops(user.userId(), batchId, request);
  }

  @PostMapping(value = "/{batchId}/candidates/merge", consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.BatchDetail merge(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @Valid @RequestBody QuestionImportRequests.MergeCandidates request) {
    return imports.merge(user.userId(), batchId, request);
  }

  @PostMapping(
      value = "/{batchId}/candidates/{candidateId}/split",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.BatchDetail split(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @PathVariable @Positive long candidateId,
      @Valid @RequestBody QuestionImportRequests.SplitCandidate request) {
    return imports.split(user.userId(), batchId, candidateId, request);
  }

  @PostMapping(value = "/{batchId}/import", consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionImportService.ImportResult importCandidates(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long batchId,
      @Valid @RequestBody QuestionImportRequests.ImportCandidates request) {
    return imports.importCandidates(user.userId(), batchId, request);
  }

  @ExceptionHandler({
    QuestionImportService.BatchNotFoundException.class,
    QuestionImportService.CandidateNotFoundException.class,
    QuestionImportService.SourcePageNotFoundException.class,
    QuestionImportService.DiagramCropNotFoundException.class
  })
  ResponseEntity<ClassController.ApiError> notFound(RuntimeException exception) {
    return error(
        HttpStatus.NOT_FOUND,
        "QUESTION_IMPORT_NOT_FOUND",
        "The requested import item was not found.",
        Map.of());
  }

  @ExceptionHandler(QuestionImportService.InvalidImportException.class)
  ResponseEntity<ClassController.ApiError> invalid(
      QuestionImportService.InvalidImportException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_QUESTION_IMPORT",
        exception.getMessage(),
        Map.of("files", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ClassController.ApiError> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors())
      fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Question import data is invalid.", fields);
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ClassController.ApiError(code, message, fields));
  }
}
