package com.fttranscendence.learning.question;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionImageService {
  public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
  public static final int MAX_IMAGES_PER_QUESTION = 10;
  public static final long MAX_IMAGE_PIXELS = 20_000_000L;

  private final QuestionRepository questions;
  private final QuestionImageRepository images;

  public QuestionImageService(QuestionRepository questions, QuestionImageRepository images) {
    this.questions = questions;
    this.images = images;
  }

  @Transactional
  public ImageSummary upload(long questionId, MultipartFile file) {
    Question question =
        questions.findById(questionId).orElseThrow(QuestionService.QuestionNotFoundException::new);
    if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
      throw new InvalidImageException("Choose a PNG or JPEG image no larger than 8 MB.");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException exception) {
      throw new InvalidImageException("The image could not be read.");
    }
    return attachValidated(question, file.getOriginalFilename(), bytes, null);
  }

  /**
   * Stores a diagram cropped from an accepted import source page. Import processing uses this same
   * validation and persistence path as tutor image uploads; it never attempts to recreate a diagram
   * from OCR text.
   */
  @Transactional
  public ImageSummary attachImportedCrop(
      long questionId, String filename, String declaredContentType, byte[] bytes) {
    Question question =
        questions.findById(questionId).orElseThrow(QuestionService.QuestionNotFoundException::new);
    return attachValidated(question, filename, bytes, declaredContentType);
  }

  @Transactional(readOnly = true)
  public ImageContent content(long questionId, long imageId) {
    QuestionImage image =
        images.findByIdAndQuestion_Id(imageId, questionId).orElseThrow(ImageNotFoundException::new);
    return new ImageContent(
        image.getContentType(), image.getOriginalFilename(), image.getImageBytes());
  }

  @Transactional
  public void remove(long questionId, long imageId) {
    Question question =
        questions.findById(questionId).orElseThrow(QuestionService.QuestionNotFoundException::new);
    QuestionImage image =
        images.findByIdAndQuestion_Id(imageId, questionId).orElseThrow(ImageNotFoundException::new);
    question.removeImage(image);
    images.delete(image);
  }

  static String detectedContentType(byte[] bytes) {
    boolean png =
        bytes.length >= 8
            && bytes[0] == (byte) 0x89
            && bytes[1] == 0x50
            && bytes[2] == 0x4e
            && bytes[3] == 0x47
            && bytes[4] == 0x0d
            && bytes[5] == 0x0a
            && bytes[6] == 0x1a
            && bytes[7] == 0x0a;
    boolean jpeg =
        bytes.length >= 3
            && bytes[0] == (byte) 0xff
            && bytes[1] == (byte) 0xd8
            && bytes[2] == (byte) 0xff;
    if (png) return "image/png";
    if (jpeg) return "image/jpeg";
    throw new InvalidImageException("Only PNG and JPEG images are supported.");
  }

  private ImageSummary attachValidated(
      Question question, String filename, byte[] bytes, String declaredContentType) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
      throw new InvalidImageException("Choose a PNG or JPEG image no larger than 8 MB.");
    }
    String contentType = detectedContentType(bytes);
    if (declaredContentType != null && !declaredContentType.equals(contentType)) {
      throw new InvalidImageException("The declared image type does not match its contents.");
    }
    BufferedImage decoded = decode(bytes);
    if ((long) decoded.getWidth() * decoded.getHeight() > MAX_IMAGE_PIXELS) {
      throw new InvalidImageException("Image dimensions are too large.");
    }
    if (question.getImages().size() >= MAX_IMAGES_PER_QUESTION) {
      throw new InvalidImageException("A question can have at most 10 images.");
    }
    QuestionImage image =
        question.addImage(
            safeFilename(filename), contentType, bytes, decoded.getWidth(), decoded.getHeight());
    return ImageSummary.from(images.save(image));
  }

  private BufferedImage decode(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1) throw new IOException();
      return image;
    } catch (IOException exception) {
      throw new InvalidImageException("The uploaded file is not a valid image.");
    }
  }

  private String safeFilename(String source) {
    String value = source == null ? "diagram" : source.replaceAll("[\\r\\n\\\\/]", "_").trim();
    if (value.isBlank()) value = "diagram";
    return value.substring(0, Math.min(value.length(), 255));
  }

  public record ImageSummary(long id, String filename, String contentType, int width, int height) {
    static ImageSummary from(QuestionImage image) {
      return new ImageSummary(
          image.getId(),
          image.getOriginalFilename(),
          image.getContentType(),
          image.getWidth(),
          image.getHeight());
    }
  }

  public record ImageContent(String contentType, String filename, byte[] bytes) {}

  public static final class ImageNotFoundException extends RuntimeException {}

  public static final class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
      super(message);
    }
  }
}
