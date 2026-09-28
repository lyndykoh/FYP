package com.fttranscendence.learning.report;

import com.fttranscendence.learning.pdf.PdfDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies report access scope before rendering the immutable stored snapshot. */
@Service
public class ReportPdfService {
  private final ReportService reports;
  private final PdfDocumentService pdfDocuments;

  public ReportPdfService(ReportService reports, PdfDocumentService pdfDocuments) {
    this.reports = reports;
    this.pdfDocuments = pdfDocuments;
  }

  @Transactional(readOnly = true)
  public PdfExport tutorExport(long tutorId, long reportId) {
    return render(reports.tutorReport(tutorId, reportId));
  }

  @Transactional(readOnly = true)
  public PdfExport studentExport(long loginUserId, long reportId) {
    return render(reports.studentReport(loginUserId, reportId));
  }

  private PdfExport render(ReportResponse report) {
    try {
      return new PdfExport(pdfDocuments.createReportPdf(report), filename(report));
    } catch (RuntimeException exception) {
      throw new ReportPdfUnavailableException(exception);
    }
  }

  private String filename(ReportResponse report) {
    String code =
        report.reportCode() == null ? "" : report.reportCode().replaceAll("[^A-Za-z0-9._-]", "_");
    if (code.isBlank()) code = "report-" + report.id();
    return "progress-report-" + code + ".pdf";
  }

  public record PdfExport(byte[] bytes, String filename) {}

  public static class ReportPdfUnavailableException extends RuntimeException {
    public ReportPdfUnavailableException(Throwable cause) {
      super("Unable to generate progress report PDF.", cause);
    }
  }
}
