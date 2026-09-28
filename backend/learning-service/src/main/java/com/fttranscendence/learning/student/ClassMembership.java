package com.fttranscendence.learning.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "class_memberships",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_class_memberships_student_class",
            columnNames = {"student_profile_id", "class_id"}))
public class ClassMembership {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @NotNull
  @Positive
  @Column(name = "class_id", nullable = false)
  private Long classId;

  @NotNull
  @Positive
  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @Column(name = "joined_at", nullable = false, updatable = false)
  private LocalDateTime joinedAt;

  protected ClassMembership() {}

  ClassMembership(StudentProfile studentProfile, Long classId, Long tutorId) {
    this.studentProfile = studentProfile;
    this.classId = classId;
    this.tutorId = tutorId;
  }

  @PrePersist
  void prepareForInsert() {
    alignTutorId(studentProfile == null ? null : studentProfile.getTutorId());
    joinedAt = LocalDateTime.now();
  }

  void alignTutorId(Long owningTutorId) {
    tutorId = owningTutorId;
  }

  void detach() {
    studentProfile = null;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentProfileId() {
    return studentProfile == null ? null : studentProfile.getId();
  }

  public Long getClassId() {
    return classId;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public LocalDateTime getJoinedAt() {
    return joinedAt;
  }
}
