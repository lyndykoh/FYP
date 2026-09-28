package com.fttranscendence.grading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Service
public class AiOcrService {

  private static final String JPEG_MEDIA_TYPE = "image/jpeg";
  private static final String PNG_MEDIA_TYPE = "image/png";
  private static final String PDF_MEDIA_TYPE = "application/pdf";
  private static final int MAX_PDF_PAGES = 100;
  private static final int PDF_RENDER_DPI = 144;
  private static final long MAX_RENDERED_PDF_PIXELS = 20_000_000L;
  // The diagnostic copy is deliberately short-lived and bounded. It is never logged or stored.
  private static final int MAX_DIAGNOSTIC_RAW_RESPONSE_BYTES = 32_000;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String OCR_PROMPT =
      "You are a diagram-aware, answer-only OCR engine for submitted worksheets. "
          + "First distinguish student answer regions from printed/template regions and figures. "
          + "A figure includes a diagram, drawing, graph, chart, map, table, axis, flowchart, "
          + "caption, callout, legend, shape, arrow, value, or label placed in or on that figure. "
          + "Figure content and printed content are never answers. Transcribe only literal, "
          + "student-authored typed or handwritten answer text, calculations, or working that is "
          + "clearly an answer to the figure question. An answer can be beside, below, or in a "
          + "student-filled answer blank associated with a figure, but do not transcribe diagram "
          + "content, printed labels, or describe what the figure shows. Do not solve, correct, "
          + "infer, or paraphrase anything. If a mark cannot reliably be distinguished as student "
          + "answer text rather than figure content, use 'uncertain'. For example, when a printed "
          + "force diagram has labels '10 N' and 'left', while the student writes 'The forces are "
          + "balanced.' in the response area, return only 'The forces are balanced.'. "
          + "Return exactly one JSON object with exactly these fields: status, regions, confidence. "
          + "status must be 'answers', 'no_answers', or 'uncertain'. regions must be an array of "
          + "objects with exactly type and text. type must be 'student_answer', 'diagram', or "
          + "'printed_content'. Use 'student_answer' only for literal student answer-region text; "
          + "use 'diagram' for figure content and 'printed_content' for all template text. For "
          + "'answers', include at least one nonblank student_answer region in reading order. For "
          + "'no_answers', include no student_answer regions. confidence must be a number from 0 to 1 "
          + "for the retained student answers and their attribution. Do not include markdown, "
          + "explanation, preamble, or commentary.";

  @Value("${ai.engine.url}")
  private String apiUrl;

  @Value("${ai.vision.model}")
  private String visionModel;

  @Value("${ai.engine.api-key}")
  private String apiKey;

  /** Diagnostic provider calls are disabled unless explicitly enabled for a controlled run. */
  @Value("${ai.ocr.diagnostic.enabled:false}")
  private boolean diagnosticEnabled;

  private final RestTemplate restTemplate;

  public AiOcrService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @PostConstruct
  void validateProviderCredential() {
    validateApiKey(apiKey);
  }

  static void validateApiKey(String candidate) {
    String normalizedCredential = candidate == null ? "" : candidate.trim();
    boolean isBlankCredential = normalizedCredential.isBlank();
    boolean containsTemplateValue =
        normalizedCredential.toLowerCase(java.util.Locale.ROOT).contains("change-me");
    boolean usesReplacementPlaceholder = normalizedCredential.startsWith("REPLACE_WITH_");

    if (isBlankCredential || containsTemplateValue || usesReplacementPlaceholder) {
      throw new IllegalArgumentException(
          "AI_ENGINE_API_KEY must be a real provider credential, not a placeholder");
    }
  }

  public String extractTextFromImage(String base64Image) {
    return toOcrResult(
            callProvider(base64Image, JPEG_MEDIA_TYPE, UUID.randomUUID().toString()).modelOutput())
        .text();
  }

  public OcrResult extract(byte[] bytes, String mediaType) {
    return extractWithOutcome(bytes, mediaType, UUID.randomUUID().toString()).result();
  }

  /**
   * Performs one OCR extraction with a caller-supplied UUID correlation ID. Invalid IDs are
   * replaced locally. The ID is only forwarded to the provider; it is never included in extracted
   * text or persisted OCR records.
   */
  public OcrExtractionResult extractWithOutcome(
      byte[] bytes, String mediaType, String correlationId) {
    String safeCorrelationId = normalizeCorrelationId(correlationId);

    if (PDF_MEDIA_TYPE.equals(mediaType)) {
      return extractPdfPages(bytes, safeCorrelationId);
    }

    if (!isSupportedImage(mediaType)) {
      return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
    }

    String base64Image = Base64.getEncoder().encodeToString(bytes);
    ProviderOcrCall providerCall = callProvider(base64Image, mediaType, safeCorrelationId);
    return toExtractionResult(providerCall);
  }

  private String normalizeCorrelationId(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return UUID.randomUUID().toString();
    }

    try {
      return UUID.fromString(correlationId).toString();
    } catch (IllegalArgumentException exception) {
      return UUID.randomUUID().toString();
    }
  }

  /**
   * Calls the vision provider only when the diagnostic switch is explicitly enabled.
   *
   * <p>The returned value deliberately exposes only correlation and recognition metadata. The raw
   * provider HTTP body remains in bounded process memory and is package-visible solely to the
   * opt-in diagnostic harness. This prevents a controller, logger, or database mapper from
   * accidentally persisting an uploaded answer.
   */
  public OcrDiagnosticResult diagnose(byte[] bytes, String mediaType) {
    String correlationId = UUID.randomUUID().toString();

    if (!diagnosticEnabled) {
      return OcrDiagnosticResult.disabled(correlationId);
    }

    if (!isSupportedImage(mediaType) || bytes == null || bytes.length == 0) {
      return OcrDiagnosticResult.unsupportedInput(correlationId);
    }

    String base64Image = Base64.getEncoder().encodeToString(bytes);
    DiagnosticProviderCall providerCall =
        callProviderForDiagnostic(base64Image, mediaType, correlationId);
    ModelOcrOutput modelOutput = providerCall.modelOutput();

    return new OcrDiagnosticResult(
        correlationId,
        diagnosticStatus(providerCall, modelOutput),
        new OcrDiagnosticMetadata(
            providerCall.rawResponseCaptured(),
            providerCall.rawResponseTruncated(),
            providerCall.rawResponseLength(),
            providerCall.httpStatus(),
            modelOutput.status() == OcrStatus.ANSWERS,
            modelOutput.confidence()),
        providerCall.rawResponse(),
        modelOutput.text());
  }

  private OcrExtractionResult extractPdfPages(byte[] pdfBytes, String correlationId) {
    if (pdfBytes == null || pdfBytes.length == 0) {
      return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
    }

    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      if (document.isEncrypted() || !hasSupportedPageCount(document)) {
        return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
      }

      PDFRenderer renderer = new PDFRenderer(document);
      List<String> answerPages = new ArrayList<>();
      double lowestConfidence = 1;

      for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
        if (!canRenderWithinLimit(document.getPage(pageIndex))) {
          return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
        }

        byte[] pageImage = renderPdfPage(renderer, pageIndex);
        ProviderOcrCall providerCall =
            callProvider(
                Base64.getEncoder().encodeToString(pageImage), JPEG_MEDIA_TYPE, correlationId);
        if (!providerCall.providerAvailable()) {
          return unreadable(OcrOutcome.PROVIDER_UNAVAILABLE);
        }

        ModelOcrOutput pageOutput = providerCall.modelOutput();

        if (pageOutput.status() == OcrStatus.INVALID) {
          return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
        }

        if (pageOutput.status() == OcrStatus.UNCERTAIN) {
          return unreadable(OcrOutcome.UNCERTAIN);
        }

        if (pageOutput.status() == OcrStatus.ANSWERS) {
          answerPages.add(pageOutput.text());
          lowestConfidence = Math.min(lowestConfidence, pageOutput.confidence());
        }
      }

      if (answerPages.isEmpty()) {
        return unreadable(OcrOutcome.NO_ANSWERS);
      }

      return new OcrExtractionResult(
          new OcrResult(String.join("\n", answerPages), lowestConfidence, false),
          OcrOutcome.ANSWERS);
    } catch (Exception exception) {
      return unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
    }
  }

  private boolean hasSupportedPageCount(PDDocument document) {
    int pageCount = document.getNumberOfPages();
    return pageCount > 0 && pageCount <= MAX_PDF_PAGES;
  }

  private boolean canRenderWithinLimit(org.apache.pdfbox.pdmodel.PDPage page) {
    PDRectangle cropBox = page.getCropBox();
    double widthPoints = cropBox.getWidth();
    double heightPoints = cropBox.getHeight();
    int rotation = Math.floorMod(page.getRotation(), 360);

    if (rotation == 90 || rotation == 270) {
      double originalWidth = widthPoints;
      widthPoints = heightPoints;
      heightPoints = originalWidth;
    }

    long widthPixels = (long) Math.ceil(widthPoints * PDF_RENDER_DPI / 72);
    long heightPixels = (long) Math.ceil(heightPoints * PDF_RENDER_DPI / 72);
    return widthPixels > 0
        && heightPixels > 0
        && widthPixels <= Integer.MAX_VALUE
        && heightPixels <= Integer.MAX_VALUE
        && widthPixels <= MAX_RENDERED_PDF_PIXELS / heightPixels;
  }

  private byte[] renderPdfPage(PDFRenderer renderer, int pageIndex) throws IOException {
    BufferedImage renderedPage =
        renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI, ImageType.RGB);

    if ((long) renderedPage.getWidth() * renderedPage.getHeight() > MAX_RENDERED_PDF_PIXELS) {
      throw new IOException("Rendered PDF page exceeds image size limit");
    }

    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(renderedPage, "jpeg", output)) {
        throw new IOException("JPEG encoder is unavailable");
      }
      return output.toByteArray();
    }
  }

  private ProviderOcrCall callProvider(String base64Image, String mediaType, String correlationId) {
    Map<String, Object> requestPayload = buildOcrRequest(base64Image, mediaType);
    HttpHeaders headers = createProviderHeaders();
    headers.set("X-Ocr-Correlation-Id", correlationId);

    try {
      Map<String, Object> providerResponse =
          restTemplate.postForObject(apiUrl, new HttpEntity<>(requestPayload, headers), Map.class);

      if (providerResponse == null || !providerResponse.containsKey("choices")) {
        return ProviderOcrCall.available(ModelOcrOutput.invalid());
      }

      return ProviderOcrCall.available(
          parseModelOutput(extractFirstChoiceContent(providerResponse)));
    } catch (Exception exception) {
      return ProviderOcrCall.unavailable();
    }
  }

  private OcrExtractionResult toExtractionResult(ProviderOcrCall providerCall) {
    if (!providerCall.providerAvailable()) {
      return unreadable(OcrOutcome.PROVIDER_UNAVAILABLE);
    }

    ModelOcrOutput output = providerCall.modelOutput();
    return switch (output.status()) {
      case ANSWERS -> new OcrExtractionResult(toOcrResult(output), OcrOutcome.ANSWERS);
      case NO_ANSWERS -> unreadable(OcrOutcome.NO_ANSWERS);
      case UNCERTAIN -> unreadable(OcrOutcome.UNCERTAIN);
      case INVALID -> unreadable(OcrOutcome.INVALID_PROVIDER_RESPONSE);
    };
  }

  /**
   * Streams and retains no more than 32 KiB of the provider HTTP body. Production extraction keeps
   * its existing request path, so the diagnostic safety limit cannot change submissions.
   */
  private DiagnosticProviderCall callProviderForDiagnostic(
      String base64Image, String mediaType, String correlationId) {
    Map<String, Object> requestPayload = buildOcrRequest(base64Image, mediaType);
    HttpHeaders headers = createProviderHeaders();
    headers.set("X-Ocr-Diagnostic-Correlation-Id", correlationId);

    try {
      BoundedRawResponse rawProviderResponse =
          diagnosticRestTemplate()
              .execute(
                  apiUrl,
                  HttpMethod.POST,
                  request -> {
                    request.getHeaders().putAll(headers);
                    OBJECT_MAPPER.writeValue(request.getBody(), requestPayload);
                  },
                  this::readBoundedProviderResponse);

      if (rawProviderResponse == null) {
        return DiagnosticProviderCall.providerUnavailable();
      }

      if (rawProviderResponse.truncated()) {
        return DiagnosticProviderCall.responseTooLarge(rawProviderResponse);
      }

      return new DiagnosticProviderCall(
          parseRawProviderResponse(rawProviderResponse.value()),
          rawProviderResponse.value(),
          true,
          false,
          rawProviderResponse.length(),
          rawProviderResponse.statusCode(),
          false);
    } catch (Exception exception) {
      // Provider exception details can include request or response content. Do not retain them.
      return DiagnosticProviderCall.providerUnavailable();
    }
  }

  private RestTemplate diagnosticRestTemplate() {
    RestTemplate diagnosticRestTemplate = new RestTemplate(restTemplate.getRequestFactory());
    diagnosticRestTemplate.setErrorHandler(
        new ResponseErrorHandler() {
          @Override
          public boolean hasError(ClientHttpResponse response) {
            return false;
          }
        });
    return diagnosticRestTemplate;
  }

  private BoundedRawResponse readBoundedProviderResponse(ClientHttpResponse response)
      throws IOException {
    int statusCode = response.getStatusCode().value();
    InputStream body = response.getBody();
    if (body == null) {
      return new BoundedRawResponse("", false, 0, statusCode);
    }

    byte[] buffer = new byte[4_096];
    try (InputStream responseBody = body;
        ByteArrayOutputStream capturedResponse =
            new ByteArrayOutputStream(MAX_DIAGNOSTIC_RAW_RESPONSE_BYTES)) {
      int capturedBytes = 0;
      while (capturedBytes < MAX_DIAGNOSTIC_RAW_RESPONSE_BYTES) {
        int requestedBytes =
            Math.min(buffer.length, MAX_DIAGNOSTIC_RAW_RESPONSE_BYTES - capturedBytes);
        int bytesRead = responseBody.read(buffer, 0, requestedBytes);
        if (bytesRead == -1) {
          return new BoundedRawResponse(
              capturedResponse.toString(StandardCharsets.UTF_8), false, capturedBytes, statusCode);
        }

        capturedResponse.write(buffer, 0, bytesRead);
        capturedBytes += bytesRead;
      }

      boolean responseTooLarge = responseBody.read() != -1;
      return new BoundedRawResponse(
          capturedResponse.toString(StandardCharsets.UTF_8),
          responseTooLarge,
          capturedBytes,
          statusCode);
    }
  }

  private ModelOcrOutput parseRawProviderResponse(String rawProviderResponse) {
    try {
      JsonNode response = OBJECT_MAPPER.readTree(rawProviderResponse);
      if (response == null || !response.isObject()) {
        return ModelOcrOutput.invalid();
      }

      JsonNode choices = response.get("choices");
      if (choices == null || !choices.isArray() || choices.isEmpty()) {
        return ModelOcrOutput.invalid();
      }

      JsonNode firstChoice = choices.get(0);
      JsonNode message = firstChoice == null ? null : firstChoice.get("message");
      JsonNode content = message == null ? null : message.get("content");
      if (content == null || !content.isTextual()) {
        return ModelOcrOutput.invalid();
      }

      return parseModelOutput(content.textValue());
    } catch (JsonProcessingException exception) {
      return ModelOcrOutput.invalid();
    }
  }

  private OcrDiagnosticStatus diagnosticStatus(
      DiagnosticProviderCall providerCall, ModelOcrOutput modelOutput) {
    if (!providerCall.rawResponseCaptured()) {
      return OcrDiagnosticStatus.PROVIDER_UNAVAILABLE;
    }

    if (providerCall.httpStatus() >= 400) {
      return OcrDiagnosticStatus.HTTP_REJECTION;
    }

    if (providerCall.responseTooLarge()) {
      return OcrDiagnosticStatus.RESPONSE_TOO_LARGE;
    }

    return switch (modelOutput.status()) {
      case ANSWERS -> OcrDiagnosticStatus.ANSWERS;
      case NO_ANSWERS -> OcrDiagnosticStatus.NO_ANSWERS;
      case UNCERTAIN -> OcrDiagnosticStatus.UNCERTAIN;
      case INVALID -> OcrDiagnosticStatus.INVALID_PROVIDER_RESPONSE;
    };
  }

  private OcrResult toOcrResult(ModelOcrOutput output) {
    if (output.status() != OcrStatus.ANSWERS) {
      return unreadable();
    }

    return new OcrResult(output.text(), output.confidence(), false);
  }

  private OcrResult unreadable() {
    return new OcrResult("", 0, true);
  }

  private OcrExtractionResult unreadable(OcrOutcome outcome) {
    return new OcrExtractionResult(unreadable(), outcome);
  }

  private Map<String, Object> buildOcrRequest(String base64Image, String mediaType) {
    List<Map<String, Object>> messageContent =
        List.of(
            Map.of("type", "text", "text", OCR_PROMPT),
            Map.of(
                "type",
                "image_url",
                "image_url",
                Map.of("url", "data:" + mediaType + ";base64," + base64Image)));
    Map<String, Object> userMessage = Map.of("role", "user", "content", messageContent);

    return Map.of("model", visionModel, "messages", List.of(userMessage));
  }

  private HttpHeaders createProviderHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);
    return headers;
  }

  @SuppressWarnings("unchecked")
  private String extractFirstChoiceContent(Map<String, Object> providerResponse) {
    List<Map<String, Object>> choices = (List<Map<String, Object>>) providerResponse.get("choices");
    Map<String, Object> firstMessage = (Map<String, Object>) choices.get(0).get("message");
    return (String) firstMessage.get("content");
  }

  private ModelOcrOutput parseModelOutput(String rawContent) {
    String cleanedContent = cleanModelOutput(rawContent);
    if (cleanedContent.isBlank()) {
      return ModelOcrOutput.invalid();
    }

    try {
      JsonNode output = OBJECT_MAPPER.readTree(cleanedContent);
      if (!output.isObject() || output.size() != 3) {
        return ModelOcrOutput.invalid();
      }

      JsonNode statusNode = output.get("status");
      JsonNode regionsNode = output.get("regions");
      JsonNode confidenceNode = output.get("confidence");
      if (statusNode == null
          || regionsNode == null
          || !statusNode.isTextual()
          || !isNormalizedConfidence(confidenceNode)) {
        return ModelOcrOutput.invalid();
      }

      List<String> studentAnswerRegions = parseStudentAnswerRegions(regionsNode);
      if (studentAnswerRegions == null) {
        return ModelOcrOutput.invalid();
      }

      String text = String.join("\n", studentAnswerRegions);
      double confidence = confidenceNode.doubleValue();
      return switch (statusNode.textValue()) {
        case "answers" ->
            text.isBlank()
                ? ModelOcrOutput.invalid()
                : new ModelOcrOutput(OcrStatus.ANSWERS, text, confidence);
        case "no_answers" ->
            text.isBlank()
                ? new ModelOcrOutput(OcrStatus.NO_ANSWERS, "", confidence)
                : ModelOcrOutput.invalid();
        case "uncertain" ->
            text.isBlank()
                ? new ModelOcrOutput(OcrStatus.UNCERTAIN, "", 0)
                : ModelOcrOutput.invalid();
        default -> ModelOcrOutput.invalid();
      };
    } catch (JsonProcessingException exception) {
      return ModelOcrOutput.invalid();
    }
  }

  private List<String> parseStudentAnswerRegions(JsonNode regionsNode) {
    if (!regionsNode.isArray()) {
      return null;
    }

    List<String> studentAnswerRegions = new ArrayList<>();
    for (JsonNode region : regionsNode) {
      if (!region.isObject() || region.size() != 2) {
        return null;
      }

      JsonNode typeNode = region.get("type");
      JsonNode textNode = region.get("text");
      if (typeNode == null || textNode == null || !typeNode.isTextual() || !textNode.isTextual()) {
        return null;
      }

      String text = cleanModelOutput(textNode.textValue());
      switch (typeNode.textValue()) {
        case "student_answer" -> {
          if (text.isBlank()) {
            return null;
          }
          studentAnswerRegions.add(text);
        }
        case "diagram", "printed_content" -> {
          // These regions are intentionally never persisted as answers.
        }
        default -> {
          return null;
        }
      }
    }

    return studentAnswerRegions;
  }

  private boolean isNormalizedConfidence(JsonNode confidenceNode) {
    if (confidenceNode == null || !confidenceNode.isNumber()) {
      return false;
    }

    double confidence = confidenceNode.doubleValue();
    return Double.isFinite(confidence) && confidence >= 0 && confidence <= 1;
  }

  private String cleanModelOutput(String rawText) {
    if (rawText == null) {
      return "";
    }

    return rawText.trim();
  }

  private boolean isSupportedImage(String mediaType) {
    return JPEG_MEDIA_TYPE.equals(mediaType) || PNG_MEDIA_TYPE.equals(mediaType);
  }

  public record OcrResult(String text, double confidence, boolean unreadable) {}

  /** These values are metric labels, so the set must remain deliberately bounded. */
  public enum OcrOutcome {
    ANSWERS,
    NO_ANSWERS,
    UNCERTAIN,
    INVALID_PROVIDER_RESPONSE,
    PROVIDER_UNAVAILABLE
  }

  public record OcrExtractionResult(OcrResult result, OcrOutcome outcome) {}

  public enum OcrDiagnosticStatus {
    DISABLED,
    UNSUPPORTED_INPUT,
    PROVIDER_UNAVAILABLE,
    HTTP_REJECTION,
    RESPONSE_TOO_LARGE,
    INVALID_PROVIDER_RESPONSE,
    NO_ANSWERS,
    UNCERTAIN,
    ANSWERS
  }

  /**
   * Safe to expose from a diagnostic endpoint or test report: it has no image, answer text,
   * provider exception, credential, or raw provider response.
   */
  public record OcrDiagnosticMetadata(
      boolean rawProviderResponseCaptured,
      boolean rawProviderResponseTruncated,
      int rawProviderResponseLength,
      int providerHttpStatus,
      boolean studentAnswerDetected,
      double confidence) {}

  public static final class OcrDiagnosticResult {

    private final String correlationId;
    private final OcrDiagnosticStatus status;
    private final OcrDiagnosticMetadata metadata;
    private final String rawProviderResponse;
    private final String extractedAnswerText;

    private OcrDiagnosticResult(
        String correlationId,
        OcrDiagnosticStatus status,
        OcrDiagnosticMetadata metadata,
        String rawProviderResponse,
        String extractedAnswerText) {
      this.correlationId = correlationId;
      this.status = status;
      this.metadata = metadata;
      this.rawProviderResponse = rawProviderResponse;
      this.extractedAnswerText = extractedAnswerText;
    }

    private static OcrDiagnosticResult disabled(String correlationId) {
      return new OcrDiagnosticResult(
          correlationId, OcrDiagnosticStatus.DISABLED, emptyDiagnosticMetadata(), null, null);
    }

    private static OcrDiagnosticResult unsupportedInput(String correlationId) {
      return new OcrDiagnosticResult(
          correlationId,
          OcrDiagnosticStatus.UNSUPPORTED_INPUT,
          emptyDiagnosticMetadata(),
          null,
          null);
    }

    private static OcrDiagnosticMetadata emptyDiagnosticMetadata() {
      return new OcrDiagnosticMetadata(false, false, 0, 0, false, 0);
    }

    public String correlationId() {
      return correlationId;
    }

    public OcrDiagnosticStatus status() {
      return status;
    }

    public OcrDiagnosticMetadata metadata() {
      return metadata;
    }

    /**
     * Safe diagnostic output. Keep this to one line so a test report cannot accidentally include
     * provider content when a live reproduction is run.
     */
    public String sanitizedReport() {
      return "ocr_diagnostic correlation_id="
          + correlationId
          + " status="
          + status
          + " raw_captured="
          + metadata.rawProviderResponseCaptured()
          + " raw_truncated="
          + metadata.rawProviderResponseTruncated()
          + " raw_length="
          + metadata.rawProviderResponseLength()
          + " provider_http_status="
          + metadata.providerHttpStatus()
          + " student_answer_detected="
          + metadata.studentAnswerDetected()
          + " confidence="
          + metadata.confidence();
    }

    Optional<String> rawProviderResponse() {
      return Optional.ofNullable(rawProviderResponse);
    }

    Optional<String> extractedAnswerText() {
      return Optional.ofNullable(extractedAnswerText);
    }
  }

  private enum OcrStatus {
    ANSWERS,
    NO_ANSWERS,
    UNCERTAIN,
    INVALID
  }

  private record ModelOcrOutput(OcrStatus status, String text, double confidence) {

    private static ModelOcrOutput invalid() {
      return new ModelOcrOutput(OcrStatus.INVALID, "", 0);
    }
  }

  private record BoundedRawResponse(String value, boolean truncated, int length, int statusCode) {}

  private record DiagnosticProviderCall(
      ModelOcrOutput modelOutput,
      String rawResponse,
      boolean rawResponseCaptured,
      boolean rawResponseTruncated,
      int rawResponseLength,
      int httpStatus,
      boolean responseTooLarge) {

    private static DiagnosticProviderCall providerUnavailable() {
      return new DiagnosticProviderCall(ModelOcrOutput.invalid(), null, false, false, 0, 0, false);
    }

    private static DiagnosticProviderCall responseTooLarge(BoundedRawResponse rawResponse) {
      return new DiagnosticProviderCall(
          ModelOcrOutput.invalid(),
          rawResponse.value(),
          true,
          true,
          rawResponse.length(),
          rawResponse.statusCode(),
          true);
    }
  }

  private record ProviderOcrCall(ModelOcrOutput modelOutput, boolean providerAvailable) {

    private static ProviderOcrCall available(ModelOcrOutput modelOutput) {
      return new ProviderOcrCall(modelOutput, true);
    }

    private static ProviderOcrCall unavailable() {
      return new ProviderOcrCall(ModelOcrOutput.invalid(), false);
    }
  }
}
