package com.fttranscendence.grading.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fttranscendence.grading.service.AiOcrService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OcrObservabilityServiceTest {

  @Test
  void recordsOneAggregateCounterInTheBaselineCohortWhenEventsAreDisabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OcrObservabilityService service = new OcrObservabilityService(meterRegistry);

    service.record("a-safe-correlation-id", answerResult(.84));

    assertEquals(1, counter(meterRegistry, "answers", "nonzero", "baseline").count());
    assertEquals(
        1, meterRegistry.find(OcrObservabilityService.EXTRACTION_METRIC).counters().size());
  }

  @Test
  void recordsOneAggregateCounterInTheEnabledCohortWhenEventsAreEnabled() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OcrObservabilityService service = new OcrObservabilityService(meterRegistry);
    ReflectionTestUtils.setField(service, "correlatedEventsEnabled", true);

    service.record(
        "a-safe-correlation-id",
        new AiOcrService.OcrExtractionResult(
            new AiOcrService.OcrResult("", 0, true), AiOcrService.OcrOutcome.PROVIDER_UNAVAILABLE));

    assertEquals(1, counter(meterRegistry, "provider_unavailable", "zero", "enabled").count());
    assertEquals(
        1, meterRegistry.find(OcrObservabilityService.EXTRACTION_METRIC).counters().size());
  }

  private AiOcrService.OcrExtractionResult answerResult(double confidence) {
    return new AiOcrService.OcrExtractionResult(
        new AiOcrService.OcrResult("student answer", confidence, false),
        AiOcrService.OcrOutcome.ANSWERS);
  }

  private Counter counter(
      SimpleMeterRegistry meterRegistry, String outcome, String confidence, String cohort) {
    Counter counter =
        meterRegistry
            .find(OcrObservabilityService.EXTRACTION_METRIC)
            .tags(
                "outcome", outcome,
                "confidence", confidence,
                "cohort", cohort)
            .counter();
    assertNotNull(counter);
    return counter;
  }
}
