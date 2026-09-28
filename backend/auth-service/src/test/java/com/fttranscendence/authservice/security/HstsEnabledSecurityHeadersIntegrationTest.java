package com.fttranscendence.authservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Confirms TLS deployments can explicitly enable HSTS without hard-coding a deployment domain. */
@SpringBootTest(properties = "security.headers.hsts-enabled=true")
@AutoConfigureMockMvc
class HstsEnabledSecurityHeadersIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void secureRequestsEmitHstsWhenEnabled() throws Exception {
    mockMvc
        .perform(get("/actuator/health").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().exists("Strict-Transport-Security"));
  }
}
