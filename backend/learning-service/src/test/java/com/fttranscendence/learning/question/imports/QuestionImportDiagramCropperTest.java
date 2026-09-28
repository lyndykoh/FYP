package com.fttranscendence.learning.question.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QuestionImportDiagramCropperTest {
  @Test
  void cropsTheValidatedRectangleFromOriginalPagePixels() throws Exception {
    BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
    image.setRGB(1, 1, Color.RED.getRGB());
    image.setRGB(2, 1, Color.BLUE.getRGB());
    QuestionImportSourcePage page = page(image);

    QuestionImportDiagramCropper.Crop crop =
        QuestionImportDiagramCropper.crop(page, new QuestionVisionAnalyzer.BoundingBox(1, 1, 2, 1));
    BufferedImage result = ImageIO.read(new ByteArrayInputStream(crop.bytes()));

    assertEquals(2, crop.width());
    assertEquals(1, crop.height());
    assertEquals(Color.RED.getRGB(), result.getRGB(0, 0));
    assertEquals(Color.BLUE.getRGB(), result.getRGB(1, 0));
  }

  @Test
  void rejectsOutOfBoundsRectanglesBeforeCreatingCropBytes() throws Exception {
    QuestionImportSourcePage page = page(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB));

    assertThrows(
        QuestionImportDiagramCropper.InvalidCropException.class,
        () ->
            QuestionImportDiagramCropper.crop(
                page, new QuestionVisionAnalyzer.BoundingBox(3, 2, 2, 1)));
  }

  private QuestionImportSourcePage page(BufferedImage image) throws Exception {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      QuestionImportBatch batch =
          new QuestionImportBatch("source.png", 101L, QuestionImportBatch.Status.RUNNING);
      byte[] bytes = output.toByteArray();
      return new QuestionImportSourcePage(
          batch,
          "source.png",
          1,
          "image/png",
          com.fttranscendence.learning.question.ImageFingerprint.sha256(bytes),
          bytes,
          image.getWidth(),
          image.getHeight(),
          "",
          null);
    }
  }
}
