package com.fttranscendence.grading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GradingServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GradingServiceApplication.class, args);
  }
}
