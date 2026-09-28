package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

/** Provider-facing regression cases stay isolated from live AI credentials. */
class OcrWorkflowIntegrationTest {
  @Test
  void unreadableAndUnavailableProviderResultsAreMarkedForReview() {
    AiOcrService service =
        new AiOcrService(mock(org.springframework.web.client.RestTemplate.class));
    org.springframework.test.util.ReflectionTestUtils.setField(
        service, "apiUrl", "http://unavailable");
    org.springframework.test.util.ReflectionTestUtils.setField(service, "visionModel", "vision");
    org.springframework.test.util.ReflectionTestUtils.setField(service, "apiKey", "key");
    AiOcrService.OcrResult result = service.extract(new byte[] {1, 2}, "image/jpeg");
    assertTrue(result.unreadable());
    assertEquals(0, result.confidence());
  }
}
