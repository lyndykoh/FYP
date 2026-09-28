package com.fttranscendence.learning.worksheet;

import com.fttranscendence.learning.question.QuestionImage;
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
@Table(name = "worksheet_question_images")
public class WorksheetQuestionImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "worksheet_question_id", nullable = false)
  private WorksheetQuestion worksheetQuestion;

  @Column(nullable = false)
  private int position;

  @Column(name = "original_filename", nullable = false, length = 255)
  private String originalFilename;

  @Column(name = "content_type", nullable = false, length = 16)
  private String contentType;

  @Column(name = "image_bytes", nullable = false, columnDefinition = "bytea")
  private byte[] imageBytes;

  @Column(nullable = false)
  private int width;

  @Column(nullable = false)
  private int height;

  protected WorksheetQuestionImage() {}

  WorksheetQuestionImage(WorksheetQuestion worksheetQuestion, int position, QuestionImage source) {
    this.worksheetQuestion = worksheetQuestion;
    this.position = position;
    originalFilename = source.getOriginalFilename();
    contentType = source.getContentType();
    imageBytes = source.getImageBytes();
    width = source.getWidth();
    height = source.getHeight();
  }

  public Long getId() {
    return id;
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

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
