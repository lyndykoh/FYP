package com.fttranscendence.learning.security;

/** Stable cross-service identity carried by an auth-service JWT. */
public record AuthenticatedUser(long userId, String email, String role, String fullName) {
  public AuthenticatedUser(long userId, String email, String role) {
    this(userId, email, role, email);
  }
}
