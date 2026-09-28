package com.fttranscendence.learning.student;

/** A safe, role-filtered identity projection for the Tutor's student selector. */
public record StudentAccountResponse(Long id, String fullName, String email, String level) {
  static StudentAccountResponse from(AuthStudentDirectoryClient.StudentAccount account) {
    return new StudentAccountResponse(account.id(), account.fullName(), account.email(), null);
  }
}
