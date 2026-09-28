package com.fttranscendence.learning.question.imports;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fttranscendence.learning.LearningServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = LearningServiceApplication.class,
    properties = {
      "ai.vision.enabled=true",
      "ai.vision.url=https://vision.example.test/v1/chat/completions",
      "ai.vision.model=gpt-4.1-mini",
      "ai.vision.api-key=server-only-key"
    })
class OpenAiQuestionVisionAnalyzerContextTest {
  @Autowired private OpenAiQuestionVisionAnalyzer analyzer;

  @Test
  void createsTheVisionAnalyzerWhenVisionOcrIsEnabled() {
    assertNotNull(analyzer);
  }
}
