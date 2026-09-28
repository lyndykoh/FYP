package com.fttranscendence.learning.alert;

import com.fttranscendence.learning.student.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Minimal protected projection for review-age alerts; never carries answer text or AI output. */
@Entity
@Table(name = "marking_review_status_projection")
public class MarkingReviewStatusProjection {
  public enum State {
    PENDING_REVIEW,
    RESOLVED
  }

  @Id
  @Column(name = "source_submission_id")
  private Long sourceSubmissionId;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  /** Worksheet provenance only; the projection intentionally stores no answer or AI content. */
  @Column(name = "worksheet_id")
  private Long worksheetId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @Column(nullable = false)
  private int revision;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_state", nullable = false, length = 20)
  private State state;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected MarkingReviewStatusProjection() {}

  public MarkingReviewStatusProjection(
      long submissionId,
      long tutorId,
      long worksheetId,
      StudentProfile student,
      int revision,
      State state,
      LocalDateTime requestedAt) {
    this.sourceSubmissionId = positive(submissionId, "Submission id");
    this.tutorId = positive(tutorId, "Tutor id");
    this.worksheetId = positive(worksheetId, "Worksheet id");
    this.studentProfile = required(student, "Student profile");
    set(revision, state, requestedAt);
  }

  public void setWorksheetId(long nextWorksheetId) {
    positive(nextWorksheetId, "Worksheet id");
    if (worksheetId != null && !worksheetId.equals(nextWorksheetId))
      throw new IllegalArgumentException("A review state cannot change worksheet provenance.");
    worksheetId = nextWorksheetId;
  }

  public boolean set(int nextRevision, State nextState, LocalDateTime nextRequestedAt) {
    if (nextRevision < revision) return false;
    if (nextRevision == revision && state == nextState) return false;
    revision = positiveInt(nextRevision, "Revision");
    state = required(nextState, "Review state");
    requestedAt = required(nextRequestedAt, "Requested at");
    return true;
  }

  @PrePersist
  @PreUpdate
  void validate() {
    positive(sourceSubmissionId, "Submission id");
    positive(tutorId, "Tutor id");
    if (studentProfile == null
        || studentProfile.getTutorId() == null
        || !studentProfile.getTutorId().equals(tutorId))
      throw new IllegalStateException("Review projection tutor must own the student");
    positiveInt(revision, "Revision");
    required(state, "Review state");
    required(requestedAt, "Requested at");
    updatedAt = LocalDateTime.now();
  }

  private static long positive(Long value, String name) {
    if (value == null || value <= 0) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static int positiveInt(int value, String name) {
    if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static <T> T required(T value, String name) {
    if (value == null) throw new IllegalArgumentException(name + " is required");
    return value;
  }

  public Long getSourceSubmissionId() {
    return sourceSubmissionId;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public Long getWorksheetId() {
    return worksheetId;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public int getRevision() {
    return revision;
  }

  public State getState() {
    return state;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }
}
