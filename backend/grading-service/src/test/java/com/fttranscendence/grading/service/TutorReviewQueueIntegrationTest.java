package com.fttranscendence.grading.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    properties = {
      "learning.service.url=http://localhost/learning",
      "learning.service.sync-key=test-sync-key"
    })
@Transactional
class TutorReviewQueueIntegrationTest {
  private static final AuthenticatedUser TUTOR =
      new AuthenticatedUser(101L, "tutor@example.test", "TUTOR");
  private static final long STUDENT_ID = 201L;

  @Autowired private MarkingReviewService reviews;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private SubmissionRepository submissions;
  @Autowired private DocumentStorage storage;
  @Autowired private RestTemplate restTemplate;

  private MockRestServiceServer server;
  private final List<DocumentStorage.StoredFile> storedPages = new ArrayList<>();

  @BeforeEach
  void setUp() {
    server = MockRestServiceServer.bindTo(restTemplate).build();
  }

  @AfterEach
  void removeStoredPage() {
    for (DocumentStorage.StoredFile storedPage : storedPages) {
      storage.delete(101L, storedPage.storageKey());
    }
  }

  @Test
  void listsOnlySubmittedAnswersAndStreamsOnlyTheirAuthorizedOriginalPage() {
    Submission submitted = submittedImageAnswer();
    SubmissionDocument draftDocument = readyImageDocument();
    Submission draft =
        submissions.saveAndFlush(
            Submission.createAnswer(
                draftDocument,
                402L,
                502L,
                "Private draft answer",
                "Model answer",
                new BigDecimal("2.00"),
                601L,
                "SCI-601"));

    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var queue = reviews.listPendingReviews(TUTOR, "Bearer tutor-token");

    assertEquals(1, queue.size());
    var item = queue.get(0);
    assertEquals(submitted.getId(), item.submissionId());
    assertEquals("Ada Student", item.studentName());
    assertEquals(submitted.getSubmissionDocument().getId(), item.submissionDocumentId());
    assertEquals(401L, item.worksheetQuestionId());
    assertEquals("IMAGES", item.sourceType());
    assertEquals(Submission.ReviewStatus.PENDING_REVIEW, item.reviewStatus());
    assertEquals(true, item.sourceAvailable());
    assertFalse(
        queue.stream().anyMatch(candidate -> candidate.submissionId().equals(draft.getId())));
    server.verify();

    server.reset();
    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var source = reviews.sourceForReview(TUTOR, "Bearer tutor-token", submitted.getId());
    assertEquals(submitted.getId(), source.submissionId());
    assertEquals(1, source.pages().size());
    assertEquals("worksheet-answer.png", source.pages().get(0).originalFilename());
    assertEquals("image/png", source.pages().get(0).mediaType());
    server.verify();

    server.reset();
    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var page =
        reviews.sourcePageForReview(
            TUTOR, "Bearer tutor-token", submitted.getId(), source.pages().get(0).id());
    assertEquals("image/png", page.mediaType());
    assertArrayEquals(pngBytes("submitted answer"), page.content());
    server.verify();

    server.reset();
    expectDirectory("[]");
    assertThrows(
        MarkingReviewService.ReviewNotFound.class,
        () -> reviews.sourceForReview(TUTOR, "Bearer tutor-token", submitted.getId()));
    server.verify();
  }

  @Test
  void keepsTutorUploadedSourceDocumentsAvailableForTheSubmittedReviewFlow() {
    Submission submitted = submittedTutorImageAnswer();

    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var queue = reviews.listPendingReviews(TUTOR, "Bearer tutor-token");

    assertEquals(1, queue.size());
    var item = queue.get(0);
    assertEquals(submitted.getId(), item.submissionId());
    assertEquals(submitted.getSubmissionDocument().getId(), item.submissionDocumentId());
    assertEquals("Ada Student", item.studentName());
    assertEquals("IMAGES", item.sourceType());
    assertEquals(true, item.sourceAvailable());
    server.verify();

    server.reset();
    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var source = reviews.sourceForReview(TUTOR, "Bearer tutor-token", submitted.getId());
    assertEquals(submitted.getSubmissionDocument().getId(), source.submissionDocumentId());
    assertEquals(1, source.pages().size());
    assertEquals("tutor-upload.png", source.pages().get(0).originalFilename());
    server.verify();

    server.reset();
    expectDirectory("[{\"id\":201,\"fullName\":\"Ada Student\"}]");
    var page =
        reviews.sourcePageForReview(
            TUTOR, "Bearer tutor-token", submitted.getId(), source.pages().get(0).id());
    assertEquals("image/png", page.mediaType());
    assertArrayEquals(pngBytes("tutor uploaded answer"), page.content());
    server.verify();
  }

  private Submission submittedImageAnswer() {
    SubmissionDocument document = readyImageDocument();
    Submission submission =
        submissions.saveAndFlush(
            Submission.createAnswer(
                document,
                401L,
                501L,
                "Student answer",
                "Model answer",
                new BigDecimal("2.00"),
                601L,
                "SCI-601"));
    document.markSubmittedForReview();
    documents.saveAndFlush(document);
    return submission;
  }

  private Submission submittedTutorImageAnswer() {
    DocumentStorage.StoredFile storedPage =
        storage.store(101L, "tutor-upload.png", "image/png", pngBytes("tutor uploaded answer"));
    storedPages.add(storedPage);
    SubmissionDocument document =
        new SubmissionDocument(
            101L,
            SubmissionDocument.OwnerRole.TUTOR,
            301L,
            STUDENT_ID,
            401L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(storedPage);
    document.markReady();
    document = documents.saveAndFlush(document);

    Submission submission =
        submissions.saveAndFlush(
            Submission.createAnswer(
                document,
                401L,
                501L,
                "Tutor uploaded student answer",
                "Model answer",
                new BigDecimal("2.00"),
                601L,
                "SCI-601"));
    document.markSubmittedForReview();
    documents.saveAndFlush(document);
    return submission;
  }

  private SubmissionDocument readyImageDocument() {
    DocumentStorage.StoredFile storedPage =
        storage.store(101L, "worksheet-answer.png", "image/png", pngBytes("submitted answer"));
    storedPages.add(storedPage);
    SubmissionDocument document =
        new SubmissionDocument(
            101L,
            SubmissionDocument.OwnerRole.STUDENT,
            301L,
            STUDENT_ID,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(storedPage);
    document.markReady();
    return documents.saveAndFlush(document);
  }

  private void expectDirectory(String response) {
    server
        .expect(once(), requestTo("http://localhost/learning/api/learning/tutor/students"))
        .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
  }

  private static byte[] pngBytes(String contents) {
    byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    byte[] body = contents.getBytes(StandardCharsets.UTF_8);
    byte[] image = Arrays.copyOf(signature, signature.length + body.length);
    System.arraycopy(body, 0, image, signature.length, body.length);
    return image;
  }
}
