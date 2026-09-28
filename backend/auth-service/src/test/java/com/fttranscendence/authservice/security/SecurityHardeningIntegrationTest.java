package com.fttranscendence.authservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningIntegrationTest {
  private static final String LOCAL_ORIGIN = "http://localhost:3000";

  @Autowired private MockMvc mockMvc;

  @Test
  void healthIsPublicAndCarriesStandardSecurityHeaders() throws Exception {
    mockMvc
        .perform(get("/actuator/health").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().exists("Permissions-Policy"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"));
  }

  @Test
  void corsAcceptsOnlyConfiguredOrigins() throws Exception {
    mockMvc
        .perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Idempotency-Key"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN))
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    org.hamcrest.Matchers.containsString("Idempotency-Key")));

    mockMvc
        .perform(
            options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void validationRejectsOversizedPayloadsAndUnexpectedContentTypes() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\""
                        + "a".repeat(255)
                        + "@example.com\",\"password\":\"password\"}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.TEXT_PLAIN)
                .content("email=student@example.com&password=password"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void unknownRoutesRemainDenyByDefault() throws Exception {
    mockMvc.perform(get("/api/private")).andExpect(status().isUnauthorized());
  }
}
