// src/main/java/com/fttranscendence/authservice/service/AuthService.java
package com.fttranscendence.authservice.service;

import com.fttranscendence.authservice.dto.AuthRequest;
import com.fttranscendence.authservice.dto.AuthResponse;
import com.fttranscendence.authservice.dto.RegisterRequest;
import com.fttranscendence.authservice.dto.StudentDirectoryResponse;
import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import com.fttranscendence.authservice.repository.UserRepository;
import com.fttranscendence.authservice.security.JwtService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
  private static final String EMAIL_ALREADY_REGISTERED_MESSAGE = "Email already registered";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  public AuthResponse register(RegisterRequest request) {
    verifyPublicRegistrationRole(request);

    String normalizedEmail = normalizeEmail(request.getEmail());
    rejectExistingEmail(normalizedEmail);

    User student = createStudentAccount(request, normalizedEmail);
    saveNewAccount(student);

    return createAuthenticationResponse(student);
  }

  public AuthResponse login(AuthRequest request) {
    String normalizedEmail = normalizeEmail(request.getEmail());
    authenticateCredentials(normalizedEmail, request.getPassword());

    User authenticatedUser = findAuthenticatedUser(normalizedEmail);

    return createAuthenticationResponse(authenticatedUser);
  }

  /**
   * Account-role filtering remains in the service that owns identities. This intentionally exposes
   * no credentials or broader user-directory fields.
   */
  public List<StudentDirectoryResponse> listStudentAccounts(String search) {
    String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    List<User> studentAccounts =
        userRepository.findAllByRoleOrderByFullnameAscEmailAsc(UserRole.STUDENT);

    return studentAccounts.stream()
        .filter(student -> matchesDirectorySearch(student, normalizedSearch))
        .map(StudentDirectoryResponse::from)
        .toList();
  }

  private void verifyPublicRegistrationRole(RegisterRequest request) {
    if (request.getRole() == UserRole.STUDENT) {
      return;
    }

    throw new ResponseStatusException(
        HttpStatus.FORBIDDEN, "Tutor accounts cannot be created through public registration");
  }

  private void rejectExistingEmail(String normalizedEmail) {
    if (!userRepository.existsByEmail(normalizedEmail)) {
      return;
    }

    throw emailAlreadyRegistered();
  }

  private User createStudentAccount(RegisterRequest request, String normalizedEmail) {
    User student = new User();
    student.setEmail(normalizedEmail);
    student.setPassword(passwordEncoder.encode(request.getPassword()));
    student.setFullName(request.getFullName().trim());
    student.setRole(UserRole.STUDENT);

    return student;
  }

  private void saveNewAccount(User user) {
    try {
      userRepository.save(user);
    } catch (DataIntegrityViolationException exception) {
      throw emailAlreadyRegistered();
    }
  }

  private void authenticateCredentials(String email, String password) {
    try {
      authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    } catch (AuthenticationException exception) {
      throw invalidCredentials();
    }
  }

  private User findAuthenticatedUser(String normalizedEmail) {
    return userRepository.findByEmail(normalizedEmail).orElseThrow(this::invalidCredentials);
  }

  private AuthResponse createAuthenticationResponse(User user) {
    String jwtToken = jwtService.generateToken(user);

    return AuthResponse.builder()
        .token(jwtToken)
        .email(user.getEmail())
        .fullName(user.getFullName())
        .role(user.getRole())
        .build();
  }

  private boolean matchesDirectorySearch(User student, String normalizedSearch) {
    if (normalizedSearch.isEmpty()) {
      return true;
    }

    boolean nameMatches = student.getFullName().toLowerCase(Locale.ROOT).contains(normalizedSearch);
    if (nameMatches) {
      return true;
    }

    return student.getEmail().toLowerCase(Locale.ROOT).contains(normalizedSearch);
  }

  private ResponseStatusException emailAlreadyRegistered() {
    return new ResponseStatusException(HttpStatus.CONFLICT, EMAIL_ALREADY_REGISTERED_MESSAGE);
  }

  private ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
