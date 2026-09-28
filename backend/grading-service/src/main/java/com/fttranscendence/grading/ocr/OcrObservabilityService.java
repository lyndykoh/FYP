package com.fttranscendence.grading.ocr;

import com.fttranscendence.grading.service.AiOcrService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Records a single, bounded metric for each completed OCR extraction.
 *
 * <p>The metric never contains uploaded content, file metadata, provider responses, or correlation
 * IDs. Turning on the flag adds a content-free correlation event to aid a controlled rollout.
 */
@Service
public class OcrObservabilityService {

  static final String EXTRACTION_METRIC = "grading.ocr.extractions";

  private static final Logger logger = LoggerFactory.getLogger(OcrObservabilityService.class);

  private final MeterRegistry meterRegistry;

  @Value("${ai.ocr.observability.enabled:false}")
  private boolean correlatedEventsEnabled;

  public OcrObservabilityService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String correlationId, AiOcrService.OcrExtractionResult extractionResult) {
    String outcome = extractionResult.outcome().name().toLowerCase(Locale.ROOT);
    String confidence = extractionResult.result().confidence() == 0 ? "zero" : "nonzero";
    String cohort = correlatedEventsEnabled ? "enabled" : "baseline";

    Counter.builder(EXTRACTION_METRIC)
        .tag("outcome", outcome)
        .tag("confidence", confidence)
        .tag("cohort", cohort)
        .register(meterRegistry)
        .increment();

    if (correlatedEventsEnabled) {
      logger.info(
          "ocr_extraction_outcome correlation_id={} outcome={} confidence_zero={}",
          correlationId,
          outcome,
          "zero".equals(confidence));
    }
  }
}
