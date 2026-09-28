package com.fttranscendence.grading.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSystemDocumentStorage implements DocumentStorage {

  private static final String PDF_MEDIA_TYPE = "application/pdf";
  private static final String JPEG_MEDIA_TYPE = "image/jpeg";
  private static final String PNG_MEDIA_TYPE = "image/png";

  private static final Map<String, String> FILE_EXTENSIONS =
      Map.of(
          PDF_MEDIA_TYPE, ".pdf",
          JPEG_MEDIA_TYPE, ".jpg",
          PNG_MEDIA_TYPE, ".png");

  private final Path rootDirectory;
  private final long maxFileSizeBytes;

  @Autowired
  public FileSystemDocumentStorage(
      @Value("${document.storage.root}") String rootDirectory,
      @Value("${document.storage.max-file-size-bytes}") long maxFileSizeBytes) {
    this(Path.of(rootDirectory), maxFileSizeBytes);
  }

  public FileSystemDocumentStorage(Path rootDirectory, long maxFileSizeBytes) {
    if (rootDirectory == null) {
      throw new IllegalArgumentException("Storage root is required");
    }
    if (maxFileSizeBytes <= 0) {
      throw new IllegalArgumentException("Maximum file size must be positive");
    }
    this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    this.maxFileSizeBytes = maxFileSizeBytes;
  }

  @Override
  public StoredFile store(
      long ownerUserId, String originalFilename, String declaredMediaType, byte[] content) {
    validateOwner(ownerUserId);
    validateFilename(originalFilename);
    validateContentLength(content);

    String detectedMediaType = detectMediaType(content);
    validateDeclaredMediaType(declaredMediaType, detectedMediaType);

    String storageKey = createStorageKey(ownerUserId, detectedMediaType);
    Path destination = resolveOwnedPath(ownerUserId, storageKey);
    writeUploadedFile(destination, content);

    return new StoredFile(
        storageKey, originalFilename, detectedMediaType, content.length, sha256(content));
  }

  @Override
  public byte[] read(long ownerUserId, String storageKey) {
    Path storedFile = resolveOwnedPath(ownerUserId, storageKey);
    try {
      return Files.readAllBytes(storedFile);
    } catch (IOException exception) {
      throw new StorageException("Unable to read submission file", exception);
    }
  }

  @Override
  public void delete(long ownerUserId, String storageKey) {
    Path storedFile = resolveOwnedPath(ownerUserId, storageKey);
    try {
      Files.deleteIfExists(storedFile);
    } catch (IOException exception) {
      throw new StorageException("Unable to delete submission file", exception);
    }
  }

  private void validateOwner(long ownerUserId) {
    if (ownerUserId <= 0) {
      throw new IllegalArgumentException("Owner user id must be positive");
    }
  }

  private void validateFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new IllegalArgumentException("Original filename is required");
    }
    if (originalFilename.length() > 255
        || originalFilename.equals(".")
        || originalFilename.equals("..")
        || originalFilename.indexOf('/') >= 0
        || originalFilename.indexOf('\\') >= 0
        || originalFilename.chars().anyMatch(character -> character < 32 || character == 127)) {
      throw new IllegalArgumentException("Unsafe original filename");
    }
  }

  private void validateContentLength(byte[] content) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("Uploaded file must not be empty");
    }

    if (content.length > maxFileSizeBytes) {
      throw new IllegalArgumentException("Uploaded file exceeds the configured size limit");
    }
  }

  private String createStorageKey(long ownerUserId, String mediaType) {
    return ownerUserId + "/" + UUID.randomUUID() + FILE_EXTENSIONS.get(mediaType);
  }

  private void writeUploadedFile(Path destination, byte[] content) {
    Path temporaryFile = null;

    try {
      Files.createDirectories(destination.getParent());
      temporaryFile = Files.createTempFile(destination.getParent(), ".upload-", ".tmp");
      Files.write(
          temporaryFile, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      moveIntoPlace(temporaryFile, destination);
      temporaryFile = null;
    } catch (IOException exception) {
      throw new StorageException("Unable to store submission file", exception);
    } finally {
      deleteTemporaryFile(temporaryFile);
    }
  }

  private void deleteTemporaryFile(Path temporaryFile) {
    if (temporaryFile == null) {
      return;
    }

    try {
      Files.deleteIfExists(temporaryFile);
    } catch (IOException ignored) {
      // The primary storage failure remains the actionable error.
    }
  }

  private String detectMediaType(byte[] content) {
    if (startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'})) {
      return PDF_MEDIA_TYPE;
    }
    if (startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
      return PNG_MEDIA_TYPE;
    }
    if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
      return JPEG_MEDIA_TYPE;
    }
    throw new IllegalArgumentException("Unsupported file type");
  }

  private void validateDeclaredMediaType(String declaredMediaType, String detectedMediaType) {
    if (declaredMediaType == null || declaredMediaType.isBlank()) {
      return;
    }
    String normalizedDeclaredMediaType = declaredMediaType.trim().toLowerCase(Locale.ROOT);
    if (normalizedDeclaredMediaType.equals("image/jpg")) {
      normalizedDeclaredMediaType = JPEG_MEDIA_TYPE;
    }

    if (!normalizedDeclaredMediaType.equals(detectedMediaType)) {
      throw new IllegalArgumentException("Declared media type does not match file content");
    }
  }

  private Path resolveOwnedPath(long ownerUserId, String storageKey) {
    validateOwner(ownerUserId);
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("Storage key is required");
    }

    Path relativePath;
    try {
      relativePath = Path.of(storageKey).normalize();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid storage key", exception);
    }
    if (relativePath.isAbsolute()
        || relativePath.getNameCount() != 2
        || !relativePath.getName(0).toString().equals(Long.toString(ownerUserId))
        || relativePath.toString().contains("..")) {
      throw new SecurityException("Storage key does not belong to the authenticated owner");
    }

    Path resolved = rootDirectory.resolve(relativePath).normalize();
    Path ownerDirectory = rootDirectory.resolve(Long.toString(ownerUserId)).normalize();
    if (!resolved.startsWith(ownerDirectory)) {
      throw new SecurityException("Storage key escapes the owner directory");
    }
    return resolved;
  }

  private boolean startsWith(byte[] content, byte[] signature) {
    if (content.length < signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if (content[index] != signature[index]) {
        return false;
      }
    }
    return true;
  }

  private void moveIntoPlace(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, destination);
    }
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
