import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { LearningProfile } from "@/services/insights";

import LearningInsightsPanel from "./LearningInsightsPanel";

const profile: LearningProfile = {
  studentId: 31,
  strengths: [],
  growthAreas: [],
  improvements: [],
  dimensions: ["CONCEPT", "KEYWORD", "EXPRESSION", "APPLICATION"].map(
    (category) => ({ category, evidenceCount: 0, evidence: [] }),
  ) as LearningProfile["dimensions"],
  dataAsOf: null,
  source: "DETERMINISTIC",
  findings: [
    {
      type: "KEYWORD_WEAKNESS",
      title: "Keyword weakness in Adaptation",
      summary: "A tutor-approved learning record identified keyword weakness.",
      suggestedAction:
        "Practise using the required keyword in a complete answer.",
      evidence: [
        {
          topicId: 41,
          topicName: "Adaptation",
          score: 48,
          status: "PRACTISING",
          attemptCount: 2,
          sourceReason: "keyword omitted",
          occurredAt: null,
        },
      ],
    },
  ],
};

describe("LearningInsightsPanel", () => {
  it("labels deterministic suggestions and cites their factual evidence", async () => {
    render(<LearningInsightsPanel loadProfile={async () => profile} />);
    expect(
      await screen.findByText("Keyword weakness in Adaptation"),
    ).toBeVisible();
    expect(screen.getByText("Evidence: Adaptation · 48%")).toBeVisible();
    expect(
      screen.getByText(
        "Derived from tutor-approved mastery and history. Suggestions are not saved decisions.",
      ),
    ).toBeVisible();
  });
});
