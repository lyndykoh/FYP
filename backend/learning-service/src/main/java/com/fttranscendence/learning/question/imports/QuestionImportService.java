package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.ImageFingerprint;
import com.fttranscendence.learning.question.Question;
import com.fttranscendence.learning.question.QuestionImage;
import com.fttranscendence.learning.question.QuestionImageService;
import com.fttranscendence.learning.question.QuestionRepository;
import com.fttranscendence.learning.question.QuestionRequest;
import com.fttranscendence.learning.question.QuestionService;
import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionImportService {
  private static final int MAX_FILES_PER_BATCH = 20;
  private static final int MAX_PAGES_PER_BATCH = 100;
  private static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
  private static final long MAX_PAGE_PIXELS = 20_000_000L;
  private static final int PDF_RENDER_DPI = 144;
  private static final Pattern QUESTION_BOUNDARY =
      Pattern.compile("(?m)(?=^\\s*(?:question\\s*)?\\d{1,3}[.)])", Pattern.CASE_INSENSITIVE);
  private static final Pattern ANSWER =
      Pattern.compile("(?im)^\\s*(?:answer|ans|solution)\\s*[:.-]\\s*(.+)$");

  private final QuestionImportBatchRepository batches;
  private final QuestionImportSourcePageRepository pages;
  private final QuestionImportCandidateRepository candidates;
  private final QuestionImportDiagramCropRepository diagramCrops;
  private final QuestionRepository questions;
  private final QuestionService questionService;
  private final QuestionImageService questionImages;
  private final SyllabusTopicRepository syllabusTopics;
  private final EntityManager entityManager;

  public QuestionImportService(
      QuestionImportBatchRepository batches,
      QuestionImportSourcePageRepository pages,
      QuestionImportCandidateRepository candidates,
      QuestionImportDiagramCropRepository diagramCrops,
      QuestionRepository questions,
      QuestionService questionService,
      QuestionImageService questionImages,
      SyllabusTopicRepository syllabusTopics,
      EntityManager entityManager) {
    this.batches = batches;
    this.pages = pages;
    this.candidates = candidates;
    this.questions = questions;
    this.diagramCrops = diagramCrops;
    this.questionService = questionService;
    this.questionImages = questionImages;
    this.syllabusTopics = syllabusTopics;
    this.entityManager = entityManager;
  }

  @Transactional
  public BatchDetail upload(long tutorUserId, List<MultipartFile> uploadedFiles) {
    if (uploadedFiles == null
        || uploadedFiles.isEmpty()
        || uploadedFiles.size() > MAX_FILES_PER_BATCH) {
      throw new InvalidImportException("Upload from 1 to 20 PDF, PNG, or JPEG files.");
    }
    if (tutorUserId <= 0) throw new InvalidImportException("A signed-in tutor is required.");
    QuestionImportBatch batch =
        batches.save(
            new QuestionImportBatch(
                "Question import", tutorUserId, QuestionImportBatch.Status.QUEUED));
    int importedPages = 0;
    for (MultipartFile file : uploadedFiles) {
      validateUploadMetadata(file);
      String filename = safeFilename(file.getOriginalFilename());
      byte[] bytes = readBytes(file);
      UploadType type = uploadType(bytes);
      if (type == UploadType.PDF) {
        importedPages += addPdfPages(batch, filename, bytes, MAX_PAGES_PER_BATCH - importedPages);
      } else {
        addImagePage(batch, filename, bytes);
        importedPages++;
      }
      if (importedPages > MAX_PAGES_PER_BATCH)
        throw new InvalidImportException("A batch can contain at most 100 pages.");
    }
    entityManager.flush();
    return get(tutorUserId, batch.getId());
  }

  @Transactional(readOnly = true)
  public BatchDetail get(long tutorUserId, long batchId) {
    QuestionImportBatch batch = ownedBatch(tutorUserId, batchId);
    List<QuestionImportCandidate> batchCandidates = candidates.findAllForBatch(batchId);
    return BatchDetail.from(
        batch,
        batchCandidates,
        diagramCrops.findAllForBatch(batchId),
        candidates.findAllImportedForDuplicateComparison(),
        questions.findAllForDuplicateComparison());
  }

  @Transactional(readOnly = true)
  public ImageContent sourcePageImage(long tutorUserId, long batchId, long pageId) {
    ownedBatch(tutorUserId, batchId);
    QuestionImportSourcePage page =
        pages.findByIdAndBatch_Id(pageId, batchId).orElseThrow(SourcePageNotFoundException::new);
    return new ImageContent(page.getContentType(), page.getPageImageBytes());
  }

  @Transactional(readOnly = true)
  public ImageContent diagramCropImage(long tutorUserId, long batchId, long cropId) {
    ownedBatch(tutorUserId, batchId);
    QuestionImportDiagramCrop crop =
        diagramCrops
            .findByIdAndBatch_Id(cropId, batchId)
            .orElseThrow(DiagramCropNotFoundException::new);
    return new ImageContent(crop.getContentType(), crop.getCropImageBytes());
  }

  @Transactional
  public CandidateDetail update(
      long tutorUserId,
      long batchId,
      long candidateId,
      QuestionImportRequests.CandidateUpdate update) {
    reviewBatch(tutorUserId, batchId);
    QuestionImportCandidate candidate = candidate(batchId, candidateId);
    requireReviewable(candidate, "edit");
    SyllabusTopic topic =
        update.syllabusTopicId() == null
            ? null
            : syllabusTopics
                .findById(update.syllabusTopicId())
                .orElseThrow(
                    () -> new InvalidImportException("Choose an existing syllabus topic."));
    candidate.revise(
        trimOrNull(update.code()),
        topic,
        defaultString(update.prompt()),
        defaultString(update.modelAnswer()),
        update.totalMarks() == null ? BigDecimal.ONE : update.totalMarks(),
        update.questionType() == null ? candidate.getReviewedQuestionType() : update.questionType(),
        update.difficulty() == null ? candidate.getReviewedDifficulty() : update.difficulty(),
        update.includeSourceImage());
    candidates.save(candidate);
    // Return recalculated advisory evidence so a tutor does not lose a
    // duplicate warning immediately after correcting a reviewed field.
    return get(tutorUserId, batchId).candidates().stream()
        .filter(detail -> detail.id() == candidateId)
        .findFirst()
        .orElseThrow(CandidateNotFoundException::new);
  }

  @Transactional
  public ImportResult importCandidates(
      long tutorUserId, long batchId, QuestionImportRequests.ImportCandidates request) {
    QuestionImportBatch batch = reviewBatch(tutorUserId, batchId);
    List<Long> uniqueIds = request.candidateIds().stream().distinct().toList();
    if (uniqueIds.size() != request.candidateIds().size())
      throw new InvalidImportException("Choose each draft only once.");
    List<Long> importedQuestionIds = new ArrayList<>();
    for (Long candidateId : uniqueIds) {
      QuestionImportCandidate candidate = candidate(batchId, candidateId);
      requireReviewable(candidate, "import");
      validateReadyForImport(candidate);
      String code =
          candidate.getQuestionCode() == null
              ? generatedCode(batchId, candidate)
              : candidate.getQuestionCode();
      QuestionRequest questionRequest =
          new QuestionRequest(
              code,
              candidate.getSyllabusTopic().getId(),
              candidate.getReviewedQuestionType(),
              candidate.getReviewedDifficulty(),
              candidate.getPrompt(),
              candidate.getTotalMarks(),
              candidate.getModelAnswer(),
              Question.ArchiveState.ARCHIVED,
              List.of(
                  new QuestionRequest.MarkingComponentRequest(
                      "Imported answer evidence",
                      candidate.getTotalMarks(),
                      answerKeywords(candidate.getModelAnswer()))),
              answerKeywords(candidate.getModelAnswer()));
      long questionId = questionService.create(questionRequest).id();
      Question importedQuestion = questions.findById(questionId).orElseThrow();
      if (candidate.isIncludeSourceImage()) attachAcceptedDiagramCrops(importedQuestion, candidate);
      candidate.imported(importedQuestion);
      importedQuestionIds.add(questionId);
    }
    boolean allCandidatesResolved =
        candidates.findAllForBatch(batchId).stream()
            .allMatch(
                candidate ->
                    candidate.getStatus() == QuestionImportCandidate.Status.IMPORTED
                        || candidate.getStatus() == QuestionImportCandidate.Status.SUPERSEDED);
    if (allCandidatesResolved) {
      batch.setStatus(QuestionImportBatch.Status.IMPORTED);
    }
    return new ImportResult(
        importedQuestionIds,
        "Imported questions are archived pending review. Activate each one after final review.");
  }

  @Transactional
  public BatchDetail reject(
      long tutorUserId,
      long batchId,
      long candidateId,
      QuestionImportRequests.RejectCandidate request) {
    reviewBatch(tutorUserId, batchId);
    QuestionImportCandidate candidate = candidate(batchId, candidateId);
    requireReviewable(candidate, "reject");
    candidate.reject(nullableText(request.reason()));
    candidates.save(candidate);
    return get(tutorUserId, batchId);
  }

  @Transactional
  public BatchDetail restore(long tutorUserId, long batchId, long candidateId) {
    reviewBatch(tutorUserId, batchId);
    QuestionImportCandidate candidate = candidate(batchId, candidateId);
    try {
      candidate.restore();
    } catch (IllegalStateException exception) {
      throw new InvalidImportException(exception.getMessage());
    }
    candidates.save(candidate);
    return get(tutorUserId, batchId);
  }

  @Transactional
  public BatchDetail moveCrops(
      long tutorUserId, long batchId, QuestionImportRequests.MoveCrops request) {
    reviewBatch(tutorUserId, batchId);
    QuestionImportCandidate target = candidate(batchId, request.targetCandidateId());
    requireReviewable(target, "receive diagram attachments");
    List<Long> cropIds =
        distinctIds(request.cropIds(), "Choose each diagram attachment only once.");
    List<QuestionImportDiagramCrop> moving =
        cropIds.stream()
            .map(
                cropId ->
                    diagramCrops
                        .findByIdAndBatch_Id(cropId, batchId)
                        .orElseThrow(DiagramCropNotFoundException::new))
            .toList();
    for (QuestionImportDiagramCrop crop : moving) {
      requireReviewable(crop.getCandidate(), "move diagram attachments");
      if (!crop.getSourcePage().getId().equals(target.getSourcePage().getId())) {
        throw new InvalidImportException(
            "Diagram attachments can only move to a draft from the same source page.");
      }
    }
    int targetCropCount = diagramCrops.findAllForCandidate(target.getId()).size();
    long incomingCropCount =
        moving.stream().filter(crop -> !crop.getCandidate().getId().equals(target.getId())).count();
    if (targetCropCount + incomingCropCount > QuestionImageService.MAX_IMAGES_PER_QUESTION) {
      throw new InvalidImportException(
          "A reviewed question can contain at most 10 diagram attachments.");
    }
    moving.forEach(crop -> crop.moveTo(target));
    moving.forEach(diagramCrops::save);
    return get(tutorUserId, batchId);
  }

  @Transactional
  public BatchDetail merge(
      long tutorUserId, long batchId, QuestionImportRequests.MergeCandidates request) {
    reviewBatch(tutorUserId, batchId);
    List<Long> ids =
        distinctIds(request.candidateIds(), "Choose each draft only once when merging.");
    if (!ids.contains(request.targetCandidateId())) {
      throw new InvalidImportException("The merge destination must be one of the selected drafts.");
    }
    QuestionImportCandidate target = candidate(batchId, request.targetCandidateId());
    requireReviewable(target, "receive a merge");
    List<QuestionImportCandidate> selected =
        ids.stream().map(id -> candidate(batchId, id)).toList();
    for (QuestionImportCandidate item : selected) {
      requireReviewable(item, "merge");
      if (!item.getSourcePage().getId().equals(target.getSourcePage().getId())) {
        throw new InvalidImportException("Only drafts from the same source page can be merged.");
      }
    }
    List<QuestionImportDiagramCrop> selectedCrops =
        diagramCrops.findAllForBatch(batchId).stream()
            .filter(crop -> ids.contains(crop.getCandidate().getId()))
            .toList();
    if (selectedCrops.size() > QuestionImageService.MAX_IMAGES_PER_QUESTION) {
      throw new InvalidImportException(
          "A merged draft can contain at most 10 diagram attachments.");
    }
    String mergedPrompt =
        mergeText(selected.stream().map(QuestionImportCandidate::getPrompt).toList());
    String mergedAnswer =
        mergeText(selected.stream().map(QuestionImportCandidate::getModelAnswer).toList());
    if (mergedPrompt.length() > 4000 || mergedAnswer.length() > 4000) {
      throw new InvalidImportException(
          "The merged question text or answer is too long. Edit the drafts before merging.");
    }
    target.revise(
        target.getQuestionCode(),
        target.getSyllabusTopic(),
        mergedPrompt,
        mergedAnswer,
        target.getTotalMarks(),
        target.getReviewedQuestionType(),
        target.getReviewedDifficulty(),
        target.isIncludeSourceImage());
    for (QuestionImportCandidate item : selected) {
      if (!item.getId().equals(target.getId())) item.supersede(target);
    }
    selectedCrops.forEach(crop -> crop.moveTo(target));
    selected.forEach(candidates::save);
    selectedCrops.forEach(diagramCrops::save);
    return get(tutorUserId, batchId);
  }

  @Transactional
  public BatchDetail split(
      long tutorUserId,
      long batchId,
      long candidateId,
      QuestionImportRequests.SplitCandidate request) {
    reviewBatch(tutorUserId, batchId);
    QuestionImportCandidate source = candidate(batchId, candidateId);
    requireReviewable(source, "split");
    if (request.drafts().size() < 2) {
      throw new InvalidImportException("Split a draft into at least two reviewed questions.");
    }
    List<QuestionImportCandidate> children = new ArrayList<>();
    int nextNumber = candidates.findHighestNumberForSourcePage(source.getSourcePage().getId()) + 1;
    for (QuestionImportRequests.SplitDraft draft : request.drafts()) {
      String prompt = defaultString(draft.prompt());
      String answer = defaultString(draft.modelAnswer());
      if (prompt.isBlank() || answer.isBlank()) {
        throw new InvalidImportException(
            "Every split question needs question text and a model answer.");
      }
      children.add(
          candidates.save(QuestionImportCandidate.splitFrom(source, nextNumber++, prompt, answer)));
    }
    source.supersede(children.get(0));
    candidates.save(source);
    List<QuestionImportDiagramCrop> sourceCrops = diagramCrops.findAllForCandidate(source.getId());
    sourceCrops.forEach(crop -> crop.moveTo(children.get(0)));
    sourceCrops.forEach(diagramCrops::save);
    return get(tutorUserId, batchId);
  }

  private int addPdfPages(
      QuestionImportBatch batch, String filename, byte[] bytes, int remainingPageCapacity) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      if (document.isEncrypted())
        throw new InvalidImportException("Encrypted PDFs are not supported.");
      if (document.getNumberOfPages() > remainingPageCapacity)
        throw new InvalidImportException("A batch can contain at most 100 pages.");
      PDFRenderer renderer = new PDFRenderer(document);
      PDFTextStripper stripper = new PDFTextStripper();
      for (int index = 0; index < document.getNumberOfPages(); index++) {
        validatePdfPagePixels(document, index);
        stripper.setStartPage(index + 1);
        stripper.setEndPage(index + 1);
        BufferedImage rendered = renderer.renderImageWithDPI(index, 144, ImageType.RGB);
        byte[] pageBytes = encodeJpeg(rendered);
        String extractedText = limitText(stripper.getText(document));
        QuestionImportSourcePage page =
            pages.save(
                new QuestionImportSourcePage(
                    batch,
                    filename,
                    index + 1,
                    "image/jpeg",
                    ImageFingerprint.sha256(bytes),
                    pageBytes,
                    rendered.getWidth(),
                    rendered.getHeight(),
                    extractedText,
                    null));
        if (!extractedText.isBlank()) {
          detectCandidates(batch, page, extractedText);
        }
      }
      return document.getNumberOfPages();
    } catch (IOException exception) {
      throw new InvalidImportException(
          "The PDF could not be processed. Upload a valid, unencrypted PDF.");
    }
  }

  private void addImagePage(QuestionImportBatch batch, String filename, byte[] bytes) {
    String contentType = imageContentType(bytes);
    BufferedImage decoded = decodeImage(bytes);
    QuestionImportSourcePage page =
        pages.save(
            new QuestionImportSourcePage(
                batch,
                filename,
                1,
                contentType,
                ImageFingerprint.sha256(bytes),
                bytes,
                decoded.getWidth(),
                decoded.getHeight(),
                "",
                null));
  }

  private void detectCandidates(
      QuestionImportBatch batch, QuestionImportSourcePage page, String text) {
    List<String> segments = splitQuestions(text);
    if (segments.isEmpty()) {
      candidates.save(
          new QuestionImportCandidate(
              batch,
              page,
              1,
              QuestionImportCandidate.Status.UNCERTAIN,
              20,
              "No numbered questions were detected. Check the page image and enter the question manually.",
              Question.QuestionType.OPEN_ENDED,
              Question.Difficulty.FOUNDATION,
              "needs-review",
              text,
              extractAnswer(text)));
      return;
    }
    for (int index = 0; index < segments.size(); index++) {
      String segment = segments.get(index);
      boolean hasAnswer = !extractAnswer(segment).isBlank();
      candidates.save(
          new QuestionImportCandidate(
              batch,
              page,
              index + 1,
              hasAnswer
                  ? QuestionImportCandidate.Status.READY_FOR_REVIEW
                  : QuestionImportCandidate.Status.UNCERTAIN,
              hasAnswer ? 82 : 58,
              hasAnswer ? null : "No answer or solution was detected; add one before importing.",
              inferQuestionType(segment),
              inferDifficulty(segment),
              inferTags(segment),
              segment,
              extractAnswer(segment)));
    }
  }

  private QuestionImportCandidate candidate(long batchId, long candidateId) {
    return candidates
        .findByIdAndBatch_Id(candidateId, batchId)
        .orElseThrow(CandidateNotFoundException::new);
  }

  private QuestionImportBatch reviewBatch(long tutorUserId, long batchId) {
    QuestionImportBatch batch =
        batches.findByIdForReviewUpdate(batchId).orElseThrow(BatchNotFoundException::new);
    requireOwner(tutorUserId, batch);
    if (batch.getStatus() != QuestionImportBatch.Status.READY_FOR_REVIEW) {
      throw new InvalidImportException(
          "Wait for OCR processing to finish successfully before changing review drafts.");
    }
    return batch;
  }

  private QuestionImportBatch ownedBatch(long tutorUserId, long batchId) {
    QuestionImportBatch batch = batches.findById(batchId).orElseThrow(BatchNotFoundException::new);
    requireOwner(tutorUserId, batch);
    return batch;
  }

  private void requireOwner(long tutorUserId, QuestionImportBatch batch) {
    if (tutorUserId <= 0
        || batch.getCreatedByTutorId() == null
        || !batch.getCreatedByTutorId().equals(tutorUserId)) {
      throw new BatchNotFoundException();
    }
  }

  private void requireReviewable(QuestionImportCandidate candidate, String action) {
    if (!candidate.isReviewable()) {
      throw new InvalidImportException("Only ready or uncertain drafts can " + action + ".");
    }
  }

  private List<Long> distinctIds(List<Long> ids, String duplicateMessage) {
    List<Long> uniqueIds = ids.stream().distinct().toList();
    if (uniqueIds.size() != ids.size()) throw new InvalidImportException(duplicateMessage);
    return uniqueIds;
  }

  private void validateReadyForImport(QuestionImportCandidate candidate) {
    if (candidate.getSyllabusTopic() == null
        || candidate.getPrompt().isBlank()
        || candidate.getModelAnswer().isBlank()) {
      throw new InvalidImportException(
          "Each draft needs a syllabus topic, question text, and model answer before import.");
    }
  }

  private String mergeText(List<String> values) {
    return values.stream()
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .collect(java.util.stream.Collectors.joining("\n\n"));
  }

  private void attachAcceptedDiagramCrops(Question question, QuestionImportCandidate candidate) {
    List<QuestionImportDiagramCrop> crops = diagramCrops.findAllForCandidate(candidate.getId());
    for (QuestionImportDiagramCrop crop : crops) {
      if (question.getImages().size() >= QuestionImageService.MAX_IMAGES_PER_QUESTION) return;
      String filename =
          "import-batch-"
              + crop.getBatch().getId()
              + "-page-"
              + crop.getSourcePage().getSourcePageNumber()
              + "-"
              + crop.getDiagramRegionId()
              + ".png";
      questionImages.attachImportedCrop(
          question.getId(), filename, crop.getContentType(), crop.getCropImageBytes());
    }
  }

  private List<String> splitQuestions(String text) {
    if (text == null || text.isBlank()) return List.of();
    String[] split = QUESTION_BOUNDARY.split(text.trim());
    return Arrays.stream(split)
        .map(String::trim)
        .filter(value -> value.length() >= 4)
        .limit(50)
        .toList();
  }

  private String extractAnswer(String text) {
    Matcher matcher = ANSWER.matcher(text);
    return matcher.find() ? matcher.group(1).trim() : "";
  }

  private Question.QuestionType inferQuestionType(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    return lower.contains("calculate") || lower.contains("cm") || lower.contains("kg")
        ? Question.QuestionType.CALCULATION
        : lower.contains("diagram") || lower.contains("label")
            ? Question.QuestionType.DIAGRAM
            : Question.QuestionType.OPEN_ENDED;
  }

  private Question.Difficulty inferDifficulty(String text) {
    return text.toLowerCase(Locale.ROOT).contains("explain")
        ? Question.Difficulty.APPLICATION
        : Question.Difficulty.FOUNDATION;
  }

  private String inferTags(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    List<String> tags = new ArrayList<>();
    for (String tag : List.of("diagram", "calculation", "explain", "experiment", "plant", "water"))
      if (lower.contains(tag)) tags.add(tag);
    return String.join(", ", tags);
  }

  private List<String> answerKeywords(String answer) {
    return Arrays.stream(answer.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
        .filter(word -> word.length() >= 3)
        .distinct()
        .limit(20)
        .toList();
  }

  private void validateUploadMetadata(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES)
      throw new InvalidImportException(
          "Each upload must be a PDF, PNG, or JPEG no larger than 25 MB.");
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exception) {
      throw new InvalidImportException("An uploaded file could not be read.");
    }
  }

  private UploadType uploadType(byte[] bytes) {
    if (bytes.length >= 5
        && bytes[0] == '%'
        && bytes[1] == 'P'
        && bytes[2] == 'D'
        && bytes[3] == 'F'
        && bytes[4] == '-') return UploadType.PDF;
    if (bytes.length >= 8
        && bytes[0] == (byte) 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4e
        && bytes[3] == 0x47
        && bytes[4] == 0x0d
        && bytes[5] == 0x0a
        && bytes[6] == 0x1a
        && bytes[7] == 0x0a) return UploadType.PNG;
    if (bytes.length >= 3
        && bytes[0] == (byte) 0xff
        && bytes[1] == (byte) 0xd8
        && bytes[2] == (byte) 0xff) return UploadType.JPEG;
    throw new InvalidImportException(
        "Only PDF, PNG, and JPEG files with valid signatures are supported.");
  }

  private String imageContentType(byte[] bytes) {
    return switch (uploadType(bytes)) {
      case PNG -> "image/png";
      case JPEG -> "image/jpeg";
      case PDF ->
          throw new InvalidImportException("Only image files can be decoded as source pages.");
    };
  }

  private BufferedImage decodeImage(byte[] bytes) {
    String expectedFormat = imageContentType(bytes).equals("image/png") ? "png" : "jpeg";
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException();
      ImageReader reader = readers.next();
      try {
        if (!expectedFormat.equalsIgnoreCase(reader.getFormatName())) throw new IOException();
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        validatePagePixels(width, height);
        BufferedImage image = reader.read(0);
        if (image == null || image.getWidth() != width || image.getHeight() != height)
          throw new IOException();
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException exception) {
      throw new InvalidImportException(
          "The uploaded image is invalid or exceeds the page pixel limit.");
    }
  }

  private void validatePdfPagePixels(PDDocument document, int index) {
    org.apache.pdfbox.pdmodel.common.PDRectangle box = document.getPage(index).getMediaBox();
    double width = box.getWidth() * PDF_RENDER_DPI / 72D;
    double height = box.getHeight() * PDF_RENDER_DPI / 72D;
    if (!Double.isFinite(width)
        || !Double.isFinite(height)
        || width < 1
        || height < 1
        || width > Integer.MAX_VALUE
        || height > Integer.MAX_VALUE) {
      throw new InvalidImportException("A PDF page has invalid render dimensions.");
    }
    validatePagePixels((int) Math.ceil(width), (int) Math.ceil(height));
  }

  private void validatePagePixels(int width, int height) {
    if (width < 1 || height < 1 || (long) width * height > MAX_PAGE_PIXELS) {
      throw new InvalidImportException("A source page exceeds the 20 megapixel limit.");
    }
  }

  private byte[] encodeJpeg(BufferedImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "jpeg", output)) throw new IOException();
    return output.toByteArray();
  }

  private String safeFilename(String name) {
    String value = name == null ? "source" : name.replaceAll("[\\r\\n\\\\/]", "_").trim();
    return value.isBlank() ? "source" : value.substring(0, Math.min(value.length(), 255));
  }

  private String limitText(String value) {
    return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), 12000));
  }

  private String generatedCode(long batchId, QuestionImportCandidate candidate) {
    return "IMPORT-" + batchId + "-" + candidate.getId();
  }

  private String defaultString(String value) {
    return value == null ? "" : value.trim();
  }

  private String trimOrNull(String value) {
    String result = defaultString(value);
    return result.isBlank() ? null : result.toUpperCase(Locale.ROOT);
  }

  private String nullableText(String value) {
    String result = defaultString(value);
    return result.isBlank() ? null : result;
  }

  private enum UploadType {
    PDF,
    PNG,
    JPEG
  }

  /**
   * Local, explainable evidence only. A warning never changes a draft's importability and no
   * embedding or external search provider is used.
   */
  private static Map<Long, List<DuplicateWarning>> duplicateWarnings(
      List<QuestionImportCandidate> batchCandidates,
      List<QuestionImportCandidate> importedCandidates,
      List<Question> questionBank,
      Map<Long, List<QuestionImportDiagramCrop>> cropsByCandidate) {
    Map<Long, List<DuplicateWarning>> warnings = new LinkedHashMap<>();
    Set<Long> importedQuestionIds =
        importedCandidates.stream()
            .map(QuestionImportCandidate::getImportedQuestion)
            .filter(java.util.Objects::nonNull)
            .map(Question::getId)
            .collect(java.util.stream.Collectors.toSet());

    for (QuestionImportCandidate candidate : batchCandidates) {
      if (!candidate.isReviewable()) {
        continue;
      }
      List<DuplicateWarning> candidateWarnings = new ArrayList<>();
      for (QuestionImportCandidate peer : batchCandidates) {
        if (!peer.isReviewable() || candidate.getId().equals(peer.getId())) {
          continue;
        }
        candidateWarnings.add(candidateWarning(candidate, peer, true));
      }
      for (QuestionImportCandidate imported : importedCandidates) {
        if (candidate.getId().equals(imported.getId())) {
          continue;
        }
        candidateWarnings.add(candidateWarning(candidate, imported, false));
      }
      for (Question question : questionBank) {
        if (importedQuestionIds.contains(question.getId())) {
          continue;
        }
        candidateWarnings.add(
            questionWarning(
                candidate, question, cropsByCandidate.getOrDefault(candidate.getId(), List.of())));
      }
      List<DuplicateWarning> relevantWarnings =
          candidateWarnings.stream()
              .filter(java.util.Objects::nonNull)
              .sorted(
                  Comparator.comparing(DuplicateWarning::strongestSignal)
                      .reversed()
                      .thenComparing(warning -> warning.target().label()))
              .limit(10)
              .toList();
      warnings.put(candidate.getId(), relevantWarnings);
    }
    return warnings;
  }

  private static DuplicateWarning candidateWarning(
      QuestionImportCandidate candidate,
      QuestionImportCandidate comparison,
      boolean isCurrentBatchPeer) {
    List<DuplicateSignal> signals = candidateSignals(candidate, comparison);
    if (signals.isEmpty()) {
      return null;
    }
    String label =
        isCurrentBatchPeer
            ? "Draft " + comparison.getCandidateNumber()
            : "Previously imported question " + comparison.getImportedQuestion().getCode();
    return new DuplicateWarning(
        new DuplicateTarget(
            isCurrentBatchPeer ? "IMPORT_DRAFT" : "IMPORTED_QUESTION",
            isCurrentBatchPeer ? comparison.getId() : null,
            isCurrentBatchPeer ? null : comparison.getImportedQuestion().getId(),
            label),
        signals,
        strongestSignal(signals));
  }

  private static DuplicateWarning questionWarning(
      QuestionImportCandidate candidate,
      Question question,
      List<QuestionImportDiagramCrop> candidateCrops) {
    List<DuplicateSignal> signals = new ArrayList<>();
    addPromptSignal(signals, candidate.getPrompt(), question.getPrompt());
    addMetadataSignal(
        signals,
        candidate.getTotalMarks(),
        candidate.getReviewedQuestionType(),
        candidate.getSyllabusTopic(),
        question.getTotalMarks(),
        question.getQuestionType(),
        question.getSyllabusTopic());
    addImageSignals(signals, candidateCrops, question.getImages());
    if (signals.isEmpty()) {
      return null;
    }
    return new DuplicateWarning(
        new DuplicateTarget(
            "QUESTION_BANK", null, question.getId(), "Question Bank item " + question.getCode()),
        signals,
        strongestSignal(signals));
  }

  private static List<DuplicateSignal> candidateSignals(
      QuestionImportCandidate candidate, QuestionImportCandidate comparison) {
    List<DuplicateSignal> signals = new ArrayList<>();
    addPromptSignal(signals, candidate.getPrompt(), comparison.getPrompt());
    addSourceSignals(signals, candidate.getSourcePage(), comparison.getSourcePage());
    addMetadataSignal(
        signals,
        candidate.getTotalMarks(),
        candidate.getReviewedQuestionType(),
        candidate.getSyllabusTopic(),
        comparison.getTotalMarks(),
        comparison.getReviewedQuestionType(),
        comparison.getSyllabusTopic());
    return signals;
  }

  private static void addPromptSignal(
      List<DuplicateSignal> signals, String firstPrompt, String secondPrompt) {
    int similarity = promptSimilarity(firstPrompt, secondPrompt);
    if (similarity >= 85) {
      String detail =
          similarity == 100
              ? "The normalized question text matches exactly."
              : "The normalized question text is " + similarity + "% similar.";
      signals.add(new DuplicateSignal("NORMALIZED_PROMPT_SIMILARITY", similarity, detail));
    }
  }

  private static void addSourceSignals(
      List<DuplicateSignal> signals,
      QuestionImportSourcePage first,
      QuestionImportSourcePage second) {
    if (first.getSourceChecksum() != null
        && first.getSourceChecksum().equals(second.getSourceChecksum())
        && first.getSourcePageNumber() == second.getSourcePageNumber()) {
      signals.add(
          new DuplicateSignal(
              "SOURCE_CHECKSUM_PAGE_IDENTITY",
              100,
              "Both drafts came from the same source file checksum and page number."));
    }
    String firstImageHash = valueOrHash(first.getPageImageSha256(), first.getPageImageBytes());
    String secondImageHash = valueOrHash(second.getPageImageSha256(), second.getPageImageBytes());
    if (firstImageHash.equals(secondImageHash)) {
      signals.add(
          new DuplicateSignal(
              "EXACT_PAGE_IMAGE_SHA256",
              100,
              "The source-page image bytes have the same SHA-256 hash."));
    } else {
      addNearImageSignal(
          signals,
          valueOrPerceptualHash(first.getPageImagePerceptualHash(), first.getPageImageBytes()),
          valueOrPerceptualHash(second.getPageImagePerceptualHash(), second.getPageImageBytes()),
          "source-page image");
    }
  }

  private static void addMetadataSignal(
      List<DuplicateSignal> signals,
      BigDecimal firstMarks,
      Question.QuestionType firstType,
      SyllabusTopic firstTopic,
      BigDecimal secondMarks,
      Question.QuestionType secondType,
      SyllabusTopic secondTopic) {
    if (firstTopic != null
        && secondTopic != null
        && firstMarks.compareTo(secondMarks) == 0
        && firstType == secondType
        && firstTopic.getId().equals(secondTopic.getId())) {
      signals.add(
          new DuplicateSignal(
              "MATCHING_MARKS_TYPE_TOPIC",
              70,
              "Marks, reviewed question type, and syllabus topic all match."));
    }
  }

  private static void addImageSignals(
      List<DuplicateSignal> signals,
      List<QuestionImportDiagramCrop> candidateCrops,
      List<QuestionImage> questionImages) {
    for (QuestionImportDiagramCrop crop : candidateCrops) {
      for (QuestionImage image : questionImages) {
        String cropHash = valueOrHash(crop.getCropSha256(), crop.getCropImageBytes());
        String imageHash = valueOrHash(image.getImageSha256(), image.getImageBytes());
        if (cropHash.equals(imageHash)) {
          signals.add(
              new DuplicateSignal(
                  "EXACT_IMAGE_SHA256",
                  100,
                  "A diagram crop and Question Bank image have the same SHA-256 hash."));
          return;
        }
        int distance =
            ImageFingerprint.hammingDistance(
                valueOrPerceptualHash(crop.getCropPerceptualHash(), crop.getCropImageBytes()),
                valueOrPerceptualHash(image.getImagePerceptualHash(), image.getImageBytes()));
        if (distance <= 6) {
          signals.add(
              new DuplicateSignal(
                  "NEAR_IMAGE_PERCEPTUAL_HASH",
                  100 - distance,
                  "A diagram crop and Question Bank image have a near perceptual hash (distance "
                      + distance
                      + ")."));
          return;
        }
      }
    }
  }

  private static void addNearImageSignal(
      List<DuplicateSignal> signals, String firstHash, String secondHash, String imageDescription) {
    int distance = ImageFingerprint.hammingDistance(firstHash, secondHash);
    if (distance <= 6) {
      signals.add(
          new DuplicateSignal(
              "NEAR_IMAGE_PERCEPTUAL_HASH",
              100 - distance,
              "The "
                  + imageDescription
                  + " has a near perceptual hash (distance "
                  + distance
                  + ")."));
    }
  }

  private static int strongestSignal(List<DuplicateSignal> signals) {
    return signals.stream().mapToInt(DuplicateSignal::strength).max().orElse(0);
  }

  private static String valueOrHash(String storedHash, byte[] bytes) {
    return storedHash == null ? ImageFingerprint.sha256(bytes) : storedHash;
  }

  private static String valueOrPerceptualHash(String storedHash, byte[] bytes) {
    return storedHash == null ? ImageFingerprint.perceptualHash(bytes) : storedHash;
  }

  private static int promptSimilarity(String firstPrompt, String secondPrompt) {
    String firstNormalized = normalizedPrompt(firstPrompt);
    String secondNormalized = normalizedPrompt(secondPrompt);
    if (firstNormalized.isBlank() || secondNormalized.isBlank()) {
      return 0;
    }
    if (firstNormalized.equals(secondNormalized)) {
      return 100;
    }
    Set<String> firstWords = normalizedWords(firstPrompt);
    Set<String> secondWords = normalizedWords(secondPrompt);
    if (firstWords.isEmpty() || secondWords.isEmpty()) return 0;
    if (firstWords.equals(secondWords)) return 100;
    Set<String> intersection = new HashSet<>(firstWords);
    intersection.retainAll(secondWords);
    Set<String> union = new HashSet<>(firstWords);
    union.addAll(secondWords);
    return (int) Math.round(intersection.size() * 100.0 / union.size());
  }

  private static Set<String> normalizedWords(String prompt) {
    if (prompt == null) return Set.of();
    Set<String> words = new HashSet<>();
    for (String word : normalizedPrompt(prompt).split(" ")) {
      // Numbering is layout metadata, not part of the question wording.
      if (!word.isBlank() && !word.matches("\\d+")) words.add(word);
    }
    return words;
  }

  private static String normalizedPrompt(String prompt) {
    if (prompt == null) {
      return "";
    }
    String normalized =
        Normalizer.normalize(prompt, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("^(?:question\\s*)?\\d{1,3}\\s*[.)-]\\s*", "")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
    return normalized.replaceAll("\\s+", " ");
  }

  public record ImageContent(String contentType, byte[] bytes) {}

  public record SourceReference(long pageId, String filename, int pageNumber) {}

  public record CandidateDetail(
      long id,
      int number,
      String status,
      int confidence,
      String warningMessage,
      String code,
      Long syllabusTopicId,
      String prompt,
      String modelAnswer,
      BigDecimal totalMarks,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      String suggestedTags,
      boolean includeSourceImage,
      SourceReference source,
      List<DiagramCropDetail> diagramCrops,
      SuggestionDetail suggestions,
      FieldConfidence confidenceByField,
      LineageDetail lineage,
      List<DuplicateWarning> duplicateWarnings) {
    static CandidateDetail from(
        QuestionImportCandidate candidate, List<QuestionImportDiagramCrop> crops) {
      return new CandidateDetail(
          candidate.getId(),
          candidate.getCandidateNumber(),
          candidate.getStatus().name(),
          candidate.getConfidence(),
          candidate.getWarningMessage(),
          candidate.getQuestionCode(),
          candidate.getSyllabusTopic() == null ? null : candidate.getSyllabusTopic().getId(),
          candidate.getPrompt(),
          candidate.getModelAnswer(),
          candidate.getTotalMarks(),
          candidate.getReviewedQuestionType(),
          candidate.getReviewedDifficulty(),
          candidate.getSuggestedTags(),
          candidate.isIncludeSourceImage(),
          new SourceReference(
              candidate.getSourcePage().getId(),
              candidate.getSourcePage().getSourceFilename(),
              candidate.getSourcePage().getSourcePageNumber()),
          crops.stream().map(DiagramCropDetail::from).toList(),
          new SuggestionDetail(
              candidate.getSuggestedPrompt(),
              candidate.getSuggestedModelAnswer(),
              candidate.getSuggestedTotalMarks(),
              candidate.getSuggestedQuestionType(),
              candidate.getSuggestedDifficulty(),
              candidate.getSuggestedTags()),
          new FieldConfidence(
              candidate.getPromptConfidence(),
              candidate.getModelAnswerConfidence(),
              candidate.getClassificationConfidence(),
              candidate.getMarksConfidence()),
          new LineageDetail(
              candidate.getParentCandidate() == null
                  ? null
                  : candidate.getParentCandidate().getId(),
              candidate.getSupersededByCandidate() == null
                  ? null
                  : candidate.getSupersededByCandidate().getId(),
              candidate.getRejectedReason(),
              candidate.getRejectedAt() != null),
          List.of());
    }

    CandidateDetail withDuplicateWarnings(List<DuplicateWarning> warnings) {
      return new CandidateDetail(
          id,
          number,
          status,
          confidence,
          warningMessage,
          code,
          syllabusTopicId,
          prompt,
          modelAnswer,
          totalMarks,
          questionType,
          difficulty,
          suggestedTags,
          includeSourceImage,
          source,
          diagramCrops,
          suggestions,
          confidenceByField,
          lineage,
          warnings);
    }
  }

  public record BatchDetail(
      long id,
      String status,
      String originalFilename,
      int attemptCount,
      String processingError,
      List<CandidateDetail> candidates) {
    static BatchDetail from(
        QuestionImportBatch batch,
        List<QuestionImportCandidate> candidates,
        List<QuestionImportDiagramCrop> crops,
        List<QuestionImportCandidate> importedCandidates,
        List<Question> questionBank) {
      Map<Long, List<QuestionImportDiagramCrop>> cropsByCandidate =
          crops.stream()
              .collect(java.util.stream.Collectors.groupingBy(crop -> crop.getCandidate().getId()));
      List<CandidateDetail> details =
          candidates.stream()
              .map(
                  candidate ->
                      CandidateDetail.from(
                          candidate, cropsByCandidate.getOrDefault(candidate.getId(), List.of())))
              .toList();
      Map<Long, List<DuplicateWarning>> warnings =
          duplicateWarnings(candidates, importedCandidates, questionBank, cropsByCandidate);
      return new BatchDetail(
          batch.getId(),
          batch.getStatus().name(),
          batch.getOriginalFilename(),
          batch.getAttemptCount(),
          batch.getLastProcessingError(),
          details.stream()
              .map(
                  candidate ->
                      candidate.withDuplicateWarnings(
                          warnings.getOrDefault(candidate.id(), List.of())))
              .toList());
    }
  }

  public record CropRectangle(int x, int y, int width, int height) {}

  public record SuggestionDetail(
      String prompt,
      String modelAnswer,
      BigDecimal totalMarks,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      String tags) {}

  public record FieldConfidence(
      Integer prompt, Integer modelAnswer, Integer classification, Integer marks) {}

  public record LineageDetail(
      Long parentCandidateId,
      Long supersededByCandidateId,
      String rejectionReason,
      boolean isRejected) {}

  public record DuplicateTarget(String kind, Long candidateId, Long questionId, String label) {}

  public record DuplicateSignal(String code, int strength, String detail) {}

  public record DuplicateWarning(
      DuplicateTarget target, List<DuplicateSignal> signals, int strongestSignal) {}

  public record DiagramCropDetail(
      long id,
      String regionId,
      String subQuestionId,
      CropRectangle rectangle,
      int width,
      int height,
      String contentType) {
    static DiagramCropDetail from(QuestionImportDiagramCrop crop) {
      return new DiagramCropDetail(
          crop.getId(),
          crop.getDiagramRegionId(),
          crop.getSubQuestionId(),
          new CropRectangle(crop.getX(), crop.getY(), crop.getWidth(), crop.getHeight()),
          crop.getCropWidth(),
          crop.getCropHeight(),
          crop.getContentType());
    }
  }

  public record ImportResult(List<Long> questionIds, String message) {}

  public static final class BatchNotFoundException extends RuntimeException {}

  public static final class CandidateNotFoundException extends RuntimeException {}

  public static final class SourcePageNotFoundException extends RuntimeException {}

  public static final class DiagramCropNotFoundException extends RuntimeException {}

  public static final class InvalidImportException extends RuntimeException {
    public InvalidImportException(String message) {
      super(message);
    }
  }
}
