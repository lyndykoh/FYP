package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.classroom.ClassController;
import com.fttranscendence.learning.pdf.PdfDocumentService;
import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping(value = "/api/learning", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorksheetController {
  private final WorksheetService worksheets;
  private final DiagnosticWorksheetService diagnostics;
  private final WorksheetPdfService worksheetPdfs;
  private final WorksheetImageService worksheetImages;

  public WorksheetController(
      WorksheetService worksheets,
      DiagnosticWorksheetService diagnostics,
      WorksheetPdfService worksheetPdfs,
      WorksheetImageService worksheetImages) {
    this.worksheets = worksheets;
    this.diagnostics = diagnostics;
    this.worksheetPdfs = worksheetPdfs;
    this.worksheetImages = worksheetImages;
  }

  @PostMapping(
      value = "/tutor/classes/{classId}/worksheet-generation-requests",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<WorksheetRequests.GenerationRequestResponse> generate(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody WorksheetRequests.GenerateWorksheetRequest request) {
    WorksheetRequests.GenerationRequestResponse response =
        worksheets.generate(user.userId(), classId, idempotencyKey, request);
    return generationResponse(response);
  }

  /**
   * Evidence-backed generation still yields a draft; only the standard approve endpoint may assign
   * it.
   */
  @PostMapping(
      value = "/tutor/classes/{classId}/diagnostic-worksheet-generation-requests",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<WorksheetRequests.GenerationRequestResponse> generateDiagnostic(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody WorksheetRequests.GenerateDiagnosticWorksheetRequest request) {
    WorksheetRequests.GenerationRequestResponse response =
        diagnostics.generate(user.userId(), classId, idempotencyKey, request);
    return generationResponse(response);
  }

  @GetMapping("/tutor/classes/{classId}/worksheet-generation-requests/{requestId}")
  public WorksheetRequests.GenerationRequestResponse generationRequest(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @PathVariable @Positive long requestId) {
    return worksheets.getGenerationRequest(user.userId(), classId, requestId);
  }

  @GetMapping("/tutor/worksheets/{worksheetId}")
  public WorksheetRequests.WorksheetResponse worksheet(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long worksheetId) {
    return worksheets.getWorksheet(user.userId(), worksheetId);
  }

  @GetMapping("/tutor/worksheets")
  public List<WorksheetRequests.WorksheetResponse> worksheets(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @Positive Long classId) {
    return worksheets.listWorksheets(user.userId(), classId);
  }

  @GetMapping("/tutor/classes/{classId}/students/{studentId}/submission-worksheets")
  public List<WorksheetRequests.WorksheetResponse> submissionWorksheets(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @PathVariable @Positive long studentId) {
    return worksheets.listSubmissionWorksheets(user.userId(), classId, studentId);
  }

  @GetMapping(
      value = "/tutor/worksheets/{worksheetId}/pdf",
      produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> worksheetPdf(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long worksheetId) {
    WorksheetPdfService.PdfExport export = worksheetPdfs.export(user.userId(), worksheetId);
    return pdfResponse(export);
  }

  @GetMapping("/tutor/worksheets/{worksheetId}/images/{imageId}")
  public ResponseEntity<byte[]> tutorWorksheetImage(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long worksheetId,
      @PathVariable @Positive long imageId) {
    WorksheetImageService.ImageContent image =
        worksheetImages.tutorImage(user.userId(), worksheetId, imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @PatchMapping(
      value = "/tutor/worksheets/{worksheetId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public WorksheetRequests.WorksheetResponse updateWorksheet(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long worksheetId,
      @Valid @RequestBody WorksheetRequests.UpdateWorksheetRequest request) {
    return worksheets.updateWorksheet(user.userId(), worksheetId, request);
  }

  @PostMapping(
      value = "/tutor/worksheets/{worksheetId}/approve",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public WorksheetRequests.WorksheetResponse approve(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long worksheetId,
      @Valid @RequestBody WorksheetRequests.ApproveWorksheetRequest request) {
    return worksheets.approveAndAssign(user.userId(), worksheetId, request);
  }

  @GetMapping("/tutor/classes/{classId}/worksheet-recommendations")
  public DiagnosticWorksheetService.Recommendations recommendations(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return diagnostics.recommendations(user.userId(), classId);
  }

  /** No student selector: the server derives the linked learner from the authenticated JWT. */
  @GetMapping("/student/worksheets")
  public List<WorksheetRequests.StudentWorksheetLibraryItem> studentWorksheets(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) Long topicId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate assignedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate assignedTo) {
    WorksheetRequests.StudentWorksheetStatus normalizedStatus =
        normalizeStudentWorksheetStatus(status);
    WorksheetService.StudentWorksheetFilter filter =
        new WorksheetService.StudentWorksheetFilter(
            subjectId, topicId, normalizedStatus, assignedFrom, assignedTo);
    return worksheets.listStudentWorksheets(user.userId(), filter);
  }

  @GetMapping("/student/worksheets/{worksheetId}")
  public WorksheetRequests.StudentWorksheetDetail studentWorksheet(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long worksheetId) {
    return worksheets.getStudentWorksheet(user.userId(), worksheetId);
  }

  @GetMapping("/student/worksheets/{worksheetId}/images/{imageId}")
  public ResponseEntity<byte[]> studentWorksheetImage(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long worksheetId,
      @PathVariable @Positive long imageId) {
    WorksheetImageService.ImageContent image =
        worksheetImages.studentImage(user.userId(), worksheetId, imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .body(image.bytes());
  }

  @GetMapping(
      value = "/student/worksheets/{worksheetId}/pdf",
      produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> studentWorksheetPdf(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long worksheetId) {
    WorksheetPdfService.PdfExport export = worksheetPdfs.exportStudent(user.userId(), worksheetId);
    return pdfResponse(export);
  }

  private ResponseEntity<WorksheetRequests.GenerationRequestResponse> generationResponse(
      WorksheetRequests.GenerationRequestResponse response) {
    HttpStatus status =
        response.status() == WorksheetGenerationRequest.Status.SUCCEEDED
            ? HttpStatus.CREATED
            : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(response);
  }

  private ResponseEntity<byte[]> pdfResponse(WorksheetPdfService.PdfExport export) {
    String contentDisposition =
        ContentDisposition.attachment()
            .filename(export.filename(), StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
        .body(export.bytes());
  }

  private WorksheetRequests.StudentWorksheetStatus normalizeStudentWorksheetStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }

    try {
      return WorksheetRequests.StudentWorksheetStatus.valueOf(
          status.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new WorksheetService.InvalidStudentWorksheetFilterException(
          "status must be ASSIGNED, SUBMITTED or MARKED.");
    }
  }

  @ExceptionHandler({
    WorksheetService.ClassNotFoundException.class,
    WorksheetService.WorksheetNotFoundException.class,
    WorksheetService.GenerationRequestNotFoundException.class,
    WorksheetService.StudentWorksheetNotFoundException.class
  })
  ResponseEntity<ClassController.ApiError> notFound(RuntimeException exception) {
    return error(
        HttpStatus.NOT_FOUND, "WORKSHEET_RESOURCE_NOT_FOUND", "Worksheet resource was not found.");
  }

  @ExceptionHandler(WorksheetService.IdempotencyConflictException.class)
  ResponseEntity<ClassController.ApiError> conflict(
      WorksheetService.IdempotencyConflictException exception) {
    return error(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_KEY_REUSED",
        "This idempotency key was already used for a different request.");
  }

  @ExceptionHandler({
    WorksheetService.WorksheetNotDraftException.class,
    WorksheetService.WorksheetNotGeneratedException.class
  })
  ResponseEntity<ClassController.ApiError> invalidState(RuntimeException exception) {
    return error(
        HttpStatus.CONFLICT,
        "WORKSHEET_STATE_CONFLICT",
        "This worksheet cannot be changed in its current state.");
  }

  @ExceptionHandler(WorksheetPdfService.WorksheetNotApprovedException.class)
  ResponseEntity<ClassController.ApiError> pdfUnavailable(
      WorksheetPdfService.WorksheetNotApprovedException exception) {
    return error(
        HttpStatus.CONFLICT,
        "WORKSHEET_NOT_APPROVED",
        "Only approved worksheets can be exported as PDFs.");
  }

  @ExceptionHandler(PdfDocumentService.PdfGenerationException.class)
  ResponseEntity<ClassController.ApiError> pdfFailure(
      PdfDocumentService.PdfGenerationException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "WORKSHEET_PDF_UNAVAILABLE",
        "Worksheet PDF generation is temporarily unavailable.");
  }

  @ExceptionHandler(WorksheetService.InvalidWorksheetRequestException.class)
  ResponseEntity<ClassController.ApiError> invalid(
      WorksheetService.InvalidWorksheetRequestException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_WORKSHEET_REQUEST", exception.getMessage());
  }

  @ExceptionHandler(WorksheetService.InvalidStudentWorksheetFilterException.class)
  ResponseEntity<ClassController.ApiError> invalidLibraryFilter(
      WorksheetService.InvalidStudentWorksheetFilterException exception) {
    return error(
        HttpStatus.BAD_REQUEST, "INVALID_STUDENT_WORKSHEET_FILTER", exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ClassController.ApiError> malformedLibraryFilter(
      MethodArgumentTypeMismatchException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_STUDENT_WORKSHEET_FILTER",
        "Worksheet library filters are invalid.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ClassController.ApiError> databaseUnavailable(DataAccessException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "WORKSHEET_DATA_UNAVAILABLE",
        "Worksheet data is temporarily unavailable.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ClassController.ApiError> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError field : exception.getBindingResult().getFieldErrors())
      fields.putIfAbsent(field.getField(), field.getDefaultMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ClassController.ApiError(
                "VALIDATION_FAILED", "Worksheet request is invalid.", fields));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ClassController.ApiError> methodValidation(
      HandlerMethodValidationException exception) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Worksheet path values are invalid.");
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ClassController.ApiError(code, message, Map.of()));
  }
}
