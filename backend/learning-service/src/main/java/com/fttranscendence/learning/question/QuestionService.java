package com.fttranscendence.learning.question;

import com.fttranscendence.learning.syllabus.SyllabusTopic;
import com.fttranscendence.learning.syllabus.SyllabusTopicRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

  private final QuestionRepository questions;
  private final SyllabusTopicRepository syllabusTopics;
  private final EntityManager entityManager;

  public QuestionService(
      QuestionRepository questions,
      SyllabusTopicRepository syllabusTopics,
      EntityManager entityManager) {
    this.questions = questions;
    this.syllabusTopics = syllabusTopics;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public QuestionPage list(QuestionQuery query) {
    String escapedSearchTerm = normalizeSearch(query.search());
    PageRequest pageRequest = PageRequest.of(query.page(), query.size());
    Page<Question> questionPage =
        questions.findQuestionBank(
            query.topicId(),
            query.questionType(),
            query.difficulty(),
            query.archiveState(),
            escapedSearchTerm,
            pageRequest);

    return new QuestionPage(
        questionPage.getContent().stream().map(QuestionItem::from).toList(),
        questionPage.getNumber(),
        questionPage.getSize(),
        questionPage.getTotalElements(),
        questionPage.getTotalPages(),
        questionPage.hasNext());
  }

  @Transactional(readOnly = true)
  public QuestionDetail get(long questionId) {
    return QuestionDetail.from(getQuestion(questionId));
  }

  @Transactional
  public QuestionDetail create(QuestionRequest request) {
    String normalizedQuestionCode = normalizedCode(request.code());
    if (questions.existsByCode(normalizedQuestionCode)) {
      throw new DuplicateQuestionCodeException();
    }

    Question question = new Question();
    applyRequest(question, request, false);

    return saveQuestion(question);
  }

  @Transactional
  public QuestionDetail update(long questionId, QuestionRequest request) {
    Question existingQuestion = getQuestion(questionId);
    String normalizedQuestionCode = normalizedCode(request.code());
    rejectDuplicateCode(normalizedQuestionCode, existingQuestion.getId());

    boolean contentCanNoLongerChange =
        questions.isUsedByAnyWorksheet(questionId) && changesContent(existingQuestion, request);
    if (contentCanNoLongerChange) {
      throw new QuestionInUseException();
    }

    applyRequest(existingQuestion, request, true);

    return saveQuestion(existingQuestion);
  }

  private QuestionDetail saveQuestion(Question question) {
    try {
      Question savedQuestion = questions.save(question);
      entityManager.flush();

      return QuestionDetail.from(savedQuestion);
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      throw new DuplicateQuestionCodeException();
    }
  }

  private void rejectDuplicateCode(String normalizedCode, Long currentQuestionId) {
    questions
        .findByCode(normalizedCode)
        .filter(question -> !question.getId().equals(currentQuestionId))
        .ifPresent(
            question -> {
              throw new DuplicateQuestionCodeException();
            });
  }

  private Question getQuestion(long questionId) {
    return questions.findById(questionId).orElseThrow(QuestionNotFoundException::new);
  }

  private void applyRequest(
      Question question, QuestionRequest request, boolean updatingExistingQuestion) {
    SyllabusTopic syllabusTopic = requireActiveQuestionTopic(request.syllabusTopicId());
    validateAggregate(request);

    question.setCode(normalizedCode(request.code()));
    question.setSyllabusTopic(syllabusTopic);
    question.setQuestionType(request.questionType());
    question.setDifficulty(defaultDifficulty(request.difficulty()));
    question.setPrompt(request.prompt().trim());
    question.setTotalMarks(request.totalMarks());
    question.setModelAnswer(request.modelAnswer().trim());
    question.replaceMarkingComponents(markingComponentsFrom(request));
    question.replaceKeywords(normalizedKeywords(request.keywords()));

    applyArchiveState(question, request.archiveState(), updatingExistingQuestion);
  }

  private SyllabusTopic requireActiveQuestionTopic(long syllabusTopicId) {
    return syllabusTopics
        .findById(syllabusTopicId)
        .filter(SyllabusTopic::isActive)
        .filter(this::isQuestionTopic)
        .orElseThrow(
            () ->
                new InvalidQuestionRequestException(
                    "syllabusTopicId", "Choose an active syllabus topic or subtopic."));
  }

  private boolean isQuestionTopic(SyllabusTopic topic) {
    return topic.getNodeType() == SyllabusTopic.NodeType.TOPIC
        || topic.getNodeType() == SyllabusTopic.NodeType.SUBTOPIC;
  }

  private Question.Difficulty defaultDifficulty(Question.Difficulty difficulty) {
    return difficulty == null ? Question.Difficulty.FOUNDATION : difficulty;
  }

  private List<MarkingComponent> markingComponentsFrom(QuestionRequest request) {
    return request.markingComponents().stream().map(this::markingComponentFrom).toList();
  }

  private MarkingComponent markingComponentFrom(
      QuestionRequest.MarkingComponentRequest componentRequest) {
    return new MarkingComponent(
        componentRequest.description().trim(),
        componentRequest.marks(),
        normalizedKeywords(componentRequest.keywords()));
  }

  private void applyArchiveState(
      Question question,
      Question.ArchiveState requestedArchiveState,
      boolean updatingExistingQuestion) {
    if (requestedArchiveState == null && updatingExistingQuestion) {
      throw new InvalidQuestionRequestException(
          "archiveState", "Choose whether this question is active or archived.");
    }

    if (requestedArchiveState == Question.ArchiveState.ARCHIVED) {
      question.archive();
      return;
    }

    question.restore();
  }

  private void validateAggregate(QuestionRequest request) {
    if (request.totalMarks().scale() > 2) {
      throw new InvalidQuestionRequestException(
          "totalMarks", "Total marks may have at most two decimal places.");
    }

    BigDecimal componentTotal =
        request.markingComponents().stream()
            .map(QuestionRequest.MarkingComponentRequest::marks)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (request.totalMarks().compareTo(componentTotal) != 0) {
      throw new InvalidQuestionRequestException(
          "markingComponents", "Marking component marks must exactly equal the total marks.");
    }

    validateMarkingComponents(request.markingComponents());
    validateUniqueKeywords("keywords", request.keywords(), "Keywords must be unique.");
  }

  private void validateMarkingComponents(
      List<QuestionRequest.MarkingComponentRequest> componentRequests) {
    for (QuestionRequest.MarkingComponentRequest componentRequest : componentRequests) {
      if (componentRequest.marks().scale() > 2) {
        throw new InvalidQuestionRequestException(
            "markingComponents", "Component marks may have at most two decimal places.");
      }

      validateUniqueKeywords(
          "markingComponents", componentRequest.keywords(), "Component keywords must be unique.");
    }
  }

  private void validateUniqueKeywords(
      String field, List<String> keywords, String duplicateMessage) {
    List<String> normalizedKeywordList = normalizedKeywords(keywords);
    boolean containsDuplicateKeyword =
        normalizedKeywordList.stream().distinct().count() != normalizedKeywordList.size();
    if (containsDuplicateKeyword) {
      throw new InvalidQuestionRequestException(field, duplicateMessage);
    }
  }

  private boolean changesContent(Question question, QuestionRequest request) {
    boolean coreContentChanged =
        !question.getCode().equals(normalizedCode(request.code()))
            || !question.getSyllabusTopic().getId().equals(request.syllabusTopicId())
            || question.getQuestionType() != request.questionType()
            || question.getDifficulty() != defaultDifficulty(request.difficulty())
            || !question.getPrompt().equals(request.prompt().trim())
            || question.getTotalMarks().compareTo(request.totalMarks()) != 0
            || !question.getModelAnswer().equals(request.modelAnswer().trim())
            || !componentRequestsEqual(
                question.getMarkingComponents(), request.markingComponents());
    if (coreContentChanged) {
      return true;
    }

    return !question.getKeywords().equals(normalizedKeywords(request.keywords()));
  }

  private boolean componentRequestsEqual(
      List<MarkingComponent> components, List<QuestionRequest.MarkingComponentRequest> requests) {
    if (components.size() != requests.size()) {
      return false;
    }

    for (int index = 0; index < components.size(); index++) {
      MarkingComponent existingComponent = components.get(index);
      QuestionRequest.MarkingComponentRequest requestedComponent = requests.get(index);
      boolean componentDiffers =
          !existingComponent.getDescription().equals(requestedComponent.description().trim())
              || existingComponent.getMarks().compareTo(requestedComponent.marks()) != 0
              || !existingComponent
                  .getKeywords()
                  .equals(normalizedKeywords(requestedComponent.keywords()));
      if (componentDiffers) {
        return false;
      }
    }

    return true;
  }

  private String normalizedCode(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private List<String> normalizedKeywords(List<String> keywords) {
    if (keywords == null) {
      return List.of();
    }

    List<String> normalizedKeywords = new ArrayList<>(keywords.size());
    for (String keyword : keywords) {
      normalizedKeywords.add(keyword.trim().toLowerCase(Locale.ROOT));
    }

    return List.copyOf(normalizedKeywords);
  }

  /**
   * The repository uses ! as the explicit LIKE escape character. Keeping the escaping here makes a
   * search for a code such as SCI_01 or 50% literal, rather than turning user input into a pattern.
   */
  private String normalizeSearch(String search) {
    if (search == null || search.isBlank()) {
      return null;
    }

    String normalizedSearch =
        java.text.Normalizer.normalize(search.trim(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT);
    String escapedSearch =
        normalizedSearch.replace("!", "!!").replace("%", "!%").replace("_", "!_");

    return "%" + escapedSearch + "%";
  }

  public record QuestionQuery(
      Long topicId,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      Question.ArchiveState archiveState,
      String search,
      int page,
      int size) {}

  public record QuestionPage(
      List<QuestionItem> items,
      int page,
      int size,
      long totalElements,
      int totalPages,
      boolean hasNext) {}

  public record QuestionItem(
      long id,
      String code,
      SyllabusTopicSummary syllabusTopic,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      String prompt,
      BigDecimal totalMarks,
      Question.ArchiveState archiveState) {
    static QuestionItem from(Question question) {
      SyllabusTopic topic = question.getSyllabusTopic();
      SyllabusTopicSummary syllabusTopic =
          new SyllabusTopicSummary(
              topic.getId(), topic.getCode(), topic.getName(), topic.getNodeType());

      return new QuestionItem(
          question.getId(),
          question.getCode(),
          syllabusTopic,
          question.getQuestionType(),
          question.getDifficulty(),
          question.getPrompt(),
          question.getTotalMarks(),
          question.getArchiveState());
    }
  }

  public record SyllabusTopicSummary(
      long id, String code, String name, SyllabusTopic.NodeType nodeType) {}

  public record QuestionDetail(
      long id,
      String code,
      SyllabusTopicSummary syllabusTopic,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      String prompt,
      BigDecimal totalMarks,
      String modelAnswer,
      Question.ArchiveState archiveState,
      List<MarkingComponentDetail> markingComponents,
      List<String> keywords,
      List<QuestionImageService.ImageSummary> images,
      java.time.LocalDateTime createdAt,
      java.time.LocalDateTime updatedAt) {
    static QuestionDetail from(Question question) {
      SyllabusTopic topic = question.getSyllabusTopic();
      SyllabusTopicSummary syllabusTopic =
          new SyllabusTopicSummary(
              topic.getId(), topic.getCode(), topic.getName(), topic.getNodeType());
      List<MarkingComponentDetail> markingComponents =
          question.getMarkingComponents().stream()
              .map(
                  component ->
                      new MarkingComponentDetail(
                          component.getPosition(),
                          component.getDescription(),
                          component.getMarks(),
                          component.getKeywords()))
              .toList();

      return new QuestionDetail(
          question.getId(),
          question.getCode(),
          syllabusTopic,
          question.getQuestionType(),
          question.getDifficulty(),
          question.getPrompt(),
          question.getTotalMarks(),
          question.getModelAnswer(),
          question.getArchiveState(),
          markingComponents,
          question.getKeywords(),
          question.getImages().stream().map(QuestionImageService.ImageSummary::from).toList(),
          question.getCreatedAt(),
          question.getUpdatedAt());
    }
  }

  public record MarkingComponentDetail(
      int position, String description, BigDecimal marks, List<String> keywords) {}

  public static final class QuestionNotFoundException extends RuntimeException {}

  public static final class DuplicateQuestionCodeException extends RuntimeException {}

  public static final class QuestionInUseException extends RuntimeException {}

  public static final class InvalidQuestionRequestException extends RuntimeException {
    private final String field;

    public InvalidQuestionRequestException(String field, String message) {
      super(message);
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
