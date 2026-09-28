package com.fttranscendence.learning.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Owner- and recipient-scoped reads of immutable progress-report snapshots. */
@Service
public class ReportService {
  private final ProgressReportRepository reports;
  private final ObjectMapper objectMapper;

  public ReportService(ProgressReportRepository reports, ObjectMapper objectMapper) {
    this.reports = reports;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public ReportResponse tutorReport(long tutorId, long reportId) {
    return responseFor(
        reports.findByIdAndTutorId(reportId, tutorId).orElseThrow(ReportNotFoundException::new));
  }

  @Transactional(readOnly = true)
  public ReportResponse studentReport(long loginUserId, long reportId) {
    return responseFor(
        reports
            .findByIdAndStudentProfile_LoginUserIdAndReportStatus(
                reportId, loginUserId, ProgressReport.ReportStatus.FINAL)
            .orElseThrow(ReportNotFoundException::new));
  }

  private ReportResponse responseFor(ProgressReport report) {
    return new ReportResponse(
        report.getId(),
        report.getStudentProfile().getId(),
        report.getStudentProfile().getFullName(),
        report.getReportCode(),
        report.getReportStatus(),
        report.getPeriodStart(),
        report.getPeriodEnd(),
        snapshotObject(report.getSnapshot()),
        report.getGeneratedAt(),
        report.getFinalizedAt());
  }

  private JsonNode snapshotObject(String value) {
    try {
      JsonNode parsed = objectMapper.readTree(value);
      if (parsed == null || !parsed.isObject()) {
        throw new InvalidReportSnapshotException();
      }
      return parsed;
    } catch (JacksonException exception) {
      throw new InvalidReportSnapshotException();
    }
  }

  public static class ReportNotFoundException extends RuntimeException {}

  public static class InvalidReportSnapshotException extends RuntimeException {}
}
