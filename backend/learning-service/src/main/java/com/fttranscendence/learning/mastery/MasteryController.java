package com.fttranscendence.learning.mastery;

import com.fttranscendence.learning.security.AuthenticatedUser;
import com.fttranscendence.learning.security.DomainAuthorizationService;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Canonical, role-scoped read API for the complete active syllabus hierarchy. */
@RestController
@RequestMapping("/api/learning")
public class MasteryController {

  private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO.setScale(2);

  private final MasteryRecordRepository records;
  private final DomainAuthorizationService authorization;
  private final SyllabusTopicRepository topics;

  public MasteryController(
      MasteryRecordRepository records,
      DomainAuthorizationService authorization,
      SyllabusTopicRepository topics) {
    this.records = records;
    this.authorization = authorization;
    this.topics = topics;
  }

  @GetMapping("/tutor/students/{studentId}/mastery-map")
  public MasteryMapResponse tutorMap(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long studentId) {
    return mapFor(ownedStudent(user.userId(), studentId));
  }

  @GetMapping("/student/mastery-map")
  public MasteryMapResponse studentMap(@AuthenticationPrincipal AuthenticatedUser user) {
    return mapFor(linkedStudent(user.userId()));
  }

  @GetMapping("/tutor/students/{studentId}/mastery-map/topics/{topicId}")
  public MasteryTopicDetailResponse tutorTopic(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long studentId,
      @PathVariable @Positive long topicId) {
    return topicFor(ownedStudent(user.userId(), studentId), topicId);
  }

  @GetMapping("/student/mastery-map/topics/{topicId}")
  public MasteryTopicDetailResponse studentTopic(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long topicId) {
    return topicFor(linkedStudent(user.userId()), topicId);
  }

  private StudentProfile ownedStudent(long tutorId, long studentId) {
    try {
      return authorization.requireTutorOwnedStudent(tutorId, studentId);
    } catch (DomainAuthorizationService.ResourceNotFoundException exception) {
      throw new MasteryNotFoundException();
    }
  }

  private StudentProfile linkedStudent(long loginUserId) {
    try {
      return authorization.requireStudentSelf(loginUserId);
    } catch (DomainAuthorizationService.ResourceNotFoundException exception) {
      throw new MasteryNotFoundException();
    }
  }

  private MasteryMapResponse mapFor(StudentProfile student) {
    List<SyllabusTopic> activeTopics =
        topics.findAllByActiveTrueOrderByDepthAscSortOrderAscCodeAsc();
    Map<Long, SyllabusTopic> activeTopicsById =
        activeTopics.stream().collect(Collectors.toMap(SyllabusTopic::getId, Function.identity()));
    List<MasteryRecord> profileRecords =
        records.findProfileRecordsByStudentProfileIdWithTopicAndHistory(student.getId()).stream()
            .filter(record -> activeTopicsById.containsKey(record.getSyllabusTopic().getId()))
            // A retraction leaves the aggregate row in place so future revisions
            // can be replayed deterministically, but a zero-attempt row contains
            // no currently approved learning evidence.
            .filter(record -> record.getAttemptCount() > 0)
            .toList();
    Map<Long, MasteryRecord> recordsByTopicId =
        profileRecords.stream()
            .collect(
                Collectors.toMap(
                    record -> record.getSyllabusTopic().getId(),
                    Function.identity(),
                    (first, ignored) -> first));
    List<MasteryNode> nodes =
        activeTopics.stream()
            .map(topic -> nodeFor(topic, recordsByTopicId.get(topic.getId())))
            .toList();
    BigDecimal overall =
        profileRecords.isEmpty()
            ? null
            : profileRecords.stream()
                .map(MasteryRecord::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(profileRecords.size()), 2, RoundingMode.HALF_UP);
    return new MasteryMapResponse(student.getId(), overall, nodes);
  }

  private MasteryTopicDetailResponse topicFor(StudentProfile student, long topicId) {
    SyllabusTopic topic =
        topics
            .findById(topicId)
            .filter(SyllabusTopic::isActive)
            .orElseThrow(MasteryNotFoundException::new);
    MasteryRecord record =
        records
            .findByStudentProfileIdAndSyllabusTopicIdWithHistory(student.getId(), topicId)
            .orElse(null);
    List<MasteryHistoryItem> history =
        record == null
            ? List.of()
            : record.getHistory().stream()
                .map(
                    item ->
                        new MasteryHistoryItem(
                            item.getPreviousScore(),
                            item.getNewScore(),
                            item.getPreviousStatus(),
                            item.getNewStatus(),
                            item.getReason(),
                            item.getCreatedAt()))
                .toList();
    return new MasteryTopicDetailResponse(student.getId(), nodeFor(topic, record), history);
  }

  private MasteryNode nodeFor(SyllabusTopic topic, MasteryRecord record) {
    if (record == null) {
      return new MasteryNode(
          topic.getId(),
          topic.getCode(),
          topic.getName(),
          topic.getParentId(),
          topic.getParentDepth(),
          topic.getDepth(),
          topic.getNodeType(),
          ZERO_SCORE,
          MasteryRecord.MasteryStatus.NOT_STARTED,
          0,
          null);
    }
    return new MasteryNode(
        topic.getId(),
        topic.getCode(),
        topic.getName(),
        topic.getParentId(),
        topic.getParentDepth(),
        topic.getDepth(),
        topic.getNodeType(),
        record.getScore(),
        record.getMasteryStatus(),
        record.getAttemptCount(),
        record.getCalculatedAt());
  }

  @ExceptionHandler(MasteryNotFoundException.class)
  ResponseEntity<ApiError> notFound(MasteryNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "MASTERY_NOT_FOUND", "Mastery data was not found");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistence(Exception exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "MASTERY_DATABASE_UNAVAILABLE",
        "Mastery data is temporarily unavailable");
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> validation(HandlerMethodValidationException exception) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Mastery request is invalid");
  }

  private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
  }

  public record MasteryMapResponse(
      Long studentId, BigDecimal overallScore, List<MasteryNode> nodes) {}

  public record MasteryNode(
      Long topicId,
      String topicCode,
      String topicName,
      Long parentTopicId,
      Short parentDepth,
      short depth,
      SyllabusTopic.NodeType nodeType,
      BigDecimal score,
      MasteryRecord.MasteryStatus status,
      int attemptCount,
      LocalDateTime calculatedAt) {}

  public record MasteryTopicDetailResponse(
      Long studentId, MasteryNode node, List<MasteryHistoryItem> history) {}

  public record MasteryHistoryItem(
      BigDecimal previousScore,
      BigDecimal newScore,
      MasteryRecord.MasteryStatus previousStatus,
      MasteryRecord.MasteryStatus newStatus,
      String reason,
      LocalDateTime occurredAt) {}

  public record ApiError(String code, String message, Map<String, String> fields) {}

  static class MasteryNotFoundException extends RuntimeException {}
}
