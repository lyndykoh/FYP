package com.fttranscendence.learning.insight;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface ClassInsightSnapshotRepository extends Repository<ClassInsightSnapshot, Long> {
  List<ClassInsightSnapshot> findTop1ByTutorIdAndClassIdOrderByIdDesc(Long tutorId, Long classId);
}
