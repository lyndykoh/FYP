package com.fttranscendence.learning.question.imports;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps a slow provider request outside the import database transaction. */
@Service
class QuestionImportVisionService {
  private final QuestionVisionAnalyzer analyzer;

  QuestionImportVisionService(QuestionVisionAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  QuestionVisionAnalyzer.PageAnalysis analyze(QuestionVisionAnalyzer.PageImage page) {
    return analyzer.analyze(page);
  }
}
