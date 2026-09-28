package com.fttranscendence.learning.question;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Entity
@Table(name = "marking_components")
public class MarkingComponent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(nullable = false)
  private int position;

  @NotBlank
  @Size(max = 1000)
  @Column(nullable = false, length = 1000)
  private String description;

  @NotNull
  @DecimalMin(value = "0.01")
  @Digits(integer = 4, fraction = 2)
  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal marks;

  /**
   * Explicit deterministic evidence for this criterion. Descriptions are tutor guidance, not answer
   * text, and must never be used as a matcher.
   */
  @Size(max = 100)
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "marking_component_keywords",
      joinColumns = @JoinColumn(name = "marking_component_id", nullable = false))
  @jakarta.persistence.OrderColumn(name = "position")
  @Column(name = "keyword", nullable = false, length = 80)
  private List<@NotBlank @Size(max = 80) String> keywords = new ArrayList<>();

  protected MarkingComponent() {}

  /** Retained for existing aggregate fixtures; legacy components have no deterministic keywords. */
  public MarkingComponent(String description, BigDecimal marks) {
    this(description, marks, List.of());
  }

  public MarkingComponent(String description, BigDecimal marks, List<String> keywords) {
    this.description = description;
    this.marks = marks;
    setKeywords(keywords);
  }

  @PrePersist
  @PreUpdate
  void normalizeDescription() {
    if (description != null) {
      description = description.trim();
    }
    Set<String> seen = new HashSet<>();
    List<String> normalized = new ArrayList<>(keywords.size());
    for (String keyword : keywords) {
      if (keyword == null) {
        normalized.add(null);
        continue;
      }
      String value = keyword.trim().toLowerCase(Locale.ROOT);
      if (!seen.add(value)) {
        throw new IllegalArgumentException("Marking component keywords must be unique");
      }
      normalized.add(value);
    }
    keywords.clear();
    keywords.addAll(normalized);
  }

  void attachTo(Question question) {
    this.question = question;
  }

  void detach() {
    this.question = null;
  }

  void setPosition(int position) {
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public int getPosition() {
    return position;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getMarks() {
    return marks;
  }

  public void setMarks(BigDecimal marks) {
    this.marks = marks;
  }

  public List<String> getKeywords() {
    return List.copyOf(keywords);
  }

  public void setKeywords(List<String> keywords) {
    this.keywords.clear();
    if (keywords != null) {
      this.keywords.addAll(keywords);
    }
  }
}
