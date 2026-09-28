package com.fttranscendence.learning.dashboard;

import com.fttranscendence.learning.security.AuthenticatedUser;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/tutor/dashboard")
public class TutorDashboardController {
  private final TutorDashboardService dashboard;

  public TutorDashboardController(TutorDashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping
  public TutorDashboardResponse get(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "UTC") String timeZone) {
    return dashboard.dashboard(user.userId(), parseTimeZone(timeZone));
  }

  private ZoneId parseTimeZone(String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidTimeZoneException();
    }
    try {
      return ZoneId.of(value);
    } catch (DateTimeException exception) {
      throw new InvalidTimeZoneException();
    }
  }

  @ExceptionHandler(InvalidTimeZoneException.class)
  ResponseEntity<ApiError> invalidTimeZone() {
    return error(
        HttpStatus.BAD_REQUEST, "INVALID_TIME_ZONE", "timeZone must be a valid IANA time zone.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistence(RuntimeException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DASHBOARD_DATABASE_UNAVAILABLE",
        "Dashboard data is temporarily unavailable.");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  public record ApiError(String code, String message, Map<String, String> fields) {}

  public static class InvalidTimeZoneException extends RuntimeException {}
}
