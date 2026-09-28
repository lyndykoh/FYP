package com.fttranscendence.grading.security;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.storage.DocumentStorage;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

/**
 * Exercises the public HTTP boundary rather than relying on controller method calls. Cross-owner
 * paths deliberately use the same 404 response as missing records so stable database identifiers
 * cannot be enumerated.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DataAccessAuthorizationIntegrationTest {

  private static final String JWT_SECRET = "test-secret-key-that-is-at-least-thirty-two-bytes-long";
  private static final long OWNER_STUDENT_USER_ID = 101L;
  private static final long OTHER_STUDENT_USER_ID = 202L;
  private static final long UNRELATED_TUTOR_USER_ID = 303L;

  @Autowired private MockMvc mockMvc;
  @Autowired private RestTemplate restTemplate;
  @Autowired private DocumentStorage storage;
  @Autowired private SubmissionDocumentRepository documents;
  @Autowired private OcrExtractionRepository extractions;

  private MockRestServiceServer learningServer;
  private Long createdExtractionId;
  private Long createdDocumentId;

  @BeforeEach
  void bindLearningService() {
    learningServer = MockRestServiceServer.bindTo(restTemplate).build();
  }

  @AfterEach
  void removeOwnedFixture() {
    if (createdExtractionId != null) {
      extractions.deleteById(createdExtractionId);
    }
    if (createdDocumentId != null) {
      documents.deleteById(createdDocumentId);
    }
  }

  @Test
  void anonymousAndUnlistedRoutesAreDeniedIncludingTheFormerBulkSubmissionPath() throws Exception {
    mockMvc.perform(get("/api/grading/submissions")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/api/grading/submissions")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            multipart("/api/grading/submission-documents")
                .param("studentId", "1")
                .param("worksheetId", "1")
                .file("files", pngBytes("page")))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            patch("/api/grading/ocr-extractions/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correctedText\":\"answer\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(multipart("/api/grading/ocr").file("file", pngBytes("page")))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/grading/submission-documents/manual-answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"studentId\":1,\"worksheetId\":1,\"answers\":[{\"questionBankId\":1,\"answer\":\"answer\"}],\"submit\":false}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tutorOnlyReviewsRejectStudentsWhileExplicitOcrRoutesAllowOnlyDomainRoles() throws Exception {
    mockMvc
        .perform(
            get("/api/grading/tutor/reviews/100")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/grading/ocr-extractions/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correctedText\":\"answer\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("OCR_EXTRACTION_NOT_FOUND"));

    mockMvc
        .perform(
            multipart("/api/grading/ocr")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                .file("file", pngBytes("retired")))
        .andExpect(status().isForbidden());
  }

  @Test
  void manualAnswersUseAuthoritativeWorksheetScopeAndReturnNotFoundRatherThanAFalseOutage()
      throws Exception {
    learningServer
        .expect(
            once(),
            requestTo(
                "http://localhost:8083/api/learning/internal/submission-authorization/marking-context"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            post("/api/grading/submission-documents/manual-answers")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"studentId\":101,\"worksheetId\":999,\"answers\":[{\"questionBankId\":1,\"answer\":\"answer\"}],\"submit\":false}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_MARKING_CONTEXT_NOT_FOUND"));
    learningServer.verify();
  }

  @Test
  void ruleChecksRequireTutorAndUseNonEnumeratingAuthoritativeQuestionScope() throws Exception {
    mockMvc
        .perform(
            post("/api/grading/tutor/questions/601/rule-check")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"heat conduction\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/grading/tutor/questions/601/rule-check")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"heat conduction\"}"))
        .andExpect(status().isForbidden());

    learningServer
        .expect(once(), requestTo("http://localhost:8083/api/learning/tutor/questions/601"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    mockMvc
        .perform(
            post("/api/grading/tutor/questions/601/rule-check")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"heat conduction\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
    learningServer.verify();

    learningServer.reset();
    learningServer
        .expect(once(), requestTo("http://localhost:8083/api/learning/tutor/questions/601"))
        .andRespond(
            withSuccess(
                """
                {"id":601,"prompt":"Why?","modelAnswer":"Heat conducts.","totalMarks":2,
                 "keywords":["conductor"],"syllabusTopic":{"id":44,"code":"SCI-44"},
                 "markingComponents":[{"position":0,"description":"Explains heat conduction","marks":2,"keywords":["heat conduction"]}]}
                """,
                MediaType.APPLICATION_JSON));
    mockMvc
        .perform(
            post("/api/grading/tutor/questions/601/rule-check")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"heat conduction\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.awardedMarks").value(2.0))
        .andExpect(jsonPath("$.componentResults[0].matched").value(true));
    learningServer.verify();
  }

  @Test
  void mistakeHistoryIsSelfScopedForStudentsAndNonEnumeratingForTutors() throws Exception {
    learningServer
        .expect(once(), requestTo("http://localhost:8083/api/learning/student/profile"))
        .andRespond(withSuccess("{\"id\":501}", MediaType.APPLICATION_JSON));

    mockMvc
        .perform(
            get("/api/grading/mistakes/me")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    learningServer.verify();

    mockMvc
        .perform(
            get("/api/grading/mistakes/students/501")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isForbidden());

    learningServer.reset();
    learningServer
        .expect(once(), requestTo("http://localhost:8083/api/learning/tutor/students/501"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    mockMvc
        .perform(
            get("/api/grading/mistakes/students/501")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MISTAKE_HISTORY_NOT_FOUND"));
    learningServer.verify();

    learningServer.reset();
    learningServer
        .expect(once(), requestTo("http://localhost:8083/api/learning/tutor/students/999999"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    mockMvc
        .perform(
            get("/api/grading/mistakes/students/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MISTAKE_HISTORY_NOT_FOUND"));
    learningServer.verify();
  }

  @Test
  void learningScopeDeniesCrossTutorAndUnrelatedStudentDocumentCreation() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            multipart("/api/grading/submission-documents")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                .param("studentId", "901")
                .param("worksheetId", "401")
                .param("classId", "301")
                .file("files", pngBytes("cross-tutor")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBMISSION_FORBIDDEN"));
    learningServer.verify();

    learningServer.reset();
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    mockMvc
        .perform(
            multipart("/api/grading/submission-documents")
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OTHER_STUDENT_USER_ID))
                .param("studentId", "902")
                .param("worksheetId", "401")
                .file("files", pngBytes("cross-student")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SUBMISSION_FORBIDDEN"));
    learningServer.verify();
  }

  @Test
  void tutorDocumentCreationRequiresAnExplicitClassContext() throws Exception {
    mockMvc
        .perform(
            multipart("/api/grading/submission-documents")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                .param("studentId", "901")
                .param("worksheetId", "401")
                .file("files", pngBytes("missing-class")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SUBMISSION_DOCUMENT"));
    learningServer.verify();
  }

  @Test
  void submissionDocumentCreationRejectsAMissingFileBeforeAuthorisation() throws Exception {
    mockMvc
        .perform(
            multipart("/api/grading/submission-documents")
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                .param("classId", "301")
                .param("studentId", "901")
                .param("worksheetId", "401"))
        .andExpect(status().isBadRequest());
    learningServer.verify();
  }

  @Test
  void authorizedStudentUploadPersistsOnlyStudentAnswerRegionsFromDiagramOcr() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"answers\\",\\"regions\\":[{\\"type\\":\\"diagram\\",\\"text\\":\\"10 N, left\\"},{\\"type\\":\\"printed_content\\",\\"text\\":\\"Figure 1: Forces\\"},{\\"type\\":\\"student_answer\\",\\"text\\":\\"answer\\"}],\\"confidence\\":0.95}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile(
                            "files", "answer.png", "image/png", pngBytes("answer")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                    .param("studentId", "501")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.pages[0].id").isNumber())
            .andExpect(jsonPath("$.pages[0].extractionId").isNumber())
            .andExpect(jsonPath("$.pages[0].text").value("answer"))
            .andExpect(jsonPath("$.pages[0].status").value("READY"))
            .andReturn();
    learningServer.verify();

    Number documentIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Number extractionIdValue =
        JsonPath.read(result.getResponse().getContentAsString(), "$.pages[0].extractionId");
    long documentId = documentIdValue.longValue();
    createdDocumentId = documentId;
    createdExtractionId = extractionIdValue.longValue();
    org.junit.jupiter.api.Assertions.assertTrue(documents.existsById(documentId));
    org.junit.jupiter.api.Assertions.assertTrue(extractions.existsById(createdExtractionId));
    org.junit.jupiter.api.Assertions.assertEquals(
        "answer", extractions.findById(createdExtractionId).orElseThrow().getExtractedText());

    // The Student can reopen only the document they just saved, which is
    // the durable context required by the OCR review screen.
    mockMvc
        .perform(
            get("/api/grading/submission-documents/{documentId}", documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(documentId))
        .andExpect(jsonPath("$.studentId").value(501))
        .andExpect(jsonPath("$.worksheetId").value(401));

    // Role is part of the ownership boundary; a Tutor token with the same
    // numeric subject must not read a Student-owned upload.
    mockMvc
        .perform(
            get("/api/grading/submission-documents/{documentId}", documentId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isNotFound());
  }

  @Test
  void authorizedStudentUploadExtractsTypedTextFromAnEditedPdf() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"answers\\",\\"regions\\":[{\\"type\\":\\"student_answer\\",\\"text\\":\\"x = 42\\"}],\\"confidence\\":0.94}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile(
                            "files",
                            "edited-answer.pdf",
                            "application/pdf",
                            typedPdf(
                                "Worksheet title", "Question 1: solve x", "Edited answer: x = 42")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                    .param("studentId", "501")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pages[0].mediaType").value("application/pdf"))
            .andExpect(jsonPath("$.pages[0].text").value("x = 42"))
            .andExpect(jsonPath("$.pages[0].confidence").value(0.94))
            .andExpect(jsonPath("$.pages[0].status").value("READY"))
            .andReturn();
    learningServer.verify();

    Number documentIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Number extractionIdValue =
        JsonPath.read(result.getResponse().getContentAsString(), "$.pages[0].extractionId");
    createdDocumentId = documentIdValue.longValue();
    createdExtractionId = extractionIdValue.longValue();
  }

  @Test
  void printedOnlyPdfWithNoAnswersDoesNotPersistWorksheetText() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"no_answers\\",\\"regions\\":[{\\"type\\":\\"printed_content\\",\\"text\\":\\"Worksheet title\\"}],\\"confidence\\":0.99}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile(
                            "files",
                            "printed-worksheet.pdf",
                            "application/pdf",
                            typedPdf(
                                "Worksheet title",
                                "Question 1: Calculate 2 + 2",
                                "Instructions: show work")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                    .param("studentId", "501")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pages[0].text").value(""))
            .andExpect(jsonPath("$.pages[0].confidence").value(0.0))
            .andExpect(jsonPath("$.pages[0].status").value("UNREADABLE"))
            .andReturn();
    learningServer.verify();

    storeCreatedArtifacts(result);
    org.junit.jupiter.api.Assertions.assertEquals(
        "", extractions.findById(createdExtractionId).orElseThrow().getExtractedText());
  }

  @Test
  void uncertainImageDoesNotPersistClaimedWorksheetText() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"uncertain\\",\\"regions\\":[],\\"confidence\\":0.5}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile(
                            "files", "uncertain.png", "image/png", pngBytes("printed worksheet")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                    .param("studentId", "501")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pages[0].text").value(""))
            .andExpect(jsonPath("$.pages[0].confidence").value(0.0))
            .andExpect(jsonPath("$.pages[0].status").value("UNREADABLE"))
            .andReturn();
    learningServer.verify();

    storeCreatedArtifacts(result);
    org.junit.jupiter.api.Assertions.assertEquals(
        "", extractions.findById(createdExtractionId).orElseThrow().getExtractedText());
  }

  @Test
  void lowConfidenceAnswerRequiresTutorReview() throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"answers\\",\\"regions\\":[{\\"type\\":\\"student_answer\\",\\"text\\":\\"faint student work\\"}],\\"confidence\\":0.3}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile(
                            "files", "faint-work.png", "image/png", pngBytes("faint student work")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                    .param("studentId", "501")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pages[0].text").value("faint student work"))
            .andExpect(jsonPath("$.pages[0].confidence").value(0.3))
            .andExpect(jsonPath("$.pages[0].status").value("REQUIRES_REVIEW"))
            .andReturn();
    learningServer.verify();

    storeCreatedArtifacts(result);
  }

  @Test
  void tutorUploadPersistsItsClassStudentWorksheetAndOwnerAndCanBeReloadedForOcr()
      throws Exception {
    learningServer
        .expect(
            once(),
            requestTo("http://localhost:8083/api/learning/internal/submission-authorization"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    learningServer
        .expect(once(), requestTo("http://localhost/ai-test"))
        .andRespond(
            withSuccess(
                """
                {"choices":[{"message":{"content":"{\\"status\\":\\"answers\\",\\"regions\\":[{\\"type\\":\\"student_answer\\",\\"text\\":\\"water evaporates\\"}],\\"confidence\\":0.93}"}}]}
                """,
                MediaType.APPLICATION_JSON));

    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/grading/submission-documents")
                    .file(
                        new MockMultipartFile("files", "water.png", "image/png", pngBytes("water")))
                    .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID))
                    .param("classId", "301")
                    .param("studentId", "901")
                    .param("worksheetId", "401")
                    .param("worksheetQuestionId", "601"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.classId").value(301))
            .andExpect(jsonPath("$.studentId").value(901))
            .andExpect(jsonPath("$.worksheetId").value(401))
            .andExpect(jsonPath("$.uploadedByTutorId").value(UNRELATED_TUTOR_USER_ID))
            .andExpect(jsonPath("$.status").value("READY"))
            .andReturn();
    learningServer.verify();

    Number documentIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Number extractionIdValue =
        JsonPath.read(result.getResponse().getContentAsString(), "$.pages[0].extractionId");
    createdDocumentId = documentIdValue.longValue();
    createdExtractionId = extractionIdValue.longValue();
    SubmissionDocument persisted = documents.findById(createdDocumentId).orElseThrow();
    org.junit.jupiter.api.Assertions.assertEquals(301L, persisted.getClassId());
    org.junit.jupiter.api.Assertions.assertEquals(901L, persisted.getStudentId());
    org.junit.jupiter.api.Assertions.assertEquals(401L, persisted.getWorksheetId());
    org.junit.jupiter.api.Assertions.assertEquals(
        UNRELATED_TUTOR_USER_ID, persisted.getOwnerUserId());

    mockMvc
        .perform(
            get("/api/grading/submission-documents/{id}", createdDocumentId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", UNRELATED_TUTOR_USER_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(createdDocumentId))
        .andExpect(jsonPath("$.pages[0].text").value("water evaporates"));
    mockMvc
        .perform(
            get("/api/grading/submission-documents/{id}", createdDocumentId)
                .header(HttpHeaders.AUTHORIZATION, bearer("TUTOR", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_DOCUMENT_NOT_FOUND"));
    mockMvc
        .perform(
            get("/api/grading/submission-documents/{id}", createdDocumentId)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBMISSION_DOCUMENT_NOT_FOUND"));
  }

  @Test
  void studentCanCorrectOnlyTheirOwnExtractionAndForeignIdsAreNonEnumerating() throws Exception {
    long extractionId = ownedExtraction();

    mockMvc
        .perform(
            patch("/api/grading/ocr-extractions/{id}", extractionId)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OTHER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correctedText\":\"other answer\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("OCR_EXTRACTION_NOT_FOUND"));

    mockMvc
        .perform(
            patch("/api/grading/ocr-extractions/{id}", extractionId + 100_000)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OTHER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correctedText\":\"other answer\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("OCR_EXTRACTION_NOT_FOUND"));

    mockMvc
        .perform(
            patch("/api/grading/ocr-extractions/{id}", extractionId)
                .header(HttpHeaders.AUTHORIZATION, bearer("STUDENT", OWNER_STUDENT_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correctedText\":\"my corrected answer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("my corrected answer"));
  }

  private long ownedExtraction() {
    DocumentStorage.StoredFile stored =
        storage.store(OWNER_STUDENT_USER_ID, "answer.png", "image/png", pngBytes("answer"));
    SubmissionDocument document =
        new SubmissionDocument(
            OWNER_STUDENT_USER_ID,
            SubmissionDocument.OwnerRole.STUDENT,
            401L,
            501L,
            SubmissionDocument.SourceType.IMAGES);
    document.addPage(stored);
    document.markReady();
    document = documents.saveAndFlush(document);
    createdDocumentId = document.getId();

    OcrExtraction extraction =
        extractions.saveAndFlush(
            new OcrExtraction(document.getPages().get(0), 601L, "original answer", 0.4, "test"));
    createdExtractionId = extraction.getId();
    return extraction.getId();
  }

  private static byte[] pngBytes(String text) {
    byte[] signature = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    byte[] body = text.getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[signature.length + body.length];
    System.arraycopy(signature, 0, bytes, 0, signature.length);
    System.arraycopy(body, 0, bytes, signature.length, body.length);
    return bytes;
  }

  private void storeCreatedArtifacts(MvcResult result) throws Exception {
    Number documentIdValue = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    Number extractionIdValue =
        JsonPath.read(result.getResponse().getContentAsString(), "$.pages[0].extractionId");
    createdDocumentId = documentIdValue.longValue();
    createdExtractionId = extractionIdValue.longValue();
  }

  private static byte[] typedPdf(String... lines) throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());

      try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0))) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(72, 700);
        for (String line : lines) {
          content.showText(line);
          content.newLineAtOffset(0, -18);
        }
        content.endText();
      }

      document.save(output);
      return output.toByteArray();
    }
  }

  private static String bearer(String role, long userId) {
    String jwt =
        Jwts.builder()
            .setSubject(role.toLowerCase() + "@example.com")
            .claim("role", role)
            .claim("userId", userId)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
            .signWith(
                Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                SignatureAlgorithm.HS256)
            .compact();
    return "Bearer " + jwt;
  }
}
