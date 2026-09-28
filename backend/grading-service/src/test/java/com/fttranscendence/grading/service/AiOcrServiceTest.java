package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AiOcrServiceTest {

  @Mock private RestTemplate restTemplate;

  private AiOcrService service;

  @BeforeEach
  void setUp() {
    service = new AiOcrService(restTemplate);
    ReflectionTestUtils.setField(service, "apiUrl", "http://localhost/ocr-test");
    ReflectionTestUtils.setField(service, "visionModel", "test-vision-model");
    ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
  }

  @Test
  void rejectsMissingAndTemplateProviderCredentials() {
    assertThrows(IllegalArgumentException.class, () -> AiOcrService.validateApiKey(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> AiOcrService.validateApiKey("REPLACE_WITH_AN_APPROVED_AI_PROVIDER_KEY"));
    assertThrows(
        IllegalArgumentException.class, () -> AiOcrService.validateApiKey("change-me-key"));
    assertDoesNotThrow(() -> AiOcrService.validateApiKey("sk-real-provider-key"));
  }

  @Test
  void diagnosticIsDisabledByDefaultAndDoesNotCallTheProvider() {
    AiOcrService.OcrDiagnosticResult result = service.diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertDoesNotThrow(() -> UUID.fromString(result.correlationId()));
    assertEquals(AiOcrService.OcrDiagnosticStatus.DISABLED, result.status());
    assertFalse(result.metadata().rawProviderResponseCaptured());
    assertFalse(result.metadata().studentAnswerDetected());
    assertEquals(0, result.metadata().confidence());
    verifyNoInteractions(restTemplate);
  }

  @Test
  void diagnosticCapturesTheRawProviderHttpBodyInBoundedMemoryAndExposesMetadataOnly() {
    DiagnosticServiceFixture fixture = diagnosticService();
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            request ->
                assertTrue(
                    request.getHeaders().getFirst("X-Ocr-Diagnostic-Correlation-Id") != null
                        && !request
                            .getHeaders()
                            .getFirst("X-Ocr-Diagnostic-Correlation-Id")
                            .isBlank(),
                    "The diagnostic correlation header must be present."))
        .andRespond(
            withSuccess(
                providerRawResponse(answers("student response", .82)), MediaType.APPLICATION_JSON));

    AiOcrService.OcrDiagnosticResult result =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals(AiOcrService.OcrDiagnosticStatus.ANSWERS, result.status());
    assertTrue(result.metadata().rawProviderResponseCaptured());
    assertFalse(result.metadata().rawProviderResponseTruncated());
    assertTrue(result.metadata().rawProviderResponseLength() > 0);
    assertTrue(result.metadata().studentAnswerDetected());
    assertEquals(.82, result.metadata().confidence());
    assertTrue(result.rawProviderResponse().isPresent());
    assertTrue(result.extractedAnswerText().isPresent());
    assertFalse(result.sanitizedReport().contains("student response"));
    assertTrue(result.sanitizedReport().startsWith("ocr_diagnostic correlation_id="));
    fixture.server().verify();
  }

  @Test
  void diagnosticDoesNotCallTheProviderForUnsupportedInput() {
    ReflectionTestUtils.setField(service, "diagnosticEnabled", true);

    AiOcrService.OcrDiagnosticResult result =
        service.diagnose(new byte[] {1, 2, 3}, "application/pdf");

    assertEquals(AiOcrService.OcrDiagnosticStatus.UNSUPPORTED_INPUT, result.status());
    assertFalse(result.metadata().rawProviderResponseCaptured());
    verifyNoInteractions(restTemplate);
  }

  @Test
  void diagnosticClassifiesNoAnswerUncertainAndMalformedProviderBodiesWithoutLeakingContent() {
    DiagnosticServiceFixture fixture = diagnosticService();
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(withSuccess(providerRawResponse(noAnswers(.91)), MediaType.APPLICATION_JSON));
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(
            withSuccess(
                providerRawResponse(classifiedOutput("uncertain", List.of(), .4)),
                MediaType.APPLICATION_JSON));
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

    AiOcrService.OcrDiagnosticResult noAnswers =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");
    AiOcrService.OcrDiagnosticResult uncertain =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");
    AiOcrService.OcrDiagnosticResult malformed =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals(AiOcrService.OcrDiagnosticStatus.NO_ANSWERS, noAnswers.status());
    assertFalse(noAnswers.metadata().studentAnswerDetected());
    assertEquals(AiOcrService.OcrDiagnosticStatus.UNCERTAIN, uncertain.status());
    assertFalse(uncertain.metadata().studentAnswerDetected());
    assertEquals(AiOcrService.OcrDiagnosticStatus.INVALID_PROVIDER_RESPONSE, malformed.status());
    assertTrue(malformed.metadata().rawProviderResponseCaptured());
    assertFalse(malformed.sanitizedReport().contains("not-json"));
    fixture.server().verify();
  }

  @Test
  void diagnosticCapturesAProviderRejectionBodyWithoutExposingItsContent() {
    DiagnosticServiceFixture fixture = diagnosticService();
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"provider rejected request\"}"));

    AiOcrService.OcrDiagnosticResult result =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals(AiOcrService.OcrDiagnosticStatus.HTTP_REJECTION, result.status());
    assertTrue(result.metadata().rawProviderResponseCaptured());
    assertEquals(422, result.metadata().providerHttpStatus());
    assertFalse(result.sanitizedReport().contains("provider rejected request"));
    fixture.server().verify();
  }

  @Test
  void diagnosticDoesNotRetainTransportFailureDetails() {
    DiagnosticServiceFixture fixture = diagnosticService();
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(
            request -> {
              throw new IOException("provider transport failure");
            });

    AiOcrService.OcrDiagnosticResult result =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals(AiOcrService.OcrDiagnosticStatus.PROVIDER_UNAVAILABLE, result.status());
    assertFalse(result.metadata().rawProviderResponseCaptured());
    assertFalse(result.sanitizedReport().contains("provider transport failure"));
    fixture.server().verify();
  }

  @Test
  void diagnosticBoundsAProviderResponseKeptInMemory() {
    DiagnosticServiceFixture fixture = diagnosticService();
    fixture
        .server()
        .expect(once(), requestTo("http://localhost/ocr-test"))
        .andRespond(
            withSuccess(
                providerRawResponseWithPadding(answers("student response", .82), 32_001),
                MediaType.APPLICATION_JSON));

    AiOcrService.OcrDiagnosticResult result =
        fixture.service().diagnose(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals(AiOcrService.OcrDiagnosticStatus.RESPONSE_TOO_LARGE, result.status());
    assertTrue(result.metadata().rawProviderResponseTruncated());
    assertEquals(32_000, result.metadata().rawProviderResponseLength());
    assertEquals(32_000, result.rawProviderResponse().orElseThrow().length());
    fixture.server().verify();
  }

  @Test
  void extractsOnlyStudentAnswersAndSendsAnswerOnlyPrompt() {
    whenProviderReturns(answers("4", .98));

    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals("4", result.text());
    assertEquals(.98, result.confidence());
    assertFalse(result.unreadable());
    ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .postForObject(eq("http://localhost/ocr-test"), entityCaptor.capture(), eq(Map.class));
    assertEquals(
        "Bearer test-api-key", entityCaptor.getValue().getHeaders().getFirst("Authorization"));
    assertDoesNotThrow(
        () ->
            UUID.fromString(entityCaptor.getValue().getHeaders().getFirst("X-Ocr-Correlation-Id")));
    assertTrue(entityCaptor.getValue().getBody().toString().contains("student-authored"));
    assertTrue(entityCaptor.getValue().getBody().toString().contains("diagram-aware"));
    assertTrue(entityCaptor.getValue().getBody().toString().contains("student_answer"));
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) entityCaptor.getValue().getBody();
    assertFalse(payload.containsKey("temperature"));
  }

  @Test
  void filtersDiagramAndPrintedRegionsFromStudentAnswerOutput() {
    whenProviderReturns(
        classifiedOutput(
            "answers",
            List.of(
                region("diagram", "10 N, left"),
                region("printed_content", "Figure 1: Forces"),
                region("student_answer", "The forces are balanced."),
                region("diagram", "right, 10 N")),
            .96));

    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals("The forces are balanced.", result.text());
    assertEquals(.96, result.confidence());
    assertFalse(result.unreadable());
  }

  @Test
  void treatsDiagramOnlyOutputAsNoAnswer() {
    whenProviderReturns(
        classifiedOutput(
            "no_answers",
            List.of(region("diagram", "10 N, left"), region("printed_content", "Figure 1: Forces")),
            .99));

    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals("", result.text());
    assertEquals(0, result.confidence());
    assertTrue(result.unreadable());
  }

  @Test
  void preservesLowModelConfidenceForAnswerOnlyRecognition() {
    whenProviderReturns(answers("x = ?", .31));

    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals("x = ?", result.text());
    assertEquals(.31, result.confidence());
    assertFalse(result.unreadable());
  }

  @Test
  void classifiesProviderAndRecognitionOutcomesWithoutChangingTheOcrResultContract() {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(
            providerResponse(answers("answer", .82)),
            providerResponse(noAnswers(.9)),
            providerResponse(classifiedOutput("uncertain", List.of(), 0)),
            providerResponse("not-json"))
        .thenThrow(new RestClientException("provider unavailable"));

    assertEquals(
        AiOcrService.OcrOutcome.ANSWERS,
        service.extractWithOutcome(new byte[] {1}, "image/jpeg", "correlation-one").outcome());
    assertEquals(
        AiOcrService.OcrOutcome.NO_ANSWERS,
        service.extractWithOutcome(new byte[] {2}, "image/jpeg", "correlation-two").outcome());
    assertEquals(
        AiOcrService.OcrOutcome.UNCERTAIN,
        service.extractWithOutcome(new byte[] {3}, "image/jpeg", "correlation-three").outcome());
    assertEquals(
        AiOcrService.OcrOutcome.INVALID_PROVIDER_RESPONSE,
        service.extractWithOutcome(new byte[] {4}, "image/jpeg", "correlation-four").outcome());
    assertEquals(
        AiOcrService.OcrOutcome.PROVIDER_UNAVAILABLE,
        service.extractWithOutcome(new byte[] {5}, "image/jpeg", "correlation-five").outcome());
  }

  @Test
  void treatsConfirmedNoAnswerImageAsUnreadable() {
    whenProviderReturns(noAnswers(.99));

    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");

    assertEquals("", result.text());
    assertEquals(0, result.confidence());
    assertTrue(result.unreadable());
  }

  @Test
  void rejectsLegacyMalformedAndUncertainProviderResponses() {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(
            providerResponse("answer"),
            providerResponse("{\"status\":\"answers\",\"regions\":[]}"),
            providerResponse(
                "{\"status\":\"answers\",\"regions\":[{\"type\":\"student_answer\",\"text\":\"4\"}],\"confidence\":1.2}"),
            providerResponse(
                "{\"status\":\"no_answers\",\"regions\":[{\"type\":\"student_answer\",\"text\":\"printed title\"}],\"confidence\":.9}"),
            providerResponse(
                "{\"status\":\"uncertain\",\"regions\":[{\"type\":\"student_answer\",\"text\":\"4\"}],\"confidence\":.8}"));

    for (int call = 0; call < 5; call++) {
      AiOcrService.OcrResult result = service.extract(new byte[] {1, 2, 3}, "image/jpeg");
      assertEquals("", result.text());
      assertEquals(0, result.confidence());
      assertTrue(result.unreadable());
    }
  }

  @Test
  void preservesTheUploadedImageMediaTypeForVisionProviders() {
    whenProviderReturns(answers("student answer", .9));

    service.extract(new byte[] {1, 2, 3}, "image/png");

    ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .postForObject(eq("http://localhost/ocr-test"), entityCaptor.capture(), eq(Map.class));
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) entityCaptor.getValue().getBody();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) messages.get(0).get("content");
    @SuppressWarnings("unchecked")
    Map<String, String> imageUrl = (Map<String, String>) content.get(1).get("image_url");
    assertEquals("data:image/png;base64,AQID", imageUrl.get("url"));
  }

  @Test
  void rendersDiagramWorksheetPdfAndReturnsOnlyTheStudentAnswerRegion() throws IOException {
    whenProviderReturns(
        classifiedOutput(
            "answers",
            List.of(
                region("printed_content", "Figure 1: Forces"),
                region("diagram", "10 N, left"),
                region("student_answer", "The forces are balanced."),
                region("diagram", "right, 10 N")),
            .96));

    AiOcrService.OcrResult result = service.extract(diagramWorksheetPdf(), "application/pdf");

    assertEquals("The forces are balanced.", result.text());
    assertEquals(.96, result.confidence());
    assertFalse(result.unreadable());
    verify(restTemplate)
        .postForObject(eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  void aggregatesAnswerPagesInOrderAndUsesLowestConfidence() throws IOException {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(
            providerResponse(answers("first answer", .96)),
            providerResponse(noAnswers(.99)),
            providerResponse(answers("second answer", .72)));

    AiOcrService.OcrResult result =
        service.extract(
            typedPdf(
                List.of("Question 1", "first answer"),
                List.of("Question 2"),
                List.of("Question 3", "second answer")),
            "application/pdf");

    assertEquals("first answer\nsecond answer", result.text());
    assertEquals(.72, result.confidence());
    assertFalse(result.unreadable());
    verify(restTemplate, times(3))
        .postForObject(eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class));
  }

  @Test
  void rejectsWholePdfWhenAnyPageProviderResponseFails() throws IOException {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(providerResponse(answers("first answer", .96)))
        .thenThrow(new RestClientException("timeout"));

    AiOcrService.OcrResult result =
        service.extract(
            typedPdf(List.of("Question 1", "first answer"), List.of("Question 2", "second answer")),
            "application/pdf");

    assertEquals("", result.text());
    assertEquals(0, result.confidence());
    assertTrue(result.unreadable());
  }

  @Test
  void rejectsPdfWhenAllPagesHaveNoAnswers() throws IOException {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(providerResponse(noAnswers(.99)), providerResponse(noAnswers(.99)));

    AiOcrService.OcrResult result =
        service.extract(
            typedPdf(List.of("Worksheet title"), List.of("Question 1")), "application/pdf");

    assertTrue(result.unreadable());
    assertEquals("", result.text());
    assertEquals(0, result.confidence());
  }

  @Test
  void marksMalformedAndEncryptedPdfsAsUnreadableWithoutCallingProvider() throws IOException {
    AiOcrService.OcrResult malformedPdf = service.extract(new byte[] {1, 2, 3}, "application/pdf");
    AiOcrService.OcrResult encryptedPdf = service.extract(encryptedPdf(), "application/pdf");

    assertTrue(malformedPdf.unreadable());
    assertTrue(encryptedPdf.unreadable());
    verifyNoInteractions(restTemplate);
  }

  @Test
  void rejectsPdfPageAndRenderLimitsBeforeCallingProvider() throws IOException {
    AiOcrService.OcrResult tooManyPages = service.extract(pdfWithPageCount(101), "application/pdf");
    AiOcrService.OcrResult oversizedPage =
        service.extract(pdfWithPageSize(10_000, 10_000), "application/pdf");

    assertTrue(tooManyPages.unreadable());
    assertTrue(oversizedPage.unreadable());
    verifyNoInteractions(restTemplate);
  }

  private void whenProviderReturns(String content) {
    when(restTemplate.postForObject(
            eq("http://localhost/ocr-test"), any(HttpEntity.class), eq(Map.class)))
        .thenReturn(providerResponse(content));
  }

  private Map<String, Object> providerResponse(String content) {
    return Map.of("choices", List.of(Map.of("message", Map.of("content", content))));
  }

  private String providerRawResponse(String content) {
    String escapedContent = content.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"choices\":[{\"message\":{\"content\":\"" + escapedContent + "\"}}]}";
  }

  private String providerRawResponseWithPadding(String content, int paddingLength) {
    String rawProviderResponse = providerRawResponse(content);
    String padding = "x".repeat(paddingLength);
    return rawProviderResponse.substring(0, rawProviderResponse.length() - 1)
        + ",\"padding\":\""
        + padding
        + "\"}";
  }

  private DiagnosticServiceFixture diagnosticService() {
    RestTemplate diagnosticRestTemplate = new RestTemplate();
    AiOcrService diagnosticService = new AiOcrService(diagnosticRestTemplate);
    ReflectionTestUtils.setField(diagnosticService, "apiUrl", "http://localhost/ocr-test");
    ReflectionTestUtils.setField(diagnosticService, "visionModel", "test-vision-model");
    ReflectionTestUtils.setField(diagnosticService, "apiKey", "test-api-key");
    ReflectionTestUtils.setField(diagnosticService, "diagnosticEnabled", true);
    return new DiagnosticServiceFixture(
        diagnosticService, MockRestServiceServer.bindTo(diagnosticRestTemplate).build());
  }

  private record DiagnosticServiceFixture(AiOcrService service, MockRestServiceServer server) {}

  private String answers(String text, double confidence) {
    return classifiedOutput("answers", List.of(region("student_answer", text)), confidence);
  }

  private String noAnswers(double confidence) {
    return classifiedOutput("no_answers", List.of(), confidence);
  }

  private String classifiedOutput(String status, List<String> regions, double confidence) {
    return "{\"status\":\""
        + status
        + "\",\"regions\":["
        + String.join(",", regions)
        + "],\"confidence\":"
        + confidence
        + "}";
  }

  private String region(String type, String text) {
    return "{\"type\":\"" + type + "\",\"text\":\"" + text + "\"}";
  }

  @SafeVarargs
  private final byte[] typedPdf(List<String>... pageLines) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (List<String> lines : pageLines) {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
          content.beginText();
          content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          content.newLineAtOffset(72, 700);
          for (String line : lines) {
            content.showText(line);
            content.newLineAtOffset(0, -18);
          }
          content.endText();
        }
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private byte[] encryptedPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      document.protect(new StandardProtectionPolicy("owner-password", "", new AccessPermission()));
      document.save(output);
      return output.toByteArray();
    }
  }

  private byte[] diagramWorksheetPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.beginText();
        content.newLineAtOffset(72, 700);
        content.showText("Worksheet title");
        content.newLineAtOffset(0, -24);
        content.showText("Question: Describe the forces in Figure 1.");
        content.newLineAtOffset(0, -24);
        content.showText("Figure 1: Forces");
        content.endText();

        content.moveTo(160, 600);
        content.lineTo(360, 600);
        content.stroke();
        content.beginText();
        content.newLineAtOffset(110, 610);
        content.showText("10 N, left");
        content.newLineAtOffset(250, 0);
        content.showText("right, 10 N");
        content.newLineAtOffset(-250, -90);
        content.showText("Student response: The forces are balanced.");
        content.endText();
      }

      document.save(output);
      return output.toByteArray();
    }
  }

  private byte[] pdfWithPageCount(int pageCount) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
        document.addPage(new PDPage());
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private byte[] pdfWithPageSize(float width, float height) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage(new PDRectangle(width, height)));
      document.save(output);
      return output.toByteArray();
    }
  }
}
