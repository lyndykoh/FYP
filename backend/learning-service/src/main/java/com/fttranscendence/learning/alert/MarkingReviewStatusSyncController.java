package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/learning/internal/marking-review-state")
public class MarkingReviewStatusSyncController {
  private static final String INTEGRATION_KEY_HEADER = "X-Learning-Integration-Key";

  private final MarkingReviewStatusProjectionRepository reviews;
  private final StudentProfileRepository students;
  private final byte[] integrationKey;

  public MarkingReviewStatusSyncController(
      MarkingReviewStatusProjectionRepository reviews,
      StudentProfileRepository students,
      @Value("${learning.marking-sync-key}") String integrationKey) {
    if (integrationKey == null || integrationKey.isBlank()) {
      throw new IllegalArgumentException("LEARNING_MARKING_SYNC_KEY is required");
    }

    this.reviews = reviews;
    this.students = students;
    this.integrationKey = integrationKey.getBytes(StandardCharsets.UTF_8);
  }

  @PostMapping
  @Transactional
  public ResponseEntity<Void> sync(
      @RequestHeader(value = INTEGRATION_KEY_HEADER, required = false) String integrationKeyHeader,
      @RequestBody ReviewState request) {
    verifyIntegrationKey(integrationKeyHeader);
    validateRequest(request);

    StudentProfile student = findStudent(request);
    MarkingReviewStatusProjection.State state = parseReviewState(request.reviewState());
    int revision = Math.toIntExact(request.revision());
    MarkingReviewStatusProjection projection =
        findOrCreateProjection(request, student, revision, state);

    projection.setWorksheetId(request.worksheetId());
    projection.set(revision, state, request.occurredAt());
    reviews.save(projection);

    return ResponseEntity.noContent().build();
  }

  private void verifyIntegrationKey(String integrationKeyHeader) {
    if (!matches(integrationKeyHeader)) {
      throw new Forbidden();
    }
  }

  private void validateRequest(ReviewState request) {
    boolean isInvalidRequest =
        request == null
            || request.submissionId() <= 0
            || request.studentId() <= 0
            || request.tutorUserId() <= 0
            || request.worksheetId() <= 0
            || request.revision() <= 0
            || request.reviewState() == null
            || request.occurredAt() == null;

    if (isInvalidRequest) {
      throw new IllegalArgumentException("Review state payload is invalid.");
    }
  }

  private StudentProfile findStudent(ReviewState request) {
    return students
        .findByIdAndTutorId(request.studentId(), request.tutorUserId())
        .orElseThrow(ReviewContextNotFound::new);
  }

  private MarkingReviewStatusProjection.State parseReviewState(String reviewState) {
    try {
      return MarkingReviewStatusProjection.State.valueOf(reviewState);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Review state payload is invalid.");
    }
  }

  private MarkingReviewStatusProjection findOrCreateProjection(
      ReviewState request,
      StudentProfile student,
      int revision,
      MarkingReviewStatusProjection.State state) {
    return reviews
        .findById(request.submissionId())
        .orElseGet(
            () ->
                new MarkingReviewStatusProjection(
                    request.submissionId(),
                    request.tutorUserId(),
                    request.worksheetId(),
                    student,
                    revision,
                    state,
                    request.occurredAt()));
  }

  private boolean matches(String candidate) {
    return candidate != null
        && MessageDigest.isEqual(integrationKey, candidate.getBytes(StandardCharsets.UTF_8));
  }

  @ExceptionHandler(Forbidden.class)
  ResponseEntity<ApiError> forbidden() {
    return error(HttpStatus.FORBIDDEN, "MARKING_SYNC_FORBIDDEN", "Marking sync is not permitted.");
  }

  @ExceptionHandler(ReviewContextNotFound.class)
  ResponseEntity<ApiError> missing() {
    return error(
        HttpStatus.NOT_FOUND,
        "MARKING_SYNC_CONTEXT_NOT_FOUND",
        "Marking sync context was not found.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_MARKING_SYNC", exception.getMessage());
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  record ReviewState(
      String eventKey,
      long revision,
      long submissionId,
      long tutorUserId,
      long studentId,
      long worksheetId,
      String reviewState,
      LocalDateTime occurredAt) {}

  record ApiError(String code, String message, Map<String, String> fields) {}

  static class Forbidden extends RuntimeException {}

  static class ReviewContextNotFound extends RuntimeException {}
}
