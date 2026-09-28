package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.pdf.PdfDocumentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies worksheet access and lifecycle rules before rendering a PDF. */
@Service
public class WorksheetPdfService {
  private final WorksheetRepository worksheets;
  private final PdfDocumentService pdfDocuments;
  private final WorksheetService worksheetService;

  public WorksheetPdfService(
      WorksheetRepository worksheets,
      PdfDocumentService pdfDocuments,
      WorksheetService worksheetService) {
    this.worksheets = worksheets;
    this.pdfDocuments = pdfDocuments;
    this.worksheetService = worksheetService;
  }

  @Transactional(readOnly = true)
  public PdfExport export(long tutorId, long worksheetId) {
    Worksheet approvedWorksheet =
        worksheets
            .findByIdAndTutorId(worksheetId, tutorId)
            .orElseThrow(WorksheetService.WorksheetNotFoundException::new);
    if (approvedWorksheet.getStatus() != Worksheet.Status.APPROVED) {
      throw new WorksheetNotApprovedException();
    }
    return export(approvedWorksheet);
  }

  /**
   * Student access is proven by the linked profile and assignment, never by a supplied profile id.
   */
  @Transactional(readOnly = true)
  public PdfExport exportStudent(long loginUserId, long worksheetId) {
    Worksheet assignedWorksheet =
        worksheetService.studentAssignedWorksheet(loginUserId, worksheetId);
    return export(assignedWorksheet);
  }

  private PdfExport export(Worksheet worksheet) {
    byte[] pdfBytes = pdfDocuments.createWorksheetPdf(worksheet);
    return new PdfExport(pdfBytes, filename(worksheet));
  }

  private String filename(Worksheet worksheet) {
    return "worksheet-" + worksheet.getCode().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
  }

  public record PdfExport(byte[] bytes, String filename) {}

  public static class WorksheetNotApprovedException extends RuntimeException {}
}
