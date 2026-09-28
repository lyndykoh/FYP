package com.fttranscendence.authservice.database;

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
  void authMigrationIsRepeatableOnPostgres() throws Exception {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas("auth")
            .defaultSchema("auth")
            .locations("classpath:db/migration")
            .load();

    assertEquals(1, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);

    try (Connection connection = POSTGRES.createConnection("");
        ResultSet result =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'auth' AND table_name = 'users'")) {
      result.next();
      assertEquals(1, result.getInt(1));
    }
  }
}
