package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.ImageFingerprint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * An immutable local crop of a validated diagram region. The source page and rectangle remain with
 * the crop so a tutor can always trace image evidence back to the uploaded page rather than to OCR
 * text.
 */
@Entity
@Table(name = "question_import_diagram_crops")
public class QuestionImportDiagramCrop {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private QuestionImportBatch batch;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "source_page_id", nullable = false)
  private QuestionImportSourcePage sourcePage;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "candidate_id", nullable = false)
  private QuestionImportCandidate candidate;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "original_candidate_id", nullable = false)
  private QuestionImportCandidate originalCandidate;

  @Column(name = "diagram_region_id", nullable = false, length = 64)
  private String diagramRegionId;

  @Column(name = "sub_question_id", length = 64)
  private String subQuestionId;

  @Column(nullable = false)
  private int x;

  @Column(nullable = false)
  private int y;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  @Column(name = "content_type", nullable = false, length = 16)
  private String contentType;

  @Column(name = "crop_image_bytes", nullable = false, columnDefinition = "bytea")
  private byte[] cropImageBytes;

  @Column(name = "crop_sha256", length = 64)
  private String cropSha256;

  @Column(name = "crop_perceptual_hash", length = 16)
  private String cropPerceptualHash;

  @Column(name = "crop_width", nullable = false)
  private int cropWidth;

  @Column(name = "crop_height", nullable = false)
  private int cropHeight;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected QuestionImportDiagramCrop() {}

  QuestionImportDiagramCrop(
      QuestionImportBatch batch,
      QuestionImportSourcePage sourcePage,
      QuestionImportCandidate candidate,
      String diagramRegionId,
      String subQuestionId,
      QuestionVisionAnalyzer.BoundingBox rectangle,
      byte[] cropImageBytes,
      int cropWidth,
      int cropHeight) {
    this.batch = batch;
    this.sourcePage = sourcePage;
    this.candidate = candidate;
    this.originalCandidate = candidate;
    this.diagramRegionId = diagramRegionId;
    this.subQuestionId = subQuestionId;
    x = rectangle.x();
    y = rectangle.y();
    width = rectangle.width();
    height = rectangle.height();
    contentType = "image/png";
    this.cropImageBytes = cropImageBytes.clone();
    cropSha256 = ImageFingerprint.sha256(cropImageBytes);
    cropPerceptualHash = ImageFingerprint.perceptualHash(cropImageBytes);
    this.cropWidth = cropWidth;
    this.cropHeight = cropHeight;
  }

  @jakarta.persistence.PrePersist
  void created() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public QuestionImportBatch getBatch() {
    return batch;
  }

  public QuestionImportSourcePage getSourcePage() {
    return sourcePage;
  }

  public QuestionImportCandidate getCandidate() {
    return candidate;
  }

  public QuestionImportCandidate getOriginalCandidate() {
    return originalCandidate;
  }

  public String getDiagramRegionId() {
    return diagramRegionId;
  }

  public String getSubQuestionId() {
    return subQuestionId;
  }

  public int getX() {
    return x;
  }

  public int getY() {
    return y;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getCropImageBytes() {
    return cropImageBytes.clone();
  }

  public String getCropSha256() {
    return cropSha256;
  }

  public String getCropPerceptualHash() {
    return cropPerceptualHash;
  }

  public int getCropWidth() {
    return cropWidth;
  }

  public int getCropHeight() {
    return cropHeight;
  }

  void moveTo(QuestionImportCandidate target) {
    candidate = target;
  }
}
