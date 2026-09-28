package com.fttranscendence.learning.question.imports;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Creates a PNG from the uploaded page pixels; it has no OCR/text input. */
final class QuestionImportDiagramCropper {
  private QuestionImportDiagramCropper() {}

  static Crop crop(QuestionImportSourcePage page, QuestionVisionAnalyzer.BoundingBox rectangle) {
    validateRectangle(page, rectangle);
    BufferedImage source = decode(page.getPageImageBytes());
    if (source.getWidth() != page.getWidth() || source.getHeight() != page.getHeight()) {
      throw new InvalidCropException("The stored source-page dimensions do not match its pixels.");
    }
    BufferedImage cropped =
        new BufferedImage(rectangle.width(), rectangle.height(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = cropped.createGraphics();
    try {
      graphics.drawImage(
          source.getSubimage(rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height()),
          0,
          0,
          null);
    } finally {
      graphics.dispose();
    }
    return new Crop(encodePng(cropped), cropped.getWidth(), cropped.getHeight());
  }

  private static void validateRectangle(
      QuestionImportSourcePage page, QuestionVisionAnalyzer.BoundingBox rectangle) {
    if (rectangle == null
        || rectangle.x() < 0
        || rectangle.y() < 0
        || rectangle.width() < 1
        || rectangle.height() < 1
        || rectangle.x() >= page.getWidth()
        || rectangle.y() >= page.getHeight()
        || rectangle.width() > page.getWidth() - rectangle.x()
        || rectangle.height() > page.getHeight() - rectangle.y()) {
      throw new InvalidCropException("The diagram rectangle lies outside the original page image.");
    }
  }

  private static BufferedImage decode(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) throw new IOException("No image decoder accepted the source bytes.");
      return image;
    } catch (IOException exception) {
      throw new InvalidCropException("The original page image cannot be cropped.");
    }
  }

  private static byte[] encodePng(BufferedImage image) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable.");
      return output.toByteArray();
    } catch (IOException exception) {
      throw new InvalidCropException("The diagram crop could not be encoded.");
    }
  }

  record Crop(byte[] bytes, int width, int height) {
    Crop {
      bytes = bytes.clone();
    }
  }

  static final class InvalidCropException extends RuntimeException {
    InvalidCropException(String message) {
      super(message);
    }
  }
}
