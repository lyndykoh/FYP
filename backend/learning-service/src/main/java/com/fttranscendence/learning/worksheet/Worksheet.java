package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.question.Question;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
    name = "worksheets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_worksheets_owner_code",
            columnNames = {"tutor_id", "code"}))
public class Worksheet {

  public enum AudienceType {
    CLASS,
    STUDENT
  }

  public enum Status {
    DRAFT,
    APPROVED,
    ARCHIVED
  }

  /**
   * STANDARD worksheets are Tutor-curated practice. DIAGNOSTIC worksheets are generated from
   * persisted learning evidence; both remain drafts until a Tutor approves them.
   */
  public enum WorksheetType {
    STANDARD,
    DIAGNOSTIC
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Positive
  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @NotBlank
  @Size(max = 120)
  @Column(nullable = false, length = 120)
  private String code;

  @NotBlank
  @Size(max = 200)
  @Column(nullable = false, length = 200)
  private String title;

  @Size(max = 2000)
  @Column(length = 2000)
  private String instructions;

  /** Snapshot of the source class subject when one is available. */
  @Size(max = 80)
  @Column(length = 80)
  private String subject;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "worksheet_type", nullable = false, length = 16)
  private WorksheetType worksheetType = WorksheetType.STANDARD;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "audience_type", nullable = false, length = 16)
  private AudienceType audienceType;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status = Status.DRAFT;

  @Column(name = "approved_at")
  private LocalDateTime approvedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "generation_request_id")
  private WorksheetGenerationRequest generationRequest;

  @Valid
  @Size(max = 100)
  @OneToMany(
      mappedBy = "worksheet",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC, id ASC")
  private List<WorksheetQuestion> questions = new ArrayList<>();

  @Valid
  @Size(max = 1000)
  @OneToMany(
      mappedBy = "worksheet",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("assignedAt ASC, id ASC")
  private List<WorksheetAssignment> assignments = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Worksheet() {}

  @PrePersist
  void prepareForInsert() {
    normalizeAndAlign();
    if (status == null) {
      status = Status.DRAFT;
    }
    if (worksheetType == null) {
      worksheetType = WorksheetType.STANDARD;
    }
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void prepareForUpdate() {
    normalizeAndAlign();
    updatedAt = LocalDateTime.now();
  }

  private void normalizeAndAlign() {
    if (code != null) {
      code = code.trim().toUpperCase(Locale.ROOT);
    }
    if (title != null) {
      title = title.trim();
    }
    if (instructions != null) {
      instructions = instructions.trim();
      if (instructions.isEmpty()) {
        instructions = null;
      }
    }
    if (subject != null) {
      subject = subject.trim();
      if (subject.isEmpty()) {
        subject = null;
      }
    }
    renumberQuestions();
    assignments.forEach(assignment -> assignment.alignTo(this));
  }

  @AssertTrue(message = "approved worksheets must contain questions and an approval time")
  public boolean isApprovalStateValid() {
    if (status == null) {
      return false;
    }
    if (status == Status.DRAFT) {
      return approvedAt == null;
    }
    return approvedAt != null && questions != null && !questions.isEmpty();
  }

  @AssertTrue(message = "worksheet question order and assignments must be consistent")
  public boolean isAggregateConsistent() {
    if (questions == null || assignments == null) {
      return false;
    }
    Set<Long> questionIds = new HashSet<>();
    for (int index = 0; index < questions.size(); index++) {
      WorksheetQuestion worksheetQuestion = questions.get(index);
      Question question = worksheetQuestion.getQuestion();
      if (worksheetQuestion.getPosition() != index
          || question == null
          || question.getId() == null
          || !questionIds.add(question.getId())) {
        return false;
      }
    }
    Set<String> assignmentTargets = new HashSet<>();
    for (WorksheetAssignment assignment : assignments) {
      if (assignment.getAssignmentType() != audienceType
          || !java.util.Objects.equals(assignment.getTutorId(), tutorId)
          || !assignmentTargets.add(
              assignment.getAssignmentType() + ":" + assignment.getTargetId())) {
        return false;
      }
    }
    return true;
  }

  public WorksheetQuestion addQuestion(Question question) {
    ensureDraft();
    if (question == null || question.getId() == null) {
      throw new IllegalArgumentException("Worksheet questions must already exist in the bank");
    }
    if (question.getArchiveState() != Question.ArchiveState.ACTIVE) {
      throw new IllegalArgumentException("Archived questions cannot be added to a worksheet");
    }
    boolean duplicate =
        questions.stream()
            .map(WorksheetQuestion::getQuestion)
            .map(Question::getId)
            .anyMatch(question.getId()::equals);
    if (duplicate) {
      throw new IllegalArgumentException("Question is already included in this worksheet");
    }
    WorksheetQuestion worksheetQuestion = new WorksheetQuestion(this, question, questions.size());
    questions.add(worksheetQuestion);
    return worksheetQuestion;
  }

  public void removeQuestion(WorksheetQuestion worksheetQuestion) {
    ensureDraft();
    if (questions.remove(worksheetQuestion)) {
      worksheetQuestion.detach();
      renumberQuestions();
    }
  }

  public void moveQuestion(int fromIndex, int toIndex) {
    ensureDraft();
    if (fromIndex < 0
        || fromIndex >= questions.size()
        || toIndex < 0
        || toIndex >= questions.size()) {
      throw new IndexOutOfBoundsException("Question position is outside the worksheet");
    }
    if (fromIndex == toIndex) {
      return;
    }
    WorksheetQuestion moved = questions.remove(fromIndex);
    questions.add(toIndex, moved);
    renumberQuestions();
  }

  private void renumberQuestions() {
    for (int index = 0; index < questions.size(); index++) {
      WorksheetQuestion worksheetQuestion = questions.get(index);
      worksheetQuestion.attachTo(this);
      worksheetQuestion.setPosition(index);
    }
  }

  public void approve() {
    ensureDraft();
    if (questions.isEmpty()) {
      throw new IllegalStateException("A worksheet must contain a question before approval");
    }
    status = Status.APPROVED;
    approvedAt = LocalDateTime.now();
  }

  public void archive() {
    if (status != Status.APPROVED) {
      throw new IllegalStateException("Only approved worksheets can be archived");
    }
    status = Status.ARCHIVED;
  }

  public void replaceQuestions(List<Question> replacement) {
    ensureDraft();
    questions.clear();
    for (Question question : replacement) {
      addQuestion(question);
    }
  }

  public WorksheetAssignment assignToClass(Long classId, LocalDateTime dueAt) {
    requireAudience(AudienceType.CLASS);
    ensureApproved();
    ensureUniqueAssignment(classId);
    WorksheetAssignment assignment = WorksheetAssignment.forClass(this, classId, dueAt);
    assignments.add(assignment);
    return assignment;
  }

  public WorksheetAssignment assignToStudent(Long studentProfileId, LocalDateTime dueAt) {
    requireAudience(AudienceType.STUDENT);
    ensureApproved();
    ensureUniqueAssignment(studentProfileId);
    WorksheetAssignment assignment = WorksheetAssignment.forStudent(this, studentProfileId, dueAt);
    assignments.add(assignment);
    return assignment;
  }

  private void ensureUniqueAssignment(Long targetId) {
    if (targetId == null || targetId <= 0) {
      throw new IllegalArgumentException("Assignment target must be a positive identifier");
    }
    if (assignments.stream().anyMatch(assignment -> targetId.equals(assignment.getTargetId()))) {
      throw new IllegalArgumentException("Worksheet is already assigned to this target");
    }
  }

  private void requireAudience(AudienceType requiredType) {
    if (audienceType != requiredType) {
      throw new IllegalStateException("Assignment target does not match worksheet audience");
    }
  }

  private void ensureDraft() {
    if (status != Status.DRAFT) {
      throw new IllegalStateException("Only draft worksheets can be edited");
    }
  }

  private void ensureApproved() {
    if (status != Status.APPROVED) {
      throw new IllegalStateException("Only approved worksheets can be assigned");
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
    assignments.forEach(assignment -> assignment.alignTo(this));
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(String instructions) {
    this.instructions = instructions;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public WorksheetType getWorksheetType() {
    return worksheetType;
  }

  public void setWorksheetType(WorksheetType worksheetType) {
    this.worksheetType = worksheetType;
  }

  public AudienceType getAudienceType() {
    return audienceType;
  }

  public void setAudienceType(AudienceType audienceType) {
    if (!assignments.isEmpty() && this.audienceType != audienceType) {
      throw new IllegalStateException("Cannot change audience after assignment");
    }
    this.audienceType = audienceType;
  }

  public Status getStatus() {
    return status;
  }

  public LocalDateTime getApprovedAt() {
    return approvedAt;
  }

  public List<WorksheetQuestion> getQuestions() {
    return List.copyOf(questions);
  }

  public List<WorksheetAssignment> getAssignments() {
    return List.copyOf(assignments);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public Long getGenerationRequestId() {
    return generationRequest == null ? null : generationRequest.getId();
  }

  /** The class that supplied this generated worksheet's target context. */
  public Long getSourceClassId() {
    if (generationRequest != null) {
      return generationRequest.getClassId();
    }
    return assignments.stream()
        .map(WorksheetAssignment::getClassId)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  public void setGenerationRequest(WorksheetGenerationRequest generationRequest) {
    this.generationRequest = generationRequest;
  }
}
