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
@RequestMapping("/api/learning/student/dashboard")
public class StudentDashboardController {
  private final StudentDashboardService dashboard;

  public StudentDashboardController(StudentDashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping
  public StudentDashboardResponse get(
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

  @ExceptionHandler(StudentDashboardService.StudentDashboardNotFoundException.class)
  ResponseEntity<ApiError> notFound() {
    return error(
        HttpStatus.NOT_FOUND, "STUDENT_DASHBOARD_NOT_FOUND", "Student dashboard was not found.");
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
        "STUDENT_DASHBOARD_DATABASE_UNAVAILABLE",
        "Student dashboard data is temporarily unavailable.");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  public record ApiError(String code, String message, Map<String, String> fields) {}

  public static class InvalidTimeZoneException extends RuntimeException {}
}
