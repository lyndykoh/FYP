package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AiMarkingServiceTest {
  @Mock private RestTemplate restTemplate;
  private AiGradingService service;

  @BeforeEach
  void setUp() {
    service = new AiGradingService(restTemplate, new ObjectMapper(), new RuleBasedAnswerChecker());
    ReflectionTestUtils.setField(service, "apiUrl", "http://localhost/ai-test");
    ReflectionTestUtils.setField(service, "apiModel", "test-model");
    ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
  }

  @Test
  void acceptsACompleteProviderSuggestionAsAdvisoryOnly() {
    when(restTemplate.postForObject(
            eq("http://localhost/ai-test"),
            any(HttpEntity.class),
            eq(AiGradingService.ApiResponse.class)))
        .thenReturn(
            response(
                "{\"suggested_marks\":1.5,\"correctness\":\"Partially correct\",\"error_category\":\"Missing key point\",\"missing_keywords\":[\"conduction\"],\"feedback\":\"Explain heat transfer.\"}"));

    AiGradingService.AiMarkingResult result = mark("Metal transfers heat.");

    assertEquals(new BigDecimal("1.50"), result.suggestedMarks());
    assertEquals("Partially correct", result.correctness());
    assertEquals(List.of("conduction"), result.missingKeywords());
    assertEquals(true, result.providerResponseValid());
  }

  @Test
  void fallsBackWhenProviderFieldsAreMissingOrMalformed() {
    when(restTemplate.postForObject(
            eq("http://localhost/ai-test"),
            any(HttpEntity.class),
            eq(AiGradingService.ApiResponse.class)))
        .thenReturn(response("{\"correctness\":\"Correct\",\"feedback\":\"Looks good\"}"));

    AiGradingService.AiMarkingResult result = mark("METAL CONDUCTOR");

    assertEquals(new BigDecimal("2.00"), result.suggestedMarks());
    assertEquals("AI_RESPONSE_INVALID", result.errorCategory());
    assertEquals(false, result.providerResponseValid());
  }

  @Test
  void fallsBackDeterministicallyForTimeoutAndUnavailableProvider() {
    when(restTemplate.postForObject(
            eq("http://localhost/ai-test"),
            any(HttpEntity.class),
            eq(AiGradingService.ApiResponse.class)))
        .thenThrow(new RestClientException("timeout"));

    AiGradingService.AiMarkingResult result = mark("An unrelated answer");

    assertEquals(new BigDecimal("0.00"), result.suggestedMarks());
    assertEquals("AI_UNAVAILABLE", result.errorCategory());
    assertEquals(List.of("conductor"), result.missingKeywords());
  }

  @Test
  void calculatesAndSuppliesDeterministicComponentEvidenceBeforeCallingProvider() {
    when(restTemplate.postForObject(
            eq("http://localhost/ai-test"),
            any(HttpEntity.class),
            eq(AiGradingService.ApiResponse.class)))
        .thenReturn(
            response(
                "{\"suggested_marks\":2.0,\"correctness\":\"Correct\",\"feedback\":\"Good.\"}"));

    AiGradingService.AiMarkingResult result =
        service.evaluateMarking(
            "Why does metal feel hot?",
            "Metal conducts heat.",
            List.of("Explains heat conduction"),
            List.of(
                new RuleBasedAnswerChecker.WeightedMarkingComponent(
                    0,
                    "Explains heat conduction",
                    new BigDecimal("2.00"),
                    List.of("heat conduction"))),
            List.of("conductor"),
            "It uses heat conduction.",
            new BigDecimal("2.00"));

    assertEquals(new BigDecimal("2.00"), result.deterministicEvidence().awardedMarks());
    assertEquals(1, result.deterministicEvidence().componentResults().size());
    verify(restTemplate)
        .postForObject(
            eq("http://localhost/ai-test"),
            any(HttpEntity.class),
            eq(AiGradingService.ApiResponse.class));
  }

  private AiGradingService.AiMarkingResult mark(String answer) {
    return service.evaluateMarking(
        "Why does metal feel hot?",
        "Metal conducts heat.",
        List.of("Explains conduction"),
        List.of("conductor"),
        answer,
        new BigDecimal("2.00"));
  }

  private AiGradingService.ApiResponse response(String content) {
    return new AiGradingService.ApiResponse(
        List.of(new AiGradingService.Choice(new AiGradingService.Message("assistant", content))));
  }
}
