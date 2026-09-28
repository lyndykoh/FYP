package com.fttranscendence.grading.submission;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.model.SubmissionPage;
import com.fttranscendence.grading.storage.DocumentStorage;
import com.fttranscendence.grading.storage.FileSystemDocumentStorage;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SubmissionDocumentIntegrationTest {

  private static final long OWNER_ID = 101L;

  @Autowired private EntityManager entityManager;

  @TempDir Path temporaryDirectory;

  @Test
  void storesAndPersistsAPdfWithItsSourceMetadata() {
    DocumentStorage storage = storage("pdf", 1024);
    byte[] pdf = pdfBytes("one-page");
    DocumentStorage.StoredFile storedFile =
        storage.store(OWNER_ID, "science worksheet.pdf", "application/pdf", pdf);
    SubmissionDocument document =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.TUTOR,
            301L,
            201L,
            101L,
            SubmissionDocument.SourceType.PDF);
    document.addPage(storedFile);
    document.markReady();

    entityManager.persist(document);
    entityManager.flush();
    Long documentId = document.getId();
    entityManager.clear();

    SubmissionDocument persisted = entityManager.find(SubmissionDocument.class, documentId);
    assertNotNull(persisted);
    assertEquals(SubmissionDocument.Status.READY, persisted.getStatus());
    assertEquals(SubmissionDocument.SourceType.PDF, persisted.getSourceType());
    assertEquals(OWNER_ID, persisted.getOwnerUserId());
    assertEquals(301L, persisted.getWorksheetId());
    assertEquals(201L, persisted.getStudentId());
    assertEquals(101L, persisted.getClassId());
    assertEquals(1, persisted.getPages().size());

    SubmissionPage page = persisted.getPages().get(0);
    assertEquals(1, page.getPageNumber());
    assertEquals("science worksheet.pdf", page.getOriginalFilename());
    assertEquals("application/pdf", page.getMediaType());
    assertEquals(pdf.length, page.getByteSize());
    assertTrue(page.getChecksumSha256().matches("[0-9a-f]{64}"));
    assertArrayEquals(pdf, storage.read(OWNER_ID, page.getStorageKey()));
    assertThrows(IllegalStateException.class, () -> persisted.addPage(storedFile));
  }

  @Test
  void persistsMultipleImagesInTheTutorSelectedOrder() {
    DocumentStorage storage = storage("images", 1024);
    DocumentStorage.StoredFile first =
        storage.store(OWNER_ID, "front.png", "image/png", pngBytes("front"));
    DocumentStorage.StoredFile second =
        storage.store(OWNER_ID, "back.jpg", "image/jpeg", jpegBytes("back"));
    SubmissionDocument document =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.STUDENT,
            302L,
            202L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(first);
    document.addPage(second);
    document.movePage(2, 1);
    document.markReady();

    entityManager.persist(document);
    entityManager.flush();
    Long documentId = document.getId();
    entityManager.clear();

    List<SubmissionPage> pages =
        entityManager.find(SubmissionDocument.class, documentId).getPages();
    assertEquals(List.of(1, 2), pages.stream().map(SubmissionPage::getPageNumber).toList());
    assertEquals(
        List.of("back.jpg", "front.png"),
        pages.stream().map(SubmissionPage::getOriginalFilename).toList());
  }

  @Test
  void rejectsDuplicatePagesAndSourceTypeMismatches() {
    DocumentStorage storage = storage("duplicates", 1024);
    byte[] repeatedImage = pngBytes("same-page");
    DocumentStorage.StoredFile first =
        storage.store(OWNER_ID, "page-one.png", "image/png", repeatedImage);
    DocumentStorage.StoredFile duplicate =
        storage.store(OWNER_ID, "page-one-copy.png", "image/png", repeatedImage);
    SubmissionDocument imageDocument =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.STUDENT,
            303L,
            203L,
            SubmissionDocument.SourceType.IMAGES);
    imageDocument.addPage(first);

    assertThrows(IllegalArgumentException.class, () -> imageDocument.addPage(duplicate));

    SubmissionDocument pdfDocument =
        new SubmissionDocument(
            OWNER_ID,
            SubmissionDocument.OwnerRole.TUTOR,
            304L,
            204L,
            SubmissionDocument.SourceType.PDF);
    assertThrows(IllegalArgumentException.class, () -> pdfDocument.addPage(first));
    assertThrows(IllegalStateException.class, pdfDocument::markReady);
  }

  @Test
  void rejectsUnsupportedOversizedAndMislabelledFiles() {
    DocumentStorage storage = storage("validation", 16);

    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "page.gif", "image/gif", new byte[] {'G', 'I', 'F'}));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            storage.store(
                OWNER_ID, "large.pdf", "application/pdf", pdfBytes("too-large-for-limit")));
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "fake.pdf", "application/pdf", pngBytes("png")));
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "empty.png", "image/png", new byte[0]));
  }

  @Test
  void rejectsUnsafeFilenamesAndCrossOwnerStorageAccess() {
    DocumentStorage storage = storage("ownership", 1024);

    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "../page.png", "image/png", pngBytes("unsafe")));
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "folder/page.png", "image/png", pngBytes("unsafe")));
    assertThrows(
        IllegalArgumentException.class,
        () -> storage.store(OWNER_ID, "folder\\page.png", "image/png", pngBytes("unsafe")));

    DocumentStorage.StoredFile storedFile =
        storage.store(OWNER_ID, "safe.png", "image/png", pngBytes("safe"));
    assertThrows(
        SecurityException.class, () -> storage.read(OWNER_ID + 1, storedFile.storageKey()));
    assertThrows(
        SecurityException.class, () -> storage.delete(OWNER_ID + 1, storedFile.storageKey()));
    assertArrayEquals(pngBytes("safe"), storage.read(OWNER_ID, storedFile.storageKey()));
  }

  @Test
  void reportsStorageFailuresWithoutPersistingMetadata() throws IOException {
    Path blockedRoot = temporaryDirectory.resolve("blocked-root");
    FileSystemDocumentStorage storage = new FileSystemDocumentStorage(blockedRoot, 1024);
    Files.writeString(blockedRoot, "this path is a file, not a directory");

    assertThrows(
        DocumentStorage.StorageException.class,
        () -> storage.store(OWNER_ID, "page.png", "image/png", pngBytes("page")));
    assertEquals(
        0L,
        entityManager
            .createQuery("select count(d) from SubmissionDocument d", Long.class)
            .getSingleResult());
  }

  private FileSystemDocumentStorage storage(String testName, long maxFileSizeBytes) {
    return new FileSystemDocumentStorage(temporaryDirectory.resolve(testName), maxFileSizeBytes);
  }

  private byte[] pdfBytes(String content) {
    return ("%PDF-1.7\n" + content).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private byte[] pngBytes(String content) {
    byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    byte[] body = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] bytes = new byte[signature.length + body.length];
    System.arraycopy(signature, 0, bytes, 0, signature.length);
    System.arraycopy(body, 0, bytes, signature.length, body.length);
    return bytes;
  }

  private byte[] jpegBytes(String content) {
    byte[] signature = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    byte[] body = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] bytes = new byte[signature.length + body.length];
    System.arraycopy(signature, 0, bytes, 0, signature.length);
    System.arraycopy(body, 0, bytes, signature.length, body.length);
    return bytes;
  }
}
