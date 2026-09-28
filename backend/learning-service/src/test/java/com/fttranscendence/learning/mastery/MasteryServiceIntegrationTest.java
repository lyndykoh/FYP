package com.fttranscendence.learning.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.learning.LearningServiceApplication;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = LearningServiceApplication.class)
@Transactional
class MasteryServiceIntegrationTest {
  private static final long TUTOR_ID = 101L;

  @Autowired MasteryService service;
  @Autowired StudentProfileRepository students;
  @Autowired MasteryRecordRepository records;
  @Autowired MasteryApprovedResultRepository approvedResults;
  @Autowired MasteryDiagnosticEvidenceRepository evidence;
  @Autowired EntityManager entityManager;

  @Test
  void persistsOnlyApprovedResultsAndIsIdempotent() {
    StudentProfile student = student("Ada");
    long topicId = activeTopics(1).get(0);
    var approved =
        new MasteryService.ApprovedResult(
            900L,
            TUTOR_ID,
            student.getId(),
            topicId,
            new BigDecimal("1.50"),
            new BigDecimal("2.00"),
            0,
            true);

    MasteryRecord saved = service.applyApprovedResult(approved);

    assertEquals(new BigDecimal("75.00"), saved.getScore());
    assertEquals(1, saved.getAttemptCount());
    assertEquals(1, service.applyApprovedResult(approved).getAttemptCount());
    assertThrows(
        MasteryService.UnapprovedResultException.class,
        () ->
            service.applyApprovedResult(
                new MasteryService.ApprovedResult(
                    901L,
                    TUTOR_ID,
                    student.getId(),
                    topicId,
                    BigDecimal.ONE,
                    new BigDecimal("2.00"),
                    0,
                    false)));
    assertEquals(
        1,
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topicId)
            .orElseThrow()
            .getAttemptCount());
  }

  @Test
  void firstDiagnosticIsNotRepeatedButTheSecondOccurrenceIsAndRebuildRemainsTopicScoped() {
    StudentProfile student = student("Grace");
    List<Long> topics = activeTopics(2);
    long heat = topics.get(0);
    long forces = topics.get(1);
    LocalDateTime start = LocalDateTime.of(2026, 8, 28, 9, 0);
    var concept =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.Category.CONCEPT,
            "Does not connect heat transfer to particle movement.",
            List.of("particle movement"));

    MasteryRecord first =
        service.applyApprovedMarking(
            approved(
                1001L, student.getId(), heat, new BigDecimal("1.50"), 1, start, List.of(concept)));
    assertEquals(new BigDecimal("75.00"), first.getScore());
    assertEquals(
        0,
        approvedResults.findBySourceSubmissionId(1001L).orElseThrow().getRepeatedMistakeCount(),
        "The current evidence must not make its own first occurrence repeated.");
    assertTrue(evidence.existsBySourceSubmissionId(1001L));

    service.applyApprovedMarking(
        approved(1001L, student.getId(), heat, new BigDecimal("1.50"), 1, start, List.of(concept)));
    assertEquals(
        1,
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), heat)
            .orElseThrow()
            .getAttemptCount(),
        "Same source and revision is idempotent.");

    MasteryRecord second =
        service.applyApprovedMarking(
            approved(
                1002L,
                student.getId(),
                heat,
                new BigDecimal("2.00"),
                1,
                start.plusMinutes(1),
                List.of(concept)));
    assertEquals(
        new BigDecimal("37.50"),
        second.getScore(),
        "The recurrent diagnostic makes only the second attempted mark contribute zero.");
    assertEquals(
        1, approvedResults.findBySourceSubmissionId(1002L).orElseThrow().getRepeatedMistakeCount());

    MasteryRecord separateTopic =
        service.applyApprovedMarking(
            approved(
                1003L,
                student.getId(),
                forces,
                new BigDecimal("1.00"),
                1,
                start.plusMinutes(2),
                List.of()));
    assertEquals(new BigDecimal("50.00"), separateTopic.getScore());
    assertEquals(
        new BigDecimal("37.50"),
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), heat)
            .orElseThrow()
            .getScore(),
        "Evidence and replay are scoped to the marked syllabus topic.");

    MasteryRecord third =
        service.applyApprovedMarking(
            approved(
                1004L,
                student.getId(),
                heat,
                new BigDecimal("1.00"),
                1,
                start.plusMinutes(3),
                List.of()));
    assertEquals(3, third.getAttemptCount());
    assertEquals(
        new BigDecimal("41.67"),
        third.getScore(),
        "75 + 0 + 50 is rebuilt as a deterministic mean.");
    assertEquals(3, third.getHistory().size());
  }

  @Test
  void recurrenceUsesTheCanonicalTypeRatherThanItsBroadCategory() {
    StudentProfile student = student("Canonical evidence");
    long topic = activeTopics(1).get(0);
    LocalDateTime start = LocalDateTime.of(2026, 8, 29, 9, 0);
    var incompleteWorking =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.MistakeType.INCOMPLETE_WORKING,
            MasteryDiagnosticEvidence.Category.APPLICATION,
            "The solution stops before the working is complete.",
            List.of());
    var calculationError =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.MistakeType.CALCULATION_ERROR,
            MasteryDiagnosticEvidence.Category.APPLICATION,
            "The arithmetic step is incorrect.",
            List.of());

    service.applyApprovedMarking(
        approved(
            1011L,
            student.getId(),
            topic,
            new BigDecimal("1.50"),
            1,
            start,
            List.of(incompleteWorking)));
    service.applyApprovedMarking(
        approved(
            1012L,
            student.getId(),
            topic,
            new BigDecimal("1.00"),
            1,
            start.plusMinutes(1),
            List.of(calculationError)));
    assertEquals(
        0,
        approvedResults.findBySourceSubmissionId(1012L).orElseThrow().getRepeatedMistakeCount(),
        "A different canonical type in APPLICATION must not be considered a repeat.");

    service.applyApprovedMarking(
        approved(
            1013L,
            student.getId(),
            topic,
            new BigDecimal("1.00"),
            1,
            start.plusMinutes(2),
            List.of(incompleteWorking)));
    assertEquals(
        1,
        approvedResults.findBySourceSubmissionId(1013L).orElseThrow().getRepeatedMistakeCount(),
        "The same canonical mistake type must be considered a repeat.");
    assertEquals(
        new BigDecimal("41.67"),
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic)
            .orElseThrow()
            .getScore(),
        "Only the third attempt is penalised for the repeated canonical type.");
  }

  @Test
  void canonicalMappingsArePersistedAndLegacyCategoriesHaveDocumentedDefaults() {
    StudentProfile student = student("Mapping evidence");
    long topic = activeTopics(1).get(0);
    LocalDateTime start = LocalDateTime.of(2026, 8, 29, 10, 0);
    int submission = 1020;
    for (MasteryDiagnosticEvidence.MistakeType type :
        MasteryDiagnosticEvidence.MistakeType.values()) {
      MasteryService.DiagnosticEvidence diagnostic =
          new MasteryService.DiagnosticEvidence(
              type, type.category(), "Tutor-confirmed " + type.name() + ".", List.of());
      service.applyApprovedMarking(
          approved(
              submission++,
              student.getId(),
              topic,
              new BigDecimal("1.00"),
              1,
              start.plusMinutes(submission),
              List.of(diagnostic)));
    }
    assertEquals(
        10,
        evidence
            .findByMasteryRecordId(
                records
                    .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic)
                    .orElseThrow()
                    .getId())
            .stream()
            .map(MasteryDiagnosticEvidence::getMistakeType)
            .distinct()
            .count());

    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.CONCEPT_MISUNDERSTANDING,
        new MasteryService.DiagnosticEvidence(
                MasteryDiagnosticEvidence.Category.CONCEPT, "Legacy", List.of())
            .canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT,
        new MasteryService.DiagnosticEvidence(
                MasteryDiagnosticEvidence.Category.KEYWORD, "Legacy", List.of())
            .canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.WEAK_EXPLANATION,
        new MasteryService.DiagnosticEvidence(
                MasteryDiagnosticEvidence.Category.EXPRESSION, "Legacy", List.of())
            .canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.INCOMPLETE_WORKING,
        new MasteryService.DiagnosticEvidence(
                MasteryDiagnosticEvidence.Category.APPLICATION, "Legacy", List.of())
            .canonicalMistakeType());
  }

  @Test
  void rejectsMismatchedCanonicalMistakeTypeAndCategory() {
    StudentProfile student = student("Invalid canonical evidence");
    long topic = activeTopics(1).get(0);
    var invalid =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT,
            MasteryDiagnosticEvidence.Category.CONCEPT,
            "A contradiction in external evidence.",
            List.of());
    MasteryService.InvalidResultException exception =
        assertThrows(
            MasteryService.InvalidResultException.class,
            () ->
                service.applyApprovedMarking(
                    approved(
                        1031L,
                        student.getId(),
                        topic,
                        BigDecimal.ONE,
                        1,
                        LocalDateTime.of(2026, 8, 29, 11, 0),
                        List.of(invalid))));
    assertTrue(exception.getMessage().contains("must match"));
  }

  @Test
  void retractionRemovesTheContributionAndARevisionCanReapplyItWithoutDuplicatingHistory() {
    StudentProfile student = student("Lin");
    long topic = activeTopics(1).get(0);
    LocalDateTime start = LocalDateTime.of(2026, 8, 28, 10, 0);
    var keyword =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.Category.KEYWORD,
            "The required term is absent.",
            List.of("evaporation"));

    service.applyApprovedMarking(
        approved(1101L, student.getId(), topic, new BigDecimal("2.00"), 1, start, List.of()));
    MasteryRecord initial =
        service.applyApprovedMarking(
            approved(
                1102L,
                student.getId(),
                topic,
                new BigDecimal("1.00"),
                1,
                start.plusMinutes(1),
                List.of(keyword)));
    assertEquals(new BigDecimal("75.00"), initial.getScore());
    assertEquals(2, initial.getAttemptCount());

    MasteryRecord retracted =
        service.applyApprovedMarking(
            retracted(
                1102L, student.getId(), topic, new BigDecimal("1.00"), 2, start.plusMinutes(1)));
    assertEquals(new BigDecimal("100.00"), retracted.getScore());
    assertEquals(1, retracted.getAttemptCount());
    assertFalse(approvedResults.findBySourceSubmissionId(1102L).orElseThrow().isActive());
    assertFalse(evidence.existsBySourceSubmissionId(1102L));

    service.applyApprovedMarking(
        retracted(1102L, student.getId(), topic, new BigDecimal("1.00"), 2, start.plusMinutes(1)));
    assertEquals(
        1,
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic)
            .orElseThrow()
            .getAttemptCount(),
        "A duplicate retraction must not rebuild another history row.");

    MasteryRecord revised =
        service.applyApprovedMarking(
            approved(
                1102L,
                student.getId(),
                topic,
                BigDecimal.ZERO.setScale(2),
                3,
                start.plusMinutes(2),
                List.of()));
    assertEquals(new BigDecimal("50.00"), revised.getScore());
    assertEquals(2, revised.getAttemptCount());
    assertTrue(approvedResults.findBySourceSubmissionId(1102L).orElseThrow().isActive());
  }

  @Test
  void higherApprovedRevisionReplacesEvidenceWithTheSameMistakeType() {
    StudentProfile student = student("Evidence revision");
    long topic = activeTopics(1).get(0);
    LocalDateTime reviewedAt = LocalDateTime.of(2026, 8, 28, 13, 0);
    var initial =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT,
            MasteryDiagnosticEvidence.Category.KEYWORD,
            "Initial evidence.",
            List.of("evaporation"));
    var revised =
        new MasteryService.DiagnosticEvidence(
            MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT,
            MasteryDiagnosticEvidence.Category.KEYWORD,
            "Revised evidence.",
            List.of("condensation"));

    service.applyApprovedMarking(
        approved(1161L, student.getId(), topic, BigDecimal.ONE, 1, reviewedAt, List.of(initial)));
    service.applyApprovedMarking(
        approved(
            1161L,
            student.getId(),
            topic,
            BigDecimal.ONE,
            2,
            reviewedAt.plusMinutes(1),
            List.of(revised)));

    entityManager.flush();
    entityManager.clear();
    List<MasteryDiagnosticEvidence> stored =
        evidence.findByStudentProfileIdOrderByCreatedAtDescIdDesc(student.getId());
    assertEquals(1, stored.size());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT, stored.get(0).getMistakeType());
    assertEquals(List.of("condensation"), stored.get(0).getMissingKeywords());
    assertEquals("Revised evidence.", stored.get(0).getTutorRationale());
  }

  @Test
  void equalOrOlderEventsCannotOverwriteTheLatestRetractionState() {
    StudentProfile student = student("Nora");
    long topic = activeTopics(1).get(0);
    LocalDateTime approvedAt = LocalDateTime.of(2026, 8, 28, 12, 0);

    service.applyApprovedMarking(
        approved(1151L, student.getId(), topic, new BigDecimal("2.00"), 1, approvedAt, List.of()));
    service.applyApprovedMarking(
        retracted(1151L, student.getId(), topic, new BigDecimal("2.00"), 1, approvedAt));
    assertTrue(
        approvedResults.findBySourceSubmissionId(1151L).orElseThrow().isActive(),
        "A conflicting equal revision is an idempotent no-op, not a state overwrite.");

    service.applyApprovedMarking(
        retracted(1151L, student.getId(), topic, new BigDecimal("2.00"), 2, approvedAt));
    assertFalse(approvedResults.findBySourceSubmissionId(1151L).orElseThrow().isActive());
    assertEquals(
        0,
        records
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic)
            .orElseThrow()
            .getAttemptCount());

    service.applyApprovedMarking(
        approved(1151L, student.getId(), topic, new BigDecimal("2.00"), 1, approvedAt, List.of()));
    MasteryRecord finalRecord =
        records.findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic).orElseThrow();
    assertFalse(
        approvedResults.findBySourceSubmissionId(1151L).orElseThrow().isActive(),
        "The delayed, older approval must not reactivate a newer retraction.");
    assertEquals(0, finalRecord.getAttemptCount());
    assertEquals(BigDecimal.ZERO.setScale(2), finalRecord.getScore());
  }

  @Test
  void exactMasteryThresholdsUseTheCanonicalStatuses() {
    long topic = activeTopics(1).get(0);
    assertStatusAt(topic, "50", MasteryRecord.MasteryStatus.PRACTISING, 1201L);
    assertStatusAt(topic, "70", MasteryRecord.MasteryStatus.IMPROVING, 1202L);
    assertStatusAt(topic, "85", MasteryRecord.MasteryStatus.MASTERED, 1203L);
  }

  private void assertStatusAt(
      long topic, String percent, MasteryRecord.MasteryStatus expected, long submissionId) {
    StudentProfile student = student("Student " + submissionId);
    BigDecimal marks = new BigDecimal(percent).multiply(new BigDecimal("0.02")).setScale(2);
    MasteryRecord result =
        service.applyApprovedMarking(
            approved(
                submissionId,
                student.getId(),
                topic,
                marks,
                1,
                LocalDateTime.of(2026, 8, 28, 11, 0).plusMinutes(submissionId - 1200),
                List.of()));
    assertEquals(expected, result.getMasteryStatus());
  }

  private MasteryService.ApprovedMarking approved(
      long submissionId,
      long studentId,
      long topicId,
      BigDecimal marks,
      int revision,
      LocalDateTime reviewedAt,
      List<MasteryService.DiagnosticEvidence> diagnostics) {
    return new MasteryService.ApprovedMarking(
        submissionId,
        TUTOR_ID,
        77L,
        88L,
        studentId,
        topicId,
        marks,
        new BigDecimal("2.00"),
        revision,
        MasteryService.State.APPROVED,
        reviewedAt,
        diagnostics);
  }

  private MasteryService.ApprovedMarking retracted(
      long submissionId,
      long studentId,
      long topicId,
      BigDecimal previousMarks,
      int revision,
      LocalDateTime approvedAt) {
    return new MasteryService.ApprovedMarking(
        submissionId,
        TUTOR_ID,
        77L,
        88L,
        studentId,
        topicId,
        previousMarks,
        new BigDecimal("2.00"),
        revision,
        MasteryService.State.RETRACTED,
        approvedAt,
        List.of());
  }

  private StudentProfile student(String name) {
    StudentProfile student = new StudentProfile();
    student.setTutorId(TUTOR_ID);
    student.setFullName(name);
    students.save(student);
    entityManager.flush();
    return student;
  }

  private List<Long> activeTopics(int count) {
    return entityManager
        .createQuery(
            "select t.id from SyllabusTopic t where t.active = true order by t.id", Long.class)
        .setMaxResults(count)
        .getResultList();
  }
}
