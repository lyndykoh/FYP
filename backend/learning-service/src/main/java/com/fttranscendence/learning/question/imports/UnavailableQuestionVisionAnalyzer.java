package com.fttranscendence.learning.question.imports;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Provides an explicit manual-review fallback when vision is not configured. */
@Service
@ConditionalOnProperty(name = "ai.vision.enabled", havingValue = "false", matchIfMissing = true)
class UnavailableQuestionVisionAnalyzer implements QuestionVisionAnalyzer {
  private static final String MESSAGE =
      "Automatic vision OCR is unavailable. Transcribe and review this source image before importing.";

  @Override
  public PageAnalysis analyze(PageImage page) {
    return new PageAnalysis(List.of(), MESSAGE);
  }
}
