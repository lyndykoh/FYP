package com.fttranscendence.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import com.fttranscendence.authservice.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TutorBootstrapRunnerTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  void isDisabledWhenNoBootstrapValuesAreConfigured() {
    runner("", "", "").run(null);

    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  void rejectsPartialOrWeakBootstrapConfiguration() {
    assertThrows(
        IllegalStateException.class, () -> runner("tutor@example.com", "", "Tutor User").run(null));
    assertThrows(
        IllegalStateException.class,
        () -> runner("tutor@example.com", "weak-password", "Tutor User").run(null));

    verifyNoInteractions(userRepository, passwordEncoder);
  }

  @Test
  void createsOneNormalizedTutorWithABcryptEncodedPassword() {
    when(userRepository.findByEmail("tutor@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("StrongPassword1!")).thenReturn("bcrypt-hash");

    runner(" Tutor@Example.com ", "StrongPassword1!", " Test Tutor ").run(null);

    ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(user.capture());
    assertEquals("tutor@example.com", user.getValue().getEmail());
    assertEquals("bcrypt-hash", user.getValue().getPassword());
    assertEquals("Test Tutor", user.getValue().getFullName());
    assertEquals(UserRole.TUTOR, user.getValue().getRole());
  }

  @Test
  void existingTutorIsNotModifiedAndStudentCollisionFailsClosed() {
    User tutor = new User();
    tutor.setRole(UserRole.TUTOR);
    when(userRepository.findByEmail("tutor@example.com")).thenReturn(Optional.of(tutor));

    runner("tutor@example.com", "StrongPassword1!", "Test Tutor").run(null);

    verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(passwordEncoder);

    User student = new User();
    student.setRole(UserRole.STUDENT);
    when(userRepository.findByEmail("student@example.com")).thenReturn(Optional.of(student));
    assertThrows(
        IllegalStateException.class,
        () -> runner("student@example.com", "StrongPassword1!", "Test Tutor").run(null));
  }

  private TutorBootstrapRunner runner(String email, String password, String fullName) {
    return new TutorBootstrapRunner(userRepository, passwordEncoder, email, password, fullName);
  }
}
