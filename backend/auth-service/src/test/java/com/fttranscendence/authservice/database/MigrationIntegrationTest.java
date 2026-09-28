package com.fttranscendence.authservice.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
  void clearUsers() {
    jdbcTemplate.update("DELETE FROM users");
  }

  @Test
  void cleanDatabaseIsCreatedOnlyByTheVersionedMigration() {
    assertEquals(1, tableCount("users"));
    assertEquals(1, versionedMigrationCount());
    assertEquals("1", flyway.info().current().getVersion().getVersion());
  }

  @Test
  void applyingMigrationsAgainIsANoOp() {
    long appliedBefore = versionedMigrationCount();

    flyway.migrate();

    assertEquals(appliedBefore, versionedMigrationCount());
    assertEquals(1, tableCount("users"));
  }

  @Test
  void usersMigrationEnforcesUniqueEmailAddresses() {
    jdbcTemplate.update(
        "INSERT INTO users (email, password, fullname, role) VALUES (?, ?, ?, ?)",
        "duplicate@example.com",
        "encoded-password",
        "First User",
        "STUDENT");

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                "INSERT INTO users (email, password, fullname, role) VALUES (?, ?, ?, ?)",
                "duplicate@example.com",
                "another-encoded-password",
                "Second User",
                "TUTOR"));
  }

  @Test
  void productionConfigurationRequiresExternalCredentialsAndDisablesSchemaMutation()
      throws IOException {
    Properties properties = loadProductionProperties();

    assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertRequiredPlaceholder(properties, "spring.datasource.url", "${AUTH_DB_URL}");
    assertRequiredPlaceholder(properties, "spring.datasource.username", "${AUTH_DB_USERNAME}");
    assertRequiredPlaceholder(properties, "spring.datasource.password", "${AUTH_DB_PASSWORD}");
    assertEquals(
        "${AUTH_DB_SCHEMA:auth}",
        properties.getProperty("spring.jpa.properties.hibernate.default_schema"));
    assertEquals("${AUTH_DB_SCHEMA:auth}", properties.getProperty("spring.flyway.default-schema"));
    assertRequiredPlaceholder(properties, "jwt.secret", "${JWT_SECRET}");
    assertRequiredPlaceholder(properties, "jwt.expiration", "${JWT_EXPIRATION_MS}");
    assertEquals("${BOOTSTRAP_TUTOR_EMAIL:}", properties.getProperty("bootstrap.tutor.email"));
    assertEquals(
        "${BOOTSTRAP_TUTOR_PASSWORD:}", properties.getProperty("bootstrap.tutor.password"));
    assertEquals(
        "${BOOTSTRAP_TUTOR_FULL_NAME:}", properties.getProperty("bootstrap.tutor.full-name"));
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
