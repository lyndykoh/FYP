package com.fttranscendence.authservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

  private JwtService jwtService;
  private User user;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(
        jwtService, "secretKey", "test-secret-key-that-is-at-least-thirty-two-bytes-long");
    ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);

    user = new User();
    user.setEmail("student@example.com");
    user.setPassword("encoded-password");
    user.setFullName("Test Student");
    user.setRole(UserRole.STUDENT);
    user.setId(42L);
  }

  @Test
  void generatedTokenContainsTheUserEmailAndIsValidForThatUser() {
    String token = jwtService.generateToken(user);

    assertEquals(user.getEmail(), jwtService.extractEmail(token));
    assertEquals(42L, jwtService.extractUserId(token));
    assertTrue(jwtService.isTokenValid(token, user));
  }

  @Test
  void generatedTokenAlwaysCarriesTheValidatedUserRole() {
    String token = jwtService.generateToken(Map.of("role", "TUTOR", "tenant", "academy-1"), user);

    assertEquals(UserRole.STUDENT, jwtService.extractRole(token));
    assertEquals(
        "academy-1", jwtService.extractClaim(token, claims -> claims.get("tenant", String.class)));
  }

  @Test
  void tokenIsInvalidForAUserWithADifferentEmail() {
    String token = jwtService.generateToken(user);
    User otherUser = new User();
    otherUser.setEmail("someone-else@example.com");
    otherUser.setPassword("encoded-password");
    otherUser.setFullName("Someone Else");
    otherUser.setRole(UserRole.STUDENT);
    otherUser.setId(43L);

    assertFalse(jwtService.isTokenValid(token, otherUser));
  }

  @Test
  void tokenIsInvalidWhenThePersistedRoleDoesNotMatchTheClaim() {
    String token = jwtService.generateToken(user);
    User promotedUser = new User();
    promotedUser.setEmail(user.getEmail());
    promotedUser.setPassword("encoded-password");
    promotedUser.setFullName("Test Student");
    promotedUser.setRole(UserRole.TUTOR);
    promotedUser.setId(42L);

    assertFalse(jwtService.isTokenValid(token, promotedUser));
  }

  @Test
  void expiredTokenIsRejected() {
    ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1L);
    String token = jwtService.generateToken(user);

    assertFalse(jwtService.isTokenValid(token, user));
  }

  @Test
  void malformedTokenIsRejected() {
    assertFalse(jwtService.isTokenValid("not-a-jwt", user));
  }

  @Test
  void tokenCannotBeIssuedForAnUnpersistedIdentity() {
    user.setId(0L);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> jwtService.generateToken(user));
  }

  @Test
  void tokenIsInvalidWhenItsUserIdDoesNotMatchTheAccount() {
    String token = jwtService.generateToken(user);
    user.setId(99L);

    assertFalse(jwtService.isTokenValid(token, user));
  }

  @Test
  void rejectsPlaceholderAndTooShortSigningSecrets() {
    assertFalse(JwtService.isUsableSecret("change-me-to-a-long-enough-example-secret-value"));
    assertFalse(JwtService.isUsableSecret("too-short"));
    assertTrue(JwtService.isUsableSecret("a-real-secret-that-is-at-least-thirty-two-bytes"));

    ReflectionTestUtils.setField(
        jwtService, "secretKey", "change-me-to-a-long-enough-example-secret-value");
    assertThrows(IllegalArgumentException.class, jwtService::validateConfiguration);
  }
}
