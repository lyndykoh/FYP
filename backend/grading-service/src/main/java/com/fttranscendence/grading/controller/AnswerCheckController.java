package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.RuleBasedAnswerChecker;
import com.fttranscendence.grading.service.RuleCheckResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Produces reproducible, non-persistent rubric evidence for a Tutor. Question metadata comes only
 * from Learning Service; the request carries answer text and cannot select marks, criteria, or a
 * different student's state.
 */
@RestController
@RequestMapping("/api/grading/tutor/questions")
public class AnswerCheckController {
  private final LearningAuthorizationClient learning;
  private final RuleBasedAnswerChecker checker;

  public AnswerCheckController(
      LearningAuthorizationClient learning, RuleBasedAnswerChecker checker) {
    this.learning = learning;
    this.checker = checker;
  }

  @PostMapping("/{questionId}/rule-check")
  public RuleCheckResult check(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long questionId,
      @RequestBody AnswerCheckRequest request) {
    if (questionId <= 0 || request == null || request.answer() == null) {
      throw new InvalidCheckRequest("A positive question id and answer are required.");
    }

    LearningAuthorizationClient.QuestionContext question =
        learning.loadQuestion(user, bearer, questionId);

    if (!question.markingComponents().isEmpty()) {
      return checkWeightedCriteria(request.answer(), question);
    }

    return checker.check(request.answer(), question.keywords(), question.totalMarks());
  }

  private RuleCheckResult checkWeightedCriteria(
      String answer, LearningAuthorizationClient.QuestionContext question) {
    var ruleComponents =
        question.markingComponents().stream()
            .map(LearningAuthorizationClient.MarkingComponentContext::toRuleComponent)
            .toList();

    return checker.checkWeighted(answer, ruleComponents, question.totalMarks());
  }

  @ExceptionHandler(LearningAuthorizationClient.Forbidden.class)
  ResponseEntity<Map<String, String>> forbidden() {
    return error(
        HttpStatus.FORBIDDEN,
        "RULE_CHECK_FORBIDDEN",
        "You are not allowed to check this question.");
  }

  @ExceptionHandler(LearningAuthorizationClient.QuestionNotFound.class)
  ResponseEntity<Map<String, String>> notFound() {
    return error(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question was not found.");
  }

  @ExceptionHandler(LearningAuthorizationClient.QuestionUnavailable.class)
  ResponseEntity<Map<String, String>> unavailable() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "QUESTION_CONTEXT_UNAVAILABLE",
        "Question context is temporarily unavailable.");
  }

  @ExceptionHandler({InvalidCheckRequest.class, IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_RULE_CHECK", exception.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, String>> malformed() {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_RULE_CHECK",
        "Answer check contains invalid JSON or values.");
  }

  private ResponseEntity<Map<String, String>> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "error", message));
  }

  public record AnswerCheckRequest(String answer) {}

  public static final class InvalidCheckRequest extends RuntimeException {
    InvalidCheckRequest(String message) {
      super(message);
    }
  }
}
