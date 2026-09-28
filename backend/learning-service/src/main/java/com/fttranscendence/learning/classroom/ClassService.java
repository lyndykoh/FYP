package com.fttranscendence.learning.classroom;

import com.fttranscendence.learning.insight.ClassInsightResponse;
import com.fttranscendence.learning.insight.ClassInsightService;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetAssignment;
import com.fttranscendence.learning.worksheet.WorksheetRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassService {

  private static final BigDecimal WEAK_AREA_SCORE_THRESHOLD = new BigDecimal("70.00");

  private final TutorClassRepository repository;
  private final EntityManager entityManager;
  private final StudentProfileRepository studentRepository;
  private final MasteryRecordRepository masteryRepository;
  private final WorksheetRepository worksheetRepository;
  private final ClassInsightService insightService;

  public ClassService(
      TutorClassRepository repository,
      EntityManager entityManager,
      StudentProfileRepository studentRepository,
      MasteryRecordRepository masteryRepository,
      WorksheetRepository worksheetRepository,
      ClassInsightService insightService) {
    this.repository = repository;
    this.entityManager = entityManager;
    this.studentRepository = studentRepository;
    this.masteryRepository = masteryRepository;
    this.worksheetRepository = worksheetRepository;
    this.insightService = insightService;
  }

  @Transactional(readOnly = true)
  public List<ClassRequest.ClassResponse> listOwnedClasses(long tutorId) {
    requireTutor(tutorId);

    return ownedClasses(tutorId).stream().map(ClassRequest.ClassResponse::from).toList();
  }

  /**
   * The detail projection itself remains read-only. Its insight availability lookup opens a
   * separate write-capable transaction only when it needs to enqueue a background refresh; see
   * {@link ClassInsightService#insights(long, long)}.
   */
  @Transactional(readOnly = true)
  public ClassDetailResponse getOwnedClassDetail(long tutorId, long classId) {
    requireTutor(tutorId);
    TutorClass tutorClass =
        repository.findById(classId).orElseThrow(() -> new ClassNotFoundException(classId));
    if (!Long.valueOf(tutorId).equals(tutorClass.getTutorId())) {
      throw new ClassAccessDeniedException(classId);
    }

    List<StudentProfile> students =
        studentRepository.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId);
    List<Long> studentIds = students.stream().map(StudentProfile::getId).toList();
    List<MasteryRecord> masteryRecords =
        studentIds.isEmpty()
            ? List.of()
            : masteryRepository.findAllByStudentProfileIdInWithTopic(studentIds);

    MasterySummaryData masterySummary = summarizeMasteryRecords(masteryRecords);

    List<ClassDetailResponse.StudentResponse> studentResponses =
        students.stream()
            .map(
                student ->
                    studentResponse(
                        student,
                        masterySummary.recordsByStudent().getOrDefault(student.getId(), List.of())))
            .toList();
    int recordsWithData = masteryRecords.size();
    int studentsWithMastery =
        (int) studentResponses.stream().filter(student -> student.masteryRecordCount() > 0).count();
    BigDecimal averageMasteryScore =
        recordsWithData == 0 ? null : average(masterySummary.totalScore(), recordsWithData);
    List<ClassDetailResponse.WeakAreaResponse> weakAreas = weakAreas(masterySummary.topics());

    List<ClassDetailResponse.WorksheetResponse> worksheets =
        worksheetRepository.findClassAssignedWorksheetsByTutorId(tutorId, classId).stream()
            .map(worksheet -> worksheetResponse(worksheet, classId))
            .toList();

    List<ClassDetailResponse.ScheduleResponse> schedules = schedulesFor(tutorClass);

    return new ClassDetailResponse(
        tutorClass.getId(),
        tutorClass.getTutorId(),
        tutorClass.getClassName(),
        tutorClass.getSubject(),
        tutorClass.getLevel(),
        tutorClass.getStatus(),
        schedules,
        studentResponses,
        new ClassDetailResponse.MasterySummary(
            averageMasteryScore, recordsWithData, studentsWithMastery),
        weakAreas,
        insightResponse(tutorId, classId),
        worksheets);
  }

  private MasterySummaryData summarizeMasteryRecords(List<MasteryRecord> masteryRecords) {
    Map<Long, List<MasteryRecord>> recordsByStudent = new HashMap<>();
    Map<Long, TopicAggregate> topics = new LinkedHashMap<>();
    BigDecimal totalScore = BigDecimal.ZERO;

    for (MasteryRecord masteryRecord : masteryRecords) {
      Long studentId = masteryRecord.getStudentProfile().getId();
      recordsByStudent.computeIfAbsent(studentId, ignored -> new ArrayList<>()).add(masteryRecord);

      Long topicId = masteryRecord.getSyllabusTopic().getId();
      String topicName = masteryRecord.getSyllabusTopic().getName();
      topics
          .computeIfAbsent(topicId, ignored -> new TopicAggregate(topicId, topicName))
          .add(masteryRecord.getScore());
      totalScore = totalScore.add(masteryRecord.getScore());
    }

    return new MasterySummaryData(recordsByStudent, topics, totalScore);
  }

  private List<ClassDetailResponse.WeakAreaResponse> weakAreas(
      Map<Long, TopicAggregate> topicAggregates) {
    return topicAggregates.values().stream()
        .map(TopicAggregate::response)
        .filter(area -> area.averageScore().compareTo(WEAK_AREA_SCORE_THRESHOLD) < 0)
        .sorted(
            Comparator.comparing(ClassDetailResponse.WeakAreaResponse::affectedStudentCount)
                .reversed()
                .thenComparing(ClassDetailResponse.WeakAreaResponse::averageScore)
                .thenComparing(ClassDetailResponse.WeakAreaResponse::topicName)
                .thenComparing(ClassDetailResponse.WeakAreaResponse::topicId))
        .toList();
  }

  private List<ClassDetailResponse.ScheduleResponse> schedulesFor(TutorClass tutorClass) {
    return tutorClass.getSchedules().stream()
        .map(
            schedule ->
                new ClassDetailResponse.ScheduleResponse(
                    schedule.getDayOfWeek(), schedule.getStartTime(), schedule.getEndTime()))
        .sorted(
            Comparator.comparing(ClassDetailResponse.ScheduleResponse::dayOfWeek)
                .thenComparing(ClassDetailResponse.ScheduleResponse::startTime)
                .thenComparing(ClassDetailResponse.ScheduleResponse::endTime))
        .toList();
  }

  private ClassDetailResponse.InsightResponse insightResponse(long tutorId, long classId) {
    ClassInsightResponse insight = insightService.insights(tutorId, classId);
    return new ClassDetailResponse.InsightResponse(
        ClassDetailResponse.InsightStatus.valueOf(insight.status().name()), insight.message());
  }

  private ClassDetailResponse.StudentResponse studentResponse(
      StudentProfile student, List<MasteryRecord> records) {
    BigDecimal total =
        records.stream().map(MasteryRecord::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new ClassDetailResponse.StudentResponse(
        student.getId(),
        student.getFullName(),
        records.isEmpty() ? null : average(total, records.size()),
        records.size());
  }

  private ClassDetailResponse.WorksheetResponse worksheetResponse(
      Worksheet worksheet, long classId) {
    WorksheetAssignment assignment =
        worksheet.getAssignments().stream()
            .filter(item -> item.getAssignmentType() == Worksheet.AudienceType.CLASS)
            .filter(item -> Long.valueOf(classId).equals(item.getClassId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ClassPersistenceException(
                        new IllegalStateException("Class worksheet assignment was not loaded")));
    return new ClassDetailResponse.WorksheetResponse(
        worksheet.getId(),
        worksheet.getTitle(),
        worksheet.getStatus(),
        assignment.getAssignedAt(),
        assignment.getDueAt());
  }

  private static BigDecimal average(BigDecimal total, int count) {
    return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
  }

  private record MasterySummaryData(
      Map<Long, List<MasteryRecord>> recordsByStudent,
      Map<Long, TopicAggregate> topics,
      BigDecimal totalScore) {}

  private static final class TopicAggregate {
    private final Long topicId;
    private final String topicName;
    private BigDecimal total = BigDecimal.ZERO;
    private int count;
    private int affected;

    private TopicAggregate(Long topicId, String topicName) {
      this.topicId = topicId;
      this.topicName = topicName;
    }

    void add(BigDecimal score) {
      total = total.add(score);
      count++;
      if (score.compareTo(WEAK_AREA_SCORE_THRESHOLD) < 0) {
        affected++;
      }
    }

    ClassDetailResponse.WeakAreaResponse response() {
      return new ClassDetailResponse.WeakAreaResponse(
          topicId, topicName, average(total, count), affected);
    }
  }

  @Transactional
  public ClassRequest.ClassResponse create(long tutorId, ClassRequest request) {
    requireTutor(tutorId);
    validateRequest(request);
    ensureNameAvailable(tutorId, request.className(), null);

    TutorClass tutorClass = new TutorClass();
    tutorClass.setTutorId(tutorId);
    applyRequest(tutorClass, request, true);
    try {
      TutorClass saved = repository.save(tutorClass);
      entityManager.flush();
      return ClassRequest.ClassResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateClassException(request.className(), exception);
    } catch (DataAccessException exception) {
      throw new ClassPersistenceException(exception);
    }
  }

  @Transactional
  public ClassRequest.ClassResponse update(long tutorId, long classId, ClassRequest request) {
    requireTutor(tutorId);
    validateRequest(request);
    TutorClass tutorClass =
        repository
            .findByIdAndTutorId(classId, tutorId)
            .orElseThrow(() -> new ClassNotFoundException(classId));
    ensureNameAvailable(tutorId, request.className(), classId);

    applyRequest(tutorClass, request, false);
    try {
      entityManager.flush();
      return ClassRequest.ClassResponse.from(tutorClass);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateClassException(request.className(), exception);
    } catch (DataAccessException exception) {
      throw new ClassPersistenceException(exception);
    }
  }

  private List<TutorClass> ownedClasses(long tutorId) {
    List<TutorClass> classes = new ArrayList<>();
    classes.addAll(
        repository.findAllByTutorIdAndStatusOrderByClassNameAsc(tutorId, TutorClass.Status.ACTIVE));
    classes.addAll(
        repository.findAllByTutorIdAndStatusOrderByClassNameAsc(
            tutorId, TutorClass.Status.INACTIVE));
    return classes.stream()
        .sorted(
            Comparator.comparing(
                    TutorClass::getClassName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(TutorClass::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }

  private void applyRequest(TutorClass tutorClass, ClassRequest request, boolean creating) {
    tutorClass.setClassName(request.className().trim());
    tutorClass.setSubject(request.subject().trim());
    tutorClass.setLevel(request.level().trim());
    if (request.status() != null) {
      tutorClass.setStatus(request.status());
    } else if (creating) {
      tutorClass.setStatus(TutorClass.Status.ACTIVE);
    }

    Set<TutorClass.ScheduleSlot> schedules = new LinkedHashSet<>();
    for (ClassRequest.ScheduleRequest schedule : request.schedules()) {
      if (schedule == null
          || schedule.dayOfWeek() == null
          || schedule.startTime() == null
          || schedule.endTime() == null) {
        throw new InvalidClassRequestException("Every schedule requires a day and start/end time");
      }
      LocalTime start = schedule.startTime();
      LocalTime end = schedule.endTime();
      if (!start.isBefore(end)) {
        throw new InvalidClassRequestException("Schedule start time must be before end time");
      }
      TutorClass.ScheduleSlot slot = new TutorClass.ScheduleSlot(schedule.dayOfWeek(), start, end);
      if (!schedules.add(slot)) {
        throw new InvalidClassRequestException("Duplicate schedule entries are not allowed");
      }
    }
    tutorClass.setSchedules(schedules);
  }

  private void validateRequest(ClassRequest request) {
    if (request == null) {
      throw new InvalidClassRequestException("Class request is required");
    }
  }

  private void ensureNameAvailable(long tutorId, String name, Long currentClassId) {
    String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    boolean duplicate =
        ownedClasses(tutorId).stream()
            .filter(existing -> currentClassId == null || !currentClassId.equals(existing.getId()))
            .map(TutorClass::getClassName)
            .filter(existing -> existing != null)
            .map(existing -> existing.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    if (duplicate || repository.existsByTutorIdAndClassNameIgnoreCase(tutorId, name.trim())) {
      if (currentClassId == null
          || !ownedClasses(tutorId).stream()
              .filter(existing -> currentClassId.equals(existing.getId()))
              .anyMatch(existing -> existing.getClassName().trim().equalsIgnoreCase(name.trim()))) {
        throw new DuplicateClassException(name);
      }
    }
  }

  private void requireTutor(long tutorId) {
    if (tutorId <= 0) {
      throw new InvalidClassRequestException("Authenticated tutor id must be positive");
    }
  }

  public static class ClassNotFoundException extends RuntimeException {
    public ClassNotFoundException(long classId) {
      super("Class " + classId + " was not found for this tutor");
    }
  }

  public static class ClassAccessDeniedException extends RuntimeException {
    public ClassAccessDeniedException(long classId) {
      super("You are not authorized to access class " + classId);
    }
  }

  public static class DuplicateClassException extends RuntimeException {
    public DuplicateClassException(String className) {
      super("A class named '" + className.trim() + "' already exists for this tutor");
    }

    public DuplicateClassException(String className, Throwable cause) {
      super("A class named '" + className.trim() + "' already exists for this tutor", cause);
    }
  }

  public static class InvalidClassRequestException extends RuntimeException {
    public InvalidClassRequestException(String message) {
      super(message);
    }
  }

  public static class ClassPersistenceException extends RuntimeException {
    public ClassPersistenceException(Throwable cause) {
      super("Class data could not be saved", cause);
    }
  }
}
