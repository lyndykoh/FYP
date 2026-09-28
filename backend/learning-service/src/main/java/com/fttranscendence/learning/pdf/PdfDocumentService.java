package com.fttranscendence.learning.pdf;

import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.report.ReportResponse;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetQuestion;
import com.fttranscendence.learning.worksheet.WorksheetQuestionImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Renders the printable student-facing form of an approved worksheet. */
@Service
public class PdfDocumentService {
  private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
  private static final float MARGIN = 54;
  private static final float BODY_SIZE = 11;
  private static final float BODY_LEADING = 15;
  private static final float HEADING_SIZE = 18;
  private static final float SUBHEADING_SIZE = 12;

  public byte[] createWorksheetPdf(Worksheet worksheet) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      applyMetadata(document, worksheet);
      PageWriter writer = new PageWriter(document, loadFonts(document), "Worksheet");
      writeWorksheetHeader(writer, worksheet);
      writer.spacer(10);

      int number = 1;
      for (WorksheetQuestion worksheetQuestion : worksheet.getQuestions()) {
        Question question = worksheetQuestion.getQuestion();
        String prompt =
            worksheetQuestion.getPromptSnapshot() == null
                ? question.getPrompt()
                : worksheetQuestion.getPromptSnapshot();
        Question.QuestionType type =
            worksheetQuestion.getQuestionTypeSnapshot() == null
                ? question.getQuestionType()
                : worksheetQuestion.getQuestionTypeSnapshot();
        BigDecimal marks =
            worksheetQuestion.getTotalMarksSnapshot() == null
                ? question.getTotalMarks()
                : worksheetQuestion.getTotalMarksSnapshot();
        writer.question(number++, prompt, type, marks, worksheetQuestion.getImages());
      }
      writer.finish();
      document.save(bytes);
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new PdfGenerationException("Unable to generate the worksheet PDF.", exception);
    }
  }

  private void writeWorksheetHeader(PageWriter writer, Worksheet worksheet) throws IOException {
    writer.heading(worksheet.getTitle());
    writer.body("Worksheet code: " + worksheet.getCode());

    boolean hasInstructions =
        worksheet.getInstructions() != null && !worksheet.getInstructions().isBlank();
    if (!hasInstructions) {
      return;
    }

    writer.spacer(6);
    writer.boldBody("Instructions");
    writer.body(worksheet.getInstructions());
  }

  /**
   * Renders a persisted progress-report snapshot. This deliberately accepts the read DTO rather
   * than querying current learning data: finalized reports are historical records and their PDF
   * must agree with the web snapshot.
   */
  public byte[] createReportPdf(ReportResponse report) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      applyMetadata(document, report);
      PageWriter writer = new PageWriter(document, loadFonts(document), "Progress report");
      writer.heading("Progress report");
      writer.body("Student: " + report.studentName());
      writer.body("Report code: " + report.reportCode());
      writer.body("Period: " + report.periodStart() + " to " + report.periodEnd());
      writer.body("Status: " + report.status());
      writer.spacer(10);
      writer.boldBody("Evidence snapshot");
      writeSnapshot(writer, report.snapshot(), 0);
      writer.finish();
      document.save(bytes);
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new PdfGenerationException("Unable to generate the progress report PDF.", exception);
    }
  }

  private void applyMetadata(PDDocument document, Worksheet worksheet) {
    PDDocumentInformation metadata = document.getDocumentInformation();
    metadata.setTitle(worksheet.getTitle());
    metadata.setSubject("Worksheet " + worksheet.getCode());
    metadata.setAuthor("ft_transcendence");
    metadata.setCreator("ft_transcendence learning service");
    metadata.setKeywords("worksheet," + worksheet.getCode());
  }

  private void applyMetadata(PDDocument document, ReportResponse report) {
    PDDocumentInformation metadata = document.getDocumentInformation();
    metadata.setTitle("Progress report - " + report.studentName());
    metadata.setSubject(
        "Report "
            + report.reportCode()
            + " for "
            + report.periodStart()
            + " to "
            + report.periodEnd());
    metadata.setAuthor("ft_transcendence");
    metadata.setCreator("ft_transcendence learning service");
    metadata.setKeywords("progress report," + report.reportCode());
  }

  private void writeSnapshot(PageWriter writer, JsonNode value, int depth) throws IOException {
    if (value == null || value.isNull() || value.isMissingNode()) {
      writer.body("No evidence was captured for this report period.");
      return;
    }
    if (value.isObject()) {
      if (value.isEmpty()) {
        writer.body("No evidence was captured for this report period.");
        return;
      }
      for (java.util.Map.Entry<String, JsonNode> field : value.properties()) {
        String label = readableLabel(field.getKey());
        JsonNode fieldValue = field.getValue();
        if (fieldValue.isValueNode() || fieldValue.isNull()) {
          writer.boldBody(label + ": " + printableSnapshotValue(fieldValue));
        } else {
          writer.boldBody(label);
          writeSnapshot(writer, fieldValue, depth + 1);
        }
      }
      return;
    }
    if (value.isArray()) {
      if (value.isEmpty()) {
        writer.body("None recorded.");
        return;
      }
      for (JsonNode item : value) {
        if (item.isValueNode() || item.isNull()) {
          writer.body("- " + printableSnapshotValue(item));
        } else {
          writeSnapshot(writer, item, depth + 1);
        }
      }
      return;
    }
    writer.body(printableSnapshotValue(value));
  }

  private String printableSnapshotValue(JsonNode value) {
    if (value == null || value.isNull()) return "Not recorded";
    if (value.isTextual()) return value.textValue();
    return value.asText();
  }

  private String readableLabel(String value) {
    if (value == null || value.isBlank()) return "Evidence";
    String spaced = value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ');
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private PdfFonts loadFonts(PDDocument document) throws IOException {
    return new PdfFonts(
        loadFont(document, "fonts/NotoSans-Regular.ttf"),
        loadFont(document, "fonts/NotoSans-Bold.ttf"));
  }

  private PDFont loadFont(PDDocument document, String resourcePath) throws IOException {
    try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (resource == null)
        throw new IOException("Embedded PDF font resource is missing: " + resourcePath);
      return PDType0Font.load(document, resource, true);
    }
  }

  private final class PageWriter {
    private final PDDocument document;
    private final PdfFonts fonts;
    private final String documentLabel;
    private PDPageContentStream content;
    private float y;

    private PageWriter(PDDocument document, PdfFonts fonts, String documentLabel)
        throws IOException {
      this.document = document;
      this.fonts = fonts;
      this.documentLabel = documentLabel;
      startPage();
    }

    private void startPage() throws IOException {
      if (content != null) content.close();
      document.addPage(new PDPage(PAGE_SIZE));
      content =
          new PDPageContentStream(document, document.getPage(document.getNumberOfPages() - 1));
      y = PAGE_SIZE.getHeight() - MARGIN;
      content.beginText();
      content.setFont(fonts.regular(), 8);
      content.newLineAtOffset(MARGIN, 28);
      content.showText(documentLabel);
      content.endText();
    }

    private void heading(String value) throws IOException {
      writeLines(
          wrap(value, fonts.bold(), HEADING_SIZE), fonts.bold(), HEADING_SIZE, HEADING_SIZE + 7);
    }

    private void boldBody(String value) throws IOException {
      writeLines(
          wrap(value, fonts.bold(), SUBHEADING_SIZE), fonts.bold(), SUBHEADING_SIZE, BODY_LEADING);
    }

    private void body(String value) throws IOException {
      writeLines(wrap(value, fonts.regular(), BODY_SIZE), fonts.regular(), BODY_SIZE, BODY_LEADING);
    }

    private void question(
        int number,
        String prompt,
        Question.QuestionType type,
        BigDecimal marks,
        List<WorksheetQuestionImage> images)
        throws IOException {
      writeLines(
          wrap(number + ". " + prompt, fonts.bold(), BODY_SIZE),
          fonts.bold(),
          BODY_SIZE,
          BODY_LEADING);
      String details =
          "Type: "
              + readableType(type)
              + (marks == null ? "" : "  *  Marks: " + marks.stripTrailingZeros().toPlainString());
      writeLines(wrap(details, fonts.regular(), 9), fonts.regular(), 9, 12);
      for (WorksheetQuestionImage image : images) {
        drawImage(image);
        spacer(8);
      }
      int answerLines =
          switch (type) {
            case TRUE_FALSE, MULTIPLE_CHOICE -> 2;
            case FILL_IN_THE_BLANK -> 3;
            default -> 5;
          };
      for (int line = 0; line < answerLines; line++) {
        ensureSpace(13);
        content.moveTo(MARGIN, y - 7);
        content.lineTo(PAGE_SIZE.getWidth() - MARGIN, y - 7);
        content.stroke();
        y -= 13;
      }
      spacer(12);
    }

    private void drawImage(WorksheetQuestionImage source) throws IOException {
      java.awt.image.BufferedImage decoded =
          ImageIO.read(new ByteArrayInputStream(source.getImageBytes()));
      if (decoded == null) throw new IOException("Worksheet image snapshot cannot be decoded");
      float maxWidth = PAGE_SIZE.getWidth() - (MARGIN * 2);
      float maxHeight = 280;
      float scale = Math.min(maxWidth / decoded.getWidth(), maxHeight / decoded.getHeight());
      float width = Math.max(1, decoded.getWidth() * scale);
      float height = Math.max(1, decoded.getHeight() * scale);
      ensureSpace(height);
      PDImageXObject pdfImage = LosslessFactory.createFromImage(document, decoded);
      content.drawImage(pdfImage, MARGIN, y - height, width, height);
      y -= height;
    }

    private void spacer(float height) throws IOException {
      ensureSpace(height);
      y -= height;
    }

    private void writeLines(List<String> lines, PDFont font, float fontSize, float leading)
        throws IOException {
      for (String line : lines) {
        ensureSpace(leading);
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(MARGIN, y);
        content.showText(line);
        content.endText();
        y -= leading;
      }
    }

    private void ensureSpace(float requiredHeight) throws IOException {
      if (y - requiredHeight < MARGIN) startPage();
    }

    private void finish() throws IOException {
      if (content != null) content.close();
      content = null;
    }
  }

  private List<String> wrap(String input, PDFont font, float fontSize) throws IOException {
    String text = printableText(input == null ? "" : input);
    if (text.isBlank()) return List.of("");
    float maximumWidth = PAGE_SIZE.getWidth() - (2 * MARGIN);
    List<String> lines = new ArrayList<>();
    for (String paragraph : text.split("\\R", -1)) {
      if (paragraph.isBlank()) {
        lines.add("");
        continue;
      }
      StringBuilder line = new StringBuilder();
      for (String word : paragraph.split("\\s+")) {
        String candidate = line.isEmpty() ? word : line + " " + word;
        if (font.getStringWidth(candidate) / 1000 * fontSize <= maximumWidth) {
          line.setLength(0);
          line.append(candidate);
        } else if (line.isEmpty()) {
          lines.add(word);
        } else {
          lines.add(line.toString());
          line.setLength(0);
          line.append(word);
        }
      }
      if (!line.isEmpty()) lines.add(line.toString());
    }
    return lines;
  }

  private String printableText(String value) {
    // NFC preserves accents, typographic punctuation, and supported non-Latin
    // scripts while keeping composed glyphs suitable for the embedded font.
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
  }

  private record PdfFonts(PDFont regular, PDFont bold) {}

  private String readableType(Question.QuestionType type) {
    return type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
  }

  public static class PdfGenerationException extends RuntimeException {
    PdfGenerationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
