import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigation = vi.hoisted(() => ({ submissionId: "91" }));
const sourceClient = vi.hoisted(() => ({
  fetchSubmittedWorksheetSource: vi.fn(),
  fetchSubmittedWorksheetSourcePageUrl: vi.fn(),
}));
const reviews = vi.hoisted(() => ({ fetchMarkingReview: vi.fn() }));

vi.mock("next/navigation", () => ({
  useParams: () => navigation,
}));
vi.mock("@/services/marking-reviews", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/services/marking-reviews")>();
  return { ...actual, ...sourceClient };
});
vi.mock("@/services/submissions", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/submissions")>();
  return { ...actual, fetchMarkingReview: reviews.fetchMarkingReview };
});

import SubmittedWorksheetSourcePage from "./page";

const source = {
  submissionId: 91,
  submissionDocumentId: 34,
  studentId: 7,
  worksheetId: 8,
  sourceType: "IMAGES" as const,
  status: "SUBMITTED_FOR_REVIEW" as const,
  pages: [
    {
      id: 15,
      pageNumber: 1,
      originalFilename: "student-work.png",
      mediaType: "image/png" as const,
      byteSize: 3000,
    },
  ],
};

const review = {
  id: 91,
  studentId: 7,
  worksheetId: 8,
  worksheetQuestionId: 22,
  questionBankId: 5,
  extractedAnswer: "Condensation forms droplets.",
  modelAnswer: "Water vapour cools into liquid water.",
  maxMarks: 2,
  aiSuggestedMarks: 2,
  aiSuggestedOutcome: "CORRECT",
  aiErrorCategory: null,
  missingKeywords: [],
  aiSuggestedFeedback: "Correct.",
  reviewStatus: "PENDING_REVIEW" as const,
  approvedMarks: null,
  approvedFeedback: null,
  reviewedByUserId: null,
  reviewedAt: null,
  providerResponseValid: true,
  diagnosticEvidence: [],
  history: [],
};

describe("SubmittedWorksheetSourcePage", () => {
  beforeEach(() => {
    navigation.submissionId = "91";
    sourceClient.fetchSubmittedWorksheetSource.mockReset();
    sourceClient.fetchSubmittedWorksheetSourcePageUrl.mockReset();
    reviews.fetchMarkingReview.mockReset();
    sourceClient.fetchSubmittedWorksheetSource.mockResolvedValue(source);
    sourceClient.fetchSubmittedWorksheetSourcePageUrl.mockResolvedValue(
      "blob:student-work",
    );
    reviews.fetchMarkingReview.mockResolvedValue(review);
  });

  it("shows the original submitted page before linking to the existing review", async () => {
    render(<SubmittedWorksheetSourcePage />);

    expect(
      await screen.findByRole("heading", { name: "Submitted worksheet" }),
    ).toBeVisible();
    expect(screen.getByText("Condensation forms droplets.")).toBeVisible();
    expect(
      await screen.findByRole("img", { name: "Submitted worksheet page 1" }),
    ).toHaveAttribute("src", "blob:student-work");
    expect(screen.getByRole("link", { name: "Continue to Review" }))
      .toHaveAttribute("href", "/tutor/reviews/91");
  });

  it("does not request protected source data for an invalid submission id", () => {
    navigation.submissionId = "invalid";
    render(<SubmittedWorksheetSourcePage />);

    expect(screen.getByRole("alert")).toHaveTextContent("submission id is invalid");
    expect(sourceClient.fetchSubmittedWorksheetSource).not.toHaveBeenCalled();
    expect(reviews.fetchMarkingReview).not.toHaveBeenCalled();
  });
});
