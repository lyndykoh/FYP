package com.fttranscendence.grading.model;

import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "submission_documents")
public class SubmissionDocument {

  public enum OwnerRole {
    TUTOR,
    STUDENT
  }

  public enum SourceType {
    PDF,
    IMAGES,
    /** A Tutor-entered result with no uploaded source pages. */
    MANUAL
  }

  public enum Status {
    UPLOADING,
    READY,
    SUBMITTED_FOR_REVIEW
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_user_id", nullable = false)
  private Long ownerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_role", nullable = false, length = 16)
  private OwnerRole ownerRole;

  @Column(name = "worksheet_id", nullable = false)
  private Long worksheetId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  /**
   * The class selected by a Tutor when this source document was uploaded. Historic Student and
   * manual documents predate this field, so it is nullable at database level. New Tutor file
   * uploads must supply it.
   */
  @Column(name = "class_id")
  private Long classId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 16)
  private SourceType sourceType;

  /**
   * A durable, unique scope for page-less manual documents. It prevents retrying a manual result
   * from creating another hidden document.
   */
  @Column(name = "manual_scope_key", length = 160, unique = true)
  private String manualScopeKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status = Status.UPLOADING;

  @OneToMany(
      mappedBy = "document",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("pageNumber ASC, id ASC")
  private List<SubmissionPage> pages = new ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected SubmissionDocument() {}

  public SubmissionDocument(
      Long ownerUserId,
      OwnerRole ownerRole,
      Long worksheetId,
      Long studentId,
      SourceType sourceType) {
    this(ownerUserId, ownerRole, worksheetId, studentId, null, sourceType);
  }

  public SubmissionDocument(
      Long ownerUserId,
      OwnerRole ownerRole,
      Long worksheetId,
      Long studentId,
      Long classId,
      SourceType sourceType) {
    this.ownerUserId = requirePositive(ownerUserId, "Owner user id");
    this.ownerRole = requireValue(ownerRole, "Owner role");
    this.worksheetId = requirePositive(worksheetId, "Worksheet id");
    this.studentId = requirePositive(studentId, "Student id");
    this.classId = classId == null ? null : requirePositive(classId, "Class id");
    this.sourceType = requireValue(sourceType, "Source type");
    this.manualScopeKey =
        sourceType == SourceType.MANUAL
            ? manualScopeKey(ownerUserId, ownerRole, worksheetId, studentId)
            : null;
  }

  public SubmissionPage addPage(DocumentStorage.StoredFile storedFile) {
    ensureUploading();
    if (sourceType == SourceType.MANUAL) {
      throw new IllegalStateException("A manual result cannot contain uploaded pages");
    }
    if (storedFile == null) {
      throw new IllegalArgumentException("Stored file metadata is required");
    }
    validateMediaType(storedFile.mediaType());
    if (pages.stream()
        .anyMatch(page -> page.getChecksumSha256().equals(storedFile.checksumSha256()))) {
      throw new IllegalArgumentException("Duplicate submission page");
    }
    if (sourceType == SourceType.PDF && !pages.isEmpty()) {
      throw new IllegalStateException("A PDF submission accepts exactly one source file");
    }

    SubmissionPage page = new SubmissionPage(this, pages.size() + 1, storedFile);
    pages.add(page);
    return page;
  }

  public void movePage(int fromPageNumber, int toPageNumber) {
    ensureUploading();
    if (sourceType == SourceType.MANUAL) {
      throw new IllegalStateException("A manual result cannot contain uploaded pages");
    }
    if (sourceType == SourceType.PDF) {
      throw new IllegalStateException("A PDF source cannot be reordered");
    }
    if (fromPageNumber <= 0
        || fromPageNumber > pages.size()
        || toPageNumber <= 0
        || toPageNumber > pages.size()) {
      throw new IllegalArgumentException("Page number is outside the document");
    }
    if (fromPageNumber == toPageNumber) {
      return;
    }

    pages.sort(Comparator.comparingInt(SubmissionPage::getPageNumber));
    SubmissionPage movedPage = pages.remove(fromPageNumber - 1);
    pages.add(toPageNumber - 1, movedPage);
    renumberPages();
  }

  public void removePage(int pageNumber) {
    ensureUploading();
    if (sourceType == SourceType.MANUAL) {
      throw new IllegalStateException("A manual result cannot contain uploaded pages");
    }
    pages.sort(Comparator.comparingInt(SubmissionPage::getPageNumber));
    if (pageNumber <= 0 || pageNumber > pages.size()) {
      throw new IllegalArgumentException("Page number is outside the document");
    }
    pages.remove(pageNumber - 1);
    renumberPages();
  }

  public void markReady() {
    ensureUploading();
    validatePages();
    status = Status.READY;
  }

  /** Locks a completed OCR document once canonical answer records exist. */
  public void markSubmittedForReview() {
    if (status != Status.READY) {
      throw new IllegalStateException(
          "Only a ready submission document can be submitted for review");
    }
    status = Status.SUBMITTED_FOR_REVIEW;
  }

  @PrePersist
  protected void beforeInsert() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    validateAggregate();
  }

  @PreUpdate
  protected void beforeUpdate() {
    updatedAt = LocalDateTime.now();
    validateAggregate();
  }

  private void ensureUploading() {
    if (status != Status.UPLOADING) {
      throw new IllegalStateException("A ready submission document is immutable");
    }
  }

  private void validateAggregate() {
    requirePositive(ownerUserId, "Owner user id");
    requireValue(ownerRole, "Owner role");
    requirePositive(worksheetId, "Worksheet id");
    requirePositive(studentId, "Student id");
    requireValue(sourceType, "Source type");
    requireValue(status, "Status");
    if (sourceType == SourceType.MANUAL) {
      String expectedKey = manualScopeKey(ownerUserId, ownerRole, worksheetId, studentId);
      if (!expectedKey.equals(manualScopeKey) || !pages.isEmpty()) {
        throw new IllegalStateException("Manual result document metadata is inconsistent");
      }
    } else if (manualScopeKey != null) {
      throw new IllegalStateException(
          "Uploaded submission documents cannot have a manual scope key");
    }
    if (status == Status.READY || status == Status.SUBMITTED_FOR_REVIEW) {
      validatePages();
    } else {
      validateExistingPages();
    }
  }

  private void validatePages() {
    if (sourceType == SourceType.MANUAL) {
      return;
    }
    if (pages.isEmpty()) {
      throw new IllegalStateException("A submission document requires at least one page");
    }
    if (sourceType == SourceType.PDF && pages.size() != 1) {
      throw new IllegalStateException("A PDF submission requires exactly one source file");
    }
    validateExistingPages();
  }

  private void validateExistingPages() {
    List<SubmissionPage> orderedPages =
        pages.stream().sorted(Comparator.comparingInt(SubmissionPage::getPageNumber)).toList();
    Set<String> checksums = new HashSet<>();
    for (int index = 0; index < orderedPages.size(); index++) {
      SubmissionPage page = orderedPages.get(index);
      if (page.getPageNumber() != index + 1) {
        throw new IllegalStateException("Submission page order must be contiguous");
      }
      validateMediaType(page.getMediaType());
      if (!checksums.add(page.getChecksumSha256())) {
        throw new IllegalStateException("Duplicate submission page");
      }
    }
  }

  private void validateMediaType(String mediaType) {
    boolean validPdf = sourceType == SourceType.PDF && "application/pdf".equals(mediaType);
    boolean validImage =
        sourceType == SourceType.IMAGES
            && ("image/jpeg".equals(mediaType) || "image/png".equals(mediaType));
    if (!validPdf && !validImage) {
      throw new IllegalArgumentException("File media type does not match document source type");
    }
  }

  private static String manualScopeKey(
      Long ownerUserId, OwnerRole ownerRole, Long worksheetId, Long studentId) {
    return "manual:" + ownerUserId + ":" + ownerRole + ":" + worksheetId + ":" + studentId;
  }

  private void renumberPages() {
    for (int index = 0; index < pages.size(); index++) {
      pages.get(index).setPageNumber(index + 1);
    }
  }

  private static Long requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  private static <T> T requireValue(T value, String fieldName) {
    if (value == null) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerUserId() {
    return ownerUserId;
  }

  public OwnerRole getOwnerRole() {
    return ownerRole;
  }

  public Long getWorksheetId() {
    return worksheetId;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getClassId() {
    return classId;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public Status getStatus() {
    return status;
  }

  public List<SubmissionPage> getPages() {
    return pages.stream().sorted(Comparator.comparingInt(SubmissionPage::getPageNumber)).toList();
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
