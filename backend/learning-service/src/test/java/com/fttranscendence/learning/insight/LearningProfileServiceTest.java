package com.fttranscendence.learning.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidence;
import com.fttranscendence.learning.mastery.MasteryDiagnosticEvidenceRepository;
import com.fttranscendence.learning.mastery.MasteryHistory;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class LearningProfileServiceTest {
  private StudentProfileRepository students;
  private MasteryRecordRepository mastery;
  private MasteryDiagnosticEvidenceRepository diagnostics;
  private LearningProfileService service;
  private StudentProfile student;

  @BeforeEach
  void setUp() {
    students = mock(StudentProfileRepository.class);
    mastery = mock(MasteryRecordRepository.class);
    diagnostics = mock(MasteryDiagnosticEvidenceRepository.class);
    service = new LearningProfileService(students, mastery, diagnostics);
    student = mock(StudentProfile.class);
    when(student.getId()).thenReturn(31L);
    when(students.findByIdAndTutorId(31L, 7L)).thenReturn(java.util.Optional.of(student));
  }

  @ParameterizedTest
  @CsvSource({
    "CONCEPT,CONCEPT_WEAKNESS",
    "KEYWORD,KEYWORD_WEAKNESS",
    "EXPRESSION,EXPRESSION_WEAKNESS",
    "APPLICATION,APPLICATION_WEAKNESS"
  })
  void createsEvidenceCitedFindingsOnlyFromStructuredTutorConfirmedEvidence(
      String category, String type) {
    MasteryRecord evidence = record(61, 1, history(70, 61, "Tutor-approved result"));
    when(mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(31L))
        .thenReturn(List.of(evidence));
    MasteryDiagnosticEvidence diagnostic = mock(MasteryDiagnosticEvidence.class);
    when(diagnostic.getMasteryRecord()).thenReturn(evidence);
    when(diagnostic.getCategory()).thenReturn(MasteryDiagnosticEvidence.Category.valueOf(category));
    when(diagnostic.getTutorRationale()).thenReturn("Tutor confirmed this diagnostic.");
    when(diagnostic.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 28, 10, 0));
    when(diagnostic.getMissingKeywords())
        .thenReturn(category.equals("KEYWORD") ? List.of("adaptation") : List.of());
    when(diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(31L))
        .thenReturn(List.of(diagnostic));

    LearningProfileService.LearningProfileResponse profile = service.forTutor(7L, 31L);

    LearningProfileService.Finding finding =
        profile.findings().stream()
            .filter(item -> item.type().name().equals(type))
            .findFirst()
            .orElseThrow();
    assertEquals(1, finding.evidence().size());
    assertEquals("Tutor confirmed this diagnostic.", finding.evidence().get(0).sourceReason());
    LearningProfileService.LearningDimension dimension =
        profile.dimensions().stream()
            .filter(item -> item.category().name().equals(category))
            .findFirst()
            .orElseThrow();
    assertEquals(1, dimension.evidenceCount());
    assertEquals("Tutor confirmed this diagnostic.", dimension.evidence().get(0).sourceReason());
    assertEquals(LearningProfileService.Source.DETERMINISTIC, profile.source());
  }

  @Test
  void exposesImprovingTopicsFromApprovedMasteryStatusWithoutInventingDiagnosticEvidence() {
    MasteryRecord improving = record(76, 2, history(61, 76, "Approved result"));
    when(improving.getMasteryStatus()).thenReturn(MasteryRecord.MasteryStatus.IMPROVING);
    when(mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(31L))
        .thenReturn(List.of(improving));
    when(diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(31L)).thenReturn(List.of());

    LearningProfileService.LearningProfileResponse profile = service.forTutor(7L, 31L);

    assertEquals(
        List.of("Adaptation"),
        profile.improvements().stream()
            .map(LearningProfileService.TopicSummary::topicName)
            .toList());
    assertTrue(profile.dimensions().stream().allMatch(item -> item.evidenceCount() == 0));
  }

  @Test
  void derivesStrongWeakAndImprovingTopicsFromCanonicalMasteryRecords() {
    MasteryRecord strong = record(91, 3, history(88, 91, "Approved result"));
    MasteryRecord weak = record(48, 2, history(52, 48, "Approved result"));
    MasteryRecord improving = record(76, 2, history(61, 76, "Approved result"));
    when(improving.getMasteryStatus()).thenReturn(MasteryRecord.MasteryStatus.IMPROVING);
    when(mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(31L))
        .thenReturn(List.of(strong, weak, improving));
    when(diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(31L)).thenReturn(List.of());

    LearningProfileService.LearningProfileResponse profile = service.forTutor(7L, 31L);

    assertEquals(
        List.of(new BigDecimal("91.00")),
        profile.strengths().stream().map(LearningProfileService.TopicSummary::score).toList());
    assertEquals(
        List.of(new BigDecimal("48.00")),
        profile.growthAreas().stream().map(LearningProfileService.TopicSummary::score).toList());
    assertEquals(
        List.of(new BigDecimal("76.00")),
        profile.improvements().stream().map(LearningProfileService.TopicSummary::score).toList());
  }

  @Test
  void identifiesRepeatedWeaknessAndRegressionFromApprovedMasteryOnly() {
    MasteryRecord evidence = record(48, 3, history(68, 48, "Approved result"));
    when(mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(31L))
        .thenReturn(List.of(evidence));
    when(diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(31L)).thenReturn(List.of());

    LearningProfileService.LearningProfileResponse profile = service.forTutor(7L, 31L);

    assertTrue(
        profile.findings().stream()
            .anyMatch(item -> item.type() == LearningProfileService.FindingType.REPEATED_WEAKNESS));
    assertTrue(
        profile.findings().stream()
            .anyMatch(item -> item.type() == LearningProfileService.FindingType.REGRESSED));
    assertTrue(profile.findings().stream().allMatch(item -> !item.evidence().isEmpty()));
  }

  @Test
  void returnsAStableEmptyProfileWhenNoApprovedHistoryExists() {
    when(mastery.findProfileRecordsByStudentProfileIdWithTopicAndHistory(31L))
        .thenReturn(List.of());
    when(diagnostics.findByStudentProfileIdOrderByCreatedAtDescIdDesc(31L)).thenReturn(List.of());

    LearningProfileService.LearningProfileResponse profile = service.forTutor(7L, 31L);

    assertEquals(List.of(), profile.strengths());
    assertEquals(List.of(), profile.growthAreas());
    assertEquals(List.of(), profile.improvements());
    assertEquals(4, profile.dimensions().size());
    assertTrue(
        profile.dimensions().stream()
            .allMatch(
                dimension -> dimension.evidenceCount() == 0 && dimension.evidence().isEmpty()));
    assertEquals(List.of(), profile.findings());
    assertEquals(LearningProfileService.Source.DETERMINISTIC, profile.source());
  }

  private MasteryRecord record(int score, int attempts, MasteryHistory history) {
    MasteryRecord record = mock(MasteryRecord.class);
    SyllabusTopic topic = mock(SyllabusTopic.class);
    when(topic.getId()).thenReturn(41L);
    when(topic.getName()).thenReturn("Adaptation");
    when(record.getSyllabusTopic()).thenReturn(topic);
    when(record.getId()).thenReturn(55L);
    when(record.getScore()).thenReturn(new BigDecimal(score + ".00"));
    when(record.getAttemptCount()).thenReturn(attempts);
    when(record.getMasteryStatus()).thenReturn(MasteryRecord.MasteryStatus.PRACTISING);
    when(record.getCalculatedAt()).thenReturn(LocalDateTime.of(2026, 8, 28, 10, 0));
    when(record.getHistory()).thenReturn(List.of(history));
    return record;
  }

  private MasteryHistory history(int previous, int next, String reason) {
    try {
      var constructor = MasteryHistory.class.getDeclaredConstructor();
      constructor.setAccessible(true);
      MasteryHistory history = constructor.newInstance();
      ReflectionTestUtils.setField(history, "previousScore", new BigDecimal(previous + ".00"));
      ReflectionTestUtils.setField(history, "newScore", new BigDecimal(next + ".00"));
      ReflectionTestUtils.setField(history, "reason", reason);
      ReflectionTestUtils.setField(history, "createdAt", LocalDateTime.of(2026, 8, 28, 10, 0));
      return history;
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
