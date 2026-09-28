package com.fttranscendence.learning.classroom;

import com.fttranscendence.learning.security.AuthenticatedUser;
import com.fttranscendence.learning.student.ClassStudentRequest;
import com.fttranscendence.learning.student.ClassStudentResponse;
import com.fttranscendence.learning.student.StudentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestController
@RequestMapping(value = "/api/learning/tutor/classes", produces = MediaType.APPLICATION_JSON_VALUE)
public class ClassController {

  private static final Logger logger = LoggerFactory.getLogger(ClassController.class);

  private final ClassService classService;
  private final StudentService studentService;

  public ClassController(ClassService classService, StudentService studentService) {
    this.classService = classService;
    this.studentService = studentService;
  }

  @GetMapping
  public List<ClassRequest.ClassResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
    return classService.listOwnedClasses(user.userId());
  }

  @GetMapping("/{classId}")
  public ClassDetailResponse detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return classService.getOwnedClassDetail(user.userId(), classId);
  }

  /** Existing Student logins available for this Tutor to add to the class. */
  @GetMapping("/{classId}/eligible-students")
  public List<ClassStudentResponse.EligibleStudentResponse> eligibleStudents(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @RequestHeader("Authorization") String bearerToken) {
    return studentService.listEligibleClassStudents(user.userId(), classId, bearerToken);
  }

  /** Current class roster; class detail includes this same roster projection. */
  @GetMapping("/{classId}/students")
  public List<ClassStudentResponse.ClassMemberResponse> students(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long classId) {
    return studentService.listClassMembers(user.userId(), classId);
  }

  @PostMapping(value = "/{classId}/students", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ClassStudentResponse.ClassMemberResponse> addStudent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @RequestHeader("Authorization") String bearerToken,
      @Valid @RequestBody ClassStudentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            studentService.addExistingStudentToClass(user.userId(), classId, request, bearerToken));
  }

  @DeleteMapping("/{classId}/students/{studentId}")
  public ResponseEntity<Void> removeStudent(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @PathVariable @Positive long studentId) {
    studentService.removeStudentFromClass(user.userId(), classId, studentId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ClassRequest.ClassResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ClassRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(classService.create(user.userId(), request));
  }

  @PutMapping(value = "/{classId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ClassRequest.ClassResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long classId,
      @Valid @RequestBody ClassRequest request) {
    return classService.update(user.userId(), classId, request);
  }

  @ExceptionHandler(ClassService.ClassNotFoundException.class)
  ResponseEntity<ApiError> notFound(ClassService.ClassNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(ClassService.ClassAccessDeniedException.class)
  ResponseEntity<ApiError> accessDenied(ClassService.ClassAccessDeniedException exception) {
    return error(HttpStatus.FORBIDDEN, "CLASS_ACCESS_FORBIDDEN", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.ClassNotFoundException.class)
  ResponseEntity<ApiError> membershipClassNotFound(
      StudentService.ClassNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(ClassService.DuplicateClassException.class)
  ResponseEntity<ApiError> duplicate(ClassService.DuplicateClassException exception) {
    return error(HttpStatus.CONFLICT, "CLASS_ALREADY_EXISTS", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(ClassService.InvalidClassRequestException.class)
  ResponseEntity<ApiError> invalid(ClassService.InvalidClassRequestException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_CLASS_REQUEST", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.StudentNotFoundException.class)
  ResponseEntity<ApiError> studentNotFound(StudentService.StudentNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.DuplicateClassMembershipException.class)
  ResponseEntity<ApiError> duplicateMembership(
      StudentService.DuplicateClassMembershipException exception) {
    return error(HttpStatus.CONFLICT, "DUPLICATE_MEMBERSHIP", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.StudentDirectoryUnavailableException.class)
  ResponseEntity<ApiError> studentDirectoryUnavailable(
      StudentService.StudentDirectoryUnavailableException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STUDENT_DIRECTORY_UNAVAILABLE",
        "Student accounts are temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(StudentService.InvalidStudentRequestException.class)
  ResponseEntity<ApiError> invalidStudent(StudentService.InvalidStudentRequestException exception) {
    return error(
        HttpStatus.BAD_REQUEST, "INVALID_STUDENT_REQUEST", exception.getMessage(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Class request is invalid", fields);
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<ApiError> persistence(DataAccessException exception) {
    logger.error("class_database_unavailable", exception);
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CLASS_DATABASE_UNAVAILABLE",
        "Class data is temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(ClassService.ClassPersistenceException.class)
  ResponseEntity<ApiError> persistence(ClassService.ClassPersistenceException exception) {
    logger.error("class_database_unavailable", exception);
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CLASS_DATABASE_UNAVAILABLE",
        "Class data is temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_CLASS_REQUEST",
        "Class request contains invalid JSON or enum/time values",
        Map.of());
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException exception) {
    return error(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "Class request is invalid",
        Map.of("classId", "must be greater than 0"));
  }

  public record ApiError(String code, String message, Map<String, String> fields) {}

  private ResponseEntity<ApiError> error(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ApiError(code, message, fields));
  }
}
