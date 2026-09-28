package com.fttranscendence.learning.student;

import com.fttranscendence.learning.alert.TutorAlert;
import com.fttranscendence.learning.alert.TutorAlertRepository;
import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.mastery.MasteryApprovedResultRepository;
import com.fttranscendence.learning.mastery.MasteryHistory;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.report.ProgressReport;
import com.fttranscendence.learning.report.ProgressReportRepository;
import com.fttranscendence.learning.worksheet.Worksheet;
import com.fttranscendence.learning.worksheet.WorksheetAssignment;
import com.fttranscendence.learning.worksheet.WorksheetRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

  private static final BigDecimal STRENGTH_SCORE_THRESHOLD = new BigDecimal("85.00");
  private static final BigDecimal FOCUS_AREA_SCORE_THRESHOLD = new BigDecimal("70.00");

  private final StudentProfileRepository students;
  private final TutorClassRepository classes;
  private final EntityManager entityManager;
  private final MasteryRecordRepository masteryRecords;
  private final MasteryApprovedResultRepository approvedResults;
  private final WorksheetRepository worksheets;
  private final TutorAlertRepository alerts;
  private final ProgressReportRepository reports;
  private final AuthStudentDirectoryClient studentDirectory;

  public StudentService(
      StudentProfileRepository students,
      TutorClassRepository classes,
      EntityManager entityManager,
      MasteryRecordRepository masteryRecords,
      MasteryApprovedResultRepository approvedResults,
      WorksheetRepository worksheets,
      TutorAlertRepository alerts,
      ProgressReportRepository reports,
      AuthStudentDirectoryClient studentDirectory) {
    this.students = students;
    this.classes = classes;
    this.entityManager = entityManager;
    this.masteryRecords = masteryRecords;
    this.approvedResults = approvedResults;
    this.worksheets = worksheets;
    this.alerts = alerts;
    this.reports = reports;
    this.studentDirectory = studentDirectory;
  }

  /**
   * Returns only existing auth-service Student accounts that this Tutor may enrol. Accounts linked
   * to another Tutor are deliberately omitted rather than disclosed; already-enrolled accounts are
   * omitted as well.
   */
  @Transactional(readOnly = true)
  public List<ClassStudentResponse.EligibleStudentResponse> listEligibleClassStudents(
      long tutorId, long classId, String bearerToken) {
    requireTutor(tutorId);
    requireOwnedClass(tutorId, classId);
    List<AuthStudentDirectoryClient.StudentAccount> studentAccounts =
        studentDirectory.listStudents(bearerToken);
    if (studentAccounts.isEmpty()) {
      return List.of();
    }

    Map<Long, StudentProfile> profilesByLogin = profilesByLoginUserId(studentAccounts);

    return studentAccounts.stream()
        .filter(account -> isEligibleForClass(profilesByLogin.get(account.id()), tutorId, classId))
        .map(ClassStudentResponse.EligibleStudentResponse::from)
        .toList();
  }

  /**
   * Searchable account directory for creating a Tutor-owned student profile. Auth-service filters
   * by the STUDENT role; profiles already claimed by any Tutor are withheld to prevent duplicate or
   * cross-Tutor relationships.
   */
  @Transactional(readOnly = true)
  public List<StudentAccountResponse> listAvailableStudentAccounts(
      long tutorId, String bearerToken, String search) {
    requireTutor(tutorId);
    List<AuthStudentDirectoryClient.StudentAccount> studentAccounts =
        studentDirectory.listStudents(bearerToken, search);
    if (studentAccounts.isEmpty()) {
      return List.of();
    }

    Map<Long, StudentProfile> profilesByLogin = profilesByLoginUserId(studentAccounts);
    String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

    return studentAccounts.stream()
        .filter(account -> isAvailableForTutor(profilesByLogin.get(account.id())))
        // Keep the learning API search behaviour correct even if an older
        // auth-service instance has not yet applied its query filter.
        .filter(
            account ->
                normalizedSearch.isEmpty()
                    || account.fullName().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    || account.email().toLowerCase(Locale.ROOT).contains(normalizedSearch))
        .map(StudentAccountResponse::from)
        .toList();
  }

  private Map<Long, StudentProfile> profilesByLoginUserId(
      List<AuthStudentDirectoryClient.StudentAccount> studentAccounts) {
    List<Long> loginUserIds =
        studentAccounts.stream().map(AuthStudentDirectoryClient.StudentAccount::id).toList();

    return students.findAllByLoginUserIdInWithMemberships(loginUserIds).stream()
        .collect(Collectors.toMap(StudentProfile::getLoginUserId, profile -> profile));
  }

  private boolean isAvailableForTutor(StudentProfile profile) {
    return profile == null || profile.getTutorId() == null;
  }

  @Transactional(readOnly = true)
  public List<ClassStudentResponse.ClassMemberResponse> listClassMembers(
      long tutorId, long classId) {
    requireTutor(tutorId);
    requireOwnedClass(tutorId, classId);
    return students.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId).stream()
        .map(ClassStudentResponse.ClassMemberResponse::from)
        .toList();
  }

  /** Adds an existing auth-service Student login to an owned class. */
  @Transactional
  public ClassStudentResponse.ClassMemberResponse addExistingStudentToClass(
      long tutorId, long classId, ClassStudentRequest request, String bearerToken) {
    requireTutor(tutorId);
    if (request == null || request.loginUserId() == null || request.loginUserId() <= 0) {
      throw new InvalidStudentRequestException("An existing Student login is required");
    }
    requireOwnedClass(tutorId, classId);
    AuthStudentDirectoryClient.StudentAccount studentAccount =
        requireStudentAccount(request.loginUserId(), bearerToken);
    StudentProfile profile = students.findByLoginUserId(studentAccount.id()).orElse(null);
    boolean claimingUnassignedProfile = profile != null && profile.getTutorId() == null;
    boolean belongsToAnotherTutor =
        profile != null
            && profile.getTutorId() != null
            && !Long.valueOf(tutorId).equals(profile.getTutorId());
    if (belongsToAnotherTutor) {
      // Do not let a Tutor claim or enumerate another Tutor's learner.
      throw new StudentNotFoundException(profile.getId());
    }
    if (profile == null) {
      profile = new StudentProfile();
      profile.setLoginUserId(studentAccount.id());
      profile.setFullName(studentAccount.fullName());
      profile.setTutorId(tutorId);
    } else {
      profile.setTutorId(tutorId);
      profile.setFullName(studentAccount.fullName());
    }
    if (!isEligibleForClass(profile, tutorId, classId)) {
      throw new DuplicateClassMembershipException(classId);
    }
    try {
      // class_memberships has a composite FK (student_profile_id, tutor_id).
      // An auto-provisioned profile has no tutor yet, so persist that owner
      // transition before inserting its first membership.
      if (claimingUnassignedProfile) {
        entityManager.flush();
      }
      profile.addClassMembership(classId);
      StudentProfile saved = students.save(profile);
      entityManager.flush();
      return ClassStudentResponse.ClassMemberResponse.from(saved);
    } catch (DataIntegrityViolationException exception) {
      throw new DuplicateClassMembershipException(classId, exception);
    } catch (DataAccessException exception) {
      throw new StudentPersistenceException(exception);
    }
  }

  @Transactional
  public void removeStudentFromClass(long tutorId, long classId, long studentId) {
    requireTutor(tutorId);
    requireOwnedClass(tutorId, classId);
    StudentProfile profile = requireOwnedStudent(tutorId, studentId);
    ClassMembership membership =
        profile.getMemberships().stream()
            .filter(item -> Long.valueOf(classId).equals(item.getClassId()))
            .findFirst()
            .orElseThrow(() -> new StudentNotFoundException(studentId));
    profile.removeClassMembership(membership);
    try {
      entityManager.flush();
    } catch (DataAccessException exception) {
      throw new StudentPersistenceException(exception);
    }
  }

  @Transactional(readOnly = true)
  public List<StudentRequest.StudentResponse> listOwnedStudents(long tutorId, Long classId) {
    requireTutor(tutorId);
    if (classId != null) {
      requireOwnedClass(tutorId, classId);
    }
    List<StudentProfile> profiles =
        classId == null
            ? students.findAllByTutorIdOrderByFullNameAsc(tutorId)
            : students.findAllByTutorIdAndClassIdOrderByFullNameAsc(tutorId, classId);
    Map<Long, TutorClass> classesById = ownedClassMap(tutorId);
    return profiles.stream()
        .map(profile -> StudentRequest.StudentResponse.from(profile, classesById))
        .toList();
  }

  @Transactional(readOnly = true)
  public StudentRequest.StudentResponse getOwnedStudent(long tutorId, long studentId) {
    requireTutor(tutorId);
    StudentProfile student = requireOwnedStudent(tutorId, studentId);
    return StudentRequest.StudentResponse.from(student, ownedClassMap(tutorId));
  }

  @Transactional(readOnly = true)
  public StudentProfileResponse getOwnedStudentProfile(long tutorId, long studentId) {
    requireTutor(tutorId);
    StudentProfile student =
        students.findByIdAndTutorId(studentId, tutorId).orElseThrow(ProfileNotFoundException::new);
    return profileResponse(student, true);
  }

  @Transactional(readOnly = true)
  public StudentProfileResponse getLinkedStudentProfile(long loginUserId) {
    if (loginUserId <= 0) {
      throw new ProfileNotFoundException();
    }
    StudentProfile student =
        students.findByLoginUserId(loginUserId).orElseThrow(ProfileNotFoundException::new);
    return profileResponse(student, false);
  }

  @Transactional
  public StudentRequest.StudentResponse create(
      long tutorId, StudentRequest request, String bearerToken) {
    requireTutor(tutorId);
    validateRequest(request);
    if (request.loginUserId() == null) {
      throw new InvalidStudentRequestException("Select an existing Student account.");
    }
    AuthStudentDirectoryClient.StudentAccount studentAccount =
        requireStudentAccount(request.loginUserId(), bearerToken);
    Map<Long, TutorClass> requestedClasses = resolveRequestedClasses(tutorId, request.classIds());
    StudentProfile student = students.findByLoginUserId(studentAccount.id()).orElse(null);
    if (student != null && student.getTutorId() != null) {
      throw new LoginIdentityConflictException(studentAccount.id());
    }
    if (student == null) {
      student = new StudentProfile();
    }
    student.setTutorId(tutorId);
    StudentRequest verifiedStudentRequest =
        new StudentRequest(studentAccount.fullName(), studentAccount.id(), request.classIds());
    // Full name and login identity are always re-derived from the Student
    // account selected in auth-service, never trusted from browser input.
    apply(student, verifiedStudentRequest, requestedClasses);
    try {
      StudentProfile saved = students.save(student);
      entityManager.flush();
      return StudentRequest.StudentResponse.from(saved, requestedClasses);
    } catch (DataIntegrityViolationException exception) {
      throw conflictFor(request, exception);
    } catch (DataAccessException exception) {
      throw new StudentPersistenceException(exception);
    }
  }

  @Transactional
  public StudentRequest.StudentResponse update(
      long tutorId, long studentId, StudentRequest request) {
    requireTutor(tutorId);
    validateRequest(request);
    StudentProfile student = requireOwnedStudent(tutorId, studentId);
    Map<Long, TutorClass> requestedClasses = resolveRequestedClasses(tutorId, request.classIds());
    ensureLoginIdentityAvailable(request.loginUserId(), studentId);

    apply(student, request, requestedClasses);
    try {
      entityManager.flush();
      return StudentRequest.StudentResponse.from(student, requestedClasses);
    } catch (DataIntegrityViolationException exception) {
      throw conflictFor(request, exception);
    } catch (DataAccessException exception) {
      throw new StudentPersistenceException(exception);
    }
  }

  private void apply(
      StudentProfile student, StudentRequest request, Map<Long, TutorClass> requestedClasses) {
    student.setFullName(request.fullName().trim());
    student.setLoginUserId(request.loginUserId());

    updateClassMemberships(student, requestedClasses.keySet());
  }

  private void updateClassMemberships(StudentProfile student, Set<Long> requestedClassIds) {
    List<ClassMembership> existingMemberships = new ArrayList<>(student.getMemberships());
    existingMemberships.stream()
        .filter(membership -> !requestedClassIds.contains(membership.getClassId()))
        .forEach(student::removeClassMembership);

    Set<Long> currentClassIds =
        student.getMemberships().stream()
            .map(ClassMembership::getClassId)
            .collect(Collectors.toSet());
    requestedClassIds.stream()
        .filter(classId -> !currentClassIds.contains(classId))
        .sorted()
        .forEach(student::addClassMembership);
  }

  private Map<Long, TutorClass> resolveRequestedClasses(long tutorId, List<Long> classIds) {
    Set<Long> requestedClassIds = new HashSet<>();
    for (Long classId : classIds) {
      if (!requestedClassIds.add(classId)) {
        throw new DuplicateMembershipException(classId);
      }
    }

    Map<Long, TutorClass> ownedClasses = ownedClassMap(tutorId);
    for (Long classId : requestedClassIds) {
      if (!ownedClasses.containsKey(classId)) {
        throw new ClassNotFoundException(classId);
      }
    }

    return ownedClasses.entrySet().stream()
        .filter(entry -> requestedClassIds.contains(entry.getKey()))
        .sorted(Map.Entry.comparingByKey())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
  }

  private Map<Long, TutorClass> ownedClassMap(Long tutorId) {
    if (tutorId == null || tutorId <= 0) {
      return Map.of();
    }
    Map<Long, TutorClass> classesById = new HashMap<>();
    for (TutorClass tutorClass : classes.findAllByTutorIdOrderByClassNameAsc(tutorId)) {
      classesById.put(tutorClass.getId(), tutorClass);
    }
    return classesById;
  }

  private StudentProfileResponse profileResponse(StudentProfile student, boolean includeTutorOnly) {
    List<MasteryRecord> records =
        masteryRecords.findProfileRecordsByStudentProfileIdWithTopicAndHistory(student.getId());
    List<StudentProfileResponse.ClassSummary> classResponses = classSummaries(student);
    StudentProfileResponse.Metrics metrics = calculateMetrics(records);
    List<StudentProfileResponse.MasteryTopic> masteryTopics = masteryTopics(records);
    StudentProfileResponse.LearningProfile learningProfile = learningProfile(records);
    List<StudentProfileResponse.HistoryItem> recentHistory = recentHistory(records);
    List<Long> classIds =
        classResponses.stream().map(StudentProfileResponse.ClassSummary::id).toList();
    List<StudentProfileResponse.WorksheetAssignmentSummary> worksheetAssignments =
        effectiveWorksheets(student, classIds);
    StudentProfileResponse.TutorOnly tutorOnly = includeTutorOnly ? tutorOnly(student) : null;

    return new StudentProfileResponse(
        student.getId(),
        student.getFullName(),
        classResponses,
        metrics,
        masteryTopics,
        learningProfile,
        recentHistory,
        worksheetAssignments,
        tutorOnly);
  }

  private List<StudentProfileResponse.ClassSummary> classSummaries(StudentProfile student) {
    Map<Long, TutorClass> classesById = ownedClassMap(student.getTutorId());

    return student.getMemberships().stream()
        .map(ClassMembership::getClassId)
        .map(classesById::get)
        .filter(Objects::nonNull)
        .map(this::classSummary)
        .sorted(classSummaryComparator())
        .toList();
  }

  private StudentProfileResponse.ClassSummary classSummary(TutorClass tutorClass) {
    return new StudentProfileResponse.ClassSummary(
        tutorClass.getId(),
        tutorClass.getClassName(),
        tutorClass.getSubject(),
        tutorClass.getLevel(),
        tutorClass.getStatus());
  }

  private Comparator<StudentProfileResponse.ClassSummary> classSummaryComparator() {
    return Comparator.comparing(
            StudentProfileResponse.ClassSummary::className,
            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
        .thenComparing(StudentProfileResponse.ClassSummary::id);
  }

  private StudentProfileResponse.Metrics calculateMetrics(List<MasteryRecord> records) {
    BigDecimal totalScore =
        records.stream().map(MasteryRecord::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    int totalAttempts = records.stream().mapToInt(MasteryRecord::getAttemptCount).sum();
    LocalDateTime lastCalculatedAt =
        records.stream()
            .map(MasteryRecord::getCalculatedAt)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);

    BigDecimal averageScore = records.isEmpty() ? null : average(totalScore, records.size());
    return new StudentProfileResponse.Metrics(
        averageScore, records.size(), totalAttempts, lastCalculatedAt);
  }

  private List<StudentProfileResponse.MasteryTopic> masteryTopics(List<MasteryRecord> records) {
    return records.stream()
        .map(
            record ->
                new StudentProfileResponse.MasteryTopic(
                    record.getSyllabusTopic().getId(),
                    record.getSyllabusTopic().getCode(),
                    record.getSyllabusTopic().getName(),
                    record.getScore(),
                    record.getMasteryStatus(),
                    record.getAttemptCount(),
                    record.getCalculatedAt()))
        .toList();
  }

  private StudentProfileResponse.LearningProfile learningProfile(List<MasteryRecord> records) {
    List<StudentProfileResponse.TopicSummary> strengths =
        records.stream()
            .filter(record -> record.getScore().compareTo(STRENGTH_SCORE_THRESHOLD) >= 0)
            .map(this::topicSummary)
            .sorted(
                Comparator.comparing(StudentProfileResponse.TopicSummary::score)
                    .reversed()
                    .thenComparing(StudentProfileResponse.TopicSummary::topicName)
                    .thenComparing(StudentProfileResponse.TopicSummary::topicId))
            .toList();
    List<StudentProfileResponse.TopicSummary> focusAreas =
        records.stream()
            .filter(record -> isFocusArea(record))
            .map(this::topicSummary)
            .sorted(
                Comparator.comparing(StudentProfileResponse.TopicSummary::score)
                    .thenComparing(StudentProfileResponse.TopicSummary::topicName)
                    .thenComparing(StudentProfileResponse.TopicSummary::topicId))
            .toList();

    return new StudentProfileResponse.LearningProfile(strengths, focusAreas);
  }

  private boolean isFocusArea(MasteryRecord record) {
    boolean hasBelowTargetScore = record.getScore().compareTo(FOCUS_AREA_SCORE_THRESHOLD) < 0;
    boolean needsRevision = record.getMasteryStatus() == MasteryRecord.MasteryStatus.NEEDS_REVISION;
    return hasBelowTargetScore || needsRevision;
  }

  private List<StudentProfileResponse.HistoryItem> recentHistory(List<MasteryRecord> records) {
    return records.stream()
        .flatMap(record -> record.getHistory().stream().map(item -> historyItem(record, item)))
        .sorted(
            Comparator.comparing(
                    StudentProfileResponse.HistoryItem::occurredAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StudentProfileResponse.HistoryItem::topicId))
        .limit(50)
        .toList();
  }

  private List<StudentProfileResponse.WorksheetAssignmentSummary> effectiveWorksheets(
      StudentProfile student, List<Long> classIds) {
    List<StudentProfileResponse.WorksheetAssignmentSummary> assignmentSummaries = new ArrayList<>();
    addStudentWorksheetAssignments(student, assignmentSummaries);
    addClassWorksheetAssignments(student, classIds, assignmentSummaries);

    return assignmentSummaries.stream()
        .sorted(
            Comparator.comparing(
                    StudentProfileResponse.WorksheetAssignmentSummary::assignedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StudentProfileResponse.WorksheetAssignmentSummary::worksheetId))
        .toList();
  }

  private void addStudentWorksheetAssignments(
      StudentProfile student,
      List<StudentProfileResponse.WorksheetAssignmentSummary> assignmentSummaries) {
    List<Worksheet> studentWorksheets =
        worksheets.findApprovedStudentAssignedWorksheetsByTutorId(
            student.getTutorId(), student.getId());
    for (Worksheet worksheet : studentWorksheets) {
      worksheet.getAssignments().stream()
          .filter(assignment -> assignment.getAssignmentType() == Worksheet.AudienceType.STUDENT)
          .filter(assignment -> student.getId().equals(assignment.getStudentProfileId()))
          .forEach(assignment -> assignmentSummaries.add(assignmentSummary(worksheet, assignment)));
    }
  }

  private void addClassWorksheetAssignments(
      StudentProfile student,
      List<Long> classIds,
      List<StudentProfileResponse.WorksheetAssignmentSummary> assignmentSummaries) {
    for (Long classId : classIds) {
      List<Worksheet> classWorksheets =
          worksheets.findClassAssignedWorksheetsByTutorId(student.getTutorId(), classId);
      for (Worksheet worksheet : classWorksheets) {
        if (worksheet.getStatus() != Worksheet.Status.APPROVED) {
          continue;
        }
        worksheet.getAssignments().stream()
            .filter(assignment -> assignment.getAssignmentType() == Worksheet.AudienceType.CLASS)
            .filter(assignment -> classId.equals(assignment.getClassId()))
            .forEach(
                assignment -> assignmentSummaries.add(assignmentSummary(worksheet, assignment)));
      }
    }
  }

  private StudentProfileResponse.WorksheetAssignmentSummary assignmentSummary(
      Worksheet worksheet, WorksheetAssignment assignment) {
    return new StudentProfileResponse.WorksheetAssignmentSummary(
        worksheet.getId(),
        worksheet.getTitle(),
        assignment.getAssignmentType(),
        assignment.getClassId(),
        assignment.getAssignedAt(),
        assignment.getDueAt());
  }

  private StudentProfileResponse.TutorOnly tutorOnly(StudentProfile student) {
    List<TutorAlert.AlertStatus> activeStatuses =
        List.of(TutorAlert.AlertStatus.OPEN, TutorAlert.AlertStatus.ACKNOWLEDGED);
    List<StudentProfileResponse.AlertSummary> activeAlerts =
        alerts
            .findActiveByTutorIdAndStudentProfileId(
                student.getTutorId(), student.getId(), activeStatuses)
            .stream()
            .map(
                alert ->
                    new StudentProfileResponse.AlertSummary(
                        alert.getId(),
                        alert.getAlertType().name(),
                        alert.getSeverity().name(),
                        alert.getAlertStatus().name(),
                        alert.getTitle(),
                        alert.getCreatedAt()))
            .toList();
    List<StudentProfileResponse.ReportMetadata> reportMetadata =
        reports.findAllByStudentProfileIdOrderByPeriodEndDesc(student.getId()).stream()
            .filter(report -> student.getTutorId().equals(report.getTutorId()))
            .map(this::reportMetadata)
            .toList();
    long approvedWorksheetCount =
        approvedResults.countDistinctActiveWorksheetIdsByTutorAndStudent(
            student.getTutorId(), student.getId());
    return new StudentProfileResponse.TutorOnly(
        activeAlerts, reportMetadata, approvedWorksheetCount);
  }

  private StudentProfileResponse.TopicSummary topicSummary(MasteryRecord record) {
    return new StudentProfileResponse.TopicSummary(
        record.getSyllabusTopic().getId(),
        record.getSyllabusTopic().getName(),
        record.getScore(),
        record.getMasteryStatus());
  }

  private StudentProfileResponse.ReportMetadata reportMetadata(ProgressReport report) {
    return new StudentProfileResponse.ReportMetadata(
        report.getId(),
        report.getReportCode(),
        report.getReportStatus(),
        report.getPeriodStart(),
        report.getPeriodEnd(),
        report.getGeneratedAt(),
        report.getFinalizedAt());
  }

  private StudentProfileResponse.HistoryItem historyItem(
      MasteryRecord record, MasteryHistory history) {
    return new StudentProfileResponse.HistoryItem(
        record.getSyllabusTopic().getId(),
        record.getSyllabusTopic().getName(),
        history.getPreviousScore(),
        history.getNewScore(),
        history.getPreviousStatus(),
        history.getNewStatus(),
        history.getReason(),
        history.getCreatedAt());
  }

  private static BigDecimal average(BigDecimal total, int count) {
    return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
  }

  private void ensureLoginIdentityAvailable(Long loginUserId, Long currentStudentId) {
    if (loginUserId == null) {
      return;
    }
    students
        .findByLoginUserId(loginUserId)
        .filter(existing -> currentStudentId == null || !currentStudentId.equals(existing.getId()))
        .ifPresent(
            existing -> {
              throw new LoginIdentityConflictException(loginUserId);
            });
  }

  private AuthStudentDirectoryClient.StudentAccount requireStudentAccount(
      long loginUserId, String bearerToken) {
    return studentDirectory.listStudents(bearerToken).stream()
        .filter(account -> account.id() == loginUserId)
        .findFirst()
        .orElseThrow(() -> new StudentNotFoundException(loginUserId));
  }

  private boolean isEligibleForClass(StudentProfile profile, long tutorId, long classId) {
    if (profile == null) {
      return true;
    }
    if (profile.getTutorId() != null && !Long.valueOf(tutorId).equals(profile.getTutorId())) {
      return false;
    }
    return profile.getMemberships().stream()
        .noneMatch(membership -> Long.valueOf(classId).equals(membership.getClassId()));
  }

  private StudentProfile requireOwnedStudent(long tutorId, long studentId) {
    return students
        .findByIdAndTutorId(studentId, tutorId)
        .orElseThrow(() -> new StudentNotFoundException(studentId));
  }

  private void requireOwnedClass(long tutorId, long classId) {
    if (!classes.findByIdAndTutorId(classId, tutorId).isPresent()) {
      throw new ClassNotFoundException(classId);
    }
  }

  private void validateRequest(StudentRequest request) {
    if (request == null) {
      throw new InvalidStudentRequestException("Student request is required");
    }
  }

  private void requireTutor(long tutorId) {
    if (tutorId <= 0) {
      throw new InvalidStudentRequestException("Authenticated tutor id must be positive");
    }
  }

  private RuntimeException conflictFor(
      StudentRequest request, DataIntegrityViolationException exception) {
    if (request.loginUserId() != null) {
      return new LoginIdentityConflictException(request.loginUserId(), exception);
    }
    return new StudentPersistenceException(exception);
  }

  public static class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException(long studentId) {
      super("Student " + studentId + " was not found for this tutor");
    }
  }

  /** Same response for a missing, foreign, or unlinked profile. */
  public static class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
      super("Student profile was not found");
    }
  }

  public static class ClassNotFoundException extends RuntimeException {
    public ClassNotFoundException(long classId) {
      super("Class " + classId + " was not found for this tutor");
    }
  }

  public static class DuplicateMembershipException extends RuntimeException {
    public DuplicateMembershipException(long classId) {
      super("Class " + classId + " appears more than once in this student request");
    }
  }

  public static class DuplicateClassMembershipException extends RuntimeException {
    public DuplicateClassMembershipException(long classId) {
      super("Student is already enrolled in class " + classId);
    }

    public DuplicateClassMembershipException(long classId, Throwable cause) {
      super("Student is already enrolled in class " + classId, cause);
    }
  }

  public static class StudentDirectoryUnavailableException extends RuntimeException {
    public StudentDirectoryUnavailableException(Throwable cause) {
      super("Student account directory is temporarily unavailable", cause);
    }
  }

  public static class LoginIdentityConflictException extends RuntimeException {
    public LoginIdentityConflictException(long loginUserId) {
      super("Login identity " + loginUserId + " is already linked to another student");
    }

    public LoginIdentityConflictException(long loginUserId, Throwable cause) {
      super("Login identity " + loginUserId + " is already linked to another student", cause);
    }
  }

  public static class InvalidStudentRequestException extends RuntimeException {
    public InvalidStudentRequestException(String message) {
      super(message);
    }
  }

  public static class StudentPersistenceException extends RuntimeException {
    public StudentPersistenceException(Throwable cause) {
      super("Student data could not be saved", cause);
    }
  }
}
