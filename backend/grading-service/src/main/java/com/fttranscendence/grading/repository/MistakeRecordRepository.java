package com.fttranscendence.grading.repository;

import com.fttranscendence.grading.model.MistakeRecord;
import com.fttranscendence.grading.model.MistakeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MistakeRecordRepository extends JpaRepository<MistakeRecord, Long> {

  List<MistakeRecord> findByStudentIdOrderByCreatedAtDescIdDesc(Long studentId);

  List<MistakeRecord> findByStudentIdAndMistakeTypeOrderByCreatedAtDescIdDesc(
      Long studentId, MistakeType mistakeType);

  List<MistakeRecord> findByStudentIdAndSyllabusTopicIdOrderByCreatedAtDescIdDesc(
      Long studentId, Long syllabusTopicId);

  List<MistakeRecord> findByStudentIdAndSyllabusTopicCodeOrderByCreatedAtDescIdDesc(
      Long studentId, String syllabusTopicCode);

  List<MistakeRecord> findBySubmissionIdOrderByCreatedAtAscIdAsc(Long submissionId);

  long countByStudentIdAndMistakeType(Long studentId, MistakeType mistakeType);
}
