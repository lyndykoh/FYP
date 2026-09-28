package com.fttranscendence.authservice.dto;

import com.fttranscendence.authservice.model.User;

/** Minimal account identity a Tutor may use to select an existing Student login. */
public record StudentDirectoryResponse(long id, String fullName, String email) {

  public static StudentDirectoryResponse from(User user) {
    return new StudentDirectoryResponse(user.getId(), user.getFullName(), user.getEmail());
  }
}
