package com.fttranscendence.authservice.service;

import com.fttranscendence.authservice.model.User;
import com.fttranscendence.authservice.model.UserRole;
import com.fttranscendence.authservice.repository.UserRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TutorBootstrapRunner implements ApplicationRunner {

  private static final int MAXIMUM_EMAIL_LENGTH = 254;
  private static final int MINIMUM_FULL_NAME_LENGTH = 2;
  private static final int MAXIMUM_FULL_NAME_LENGTH = 100;
  private static final int MINIMUM_PASSWORD_LENGTH = 12;
  private static final int MAXIMUM_PASSWORD_LENGTH = 128;

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern STRONG_PASSWORD =
      Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;
  private final String fullName;

  public TutorBootstrapRunner(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${bootstrap.tutor.email:}") String email,
      @Value("${bootstrap.tutor.password:}") String password,
      @Value("${bootstrap.tutor.full-name:}") String fullName) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
    this.fullName = fullName;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!hasAnyBootstrapValue()) {
      return;
    }

    requireCompleteBootstrapConfiguration();

    String normalizedEmail = normalizeEmail(email);
    String normalizedFullName = fullName.trim();
    validateBootstrapValues(normalizedEmail, normalizedFullName);

    userRepository
        .findByEmail(normalizedEmail)
        .ifPresentOrElse(
            this::validateExistingAccount, () -> createTutor(normalizedEmail, normalizedFullName));
  }

  private boolean hasAnyBootstrapValue() {
    return StringUtils.hasText(email)
        || StringUtils.hasText(password)
        || StringUtils.hasText(fullName);
  }

  private void requireCompleteBootstrapConfiguration() {
    boolean hasCompleteConfiguration =
        StringUtils.hasText(email)
            && StringUtils.hasText(password)
            && StringUtils.hasText(fullName);

    if (!hasCompleteConfiguration) {
      throw new IllegalStateException("All Tutor bootstrap values must be supplied together");
    }
  }

  private String normalizeEmail(String bootstrapEmail) {
    return bootstrapEmail.trim().toLowerCase(Locale.ROOT);
  }

  private void validateBootstrapValues(String normalizedEmail, String normalizedFullName) {
    validateEmail(normalizedEmail);
    validateFullName(normalizedFullName);
    validatePassword();
  }

  private void validateEmail(String normalizedEmail) {
    boolean exceedsMaximumLength = normalizedEmail.length() > MAXIMUM_EMAIL_LENGTH;
    boolean hasInvalidFormat = !EMAIL_PATTERN.matcher(normalizedEmail).matches();

    if (exceedsMaximumLength || hasInvalidFormat) {
      throw new IllegalStateException("BOOTSTRAP_TUTOR_EMAIL is invalid");
    }
  }

  private void validateFullName(String normalizedFullName) {
    boolean isTooShort = normalizedFullName.length() < MINIMUM_FULL_NAME_LENGTH;
    boolean isTooLong = normalizedFullName.length() > MAXIMUM_FULL_NAME_LENGTH;

    if (isTooShort || isTooLong) {
      throw new IllegalStateException("BOOTSTRAP_TUTOR_FULL_NAME must contain 2 to 100 characters");
    }
  }

  private void validatePassword() {
    boolean isTooShort = password.length() < MINIMUM_PASSWORD_LENGTH;
    boolean isTooLong = password.length() > MAXIMUM_PASSWORD_LENGTH;

    if (isTooShort || isTooLong) {
      throw new IllegalStateException("BOOTSTRAP_TUTOR_PASSWORD does not meet the password policy");
    }

    boolean meetsComplexityRequirements = STRONG_PASSWORD.matcher(password).matches();
    if (!meetsComplexityRequirements) {
      throw new IllegalStateException("BOOTSTRAP_TUTOR_PASSWORD does not meet the password policy");
    }
  }

  private void validateExistingAccount(User existingUser) {
    if (existingUser.getRole() != UserRole.TUTOR) {
      throw new IllegalStateException("Tutor bootstrap email belongs to a non-Tutor account");
    }
  }

  private void createTutor(String normalizedEmail, String normalizedFullName) {
    User tutor = new User();
    tutor.setEmail(normalizedEmail);
    tutor.setPassword(passwordEncoder.encode(password));
    tutor.setFullName(normalizedFullName);
    tutor.setRole(UserRole.TUTOR);

    userRepository.save(tutor);
  }
}
