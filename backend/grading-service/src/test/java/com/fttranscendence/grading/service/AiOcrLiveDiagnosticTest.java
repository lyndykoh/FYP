package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/**
 * A deliberately opt-in harness for a one-off handwriting diagnosis. It reads the image only during
 * this process, sends nothing unless all activation properties are present, and never writes image
 * bytes, answer text, raw provider text, credentials, or exceptions to output.
 */
@EnabledIfSystemProperty(named = "ocr.diagnostic.run-live", matches = "true")
@EnabledIfSystemProperty(named = "ocr.diagnostic.image-path", matches = ".+")
@EnabledIfSystemProperty(named = "ai.ocr.diagnostic.enabled", matches = "true")
class AiOcrLiveDiagnosticTest {

  @Test
  void reproducesTheConfiguredHandwritingImageWithoutPersistingItsContent() throws IOException {
    AiOcrService service = new AiOcrService(diagnosticRestTemplate());
    configureProvider(service);

    // This byte array is local to this test and is never logged, saved, or returned.
    byte[] imageBytes = Files.readAllBytes(Path.of(requiredProperty("ocr.diagnostic.image-path")));
    AiOcrService.OcrDiagnosticResult result = service.diagnose(imageBytes, "image/jpeg");

    try {
      assertEquals(AiOcrService.OcrDiagnosticStatus.ANSWERS, result.status());
      assertTrue(result.metadata().rawProviderResponseCaptured());
      assertTrue(result.metadata().studentAnswerDetected());
      assertTrue(result.metadata().confidence() > 0);
      assertTrue(result.rawProviderResponse().isPresent());
      assertOnlyStudentAnswerWasExtracted(result);
    } finally {
      // This is the sole output from a live run. It has no answer text, image content, or raw body.
      System.out.println(result.sanitizedReport());
    }
  }

  private RestTemplate diagnosticRestTemplate() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(10_000);
    requestFactory.setReadTimeout(30_000);
    return new RestTemplate(requestFactory);
  }

  private void configureProvider(AiOcrService service) {
    ReflectionTestUtils.setField(
        service, "apiUrl", requiredProviderSetting("ai.engine.url", "AI_ENGINE_URL"));
    ReflectionTestUtils.setField(
        service, "visionModel", requiredProviderSetting("ai.vision.model", "AI_VISION_MODEL"));
    ReflectionTestUtils.setField(
        service, "apiKey", requiredProviderSetting("ai.engine.api-key", "AI_ENGINE_API_KEY"));
    ReflectionTestUtils.setField(service, "diagnosticEnabled", true);
  }

  private String requiredProperty(String propertyName) {
    String propertyValue = System.getProperty(propertyName);
    assertTrue(
        propertyValue != null && !propertyValue.isBlank(),
        "A required live OCR diagnostic property is missing.");
    return propertyValue;
  }

  private String requiredProviderSetting(String propertyName, String environmentName) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }

    String environmentValue = System.getenv(environmentName);
    assertTrue(
        environmentValue != null && !environmentValue.isBlank(),
        "A required live OCR diagnostic provider setting is missing.");
    return environmentValue;
  }

  private void assertOnlyStudentAnswerWasExtracted(AiOcrService.OcrDiagnosticResult result) {
    String extractedAnswer = result.extractedAnswerText().orElse("");
    String normalizedAnswer = extractedAnswer.toLowerCase(Locale.ROOT);

    assertFalse(extractedAnswer.isBlank(), "The extracted answer must not be blank.");
    assertFalse(
        normalizedAnswer.contains("worksheet code"),
        "The extracted answer must exclude the worksheet code.");
    assertFalse(
        Pattern.compile("\\bmm\\b").matcher(normalizedAnswer).find(),
        "The extracted answer must exclude the printed worksheet title.");
    assertFalse(
        normalizedAnswer.contains("design a fair test"),
        "The extracted answer must exclude the printed question.");
    assertFalse(
        normalizedAnswer.contains("type: open ended"),
        "The extracted answer must exclude the printed response type.");
    assertFalse(
        normalizedAnswer.contains("marks: 3"),
        "The extracted answer must exclude the printed marks label.");
  }
}
