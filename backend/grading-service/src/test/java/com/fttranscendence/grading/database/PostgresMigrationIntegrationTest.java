package com.fttranscendence.grading.database;

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
  void gradingMigrationIsRepeatableOnPostgres() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("grading")
            .defaultSchema("grading")
            .locations("classpath:db/migration")
            .load();

    assertEquals(11, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);

    try (Connection connection = POSTGRES.createConnection("");
        ResultSet result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'grading' "
                        + "AND table_name IN ("
                        + "'submissions', 'submission_documents', 'submission_pages', 'answer_reviews',"
                        + " 'mistake_records'"
                        + ")")) {
      result.next();
      assertEquals(5, result.getInt(1));
    }
  }
}
