package com.fttranscendence.learning.worksheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.question.MarkingComponent;
import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.question.QuestionRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorksheetRepositoryTest {

  private static final long TUTOR_ID = 101L;

  @Autowired private WorksheetRepository repository;
  @Autowired private QuestionRepository questionRepository;
  @Autowired private SyllabusTopicRepository syllabusRepository;
  @Autowired private TutorClassRepository classRepository;
  @Autowired private StudentProfileRepository studentRepository;
  @Autowired private WorksheetService worksheetService;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Validator validator;

  @BeforeEach
  void clearWorksheetData() {
    jdbcTemplate.update("DELETE FROM worksheet_assignments");
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM worksheets");
    jdbcTemplate.update("DELETE FROM question_keywords");
    jdbcTemplate.update("DELETE FROM marking_components");
    jdbcTemplate.update("DELETE FROM questions");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void persistsDraftAndTransitionsToAnApprovedImmutableWorksheet() {
    Question first = persistQuestion("SCI-WS-STATE-001");
    Question second = persistQuestion("SCI-WS-STATE-002");
    Worksheet worksheet = worksheet(" class-water-001 ", Worksheet.AudienceType.CLASS);
    worksheet.addQuestion(first);

    repository.save(worksheet);
    entityManager.flush();

    assertEquals("CLASS-WATER-001", worksheet.getCode());
    assertEquals(Worksheet.Status.DRAFT, worksheet.getStatus());
    assertNull(worksheet.getApprovedAt());

    worksheet.approve();
    entityManager.flush();
    entityManager.clear();

    Worksheet approved = repository.findByIdAndTutorId(worksheet.getId(), TUTOR_ID).orElseThrow();
    assertEquals(Worksheet.Status.APPROVED, approved.getStatus());
    assertNotNull(approved.getApprovedAt());
    assertThrows(IllegalStateException.class, () -> approved.addQuestion(second));
    assertTrue(validator.validate(approved).isEmpty());
  }

  @Test
  void keepsQuestionOrderStableAndPersistsDraftReordering() {
    Question first = persistQuestion("SCI-WS-ORDER-001");
    Question second = persistQuestion("SCI-WS-ORDER-002");
    Question third = persistQuestion("SCI-WS-ORDER-003");
    Worksheet worksheet = worksheet("CLASS-ORDER-001", Worksheet.AudienceType.CLASS);
    worksheet.addQuestion(first);
    worksheet.addQuestion(second);
    worksheet.addQuestion(third);
    repository.save(worksheet);
    entityManager.flush();

    worksheetService.updateWorksheet(
        TUTOR_ID,
        worksheet.getId(),
        new WorksheetRequests.UpdateWorksheetRequest(
            null, null, List.of(third.getId(), first.getId(), second.getId())));
    entityManager.clear();

    Worksheet reordered = repository.findByIdAndTutorId(worksheet.getId(), TUTOR_ID).orElseThrow();
    assertEquals(
        List.of("SCI-WS-ORDER-003", "SCI-WS-ORDER-001", "SCI-WS-ORDER-002"),
        questionCodes(reordered));
    assertEquals(
        List.of(0, 1, 2),
        reordered.getQuestions().stream().map(WorksheetQuestion::getPosition).toList());
  }

  @Test
  void persistsAssessmentMetadataAndQuestionSnapshots() {
    Question source = persistQuestion("SCI-WS-SNAPSHOT-001");
    Worksheet worksheet = worksheet("DIAGNOSTIC-SNAPSHOT-001", Worksheet.AudienceType.CLASS);
    worksheet.setWorksheetType(Worksheet.WorksheetType.DIAGNOSTIC);
    worksheet.setSubject("  Science  ");
    worksheet.addQuestion(source);
    repository.save(worksheet);
    entityManager.flush();

    source.setCode("SCI-WS-SNAPSHOT-CHANGED");
    source.setPrompt("This later bank edit must not change the worksheet.");
    source.setQuestionType(Question.QuestionType.CALCULATION);
    source.setTotalMarks(BigDecimal.valueOf(2));
    source.replaceMarkingComponents(
        List.of(new MarkingComponent("Updated criterion", BigDecimal.valueOf(2))));
    entityManager.flush();
    entityManager.clear();

    Worksheet loaded = repository.findByIdAndTutorId(worksheet.getId(), TUTOR_ID).orElseThrow();
    WorksheetQuestion captured = loaded.getQuestions().get(0);
    assertEquals(Worksheet.WorksheetType.DIAGNOSTIC, loaded.getWorksheetType());
    assertEquals("Science", loaded.getSubject());
    assertEquals("SCI-WS-SNAPSHOT-001", captured.getQuestionCodeSnapshot());
    assertEquals(
        "Explain the science concept for SCI-WS-SNAPSHOT-001.", captured.getPromptSnapshot());
    assertEquals(Question.QuestionType.OPEN_ENDED, captured.getQuestionTypeSnapshot());
    assertEquals(0, BigDecimal.ONE.compareTo(captured.getTotalMarksSnapshot()));
  }

  @Test
  void databaseRejectsTwoQuestionsAtTheSameWorksheetPosition() {
    Question first = persistQuestion("SCI-WS-POSITION-001");
    Question second = persistQuestion("SCI-WS-POSITION-002");
    Worksheet worksheet = worksheet("POSITION-UNIQUE-001", Worksheet.AudienceType.CLASS);
    worksheet.addQuestion(first);
    worksheet.addQuestion(second);
    repository.save(worksheet);
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "UPDATE worksheet_questions SET position = 0 WHERE worksheet_id = ? AND question_id = ?",
                worksheet.getId(),
                second.getId()));
  }

  @Test
  void assignsAnApprovedClassWorksheetToAnOwnedClass() {
    TutorClass tutorClass = persistClass(TUTOR_ID, "P5 Science A");
    Worksheet worksheet =
        approvedWorksheet("CLASS-ASSIGN-001", Worksheet.AudienceType.CLASS, "SCI-WS-CLASS-001");
    LocalDateTime dueAt = LocalDateTime.now().plusDays(7).withNano(0);
    worksheet.assignToClass(tutorClass.getId(), dueAt);
    repository.save(worksheet);
    entityManager.flush();
    entityManager.clear();

    Worksheet loaded =
        repository.findAssignedWorksheets(Worksheet.AudienceType.CLASS, tutorClass.getId()).get(0);
    WorksheetAssignment assignment = loaded.getAssignments().get(0);

    assertEquals(Worksheet.AudienceType.CLASS, loaded.getAudienceType());
    assertEquals(tutorClass.getId(), assignment.getClassId());
    assertNull(assignment.getStudentProfileId());
    assertEquals(TUTOR_ID, assignment.getTutorId());
    assertEquals(dueAt, assignment.getDueAt());
  }

  @Test
  void assignsAnApprovedPersonalWorksheetToTheCanonicalStudentProfile() {
    StudentProfile student = persistStudent(TUTOR_ID, "Bella Tan");
    Worksheet worksheet =
        approvedWorksheet(
            "STUDENT-ASSIGN-001", Worksheet.AudienceType.STUDENT, "SCI-WS-STUDENT-001");
    worksheet.assignToStudent(student.getId(), LocalDateTime.now().plusDays(3));
    repository.save(worksheet);
    entityManager.flush();
    entityManager.clear();

    Worksheet loaded =
        repository.findAssignedWorksheets(Worksheet.AudienceType.STUDENT, student.getId()).get(0);
    WorksheetAssignment assignment = loaded.getAssignments().get(0);

    assertEquals(Worksheet.AudienceType.STUDENT, loaded.getAudienceType());
    assertEquals(student.getId(), assignment.getStudentProfileId());
    assertNull(assignment.getClassId());
    assertEquals(TUTOR_ID, assignment.getTutorId());
  }

  @Test
  void rejectsMismatchedAudienceDuplicateQuestionsAndDuplicateAssignments() {
    Question question = persistQuestion("SCI-WS-DUPLICATE-001");
    TutorClass tutorClass = persistClass(TUTOR_ID, "P5 Science B");
    Worksheet worksheet = worksheet("CLASS-DUPLICATE-001", Worksheet.AudienceType.CLASS);
    worksheet.addQuestion(question);

    assertThrows(IllegalArgumentException.class, () -> worksheet.addQuestion(question));

    worksheet.approve();
    worksheet.assignToClass(tutorClass.getId(), null);

    assertThrows(
        IllegalArgumentException.class, () -> worksheet.assignToClass(tutorClass.getId(), null));
    assertThrows(IllegalStateException.class, () -> worksheet.assignToStudent(999L, null));
  }

  @Test
  void rejectsDuplicateWorksheetCodesForTheSameTutorAfterCanonicalisation() {
    Question question = persistQuestion("SCI-WS-CODE-001");
    Worksheet first = worksheet("WS-CODE-001", Worksheet.AudienceType.CLASS);
    first.addQuestion(question);
    repository.save(first);
    entityManager.flush();

    Worksheet duplicate = worksheet("  ws-code-001  ", Worksheet.AudienceType.STUDENT);
    duplicate.addQuestion(question);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          repository.save(duplicate);
          entityManager.flush();
        });
  }

  @Test
  void databaseRejectsCrossTutorClassAssignments() {
    TutorClass otherTutorClass = persistClass(202L, "Other Tutor Class");
    Worksheet worksheet =
        approvedWorksheet("CLASS-OWNER-001", Worksheet.AudienceType.CLASS, "SCI-WS-OWNER-001");
    worksheet.assignToClass(otherTutorClass.getId(), null);

    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          repository.save(worksheet);
          entityManager.flush();
        });
  }

  @Test
  void protectsBankQuestionsAndAssignedClassesFromDeletion() {
    Question question = persistQuestion("SCI-WS-DELETE-001");
    TutorClass tutorClass = persistClass(TUTOR_ID, "Protected Class");
    Worksheet worksheet = worksheet("CLASS-DELETE-001", Worksheet.AudienceType.CLASS);
    worksheet.addQuestion(question);
    worksheet.approve();
    worksheet.assignToClass(tutorClass.getId(), null);
    repository.save(worksheet);
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM questions WHERE id = ?", question.getId()));
  }

  @Test
  void protectsClassesThatHaveWorksheetAssignments() {
    TutorClass tutorClass = persistClass(TUTOR_ID, "Assigned Class");
    Worksheet worksheet =
        approvedWorksheet("CLASS-DELETE-002", Worksheet.AudienceType.CLASS, "SCI-WS-DELETE-003");
    worksheet.assignToClass(tutorClass.getId(), null);
    repository.save(worksheet);
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM tutor_classes WHERE id = ?", tutorClass.getId()));
  }

  @Test
  void protectsAssignedStudentsAndWorksheetsFromDeletion() {
    StudentProfile student = persistStudent(TUTOR_ID, "Protected Student");
    Worksheet worksheet =
        approvedWorksheet(
            "STUDENT-DELETE-001", Worksheet.AudienceType.STUDENT, "SCI-WS-DELETE-002");
    worksheet.assignToStudent(student.getId(), null);
    repository.save(worksheet);
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM student_profiles WHERE id = ?", student.getId()));
  }

  @Test
  void protectsWorksheetsThatHaveBeenAssigned() {
    TutorClass tutorClass = persistClass(TUTOR_ID, "Worksheet Delete Boundary");
    Worksheet worksheet =
        approvedWorksheet("CLASS-DELETE-003", Worksheet.AudienceType.CLASS, "SCI-WS-DELETE-004");
    worksheet.assignToClass(tutorClass.getId(), null);
    repository.save(worksheet);
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM worksheets WHERE id = ?", worksheet.getId()));
  }

  @Test
  void repositoryExposesNoHardDeleteOperation() {
    assertFalse(
        Arrays.stream(WorksheetRepository.class.getMethods())
            .anyMatch(method -> method.getName().startsWith("delete")));
  }

  private Worksheet approvedWorksheet(
      String code, Worksheet.AudienceType audienceType, String questionCode) {
    Worksheet worksheet = worksheet(code, audienceType);
    worksheet.addQuestion(persistQuestion(questionCode));
    worksheet.approve();
    return worksheet;
  }

  private Worksheet worksheet(String code, Worksheet.AudienceType audienceType) {
    Worksheet worksheet = new Worksheet();
    worksheet.setTutorId(TUTOR_ID);
    worksheet.setCode(code);
    worksheet.setTitle("Water and Energy Practice");
    worksheet.setInstructions("Answer every question clearly.");
    worksheet.setAudienceType(audienceType);
    return worksheet;
  }

  private Question persistQuestion(String code) {
    var topic = syllabusRepository.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow();
    jdbcTemplate.update(
        "INSERT INTO questions "
            + "(code, syllabus_topic_id, syllabus_topic_type, question_type, "
            + "prompt, total_marks, model_answer) VALUES (?, ?, ?, ?, ?, ?, ?)",
        code,
        topic.getId(),
        topic.getNodeType().name(),
        "OPEN_ENDED",
        "Explain the science concept for " + code + ".",
        BigDecimal.ONE,
        "A complete scientific explanation.");
    Long questionId =
        jdbcTemplate.queryForObject("SELECT id FROM questions WHERE code = ?", Long.class, code);
    jdbcTemplate.update(
        "INSERT INTO marking_components "
            + "(question_id, position, description, marks) VALUES (?, ?, ?, ?)",
        questionId,
        0,
        "States the expected concept",
        BigDecimal.ONE);
    entityManager.clear();
    Question persistedQuestion = questionRepository.findByCode(code).orElseThrow();
    persistedQuestion.getImages();
    return persistedQuestion;
  }

  private TutorClass persistClass(long tutorId, String name) {
    TutorClass tutorClass = new TutorClass();
    tutorClass.setTutorId(tutorId);
    tutorClass.setClassName(name);
    tutorClass.setSubject("Science");
    tutorClass.setLevel("P5");
    classRepository.save(tutorClass);
    entityManager.flush();
    return tutorClass;
  }

  private StudentProfile persistStudent(long tutorId, String name) {
    StudentProfile student = new StudentProfile();
    student.setTutorId(tutorId);
    student.setFullName(name);
    studentRepository.save(student);
    entityManager.flush();
    return student;
  }

  private List<String> questionCodes(Worksheet worksheet) {
    return worksheet.getQuestions().stream()
        .map(WorksheetQuestion::getQuestion)
        .map(Question::getCode)
        .toList();
  }
}
