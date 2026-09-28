package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/tutor/alerts")
public class AlertController {
  private final AlertGenerationService alerts;

  public AlertController(AlertGenerationService alerts) {
    this.alerts = alerts;
  }

  @GetMapping
  public List<AlertGenerationService.AlertResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user) {
    return alerts.refresh(user.userId());
  }

  @PostMapping("/{alertId}/resolve")
  public AlertGenerationService.AlertResponse resolve(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long alertId) {
    return alerts.resolve(user.userId(), alertId);
  }

  @PostMapping("/{alertId}/dismiss")
  public AlertGenerationService.AlertResponse dismiss(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable long alertId) {
    return alerts.dismiss(user.userId(), alertId);
  }

  @ExceptionHandler(AlertGenerationService.AlertNotFoundException.class)
  ResponseEntity<ApiError> notFound() {
    return error(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "Alert was not found.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_ALERT_REQUEST", exception.getMessage());
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  record ApiError(String code, String message, Map<String, String> fields) {}
}
