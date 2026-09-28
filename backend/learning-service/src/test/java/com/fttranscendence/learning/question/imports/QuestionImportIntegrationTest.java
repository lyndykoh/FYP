package com.fttranscendence.learning.question.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.learning.question.Question;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionImportIntegrationTest {
  private static final String SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private QuestionImportProcessingWorker worker;

  @Test
  void keepsImageImportsAsReviewDraftsUntilTutorEditsAndConfirmsThem() throws Exception {
    int questionCountBeforeUpload =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "scan.png", "image/png", onePixelPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.candidates.length()").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    assertEquals(
        questionCountBeforeUpload,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class));

    mockMvc
        .perform(
            get("/api/learning/tutor/question-imports/{batchId}", batchId)
                .header("Authorization", bearer("STUDENT")))
        .andExpect(status().isForbidden());
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/question-imports/{batchId}/source-pages/{pageId}/image",
                    batchId,
                    pageId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk());
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_source_pages WHERE batch_id = ?",
            Integer.class,
            batchId));
  }

  @Test
  void rendersPdfPagesAndSeparatesNumberedQuestionTextIntoReviewDrafts() throws Exception {
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(new MockMultipartFile("files", "paper.pdf", "application/pdf", numberedPdf()))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.candidates.length()").value(2))
        .andExpect(
            jsonPath("$.candidates[0].prompt")
                .value(org.hamcrest.Matchers.containsString("1. Explain evaporation.")))
        .andExpect(
            jsonPath("$.candidates[1].prompt")
                .value(org.hamcrest.Matchers.containsString("2. Name a gas.")))
        .andExpect(jsonPath("$.candidates[0].source.pageNumber").value(1));
  }

  @Test
  void rejectsUntrustedSignaturesBrokenImageDecodesAndOversizedPixelHeaders() throws Exception {
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(
                    new MockMultipartFile(
                        "files", "pretend.pdf", "application/pdf", "not a PDF".getBytes()))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isBadRequest());
    var tooManyFiles =
        multipart("/api/learning/tutor/question-imports").header("Authorization", bearer("TUTOR"));
    byte[] image = onePixelPng();
    for (int index = 0; index < 21; index++) {
      tooManyFiles.file(
          new MockMultipartFile("files", "scan-" + index + ".png", "image/png", image));
    }
    mockMvc.perform(tooManyFiles).andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(new MockMultipartFile("files", "broken.png", "image/png", pngHeader(1, 1)))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(
                    new MockMultipartFile(
                        "files", "large.png", "image/png", pngHeader(5_000, 5_000)))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void acceptsDecodedJpegAndRejectsPdfPageAndRasterizationLimits() throws Exception {
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(new MockMultipartFile("files", "scan.jpg", "image/jpeg", onePixelJpeg()))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(
                    new MockMultipartFile(
                        "files", "too-many-pages.pdf", "application/pdf", pdfWithPages(101)))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            multipart("/api/learning/tutor/question-imports")
                .file(
                    new MockMultipartFile(
                        "files", "huge-page.pdf", "application/pdf", oversizedPdf()))
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyTheTutorWhoCreatedABatchCanReadItsRetainedSourceOrDrafts() throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "scan.png", "image/png", onePixelPng()))
                    .header("Authorization", bearer("TUTOR", 101L)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);

    mockMvc
        .perform(
            get("/api/learning/tutor/question-imports/{batchId}", batchId)
                .header("Authorization", bearer("TUTOR", 202L)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/question-imports/{batchId}/source-pages/{pageId}/image",
                    batchId,
                    pageId)
                .header("Authorization", bearer("TUTOR", 202L)))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsExcessiveDiagramRegionsBeforeAnyCropIsPersisted() throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "scan.png", "image/png", diagramPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    java.util.List<QuestionVisionAnalyzer.Diagram> diagrams =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(
                index ->
                    new QuestionVisionAnalyzer.Diagram(
                        "diagram-" + index,
                        null,
                        new QuestionVisionAnalyzer.BoundingBox(0, 0, 1, 1)))
            .toList();
    QuestionVisionAnalyzer.Candidate candidate =
        new QuestionVisionAnalyzer.Candidate(
            1,
            "Question",
            "Answer",
            99,
            Question.QuestionType.DIAGRAM,
            Question.Difficulty.FOUNDATION,
            java.util.List.of("diagram"),
            null,
            diagrams,
            "");

    assertThrows(
        QuestionImportDiagramCropper.InvalidCropException.class,
        () ->
            worker.storeResult(
                batchId,
                pageId,
                new QuestionVisionAnalyzer.PageAnalysis(java.util.List.of(candidate), null)));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_diagram_crops WHERE batch_id = ?",
            Integer.class,
            batchId));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void retriesFailedVisionWorkThenRetainsSourceAuditRowsInFailedBatch() throws Exception {
    int questionCountBeforeUpload =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "scan.png", "image/png", onePixelPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");

    worker.processQueuedBatches();
    Thread.sleep(20);
    worker.processQueuedBatches();
    Thread.sleep(20);
    worker.processQueuedBatches();

    assertEquals(
        "READY_FOR_REVIEW",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_batches WHERE id = ?", String.class, batchId));
    assertEquals(
        3,
        jdbcTemplate.queryForObject(
            "SELECT attempt_count FROM question_import_batches WHERE id = ?",
            Integer.class,
            batchId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_source_pages WHERE batch_id = ? AND processing_error IS NOT NULL",
            Integer.class,
            batchId));
    assertEquals(
        "UNCERTAIN",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_candidates WHERE batch_id = ?",
            String.class,
            batchId));
    assertEquals(
        questionCountBeforeUpload,
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class));
    long topicId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM syllabus_topics WHERE active = TRUE AND node_type IN ('TOPIC', 'SUBTOPIC') ORDER BY id LIMIT 1",
            Long.class);
    long candidateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_candidates WHERE batch_id = ?", Long.class, batchId);
    mockMvc
        .perform(
            put(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}",
                    batchId,
                    candidateId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"syllabusTopicId":%d,"prompt":"Manual transcription","modelAnswer":"Manual answer",
                    "totalMarks":1.0,"questionType":"OPEN_ENDED","difficulty":"FOUNDATION","includeSourceImage":false}
                    """
                        .formatted(topicId)))
        .andExpect(status().isOk());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void reclaimsStaleWorkerClaimsWithoutDuplicatingAlreadyStoredReviewDrafts() throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "scan.png", "image/png", onePixelPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(
            java.util.List.of(
                new QuestionVisionAnalyzer.Candidate(
                    1,
                    "Question",
                    "Answer",
                    99,
                    Question.QuestionType.OPEN_ENDED,
                    Question.Difficulty.FOUNDATION,
                    java.util.List.of(),
                    null,
                    java.util.List.of(),
                    "")),
            null));
    jdbcTemplate.update(
        """
            UPDATE question_import_batches
            SET status = 'RUNNING', attempt_count = 1,
                processing_started_at = DATEADD('MINUTE', -4, CURRENT_TIMESTAMP)
            WHERE id = ?
            """,
        batchId);

    worker.processQueuedBatches();
    assertEquals(
        "QUEUED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_batches WHERE id = ?", String.class, batchId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_candidates WHERE batch_id = ?",
            Integer.class,
            batchId));
    worker.processQueuedBatches();
    assertEquals(
        "READY_FOR_REVIEW",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_batches WHERE id = ?", String.class, batchId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_candidates WHERE batch_id = ?",
            Integer.class,
            batchId));
  }

  @Test
  void persistsDiagramCropProvenancePreviewsOriginalPixelsAndDoesNotDuplicateOnRestart()
      throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "diagram.png", "image/png", diagramPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    QuestionVisionAnalyzer.Candidate candidate =
        new QuestionVisionAnalyzer.Candidate(
            1,
            "1. Label the diagram.",
            "Labels shown.",
            98,
            Question.QuestionType.DIAGRAM,
            Question.Difficulty.FOUNDATION,
            java.util.List.of("diagram"),
            null,
            java.util.List.of(
                new QuestionVisionAnalyzer.Diagram(
                    "diagram-1", "1a", new QuestionVisionAnalyzer.BoundingBox(1, 1, 2, 1))),
            "");

    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(java.util.List.of(candidate), null));
    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(java.util.List.of(candidate), null));

    long candidateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_candidates WHERE batch_id = ?", Long.class, batchId);
    long cropId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_diagram_crops WHERE batch_id = ?", Long.class, batchId);
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_diagram_crops WHERE batch_id = ?",
            Integer.class,
            batchId));
    assertEquals(
        "1a",
        jdbcTemplate.queryForObject(
            "SELECT sub_question_id FROM question_import_diagram_crops WHERE id = ?",
            String.class,
            cropId));

    mockMvc
        .perform(
            get("/api/learning/tutor/question-imports/{batchId}", batchId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[0].id").value(candidateId))
        .andExpect(jsonPath("$.candidates[0].diagramCrops[0].regionId").value("diagram-1"))
        .andExpect(jsonPath("$.candidates[0].diagramCrops[0].subQuestionId").value("1a"))
        .andExpect(jsonPath("$.candidates[0].diagramCrops[0].rectangle.x").value(1));
    mockMvc
        .perform(
            get(
                    "/api/learning/tutor/question-imports/{batchId}/diagram-crops/{cropId}/image",
                    batchId,
                    cropId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType(MediaType.IMAGE_PNG));
  }

  @Test
  void importsAcceptedDiagramCropsThroughQuestionImagesAndLeavesTextOnlyCandidatesImageFree()
      throws Exception {
    long topicId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM syllabus_topics WHERE active = TRUE AND node_type IN ('TOPIC', 'SUBTOPIC') ORDER BY id LIMIT 1",
            Long.class);
    long diagramQuestionId =
        storeAndImportCandidate(
            topicId,
            diagramPng(),
            java.util.List.of(
                new QuestionVisionAnalyzer.Diagram(
                    "diagram-1", null, new QuestionVisionAnalyzer.BoundingBox(1, 1, 2, 1))));
    long textQuestionId = storeAndImportCandidate(topicId, onePixelPng(), java.util.List.of());

    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_images WHERE question_id = ?",
            Integer.class,
            diagramQuestionId));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_images WHERE question_id = ?",
            Integer.class,
            textQuestionId));
    assertEquals(
        "ARCHIVED",
        jdbcTemplate.queryForObject(
            "SELECT archive_state FROM questions WHERE id = ?", String.class, diagramQuestionId));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_source_pages page "
                + "JOIN question_import_candidates candidate ON candidate.source_page_id = page.id "
                + "WHERE candidate.imported_question_id = ?",
            Integer.class,
            diagramQuestionId));
  }

  @Test
  void keepsSuggestionsLineageAndCropOriginsWhenTutorsReviewMergeSplitAndRejectDrafts()
      throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "review.png", "image/png", diagramPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(
            java.util.List.of(
                visionCandidate(1, "1. Explain evaporation.", "diagram-one", 1),
                visionCandidate(2, "2. Explain evaporation.", "diagram-two", 2)),
            null));
    worker.finishIfComplete(batchId);
    java.util.List<Long> candidateIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM question_import_candidates WHERE batch_id = ? ORDER BY candidate_number",
            Long.class,
            batchId);
    long firstId = candidateIds.get(0);
    long secondId = candidateIds.get(1);
    long firstCropId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_diagram_crops WHERE candidate_id = ?",
            Long.class,
            firstId);

    mockMvc
        .perform(
            get("/api/learning/tutor/question-imports/{batchId}", batchId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[0].suggestions.questionType").value("DIAGRAM"))
        .andExpect(
            jsonPath("$.candidates[0].duplicateWarnings[0].target.kind").value("IMPORT_DRAFT"))
        .andExpect(jsonPath("$.candidates[0].duplicateWarnings[0].signals[0].code").exists());
    mockMvc
        .perform(
            post(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}/reject",
                    batchId,
                    secondId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"duplicate scan\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[1].status").value("REJECTED"));
    mockMvc
        .perform(
            post(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}/restore",
                    batchId,
                    secondId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates[1].status").value("READY_FOR_REVIEW"));
    mockMvc
        .perform(
            post("/api/learning/tutor/question-imports/{batchId}/diagram-crops/move", batchId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"cropIds\":[" + firstCropId + "],\"targetCandidateId\":" + secondId + "}"))
        .andExpect(status().isOk());
    assertEquals(
        secondId,
        jdbcTemplate.queryForObject(
            "SELECT candidate_id FROM question_import_diagram_crops WHERE id = ?",
            Long.class,
            firstCropId));
    assertEquals(
        firstId,
        jdbcTemplate.queryForObject(
            "SELECT original_candidate_id FROM question_import_diagram_crops WHERE id = ?",
            Long.class,
            firstCropId));

    mockMvc
        .perform(
            post("/api/learning/tutor/question-imports/{batchId}/candidates/merge", batchId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"targetCandidateId\":"
                        + firstId
                        + ",\"candidateIds\":["
                        + firstId
                        + ","
                        + secondId
                        + "]}"))
        .andExpect(status().isOk());
    assertEquals(
        "SUPERSEDED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_candidates WHERE id = ?", String.class, secondId));

    mockMvc
        .perform(
            post(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}/split",
                    batchId,
                    firstId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"drafts\":[{\"prompt\":\"Part A\",\"modelAnswer\":\"Answer A\"},{\"prompt\":\"Part B\",\"modelAnswer\":\"Answer B\"}]}"))
        .andExpect(status().isOk());
    assertEquals(
        2,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM question_import_candidates WHERE parent_candidate_id = ?",
            Integer.class,
            firstId));
    assertEquals(
        "SUPERSEDED",
        jdbcTemplate.queryForObject(
            "SELECT status FROM question_import_candidates WHERE id = ?", String.class, firstId));
  }

  @Test
  void exposesPersistedLocalDuplicateSignalsWithoutBlockingTutorImport() throws Exception {
    long topicId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM syllabus_topics WHERE active = TRUE AND node_type IN ('TOPIC', 'SUBTOPIC') ORDER BY id LIMIT 1",
            Long.class);
    long importedQuestionId =
        storeAndImportCandidate(
            topicId,
            diagramPng(),
            java.util.List.of(
                new QuestionVisionAnalyzer.Diagram(
                    "full-page", null, new QuestionVisionAnalyzer.BoundingBox(0, 0, 4, 3))));
    assertEquals(
        64,
        jdbcTemplate.queryForObject(
            "SELECT length(source_checksum) FROM question_import_source_pages "
                + "WHERE id = (SELECT source_page_id FROM question_import_candidates WHERE imported_question_id = ?)",
            Integer.class,
            importedQuestionId));
    assertEquals(
        64,
        jdbcTemplate.queryForObject(
            "SELECT length(image_sha256) FROM question_images WHERE question_id = ?",
            Integer.class,
            importedQuestionId));

    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(
                        new MockMultipartFile(
                            "files", "same-source.png", "image/png", diagramPng()))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(
            java.util.List.of(visionCandidate(1, "1. Explain the evidence.", "full-page", 0)),
            null));
    worker.finishIfComplete(batchId);
    long candidateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_candidates WHERE batch_id = ?", Long.class, batchId);
    mockMvc
        .perform(
            put(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}",
                    batchId,
                    candidateId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"syllabusTopicId":%d,"prompt":"1. Explain the evidence.","modelAnswer":"Accepted answer.",
                    "totalMarks":1.0,"questionType":"DIAGRAM","difficulty":"FOUNDATION","includeSourceImage":true}
                    """
                        .formatted(topicId)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/learning/tutor/question-imports/{batchId}", batchId)
                .header("Authorization", bearer("TUTOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.candidates[0].duplicateWarnings[0].target.kind").value("IMPORTED_QUESTION"))
        .andExpect(
            jsonPath(
                    "$.candidates[0].duplicateWarnings[0].signals[?(@.code == 'NORMALIZED_PROMPT_SIMILARITY')]")
                .isNotEmpty())
        .andExpect(
            jsonPath(
                    "$.candidates[0].duplicateWarnings[0].signals[?(@.code == 'SOURCE_CHECKSUM_PAGE_IDENTITY')]")
                .isNotEmpty())
        .andExpect(
            jsonPath(
                    "$.candidates[0].duplicateWarnings[0].signals[?(@.code == 'EXACT_PAGE_IMAGE_SHA256')]")
                .isNotEmpty());
  }

  private static QuestionVisionAnalyzer.Candidate visionCandidate(
      int number, String prompt, String regionId, int x) {
    return new QuestionVisionAnalyzer.Candidate(
        number,
        prompt,
        "Accepted answer.",
        98,
        Question.QuestionType.DIAGRAM,
        Question.Difficulty.FOUNDATION,
        java.util.List.of("diagram"),
        null,
        java.util.List.of(
            new QuestionVisionAnalyzer.Diagram(
                regionId, null, new QuestionVisionAnalyzer.BoundingBox(x, 1, 1, 1))),
        "");
  }

  private long storeAndImportCandidate(
      long topicId, byte[] image, java.util.List<QuestionVisionAnalyzer.Diagram> diagrams)
      throws Exception {
    String response =
        mockMvc
            .perform(
                multipart("/api/learning/tutor/question-imports")
                    .file(new MockMultipartFile("files", "source.png", "image/png", image))
                    .header("Authorization", bearer("TUTOR")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long batchId = idAfter(response, "\\\"id\\\":(\\d+)");
    long pageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_source_pages WHERE batch_id = ?", Long.class, batchId);
    worker.storeResult(
        batchId,
        pageId,
        new QuestionVisionAnalyzer.PageAnalysis(
            java.util.List.of(
                new QuestionVisionAnalyzer.Candidate(
                    1,
                    "1. Explain the evidence.",
                    "Accepted answer.",
                    98,
                    Question.QuestionType.DIAGRAM,
                    Question.Difficulty.FOUNDATION,
                    java.util.List.of("diagram"),
                    null,
                    diagrams,
                    "")),
            null));
    worker.finishIfComplete(batchId);
    long candidateId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM question_import_candidates WHERE batch_id = ?", Long.class, batchId);
    mockMvc
        .perform(
            put(
                    "/api/learning/tutor/question-imports/{batchId}/candidates/{candidateId}",
                    batchId,
                    candidateId)
                .header("Authorization", bearer("TUTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"syllabusTopicId":%d,"prompt":"1. Explain the evidence.","modelAnswer":"Accepted answer.",
                    "totalMarks":1.0,"questionType":"DIAGRAM","difficulty":"FOUNDATION","includeSourceImage":true}
                    """
                        .formatted(topicId)))
        .andExpect(status().isOk());
    String imported =
        mockMvc
            .perform(
                post("/api/learning/tutor/question-imports/{batchId}/import", batchId)
                    .header("Authorization", bearer("TUTOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"candidateIds\":[" + candidateId + "]}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return idAfter(imported, "\\\"questionIds\\\":\\[(\\d+)");
  }

  private static long idAfter(String json, String expression) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(json);
    if (!matcher.find()) throw new AssertionError("Missing identifier in response: " + expression);
    return Long.parseLong(matcher.group(1));
  }

  private static String bearer(String role) {
    return bearer(role, 101L);
  }

  private static String bearer(String role, long userId) {
    Instant now = Instant.now();
    return "Bearer "
        + Jwts.builder()
            .setSubject("tutor@example.com")
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(now.plusSeconds(600)))
            .signWith(
                Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
  }

  private static byte[] onePixelPng() throws Exception {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private static byte[] onePixelJpeg() throws Exception {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpeg", output);
    return output.toByteArray();
  }

  private static byte[] pngHeader(int width, int height) {
    java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(24);
    header.put(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    header.putInt(13).put(new byte[] {0x49, 0x48, 0x44, 0x52});
    header.putInt(width).putInt(height);
    return header.array();
  }

  private static byte[] diagramPng() throws Exception {
    BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
    image.setRGB(1, 1, java.awt.Color.RED.getRGB());
    image.setRGB(2, 1, java.awt.Color.BLUE.getRGB());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  private static byte[] numberedPdf() throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(
            new PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, 700);
        content.showText("1. Explain evaporation.");
        content.newLineAtOffset(0, -20);
        content.showText("Answer: Water gains heat energy.");
        content.newLineAtOffset(0, -20);
        content.showText("2. Name a gas.");
        content.newLineAtOffset(0, -20);
        content.showText("Answer: Oxygen.");
        content.endText();
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] pdfWithPages(int pageCount) throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      for (int page = 0; page < pageCount; page++) document.addPage(new PDPage());
      document.save(output);
      return output.toByteArray();
    }
  }

  private static byte[] oversizedPdf() throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage(new PDRectangle(100_000, 100_000)));
      document.save(output);
      return output.toByteArray();
    }
  }
}
