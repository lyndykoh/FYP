package com.fttranscendence.learning.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fttranscendence.learning.alert.TutorAlert;
import com.fttranscendence.learning.alert.TutorAlertRepository;
import com.fttranscendence.learning.mastery.MasteryRecord;
import com.fttranscendence.learning.mastery.MasteryRecordRepository;
import com.fttranscendence.learning.report.ProgressReport;
import com.fttranscendence.learning.report.ProgressReportRepository;
import com.fttranscendence.learning.student.StudentProfile;
import com.fttranscendence.learning.student.StudentProfileRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LearningRecordsRepositoryTest {

  private static final long TUTOR_ID = 101L;

  @Autowired private StudentProfileRepository studentRepository;
  @Autowired private MasteryRecordRepository masteryRepository;
  @Autowired private TutorAlertRepository alertRepository;
  @Autowired private ProgressReportRepository reportRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearRecords() {
    jdbcTemplate.update("DELETE FROM progress_reports");
    jdbcTemplate.update("DELETE FROM tutor_alerts");
    jdbcTemplate.update("DELETE FROM mastery_history");
    jdbcTemplate.update("DELETE FROM mastery_records");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
  }

  @Test
  void masteryScoresHaveBoundariesHistoryAndOneRecordPerStudentTopic() {
    StudentProfile student = persistStudent("Mastery Student");
    var topic = topic(1L);
    MasteryRecord mastery = new MasteryRecord(student, topic);
    masteryRepository.save(mastery);
    entityManager.flush();

    mastery.updateScore(new BigDecimal("0.00"), 7001L, "First reviewed answer");
    mastery.updateScore(new BigDecimal("100.00"), 7002L, "Improved reviewed answer");
    entityManager.flush();
    entityManager.clear();

    MasteryRecord loaded =
        masteryRepository
            .findByStudentProfileIdAndSyllabusTopicId(student.getId(), topic.getId())
            .orElseThrow();
    assertEquals(new BigDecimal("100.00"), loaded.getScore());
    assertEquals(MasteryRecord.MasteryStatus.MASTERED, loaded.getMasteryStatus());
    assertEquals(2, loaded.getAttemptCount());
    assertEquals(2, loaded.getHistory().size());

    assertThrows(
        IllegalArgumentException.class,
        () -> loaded.updateScore(new BigDecimal("-0.01"), 7003L, "Invalid"));
    assertThrows(
        IllegalArgumentException.class,
        () -> loaded.updateScore(new BigDecimal("100.01"), 7003L, "Invalid"));

    MasteryRecord duplicate = new MasteryRecord(student, topic);
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          masteryRepository.save(duplicate);
          entityManager.flush();
        });
  }

  @Test
  void alertsAreTutorOwnedDeduplicatedAndHaveAnAuditableLifecycle() {
    StudentProfile student = persistStudent("Alert Student");
    TutorAlert alert =
        new TutorAlert(
            TUTOR_ID,
            student,
            null,
            TutorAlert.AlertType.WEAK_TOPIC,
            TutorAlert.Severity.WARNING,
            "student-" + student.getId() + "-topic-1",
            "Weak topic",
            "Review energy transfer.");
    alertRepository.save(alert);
    entityManager.flush();

    alert.acknowledge();
    alert.resolve(901L);
    entityManager.flush();
    assertEquals(TutorAlert.AlertStatus.RESOLVED, alert.getAlertStatus());
    assertThrows(IllegalStateException.class, alert::acknowledge);

    TutorAlert duplicate =
        new TutorAlert(
            TUTOR_ID,
            student,
            null,
            TutorAlert.AlertType.WEAK_TOPIC,
            TutorAlert.Severity.WARNING,
            alert.getDeduplicationKey(),
            "Duplicate",
            "Duplicate alert");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          alertRepository.save(duplicate);
          entityManager.flush();
        });

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new TutorAlert(
                202L,
                student,
                null,
                TutorAlert.AlertType.REPORT_READY,
                TutorAlert.Severity.INFO,
                "wrong-owner",
                "Wrong owner",
                "Invalid"));
  }

  @Test
  void finalReportsAreOwnedSnapshotsAndCannotBeEditedOrDeleteTheirStudent() {
    StudentProfile student = persistStudent("Report Student");
    ProgressReport report =
        new ProgressReport(
            TUTOR_ID,
            student,
            "progress-2026-01",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            "{\"score\": 72}");
    reportRepository.save(report);
    entityManager.flush();
    report.finalizeReport(TUTOR_ID);
    entityManager.flush();
    entityManager.clear();

    ProgressReport finalReport =
        reportRepository.findByIdAndTutorId(report.getId(), TUTOR_ID).orElseThrow();
    assertEquals(ProgressReport.ReportStatus.FINAL, finalReport.getReportStatus());
    assertThrows(IllegalStateException.class, () -> finalReport.updateSnapshot("{\"score\": 99}"));

    assertThrows(
        DataIntegrityViolationException.class,
        () -> jdbcTemplate.update("DELETE FROM student_profiles WHERE id = ?", student.getId()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ProgressReport(
                TUTOR_ID,
                student,
                "BAD",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                "snapshot"));
  }

  @Test
  void deletingAStudentCascadesItsMasteryHistoryAndAlerts() {
    StudentProfile student = persistStudent("Cascade Student");
    MasteryRecord mastery = new MasteryRecord(student, topic(1L));
    masteryRepository.save(mastery);
    entityManager.flush();
    mastery.updateScore(new BigDecimal("40.00"), 7101L, "Reviewed answer");

    TutorAlert alert =
        new TutorAlert(
            TUTOR_ID,
            student,
            mastery,
            TutorAlert.AlertType.WEAK_TOPIC,
            TutorAlert.Severity.WARNING,
            "cascade-" + student.getId(),
            "Weak topic",
            "Review the topic.");
    alertRepository.save(alert);
    entityManager.flush();

    jdbcTemplate.update("DELETE FROM student_profiles WHERE id = ?", student.getId());
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mastery_records WHERE student_profile_id = ?",
            Integer.class,
            student.getId()));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mastery_history WHERE mastery_record_id = ?",
            Integer.class,
            mastery.getId()));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tutor_alerts WHERE student_profile_id = ?",
            Integer.class,
            student.getId()));
  }

  private StudentProfile persistStudent(String name) {
    StudentProfile student = new StudentProfile();
    student.setTutorId(TUTOR_ID);
    student.setFullName(name);
    studentRepository.save(student);
    entityManager.flush();
    return student;
  }

  private com.fttranscendence.learning.syllabus.SyllabusTopic topic(long id) {
    return entityManager.find(com.fttranscendence.learning.syllabus.SyllabusTopic.class, id);
  }
}
