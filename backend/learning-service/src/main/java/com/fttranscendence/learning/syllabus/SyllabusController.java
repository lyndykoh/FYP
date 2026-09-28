package com.fttranscendence.learning.syllabus;

import com.fttranscendence.learning.classroom.ClassController;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Stable, read-only syllabus lookup contract. It intentionally exposes no mutation endpoints:
 * curriculum records remain migration-managed.
 */
@RestController
@RequestMapping(
    value = "/api/learning/shared/syllabus",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class SyllabusController {

  private final SyllabusService syllabus;

  public SyllabusController(SyllabusService syllabus) {
    this.syllabus = syllabus;
  }

  @GetMapping("/tree")
  public SyllabusService.SyllabusTreeResponse tree() {
    return syllabus.tree();
  }

  @GetMapping("/children")
  public SyllabusService.SyllabusNodeList children(
      @RequestParam(required = false) @Positive Long parentId,
      @RequestParam(required = false) SyllabusTopic.NodeType nodeType) {
    return syllabus.children(parentId, nodeType);
  }

  @ExceptionHandler(SyllabusService.SyllabusNotFoundException.class)
  ResponseEntity<ClassController.ApiError> notFound(
      SyllabusService.SyllabusNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "SYLLABUS_NODE_NOT_FOUND", "Syllabus node was not found.");
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class
  })
  ResponseEntity<ClassController.ApiError> invalidQuery(RuntimeException exception) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_SYLLABUS_QUERY", "Syllabus filters are invalid.");
  }

  private ResponseEntity<ClassController.ApiError> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(new ClassController.ApiError(code, message, Map.of()));
  }
}
