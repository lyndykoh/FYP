package com.fttranscendence.grading.ocr;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fttranscendence.grading.model.SubmissionPage;
import com.fttranscendence.grading.repository.OcrExtractionRepository;
import com.fttranscendence.grading.service.AiOcrService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcrReviewServiceTest {

  @Mock private OcrExtractionRepository extractionRepository;
  @Mock private AiOcrService ocrService;
  @Mock private SubmissionPage page;

  @Test
  void recordsExactlyOneFinalCounterForOneReviewExtraction() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    OcrReviewService service =
        new OcrReviewService(
            extractionRepository, ocrService, new OcrObservabilityService(meterRegistry));
    byte[] upload = new byte[] {1, 2, 3};
    AiOcrService.OcrExtractionResult ocrResult =
        new AiOcrService.OcrExtractionResult(
            new AiOcrService.OcrResult("student answer", .91, false),
            AiOcrService.OcrOutcome.ANSWERS);
    when(page.getMediaType()).thenReturn("image/jpeg");
    when(ocrService.extractWithOutcome(eq(upload), eq("image/jpeg"), any(String.class)))
        .thenReturn(ocrResult);
    when(extractionRepository.save(any(OcrExtraction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.extract(page, 1L, upload);

    ArgumentCaptor<String> correlationCaptor = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(ocrService)
        .extractWithOutcome(eq(upload), eq("image/jpeg"), correlationCaptor.capture());
    assertDoesNotThrow(() -> UUID.fromString(correlationCaptor.getValue()));

    Counter counter =
        meterRegistry
            .find(OcrObservabilityService.EXTRACTION_METRIC)
            .tags(
                "outcome", "answers",
                "confidence", "nonzero",
                "cohort", "baseline")
            .counter();
    assertNotNull(counter);
    assertEquals(1, counter.count());
    assertEquals(
        1, meterRegistry.find(OcrObservabilityService.EXTRACTION_METRIC).counters().size());
  }
}
