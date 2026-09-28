package com.fttranscendence.learning.report;

import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Report reads are split by principal type to make the final-snapshot boundary explicit: tutors can
 * view their drafts and finals; a linked student can view their finals only. There is not yet a
 * Parent identity/relationship model.
 */
@RestController
@RequestMapping("/api/learning")
public class ReportController {
  private final ReportService reports;
  private final ReportPdfService reportPdfs;

  public ReportController(ReportService reports, ReportPdfService reportPdfs) {
    this.reports = reports;
    this.reportPdfs = reportPdfs;
  }

  @GetMapping("/tutor/reports/{reportId}")
  public ReportResponse tutorReport(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long reportId) {
    return reports.tutorReport(user.userId(), reportId);
  }

  @GetMapping("/student/reports/{reportId}")
  public ReportResponse studentReport(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long reportId) {
    return reports.studentReport(user.userId(), reportId);
  }

  @GetMapping(value = "/tutor/reports/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> tutorReportPdf(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long reportId) {
    return pdf(reportPdfs.tutorExport(user.userId(), reportId));
  }

  @GetMapping(value = "/student/reports/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> studentReportPdf(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long reportId) {
    return pdf(reportPdfs.studentExport(user.userId(), reportId));
  }

  @ExceptionHandler(ReportService.ReportNotFoundException.class)
  ResponseEntity<ApiError> notFound() {
    // Intentionally identical for missing, foreign, and draft recipient reads.
    return error(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report was not found.");
  }

  @ExceptionHandler(ReportService.InvalidReportSnapshotException.class)
  ResponseEntity<ApiError> invalidSnapshot() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "REPORT_SNAPSHOT_UNAVAILABLE",
        "Report evidence is temporarily unavailable.");
  }

  @ExceptionHandler(ReportPdfService.ReportPdfUnavailableException.class)
  ResponseEntity<ApiError> pdfUnavailable() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "REPORT_PDF_UNAVAILABLE",
        "Report PDF generation is temporarily unavailable.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistence() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "REPORT_DATABASE_UNAVAILABLE",
        "Report data is temporarily unavailable.");
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> invalidRequest() {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Report request is invalid.");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  private ResponseEntity<byte[]> pdf(ReportPdfService.PdfExport export) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(export.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(export.bytes());
  }

  public record ApiError(String code, String message, Map<String, String> fields) {}
}
