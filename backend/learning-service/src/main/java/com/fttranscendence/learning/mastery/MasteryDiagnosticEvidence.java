package com.fttranscendence.learning.mastery;

import com.fttranscendence.learning.student.StudentProfile;
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

/** Immutable tutor-confirmed diagnostic evidence attached to an approved result. */
@Entity
@Table(name = "mastery_diagnostic_evidence")
public class MasteryDiagnosticEvidence {

  public enum Category {
    CONCEPT,
    KEYWORD,
    EXPRESSION,
    APPLICATION
  }

  /**
   * The controlled diagnostic vocabulary shared with grading. Category is retained in storage only
   * as a backwards-compatible, derived grouping; consumers must use this type for recurrence and
   * analytics.
   */
  public enum MistakeType {
    CONCEPT_MISUNDERSTANDING(Category.CONCEPT),
    CALCULATION_ERROR(Category.APPLICATION),
    MISREAD_QUESTION(Category.APPLICATION),
    INCOMPLETE_WORKING(Category.APPLICATION),
    INCORRECT_FORMULA(Category.APPLICATION),
    CARELESS_MISTAKE(Category.APPLICATION),
    WEAK_EXPLANATION(Category.EXPRESSION),
    MISSING_KEY_POINT(Category.KEYWORD),
    WRONG_UNITS(Category.EXPRESSION),
    ANSWER_FORMAT_ISSUE(Category.EXPRESSION);

    private final Category category;

    MistakeType(Category category) {
      this.category = category;
    }

    public Category category() {
      return category;
    }

    /** Deterministic compatibility fallback for pre-canonical events. */
    public static MistakeType legacyDefault(Category category) {
      if (category == null) throw new IllegalArgumentException("Diagnostic category is required");
      return switch (category) {
        case CONCEPT -> CONCEPT_MISUNDERSTANDING;
        case KEYWORD -> MISSING_KEY_POINT;
        case EXPRESSION -> WEAK_EXPLANATION;
        case APPLICATION -> INCOMPLETE_WORKING;
      };
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "mastery_record_id", nullable = false)
  private MasteryRecord masteryRecord;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_profile_id", nullable = false)
  private StudentProfile studentProfile;

  @Column(name = "tutor_id", nullable = false)
  private Long tutorId;

  @Column(name = "source_submission_id", nullable = false)
  private Long sourceSubmissionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "diagnostic_category", nullable = false, length = 16)
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(name = "mistake_type", nullable = false, length = 40)
  private MistakeType mistakeType;

  @Column(name = "tutor_rationale", nullable = false, length = 500)
  private String tutorRationale;

  @ElementCollection
  @CollectionTable(
      name = "mastery_diagnostic_evidence_keywords",
      joinColumns = @JoinColumn(name = "evidence_id"))
  @OrderColumn(name = "keyword_position")
  @Column(name = "keyword", nullable = false, length = 120)
  private List<String> missingKeywords = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected MasteryDiagnosticEvidence() {}

  public MasteryDiagnosticEvidence(
      MasteryRecord masteryRecord,
      StudentProfile studentProfile,
      long tutorId,
      long sourceSubmissionId,
      Category category,
      String tutorRationale,
      List<String> missingKeywords) {
    this(
        masteryRecord,
        studentProfile,
        tutorId,
        sourceSubmissionId,
        MistakeType.legacyDefault(category),
        category,
        tutorRationale,
        missingKeywords);
  }

  public MasteryDiagnosticEvidence(
      MasteryRecord masteryRecord,
      StudentProfile studentProfile,
      long tutorId,
      long sourceSubmissionId,
      MistakeType mistakeType,
      Category category,
      String tutorRationale,
      List<String> missingKeywords) {
    this.masteryRecord = require(masteryRecord, "Mastery record");
    this.studentProfile = require(studentProfile, "Student profile");
    this.tutorId = positive(tutorId, "Tutor id");
    this.sourceSubmissionId = positive(sourceSubmissionId, "Source submission id");
    this.mistakeType = require(mistakeType, "Mistake type");
    this.category = category == null ? mistakeType.category() : category;
    this.tutorRationale = text(tutorRationale, "Tutor rationale");
    this.missingKeywords = keywords(missingKeywords);
    validate();
  }

  @PrePersist
  void beforeInsert() {
    if (createdAt == null) createdAt = LocalDateTime.now();
    validate();
  }

  private void validate() {
    require(masteryRecord, "Mastery record");
    StudentProfile profile = require(studentProfile, "Student profile");
    if (profile.getTutorId() == null || !profile.getTutorId().equals(tutorId)) {
      throw new IllegalStateException("Diagnostic evidence tutor must own the student profile");
    }
    positive(tutorId, "Tutor id");
    positive(sourceSubmissionId, "Source submission id");
    mistakeType = require(mistakeType, "Mistake type");
    if (category == null) category = mistakeType.category();
    if (category != mistakeType.category()) {
      throw new IllegalArgumentException(
          "Diagnostic category must match the canonical mistake type");
    }
    text(tutorRationale, "Tutor rationale");
    missingKeywords = keywords(missingKeywords);
  }

  private static List<String> keywords(List<String> values) {
    if (values == null || values.isEmpty()) return new ArrayList<>();
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String value : values)
      normalized.add(text(value, "Missing keyword").toLowerCase(java.util.Locale.ROOT));
    return new ArrayList<>(normalized);
  }

  private static long positive(long value, String field) {
    if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
    return value;
  }

  private static String text(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " is required");
    return value.trim();
  }

  private static <T> T require(T value, String field) {
    if (value == null) throw new IllegalArgumentException(field + " is required");
    return value;
  }

  public Long getId() {
    return id;
  }

  public MasteryRecord getMasteryRecord() {
    return masteryRecord;
  }

  public StudentProfile getStudentProfile() {
    return studentProfile;
  }

  public Long getTutorId() {
    return tutorId;
  }

  public Long getSourceSubmissionId() {
    return sourceSubmissionId;
  }

  public Category getCategory() {
    return category;
  }

  public MistakeType getMistakeType() {
    return mistakeType;
  }

  public String getTutorRationale() {
    return tutorRationale;
  }

  public List<String> getMissingKeywords() {
    return List.copyOf(missingKeywords);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
