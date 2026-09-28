package com.fttranscendence.authservice.dto;

import com.fttranscendence.authservice.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import lombok.Data;

@Data
public class RegisterRequest {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(min = 12, max = 128)
  @Pattern(
      regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
      message = "must include uppercase, lowercase, number, and special character")
  private String password;

  @NotBlank
  @Size(min = 2, max = 100)
  private String fullName;

  @NotNull private UserRole role;

  public void setEmail(String email) {
    this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }
}
