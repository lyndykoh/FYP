package com.fttranscendence.learning.question.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OpenAiQuestionVisionAnalyzerTest {
  @Test
  void sendsStrictStructuredOutputRequestAndAcceptsBoundedProviderResult() throws Exception {
    AtomicReference<String> requestBody = new AtomicReference<>();
    OpenAiQuestionVisionAnalyzer analyzer =
        analyzer(
            (endpoint, authorization, body, timeout) -> {
              requestBody.set(body);
              assertEquals("Bearer server-only-key", authorization);
              assertEquals(Duration.ofSeconds(5), timeout);
              return completion(
                  """
                {"candidates":[{"number":1,"prompt":"1. Explain evaporation.",
                "modelAnswer":"Water gains heat energy.","confidence":96,
                "questionType":"OPEN_ENDED","difficulty":"FOUNDATION","tags":["water"],
                "boundingBox":{"x":0,"y":0,"width":4,"height":3},"diagrams":[],"warningMessage":""}]}
                """);
            });

    QuestionVisionAnalyzer.PageAnalysis result = analyzer.analyze(page());

    assertFalse(result.failed());
    assertEquals(1, result.candidates().size());
    assertEquals(96, result.candidates().get(0).confidence());
    assertEquals(4, result.candidates().get(0).boundingBox().width());
    assertTrue(requestBody.get().contains("question_page_analysis"));
    assertTrue(requestBody.get().contains("json_schema"));
    assertTrue(requestBody.get().contains("data:image/png;base64"));
    JsonNode request = new ObjectMapper().readTree(requestBody.get());
    assertFalse(request.has("temperature"));
  }

  @Test
  void rejectsProviderBoxOutsideSourcePageWithoutReturningCandidates() {
    OpenAiQuestionVisionAnalyzer analyzer =
        analyzer(
            (endpoint, authorization, body, timeout) ->
                completion(
                    """
            {"candidates":[{"number":1,"prompt":"Question text","modelAnswer":"Answer",
            "confidence":99,"questionType":"OPEN_ENDED","difficulty":"FOUNDATION","tags":[],
            "boundingBox":{"x":3,"y":0,"width":2,"height":1},"diagrams":[],"warningMessage":""}]}
            """));

    QuestionVisionAnalyzer.PageAnalysis result = analyzer.analyze(page());

    assertTrue(result.failed());
    assertTrue(result.candidates().isEmpty());
    assertTrue(result.failureMessage().contains("invalid structured OCR data"));
  }

  @Test
  void acceptsOnlyUniqueInBoundsDiagramRegions() {
    OpenAiQuestionVisionAnalyzer analyzer =
        analyzer(
            (endpoint, authorization, body, timeout) ->
                completion(
                    """
            {"candidates":[{"number":1,"prompt":"Question text","modelAnswer":"Answer",
            "confidence":99,"questionType":"DIAGRAM","difficulty":"FOUNDATION","tags":["diagram"],
            "boundingBox":null,"diagrams":[{"regionId":"diagram-1","subQuestionId":"1a",
            "boundingBox":{"x":1,"y":1,"width":2,"height":2}}],"warningMessage":""}]}
            """));

    QuestionVisionAnalyzer.PageAnalysis result = analyzer.analyze(page());

    assertFalse(result.failed());
    assertEquals("diagram-1", result.candidates().get(0).diagrams().get(0).regionId());
    assertEquals("1a", result.candidates().get(0).diagrams().get(0).subQuestionId());
  }

  @Test
  void rejectsDiagramBoundsOutsideTheOriginalPage() {
    OpenAiQuestionVisionAnalyzer analyzer =
        analyzer(
            (endpoint, authorization, body, timeout) ->
                completion(
                    """
            {"candidates":[{"number":1,"prompt":"Question text","modelAnswer":"Answer",
            "confidence":99,"questionType":"DIAGRAM","difficulty":"FOUNDATION","tags":["diagram"],
            "boundingBox":null,"diagrams":[{"regionId":"diagram-1","subQuestionId":null,
            "boundingBox":{"x":3,"y":1,"width":2,"height":2}}],"warningMessage":""}]}
            """));

    QuestionVisionAnalyzer.PageAnalysis result = analyzer.analyze(page());

    assertTrue(result.failed());
    assertTrue(result.candidates().isEmpty());
  }

  @Test
  void rejectsPlaceholderCredentialsBeforeAnyRequest() {
    try {
      OpenAiQuestionVisionAnalyzer.validateApiKey("change-me-ai-api-key");
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("Expected placeholder credential rejection");
  }

  @Test
  void convertsMalformedProviderEnvelopesAndPayloadsIntoManualReviewFailures() {
    OpenAiQuestionVisionAnalyzer malformedEnvelope =
        analyzer((endpoint, authorization, body, timeout) -> "not json");
    QuestionVisionAnalyzer.PageAnalysis envelopeResult = malformedEnvelope.analyze(page());
    assertTrue(envelopeResult.failed());
    assertTrue(envelopeResult.candidates().isEmpty());

    OpenAiQuestionVisionAnalyzer malformedPayload =
        analyzer((endpoint, authorization, body, timeout) -> completion("[]"));
    QuestionVisionAnalyzer.PageAnalysis payloadResult = malformedPayload.analyze(page());
    assertTrue(payloadResult.failed());
    assertTrue(payloadResult.failureMessage().contains("invalid structured OCR data"));
  }

  private static OpenAiQuestionVisionAnalyzer analyzer(
      OpenAiQuestionVisionAnalyzer.VisionHttpTransport transport) {
    return new OpenAiQuestionVisionAnalyzer(
        "https://vision.example.test/v1/chat/completions",
        "gpt-4.1-mini",
        "server-only-key",
        5_000,
        20,
        transport,
        new ObjectMapper());
  }

  private static QuestionVisionAnalyzer.PageImage page() {
    return new QuestionVisionAnalyzer.PageImage(new byte[] {1, 2, 3}, "image/png", 4, 3);
  }

  private static String completion(String content) {
    return "{\"choices\":[{\"message\":{\"content\":"
        + new ObjectMapper().valueToTree(content).toString()
        + "}}]}";
  }
}
