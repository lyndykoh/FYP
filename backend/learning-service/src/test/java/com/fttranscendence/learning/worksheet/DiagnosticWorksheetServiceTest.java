package com.fttranscendence.learning.worksheet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DiagnosticWorksheetServiceTest {
  private static final long TUTOR_ID = 101L;
  @Autowired private DiagnosticWorksheetService service;
  @Autowired private TutorClassRepository classes;
  @Autowired private StudentProfileRepository students;
  @Autowired private MasteryRecordRepository mastery;
  @Autowired private SyllabusTopicRepository topics;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void returnsExplicitInsufficientEvidenceUntilRecordedMasteryAttemptsExist() {
    TutorClass tutorClass = tutorClass();
    StudentProfile student = new StudentProfile();
    student.setTutorId(TUTOR_ID);
    student.setFullName("Ari Tan");
    student.addClassMembership(tutorClass.getId());
    students.save(student);
    entityManager.flush();

    assertEquals(
        DiagnosticWorksheetService.Status.INSUFFICIENT_EVIDENCE,
        service.recommendations(TUTOR_ID, tutorClass.getId()).status());

    MasteryRecord record =
        new MasteryRecord(
            student, topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow());
    mastery.save(record);
    entityManager.flush();
    record.updateScore(new BigDecimal("42.00"), 70001L, "Reviewed response");
    entityManager.flush();

    DiagnosticWorksheetService.Recommendations response =
        service.recommendations(TUTOR_ID, tutorClass.getId());
    assertEquals(DiagnosticWorksheetService.Status.READY, response.status());
    assertEquals(1, response.recommendations().size());
    assertEquals(student.getId(), response.recommendations().get(0).studentId());
  }

  @Test
  void createsAnIdempotentDiagnosticDraftWithPersistedEvidenceReasonAndNeverApprovesIt() {
    TutorClass tutorClass = tutorClass();
    StudentProfile student = new StudentProfile();
    student.setTutorId(TUTOR_ID);
    student.setFullName("Ari Tan");
    student.addClassMembership(tutorClass.getId());
    students.save(student);
    entityManager.flush();
    long topicId = topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow().getId();
    MasteryRecord record = new MasteryRecord(student, topics.findById(topicId).orElseThrow());
    mastery.save(record);
    entityManager.flush();
    record.updateScore(new BigDecimal("42.00"), 70002L, "Reviewed response");
    entityManager.flush();
    insertQuestion("SCI-DIAG-01", topicId, "Explain evaporation.");
    insertQuestion("SCI-DIAG-02", topicId, "Explain condensation.");

    WorksheetRequests.GenerateDiagnosticWorksheetRequest request =
        new WorksheetRequests.GenerateDiagnosticWorksheetRequest(
            WorksheetGenerationRequest.TargetMode.STUDENTS,
            List.of(topicId),
            2,
            Question.QuestionType.OPEN_ENDED,
            LocalDateTime.now().plusDays(2),
            "Water diagnostic",
            null,
            List.of(student.getId()));
    WorksheetRequests.GenerationRequestResponse first =
        service.generate(TUTOR_ID, tutorClass.getId(), "diagnostic-key", request);
    WorksheetRequests.GenerationRequestResponse replay =
        service.generate(TUTOR_ID, tutorClass.getId(), "diagnostic-key", request);

    assertEquals(first.id(), replay.id());
    assertEquals(Worksheet.WorksheetType.DIAGNOSTIC, first.worksheet().worksheetType());
    assertEquals(Worksheet.Status.DRAFT, first.worksheet().status());
    org.junit.jupiter.api.Assertions.assertTrue(
        first.worksheet().instructions().contains("approved mastery is 42%"));
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from worksheet_assignments where worksheet_id = ?",
            Integer.class,
            first.worksheet().id()));
  }

  private TutorClass tutorClass() {
    TutorClass tutorClass = new TutorClass();
    tutorClass.setTutorId(TUTOR_ID);
    tutorClass.setClassName("Diagnostic science");
    tutorClass.setSubject("Science");
    tutorClass.setLevel("P5");
    classes.save(tutorClass);
    entityManager.flush();
    return tutorClass;
  }

  private void insertQuestion(String code, long topicId, String prompt) {
    jdbc.update(
        "insert into questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) values (?, ?, 'SUBTOPIC', 'OPEN_ENDED', ?, ?, 'Answer', 'ACTIVE')",
        code,
        topicId,
        prompt,
        BigDecimal.ONE);
    long questionId =
        jdbc.queryForObject("select id from questions where code = ?", Long.class, code);
    jdbc.update(
        "insert into marking_components (question_id, position, description, marks) values (?, 0, 'Criterion', ?)",
        questionId,
        BigDecimal.ONE);
    entityManager.clear();
  }
}
