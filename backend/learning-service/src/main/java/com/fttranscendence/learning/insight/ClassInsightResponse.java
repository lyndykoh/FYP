package com.fttranscendence.learning.insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Owner-scoped snapshot. It contains aggregate evidence only, never student names. */
public record ClassInsightResponse(
    Status status,
    String message,
    LocalDateTime dataAsOf,
    List<Item> items,
    List<Feedback> feedback) {
  public enum Status {
    FRESH,
    STALE,
    REFRESHING,
    FAILED
  }

  public record Item(
      Long topicId,
      String topicName,
      BigDecimal averageMasteryPercent,
      int activeStudentCount,
      int assessedStudentCount,
      int affectedStudentCount,
      boolean weak,
      String suggestedAction,
      Integer displayRank,
      String rankingNote) {}

  public record Feedback(Long id, String feedback, LocalDateTime createdAt) {}
}
