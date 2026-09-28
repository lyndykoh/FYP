package com.fttranscendence.learning.student;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "student_profiles",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_student_profiles_login", columnNames = "login_user_id"))
public class StudentProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Positive
  @Column(name = "tutor_id")
  private Long tutorId;

  @Positive
  @Column(name = "login_user_id", unique = true)
  private Long loginUserId;

  @NotBlank
  @Size(max = 120)
  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Valid
  @OneToMany(
      mappedBy = "studentProfile",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<ClassMembership> memberships = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prepareForInsert() {
    normalizeFields();
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    normalizeFields();
    updatedAt = LocalDateTime.now();
  }

  private void normalizeFields() {
    if (fullName != null) {
      fullName = fullName.trim();
    }
    memberships.forEach(membership -> membership.alignTutorId(tutorId));
  }

  public ClassMembership addClassMembership(Long classId) {
    ClassMembership membership = new ClassMembership(this, classId, tutorId);
    memberships.add(membership);
    return membership;
  }

  public void removeClassMembership(ClassMembership membership) {
    if (memberships.remove(membership)) {
      membership.detach();
    }
  }

  public Long getId() {
    return id;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public void setTutorId(Long tutorId) {
    this.tutorId = tutorId;
    memberships.forEach(membership -> membership.alignTutorId(tutorId));
  }

  public Long getLoginUserId() {
    return loginUserId;
  }

  public void setLoginUserId(Long loginUserId) {
    this.loginUserId = loginUserId;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public List<ClassMembership> getMemberships() {
    return memberships;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
