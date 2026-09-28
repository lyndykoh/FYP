// src/main/java/com/fttranscendence/authservice/service/AuthUserDetailsService.java
package com.fttranscendence.authservice.service;

import com.fttranscendence.authservice.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {
  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    String normalizedEmail = normalizeEmail(email);

    return userRepository.findByEmail(normalizedEmail).orElseThrow(() -> userNotFound(email));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private UsernameNotFoundException userNotFound(String email) {
    return new UsernameNotFoundException("User not found with email: " + email);
  }
}
