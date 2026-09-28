package com.fttranscendence.learning.question;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.imageio.ImageIO;

/**
 * Stable local image fingerprints used only to surface possible duplicate questions. They are
 * evidence for tutor review, never an import gate.
 */
public final class ImageFingerprint {
  private static final int DIFFERENCE_HASH_WIDTH = 9;
  private static final int DIFFERENCE_HASH_HEIGHT = 8;

  private ImageFingerprint() {}

  public static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder value = new StringBuilder(digest.length * 2);
      for (byte item : digest) {
        value.append(String.format("%02x", item));
      }
      return value.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available.", exception);
    }
  }

  /**
   * Returns a 64-bit difference hash represented as lower-case hexadecimal. Sampling fixed source
   * coordinates avoids platform-specific image scaling.
   */
  public static String perceptualHash(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
        throw new IllegalArgumentException("Image bytes cannot be fingerprinted.");
      }
      return perceptualHash(image);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Image bytes cannot be fingerprinted.", exception);
    }
  }

  public static String perceptualHash(BufferedImage image) {
    long hash = 0L;
    for (int row = 0; row < DIFFERENCE_HASH_HEIGHT; row++) {
      int sourceY = sampleCoordinate(row, DIFFERENCE_HASH_HEIGHT, image.getHeight());
      for (int column = 0; column < DIFFERENCE_HASH_WIDTH - 1; column++) {
        int left =
            luminance(
                image.getRGB(
                    sampleCoordinate(column, DIFFERENCE_HASH_WIDTH, image.getWidth()), sourceY));
        int right =
            luminance(
                image.getRGB(
                    sampleCoordinate(column + 1, DIFFERENCE_HASH_WIDTH, image.getWidth()),
                    sourceY));
        hash = (hash << 1) | (right > left ? 1L : 0L);
      }
    }
    return String.format("%016x", hash);
  }

  public static int hammingDistance(String firstHash, String secondHash) {
    if (firstHash == null
        || secondHash == null
        || !firstHash.matches("[0-9a-f]{16}")
        || !secondHash.matches("[0-9a-f]{16}")) {
      return Integer.MAX_VALUE;
    }
    return Long.bitCount(
        Long.parseUnsignedLong(firstHash, 16) ^ Long.parseUnsignedLong(secondHash, 16));
  }

  private static int sampleCoordinate(int position, int targetLength, int sourceLength) {
    return Math.min(sourceLength - 1, (position * sourceLength) / targetLength);
  }

  private static int luminance(int argb) {
    int red = (argb >> 16) & 0xff;
    int green = (argb >> 8) & 0xff;
    int blue = argb & 0xff;
    return (299 * red + 587 * green + 114 * blue) / 1000;
  }
}
