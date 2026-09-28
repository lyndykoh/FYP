package com.fttranscendence.learning.worksheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.learning.classroom.TutorClass;
import com.fttranscendence.learning.classroom.TutorClassRepository;
import com.fttranscendence.learning.question.Question;
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
class WorksheetGenerationTest {
  private static final long TUTOR_ID = 101L;
  @Autowired private WorksheetService service;
  @Autowired private TutorClassRepository classes;
  @Autowired private SyllabusTopicRepository topics;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void createsAStableDraftForAnIdempotentRequestThenApprovesAndAssignsAtomically() {
    TutorClass tutorClass = tutorClass();
    long topicId = topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow().getId();
    insertQuestion("SCI-GEN-002", topicId, "Explain condensation.");
    insertQuestion("SCI-GEN-001", topicId, "Explain evaporation.");
    WorksheetRequests.GenerateWorksheetRequest request =
        new WorksheetRequests.GenerateWorksheetRequest(
            WorksheetGenerationRequest.TargetMode.CLASS,
            List.of(topicId),
            2,
            Question.QuestionType.OPEN_ENDED,
            LocalDateTime.now().plusDays(3),
            "Water revision",
            "Use complete sentences.",
            null);

    WorksheetRequests.GenerationRequestResponse first =
        service.generate(TUTOR_ID, tutorClass.getId(), "worksheet-key-001", request);
    WorksheetRequests.GenerationRequestResponse replay =
        service.generate(TUTOR_ID, tutorClass.getId(), "worksheet-key-001", request);

    assertEquals(WorksheetGenerationRequest.Status.SUCCEEDED, first.status());
    assertEquals(first.id(), replay.id());
    assertNotNull(first.worksheet());
    assertEquals(
        List.of("SCI-GEN-001", "SCI-GEN-002"),
        first.worksheet().questions().stream()
            .map(WorksheetRequests.QuestionSummary::code)
            .toList());
    assertEquals(Worksheet.Status.DRAFT, first.worksheet().status());

    WorksheetRequests.WorksheetResponse approved =
        service.approveAndAssign(
            TUTOR_ID, first.worksheet().id(), new WorksheetRequests.ApproveWorksheetRequest(null));
    entityManager.flush();
    assertEquals(Worksheet.Status.APPROVED, approved.status());
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from worksheet_assignments where worksheet_id = ?",
            Integer.class,
            approved.id()));
  }

  @Test
  void balancesDeterministicSelectionAcrossEveryRequestedTopicAndSnapshotsClassSubject() {
    TutorClass tutorClass = tutorClass();
    long water = topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow().getId();
    long reproduction =
        topics.findByCode("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION").orElseThrow().getId();
    insertQuestion("SCI-BAL-01", water, "Water one.");
    insertQuestion("SCI-BAL-02", water, "Water two.");
    insertQuestion("SCI-BAL-03", reproduction, "Reproduction one.");
    insertQuestion("SCI-BAL-04", reproduction, "Reproduction two.");

    WorksheetRequests.GenerationRequestResponse response =
        service.generate(
            TUTOR_ID,
            tutorClass.getId(),
            "worksheet-balanced-key",
            new WorksheetRequests.GenerateWorksheetRequest(
                WorksheetGenerationRequest.TargetMode.CLASS,
                List.of(reproduction, water),
                4,
                Question.QuestionType.OPEN_ENDED,
                null,
                null,
                null,
                null));

    assertEquals(WorksheetGenerationRequest.Status.SUCCEEDED, response.status());
    assertEquals("Science", response.worksheet().subject());
    assertEquals(Worksheet.WorksheetType.STANDARD, response.worksheet().worksheetType());
    assertEquals(
        2,
        response.worksheet().questions().stream()
            .filter(question -> question.syllabusTopicId().equals(water))
            .count());
    assertEquals(
        2,
        response.worksheet().questions().stream()
            .filter(question -> question.syllabusTopicId().equals(reproduction))
            .count());
  }

  @Test
  void rejectsAQuestionCountSmallerThanTheSelectedTopicSetBeforeCreatingARequest() {
    TutorClass tutorClass = tutorClass();
    long water = topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow().getId();
    long reproduction =
        topics.findByCode("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION").orElseThrow().getId();

    WorksheetService.InvalidWorksheetRequestException exception =
        assertThrows(
            WorksheetService.InvalidWorksheetRequestException.class,
            () ->
                service.generate(
                    TUTOR_ID,
                    tutorClass.getId(),
                    "worksheet-invalid-count",
                    new WorksheetRequests.GenerateWorksheetRequest(
                        WorksheetGenerationRequest.TargetMode.CLASS,
                        List.of(water, reproduction),
                        1,
                        Question.QuestionType.OPEN_ENDED,
                        null,
                        null,
                        null,
                        null)));

    assertEquals(
        "questionCount must be at least the number of selected topics.", exception.getMessage());
    assertEquals(
        0,
        jdbc.queryForObject("select count(*) from worksheet_generation_requests", Integer.class));
  }

  @Test
  void generatesFromAllActiveEligibleQuestionsWhenNoTopicIdsAreProvided() {
    TutorClass tutorClass = tutorClass();
    long water = topics.findByCode("SCI_P5_CYCLES_MATTER_WATER_WATER").orElseThrow().getId();
    long reproduction =
        topics.findByCode("SCI_P5_CYCLES_PLANTS_ANIMALS_REPRODUCTION").orElseThrow().getId();
    insertQuestion(
        "SCI-ALL-02", reproduction, "Reproduction question.", Question.QuestionType.DIAGRAM);
    insertQuestion("SCI-ALL-01", water, "Water question.", Question.QuestionType.DIAGRAM);

    WorksheetRequests.GenerationRequestResponse response =
        service.generate(
            TUTOR_ID,
            tutorClass.getId(),
            "worksheet-all-questions-key",
            new WorksheetRequests.GenerateWorksheetRequest(
                WorksheetGenerationRequest.TargetMode.CLASS,
                null,
                2,
                Question.QuestionType.DIAGRAM,
                null,
                null,
                null,
                null));

    assertEquals(WorksheetGenerationRequest.Status.SUCCEEDED, response.status());
    assertEquals(List.of(), response.topicIds());
    List<String> selectedCodes =
        response.worksheet().questions().stream()
            .map(WorksheetRequests.QuestionSummary::code)
            .toList();
    assertEquals(2, selectedCodes.size());
    assertTrue(selectedCodes.containsAll(List.of("SCI-ALL-01", "SCI-ALL-02")));
  }

  @Test
  void failsOnlyWhenTheUnrestrictedTopicSelectorHasNoMatchingQuestions() {
    TutorClass tutorClass = tutorClass();

    WorksheetRequests.GenerationRequestResponse response =
        service.generate(
            TUTOR_ID,
            tutorClass.getId(),
            "worksheet-no-matches-key",
            new WorksheetRequests.GenerateWorksheetRequest(
                WorksheetGenerationRequest.TargetMode.CLASS,
                null,
                1,
                Question.QuestionType.TRUE_FALSE,
                null,
                null,
                null,
                null));

    assertEquals(WorksheetGenerationRequest.Status.FAILED, response.status());
    assertEquals("INSUFFICIENT_ACTIVE_QUESTIONS", response.failureCode());
    assertNull(response.worksheet());
  }

  @Test
  void generatesAP6ScienceWorksheetFromSeededQuestionsUsingTypeAndDifficulty() {
    TutorClass tutorClass = tutorClass();
    long topic = topics.findByCode("SCI_P6_ENERGY_FORMS_USES_PHOTOSYNTHESIS").orElseThrow().getId();

    WorksheetRequests.GenerationRequestResponse response =
        service.generate(
            TUTOR_ID,
            tutorClass.getId(),
            "p6-seeded-question-key",
            new WorksheetRequests.GenerateWorksheetRequest(
                WorksheetGenerationRequest.TargetMode.CLASS,
                List.of(topic),
                1,
                Question.QuestionType.OPEN_ENDED,
                Question.Difficulty.CHALLENGE,
                null,
                "P6 Science investigation",
                null,
                null,
                Worksheet.WorksheetType.STANDARD));

    assertEquals(WorksheetGenerationRequest.Status.SUCCEEDED, response.status());
    assertNotNull(response.worksheet());
    assertEquals(
        List.of("P6SCI-PHOTO-003"),
        response.worksheet().questions().stream()
            .map(WorksheetRequests.QuestionSummary::code)
            .toList());
    assertEquals(Question.Difficulty.CHALLENGE, response.difficulty());
  }

  private TutorClass tutorClass() {
    TutorClass tutorClass = new TutorClass();
    tutorClass.setTutorId(TUTOR_ID);
    tutorClass.setClassName("Generated science");
    tutorClass.setSubject("Science");
    tutorClass.setLevel("P5");
    classes.save(tutorClass);
    entityManager.flush();
    return tutorClass;
  }

  private void insertQuestion(String code, long topicId, String prompt) {
    insertQuestion(code, topicId, prompt, Question.QuestionType.OPEN_ENDED);
  }

  private void insertQuestion(
      String code, long topicId, String prompt, Question.QuestionType questionType) {
    jdbc.update(
        "insert into questions (code, syllabus_topic_id, syllabus_topic_type, question_type, prompt, total_marks, model_answer, archive_state) values (?, ?, 'SUBTOPIC', ?, ?, ?, 'Answer', 'ACTIVE')",
        code,
        topicId,
        questionType.name(),
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
