package com.fttranscendence.grading.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiGradingService {

  private static final String JSON_OBJECT_RESPONSE_TYPE = "json_object";
  private static final String MANUAL_REVIEW_REQUIRED = "Manual review required";
  private static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
  private static final String AI_RESPONSE_INVALID = "AI_RESPONSE_INVALID";
  private static final String AI_FALLBACK_FEEDBACK =
      "AI advice is unavailable. Review the rubric evidence before approving.";

  @Value("${ai.engine.url}")
  private String apiUrl;

  @Value("${ai.engine.model}")
  private String apiModel;

  @Value("${ai.engine.api-key}")
  private String apiKey;

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final RuleBasedAnswerChecker ruleChecker;

  public AiGradingService(
      RestTemplate restTemplate, ObjectMapper objectMapper, RuleBasedAnswerChecker ruleChecker) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.ruleChecker = ruleChecker;
  }

  public AiDiagnosticResult evaluateSubmission(
      String questionContext, List<String> rubric, String studentAnswer) {
    String rubricText = formatRubric(rubric);

    String systemInstruction =
        "You are a strict grading assistant. Evaluate the student's answer against "
            + "the rubric. Always respond in pure JSON format.";
    String userPrompt =
        String.format(
            "Question Context: %s\nExpected Rubric Targets:\n%s\nStudent Answer Content: \"%s\"\n\n"
                + "Respond with a JSON object containing keys: correctness, error_category, "
                + "missing_keywords (array), and feedback.",
            questionContext, rubricText, studentAnswer);

    ApiRequest requestPayload = createJsonRequest(systemInstruction, userPrompt);

    ApiResponse rawResponse;
    try {
      rawResponse =
          restTemplate.postForObject(
              apiUrl, new HttpEntity<>(requestPayload, createProviderHeaders()), ApiResponse.class);
    } catch (Exception exception) {
      return diagnosticFallback(AI_UNAVAILABLE);
    }

    if (containsProviderMessage(rawResponse)) {
      try {
        AiDiagnosticResult providerResult =
            objectMapper.readValue(
                rawResponse.choices().get(0).message().content(), AiDiagnosticResult.class);
        if (isValid(providerResult)) {
          return normalizeDiagnosticResult(providerResult);
        }
      } catch (Exception exception) {
        return diagnosticFallback(AI_RESPONSE_INVALID);
      }
    }

    return diagnosticFallback(AI_RESPONSE_INVALID);
  }

  private AiDiagnosticResult diagnosticFallback(String category) {
    return new AiDiagnosticResult(
        MANUAL_REVIEW_REQUIRED, category, List.of(), AI_FALLBACK_FEEDBACK);
  }

  /**
   * Produces an advisory only. The returned result is not a final grade and is deliberately
   * persisted as an AI suggestion until a Tutor approves it.
   */
  public AiMarkingResult evaluateMarking(
      String questionContext,
      String modelAnswer,
      List<String> rubric,
      List<String> scoringKeywords,
      String studentAnswer,
      BigDecimal maximumMarks) {
    return evaluateMarking(
        questionContext,
        modelAnswer,
        rubric,
        List.of(),
        scoringKeywords,
        studentAnswer,
        maximumMarks);
  }

  /**
   * Builds deterministic evidence before asking the provider. The provider receives that evidence
   * as context but remains advisory-only; it cannot alter a persisted result without an explicit
   * Tutor approval.
   */
  public AiMarkingResult evaluateMarking(
      String questionContext,
      String modelAnswer,
      List<String> rubric,
      List<RuleBasedAnswerChecker.WeightedMarkingComponent> markingComponents,
      List<String> scoringKeywords,
      String studentAnswer,
      BigDecimal maximumMarks) {
    validateMarkingRequest(questionContext, modelAnswer, rubric, maximumMarks);

    boolean hasWeightedComponents = markingComponents != null && !markingComponents.isEmpty();
    RuleCheckResult deterministicEvidence =
        hasWeightedComponents
            ? ruleChecker.checkWeighted(studentAnswer, markingComponents, maximumMarks)
            : unweightedEvidence(studentAnswer, scoringKeywords, maximumMarks);

    String rubricText = String.join("\n", rubric);
    String systemInstruction =
        "You are an advisory marking assistant. Never state that a score is final. "
            + "Respond in pure JSON with suggested_marks, correctness, error_category, "
            + "missing_keywords, and feedback.";
    String userPrompt =
        String.format(
            "Question: %s\nModel answer: %s\nMarking criteria:\n%s\nMaximum marks: %s\n"
                + "Deterministic rubric evidence (advisory; do not override it): %s\n"
                + "Student answer: \"%s\"\nReturn a suggested_marks number between 0 and the "
                + "maximum. Keep feedback concise and actionable.",
            questionContext,
            modelAnswer,
            rubricText,
            maximumMarks,
            deterministicEvidence.explanation(),
            studentAnswer == null ? "" : studentAnswer);
    ApiRequest requestPayload = createJsonRequest(systemInstruction, userPrompt);

    try {
      ApiResponse providerResponse =
          restTemplate.postForObject(
              apiUrl, new HttpEntity<>(requestPayload, createProviderHeaders()), ApiResponse.class);
      if (hasChoices(providerResponse)) {
        ProviderMarking providerResult =
            objectMapper.readValue(
                providerResponse.choices().get(0).message().content(), ProviderMarking.class);
        if (isValid(providerResult, maximumMarks)) {
          return toAiMarkingResult(providerResult, deterministicEvidence);
        }
      }

      return deterministicFallback(deterministicEvidence, AI_RESPONSE_INVALID);
    } catch (Exception exception) {
      return deterministicFallback(deterministicEvidence, AI_UNAVAILABLE);
    }
  }

  private String formatRubric(List<String> rubric) {
    StringBuilder rubricText = new StringBuilder();

    for (int index = 0; index < rubric.size(); index++) {
      rubricText.append(index + 1).append(". ").append(rubric.get(index)).append("\n");
    }

    return rubricText.toString();
  }

  private ApiRequest createJsonRequest(String systemInstruction, String userPrompt) {
    return new ApiRequest(
        apiModel,
        List.of(new Message("system", systemInstruction), new Message("user", userPrompt)),
        new ResponseFormat(JSON_OBJECT_RESPONSE_TYPE));
  }

  private HttpHeaders createProviderHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);
    return headers;
  }

  private boolean containsProviderMessage(ApiResponse providerResponse) {
    return hasChoices(providerResponse) && providerResponse.choices().get(0).message() != null;
  }

  private boolean hasChoices(ApiResponse providerResponse) {
    return providerResponse != null
        && providerResponse.choices() != null
        && !providerResponse.choices().isEmpty();
  }

  private AiDiagnosticResult normalizeDiagnosticResult(AiDiagnosticResult providerResult) {
    List<String> trimmedMissingKeywords =
        providerResult.missingKeywords().stream().map(String::trim).toList();

    return new AiDiagnosticResult(
        providerResult.correctness().trim(),
        providerResult.errorCategory().trim(),
        trimmedMissingKeywords,
        providerResult.feedback().trim());
  }

  private void validateMarkingRequest(
      String questionContext, String modelAnswer, List<String> rubric, BigDecimal maximumMarks) {
    boolean hasQuestionContext = questionContext != null && !questionContext.isBlank();
    boolean hasModelAnswer = modelAnswer != null && !modelAnswer.isBlank();
    boolean hasRubric = rubric != null && !rubric.isEmpty();
    boolean hasPositiveMaximumMarks = maximumMarks != null && maximumMarks.signum() > 0;

    if (!hasQuestionContext || !hasModelAnswer || !hasRubric || !hasPositiveMaximumMarks) {
      throw new IllegalArgumentException(
          "Question context, rubric, model answer, and maximum marks are required.");
    }
  }

  private AiMarkingResult toAiMarkingResult(
      ProviderMarking providerResult, RuleCheckResult deterministicEvidence) {
    List<String> missingKeywords =
        providerResult.missingKeywords() == null ? List.of() : providerResult.missingKeywords();

    return new AiMarkingResult(
        providerResult.suggestedMarks().setScale(2),
        providerResult.correctness().trim(),
        blankToNull(providerResult.errorCategory()),
        missingKeywords,
        providerResult.feedback().trim(),
        true,
        deterministicEvidence);
  }

  private AiMarkingResult deterministicFallback(RuleCheckResult checked, String category) {
    return new AiMarkingResult(
        checked.awardedMarks(),
        MANUAL_REVIEW_REQUIRED,
        category,
        checked.missingKeywords(),
        "AI advice is unavailable. Review the deterministic rubric evidence before approving.",
        false,
        checked);
  }

  private RuleCheckResult unweightedEvidence(
      String answer, List<String> scoringKeywords, BigDecimal maximumMarks) {
    if (scoringKeywords == null || scoringKeywords.isEmpty()) {
      return new RuleCheckResult(
          BigDecimal.ZERO.setScale(2),
          maximumMarks,
          List.of(),
          List.of(),
          "No deterministic rubric targets are configured.",
          List.of());
    }
    return ruleChecker.check(answer, scoringKeywords, maximumMarks);
  }

  private boolean isValid(ProviderMarking result, BigDecimal maximumMarks) {
    return result != null
        && result.suggestedMarks() != null
        && result.suggestedMarks().signum() >= 0
        && result.suggestedMarks().compareTo(maximumMarks) <= 0
        && result.correctness() != null
        && !result.correctness().isBlank()
        && result.feedback() != null
        && !result.feedback().isBlank();
  }

  private boolean isValid(AiDiagnosticResult result) {
    return result != null
        && result.correctness() != null
        && !result.correctness().isBlank()
        && result.errorCategory() != null
        && !result.errorCategory().isBlank()
        && result.feedback() != null
        && !result.feedback().isBlank()
        && result.missingKeywords() != null
        && result.missingKeywords().stream()
            .allMatch(keyword -> keyword != null && !keyword.isBlank());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record Message(String role, String content) {}

  public record ResponseFormat(String type) {}

  public record ApiRequest(
      String model,
      List<Message> messages,
      @JsonProperty("response_format") ResponseFormat responseFormat) {}

  public record Choice(Message message) {}

  public record ApiResponse(List<Choice> choices) {}

  public record AiDiagnosticResult(
      String correctness,
      @JsonProperty("error_category") String errorCategory,
      @JsonProperty("missing_keywords") List<String> missingKeywords,
      String feedback) {}

  public record ProviderMarking(
      @JsonProperty("suggested_marks") BigDecimal suggestedMarks,
      String correctness,
      @JsonProperty("error_category") String errorCategory,
      @JsonProperty("missing_keywords") List<String> missingKeywords,
      String feedback) {}

  public record AiMarkingResult(
      BigDecimal suggestedMarks,
      String correctness,
      String errorCategory,
      List<String> missingKeywords,
      String feedback,
      boolean providerResponseValid,
      RuleCheckResult deterministicEvidence) {}
}
