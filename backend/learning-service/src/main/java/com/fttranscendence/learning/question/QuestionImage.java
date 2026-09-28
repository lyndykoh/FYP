package com.fttranscendence.learning.question;

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
@Table(name = "question_images")
public class QuestionImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "question_id", nullable = false)
  private Question question;

  @Column(nullable = false)
  private int position;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "content_type", nullable = false, length = 16)
  private String contentType;

  @Column(name = "image_bytes", nullable = false, columnDefinition = "bytea")
  private byte[] imageBytes;

  @Column(name = "image_sha256", length = 64)
  private String imageSha256;

  @Column(name = "image_perceptual_hash", length = 16)
  private String imagePerceptualHash;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  protected QuestionImage() {}

  QuestionImage(
      Question question,
      int position,
      String originalFilename,
      String contentType,
      byte[] imageBytes,
      int width,
      int height) {
    this.question = question;
    this.position = position;
    this.originalFilename = originalFilename;
    this.contentType = contentType;
    this.imageBytes = imageBytes.clone();
    imageSha256 = ImageFingerprint.sha256(imageBytes);
    imagePerceptualHash = ImageFingerprint.perceptualHash(imageBytes);
    this.width = width;
    this.height = height;
  }

  public Long getId() {
    return id;
  }

  public int getPosition() {
    return position;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getImageBytes() {
    return imageBytes.clone();
  }

  public String getImageSha256() {
    return imageSha256;
  }

  public String getImagePerceptualHash() {
    return imagePerceptualHash;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
