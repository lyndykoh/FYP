package com.fttranscendence.grading.ocr;

import com.fttranscendence.grading.model.SubmissionPage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_extractions")
public class OcrExtraction {

  private static final double REVIEW_CONFIDENCE_THRESHOLD = 0.85;

  public enum Status {
    READY,
    REQUIRES_REVIEW,
    UNREADABLE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_page_id", nullable = false, unique = true)
  private SubmissionPage page;

  @Column(name = "worksheet_question_id")
  private Long worksheetQuestionId;

  @Column(name = "extracted_text", nullable = false, columnDefinition = "TEXT")
  private String extractedText;

  @Column(name = "corrected_text", columnDefinition = "TEXT")
  private String correctedText;

  @Column(nullable = false, columnDefinition = "numeric(5,4)")
  private double confidence;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private String provider;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected OcrExtraction() {}

  public OcrExtraction(
      SubmissionPage page,
      Long worksheetQuestionId,
      String extractedText,
      double confidence,
      String provider) {
    this.page = page;
    this.worksheetQuestionId = worksheetQuestionId;
    this.extractedText = extractedText;
    this.confidence = confidence;
    this.provider = provider;
    this.status = determineInitialStatus(extractedText, confidence);
  }

  public void correct(String correctedText) {
    this.correctedText = correctedText.trim();
    status = Status.READY;
  }

  public void assignToWorksheetQuestion(Long worksheetQuestionId) {
    boolean hasValidQuestionId = worksheetQuestionId != null && worksheetQuestionId > 0;
    if (!hasValidQuestionId) {
      throw new IllegalArgumentException("Worksheet question is required.");
    }

    this.worksheetQuestionId = worksheetQuestionId;
  }

  @PrePersist
  void insert() {
    createdAt = updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void update() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public SubmissionPage getPage() {
    return page;
  }

  public Long getWorksheetQuestionId() {
    return worksheetQuestionId;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public String getCorrectedText() {
    return correctedText;
  }

  public double getConfidence() {
    return confidence;
  }

  public Status getStatus() {
    return status;
  }

  private Status determineInitialStatus(String extractedText, double confidence) {
    if (extractedText.isBlank()) {
      return Status.UNREADABLE;
    }

    if (confidence < REVIEW_CONFIDENCE_THRESHOLD) {
      return Status.REQUIRES_REVIEW;
    }

    return Status.READY;
  }
}
