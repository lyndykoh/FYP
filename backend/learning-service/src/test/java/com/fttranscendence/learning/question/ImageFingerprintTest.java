package com.fttranscendence.learning.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageFingerprintTest {
  @Test
  void createsStableExactAndNearImageFingerprints() throws Exception {
    BufferedImage original = gradient();
    BufferedImage nearCopy = gradient();
    nearCopy.setRGB(17, 15, Color.MAGENTA.getRGB());

    byte[] originalBytes = png(original);
    byte[] nearBytes = png(nearCopy);

    assertEquals(ImageFingerprint.sha256(originalBytes), ImageFingerprint.sha256(originalBytes));
    assertNotEquals(ImageFingerprint.sha256(originalBytes), ImageFingerprint.sha256(nearBytes));
    assertEquals(
        0,
        ImageFingerprint.hammingDistance(
            ImageFingerprint.perceptualHash(originalBytes),
            ImageFingerprint.perceptualHash(nearBytes)));
  }

  private static BufferedImage gradient() {
    BufferedImage image = new BufferedImage(18, 16, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        int value = Math.min(255, x * 12 + y * 2);
        image.setRGB(x, y, new Color(value, value, value).getRGB());
      }
    }
    return image;
  }

  private static byte[] png(BufferedImage image) throws Exception {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      return output.toByteArray();
    }
  }
}
