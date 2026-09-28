package com.fttranscendence.learning.insight;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClassInsightRefreshWorker {
  private final ClassInsightService insights;

  public ClassInsightRefreshWorker(ClassInsightService insights) {
    this.insights = insights;
  }

  @Scheduled(fixedDelayString = "${learning.insights.refresh-delay-ms:30000}")
  public void scheduledRefresh() {
    insights.runOnce();
  }

  public int runOnce() {
    return insights.runOnce();
  }
}
