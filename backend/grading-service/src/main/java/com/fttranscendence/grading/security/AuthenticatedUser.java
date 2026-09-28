package com.fttranscendence.grading.security;

/** Stable cross-service identity carried by an auth-service JWT. */
public record AuthenticatedUser(long userId, String email, String role) {}
