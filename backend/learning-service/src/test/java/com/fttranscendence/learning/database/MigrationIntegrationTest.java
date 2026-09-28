package com.fttranscendence.learning.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

@SpringBootTest
class MigrationIntegrationTest {

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearClasses() {
    jdbcTemplate.update("DELETE FROM question_import_diagram_crops");
    jdbcTemplate.update("DELETE FROM question_import_candidates");
    jdbcTemplate.update("DELETE FROM question_import_source_pages");
    jdbcTemplate.update("DELETE FROM question_import_batches");
    jdbcTemplate.update("DELETE FROM mastery_diagnostic_evidence_keywords");
    jdbcTemplate.update("DELETE FROM mastery_diagnostic_evidence");
    jdbcTemplate.update("DELETE FROM mastery_approved_results");
    jdbcTemplate.update("DELETE FROM marking_review_status_projection");
    jdbcTemplate.update("DELETE FROM class_insight_feedback");
    jdbcTemplate.update("DELETE FROM class_insight_items");
    jdbcTemplate.update("DELETE FROM class_insight_snapshots");
    jdbcTemplate.update("DELETE FROM class_insight_ranking_overrides");
    jdbcTemplate.update("DELETE FROM class_insight_refresh_queue");
    jdbcTemplate.update("DELETE FROM class_insight_settings");
    jdbcTemplate.update("DELETE FROM class_topic_coverage");
    jdbcTemplate.update("DELETE FROM progress_reports");
    jdbcTemplate.update("DELETE FROM tutor_alerts");
    jdbcTemplate.update("DELETE FROM mastery_history");
    jdbcTemplate.update("DELETE FROM mastery_records");
    jdbcTemplate.update("DELETE FROM worksheet_assignments");
    jdbcTemplate.update("DELETE FROM worksheet_questions");
    jdbcTemplate.update("DELETE FROM worksheets");
    jdbcTemplate.update("DELETE FROM question_keywords");
    jdbcTemplate.update("DELETE FROM marking_component_keywords");
    jdbcTemplate.update("DELETE FROM marking_components");
    jdbcTemplate.update("DELETE FROM questions");
    jdbcTemplate.update("DELETE FROM tutor_notes");
    jdbcTemplate.update("DELETE FROM class_memberships");
    jdbcTemplate.update("DELETE FROM student_profiles");
    jdbcTemplate.update("DELETE FROM tutor_classes");
  }

  @Test
  void migrationCreatesClassAndScheduleTablesAndCanBeRepeated() {
    assertEquals(1, tableCount("learning_schema_metadata"));
    assertEquals(1, tableCount("tutor_classes"));
    assertEquals(1, tableCount("class_schedules"));
    assertEquals(1, tableCount("student_profiles"));
    assertEquals(1, tableCount("class_memberships"));
    assertEquals(1, tableCount("syllabus_topics"));
    assertEquals(1, tableCount("questions"));
    assertEquals(1, tableCount("marking_components"));
    assertEquals(1, tableCount("marking_component_keywords"));
    assertEquals(1, tableCount("question_keywords"));
    assertEquals(1, tableCount("worksheets"));
    assertEquals(1, tableCount("worksheet_questions"));
    assertEquals(1, tableCount("worksheet_assignments"));
    assertEquals(1, tableCount("mastery_records"));
    assertEquals(1, tableCount("mastery_history"));
    assertEquals(1, tableCount("mastery_approved_results"));
    assertEquals(1, tableCount("mastery_diagnostic_evidence"));
    assertEquals(1, tableCount("mastery_diagnostic_evidence_keywords"));
    assertEquals(1, tableCount("marking_review_status_projection"));
    assertEquals(1, tableCount("tutor_alerts"));
    assertEquals(1, tableCount("progress_reports"));
    assertEquals(1, tableCount("class_topic_coverage"));
    assertEquals(1, tableCount("class_insight_settings"));
    assertEquals(1, tableCount("class_insight_snapshots"));
    assertEquals(1, tableCount("class_insight_items"));
    assertEquals(1, tableCount("class_insight_feedback"));
    assertEquals(1, tableCount("class_insight_ranking_overrides"));
    assertEquals(1, tableCount("class_insight_refresh_queue"));
    assertEquals(1, tableCount("tutor_notes"));
    assertEquals(1, tableCount("question_import_diagram_crops"));
    assertEquals("30", flyway.info().current().getVersion().getVersion());
    assertEquals(30, versionedMigrationCount());
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' "
                + "AND LOWER(TABLE_NAME) = 'mastery_diagnostic_evidence' AND LOWER(COLUMN_NAME) = 'mistake_type'",
            Integer.class));

    long appliedBefore = versionedMigrationCount();
    flyway.migrate();

    assertEquals(appliedBefore, versionedMigrationCount());
    assertEquals(1, tableCount("tutor_classes"));
    assertEquals(
        28, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM syllabus_topics", Integer.class));
    assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class));
    assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM worksheets", Integer.class));
  }

  @Test
  void migrationEnforcesOwnershipUniquenessStatusAndScheduleConstraints() {
    long classId = insertClass(101L, "P5 Science", "p5 science", "ACTIVE");

    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertClass(101L, "Another display name", "p5 science", "ACTIVE"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertClass(0L, "Invalid Owner", "invalid owner", "ACTIVE"));
    assertThrows(
        DataIntegrityViolationException.class,
        () -> insertClass(202L, "Invalid Status", "invalid status", "ARCHIVED"));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO class_schedules "
                    + "(class_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                classId,
                "MONDAY",
                LocalTime.of(16, 0),
                LocalTime.of(15, 0)));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO class_schedules "
                    + "(class_id, day_of_week, start_time, end_time) VALUES (?, ?, ?, ?)",
                Long.MAX_VALUE,
                "MONDAY",
                LocalTime.of(15, 0),
                LocalTime.of(16, 0)));
  }

  @Test
  void productionConfigurationRequiresExternalCredentialsAndValidationMode() throws IOException {
    Properties properties = loadProductionProperties();

    assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertRequiredPlaceholder(properties, "spring.datasource.url", "${LEARNING_DB_URL}");
    assertRequiredPlaceholder(properties, "spring.datasource.username", "${LEARNING_DB_USERNAME}");
    assertRequiredPlaceholder(properties, "spring.datasource.password", "${LEARNING_DB_PASSWORD}");
    assertEquals(
        "${LEARNING_DB_SCHEMA:learning}",
        properties.getProperty("spring.datasource.hikari.schema"));
    assertEquals(
        "${LEARNING_DB_SCHEMA:learning}",
        properties.getProperty("spring.jpa.properties.hibernate.default_schema"));
    assertEquals(
        "${LEARNING_DB_SCHEMA:learning}", properties.getProperty("spring.flyway.default-schema"));
    assertRequiredPlaceholder(properties, "jwt.secret", "${JWT_SECRET}");
    assertRequiredPlaceholder(
        properties, "learning.marking-sync-key", "${LEARNING_MARKING_SYNC_KEY}");
  }

  private long insertClass(
      long tutorId, String className, String normalizedClassName, String status) {
    jdbcTemplate.update(
        "INSERT INTO tutor_classes "
            + "(tutor_id, class_name, normalized_class_name, subject, class_level, status) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        tutorId,
        className,
        normalizedClassName,
        "Science",
        "P5",
        status);
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tutor_classes WHERE tutor_id = ? AND normalized_class_name = ?",
            Long.class,
            tutorId,
            normalizedClassName);
    if (id == null) {
      throw new IllegalStateException("Inserted class was not found");
    }
    return id;
  }

  private int tableCount(String tableName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                + "WHERE TABLE_SCHEMA = 'PUBLIC' AND LOWER(TABLE_NAME) = ?",
            Integer.class,
            tableName);
    return count == null ? 0 : count;
  }

  private long versionedMigrationCount() {
    return java.util.Arrays.stream(flyway.info().applied())
        .filter(migration -> migration.getVersion() != null)
        .count();
  }

  private Properties loadProductionProperties() throws IOException {
    Path propertiesPath =
        Path.of(System.getProperty("basedir"), "src/main/resources/application.properties");
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(propertiesPath)) {
      properties.load(input);
    }
    return properties;
  }

  private void assertRequiredPlaceholder(
      Properties properties, String propertyName, String expectedPlaceholder) {
    String configuredValue = properties.getProperty(propertyName);
    assertEquals(expectedPlaceholder, configuredValue);
    assertThrows(
        IllegalArgumentException.class,
        () -> new MockEnvironment().resolveRequiredPlaceholders(configuredValue));
  }
}
