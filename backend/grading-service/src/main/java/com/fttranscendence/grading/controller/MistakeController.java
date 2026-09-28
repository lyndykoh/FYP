package com.fttranscendence.grading.controller;

import com.fttranscendence.grading.model.MistakeRecord;
import com.fttranscendence.grading.model.MistakeType;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.service.LearningAuthorizationClient;
import com.fttranscendence.grading.service.MistakeHistoryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Student-owned, persisted mistake review with curriculum-aware filtering. */
@RestController
@RequestMapping("/api/grading/student/mistakes")
public class MistakeController {
  private final MistakeHistoryService history;
  private final LearningAuthorizationClient authorization;

  public MistakeController(
      MistakeHistoryService history, LearningAuthorizationClient authorization) {
    this.history = history;
    this.authorization = authorization;
  }

  @GetMapping
  public List<StudentMistakeItem> mine(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearer,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) String topicId,
      @RequestParam(required = false) String mistakeType,
      @RequestParam(required = false) String worksheetId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    MistakeFilter filter =
        MistakeFilter.parse(subjectId, topicId, mistakeType, worksheetId, from, to);
    long studentId = authorization.resolveMistakeHistoryStudent(user, bearer, null);
    LearningAuthorizationClient.SyllabusTaxonomy taxonomy =
        authorization.loadSyllabusTaxonomy(bearer);
    filter.validateTaxonomy(taxonomy);

    List<MistakeRecord> studentMistakes = history.recordsFor(studentId);
    Map<OccurrenceKey, Long> occurrencesByTypeAndTopic =
        countOccurrences(studentMistakes, taxonomy);
    Predicate<MistakeRecord> matchesRequestedFilters = filter.matches(taxonomy);

    return studentMistakes.stream()
        .filter(matchesRequestedFilters)
        .map(record -> toStudentMistakeItem(record, taxonomy, occurrencesByTypeAndTopic))
        .toList();
  }

  private static Map<OccurrenceKey, Long> countOccurrences(
      List<MistakeRecord> studentMistakes, LearningAuthorizationClient.SyllabusTaxonomy taxonomy) {
    return studentMistakes.stream()
        .collect(
            Collectors.groupingBy(
                record -> OccurrenceKey.of(record, taxonomy), Collectors.counting()));
  }

  private static StudentMistakeItem toStudentMistakeItem(
      MistakeRecord record,
      LearningAuthorizationClient.SyllabusTaxonomy taxonomy,
      Map<OccurrenceKey, Long> occurrencesByTypeAndTopic) {
    long occurrenceCount = occurrencesByTypeAndTopic.get(OccurrenceKey.of(record, taxonomy));
    return StudentMistakeItem.from(record, taxonomy, occurrenceCount);
  }

  @ExceptionHandler(LearningAuthorizationClient.MistakeHistoryNotFound.class)
  ResponseEntity<Map<String, String>> notFound() {
    return error(
        HttpStatus.NOT_FOUND,
        "STUDENT_MISTAKES_NOT_FOUND",
        "Student mistake history was not found.");
  }

  @ExceptionHandler(LearningAuthorizationClient.Forbidden.class)
  ResponseEntity<Map<String, String>> forbidden() {
    return error(
        HttpStatus.FORBIDDEN,
        "STUDENT_MISTAKES_FORBIDDEN",
        "You are not allowed to view this mistake history.");
  }

  @ExceptionHandler(LearningAuthorizationClient.SyllabusUnavailable.class)
  ResponseEntity<Map<String, String>> syllabusUnavailable() {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "SYLLABUS_UNAVAILABLE",
        "Curriculum metadata is temporarily unavailable.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
    return error(
        HttpStatus.BAD_REQUEST, "INVALID_STUDENT_MISTAKES_REQUEST", exception.getMessage());
  }

  private ResponseEntity<Map<String, String>> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("code", code, "error", message));
  }

  public record StudentMistakeItem(
      long id,
      long worksheetId,
      long worksheetQuestionId,
      long questionBankId,
      Long syllabusTopicId,
      String syllabusTopicCode,
      Long subjectId,
      String subjectName,
      String topicName,
      String mistakeType,
      String mistakeLabel,
      String description,
      LocalDateTime recordedAt,
      long occurrenceCount,
      String status) {
    static StudentMistakeItem from(
        MistakeRecord record,
        LearningAuthorizationClient.SyllabusTaxonomy taxonomy,
        long occurrenceCount) {
      LearningAuthorizationClient.SyllabusTopic topic = findTopic(record, taxonomy);
      LearningAuthorizationClient.SyllabusSubject subject = topic == null ? null : topic.subject();

      return new StudentMistakeItem(
          record.getId(),
          record.getWorksheetId(),
          record.getWorksheetQuestionId(),
          record.getQuestionBankId(),
          record.getSyllabusTopicId(),
          record.getSyllabusTopicCode(),
          subject == null ? null : subject.id(),
          subject == null ? null : subject.name(),
          topic == null ? null : topic.name(),
          record.getMistakeType().name(),
          record.getMistakeType().getLabel(),
          record.getDescription(),
          record.getCreatedAt(),
          occurrenceCount,
          "CONFIRMED");
    }

    private static LearningAuthorizationClient.SyllabusTopic findTopic(
        MistakeRecord record, LearningAuthorizationClient.SyllabusTaxonomy taxonomy) {
      Long syllabusTopicId = record.getSyllabusTopicId();
      return syllabusTopicId == null ? null : taxonomy.topics().get(syllabusTopicId);
    }
  }

  private record OccurrenceKey(Long subjectId, Long topicId, MistakeType type) {
    static OccurrenceKey of(
        MistakeRecord record, LearningAuthorizationClient.SyllabusTaxonomy taxonomy) {
      LearningAuthorizationClient.SyllabusTopic topic =
          StudentMistakeItem.findTopic(record, taxonomy);
      Long subjectId = topic == null || topic.subject() == null ? null : topic.subject().id();

      return new OccurrenceKey(subjectId, record.getSyllabusTopicId(), record.getMistakeType());
    }
  }

  private record MistakeFilter(
      Long subjectId,
      Long topicId,
      MistakeType type,
      Long worksheetId,
      LocalDate from,
      LocalDate to) {
    static MistakeFilter parse(
        String subjectId,
        String topicId,
        String mistakeType,
        String worksheetId,
        String from,
        String to) {
      LocalDate start = date(from, "from");
      LocalDate end = date(to, "to");

      if (start != null && end != null && start.isAfter(end)) {
        throw new IllegalArgumentException("from must be on or before to.");
      }

      return new MistakeFilter(
          positive(subjectId, "subjectId"),
          positive(topicId, "topicId"),
          blank(mistakeType) ? null : MistakeType.fromLabel(mistakeType),
          positive(worksheetId, "worksheetId"),
          start,
          end);
    }

    void validateTaxonomy(LearningAuthorizationClient.SyllabusTaxonomy taxonomy) {
      if (subjectId != null && !taxonomy.subjects().containsKey(subjectId)) {
        throw new IllegalArgumentException("subjectId must identify an active subject.");
      }
      if (topicId != null && !taxonomy.topics().containsKey(topicId)) {
        throw new IllegalArgumentException("topicId must identify an active topic or subtopic.");
      }
    }

    Predicate<MistakeRecord> matches(LearningAuthorizationClient.SyllabusTaxonomy taxonomy) {
      return record -> {
        LearningAuthorizationClient.SyllabusTopic topic =
            StudentMistakeItem.findTopic(record, taxonomy);
        Long recordSubjectId =
            topic == null || topic.subject() == null ? null : topic.subject().id();
        LocalDate recordedOn = record.getCreatedAt().toLocalDate();

        return (subjectId == null || Objects.equals(subjectId, recordSubjectId))
            && (topicId == null || Objects.equals(topicId, record.getSyllabusTopicId()))
            && (type == null || type == record.getMistakeType())
            && (worksheetId == null || Objects.equals(worksheetId, record.getWorksheetId()))
            && (from == null || !recordedOn.isBefore(from))
            && (to == null || !recordedOn.isAfter(to));
      };
    }

    private static Long positive(String value, String name) {
      if (blank(value)) {
        return null;
      }

      try {
        long parsed = Long.parseLong(value.trim());
        if (parsed <= 0) {
          throw new IllegalArgumentException(name + " must be positive.");
        }
        return parsed;
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException(name + " must be a positive integer.");
      }
    }

    private static LocalDate date(String value, String name) {
      if (blank(value)) {
        return null;
      }

      try {
        return LocalDate.parse(value.trim());
      } catch (DateTimeParseException exception) {
        throw new IllegalArgumentException(name + " must be an ISO date (yyyy-MM-dd).");
      }
    }

    private static boolean blank(String value) {
      return value == null || value.isBlank();
    }
  }
}
