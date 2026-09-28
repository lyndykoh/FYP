package com.fttranscendence.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Data;

@Data
public class AuthRequest {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(max = 128)
  private String password;

  public void setEmail(String email) {
    this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
