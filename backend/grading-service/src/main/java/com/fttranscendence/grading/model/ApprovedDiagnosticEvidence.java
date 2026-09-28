package com.fttranscendence.grading.model;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable tutor-confirmed evidence belonging to one approved answer. */
@Entity
@Table(name = "approved_diagnostic_evidence")
public class ApprovedDiagnosticEvidence {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "submission_id", nullable = false)
  private Submission submission;

  @Column(name = "position", nullable = false)
  private int position;

  @Column(name = "syllabus_topic_id", nullable = false)
  private Long syllabusTopicId;

  @Enumerated(EnumType.STRING)
  @Column(name = "mistake_type", nullable = false, length = 40)
  private MistakeType mistakeType;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 24)
  private DiagnosticCategory category;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String description;

  @ElementCollection
  @CollectionTable(
      name = "approved_diagnostic_evidence_keywords",
      joinColumns = @JoinColumn(name = "evidence_id"))
  @Column(name = "keyword", nullable = false, length = 180)
  @OrderColumn(name = "position")
  private List<String> missingKeywords = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected ApprovedDiagnosticEvidence() {}

  private ApprovedDiagnosticEvidence(
      Submission submission,
      int position,
      Long syllabusTopicId,
      MistakeType mistakeType,
      String description,
      List<String> missingKeywords) {
    if (submission == null || submission.getReviewStatus() != Submission.ReviewStatus.APPROVED) {
      throw new IllegalStateException("Diagnostic evidence requires a tutor-approved submission");
    }
    if (position < 0)
      throw new IllegalArgumentException("Diagnostic evidence position must be non-negative");
    if (syllabusTopicId == null || syllabusTopicId <= 0)
      throw new IllegalArgumentException("Syllabus topic id is required");
    if (mistakeType == null) throw new IllegalArgumentException("Mistake type is required");
    if (description == null || description.isBlank() || description.trim().length() > 4000) {
      throw new IllegalArgumentException(
          "Diagnostic description is required and must be at most 4000 characters");
    }
    this.submission = submission;
    this.position = position;
    this.syllabusTopicId = syllabusTopicId;
    this.mistakeType = mistakeType;
    this.category = mistakeType.getDiagnosticCategory();
    this.description = description.trim();
    this.missingKeywords = normalizeKeywords(missingKeywords);
  }

  public static ApprovedDiagnosticEvidence create(
      Submission submission,
      int position,
      Long syllabusTopicId,
      MistakeType mistakeType,
      String description,
      List<String> missingKeywords) {
    return new ApprovedDiagnosticEvidence(
        submission, position, syllabusTopicId, mistakeType, description, missingKeywords);
  }

  @PrePersist
  protected void beforeInsert() {
    createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
  }

  private static List<String> normalizeKeywords(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    Set<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null || value.isBlank() || value.trim().length() > 180) {
        throw new IllegalArgumentException(
            "Diagnostic keywords must be non-blank and at most 180 characters");
      }
      result.add(value.trim());
    }
    if (result.size() > 20)
      throw new IllegalArgumentException("At most 20 diagnostic keywords are allowed");
    return List.copyOf(result);
  }

  public Long getId() {
    return id;
  }

  public int getPosition() {
    return position;
  }

  public Long getSyllabusTopicId() {
    return syllabusTopicId;
  }

  public MistakeType getMistakeType() {
    return mistakeType;
  }

  public DiagnosticCategory getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public List<String> getMissingKeywords() {
    return List.copyOf(missingKeywords);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
