package com.fttranscendence.learning.question;

import com.fttranscendence.learning.syllabus.SyllabusTopic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(
    name = "questions",
    uniqueConstraints = @UniqueConstraint(name = "uk_questions_code", columnNames = "code"))
public class Question {

  public enum QuestionType {
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    FILL_IN_THE_BLANK,
    SHORT_ANSWER,
    OPEN_ENDED,
    CALCULATION,
    DIAGRAM
  }

  public enum ArchiveState {
    ACTIVE,
    ARCHIVED
  }

  /** Curriculum demand level used for question-bank and worksheet filtering. */
  public enum Difficulty {
    FOUNDATION,
    APPLICATION,
    CHALLENGE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Size(max = 120)
  @Column(nullable = false, unique = true, length = 120)
  private String code;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns(
      value = {
        @JoinColumn(name = "syllabus_topic_id", referencedColumnName = "id", nullable = false),
        @JoinColumn(
            name = "syllabus_topic_type",
            referencedColumnName = "node_type",
            nullable = false)
      },
      foreignKey = @ForeignKey(name = "fk_questions_syllabus_topic"))
  private SyllabusTopic syllabusTopic;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, length = 32)
  private QuestionType questionType;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Difficulty difficulty = Difficulty.FOUNDATION;

  @NotBlank
  @Size(max = 4000)
  @Column(nullable = false, length = 4000)
  private String prompt;

  @NotNull
  @DecimalMin(value = "0.01")
  @Digits(integer = 4, fraction = 2)
  @Column(name = "total_marks", nullable = false, precision = 6, scale = 2)
  private BigDecimal totalMarks;

  @NotBlank
  @Size(max = 4000)
  @Column(name = "model_answer", nullable = false, length = 4000)
  private String modelAnswer;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "archive_state", nullable = false, length = 16)
  private ArchiveState archiveState = ArchiveState.ACTIVE;

  @Valid
  @Size(min = 1, max = 100)
  @OneToMany(
      mappedBy = "question",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<MarkingComponent> markingComponents = new ArrayList<>();

  @Size(max = 100)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "question_keywords",
      joinColumns = @JoinColumn(name = "question_id", nullable = false))
  @OrderColumn(name = "position")
  @Column(name = "keyword", nullable = false, length = 80)
  private List<@NotBlank @Size(max = 80) String> keywords = new ArrayList<>();

  @OneToMany(
      mappedBy = "question",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("position ASC")
  private List<QuestionImage> images = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Question() {}

  @PrePersist
  void prepareForInsert() {
    normalizeFields();
    if (archiveState == null) {
      archiveState = ArchiveState.ACTIVE;
    }
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
    if (code != null) {
      code = code.trim().toUpperCase(Locale.ROOT);
    }
    if (prompt != null) {
      prompt = prompt.trim();
    }
    if (modelAnswer != null) {
      modelAnswer = modelAnswer.trim();
    }

    Set<String> seenKeywords = new HashSet<>();
    List<String> normalizedKeywords = new ArrayList<>(keywords.size());
    for (String keyword : keywords) {
      if (keyword == null) {
        normalizedKeywords.add(null);
        continue;
      }
      String normalized = keyword.trim().toLowerCase(Locale.ROOT);
      if (!seenKeywords.add(normalized)) {
        throw new IllegalArgumentException("Question keywords must be unique");
      }
      normalizedKeywords.add(normalized);
    }
    keywords.clear();
    keywords.addAll(normalizedKeywords);
    for (int index = 0; index < markingComponents.size(); index++) {
      MarkingComponent component = markingComponents.get(index);
      component.attachTo(this);
      component.setPosition(index);
    }
  }

  @AssertTrue(message = "syllabus topic must be an active topic or subtopic")
  public boolean isSyllabusTopicAssignable() {
    if (syllabusTopic == null || !syllabusTopic.isActive()) {
      return false;
    }
    return syllabusTopic.getNodeType() == SyllabusTopic.NodeType.TOPIC
        || syllabusTopic.getNodeType() == SyllabusTopic.NodeType.SUBTOPIC;
  }

  @AssertTrue(message = "marking component marks must equal total marks")
  public boolean isMarkStructureValid() {
    if (totalMarks == null || markingComponents == null || markingComponents.isEmpty()) {
      return false;
    }
    BigDecimal componentTotal =
        markingComponents.stream()
            .map(MarkingComponent::getMarks)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return markingComponents.stream().allMatch(component -> component.getMarks() != null)
        && totalMarks.compareTo(componentTotal) == 0;
  }

  public MarkingComponent addMarkingComponent(
      String description, BigDecimal marks, List<String> keywords) {
    MarkingComponent component = new MarkingComponent(description, marks, keywords);
    component.attachTo(this);
    component.setPosition(markingComponents.size());
    markingComponents.add(component);
    return component;
  }

  /** Retained for legacy callers; an empty keyword list intentionally cannot score an answer. */
  public MarkingComponent addMarkingComponent(String description, BigDecimal marks) {
    return addMarkingComponent(description, marks, List.of());
  }

  public void removeMarkingComponent(MarkingComponent component) {
    if (markingComponents.remove(component)) {
      component.detach();
      for (int index = 0; index < markingComponents.size(); index++) {
        markingComponents.get(index).setPosition(index);
      }
    }
  }

  public void addKeyword(String keyword) {
    if (keyword == null) {
      keywords.add(null);
      return;
    }
    String normalized = keyword.trim().toLowerCase(Locale.ROOT);
    boolean duplicate =
        keywords.stream()
            .filter(java.util.Objects::nonNull)
            .map(existing -> existing.trim().toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    if (duplicate) {
      throw new IllegalArgumentException("Question keywords must be unique");
    }
    keywords.add(normalized);
  }

  /** Replaces marking criteria atomically while retaining the entity aggregate. */
  public void replaceMarkingComponents(List<MarkingComponent> replacements) {
    int shared = Math.min(markingComponents.size(), replacements.size());
    for (int index = 0; index < shared; index++) {
      MarkingComponent existing = markingComponents.get(index);
      MarkingComponent replacement = replacements.get(index);
      existing.setDescription(replacement.getDescription());
      existing.setMarks(replacement.getMarks());
      existing.setKeywords(replacement.getKeywords());
      existing.attachTo(this);
      existing.setPosition(index);
    }
    while (markingComponents.size() > replacements.size()) {
      MarkingComponent removed = markingComponents.remove(markingComponents.size() - 1);
      removed.detach();
    }
    for (int index = shared; index < replacements.size(); index++) {
      MarkingComponent replacement = replacements.get(index);
      replacement.attachTo(this);
      replacement.setPosition(index);
      markingComponents.add(replacement);
    }
  }

  /** Replaces keywords; canonicalisation and duplicate validation run at persistence time. */
  public void replaceKeywords(List<String> replacements) {
    keywords.clear();
    keywords.addAll(replacements);
  }

  public QuestionImage addImage(
      String filename, String contentType, byte[] bytes, int width, int height) {
    QuestionImage image =
        new QuestionImage(this, images.size(), filename, contentType, bytes, width, height);
    images.add(image);
    return image;
  }

  public void removeImage(QuestionImage image) {
    images.remove(image);
  }

  public void archive() {
    archiveState = ArchiveState.ARCHIVED;
  }

  public void restore() {
    archiveState = ArchiveState.ACTIVE;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public SyllabusTopic getSyllabusTopic() {
    return syllabusTopic;
  }

  public void setSyllabusTopic(SyllabusTopic syllabusTopic) {
    this.syllabusTopic = syllabusTopic;
  }

  public QuestionType getQuestionType() {
    return questionType;
  }

  public void setQuestionType(QuestionType questionType) {
    this.questionType = questionType;
  }

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(Difficulty difficulty) {
    this.difficulty = difficulty;
  }

  public String getPrompt() {
    return prompt;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public BigDecimal getTotalMarks() {
    return totalMarks;
  }

  public void setTotalMarks(BigDecimal totalMarks) {
    this.totalMarks = totalMarks;
  }

  public String getModelAnswer() {
    return modelAnswer;
  }

  public void setModelAnswer(String modelAnswer) {
    this.modelAnswer = modelAnswer;
  }

  public ArchiveState getArchiveState() {
    return archiveState;
  }

  public List<MarkingComponent> getMarkingComponents() {
    return List.copyOf(markingComponents);
  }

  public List<String> getKeywords() {
    return List.copyOf(keywords);
  }

  public List<QuestionImage> getImages() {
    return List.copyOf(images);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
