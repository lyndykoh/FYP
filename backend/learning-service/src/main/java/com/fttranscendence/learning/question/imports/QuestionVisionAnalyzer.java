package com.fttranscendence.learning.question.imports;

import com.fttranscendence.learning.question.Question;
import java.util.List;

/**
 * Server-side boundary for the vision provider. Its output is advisory only: callers must create
 * review candidates, never canonical questions.
 */
public interface QuestionVisionAnalyzer {
  PageAnalysis analyze(PageImage page);

  record PageImage(byte[] bytes, String contentType, int width, int height) {
    public PageImage {
      bytes = bytes.clone();
    }
  }

  record PageAnalysis(List<Candidate> candidates, String failureMessage) {
    public PageAnalysis {
      candidates = List.copyOf(candidates);
    }

    public boolean failed() {
      return failureMessage != null;
    }
  }

  record Candidate(
      int number,
      String prompt,
      String modelAnswer,
      int confidence,
      Question.QuestionType questionType,
      Question.Difficulty difficulty,
      List<String> tags,
      BoundingBox boundingBox,
      List<Diagram> diagrams,
      String warningMessage) {
    public Candidate {
      tags = List.copyOf(tags);
      diagrams = List.copyOf(diagrams);
    }
  }

  record BoundingBox(int x, int y, int width, int height) {}

  /** A provider-suggested diagram region, anchored to its containing question. */
  record Diagram(String regionId, String subQuestionId, BoundingBox boundingBox) {}
}
