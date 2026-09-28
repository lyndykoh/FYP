package com.fttranscendence.grading.service;

import com.fttranscendence.grading.model.ApprovedDiagnosticEvidence;
import java.math.BigDecimal;
import java.util.List;

/** Minimal, pseudonymous cross-service representation of an approved mark. */
public record ApprovedMarkingSyncPayload(
    String eventKey,
    String state,
    long revision,
    long submissionId,
    long studentId,
    long tutorUserId,
    long worksheetId,
    long worksheetQuestionId,
    long questionBankId,
    long syllabusTopicId,
    String syllabusTopicCode,
    BigDecimal approvedMarks,
    BigDecimal maxMarks,
    String approvedAt,
    List<DiagnosticEvidence> diagnosticEvidence) {
  public record DiagnosticEvidence(
      long syllabusTopicId,
      String mistakeType,
      String category,
      String description,
      List<String> missingKeywords) {
    static DiagnosticEvidence from(ApprovedDiagnosticEvidence evidence) {
      return new DiagnosticEvidence(
          evidence.getSyllabusTopicId(),
          evidence.getMistakeType().name(),
          evidence.getCategory().name(),
          evidence.getDescription(),
          evidence.getMissingKeywords());
    }
  }
}
