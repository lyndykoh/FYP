package com.fttranscendence.learning.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fttranscendence.learning.pdf.PdfDocumentService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReportPdfServiceTest {
  private static final long TUTOR_ID = 101L;
  private static final long STUDENT_LOGIN_ID = 9001L;
  private static final long REPORT_ID = 701L;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void producesANonEmptyPdfWithStoredMetadataMasteryAndMistakes() throws Exception {
    ReportResponse report =
        report(
            "Ava Learner",
            "AUG-2026",
            """
            {"summary":"Secure progress update","mastery":[{"topic":"Fractions","score":82}],"mistakes":[{"topic":"Decimals","evidence":"Confused place value"}]}
            """);
    ReportService reports = mock(ReportService.class);
    when(reports.tutorReport(TUTOR_ID, REPORT_ID)).thenReturn(report);
    ReportPdfService service = new ReportPdfService(reports, new PdfDocumentService());

    ReportPdfService.PdfExport export = service.tutorExport(TUTOR_ID, REPORT_ID);

    writeQaOutput(export.bytes());

    assertFalse(export.bytes().length == 0);
    assertArrayEquals(
        "%PDF-".getBytes(StandardCharsets.US_ASCII), Arrays.copyOf(export.bytes(), 5));
    assertEquals("progress-report-AUG-2026.pdf", export.filename());
    try (PDDocument document = Loader.loadPDF(export.bytes())) {
      assertEquals("Progress report - Ava Learner", document.getDocumentInformation().getTitle());
      assertEquals(
          "Report AUG-2026 for 2026-08-01 to 2026-08-31",
          document.getDocumentInformation().getSubject());
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Student: Ava Learner"));
      assertTrue(text.contains("Period: 2026-08-01 to 2026-08-31"));
      assertTrue(text.contains("Fractions"));
      assertTrue(text.contains("82"));
      assertTrue(text.contains("Decimals"));
      assertTrue(text.contains("Confused place value"));
    }
  }

  @Test
  void startsAdditionalPagesForLargeEvidenceSnapshots() throws Exception {
    StringBuilder snapshot = new StringBuilder("{\"evidence\":[");
    for (int index = 0; index < 180; index++) {
      if (index > 0) snapshot.append(',');
      snapshot
          .append("{\"finding\":\"Evidence item ")
          .append(index + 1)
          .append(
              " describes a repeated application mistake in a detailed, tutor-approved observation.\"}");
    }
    snapshot.append("]}");
    ReportService reports = mock(ReportService.class);
    when(reports.tutorReport(TUTOR_ID, REPORT_ID))
        .thenReturn(report("Ava Learner", "LONG-EVIDENCE", snapshot.toString()));

    byte[] bytes =
        new ReportPdfService(reports, new PdfDocumentService())
            .tutorExport(TUTOR_ID, REPORT_ID)
            .bytes();

    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertTrue(document.getNumberOfPages() > 1);
    }
  }

  @Test
  void preservesSpecialCharactersAndSupportedNonLatinEvidence() throws Exception {
    ReportService reports = mock(ReportService.class);
    when(reports.studentReport(STUDENT_LOGIN_ID, REPORT_ID))
        .thenReturn(
            report(
                "Léa O’Connor – North",
                "SPECIAL",
                """
            {"summary":"Café – 75% mastery","mistakes":["‘minus’ notation was omitted","Мария applies дроби correctly","Ελένη explains κλάσματα"]}
            """));

    byte[] bytes =
        new ReportPdfService(reports, new PdfDocumentService())
            .studentExport(STUDENT_LOGIN_ID, REPORT_ID)
            .bytes();
    writeQaOutput(bytes);

    try (PDDocument document = Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Léa O’Connor – North"));
      assertTrue(text.contains("Café – 75% mastery"));
      assertTrue(text.contains("‘minus’ notation was omitted"));
      assertTrue(text.contains("Мария applies дроби correctly"));
      assertTrue(text.contains("Ελένη explains κλάσματα"));
    }
  }

  @Test
  void rendersDeeplyNestedSnapshotEvidenceWithoutRecomputingIt() throws Exception {
    String snapshot =
        "{\"level0\":{\"level1\":{\"level2\":{\"level3\":{\"level4\":{\"level5\":{\"evidence\":\"Deep snapshot evidence remains persisted\"}}}}}}}";
    ReportService reports = mock(ReportService.class);
    when(reports.tutorReport(TUTOR_ID, REPORT_ID))
        .thenReturn(report("Ava Learner", "DEEP", snapshot));

    byte[] bytes =
        new ReportPdfService(reports, new PdfDocumentService())
            .tutorExport(TUTOR_ID, REPORT_ID)
            .bytes();

    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertTrue(
          new PDFTextStripper()
              .getText(document)
              .contains("Deep snapshot evidence remains persisted"));
    }
  }

  @Test
  void delegatesRecipientScopeToReportServiceAndSurfacesRendererFailures() {
    ReportResponse report = report("Ava Learner", "AUG-2026", "{}");
    ReportService reports = mock(ReportService.class);
    PdfDocumentService documents = mock(PdfDocumentService.class);
    when(reports.studentReport(STUDENT_LOGIN_ID, REPORT_ID)).thenReturn(report);
    when(documents.createReportPdf(same(report)))
        .thenThrow(new IllegalStateException("PDF renderer unavailable"));
    ReportPdfService service = new ReportPdfService(reports, documents);

    assertThrows(
        ReportPdfService.ReportPdfUnavailableException.class,
        () -> service.studentExport(STUDENT_LOGIN_ID, REPORT_ID));

    verify(reports).studentReport(STUDENT_LOGIN_ID, REPORT_ID);
    verify(documents).createReportPdf(report);
    verifyNoMoreInteractions(reports, documents);
  }

  private ReportResponse report(String studentName, String code, String snapshot) {
    try {
      return new ReportResponse(
          REPORT_ID,
          401L,
          studentName,
          code,
          ProgressReport.ReportStatus.FINAL,
          LocalDate.of(2026, 8, 1),
          LocalDate.of(2026, 8, 31),
          objectMapper.readTree(snapshot),
          LocalDateTime.of(2026, 8, 31, 9, 0),
          LocalDateTime.of(2026, 8, 31, 12, 0));
    } catch (Exception exception) {
      throw new AssertionError("Test snapshot must be valid JSON", exception);
    }
  }

  private void writeQaOutput(byte[] bytes) throws java.io.IOException {
    String qaOutput = System.getProperty("report.pdf.qa.output");
    if (qaOutput != null && !qaOutput.isBlank()) Files.write(Path.of(qaOutput), bytes);
  }
}
