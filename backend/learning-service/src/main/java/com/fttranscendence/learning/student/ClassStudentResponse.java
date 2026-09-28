package com.fttranscendence.learning.student;

/** Class-scoped Student projections. Account email comes only from auth-service. */
public final class ClassStudentResponse {
  private ClassStudentResponse() {}

  public record EligibleStudentResponse(
      Long loginUserId, String fullName, String email, String level) {
    static EligibleStudentResponse from(AuthStudentDirectoryClient.StudentAccount account) {
      return new EligibleStudentResponse(account.id(), account.fullName(), account.email(), null);
    }
  }

  public record ClassMemberResponse(Long id, Long loginUserId, String fullName) {
    static ClassMemberResponse from(StudentProfile profile) {
      return new ClassMemberResponse(
          profile.getId(), profile.getLoginUserId(), profile.getFullName());
    }
  }
}
