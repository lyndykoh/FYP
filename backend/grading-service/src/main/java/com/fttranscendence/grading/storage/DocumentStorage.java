package com.fttranscendence.grading.storage;

public interface DocumentStorage {

  StoredFile store(
      long ownerUserId, String originalFilename, String declaredMediaType, byte[] content);

  byte[] read(long ownerUserId, String storageKey);

  void delete(long ownerUserId, String storageKey);

  record StoredFile(
      String storageKey,
      String originalFilename,
      String mediaType,
      long byteSize,
      String checksumSha256) {
    public StoredFile {
      if (storageKey == null || storageKey.isBlank()) {
        throw new IllegalArgumentException("Storage key is required");
      }
      if (originalFilename == null || originalFilename.isBlank()) {
        throw new IllegalArgumentException("Original filename is required");
      }
      if (mediaType == null || mediaType.isBlank()) {
        throw new IllegalArgumentException("Media type is required");
      }
      if (byteSize <= 0) {
        throw new IllegalArgumentException("Byte size must be positive");
      }
      if (checksumSha256 == null || !checksumSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("A lowercase SHA-256 checksum is required");
      }
    }
  }

  class StorageException extends RuntimeException {
    public StorageException(String message) {
      super(message);
    }

    public StorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
