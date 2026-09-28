package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.MistakeHistoryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped, canonical mistake history; raw OCR and Tutor notes are never exposed. */
@RestController
@RequestMapping("/api/grading/mistakes")
public class MistakeHistoryController {
  private final MistakeHistoryService history;
  private final LearningAuthorizationClient authorization;

  public MistakeHistoryController(
      MistakeHistoryService history, LearningAuthorizationClient authorization) {
    this.history = history;
    this.authorization = authorization;
  }

  @GetMapping("/students/{studentId}")
  public List<MistakeHistoryService.MistakeHistoryItem> forTutor(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @PathVariable long studentId) {
    long scopedStudentId = authorization.resolveMistakeHistoryStudent(user, bearer, studentId);
    return history.historyFor(scopedStudentId);
  }

  @GetMapping("/me")
  public List<MistakeHistoryService.MistakeHistoryItem> mine(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer) {
    long scopedStudentId = authorization.resolveMistakeHistoryStudent(user, bearer, null);
    return history.historyFor(scopedStudentId);
  }

  @ExceptionHandler(LearningAuthorizationClient.MistakeHistoryNotFound.class)
  ResponseEntity<Map<String, String>> notFound() {
    return error(
        HttpStatus.NOT_FOUND, "MISTAKE_HISTORY_NOT_FOUND", "Mistake history was not found.");
  }

  @ExceptionHandler(LearningAuthorizationClient.Forbidden.class)
  ResponseEntity<Map<String, String>> forbidden() {
    return error(
        HttpStatus.FORBIDDEN,
        "MISTAKE_HISTORY_FORBIDDEN",
        "You are not allowed to view this mistake history.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_MISTAKE_HISTORY_REQUEST", exception.getMessage());
  }

  private ResponseEntity<Map<String, String>> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "error", message));
  }
}
