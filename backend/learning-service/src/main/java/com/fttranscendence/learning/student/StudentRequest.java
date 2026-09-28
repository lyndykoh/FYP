package com.fttranscendence.learning.student;

import com.fttranscendence.learning.classroom.TutorClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Request and response contracts for tutor-owned student management. */
public record StudentRequest(
    @NotBlank @Size(max = 120) String fullName,
    @Positive Long loginUserId,
    List<@NotNull @Positive Long> classIds) {
  public StudentRequest {
    classIds =
        classIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(classIds));
  }

  public record ClassSummary(
      Long id, String className, String subject, String level, TutorClass.Status status) {
    static ClassSummary from(TutorClass tutorClass) {
      return new ClassSummary(
          tutorClass.getId(),
          tutorClass.getClassName(),
          tutorClass.getSubject(),
          tutorClass.getLevel(),
          tutorClass.getStatus());
    }
  }

  public record StudentResponse(
      Long id,
      Long tutorId,
      String fullName,
      Long loginUserId,
      List<ClassSummary> classes,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    static StudentResponse from(StudentProfile student, Map<Long, TutorClass> classesById) {
      List<ClassSummary> classes =
          student.getMemberships().stream()
              .map(ClassMembership::getClassId)
              .map(classesById::get)
              .filter(java.util.Objects::nonNull)
              .map(ClassSummary::from)
              .sorted(
                  Comparator.comparing(
                          ClassSummary::className,
                          Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                      .thenComparing(ClassSummary::id))
              .toList();
      return new StudentResponse(
          student.getId(),
          student.getTutorId(),
          student.getFullName(),
          student.getLoginUserId(),
          classes,
          student.getCreatedAt(),
          student.getUpdatedAt());
    }
  }
}
