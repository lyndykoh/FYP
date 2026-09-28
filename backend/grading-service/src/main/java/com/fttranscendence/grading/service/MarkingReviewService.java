package com.fttranscendence.grading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fttranscendence.grading.model.AnswerReview;
import com.fttranscendence.grading.model.DiagnosticCategory;
import com.fttranscendence.grading.model.MasterySyncOutbox;
import com.fttranscendence.grading.model.MistakeType;
import com.fttranscendence.grading.model.Submission;
import com.fttranscendence.grading.model.SubmissionDocument;
import com.fttranscendence.grading.model.SubmissionPage;
import com.fttranscendence.grading.ocr.OcrExtraction;
import com.fttranscendence.grading.repository.MasterySyncOutboxRepository;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.repository.SubmissionDocumentRepository;
import com.fttranscendence.grading.repository.SubmissionRepository;
import com.fttranscendence.grading.security.AuthenticatedUser;
import com.fttranscendence.grading.storage.DocumentStorage;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Coordinates advisory marking while keeping final marks behind Tutor approval. */
@Service
public class MarkingReviewService {
  private final SubmissionRepository submissions;
  private final SubmissionDocumentRepository documents;
  private final OcrExtractionRepository extractions;
  private final LearningAuthorizationClient learning;
  private final AiGradingService ai;
  private final MasterySyncOutboxRepository masteryOutbox;
  private final ObjectMapper objectMapper;
  private final DocumentStorage storage;

  public MarkingReviewService(
      SubmissionRepository submissions,
      SubmissionDocumentRepository documents,
      OcrExtractionRepository extractions,
      LearningAuthorizationClient learning,
      AiGradingService ai,
      MasterySyncOutboxRepository masteryOutbox,
      ObjectMapper objectMapper,
      DocumentStorage storage) {
    this.submissions = submissions;
    this.documents = documents;
    this.extractions = extractions;
    this.learning = learning;
    this.ai = ai;
    this.masteryOutbox = masteryOutbox;
    this.objectMapper = objectMapper;
    this.storage = storage;
  }

  /**
   * Lists only canonical, submitted answers that still need a decision. Student-owned drafts never
   * reach this query because they remain DRAFT or belong to a document that was never submitted.
   */
  @Transactional
  public List<TutorReviewQueueItem> listPendingReviews(AuthenticatedUser user, String bearer) {
    Map<Long, String> studentNameById = tutorStudentNames(user, bearer);
    if (studentNameById.isEmpty()) {
      return List.of();
    }
    return submissions
        .findByReviewStatusAndStudentIdInAndSubmissionDocumentStatusOrderByCreatedAtAsc(
            Submission.ReviewStatus.PENDING_REVIEW,
            studentNameById.keySet(),
            SubmissionDocument.Status.SUBMITTED_FOR_REVIEW)
        .stream()
        .map(
            submission ->
                TutorReviewQueueItem.from(
                    submission, studentNameById.get(submission.getStudentId())))
        .toList();
  }

  /** Returns source metadata only after a Tutor directory-scope check. */
  @Transactional
  public SubmittedSource sourceForReview(AuthenticatedUser user, String bearer, long submissionId) {
    Submission submission = submittedSourceSubmission(user, bearer, submissionId);
    return SubmittedSource.from(submission.getId(), submission.getSubmissionDocument());
  }

  /** Reads an original source page after verifying its parent review scope. */
  @Transactional
  public SourcePageContent sourcePageForReview(
      AuthenticatedUser user, String bearer, long submissionId, long pageId) {
    if (pageId <= 0) {
      throw new ReviewNotFound();
    }
    Submission submission = submittedSourceSubmission(user, bearer, submissionId);
    SubmissionDocument document = submission.getSubmissionDocument();
    SubmissionPage page =
        document.getPages().stream()
            .filter(candidate -> candidate.getId().equals(pageId))
            .findFirst()
            .orElseThrow(ReviewNotFound::new);
    byte[] content = storage.read(document.getOwnerUserId(), page.getStorageKey());
    return new SourcePageContent(page.getOriginalFilename(), page.getMediaType(), content);
  }

  @Transactional
  public MarkingReview createAdvisoryReview(
      AuthenticatedUser user, String bearer, CreateRequest request) {
    requirePositive(request.submissionDocumentId(), "Submission document id");
    requirePositive(request.worksheetQuestionId(), "Worksheet question id");
    requirePositive(request.questionBankId(), "Question bank id");
    SubmissionDocument document =
        documents.findById(request.submissionDocumentId()).orElseThrow(ReviewNotFound::new);
    if (document.getStatus() != SubmissionDocument.Status.READY) {
      throw new InvalidReviewRequest("The submission document is not ready for review.");
    }
    learning.assertCanReview(user, bearer, document.getStudentId());
    LearningAuthorizationClient.QuestionContext question =
        learning.loadQuestion(user, bearer, request.questionBankId());
    if (question.totalMarks().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidReviewRequest("Question maximum marks must be positive.");
    }

    List<OcrExtraction> questionExtractions =
        extractions.findByPageDocumentIdOrderByPagePageNumberAsc(document.getId()).stream()
            .filter(
                extraction ->
                    request.worksheetQuestionId().equals(extraction.getWorksheetQuestionId()))
            .toList();
    if (questionExtractions.isEmpty()) {
      throw new InvalidReviewRequest("No OCR text is associated with this worksheet question.");
    }
    String answer = answerFromExtractions(questionExtractions, false);

    Submission submission =
        createAnswerSubmission(
            document, request.worksheetQuestionId(), request.questionBankId(), answer, question);
    AiGradingService.AiMarkingResult suggestion =
        evaluateAndRecordSuggestion(submission, question, answer);
    submission.nextMasterySyncRevision();
    Submission saved = submissions.saveAndFlush(submission);
    enqueueReviewState(saved, user.userId(), "PENDING_REVIEW");
    return MarkingReview.from(saved, suggestion.providerResponseValid());
  }

  /**
   * Converts an actor-owned OCR document into canonical answer records. The actor may be the
   * Student or their Tutor uploading on their behalf; Learning supplies the trusted rubric and
   * owning Tutor through the integration boundary.
   */
  @Transactional
  public SubmissionForTutorReviewResponse submitOcrForTutorReview(
      AuthenticatedUser user, long submissionDocumentId, OcrSubmissionRequest request) {
    requirePositive(submissionDocumentId, "Submission document id");
    requireSubmissionActor(user);

    SubmissionDocument document =
        documents
            .findByIdAndOwnerUserIdAndOwnerRole(
                submissionDocumentId,
                user.userId(),
                SubmissionDocument.OwnerRole.valueOf(user.role()))
            .orElseThrow(ReviewNotFound::new);
    List<Submission> existingSubmissions =
        submissions.findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(document.getId());
    if (document.getStatus() == SubmissionDocument.Status.SUBMITTED_FOR_REVIEW) {
      if (existingSubmissions.isEmpty()) {
        throw new IllegalStateException("Submitted document is missing its answer records.");
      }
      return SubmissionForTutorReviewResponse.from(document.getId(), existingSubmissions);
    }
    if (document.getStatus() != SubmissionDocument.Status.READY) {
      throw new InvalidReviewRequest("The submission document is not ready for review.");
    }
    if (!existingSubmissions.isEmpty()) {
      throw new IllegalStateException("Ready document already has answer records.");
    }

    List<OcrExtraction> documentExtractions =
        extractions.findByPageDocumentIdOrderByPagePageNumberAsc(document.getId());
    Map<Long, Long> questionBankIdByExtractionId = validatedMappings(request, documentExtractions);
    LearningAuthorizationClient.SubmissionMarkingContext context =
        learning.loadSubmissionMarkingContext(
            user, document.getStudentId(), document.getWorksheetId(), document.getClassId());

    Map<Long, List<OcrExtraction>> extractionsByQuestionBankId =
        assignExtractionsToQuestions(
            documentExtractions, questionBankIdByExtractionId, context.questionsByQuestionBankId());

    List<Submission> pendingSubmissions =
        createOcrSubmissions(
            document, extractionsByQuestionBankId, context.questionsByQuestionBankId());
    List<Submission> savedSubmissions = submissions.saveAllAndFlush(pendingSubmissions);
    document.markSubmittedForReview();
    documents.saveAndFlush(document);
    enqueuePendingReviewStates(savedSubmissions, context.tutorUserId());
    return SubmissionForTutorReviewResponse.from(document.getId(), savedSubmissions);
  }

  /**
   * Saves or submits typed student answers using the exact same canonical Submission aggregate used
   * by OCR. A page-less MANUAL document is the durable submission scope; its unique
   * owner/worksheet/student key makes draft saves and repeated submit clicks idempotent.
   */
  @Transactional
  public ManualAnswerResponse saveManualAnswers(
      AuthenticatedUser user, ManualAnswerRequest request) {
    requireSubmissionActor(user);
    requirePositive(request == null ? null : request.studentId(), "Student id");
    requirePositive(request == null ? null : request.worksheetId(), "Worksheet id");
    requireClassForTutorManualAnswer(user, request.classId());

    LearningAuthorizationClient.SubmissionMarkingContext context =
        learning.loadSubmissionMarkingContext(
            user, request.studentId(), request.worksheetId(), request.classId());
    SubmissionDocument document =
        manualDocument(
            user.userId(),
            SubmissionDocument.OwnerRole.valueOf(user.role()),
            request.worksheetId(),
            request.studentId(),
            request.classId());
    List<Submission> existingSubmissions =
        submissions.findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(document.getId());
    if (document.getStatus() == SubmissionDocument.Status.SUBMITTED_FOR_REVIEW) {
      return ManualAnswerResponse.submitted(document.getId(), existingSubmissions);
    }
    if (document.getStatus() != SubmissionDocument.Status.READY) {
      throw new InvalidReviewRequest("The manual submission is not ready.");
    }

    Map<Long, Submission> submissionByQuestionBankId =
        existingSubmissions.stream()
            .collect(Collectors.toMap(Submission::getWorksheetQuestionId, Function.identity()));
    Set<Long> enteredQuestionBankIds = new HashSet<>();
    List<Submission> changedSubmissions = new ArrayList<>();
    if (request.answers() != null) {
      for (ManualAnswerEntry entry : request.answers()) {
        Submission changedSubmission =
            updateManualAnswer(
                entry,
                document,
                context.questionsByQuestionBankId(),
                submissionByQuestionBankId,
                enteredQuestionBankIds);
        if (changedSubmission != null) {
          changedSubmissions.add(changedSubmission);
        }
      }
    }
    if (!changedSubmissions.isEmpty()) {
      submissions.saveAllAndFlush(changedSubmissions);
    }

    List<Submission> canonicalSubmissions =
        submissions.findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(document.getId());
    if (!Boolean.TRUE.equals(request.submit())) {
      return ManualAnswerResponse.draft(document.getId(), canonicalSubmissions);
    }
    if (canonicalSubmissions.isEmpty()) {
      throw new InvalidReviewRequest(
          "Enter at least one answer before submitting for Tutor review.");
    }

    document.markSubmittedForReview();
    documents.saveAndFlush(document);
    for (Submission submission : canonicalSubmissions) {
      // A draft may be saved many times, but this state event is emitted
      // once when the document is made immutable and reviewable.
      submission.submitForTutorReview();
      submission.nextMasterySyncRevision();
      Submission saved = submissions.saveAndFlush(submission);
      enqueueReviewState(saved, context.tutorUserId(), "PENDING_REVIEW");
    }
    return ManualAnswerResponse.submitted(document.getId(), canonicalSubmissions);
  }

  /** Reloads an actor's own manual draft after independently revalidating its worksheet scope. */
  @Transactional
  public ManualAnswerResponse loadManualAnswers(
      AuthenticatedUser user, long studentId, long worksheetId, Long classId) {
    requireSubmissionActor(user);
    requirePositive(studentId, "Student id");
    requirePositive(worksheetId, "Worksheet id");
    requireClassForTutorManualAnswer(user, classId);

    learning.loadSubmissionMarkingContext(user, studentId, worksheetId, classId);
    return documents
        .findByOwnerUserIdAndOwnerRoleAndWorksheetIdAndStudentIdAndSourceType(
            user.userId(),
            SubmissionDocument.OwnerRole.valueOf(user.role()),
            worksheetId,
            studentId,
            SubmissionDocument.SourceType.MANUAL)
        .map(this::manualAnswerResponse)
        .orElseGet(ManualAnswerResponse::empty);
  }

  /**
   * Persists a Tutor-entered result through the same canonical submission, approval, score-boundary
   * and audit-history path as an OCR review.
   */
  @Transactional
  public MarkingReview createManualResult(
      AuthenticatedUser user, String bearer, ManualResultRequest request) {
    List<MarkingReview> created =
        createManualResults(
            user,
            bearer,
            new ManualResultBatchRequest(
                request.worksheetId(),
                request.studentId(),
                List.of(
                    new ManualResultEntry(
                        request.questionBankId(),
                        request.answer(),
                        request.marks(),
                        request.feedback()))));
    return created.get(0);
  }

  /**
   * Records a selected student's manually entered worksheet marks as one all-or-nothing approval
   * operation. Context, bounds and duplicates are checked for every row before a manual document or
   * submission is written.
   */
  @Transactional
  public List<MarkingReview> createManualResults(
      AuthenticatedUser user, String bearer, ManualResultBatchRequest request) {
    ManualResultValidation validation = validateManualResults(user, bearer, request);

    SubmissionDocument document =
        manualDocument(
            user.userId(),
            SubmissionDocument.OwnerRole.TUTOR,
            request.worksheetId(),
            request.studentId(),
            null);
    ensureManualResultsDoNotExist(document, validation.entries());

    try {
      List<Submission> pendingSubmissions =
          createApprovedManualSubmissions(document, user.userId(), validation);
      List<Submission> savedSubmissions = submissions.saveAllAndFlush(pendingSubmissions);
      enqueueApprovedManualResultEvents(savedSubmissions, user.userId());
      return savedSubmissions.stream()
          .map(submission -> MarkingReview.from(submission, null))
          .toList();
    } catch (DataIntegrityViolationException exception) {
      throw new ManualResultAlreadyExists();
    }
  }

  private ManualResultValidation validateManualResults(
      AuthenticatedUser user, String bearer, ManualResultBatchRequest request) {
    requirePositiveManual(request.worksheetId(), "Worksheet id");
    requirePositiveManual(request.studentId(), "Student id");
    if (request.entries() == null || request.entries().isEmpty()) {
      throw new InvalidManualResultRequest("Enter at least one question result.");
    }

    Map<Long, LearningAuthorizationClient.QuestionContext> questionContextByQuestionBankId =
        new LinkedHashMap<>();
    List<ManualResultEntry> normalizedEntries = new ArrayList<>();

    for (ManualResultEntry entry : request.entries()) {
      addValidatedManualResultEntry(
          user, bearer, request, entry, questionContextByQuestionBankId, normalizedEntries);
    }

    return new ManualResultValidation(questionContextByQuestionBankId, normalizedEntries);
  }

  private void addValidatedManualResultEntry(
      AuthenticatedUser user,
      String bearer,
      ManualResultBatchRequest request,
      ManualResultEntry entry,
      Map<Long, LearningAuthorizationClient.QuestionContext> questionContextByQuestionBankId,
      List<ManualResultEntry> normalizedEntries) {
    if (entry == null) {
      throw new InvalidManualResultRequest("Each question result is required.");
    }

    requirePositiveManual(entry.questionBankId(), "Question id");
    if (questionContextByQuestionBankId.containsKey(entry.questionBankId())) {
      throw new InvalidManualResultRequest("Each worksheet question may be entered only once.");
    }

    String answer = requireText(entry.answer(), "Student answer");
    String feedback = requireText(entry.feedback(), "Tutor feedback");
    LearningAuthorizationClient.QuestionContext question =
        learning.validateManualResultContext(
            user, bearer, request.studentId(), request.worksheetId(), entry.questionBankId());
    validateManualScore(entry.marks(), question.totalMarks());
    questionContextByQuestionBankId.put(entry.questionBankId(), question);
    normalizedEntries.add(
        new ManualResultEntry(entry.questionBankId(), answer, entry.marks(), feedback));
  }

  private void ensureManualResultsDoNotExist(
      SubmissionDocument document, List<ManualResultEntry> entries) {
    for (ManualResultEntry entry : entries) {
      boolean answerAlreadyExists =
          submissions
              .findBySubmissionDocumentIdAndWorksheetQuestionId(
                  document.getId(), entry.questionBankId())
              .isPresent();
      if (answerAlreadyExists) {
        throw new ManualResultAlreadyExists();
      }
    }
  }

  private List<Submission> createApprovedManualSubmissions(
      SubmissionDocument document, long tutorUserId, ManualResultValidation validation) {
    List<Submission> pendingSubmissions = new ArrayList<>();

    for (ManualResultEntry entry : validation.entries()) {
      LearningAuthorizationClient.QuestionContext question =
          validation.questionContextByQuestionBankId().get(entry.questionBankId());
      // The Learning worksheet response currently identifies its
      // question instance by question-bank ID. Persist that stable ID
      // on both sides until it exposes a distinct instance ID.
      Submission submission =
          createAnswerSubmission(
              document, entry.questionBankId(), entry.questionBankId(), entry.answer(), question);
      submission.approve(tutorUserId, entry.marks(), entry.feedback());
      submission.nextMasterySyncRevision();
      pendingSubmissions.add(submission);
    }

    return pendingSubmissions;
  }

  private void enqueueApprovedManualResultEvents(List<Submission> submissions, long tutorUserId) {
    for (Submission submission : submissions) {
      enqueueMasterySync(submission, tutorUserId, "APPROVED");
      enqueueReviewState(submission, tutorUserId, "RESOLVED");
    }
  }

  @Transactional
  public ManualResultsResponse listManualResults(
      AuthenticatedUser user, String bearer, long worksheetId) {
    requirePositiveManual(worksheetId, "Worksheet id");
    learning.assertCanManageManualResults(user, bearer, worksheetId);
    List<Submission> records =
        submissions
            .findByWorksheetIdAndSubmissionDocumentOwnerUserIdAndSubmissionDocumentOwnerRoleAndSubmissionDocumentSourceTypeOrderByCreatedAtAsc(
                worksheetId,
                user.userId(),
                SubmissionDocument.OwnerRole.TUTOR,
                SubmissionDocument.SourceType.MANUAL);
    Map<Long, List<MarkingReview>> resultsByStudentId = new LinkedHashMap<>();
    for (Submission record : records) {
      resultsByStudentId
          .computeIfAbsent(record.getStudentId(), ignored -> new ArrayList<>())
          .add(MarkingReview.from(record, null));
    }

    List<ManualResultStudentProgress> studentProgress =
        resultsByStudentId.entrySet().stream()
            .map(
                entry ->
                    new ManualResultStudentProgress(
                        entry.getKey(), entry.getValue().size(), List.copyOf(entry.getValue())))
            .toList();
    return new ManualResultsResponse(worksheetId, studentProgress);
  }

  @Transactional
  public MarkingReview get(AuthenticatedUser user, String bearer, long submissionId) {
    Submission submission = ownedSubmission(user, bearer, submissionId);
    return MarkingReview.from(submission, null);
  }

  /**
   * Returns a learner-safe, worksheet-scoped view of the latest answer for each worksheet question.
   * Provisional marking never crosses this boundary: only Tutor approval makes score and feedback
   * final.
   */
  @Transactional
  public StudentResultsResponse studentResults(
      AuthenticatedUser user, String bearer, long worksheetId) {
    long studentId = learning.resolveStudentWorksheet(user, bearer, worksheetId);
    Map<Long, StudentResult> latestResultByQuestionId = new LinkedHashMap<>();
    List<Submission> newestSubmissionsFirst =
        submissions.findByStudentIdAndWorksheetIdOrderByCreatedAtDescIdDesc(studentId, worksheetId);

    for (Submission submission : newestSubmissionsFirst) {
      // The repository is newest-first, so the first row is canonical
      // when a learner resubmits a worksheet question.
      latestResultByQuestionId.putIfAbsent(
          submission.getWorksheetQuestionId(), StudentResult.from(submission));
    }
    return new StudentResultsResponse(worksheetId, List.copyOf(latestResultByQuestionId.values()));
  }

  @Transactional
  public MarkingReview approve(
      AuthenticatedUser user, String bearer, long submissionId, ApprovalRequest request) {
    Submission submission = ownedSubmission(user, bearer, submissionId);
    BigDecimal marks = request.marks();
    String feedback = request.feedback();
    boolean wasApproved = submission.getReviewStatus() == Submission.ReviewStatus.APPROVED;
    List<Submission.DiagnosticEvidenceInput> requestedEvidence =
        diagnosticInputs(submission, request.diagnosticEvidence());
    boolean evidenceSpecified = request.diagnosticEvidence() != null;
    boolean isDuplicateApproval =
        wasApproved
            && marks != null
            && marks.compareTo(submission.getApprovedMarks()) == 0
            && feedback != null
            && feedback.trim().equals(submission.getApprovedFeedback())
            && (!evidenceSpecified || sameDiagnosticEvidence(submission, requestedEvidence));
    if (isDuplicateApproval) {
      return MarkingReview.from(submission, null);
    }
    submission.approve(user.userId(), marks, feedback);
    if (evidenceSpecified || !wasApproved) {
      submission.replaceApprovedDiagnosticEvidence(requestedEvidence);
    } else if (submission.getApprovedDiagnosticEvidence().isEmpty()) {
      submission.replaceApprovedDiagnosticEvidence(List.of());
    }
    submission.nextMasterySyncRevision();
    Submission saved = submissions.saveAndFlush(submission);
    enqueueMasterySync(saved, user.userId(), "APPROVED");
    enqueueReviewState(saved, user.userId(), "RESOLVED");
    return MarkingReview.from(saved, null);
  }

  @Transactional
  public MarkingReview flag(
      AuthenticatedUser user, String bearer, long submissionId, FlagRequest request) {
    Submission submission = ownedSubmission(user, bearer, submissionId);
    boolean wasApproved = submission.getReviewStatus() == Submission.ReviewStatus.APPROVED;
    submission.flagForLater(user.userId(), request.reason());
    if (wasApproved) submission.nextMasterySyncRevision();
    Submission saved = submissions.saveAndFlush(submission);
    if (wasApproved) {
      enqueueMasterySync(saved, user.userId(), "RETRACTED");
      enqueueReviewState(saved, user.userId(), "PENDING_REVIEW");
    }
    return MarkingReview.from(saved, null);
  }

  @Transactional
  public MarkingReview reset(AuthenticatedUser user, String bearer, long submissionId) {
    Submission submission = ownedSubmission(user, bearer, submissionId);
    boolean wasApproved = submission.getReviewStatus() == Submission.ReviewStatus.APPROVED;
    submission.resetToAiSuggestion(user.userId());
    if (wasApproved) submission.nextMasterySyncRevision();
    Submission saved = submissions.saveAndFlush(submission);
    if (wasApproved) {
      enqueueMasterySync(saved, user.userId(), "RETRACTED");
      enqueueReviewState(saved, user.userId(), "PENDING_REVIEW");
    }
    return MarkingReview.from(saved, null);
  }

  private void requireSubmissionActor(AuthenticatedUser user) {
    boolean isStudent = user != null && "STUDENT".equals(user.role());
    boolean isTutor = user != null && "TUTOR".equals(user.role());
    if (!isStudent && !isTutor) {
      throw new LearningAuthorizationClient.Forbidden();
    }
  }

  private void requireClassForTutorManualAnswer(AuthenticatedUser user, Long classId) {
    boolean isTutorMissingClass = "TUTOR".equals(user.role()) && (classId == null || classId <= 0);
    if (isTutorMissingClass) {
      throw new InvalidReviewRequest(
          "Class id is required when a Tutor enters answers for a student.");
    }
  }

  private Submission updateManualAnswer(
      ManualAnswerEntry entry,
      SubmissionDocument document,
      Map<Long, LearningAuthorizationClient.QuestionContext> questionsByQuestionBankId,
      Map<Long, Submission> submissionByQuestionBankId,
      Set<Long> enteredQuestionBankIds) {
    validateManualAnswerEntry(entry, enteredQuestionBankIds);

    LearningAuthorizationClient.QuestionContext question =
        questionsByQuestionBankId.get(entry.questionBankId());
    if (question == null) {
      throw new InvalidReviewRequest("A manual answer does not belong to this worksheet.");
    }

    String answer = entry.answer() == null ? "" : entry.answer().trim();
    // Empty fields represent unanswered questions. They are intentionally
    // absent from the canonical answer table until the learner adds text.
    if (answer.isEmpty()) {
      return null;
    }

    Submission submission = submissionByQuestionBankId.get(entry.questionBankId());
    if (submission == null) {
      submission =
          Submission.createDraftAnswer(
              document,
              entry.questionBankId(),
              entry.questionBankId(),
              answer,
              question.modelAnswer(),
              question.totalMarks(),
              question.syllabusTopicId(),
              question.syllabusTopicCode());
    } else {
      submission.replacePendingAnswer(answer);
    }

    evaluateAndRecordSuggestion(submission, question, answer);
    return submission;
  }

  private void validateManualAnswerEntry(
      ManualAnswerEntry entry, Set<Long> enteredQuestionBankIds) {
    if (entry == null || entry.questionBankId() == null || entry.questionBankId() <= 0) {
      throw new InvalidReviewRequest("Each manual answer requires a worksheet question.");
    }
    if (!enteredQuestionBankIds.add(entry.questionBankId())) {
      throw new InvalidReviewRequest("Each worksheet question may be entered only once.");
    }
  }

  private Map<Long, List<OcrExtraction>> assignExtractionsToQuestions(
      List<OcrExtraction> documentExtractions,
      Map<Long, Long> questionBankIdByExtractionId,
      Map<Long, LearningAuthorizationClient.QuestionContext> questionsByQuestionBankId) {
    Map<Long, List<OcrExtraction>> extractionsByQuestionBankId = new LinkedHashMap<>();

    for (OcrExtraction extraction : documentExtractions) {
      Long questionBankId = questionBankIdByExtractionId.get(extraction.getId());
      LearningAuthorizationClient.QuestionContext question =
          questionsByQuestionBankId.get(questionBankId);
      if (question == null) {
        throw new InvalidReviewRequest("Choose a question from this worksheet for every OCR page.");
      }

      extraction.assignToWorksheetQuestion(questionBankId);
      extractionsByQuestionBankId
          .computeIfAbsent(questionBankId, ignored -> new ArrayList<>())
          .add(extraction);
    }

    return extractionsByQuestionBankId;
  }

  private List<Submission> createOcrSubmissions(
      SubmissionDocument document,
      Map<Long, List<OcrExtraction>> extractionsByQuestionBankId,
      Map<Long, LearningAuthorizationClient.QuestionContext> questionsByQuestionBankId) {
    List<Submission> pendingSubmissions = new ArrayList<>();

    for (Map.Entry<Long, List<OcrExtraction>> questionEntry :
        extractionsByQuestionBankId.entrySet()) {
      Long questionBankId = questionEntry.getKey();
      String answer = answerFromExtractions(questionEntry.getValue(), true);
      if (answer.isBlank()) {
        throw new InvalidReviewRequest(
            "Each mapped OCR page needs corrected text before submission.");
      }

      LearningAuthorizationClient.QuestionContext question =
          questionsByQuestionBankId.get(questionBankId);
      Submission submission =
          createAnswerSubmission(document, questionBankId, questionBankId, answer, question);
      evaluateAndRecordSuggestion(submission, question, answer);
      submission.nextMasterySyncRevision();
      pendingSubmissions.add(submission);
    }

    return pendingSubmissions;
  }

  private Submission createAnswerSubmission(
      SubmissionDocument document,
      long worksheetQuestionId,
      long questionBankId,
      String answer,
      LearningAuthorizationClient.QuestionContext question) {
    return Submission.createAnswer(
        document,
        worksheetQuestionId,
        questionBankId,
        answer,
        question.modelAnswer(),
        question.totalMarks(),
        question.syllabusTopicId(),
        question.syllabusTopicCode());
  }

  private AiGradingService.AiMarkingResult evaluateAndRecordSuggestion(
      Submission submission, LearningAuthorizationClient.QuestionContext question, String answer) {
    AiGradingService.AiMarkingResult suggestion =
        ai.evaluateMarking(
            question.prompt(),
            question.modelAnswer(),
            question.markingCriteria(),
            question.markingComponents().stream()
                .map(LearningAuthorizationClient.MarkingComponentContext::toRuleComponent)
                .toList(),
            question.keywords(),
            answer,
            question.totalMarks());
    submission.recordAiSuggestion(
        suggestion.suggestedMarks(),
        suggestion.correctness(),
        suggestion.errorCategory(),
        suggestion.missingKeywords(),
        suggestion.feedback());
    return suggestion;
  }

  private String answerFromExtractions(
      List<OcrExtraction> questionExtractions, boolean trimAnswer) {
    var answerTexts = questionExtractions.stream().map(this::effectiveText);
    if (!trimAnswer) {
      return answerTexts.filter(text -> !text.isBlank()).collect(Collectors.joining("\n\n"));
    }
    return answerTexts.collect(Collectors.joining("\n\n")).trim();
  }

  private void enqueuePendingReviewStates(List<Submission> submissions, long tutorUserId) {
    for (Submission submission : submissions) {
      enqueueReviewState(submission, tutorUserId, "PENDING_REVIEW");
    }
  }

  private ManualAnswerResponse manualAnswerResponse(SubmissionDocument document) {
    List<Submission> documentSubmissions =
        submissions.findBySubmissionDocumentIdOrderByWorksheetQuestionIdAsc(document.getId());
    if (document.getStatus() == SubmissionDocument.Status.SUBMITTED_FOR_REVIEW) {
      return ManualAnswerResponse.submitted(document.getId(), documentSubmissions);
    }
    return ManualAnswerResponse.draft(document.getId(), documentSubmissions);
  }

  private Submission ownedSubmission(AuthenticatedUser user, String bearer, long submissionId) {
    requirePositive(submissionId, "Submission id");
    Submission submission = submissions.findById(submissionId).orElseThrow(ReviewNotFound::new);
    learning.assertCanReview(user, bearer, submission.getStudentId());
    return submission;
  }

  private boolean hasSubmittedDocument(Submission submission) {
    SubmissionDocument document = submission.getSubmissionDocument();
    return document != null
        && document.getStatus() == SubmissionDocument.Status.SUBMITTED_FOR_REVIEW;
  }

  private Submission submittedSourceSubmission(
      AuthenticatedUser user, String bearer, long submissionId) {
    requirePositive(submissionId, "Submission id");
    Map<Long, String> studentNameById = tutorStudentNames(user, bearer);
    Submission submission = submissions.findById(submissionId).orElseThrow(ReviewNotFound::new);
    SubmissionDocument document = submission.getSubmissionDocument();
    boolean isTutorStudent = studentNameById.containsKey(submission.getStudentId());
    boolean hasSourcePages =
        document != null
            && document.getSourceType() != SubmissionDocument.SourceType.MANUAL
            && !document.getPages().isEmpty();
    if (!isTutorStudent || !hasSubmittedDocument(submission) || !hasSourcePages) {
      throw new ReviewNotFound();
    }
    return submission;
  }

  private Map<Long, String> tutorStudentNames(AuthenticatedUser user, String bearer) {
    return learning.loadTutorStudentDirectory(user, bearer).stream()
        .collect(
            Collectors.toMap(
                LearningAuthorizationClient.TutorStudentDirectoryEntry::studentId,
                LearningAuthorizationClient.TutorStudentDirectoryEntry::fullName));
  }

  private SubmissionDocument manualDocument(
      long ownerUserId,
      SubmissionDocument.OwnerRole ownerRole,
      long worksheetId,
      long studentId,
      Long classId) {
    return findManualDocument(ownerUserId, ownerRole, worksheetId, studentId)
        .orElseGet(
            () -> createManualDocument(ownerUserId, ownerRole, worksheetId, studentId, classId));
  }

  private java.util.Optional<SubmissionDocument> findManualDocument(
      long ownerUserId, SubmissionDocument.OwnerRole ownerRole, long worksheetId, long studentId) {
    return documents.findByOwnerUserIdAndOwnerRoleAndWorksheetIdAndStudentIdAndSourceType(
        ownerUserId, ownerRole, worksheetId, studentId, SubmissionDocument.SourceType.MANUAL);
  }

  private SubmissionDocument createManualDocument(
      long ownerUserId,
      SubmissionDocument.OwnerRole ownerRole,
      long worksheetId,
      long studentId,
      Long classId) {
    SubmissionDocument createdDocument =
        new SubmissionDocument(
            ownerUserId,
            ownerRole,
            worksheetId,
            studentId,
            classId,
            SubmissionDocument.SourceType.MANUAL);
    createdDocument.markReady();

    try {
      return documents.saveAndFlush(createdDocument);
    } catch (DataIntegrityViolationException exception) {
      return findManualDocument(ownerUserId, ownerRole, worksheetId, studentId)
          .orElseThrow(() -> exception);
    }
  }

  private String effectiveText(OcrExtraction extraction) {
    String corrected = extraction.getCorrectedText();
    boolean hasCorrectedText = corrected != null && !corrected.isBlank();
    return hasCorrectedText ? corrected : extraction.getExtractedText();
  }

  private static Map<Long, Long> validatedMappings(
      OcrSubmissionRequest request, List<OcrExtraction> documentExtractions) {
    boolean hasIncorrectMappingCount =
        request == null
            || request.answers() == null
            || request.answers().size() != documentExtractions.size();
    if (hasIncorrectMappingCount || documentExtractions.isEmpty()) {
      throw new InvalidReviewRequest(
          "Assign every OCR page to a worksheet question before submitting.");
    }

    Set<Long> extractionIds =
        documentExtractions.stream().map(OcrExtraction::getId).collect(Collectors.toSet());
    Map<Long, Long> questionBankIdByExtractionId = new LinkedHashMap<>();
    for (OcrAnswerMapping mapping : request.answers()) {
      boolean isValidMapping =
          mapping != null
              && mapping.extractionId() != null
              && mapping.questionBankId() != null
              && mapping.extractionId() > 0
              && mapping.questionBankId() > 0
              && extractionIds.contains(mapping.extractionId())
              && questionBankIdByExtractionId.putIfAbsent(
                      mapping.extractionId(), mapping.questionBankId())
                  == null;
      if (!isValidMapping) {
        throw new InvalidReviewRequest("OCR page mappings are invalid.");
      }
    }

    boolean isMissingExtractionMapping =
        questionBankIdByExtractionId.size() != extractionIds.size();
    boolean hasUncorrectedExtraction =
        documentExtractions.stream()
            .anyMatch(extraction -> extraction.getStatus() != OcrExtraction.Status.READY);
    if (isMissingExtractionMapping || hasUncorrectedExtraction) {
      throw new InvalidReviewRequest(
          "Correct all OCR pages and assign each one before submitting.");
    }
    return questionBankIdByExtractionId;
  }

  private static void requirePositive(Long value, String field) {
    if (value == null || value <= 0) {
      throw new InvalidReviewRequest(field + " must be positive.");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidManualResultRequest(field + " is required.");
    }
    return value.trim();
  }

  private static void requirePositiveManual(Long value, String field) {
    if (value == null || value <= 0) {
      throw new InvalidManualResultRequest(field + " must be positive.");
    }
  }

  private static void validateManualScore(BigDecimal marks, BigDecimal maximum) {
    if (marks == null) {
      throw new InvalidManualResultRequest("Marks are required.");
    }
    try {
      BigDecimal normalized = marks.setScale(2);
      if (normalized.signum() < 0 || normalized.compareTo(maximum) > 0) {
        throw new InvalidManualResultRequest(
            "Marks must be between zero and the question maximum.");
      }
    } catch (ArithmeticException exception) {
      throw new InvalidManualResultRequest("Marks may have at most two decimal places.");
    }
  }

  private List<Submission.DiagnosticEvidenceInput> diagnosticInputs(
      Submission submission, List<DiagnosticEvidenceRequest> evidence) {
    if (evidence == null) {
      return submission.getApprovedDiagnosticEvidence().stream()
          .map(
              item ->
                  new Submission.DiagnosticEvidenceInput(
                      item.getSyllabusTopicId(),
                      item.getMistakeType(),
                      item.getDescription(),
                      item.getMissingKeywords()))
          .toList();
    }
    return evidence.stream().map(item -> diagnosticInput(submission, item)).toList();
  }

  private Submission.DiagnosticEvidenceInput diagnosticInput(
      Submission submission, DiagnosticEvidenceRequest evidence) {
    boolean hasRequiredFields =
        evidence != null
            && evidence.mistakeType() != null
            && evidence.description() != null
            && !evidence.description().isBlank();
    if (!hasRequiredFields) {
      throw new InvalidReviewRequest(
          "Each diagnostic evidence item requires a mistake type and description.");
    }
    if (evidence.category() != null
        && evidence.category() != evidence.mistakeType().getDiagnosticCategory()) {
      throw new InvalidReviewRequest(
          "Diagnostic category does not match the selected mistake type.");
    }

    boolean hasBlankKeyword =
        evidence.missingKeywords() != null
            && evidence.missingKeywords().stream()
                .anyMatch(keyword -> keyword == null || keyword.isBlank());
    if (hasBlankKeyword) {
      throw new InvalidReviewRequest("Diagnostic keywords cannot be blank.");
    }
    return new Submission.DiagnosticEvidenceInput(
        submission.getSyllabusTopicId(),
        evidence.mistakeType(),
        evidence.description(),
        evidence.missingKeywords());
  }

  private static boolean sameDiagnosticEvidence(
      Submission submission, List<Submission.DiagnosticEvidenceInput> requestedEvidence) {
    List<com.fttranscendence.grading.model.ApprovedDiagnosticEvidence> existingEvidence =
        submission.getApprovedDiagnosticEvidence();
    if (existingEvidence.size() != requestedEvidence.size()) {
      return false;
    }
    for (int index = 0; index < existingEvidence.size(); index++) {
      var storedEvidence = existingEvidence.get(index);
      var requestedItem = requestedEvidence.get(index);
      boolean matchesStoredEvidence =
          storedEvidence.getSyllabusTopicId().equals(requestedItem.syllabusTopicId())
              && storedEvidence.getMistakeType() == requestedItem.mistakeType()
              && storedEvidence.getDescription().equals(requestedItem.description().trim())
              && storedEvidence
                  .getMissingKeywords()
                  .equals(normalizeKeywords(requestedItem.missingKeywords()));
      if (!matchesStoredEvidence) {
        return false;
      }
    }
    return true;
  }

  private static List<String> normalizeKeywords(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .filter(java.util.Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private void enqueueMasterySync(Submission submission, long tutorUserId, String state) {
    if (submission.getSyllabusTopicId() == null || submission.getSyllabusTopicCode() == null) {
      throw new InvalidReviewRequest("The question is missing its syllabus topic context.");
    }

    long revision = submission.getMasterySyncRevision();
    String eventKey = "mastery:submission:" + submission.getId() + ":" + revision;
    MasteryProjectionSnapshot snapshot = masteryProjectionSnapshot(submission, state);
    ApprovedMarkingSyncPayload payload =
        new ApprovedMarkingSyncPayload(
            eventKey,
            state,
            revision,
            submission.getId(),
            submission.getStudentId(),
            tutorUserId,
            submission.getWorksheetId(),
            submission.getWorksheetQuestionId(),
            submission.getQuestionBankId(),
            submission.getSyllabusTopicId(),
            submission.getSyllabusTopicCode(),
            snapshot.approvedMarks(),
            submission.getMaxMarks(),
            snapshot.approvedAt().toString(),
            "APPROVED".equals(state)
                ? submission.getApprovedDiagnosticEvidence().stream()
                    .map(ApprovedMarkingSyncPayload.DiagnosticEvidence::from)
                    .toList()
                : List.of());
    try {
      masteryOutbox.saveAndFlush(
          new MasterySyncOutbox(
              eventKey,
              MasterySyncOutbox.EventType.APPROVED_MARKING,
              objectMapper.writeValueAsString(payload)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Could not serialize mastery synchronization event", exception);
    }
  }

  /**
   * Retraction happens after the live approved fields have intentionally been cleared. Its durable
   * event must nevertheless retain the prior Tutor-approved score snapshot so Learning can create
   * or update an inactive projection and suppress a delayed older approval.
   */
  private MasteryProjectionSnapshot masteryProjectionSnapshot(Submission submission, String state) {
    if ("APPROVED".equals(state)) {
      if (submission.getApprovedMarks() == null || submission.getReviewedAt() == null) {
        throw new InvalidReviewRequest(
            "Approved marking is missing its authoritative score snapshot.");
      }
      return new MasteryProjectionSnapshot(
          submission.getApprovedMarks(), submission.getReviewedAt());
    }
    if (!"RETRACTED".equals(state)) {
      throw new InvalidReviewRequest("Mastery synchronization state is invalid.");
    }
    return submission.getReviews().stream()
        .filter(
            review ->
                review.getNewStatus() == Submission.ReviewStatus.APPROVED
                    && review.getNewMarks() != null
                    && review.getCreatedAt() != null)
        .max(
            java.util.Comparator.comparing(AnswerReview::getCreatedAt)
                .thenComparing(
                    AnswerReview::getId,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
        .map(review -> new MasteryProjectionSnapshot(review.getNewMarks(), review.getCreatedAt()))
        .orElseThrow(
            () ->
                new InvalidReviewRequest(
                    "Retraction is missing its prior approved score snapshot."));
  }

  private void enqueueReviewState(Submission submission, long tutorUserId, String reviewState) {
    long revision = submission.getMasterySyncRevision();
    String eventKey = "review:submission:" + submission.getId() + ":" + revision;
    java.time.LocalDateTime stateChangeTime =
        "PENDING_REVIEW".equals(reviewState)
            ? submission.getCreatedAt()
            : submission.getReviewedAt();
    ReviewStateSyncPayload payload =
        new ReviewStateSyncPayload(
            eventKey,
            revision,
            submission.getId(),
            tutorUserId,
            submission.getStudentId(),
            submission.getWorksheetId(),
            reviewState,
            stateChangeTime.toString());
    try {
      masteryOutbox.saveAndFlush(
          new MasterySyncOutbox(
              eventKey,
              MasterySyncOutbox.EventType.MARKING_REVIEW_STATE,
              objectMapper.writeValueAsString(payload)));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Could not serialize review synchronization event", exception);
    }
  }

  public record CreateRequest(
      Long submissionDocumentId, Long worksheetQuestionId, Long questionBankId) {}

  public record OcrAnswerMapping(Long extractionId, Long questionBankId) {}

  public record OcrSubmissionRequest(List<OcrAnswerMapping> answers) {}

  /** Student-safe confirmation response: it deliberately excludes AI scores and rubric data. */
  public record SubmissionForTutorReviewResponse(
      Long submissionDocumentId, List<Long> submissionIds, String status) {
    static SubmissionForTutorReviewResponse from(Long documentId, List<Submission> submissions) {
      return new SubmissionForTutorReviewResponse(
          documentId, submissions.stream().map(Submission::getId).toList(), "PENDING_REVIEW");
    }
  }

  /** Tutor queue data, deliberately limited to submitted canonical answers. */
  public record TutorReviewQueueItem(
      Long submissionId,
      Long submissionDocumentId,
      Long studentId,
      String studentName,
      Long worksheetId,
      Long worksheetQuestionId,
      Long questionBankId,
      String extractedAnswer,
      BigDecimal maxMarks,
      Submission.ReviewStatus reviewStatus,
      boolean sourceAvailable,
      String sourceType,
      java.time.LocalDateTime requestedAt) {
    static TutorReviewQueueItem from(Submission submission, String studentName) {
      SubmissionDocument document = submission.getSubmissionDocument();
      boolean sourceAvailable =
          document.getSourceType() != SubmissionDocument.SourceType.MANUAL
              && !document.getPages().isEmpty();
      return new TutorReviewQueueItem(
          submission.getId(),
          document.getId(),
          submission.getStudentId(),
          studentName,
          submission.getWorksheetId(),
          submission.getWorksheetQuestionId(),
          submission.getQuestionBankId(),
          submission.getExtractedAnswer(),
          submission.getMaxMarks(),
          submission.getReviewStatus(),
          sourceAvailable,
          document.getSourceType().name(),
          submission.getCreatedAt());
    }
  }

  public record SubmittedSource(
      Long submissionId,
      Long submissionDocumentId,
      Long studentId,
      Long worksheetId,
      String sourceType,
      String status,
      List<SourcePageMetadata> pages) {
    static SubmittedSource from(Long submissionId, SubmissionDocument document) {
      return new SubmittedSource(
          submissionId,
          document.getId(),
          document.getStudentId(),
          document.getWorksheetId(),
          document.getSourceType().name(),
          document.getStatus().name(),
          document.getPages().stream().map(SourcePageMetadata::from).toList());
    }
  }

  public record SourcePageMetadata(
      Long id, int pageNumber, String originalFilename, String mediaType, long byteSize) {
    static SourcePageMetadata from(SubmissionPage page) {
      return new SourcePageMetadata(
          page.getId(),
          page.getPageNumber(),
          page.getOriginalFilename(),
          page.getMediaType(),
          page.getByteSize());
    }
  }

  public record SourcePageContent(String filename, String mediaType, byte[] content) {}

  public record ManualAnswerEntry(Long questionBankId, String answer) {}

  public record ManualAnswerRequest(
      Long studentId,
      Long worksheetId,
      Long classId,
      List<ManualAnswerEntry> answers,
      Boolean submit) {}

  /**
   * Deliberately excludes model answers and provisional AI suggestions from learner-facing clients.
   */
  public record ManualAnswerResponse(
      Long submissionDocumentId,
      List<Long> submissionIds,
      List<ManualAnswerValue> answers,
      String status,
      String inputMethod) {
    static ManualAnswerResponse empty() {
      return new ManualAnswerResponse(null, List.of(), List.of(), "DRAFT", "MANUAL");
    }

    static ManualAnswerResponse draft(Long documentId, List<Submission> submissions) {
      return response(documentId, submissions, "DRAFT");
    }

    static ManualAnswerResponse submitted(Long documentId, List<Submission> submissions) {
      return response(documentId, submissions, "PENDING_REVIEW");
    }

    private static ManualAnswerResponse response(
        Long documentId, List<Submission> submissions, String status) {
      return new ManualAnswerResponse(
          documentId,
          submissions.stream().map(Submission::getId).toList(),
          submissions.stream()
              .map(
                  submission ->
                      new ManualAnswerValue(
                          submission.getWorksheetQuestionId(), submission.getExtractedAnswer()))
              .toList(),
          status,
          "MANUAL");
    }
  }

  public record ManualAnswerValue(Long questionBankId, String answer) {}

  public record ManualResultRequest(
      Long worksheetId,
      Long studentId,
      Long questionBankId,
      String answer,
      BigDecimal marks,
      String feedback) {}

  public record ManualResultEntry(
      Long questionBankId, String answer, BigDecimal marks, String feedback) {}

  public record ManualResultBatchRequest(
      Long worksheetId, Long studentId, List<ManualResultEntry> entries) {}

  public record ManualResultStudentProgress(
      Long studentId, int completedQuestions, List<MarkingReview> results) {}

  public record ManualResultsResponse(
      Long worksheetId, List<ManualResultStudentProgress> students) {}

  public record StudentResultsResponse(Long worksheetId, List<StudentResult> results) {}

  public record StudentResult(
      Long submissionId,
      Long worksheetQuestionId,
      Long questionBankId,
      String answer,
      String modelAnswer,
      BigDecimal maximumMarks,
      Submission.ReviewStatus reviewStatus,
      StudentResultOutcome outcome,
      BigDecimal awardedMarks,
      String explanation,
      java.time.LocalDateTime reviewedAt) {
    static StudentResult from(Submission submission) {
      if (submission.getReviewStatus() != Submission.ReviewStatus.APPROVED) {
        return new StudentResult(
            submission.getId(),
            submission.getWorksheetQuestionId(),
            submission.getQuestionBankId(),
            submission.getExtractedAnswer(),
            null,
            submission.getMaxMarks(),
            submission.getReviewStatus(),
            StudentResultOutcome.REVIEW_NEEDED,
            null,
            null,
            null);
      }
      BigDecimal awarded = submission.getApprovedMarks();
      BigDecimal maximum = submission.getMaxMarks();
      StudentResultOutcome outcome =
          awarded.compareTo(maximum) == 0
              ? StudentResultOutcome.CORRECT
              : awarded.signum() == 0
                  ? StudentResultOutcome.INCORRECT
                  : StudentResultOutcome.PARTIAL;
      return new StudentResult(
          submission.getId(),
          submission.getWorksheetQuestionId(),
          submission.getQuestionBankId(),
          submission.getExtractedAnswer(),
          submission.getModelAnswerSnapshot(),
          maximum,
          submission.getReviewStatus(),
          outcome,
          awarded,
          submission.getApprovedFeedback(),
          submission.getReviewedAt());
    }
  }

  public enum StudentResultOutcome {
    CORRECT,
    PARTIAL,
    INCORRECT,
    REVIEW_NEEDED
  }

  public record ApprovalRequest(
      BigDecimal marks, String feedback, List<DiagnosticEvidenceRequest> diagnosticEvidence) {
    public ApprovalRequest(BigDecimal marks, String feedback) {
      this(marks, feedback, null);
    }
  }

  public record DiagnosticEvidenceRequest(
      MistakeType mistakeType,
      DiagnosticCategory category,
      String description,
      List<String> missingKeywords) {}

  private record ReviewStateSyncPayload(
      String eventKey,
      long revision,
      long submissionId,
      long tutorUserId,
      long studentId,
      long worksheetId,
      String reviewState,
      String occurredAt) {}

  private record ManualResultValidation(
      Map<Long, LearningAuthorizationClient.QuestionContext> questionContextByQuestionBankId,
      List<ManualResultEntry> entries) {}

  private record MasteryProjectionSnapshot(
      BigDecimal approvedMarks, java.time.LocalDateTime approvedAt) {}

  public record FlagRequest(String reason) {}

  public record MarkingReview(
      Long id,
      Long studentId,
      Long worksheetId,
      Long worksheetQuestionId,
      Long questionBankId,
      String extractedAnswer,
      String modelAnswer,
      BigDecimal maxMarks,
      BigDecimal aiSuggestedMarks,
      String aiSuggestedOutcome,
      String aiErrorCategory,
      List<String> missingKeywords,
      String aiSuggestedFeedback,
      Submission.ReviewStatus reviewStatus,
      BigDecimal approvedMarks,
      String approvedFeedback,
      Long reviewedByUserId,
      java.time.LocalDateTime reviewedAt,
      Boolean providerResponseValid,
      List<ApprovedMarkingSyncPayload.DiagnosticEvidence> diagnosticEvidence,
      List<ReviewHistory> history) {
    static MarkingReview from(Submission submission, Boolean providerResponseValid) {
      return new MarkingReview(
          submission.getId(),
          submission.getStudentId(),
          submission.getWorksheetId(),
          submission.getWorksheetQuestionId(),
          submission.getQuestionBankId(),
          submission.getExtractedAnswer(),
          submission.getModelAnswerSnapshot(),
          submission.getMaxMarks(),
          submission.getAiSuggestedMarks(),
          submission.getAiSuggestedOutcome(),
          submission.getAiErrorCategory(),
          submission.getMissingKeywords(),
          submission.getAiSuggestedFeedback(),
          submission.getReviewStatus(),
          submission.getApprovedMarks(),
          submission.getApprovedFeedback(),
          submission.getReviewedByUserId(),
          submission.getReviewedAt(),
          providerResponseValid,
          submission.getApprovedDiagnosticEvidence().stream()
              .map(ApprovedMarkingSyncPayload.DiagnosticEvidence::from)
              .toList(),
          submission.getReviews().stream().map(ReviewHistory::from).toList());
    }
  }

  public record ReviewHistory(
      Long id,
      AnswerReview.Action action,
      Long reviewerUserId,
      Submission.ReviewStatus previousStatus,
      Submission.ReviewStatus newStatus,
      BigDecimal previousMarks,
      BigDecimal newMarks,
      String previousFeedback,
      String newFeedback,
      java.time.LocalDateTime createdAt) {
    static ReviewHistory from(AnswerReview review) {
      return new ReviewHistory(
          review.getId(),
          review.getAction(),
          review.getReviewerUserId(),
          review.getPreviousStatus(),
          review.getNewStatus(),
          review.getPreviousMarks(),
          review.getNewMarks(),
          review.getPreviousFeedback(),
          review.getNewFeedback(),
          review.getCreatedAt());
    }
  }

  public static class ReviewNotFound extends RuntimeException {}

  public static class InvalidReviewRequest extends RuntimeException {
    public InvalidReviewRequest(String message) {
      super(message);
    }
  }

  public static class InvalidManualResultRequest extends RuntimeException {
    public InvalidManualResultRequest(String message) {
      super(message);
    }
  }

  public static class ManualResultAlreadyExists extends RuntimeException {}
}
