package com.fttranscendence.authservice.controller;

import com.fttranscendence.authservice.dto.AuthRequest;
import com.fttranscendence.authservice.dto.AuthResponse;
import com.fttranscendence.authservice.dto.RegisterRequest;
import com.fttranscendence.authservice.dto.StudentDirectoryResponse;
import com.fttranscendence.authservice.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  /** Provides the Tutor-only account directory used by learning-service to validate enrolment. */
  @GetMapping("/tutor/students")
  public ResponseEntity<List<StudentDirectoryResponse>> studentDirectory(
      @RequestParam(required = false) @Size(max = 120) String search) {
    return ResponseEntity.ok(authService.listStudentAccounts(search));
  }
}
