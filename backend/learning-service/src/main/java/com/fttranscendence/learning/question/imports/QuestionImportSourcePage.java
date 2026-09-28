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

@Entity
@Table(name = "question_import_source_pages")
public class QuestionImportSourcePage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "batch_id", nullable = false)
  private QuestionImportBatch batch;

  @Column(name = "source_filename", nullable = false, length = 255)
  private String sourceFilename;

  @Column(name = "source_page_number", nullable = false)
  private int sourcePageNumber;

  @Column(name = "content_type", nullable = false, length = 16)
  private String contentType;

  @Column(name = "source_checksum", length = 64)
  private String sourceChecksum;

  @Column(name = "page_image_sha256", length = 64)
  private String pageImageSha256;

  @Column(name = "page_image_perceptual_hash", length = 16)
  private String pageImagePerceptualHash;

  @Column(name = "page_image_bytes", nullable = false, columnDefinition = "bytea")
  private byte[] pageImageBytes;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  @Column(name = "extracted_text", nullable = false, length = 12000)
  private String extractedText;

  @Column(name = "processing_error", length = 1000)
  private String processingError;

  protected QuestionImportSourcePage() {}

  QuestionImportSourcePage(
      QuestionImportBatch batch,
      String sourceFilename,
      int sourcePageNumber,
      String contentType,
      String sourceChecksum,
      byte[] bytes,
      int width,
      int height,
      String extractedText,
      String processingError) {
    this.batch = batch;
    this.sourceFilename = sourceFilename;
    this.sourcePageNumber = sourcePageNumber;
    this.contentType = contentType;
    this.pageImageBytes = bytes.clone();
    this.width = width;
    this.height = height;
    this.sourceChecksum = sourceChecksum;
    pageImageSha256 = ImageFingerprint.sha256(bytes);
    pageImagePerceptualHash = ImageFingerprint.perceptualHash(bytes);
    this.extractedText = extractedText;
    this.processingError = processingError;
  }

  public Long getId() {
    return id;
  }

  public String getSourceFilename() {
    return sourceFilename;
  }

  public int getSourcePageNumber() {
    return sourcePageNumber;
  }

  public String getContentType() {
    return contentType;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public String getPageImageSha256() {
    return pageImageSha256;
  }

  public String getPageImagePerceptualHash() {
    return pageImagePerceptualHash;
  }

  public byte[] getPageImageBytes() {
    return pageImageBytes.clone();
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public String getProcessingError() {
    return processingError;
  }

  void setProcessingError(String error) {
    processingError = error;
  }
}
