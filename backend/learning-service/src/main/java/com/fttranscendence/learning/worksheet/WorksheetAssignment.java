package com.fttranscendence.learning.worksheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "worksheet_assignments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_worksheet_assignments_target",
            columnNames = {"worksheet_id", "assignment_type", "target_id"}))
public class WorksheetAssignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "worksheet_id", nullable = false)
  private Worksheet worksheet;

  @NotNull
  @Positive
  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_type", nullable = false, length = 16)
  private Worksheet.AudienceType assignmentType;

  @NotNull
  @Positive
  @Column(name = "target_id", nullable = false)
  private Long targetId;

  @Positive
  @Column(name = "class_id")
  private Long classId;

  @Positive
  @Column(name = "student_profile_id")
  private Long studentProfileId;

  @Column(name = "assigned_at", nullable = false, updatable = false)
  private LocalDateTime assignedAt;

  @Column(name = "due_at")
  private LocalDateTime dueAt;

  protected WorksheetAssignment() {}

  static WorksheetAssignment forClass(Worksheet worksheet, Long classId, LocalDateTime dueAt) {
    WorksheetAssignment assignment = new WorksheetAssignment();
    assignment.worksheet = worksheet;
    assignment.assignmentType = Worksheet.AudienceType.CLASS;
    assignment.targetId = classId;
    assignment.classId = classId;
    assignment.dueAt = dueAt;
    assignment.alignTo(worksheet);
    return assignment;
  }

  static WorksheetAssignment forStudent(
      Worksheet worksheet, Long studentProfileId, LocalDateTime dueAt) {
    WorksheetAssignment assignment = new WorksheetAssignment();
    assignment.worksheet = worksheet;
    assignment.assignmentType = Worksheet.AudienceType.STUDENT;
    assignment.targetId = studentProfileId;
    assignment.studentProfileId = studentProfileId;
    assignment.dueAt = dueAt;
    assignment.alignTo(worksheet);
    return assignment;
  }

  @PrePersist
  void prepareForInsert() {
    alignTo(worksheet);
    if (assignedAt == null) {
      assignedAt = LocalDateTime.now();
    }
  }

  void alignTo(Worksheet worksheet) {
    this.worksheet = worksheet;
    if (worksheet != null) {
      tutorId = worksheet.getTutorId();
    }
  }

  @AssertTrue(message = "assignment target must match its type")
  public boolean isTargetConsistent() {
    if (assignmentType == Worksheet.AudienceType.CLASS) {
      return targetId != null && targetId.equals(classId) && studentProfileId == null;
    }
    if (assignmentType == Worksheet.AudienceType.STUDENT) {
      return targetId != null && targetId.equals(studentProfileId) && classId == null;
    }
    return false;
  }

  @AssertTrue(message = "assignment must match the worksheet audience and owner")
  public boolean isWorksheetConsistent() {
    return worksheet != null
        && assignmentType == worksheet.getAudienceType()
        && tutorId != null
        && tutorId.equals(worksheet.getTutorId());
  }

  @AssertTrue(message = "due date must be later than assignment date")
  public boolean isDueDateValid() {
    return dueAt == null || assignedAt == null || dueAt.isAfter(assignedAt);
  }

  public Long getId() {
    return id;
  }

  /** Identifier used for navigation to the assigned worksheet. */
  public Long getWorksheetId() {
    return worksheet == null ? null : worksheet.getId();
  }

  public Worksheet.AudienceType getAssignmentType() {
    return assignmentType;
  }

  public Long getTargetId() {
    return targetId;
  }

  public Long getClassId() {
    return classId;
  }

  public Long getStudentProfileId() {
    return studentProfileId;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public LocalDateTime getAssignedAt() {
    return assignedAt;
  }

  public LocalDateTime getDueAt() {
    return dueAt;
  }
}
