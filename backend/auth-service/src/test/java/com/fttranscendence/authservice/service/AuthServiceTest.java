package com.fttranscendence.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fttranscendence.authservice.dto.AuthRequest;
import com.fttranscendence.authservice.dto.RegisterRequest;
import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import com.fttranscendence.authservice.repository.UserRepository;
import com.fttranscendence.authservice.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private AuthenticationManager authenticationManager;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
  }

  @Test
  void registerEncodesPasswordPersistsUserAndReturnsToken() {
    RegisterRequest request = registrationRequest();
    when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
    when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
    when(jwtService.generateToken(any(User.class))).thenReturn("signed-token");

    var response = authService.register(request);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User persisted = userCaptor.getValue();
    assertEquals("encoded-password", persisted.getPassword());
    assertEquals(request.getEmail(), persisted.getEmail());
    assertEquals(request.getFullName(), persisted.getFullName());
    assertEquals(request.getRole(), persisted.getRole());
    assertEquals("signed-token", response.getToken());
    assertEquals(request.getEmail(), response.getEmail());
  }

  @Test
  void registerRejectsAnExistingEmailBeforeEncodingOrSaving() {
    RegisterRequest request = registrationRequest();
    when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.register(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(passwordEncoder, never()).encode(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void registerMapsAConcurrentUniqueConstraintFailureToConflict() {
    RegisterRequest request = registrationRequest();
    when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
    when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
    when(userRepository.save(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.register(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(jwtService, never()).generateToken(any(User.class));
  }

  @Test
  void publicRegistrationRejectsTutorPrivilegeEscalation() {
    RegisterRequest request = registrationRequest();
    request.setRole(UserRole.TUTOR);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.register(request));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verify(userRepository, never()).save(any());
    verify(passwordEncoder, never()).encode(any());
  }

  @Test
  void loginAuthenticatesAndReturnsThePersistedUser() {
    AuthRequest request = loginRequest();
    User user = user();
    when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
    when(jwtService.generateToken(user)).thenReturn("signed-token");

    var response = authService.login(request);

    verify(authenticationManager).authenticate(any());
    assertEquals("signed-token", response.getToken());
    assertEquals(user.getEmail(), response.getEmail());
    assertEquals(user.getFullName(), response.getFullName());
    assertEquals(user.getRole(), response.getRole());
  }

  @Test
  void loginRejectsBadCredentialsWithoutLookingUpTheUser() {
    AuthRequest request = loginRequest();
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("bad credentials"));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    verify(userRepository, never()).findByEmail(any());
    verify(jwtService, never()).generateToken(any(User.class));
  }

  @Test
  void loginRejectsAnAuthenticatedEmailMissingFromTheDatabase() {
    AuthRequest request = loginRequest();
    when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.login(request));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    verify(jwtService, never()).generateToken(any(User.class));
  }

  private RegisterRequest registrationRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("tutor@example.com");
    request.setPassword("StrongPassword1!");
    request.setFullName("Test Tutor");
    request.setRole(UserRole.STUDENT);
    return request;
  }

  private AuthRequest loginRequest() {
    AuthRequest request = new AuthRequest();
    request.setEmail("tutor@example.com");
    request.setPassword("StrongPassword1!");
    return request;
  }

  private User user() {
    User user = new User();
    user.setEmail("tutor@example.com");
    user.setPassword("encoded-password");
    user.setFullName("Test Tutor");
    user.setRole(UserRole.TUTOR);
    return user;
  }
}
