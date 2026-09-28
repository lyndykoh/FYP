package com.fttranscendence.grading.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

@SpringBootTest
class MigrationIntegrationTest {

  @Autowired private Flyway flyway;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearSubmissions() {
    jdbcTemplate.update("DELETE FROM mastery_sync_outbox");
    jdbcTemplate.update("DELETE FROM mistake_records");
    jdbcTemplate.update("DELETE FROM answer_reviews");
    jdbcTemplate.update("DELETE FROM submission_pages");
    jdbcTemplate.update("DELETE FROM submission_missing_keywords");
    jdbcTemplate.update("DELETE FROM submissions");
    jdbcTemplate.update("DELETE FROM submission_documents");
  }

  @Test
  void cleanDatabaseContainsTheFlywayManagedGradingSchema() {
    assertEquals(1, tableCount("submissions"));
    assertEquals(1, tableCount("submission_missing_keywords"));
    assertEquals(1, tableCount("submission_documents"));
    assertEquals(1, tableCount("submission_pages"));
    assertEquals(1, tableCount("answer_reviews"));
    assertEquals(1, tableCount("mistake_records"));
    assertEquals(1, tableCount("mastery_sync_outbox"));
    assertEquals(11, versionedMigrationCount());
    assertEquals("12", flyway.info().current().getVersion().getVersion());
  }

  @Test
  void applyingMigrationsAgainIsANoOp() {
    long appliedBefore = versionedMigrationCount();

    flyway.migrate();

    assertEquals(appliedBefore, versionedMigrationCount());
    assertEquals(1, tableCount("submissions"));
    assertEquals(1, tableCount("submission_missing_keywords"));
    assertEquals(1, tableCount("submission_documents"));
    assertEquals(1, tableCount("submission_pages"));
    assertEquals(1, tableCount("answer_reviews"));
    assertEquals(1, tableCount("mistake_records"));
  }

  @Test
  void v3PreservesExistingAiDiagnosticsAsPendingLegacyRecords() throws Exception {
    String databaseUrl =
        "jdbc:h2:mem:grading-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway versionTwo =
        Flyway.configure()
            .dataSource(databaseUrl, "sa", "")
            .schemas("PUBLIC")
            .defaultSchema("PUBLIC")
            .locations("classpath:db/migration")
            .target("2")
            .load();
    assertEquals(2, versionTwo.migrate().migrationsExecuted);

    try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "")) {
      connection
          .createStatement()
          .executeUpdate(
              "INSERT INTO submissions ("
                  + "student_id, question_id, student_answer, correctness, feedback"
                  + ") VALUES (42, 'legacy-question', 'Legacy answer', "
                  + "'Partially Correct', 'Legacy AI feedback')");
    }

    Flyway latest =
        Flyway.configure()
            .dataSource(databaseUrl, "sa", "")
            .schemas("PUBLIC")
            .defaultSchema("PUBLIC")
            .locations("classpath:db/migration")
            .load();
    assertEquals(9, latest.migrate().migrationsExecuted);

    try (Connection connection = DriverManager.getConnection(databaseUrl, "sa", "");
        ResultSet result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT legacy_question_reference, extracted_answer, "
                        + "ai_suggested_outcome, ai_suggested_feedback, "
                        + "review_status, legacy_record, approved_marks "
                        + "FROM submissions WHERE student_id = 42")) {
      assertEquals(true, result.next());
      assertEquals("legacy-question", result.getString("legacy_question_reference"));
      assertEquals("Legacy answer", result.getString("extracted_answer"));
      assertEquals("Partially Correct", result.getString("ai_suggested_outcome"));
      assertEquals("Legacy AI feedback", result.getString("ai_suggested_feedback"));
      assertEquals("PENDING_REVIEW", result.getString("review_status"));
      assertEquals(true, result.getBoolean("legacy_record"));
      assertEquals(null, result.getBigDecimal("approved_marks"));
    }
  }

  @Test
  void missingKeywordsAreRemovedWhenTheirSubmissionIsDeleted() {
    jdbcTemplate.update(
        "INSERT INTO submissions ("
            + "student_id, legacy_question_reference, ai_suggested_outcome, legacy_record"
            + ") VALUES (?, ?, ?, TRUE)",
        42L,
        "question-1",
        "Incorrect");
    Long submissionId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM submissions WHERE student_id = ?", Long.class, 42L);
    jdbcTemplate.update(
        "INSERT INTO submission_missing_keywords (submission_id, keyword) VALUES (?, ?)",
        submissionId,
        "conduction");

    jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", submissionId);

    Integer keywordCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM submission_missing_keywords WHERE submission_id = ?",
            Integer.class,
            submissionId);
    assertEquals(0, keywordCount);
  }

  @Test
  void productionConfigurationRequiresDatabaseAndAiEnvironmentValues() throws IOException {
    Properties properties = loadProductionProperties();

    assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertRequiredPlaceholder(properties, "spring.datasource.url", "${GRADING_DB_URL}");
    assertRequiredPlaceholder(properties, "spring.datasource.username", "${GRADING_DB_USERNAME}");
    assertRequiredPlaceholder(properties, "spring.datasource.password", "${GRADING_DB_PASSWORD}");
    assertEquals(
        "${GRADING_DB_SCHEMA:grading}",
        properties.getProperty("spring.jpa.properties.hibernate.default_schema"));
    assertEquals(
        "${GRADING_DB_SCHEMA:grading}", properties.getProperty("spring.flyway.default-schema"));
    assertRequiredPlaceholder(properties, "jwt.secret", "${JWT_SECRET}");
    assertRequiredPlaceholder(properties, "ai.engine.url", "${AI_ENGINE_URL}");
    assertRequiredPlaceholder(properties, "ai.engine.model", "${AI_ENGINE_MODEL}");
    assertRequiredPlaceholder(properties, "ai.engine.api-key", "${AI_ENGINE_API_KEY}");
    assertRequiredPlaceholder(properties, "ai.vision.model", "${AI_VISION_MODEL}");
    assertRequiredPlaceholder(
        properties, "learning.service.sync-key", "${LEARNING_MARKING_SYNC_KEY}");
    assertEquals(
        "${DOCUMENT_STORAGE_ROOT:./data/submissions}",
        properties.getProperty("document.storage.root"));
    assertEquals(
        "${DOCUMENT_STORAGE_MAX_FILE_SIZE_BYTES:20971520}",
        properties.getProperty("document.storage.max-file-size-bytes"));
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
