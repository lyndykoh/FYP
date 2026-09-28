package com.fttranscendence.learning.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void learningMigrationsAreRepeatableOnPostgres() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("learning")
            .defaultSchema("learning")
            .locations("classpath:db/migration")
            .load();

    assertEquals(30, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);

    try (Connection connection = POSTGRES.createConnection("");
        ResultSet result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'learning' "
                        + "AND table_name IN ("
                        + "'tutor_classes', 'student_profiles', 'syllabus_topics', "
                        + "'questions', 'marking_components', 'question_keywords', "
                        + "'marking_component_keywords', "
                        + "'worksheets', 'worksheet_questions', 'worksheet_assignments', "
                        + "'mastery_records', 'mastery_history', 'mastery_approved_results', 'mastery_diagnostic_evidence', 'mastery_diagnostic_evidence_keywords', 'marking_review_status_projection', 'tutor_alerts', 'progress_reports', "
                        + "'class_topic_coverage', 'class_insight_settings', 'class_insight_snapshots', "
                        + "'class_insight_items', 'class_insight_feedback', 'class_insight_ranking_overrides', "
                        + "'class_insight_refresh_queue')")) {
      result.next();
      assertEquals(25, result.getInt(1));
    }

    try (Connection connection = POSTGRES.createConnection("");
        ResultSet result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM learning.syllabus_topics "
                        + "WHERE curriculum_version = 'MOE_PRIMARY_SCIENCE_2023'")) {
      result.next();
      assertEquals(28, result.getInt(1));
    }
  }
}
