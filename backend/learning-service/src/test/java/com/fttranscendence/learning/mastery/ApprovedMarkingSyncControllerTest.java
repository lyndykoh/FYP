package com.fttranscendence.learning.mastery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApprovedMarkingSyncControllerTest {

  @Test
  void acceptsCanonicalTypeAndValidatesTheDerivedCompatibilityCategory() {
    MasteryService.DiagnosticEvidence diagnostic =
        request("INCOMPLETE_WORKING", "APPLICATION").toMasteryInput().diagnostics().get(0);

    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.INCOMPLETE_WORKING,
        diagnostic.canonicalMistakeType());
    assertThrows(
        MasteryService.InvalidResultException.class,
        () -> request("INCOMPLETE_WORKING", "EXPRESSION").toMasteryInput());
  }

  @Test
  void mapsLegacyFourCategoryEventsToDocumentedCanonicalDefaults() {
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.CONCEPT_MISUNDERSTANDING,
        request(null, "CONCEPT").toMasteryInput().diagnostics().get(0).canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.MISSING_KEY_POINT,
        request(null, "KEYWORD").toMasteryInput().diagnostics().get(0).canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.WEAK_EXPLANATION,
        request(null, "EXPRESSION").toMasteryInput().diagnostics().get(0).canonicalMistakeType());
    assertEquals(
        MasteryDiagnosticEvidence.MistakeType.INCOMPLETE_WORKING,
        request(null, "APPLICATION").toMasteryInput().diagnostics().get(0).canonicalMistakeType());
  }

  @Test
  void rejectsMalformedCanonicalTypeAndCategory() {
    assertThrows(
        MasteryService.InvalidResultException.class,
        () -> request("not-a-type", "CONCEPT").toMasteryInput());
    assertThrows(
        MasteryService.InvalidResultException.class,
        () -> request("CONCEPT_MISUNDERSTANDING", "not-a-category").toMasteryInput());
  }

  private ApprovedMarkingSyncController.ApprovedMarkingSyncRequest request(
      String mistakeType, String category) {
    return new ApprovedMarkingSyncController.ApprovedMarkingSyncRequest(
        "approved:1:1",
        "APPROVED",
        1,
        1,
        2,
        3,
        4,
        5,
        6,
        7,
        "TOPIC",
        BigDecimal.ONE,
        new BigDecimal("2.00"),
        LocalDateTime.of(2026, 8, 29, 12, 0),
        List.of(
            new ApprovedMarkingSyncController.DiagnosticSyncEvidence(
                7,
                mistakeType,
                category,
                "Tutor-confirmed diagnostic evidence.",
                List.of("evidence"))));
  }
}
