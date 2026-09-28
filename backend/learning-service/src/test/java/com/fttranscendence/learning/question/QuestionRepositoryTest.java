package com.fttranscendence.learning.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
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
class QuestionRepositoryTest {

  @Autowired private QuestionRepository repository;
  @Autowired private SyllabusTopicRepository syllabusRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private Validator validator;

  @BeforeEach
  void clearQuestions() {
    jdbcTemplate.update("DELETE FROM question_keywords");
    jdbcTemplate.update("DELETE FROM marking_components");
    jdbcTemplate.update("DELETE FROM questions");
  }

  @Test
  void persistsAndRetrievesACompleteMultiComponentQuestion() {
    Question question =
        questionFor(" sci-water-001 ", "SCI_P5_CYCLES_MATTER_WATER_WATER", new BigDecimal("3.00"));
    question.addMarkingComponent(
        "States that heat supplies energy", new BigDecimal("1.00"), List.of("heat", "energy"));
    question.addMarkingComponent(
        "Explains faster evaporation", new BigDecimal("2.00"), List.of("evaporation"));
    question.addKeyword(" Heat ");
    question.addKeyword("Evaporation");

    repository.save(question);
    entityManager.flush();
    entityManager.clear();

    Question loaded = repository.findByCode("SCI-WATER-001").orElseThrow();

    assertEquals("SCI-WATER-001", loaded.getCode());
    assertEquals(Question.QuestionType.OPEN_ENDED, loaded.getQuestionType());
    assertEquals("SCI_P5_CYCLES_MATTER_WATER_WATER", loaded.getSyllabusTopic().getCode());
    assertEquals(new BigDecimal("3.00"), loaded.getTotalMarks());
    assertEquals(2, loaded.getMarkingComponents().size());
    assertEquals(
        List.of("States that heat supplies energy", "Explains faster evaporation"),
        loaded.getMarkingComponents().stream().map(MarkingComponent::getDescription).toList());
    assertEquals(List.of("heat", "energy"), loaded.getMarkingComponents().get(0).getKeywords());
    assertEquals(List.of("heat", "evaporation"), loaded.getKeywords());
    assertEquals(Question.ArchiveState.ACTIVE, loaded.getArchiveState());
  }

  @Test
  void rejectsMissingRequiredQuestionFields() {
    Question missingCode = validQuestion("SCI-REQ-001");
    missingCode.setCode("  ");
    Question missingTaxonomy = validQuestion("SCI-REQ-002");
    missingTaxonomy.setSyllabusTopic(null);
    Question missingType = validQuestion("SCI-REQ-003");
    missingType.setQuestionType(null);
    Question missingPrompt = validQuestion("SCI-REQ-004");
    missingPrompt.setPrompt(" ");
    Question missingModelAnswer = validQuestion("SCI-REQ-005");
    missingModelAnswer.setModelAnswer(null);
    Question nonPositiveMarks = validQuestion("SCI-REQ-006");
    nonPositiveMarks.setTotalMarks(BigDecimal.ZERO);

    assertFalse(validator.validate(missingCode).isEmpty());
    assertFalse(validator.validate(missingTaxonomy).isEmpty());
    assertFalse(validator.validate(missingType).isEmpty());
    assertFalse(validator.validate(missingPrompt).isEmpty());
    assertFalse(validator.validate(missingModelAnswer).isEmpty());
    assertFalse(validator.validate(nonPositiveMarks).isEmpty());
  }

  @Test
  void onlyAllowsActiveTopicOrSubtopicTaxonomyNodes() {
    Question subjectQuestion = questionFor("SCI-TAXONOMY-001", "SCI", BigDecimal.ONE);
    subjectQuestion.addMarkingComponent("A valid component", BigDecimal.ONE);

    assertFalse(validator.validate(subjectQuestion).isEmpty());

    Question topicQuestion =
        questionFor("SCI-TAXONOMY-002", "SCI_P6_ENERGY_CONVERSION", BigDecimal.ONE);
    topicQuestion.addMarkingComponent("A valid component", BigDecimal.ONE);
    assertTrue(validator.validate(topicQuestion).isEmpty());
  }

  @Test
  void rejectsAQuestionWhoseTaxonomyReferenceDoesNotExist() {
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO questions "
                    + "(code, syllabus_topic_id, syllabus_topic_type, question_type, "
                    + "prompt, total_marks, model_answer) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "SCI-MISSING-TOPIC-001",
                999999L,
                "TOPIC",
                "SHORT_ANSWER",
                "A valid prompt",
                new BigDecimal("1.00"),
                "A valid model answer"));
  }

  @Test
  void rejectsMarkingComponentsThatDoNotTotalTheQuestionMarks() {
    Question question =
        questionFor("SCI-MARKS-001", "SCI_P6_ENERGY_CONVERSION", new BigDecimal("3.00"));
    question.addMarkingComponent("Only two marks allocated", new BigDecimal("2.00"));

    assertFalse(validator.validate(question).isEmpty());
    assertThrows(
        ConstraintViolationException.class,
        () -> {
          repository.save(question);
          entityManager.flush();
        });
  }

  @Test
  void cascadesComponentsAndRemovesOrphansWithoutDeletingTheQuestion() {
    Question question =
        questionFor("SCI-CASCADE-001", "SCI_P6_INTERACTIONS_FORCES", new BigDecimal("3.00"));
    question.addMarkingComponent("Identifies friction", BigDecimal.ONE);
    MarkingComponent removable =
        question.addMarkingComponent("Explains the direction of friction", new BigDecimal("2.00"));
    repository.save(question);
    entityManager.flush();

    assertEquals(2, componentCount(question.getId()));

    question.removeMarkingComponent(removable);
    question.setTotalMarks(BigDecimal.ONE);
    entityManager.flush();
    entityManager.clear();

    assertEquals(1, componentCount(question.getId()));
    assertTrue(repository.findByCode("SCI-CASCADE-001").isPresent());
  }

  @Test
  void preventsDuplicateQuestionCodesAfterCanonicalisation() {
    repository.save(validQuestion("SCI-DUPLICATE-001"));
    entityManager.flush();

    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          repository.save(validQuestion("  sci-duplicate-001  "));
          entityManager.flush();
        });
  }

  @Test
  void preventsDuplicateKeywordsIgnoringCaseAndWhitespace() {
    Question question = validQuestion("SCI-KEYWORD-001");
    question.addKeyword("Evaporation");

    assertThrows(IllegalArgumentException.class, () -> question.addKeyword(" evaporation "));
  }

  @Test
  void archivesQuestionsWithoutExposingHardDeleteOperations() {
    Question question = repository.save(validQuestion("SCI-ARCHIVE-001"));
    entityManager.flush();

    assertEquals(
        List.of("SCI-ARCHIVE-001"),
        codes(
            repository.findAllBySyllabusTopicAndArchiveState(
                question.getSyllabusTopic().getId(), Question.ArchiveState.ACTIVE)));

    question.archive();
    entityManager.flush();
    entityManager.clear();

    assertTrue(
        repository
            .findAllBySyllabusTopicAndArchiveState(
                question.getSyllabusTopic().getId(), Question.ArchiveState.ACTIVE)
            .isEmpty());
    assertEquals(
        List.of("SCI-ARCHIVE-001"),
        codes(
            repository.findAllBySyllabusTopicAndArchiveState(
                question.getSyllabusTopic().getId(), Question.ArchiveState.ARCHIVED)));
    assertFalse(
        Arrays.stream(QuestionRepository.class.getMethods())
            .anyMatch(method -> method.getName().startsWith("delete")));
  }

  private Question validQuestion(String code) {
    Question question = questionFor(code, "SCI_P5_CYCLES_MATTER_WATER_WATER", BigDecimal.ONE);
    question.addMarkingComponent("States the expected answer", BigDecimal.ONE);
    return question;
  }

  private Question questionFor(String code, String topicCode, BigDecimal totalMarks) {
    SyllabusTopic topic = syllabusRepository.findByCode(topicCode).orElseThrow();
    Question question = new Question();
    question.setCode(code);
    question.setSyllabusTopic(topic);
    question.setQuestionType(Question.QuestionType.OPEN_ENDED);
    question.setPrompt("Explain the science concept.");
    question.setTotalMarks(totalMarks);
    question.setModelAnswer("A complete model answer.");
    return question;
  }

  private int componentCount(Long questionId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM marking_components WHERE question_id = ?",
            Integer.class,
            questionId);
    return count == null ? 0 : count;
  }

  private List<String> codes(List<Question> questions) {
    return questions.stream().map(Question::getCode).toList();
  }
}
