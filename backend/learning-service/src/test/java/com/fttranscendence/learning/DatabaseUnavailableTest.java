package com.fttranscendence.learning;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class DatabaseUnavailableTest {

  @Test
  void startupFailsFastWhenTheConfiguredDatabaseIsUnavailable() {
    SpringApplication application = new SpringApplication(LearningServiceApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);

    assertThrows(
        Exception.class,
        () ->
            application.run(
                "--spring.main.banner-mode=off",
                "--spring.main.log-startup-info=false",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unavailable",
                "--spring.datasource.username=unavailable",
                "--spring.datasource.password=unavailable",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.hikari.connection-timeout=250",
                "--spring.flyway.connect-retries=0",
                "--spring.flyway.connect-retries-interval=1",
                "--jwt.secret=test-secret-key-that-is-at-least-thirty-two-bytes-long"));
  }
}
