package com.fttranscendence.learning.insight;

import com.fttranscendence.learning.security.AuthenticatedUser;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-scoped read endpoints for evidence-backed individual learning profiles. */
@RestController
@RequestMapping("/api/learning")
public class InsightController {
  private final LearningProfileService profiles;

  public InsightController(LearningProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/tutor/students/{studentId}/learning-profile")
  public LearningProfileService.LearningProfileResponse tutor(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long studentId) {
    return profiles.forTutor(user.userId(), studentId);
  }

  @GetMapping("/student/learning-profile")
  public LearningProfileService.LearningProfileResponse student(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return profiles.forStudent(user.userId());
  }

  @ExceptionHandler(LearningProfileService.ProfileNotFoundException.class)
  ResponseEntity<ApiError> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            new ApiError("LEARNING_PROFILE_NOT_FOUND", "Learning profile was not found", Map.of()));
  }

  public record ApiError(String code, String message, Map<String, String> fields) {}
}
