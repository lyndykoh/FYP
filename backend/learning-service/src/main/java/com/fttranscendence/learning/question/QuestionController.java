package com.fttranscendence.learning.question;

import com.fttranscendence.learning.classroom.ClassController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(
    value = "/api/learning/tutor/questions",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class QuestionController {

  private final QuestionService questionService;
  private final QuestionImageService questionImages;

  public QuestionController(QuestionService questionService, QuestionImageService questionImages) {
    this.questionService = questionService;
    this.questionImages = questionImages;
  }

  @GetMapping
  public QuestionService.QuestionPage list(
      @RequestParam(required = false) @Positive Long topicId,
      @RequestParam(required = false) Question.QuestionType questionType,
      @RequestParam(required = false) Question.Difficulty difficulty,
      @RequestParam(defaultValue = "ACTIVE") Question.ArchiveState archiveState,
      @RequestParam(required = false) @Size(max = 120) String search,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return questionService.list(
        new QuestionService.QuestionQuery(
            topicId, questionType, difficulty, archiveState, search, page, size));
  }

  @GetMapping("/{questionId}")
  public QuestionService.QuestionDetail get(@PathVariable @Positive long questionId) {
    return questionService.get(questionId);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<QuestionService.QuestionDetail> create(
      @Valid @RequestBody QuestionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(questionService.create(request));
  }

  @PutMapping(value = "/{questionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public QuestionService.QuestionDetail update(
      @PathVariable @Positive long questionId, @Valid @RequestBody QuestionRequest request) {
    return questionService.update(questionId, request);
  }

  @PostMapping(value = "/{questionId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<QuestionImageService.ImageSummary> uploadImage(
      @PathVariable @Positive long questionId, @RequestPart("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED).body(questionImages.upload(questionId, file));
  }

  @GetMapping("/{questionId}/images/{imageId}")
  public ResponseEntity<byte[]> image(
      @PathVariable @Positive long questionId, @PathVariable @Positive long imageId) {
    QuestionImageService.ImageContent content = questionImages.content(questionId, imageId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .body(content.bytes());
  }

  @DeleteMapping("/{questionId}/images/{imageId}")
  public ResponseEntity<Void> deleteImage(
      @PathVariable @Positive long questionId, @PathVariable @Positive long imageId) {
    questionImages.remove(questionId, imageId);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(QuestionService.QuestionNotFoundException.class)
  ResponseEntity<ClassController.ApiError> notFound(
      QuestionService.QuestionNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Question was not found", Map.of());
  }

  @ExceptionHandler(QuestionService.DuplicateQuestionCodeException.class)
  ResponseEntity<ClassController.ApiError> duplicateCode(
      QuestionService.DuplicateQuestionCodeException exception) {
    return error(
        HttpStatus.CONFLICT,
        "QUESTION_CODE_EXISTS",
        "A question already uses this code",
        Map.of("code", "This code is already in use."));
  }

  @ExceptionHandler(QuestionService.QuestionInUseException.class)
  ResponseEntity<ClassController.ApiError> questionInUse(
      QuestionService.QuestionInUseException exception) {
    return error(
        HttpStatus.CONFLICT,
        "QUESTION_IN_USE",
        "A question already used in a worksheet cannot have its content changed",
        Map.of());
  }

  @ExceptionHandler(QuestionService.InvalidQuestionRequestException.class)
  ResponseEntity<ClassController.ApiError> invalidRequest(
      QuestionService.InvalidQuestionRequestException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_QUESTION_REQUEST",
        exception.getMessage(),
        Map.of(exception.field(), exception.getMessage()));
  }

  @ExceptionHandler(QuestionImageService.ImageNotFoundException.class)
  ResponseEntity<ClassController.ApiError> imageNotFound(
      QuestionImageService.ImageNotFoundException exception) {
    return error(
        HttpStatus.NOT_FOUND, "QUESTION_IMAGE_NOT_FOUND", "Question image was not found", Map.of());
  }

  @ExceptionHandler(QuestionImageService.InvalidImageException.class)
  ResponseEntity<ClassController.ApiError> invalidImage(
      QuestionImageService.InvalidImageException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_QUESTION_IMAGE",
        exception.getMessage(),
        Map.of("file", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ClassController.ApiError> invalidQuery(
      MethodArgumentTypeMismatchException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_QUESTION_QUERY",
        "Question filters contain an invalid value");
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ClassController.ApiError> validation(HandlerMethodValidationException exception) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Question filters are invalid");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ClassController.ApiError> dataUnavailable(DataAccessException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "QUESTION_DATABASE_UNAVAILABLE",
        "Question bank data is temporarily unavailable");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ClassController.ApiError> requestValidation(
      MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Question request is invalid", fields);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ClassController.ApiError> malformed(HttpMessageNotReadableException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_QUESTION_REQUEST",
        "Question request contains invalid JSON or values",
        Map.of());
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message) {
    return error(status, code, message, Map.of());
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ClassController.ApiError(code, message, fields));
  }
}
