package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.question.Question;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Provenance for a deterministic, tutor-reviewed worksheet draft. */
@Entity
@Table(name = "worksheet_generation_requests")
public class WorksheetGenerationRequest {
  public enum TargetMode {
    CLASS,
    STUDENTS
  }

  public enum Status {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @Column(name = "class_id", nullable = false)
  private Long classId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_mode", nullable = false, length = 16)
  private TargetMode targetMode;

  @Column(name = "question_count", nullable = false)
  private int questionCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", length = 32)
  private Question.QuestionType questionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", length = 16)
  private Question.Difficulty difficulty;

  @Column(name = "due_at")
  private LocalDateTime dueAt;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 128)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.QUEUED;

  @Column(name = "failure_code", length = 80)
  private String failureCode;

  @Column(name = "failure_message", length = 500)
  private String failureMessage;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "worksheet_generation_request_topics",
      joinColumns = @JoinColumn(name = "request_id"))
  @OrderColumn(name = "position")
  @Column(name = "topic_id", nullable = false)
  private List<Long> topicIds = new ArrayList<>();

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "worksheet_generation_request_students",
      joinColumns = @JoinColumn(name = "request_id"))
  @Column(name = "student_profile_id", nullable = false)
  private Set<Long> studentIds = new LinkedHashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected WorksheetGenerationRequest() {}

  public WorksheetGenerationRequest(
      long tutorId,
      long classId,
      TargetMode targetMode,
      int questionCount,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      LocalDateTime dueAt,
      String idempotencyKey,
      String requestHash,
      List<Long> topicIds,
      Set<Long> studentIds) {
    this.tutorId = tutorId;
    this.classId = classId;
    this.targetMode = targetMode;
    this.questionCount = questionCount;
    this.questionType = questionType;
    this.difficulty = difficulty;
    this.dueAt = dueAt;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.topicIds = new ArrayList<>(topicIds);
    this.studentIds = new LinkedHashSet<>(studentIds);
  }

  @PrePersist
  void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public void start() {
    status = Status.RUNNING;
    failureCode = null;
    failureMessage = null;
  }

  public void succeed() {
    status = Status.SUCCEEDED;
    failureCode = null;
    failureMessage = null;
  }

  public void fail(String code, String message) {
    status = Status.FAILED;
    failureCode = code;
    failureMessage = message;
  }

  public Long getId() {
    return id;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public Long getClassId() {
    return classId;
  }

  public TargetMode getTargetMode() {
    return targetMode;
  }

  public int getQuestionCount() {
    return questionCount;
  }

  public Question.QuestionType getQuestionType() {
    return questionType;
  }

  public Question.Difficulty getDifficulty() {
    return difficulty;
  }

  public LocalDateTime getDueAt() {
    return dueAt;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public Status getStatus() {
    return status;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public List<Long> getTopicIds() {
    return List.copyOf(topicIds);
  }

  public Set<Long> getStudentIds() {
    return Set.copyOf(studentIds);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
