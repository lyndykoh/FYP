package com.fttranscendence.grading.model;

import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.persistence.Column;
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
import java.time.LocalDateTime;

@Entity
@Table(name = "submission_pages")
public class SubmissionPage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "submission_document_id", nullable = false)
  private SubmissionDocument document;

  @Column(name = "page_number", nullable = false)
  private int pageNumber;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "media_type", nullable = false, length = 64)
  private String mediaType;

  @Column(name = "storage_key", nullable = false, unique = true, length = 255)
  private String storageKey;

  @Column(name = "byte_size", nullable = false)
  private long byteSize;

  @Column(name = "checksum_sha256", nullable = false, length = 64)
  private String checksumSha256;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected SubmissionPage() {}

  SubmissionPage(
      SubmissionDocument document, int pageNumber, DocumentStorage.StoredFile storedFile) {
    this.document = document;
    this.pageNumber = pageNumber;
    this.originalFilename = storedFile.originalFilename();
    this.mediaType = storedFile.mediaType();
    this.storageKey = storedFile.storageKey();
    this.byteSize = storedFile.byteSize();
    this.checksumSha256 = storedFile.checksumSha256();
  }

  @PrePersist
  protected void beforeInsert() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
    validateMetadata();
  }

  @PreUpdate
  protected void beforeUpdate() {
    validateMetadata();
  }

  void setPageNumber(int pageNumber) {
    this.pageNumber = pageNumber;
  }

  private void validateMetadata() {
    if (document == null) {
      throw new IllegalStateException("Submission page must belong to a document");
    }
    if (pageNumber <= 0) {
      throw new IllegalStateException("Page number must be positive");
    }
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalStateException("Original filename is required");
    }
    if (!mediaType.equals("application/pdf")
        && !mediaType.equals("image/jpeg")
        && !mediaType.equals("image/png")) {
      throw new IllegalStateException("Unsupported page media type");
    }
    if (storageKey == null || !storageKey.startsWith(document.getOwnerUserId() + "/")) {
      throw new IllegalStateException("Page storage key must be owner-scoped");
    }
    if (byteSize <= 0) {
      throw new IllegalStateException("Page byte size must be positive");
    }
    if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalStateException("Page requires a lowercase SHA-256 checksum");
    }
  }

  public Long getId() {
    return id;
  }

  public SubmissionDocument getDocument() {
    return document;
  }

  public int getPageNumber() {
    return pageNumber;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getMediaType() {
    return mediaType;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public long getByteSize() {
    return byteSize;
  }

  public String getChecksumSha256() {
    return checksumSha256;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
