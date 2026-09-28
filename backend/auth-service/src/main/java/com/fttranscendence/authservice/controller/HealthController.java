package com.fttranscendence.authservice.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/actuator")
public class HealthController {

  @GetMapping("/health")
  public Map<String, String> healthCheck() {
    Map<String, String> healthStatus = new HashMap<>();
    healthStatus.put("status", "UP");
    healthStatus.put("service", "auth-service");

    return healthStatus;
  }
}
