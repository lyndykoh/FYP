package com.fttranscendence.learning.student;

import com.fttranscendence.learning.classroom.ClassController;
import com.fttranscendence.learning.security.AuthenticatedUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class StudentController {

  private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

  private final StudentService studentService;
  private final TutorNoteService tutorNoteService;

  public StudentController(StudentService studentService, TutorNoteService tutorNoteService) {
    this.studentService = studentService;
    this.tutorNoteService = tutorNoteService;
  }

  @GetMapping("/api/learning/tutor/students")
  public List<StudentRequest.StudentResponse> list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) @Positive Long classId) {
    return studentService.listOwnedStudents(user.userId(), classId);
  }

  @GetMapping("/api/learning/tutor/student-accounts")
  public List<StudentAccountResponse> availableAccounts(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearerToken,
      @RequestParam(required = false) @jakarta.validation.constraints.Size(max = 120)
          String search) {
    return studentService.listAvailableStudentAccounts(user.userId(), bearerToken, search);
  }

  @GetMapping("/api/learning/tutor/students/{studentId}")
  public StudentRequest.StudentResponse detail(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long studentId) {
    return studentService.getOwnedStudent(user.userId(), studentId);
  }

  @PostMapping(value = "/api/learning/tutor/students", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StudentRequest.StudentResponse> create(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestHeader("Authorization") String bearerToken,
      @Valid @RequestBody StudentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(studentService.create(user.userId(), request, bearerToken));
  }

  @PutMapping(
      value = "/api/learning/tutor/students/{studentId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public StudentRequest.StudentResponse update(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long studentId,
      @Valid @RequestBody StudentRequest request) {
    return studentService.update(user.userId(), studentId, request);
  }

  @GetMapping("/api/learning/tutor/students/{studentId}/profile")
  public StudentProfileResponse tutorProfile(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long studentId) {
    return studentService.getOwnedStudentProfile(user.userId(), studentId);
  }

  @GetMapping("/api/learning/student/profile")
  public StudentProfileResponse studentProfile(@AuthenticationPrincipal AuthenticatedUser user) {
    return studentService.getLinkedStudentProfile(user.userId());
  }

  @GetMapping("/api/learning/tutor/students/{studentId}/notes")
  public List<TutorNoteRequest.Response> listNotes(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable @Positive long studentId) {
    return tutorNoteService.list(user.userId(), studentId);
  }

  @PostMapping(
      value = "/api/learning/tutor/students/{studentId}/notes",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TutorNoteRequest.Response> createNote(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long studentId,
      @Valid @RequestBody TutorNoteRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(tutorNoteService.create(user.userId(), studentId, request));
  }

  @PutMapping(
      value = "/api/learning/tutor/students/{studentId}/notes/{noteId}",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public TutorNoteRequest.Response updateNote(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long studentId,
      @PathVariable @Positive long noteId,
      @Valid @RequestBody TutorNoteRequest request) {
    return tutorNoteService.update(user.userId(), studentId, noteId, request);
  }

  @DeleteMapping("/api/learning/tutor/students/{studentId}/notes/{noteId}")
  public ResponseEntity<Void> deleteNote(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @Positive long studentId,
      @PathVariable @Positive long noteId) {
    tutorNoteService.delete(user.userId(), studentId, noteId);
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(StudentService.StudentNotFoundException.class)
  ResponseEntity<ClassController.ApiError> studentNotFound(
      StudentService.StudentNotFoundException error) {
    logger.info("student_not_found: {}", error.getMessage());
    return error(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", error.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.ProfileNotFoundException.class)
  ResponseEntity<ClassController.ApiError> profileNotFound(
      StudentService.ProfileNotFoundException error) {
    logger.info("student_profile_not_found");
    return error(
        HttpStatus.NOT_FOUND,
        "STUDENT_PROFILE_NOT_FOUND",
        "Student profile was not found",
        Map.of());
  }

  @ExceptionHandler(TutorNoteService.TutorNoteNotFoundException.class)
  ResponseEntity<ClassController.ApiError> tutorNoteNotFound(
      TutorNoteService.TutorNoteNotFoundException error) {
    return error(
        HttpStatus.NOT_FOUND, "TUTOR_NOTE_NOT_FOUND", "Tutor note was not found", Map.of());
  }

  @ExceptionHandler(StudentService.ClassNotFoundException.class)
  ResponseEntity<ClassController.ApiError> classNotFound(
      StudentService.ClassNotFoundException error) {
    return error(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", error.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.DuplicateMembershipException.class)
  ResponseEntity<ClassController.ApiError> duplicateMembership(
      StudentService.DuplicateMembershipException error) {
    return error(HttpStatus.CONFLICT, "DUPLICATE_MEMBERSHIP", error.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.LoginIdentityConflictException.class)
  ResponseEntity<ClassController.ApiError> loginIdentityConflict(
      StudentService.LoginIdentityConflictException error) {
    return error(HttpStatus.CONFLICT, "LOGIN_IDENTITY_CONFLICT", error.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.InvalidStudentRequestException.class)
  ResponseEntity<ClassController.ApiError> invalid(
      StudentService.InvalidStudentRequestException error) {
    logger.info("invalid_student_request: {}", error.getMessage());
    return error(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_REQUEST", error.getMessage(), Map.of());
  }

  @ExceptionHandler(StudentService.StudentDirectoryUnavailableException.class)
  ResponseEntity<ClassController.ApiError> directoryUnavailable(
      StudentService.StudentDirectoryUnavailableException error) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STUDENT_DIRECTORY_UNAVAILABLE",
        "Student accounts are temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ClassController.ApiError> validation(MethodArgumentNotValidException exception) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Student request is invalid", fields);
  }

  @ExceptionHandler({DataAccessException.class, StudentService.StudentPersistenceException.class})
  ResponseEntity<ClassController.ApiError> persistence(Exception error) {
    logger.error("student_database_unavailable", error);
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "STUDENT_DATABASE_UNAVAILABLE",
        "Student data is temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(TutorNoteService.TutorNotePersistenceException.class)
  ResponseEntity<ClassController.ApiError> tutorNotePersistence(
      TutorNoteService.TutorNotePersistenceException error) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE,
        "TUTOR_NOTE_DATABASE_UNAVAILABLE",
        "Tutor notes are temporarily unavailable",
        Map.of());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ClassController.ApiError> malformed(HttpMessageNotReadableException error) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_STUDENT_REQUEST",
        "Student request contains invalid JSON or values",
        Map.of());
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ClassController.ApiError> methodValidation(
      HandlerMethodValidationException error) {
    return error(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Student request is invalid", Map.of());
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ClassController.ApiError(code, message, fields));
  }
}
