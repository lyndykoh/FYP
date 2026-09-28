package com.fttranscendence.grading.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fttranscendence.grading.repository.MistakeRecordRepository;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MistakeRecordRepositoryTest {

  private static final long OWNER_ID = 101L;
  private static final long STUDENT_ID = 201L;

  @Autowired private MistakeRecordRepository mistakeRecordRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void canonicalMistakeTypesHaveTheDocumentedCompatibilityMappings() {
    assertEquals(
        DiagnosticCategory.CONCEPT, MistakeType.CONCEPT_MISUNDERSTANDING.getDiagnosticCategory());
    assertEquals(DiagnosticCategory.KEYWORD, MistakeType.MISSING_KEY_POINT.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.EXPRESSION, MistakeType.WEAK_EXPLANATION.getDiagnosticCategory());
    assertEquals(DiagnosticCategory.EXPRESSION, MistakeType.WRONG_UNITS.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.EXPRESSION, MistakeType.ANSWER_FORMAT_ISSUE.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.APPLICATION, MistakeType.CALCULATION_ERROR.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.APPLICATION, MistakeType.MISREAD_QUESTION.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.APPLICATION, MistakeType.INCOMPLETE_WORKING.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.APPLICATION, MistakeType.INCORRECT_FORMULA.getDiagnosticCategory());
    assertEquals(
        DiagnosticCategory.APPLICATION, MistakeType.CARELESS_MISTAKE.getDiagnosticCategory());
  }

  @Test
  void supportsEveryControlledMistakeTypeAndMultipleTypesPerAnswer() {
    Submission answer = answer(301L, 401L, "all-types.pdf", "a");
    for (int index = 0; index < MistakeType.values().length; index++) {
      MistakeType type = MistakeType.values()[index];
      answer.addMistake(type, 900L + index, "P5.SCI." + index, type.getLabel());
    }

    entityManager.persist(answer.getSubmissionDocument());
    entityManager.persist(answer);
    entityManager.flush();

    assertEquals(MistakeType.values().length, mistakeRecordRepository.count());
    assertEquals(
        MistakeType.CONCEPT_MISUNDERSTANDING,
        mistakeRecordRepository
            .findByStudentIdAndMistakeTypeOrderByCreatedAtDescIdDesc(
                STUDENT_ID, MistakeType.CONCEPT_MISUNDERSTANDING)
            .get(0)
            .getMistakeType());
  }

  @Test
  void repeatedMistakesAcrossWorksheetsAreQueryable() {
    Submission first = answer(302L, 402L, "first.pdf", "b");
    Submission second = answer(303L, 403L, "second.pdf", "c");
    first.addMistake(MistakeType.WRONG_UNITS, 910L, "P5.SCI.MEASURE", "First worksheet");
    second.addMistake(MistakeType.WRONG_UNITS, 910L, "P5.SCI.MEASURE", "Second worksheet");
    persist(first);
    persist(second);

    List<MistakeRecord> history =
        mistakeRecordRepository.findByStudentIdAndMistakeTypeOrderByCreatedAtDescIdDesc(
            STUDENT_ID, MistakeType.WRONG_UNITS);
    assertEquals(2, history.size());
    assertEquals(
        2,
        mistakeRecordRepository.countByStudentIdAndMistakeType(
            STUDENT_ID, MistakeType.WRONG_UNITS));
    assertEquals(303L, history.get(0).getWorksheetId());
  }

  @Test
  void retainsAnswerAndTopicProvenance() {
    Submission answer = answer(304L, 404L, "provenance.pdf", "d");
    MistakeRecord mistake =
        answer.addMistake(
            MistakeType.MISSING_KEY_POINT, 920L, "P6.SCI.CELLS", "The answer omitted the nucleus.");
    persist(answer);
    entityManager.clear();

    MistakeRecord persisted = mistakeRecordRepository.findById(mistake.getId()).orElseThrow();
    assertEquals(answer.getId(), persisted.getSubmission().getId());
    assertEquals(STUDENT_ID, persisted.getStudentId());
    assertEquals(304L, persisted.getWorksheetId());
    assertEquals(404L, persisted.getWorksheetQuestionId());
    assertEquals(504L, persisted.getQuestionBankId());
    assertEquals(920L, persisted.getSyllabusTopicId());
    assertEquals("P6.SCI.CELLS", persisted.getSyllabusTopicCode());
    assertEquals(
        1,
        mistakeRecordRepository
            .findByStudentIdAndSyllabusTopicCodeOrderByCreatedAtDescIdDesc(
                STUDENT_ID, "P6.SCI.CELLS")
            .size());
  }

  @Test
  void rejectsDuplicateTypeForOneAnswerAndInvalidTypeOrTopic() {
    Submission answer = answer(305L, 405L, "invalid.pdf", "e");
    answer.addMistake(MistakeType.CALCULATION_ERROR, null, null, "Arithmetic error");
    assertThrows(
        IllegalArgumentException.class,
        () -> answer.addMistake(MistakeType.CALCULATION_ERROR, null, null, "Repeated type"));
    assertThrows(
        IllegalArgumentException.class, () -> answer.addMistake(null, null, null, "Missing type"));
    assertThrows(
        IllegalArgumentException.class,
        () -> answer.addMistake(MistakeType.MISREAD_QUESTION, 1L, null, "Missing topic code"));
    assertThrows(
        IllegalArgumentException.class, () -> MistakeType.fromLabel("Not a supported mistake"));
  }

  @Test
  void cannotCreateHistoryFromAnUnapprovedAiSuggestion() {
    SubmissionDocument document = readyDocument(307L, "pending.pdf", "a");
    Submission pending =
        Submission.createAnswer(
            document,
            407L,
            507L,
            "Extracted answer",
            "Model answer",
            new BigDecimal("2.00"),
            607L,
            "SCI-607");
    pending.recordAiSuggestion(
        new BigDecimal("1.00"),
        "Partially Correct",
        "Missing key point",
        List.of(),
        "AI suggestion");

    assertThrows(
        IllegalStateException.class,
        () ->
            pending.addMistake(
                MistakeType.MISSING_KEY_POINT, null, null, "Must wait for tutor approval"));
  }

  @Test
  void databaseUniqueConstraintRejectsDuplicateTypeInsertedOutsideAggregate() {
    Submission answer = answer(306L, 406L, "duplicate.pdf", "f");
    answer.addMistake(MistakeType.CARELESS_MISTAKE, null, null, "Careless error");
    persist(answer);
    MistakeRecord duplicate =
        MistakeRecord.create(
            answer, MistakeType.CARELESS_MISTAKE, null, null, "Duplicate database row");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> mistakeRecordRepository.saveAndFlush(duplicate));
  }

  private Submission answer(
      long worksheetId, long worksheetQuestionId, String filename, String checksum) {
    SubmissionDocument document = readyDocument(worksheetId, filename, checksum);
    Submission answer =
        Submission.createAnswer(
            document,
            worksheetQuestionId,
            worksheetQuestionId + 100L,
            "Extracted answer",
            "Model answer",
            new BigDecimal("2.00"),
            607L,
            "SCI-607");
    answer.approve(OWNER_ID, new BigDecimal("1.00"), "Tutor-approved answer");
    return answer;
  }

  private SubmissionDocument readyDocument(long worksheetId, String filename, String checksum) {
    SubmissionDocument document =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.TUTOR,
            worksheetId,
            STUDENT_ID,
            SubmissionDocument.SourceType.PDF);
    document.addPage(
        new DocumentStorage.StoredFile(
            OWNER_ID + "/" + filename, filename, "application/pdf", 128, checksum.repeat(64)));
    document.markReady();
    return document;
  }

  private void persist(Submission answer) {
    entityManager.persist(answer.getSubmissionDocument());
    entityManager.persist(answer);
    entityManager.flush();
  }
}
