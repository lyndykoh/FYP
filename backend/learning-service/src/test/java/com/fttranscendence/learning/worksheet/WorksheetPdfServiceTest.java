package com.fttranscendence.learning.worksheet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fttranscendence.learning.pdf.PdfDocumentService;
import com.fttranscendence.learning.question.Question;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class WorksheetPdfServiceTest {
  private static final long TUTOR_ID = 101L;
  private static final long WORKSHEET_ID = 901L;

  @Test
  void producesANonEmptyPdfWithWorksheetMetadataQuestionsAndSpecialCharacters() throws Exception {
    Worksheet worksheet =
        approvedWorksheet(
            "Water & energy practice",
            List.of(
                question(
                    "What happens when water is heated? (Use < and > only where relevant.)",
                    Question.QuestionType.OPEN_ENDED,
                    "3.00"),
                question(
                    "True/false: 2 + 2 = 4 & explain your choice.",
                    Question.QuestionType.TRUE_FALSE,
                    "1.00")));
    WorksheetRepository repository = mock(WorksheetRepository.class);
    when(repository.findByIdAndTutorId(WORKSHEET_ID, TUTOR_ID)).thenReturn(Optional.of(worksheet));
    WorksheetPdfService service =
        new WorksheetPdfService(repository, new PdfDocumentService(), mock(WorksheetService.class));

    WorksheetPdfService.PdfExport export = service.export(TUTOR_ID, WORKSHEET_ID);

    String qaOutput = System.getProperty("worksheet.pdf.qa.output");
    if (qaOutput != null && !qaOutput.isBlank()) Files.write(Path.of(qaOutput), export.bytes());

    assertFalse(export.bytes().length == 0);
    assertArrayEquals(
        "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        java.util.Arrays.copyOf(export.bytes(), 5));
    assertEquals("worksheet-WS-PDF-901.pdf", export.filename());
    try (PDDocument document = Loader.loadPDF(export.bytes())) {
      assertEquals("Water & energy practice", document.getDocumentInformation().getTitle());
      assertEquals("Worksheet WS-PDF-901", document.getDocumentInformation().getSubject());
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Worksheet code: WS-PDF-901"));
      assertTrue(text.contains("What happens when water is heated?"));
      assertTrue(text.contains("True/false: 2 + 2 = 4 & explain your choice."));
    }
  }

  @Test
  void startsAdditionalPagesForLongWorksheets() throws Exception {
    List<WorksheetQuestion> questions =
        java.util.stream.IntStream.rangeClosed(1, 32)
            .mapToObj(
                number ->
                    question(
                        "Question " + number + ": explain the water cycle in complete sentences.",
                        Question.QuestionType.OPEN_ENDED,
                        "2.00"))
            .toList();
    Worksheet worksheet = approvedWorksheet("Extended practice", questions);
    WorksheetRepository repository = mock(WorksheetRepository.class);
    when(repository.findByIdAndTutorId(WORKSHEET_ID, TUTOR_ID)).thenReturn(Optional.of(worksheet));

    byte[] bytes =
        new WorksheetPdfService(repository, new PdfDocumentService(), mock(WorksheetService.class))
            .export(TUTOR_ID, WORKSHEET_ID)
            .bytes();

    try (PDDocument document = Loader.loadPDF(bytes)) {
      assertTrue(document.getNumberOfPages() > 1);
    }
  }

  @Test
  void rejectsDraftWorksheetsBeforeAttemptingPdfGeneration() {
    Worksheet worksheet = mock(Worksheet.class);
    when(worksheet.getStatus()).thenReturn(Worksheet.Status.DRAFT);
    WorksheetRepository repository = mock(WorksheetRepository.class);
    PdfDocumentService documents = mock(PdfDocumentService.class);
    when(repository.findByIdAndTutorId(WORKSHEET_ID, TUTOR_ID)).thenReturn(Optional.of(worksheet));

    WorksheetPdfService service =
        new WorksheetPdfService(repository, documents, mock(WorksheetService.class));

    assertThrows(
        WorksheetPdfService.WorksheetNotApprovedException.class,
        () -> service.export(TUTOR_ID, WORKSHEET_ID));
    verifyNoInteractions(documents);
  }

  @Test
  void treatsMissingOrForeignWorksheetsAsNotFound() {
    WorksheetRepository repository = mock(WorksheetRepository.class);
    when(repository.findByIdAndTutorId(WORKSHEET_ID, TUTOR_ID)).thenReturn(Optional.empty());

    assertThrows(
        WorksheetService.WorksheetNotFoundException.class,
        () ->
            new WorksheetPdfService(
                    repository, mock(PdfDocumentService.class), mock(WorksheetService.class))
                .export(TUTOR_ID, WORKSHEET_ID));
  }

  private Worksheet approvedWorksheet(String title, List<WorksheetQuestion> questions) {
    Worksheet worksheet = mock(Worksheet.class);
    when(worksheet.getStatus()).thenReturn(Worksheet.Status.APPROVED);
    when(worksheet.getCode()).thenReturn("WS-PDF-901");
    when(worksheet.getTitle()).thenReturn(title);
    when(worksheet.getInstructions()).thenReturn("Answer every question clearly.");
    when(worksheet.getQuestions()).thenReturn(questions);
    return worksheet;
  }

  private WorksheetQuestion question(String prompt, Question.QuestionType type, String marks) {
    Question question = mock(Question.class);
    when(question.getPrompt()).thenReturn(prompt);
    when(question.getQuestionType()).thenReturn(type);
    when(question.getTotalMarks()).thenReturn(new BigDecimal(marks));
    WorksheetQuestion worksheetQuestion = mock(WorksheetQuestion.class);
    when(worksheetQuestion.getQuestion()).thenReturn(question);
    return worksheetQuestion;
  }
}
