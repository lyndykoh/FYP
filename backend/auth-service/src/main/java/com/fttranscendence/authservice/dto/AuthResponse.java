package com.fttranscendence.authservice.dto;

import com.fttranscendence.authservice.model.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
  private String token;
  private String email;
  private String fullName;
  private UserRole role;
}
