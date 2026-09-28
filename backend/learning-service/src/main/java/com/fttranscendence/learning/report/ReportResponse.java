package com.fttranscendence.learning.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * Read-only representation of a persisted progress-report snapshot.
 *
 * <p>The snapshot is deliberately returned as stored. Consumers must not combine it with current
 * mastery or insight data: a final report is a record of the evidence available for its reporting
 * period.
 */
public record ReportResponse(
    Long id,
    Long studentId,
    String studentName,
    String reportCode,
    ProgressReport.ReportStatus status,
    LocalDate periodStart,
    LocalDate periodEnd,
    JsonNode snapshot,
    LocalDateTime generatedAt,
    LocalDateTime finalizedAt) {}
