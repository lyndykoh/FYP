package com.fttranscendence.authservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import com.fttranscendence.authservice.repository.UserRepository;
import com.fttranscendence.authservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JwtService jwtService;

  @BeforeEach
  void setUpUsers() {
    userRepository.deleteAll();
    userRepository.save(user("tutor@example.com", "Test Tutor", UserRole.TUTOR));
    userRepository.save(user("student@example.com", "Test Student", UserRole.STUDENT));
  }

  @Test
  void tutorAndStudentLoginReceiveRoleBoundTokens() throws Exception {
    String tutorToken = login("tutor@example.com", UserRole.TUTOR);
    String studentToken = login("student@example.com", UserRole.STUDENT);

    assertEquals(UserRole.TUTOR, jwtService.extractRole(tutorToken));
    assertEquals(UserRole.STUDENT, jwtService.extractRole(studentToken));
  }

  @Test
  void publicRegistrationRejectsTutorPrivilegeEscalationAndUnknownRoles() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("new-tutor@example.com", "TUTOR", "StrongPassword1!")))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value("Tutor accounts cannot be created through public registration"));

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("parent@example.com", "PARENT", "StrongPassword1!")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registrationRejectsWeakPasswordsAndDuplicateNormalizedEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("new-student@example.com", "STUDENT", "password")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(" STUDENT@EXAMPLE.COM ", "STUDENT", "StrongPassword1!")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Email already registered"));
  }

  @Test
  void publicStudentRegistrationNormalizesIdentityAndHashesPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registrationJson(" NEW.STUDENT@EXAMPLE.COM ", "STUDENT", "StrongPassword1!")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new.student@example.com"))
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.token").isNotEmpty());

    User saved = userRepository.findByEmail("new.student@example.com").orElseThrow();
    assertEquals(UserRole.STUDENT, saved.getRole());
    org.junit.jupiter.api.Assertions.assertTrue(
        passwordEncoder.matches("StrongPassword1!", saved.getPassword()));
  }

  @Test
  void tutorDirectoryReturnsOnlyStudentAccountsAndRejectsOtherCallers() throws Exception {
    userRepository.save(user("student.two@example.com", "Another Student", UserRole.STUDENT));
    String tutorToken = login("tutor@example.com", UserRole.TUTOR);
    String studentToken = login("student@example.com", UserRole.STUDENT);

    mockMvc
        .perform(get("/api/auth/tutor/students").header("Authorization", "Bearer " + tutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("student.two@example.com"))
        .andExpect(jsonPath("$[0].fullName").value("Another Student"))
        .andExpect(jsonPath("$[1].email").value("student@example.com"))
        .andExpect(jsonPath("$[?(@.email == 'tutor@example.com')]").isEmpty());

    mockMvc
        .perform(
            get("/api/auth/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .param("search", "ANOTHER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("student.two@example.com"))
        .andExpect(jsonPath("$.length()").value(1));
    mockMvc
        .perform(
            get("/api/auth/tutor/students")
                .header("Authorization", "Bearer " + tutorToken)
                .param("search", "student.two@"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fullName").value("Another Student"));

    mockMvc.perform(get("/api/auth/tutor/students")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/auth/tutor/students").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isForbidden());
  }

  private String login(String email, UserRole expectedRole) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                {"email":"%s","password":"StrongPassword1!"}
                """
                        .formatted(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value(expectedRole.name()))
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
  }

  private User user(String email, String fullName, UserRole role) {
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode("StrongPassword1!"));
    user.setFullName(fullName);
    user.setRole(role);
    return user;
  }

  private String registrationJson(String email, String role, String password) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "fullName": "New Student",
          "role": "%s"
        }
        """
        .formatted(email, password, role);
  }
}
