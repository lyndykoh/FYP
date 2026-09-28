package com.fttranscendence.learning.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ProgressReportRepository extends Repository<ProgressReport, Long> {

  <S extends ProgressReport> S save(S report);

  Optional<ProgressReport> findByIdAndTutorId(Long id, Long tutorId);

  /**
   * A student may only read a report that belongs to their linked login and that has been
   * explicitly finalised by the owning tutor. Keeping this predicate in the query avoids loading a
   * report before its access scope has been established.
   */
  Optional<ProgressReport> findByIdAndStudentProfile_LoginUserIdAndReportStatus(
      Long id, Long loginUserId, ProgressReport.ReportStatus reportStatus);

  List<ProgressReport> findAllByStudentProfileIdOrderByPeriodEndDesc(Long studentProfileId);

  long countByTutorIdAndReportStatus(Long tutorId, ProgressReport.ReportStatus reportStatus);
}
