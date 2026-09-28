package com.fttranscendence.learning.insight;

import com.fttranscendence.learning.classroom.ClassController;
import com.fttranscendence.learning.classroom.ClassService;
import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    value = "/api/learning/tutor/classes/{classId}",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class ClassInsightController {
  private final ClassInsightService service;

  public ClassInsightController(ClassInsightService service) {
    this.service = service;
  }

  @GetMapping("/covered-topics")
  public List<ClassInsightService.CoveredTopic> coveredTopics(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return service.coveredTopics(user.userId(), classId);
  }

  @PutMapping(value = "/covered-topics", consumes = MediaType.APPLICATION_JSON_VALUE)
  public List<ClassInsightService.CoveredTopic> coveredTopics(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @Valid @RequestBody ClassInsightRequests.CoveredTopicsRequest request) {
    return service.replaceCoveredTopics(user.userId(), classId, request.topicIds());
  }

  @GetMapping("/insight-settings")
  public ClassInsightService.Settings settings(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return service.settings(user.userId(), classId);
  }

  @PatchMapping(value = "/insight-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ClassInsightService.Settings settings(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @Valid @RequestBody ClassInsightRequests.SettingsRequest request) {
    return service.updateSettings(
        user.userId(),
        classId,
        new ClassInsightService.Settings(
            request.weakAverageMasteryPercent(),
            request.weakStudentRatioPercent(),
            request.minimumActiveStudents()));
  }

  @GetMapping("/insights")
  public ClassInsightResponse insights(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return service.insights(user.userId(), classId);
  }

  @PutMapping(
      value = "/insights/ranking-overrides/{topicId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> ranking(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @PathVariable @Positive long topicId,
      @Valid @RequestBody ClassInsightRequests.RankingRequest request) {
    service.updateRanking(user.userId(), classId, topicId, request.rank(), request.note());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(ClassService.ClassNotFoundException.class)
  ResponseEntity<ClassController.ApiError> notFound(ClassService.ClassNotFoundException error) {
    return ResponseEntity.status(404)
        .body(
            new ClassController.ApiError(
                "CLASS_NOT_FOUND", "Class was not found for this tutor", Map.of()));
  }

  @ExceptionHandler(ClassInsightService.InvalidInsightRequestException.class)
  ResponseEntity<ClassController.ApiError> invalid(
      ClassInsightService.InvalidInsightRequestException error) {
    return ResponseEntity.badRequest()
        .body(
            new ClassController.ApiError("INVALID_INSIGHT_REQUEST", error.getMessage(), Map.of()));
  }
}
