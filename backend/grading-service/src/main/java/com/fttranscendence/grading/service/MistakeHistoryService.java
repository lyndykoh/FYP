package com.fttranscendence.grading.service;

import com.fttranscendence.grading.model.MistakeRecord;
import com.fttranscendence.grading.repository.MistakeRecordRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only projection of Tutor-approved, canonical mistake history. */
@Service
public class MistakeHistoryService {
  private final MistakeRecordRepository mistakes;

  public MistakeHistoryService(MistakeRecordRepository mistakes) {
    this.mistakes = mistakes;
  }

  @Transactional(readOnly = true)
  public List<MistakeHistoryItem> historyFor(long studentId) {
    return recordsFor(studentId).stream().map(MistakeHistoryItem::from).toList();
  }

  /**
   * Returns the durable, Tutor-approved records that back every mistake history projection. Callers
   * may add a presentation-specific filter, but must calculate repeated-error counts from this
   * complete history.
   */
  @Transactional(readOnly = true)
  public List<MistakeRecord> recordsFor(long studentId) {
    if (studentId <= 0) throw new IllegalArgumentException("Student id must be positive.");
    return mistakes.findByStudentIdOrderByCreatedAtDescIdDesc(studentId);
  }

  public record MistakeHistoryItem(
      long id,
      long worksheetId,
      long worksheetQuestionId,
      long questionBankId,
      Long syllabusTopicId,
      String syllabusTopicCode,
      String mistakeType,
      String mistakeLabel,
      String description,
      LocalDateTime recordedAt) {
    static MistakeHistoryItem from(MistakeRecord record) {
      return new MistakeHistoryItem(
          record.getId(),
          record.getWorksheetId(),
          record.getWorksheetQuestionId(),
          record.getQuestionBankId(),
          record.getSyllabusTopicId(),
          record.getSyllabusTopicCode(),
          record.getMistakeType().name(),
          record.getMistakeType().getLabel(),
          record.getDescription(),
          record.getCreatedAt());
    }
  }
}
