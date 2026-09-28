package com.fttranscendence.learning.insight;

import com.fttranscendence.learning.classroom.ClassController;
import com.fttranscendence.learning.classroom.ClassService;
import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
    value = "/api/learning/tutor/insight-snapshots",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class InsightFeedbackController {
  private final ClassInsightService service;

  public InsightFeedbackController(ClassInsightService service) {
    this.service = service;
  }

  @PostMapping(value = "/{snapshotId}/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> feedback(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long snapshotId,
      @Valid @RequestBody ClassInsightRequests.FeedbackRequest request) {
    service.addFeedback(user.userId(), snapshotId, request.feedback());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(ClassService.ClassNotFoundException.class)
  ResponseEntity<ClassController.ApiError> notFound(ClassService.ClassNotFoundException error) {
    return ResponseEntity.status(404)
        .body(
            new ClassController.ApiError(
                "CLASS_NOT_FOUND", "Class was not found for this tutor", Map.of()));
  }
}
