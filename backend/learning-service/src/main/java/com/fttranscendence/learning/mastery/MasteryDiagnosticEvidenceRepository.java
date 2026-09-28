package com.fttranscendence.learning.mastery;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasteryDiagnosticEvidenceRepository
    extends JpaRepository<MasteryDiagnosticEvidence, Long> {
  boolean existsBySourceSubmissionId(Long sourceSubmissionId);

  void deleteBySourceSubmissionId(Long sourceSubmissionId);

  @EntityGraph(attributePaths = {"masteryRecord", "studentProfile", "missingKeywords"})
  List<MasteryDiagnosticEvidence> findByStudentProfileIdOrderByCreatedAtDescIdDesc(
      Long studentProfileId);

  @EntityGraph(attributePaths = {"masteryRecord"})
  List<MasteryDiagnosticEvidence> findByMasteryRecordId(Long masteryRecordId);
}
