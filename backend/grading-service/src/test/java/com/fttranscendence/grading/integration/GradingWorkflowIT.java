package com.fttranscendence.grading.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GradingWorkflowIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void contextFreeOcrUploadIsRetiredInFavorOfTheProtectedDocumentFlow() throws Exception {
    mockMvc
        .perform(
            multipart("/api/grading/ocr")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT"))
                .file("file", "page-bytes".getBytes()))
        .andExpect(status().isForbidden());
  }

  private String bearerToken(String role) {
    String secret = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
    String token =
        Jwts.builder()
            .setSubject(role.toLowerCase() + "@example.com")
            .claim("role", role)
            .claim("userId", 101L)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(
                Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
    return "Bearer " + token;
  }
}
