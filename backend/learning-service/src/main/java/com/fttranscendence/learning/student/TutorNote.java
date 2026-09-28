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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "tutor_notes")
public class TutorNote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tutor_id", nullable = false, updatable = false)
  private Long tutorId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @Column(nullable = false, length = 4000)
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected TutorNote() {}

  public TutorNote(Long tutorId, StudentProfile studentProfile, String content) {
    this.tutorId = requirePositive(tutorId, "Tutor id");
    this.studentProfile = requireStudent(studentProfile, this.tutorId);
    this.content = requireContent(content);
  }

  public void updateContent(String content) {
    this.content = requireContent(content);
  }

  @PrePersist
  void prepareForInsert() {
    validateOwnership();
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    validateOwnership();
    updatedAt = LocalDateTime.now();
  }

  private void validateOwnership() {
    tutorId = requirePositive(tutorId, "Tutor id");
    studentProfile = requireStudent(studentProfile, tutorId);
    content = requireContent(content);
  }

  private static Long requirePositive(Long value, String name) {
    if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static StudentProfile requireStudent(StudentProfile value, Long tutorId) {
    if (value == null || value.getTutorId() == null || !tutorId.equals(value.getTutorId())) {
      throw new IllegalArgumentException("Tutor must own the student profile");
    }
    return value;
  }

  private static String requireContent(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Note content is required");
    String normalized = value.trim();
    if (normalized.length() > 4000)
      throw new IllegalArgumentException("Note content must not exceed 4000 characters");
    return normalized;
  }

  public Long getId() {
    return id;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public String getContent() {
    return content;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
