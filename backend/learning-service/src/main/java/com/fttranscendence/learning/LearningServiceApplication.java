package com.fttranscendence.learning;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
public class LearningServiceApplication {

  /**
   * The application time source is a bean so time-sensitive read models can be exercised at
   * timezone boundaries without relying on the host clock.
   */
  @Bean
  Clock dashboardClock() {
    return Clock.systemUTC();
  }

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplate();
  }

  public static void main(String[] args) {
    SpringApplication.run(LearningServiceApplication.class, args);
  }
}
