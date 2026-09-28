import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const markingReviews = vi.hoisted(() => ({
  fetchPendingMarkingReviews: vi.fn(),
}));

vi.mock("@/services/marking-reviews", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/services/marking-reviews")>();
  return {
    ...actual,
    fetchPendingMarkingReviews: markingReviews.fetchPendingMarkingReviews,
  };
});

import TutorReviewsPage from "./page";

describe("TutorReviewsPage", () => {
  beforeEach(() => {
    markingReviews.fetchPendingMarkingReviews.mockReset();
    markingReviews.fetchPendingMarkingReviews.mockResolvedValue([
      {
        submissionId: 91,
        submissionDocumentId: 34,
        studentId: 2,
        studentName: "Tara Tan",
        worksheetId: 14,
        worksheetQuestionId: 71,
        requestedAt: "2026-08-31T10:00:00",
        sourceAvailable: true,
      },
    ]);
  });

  it("shows submitted worksheet reviews and opens the matching review", async () => {
    render(<TutorReviewsPage />);

    expect(
      await screen.findByRole("heading", { name: "Pending reviews" }),
    ).toBeVisible();
    expect(screen.getByText("Worksheet #14")).toBeVisible();
    expect(screen.getByText("Tara Tan")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "View submitted worksheet" }),
    ).toHaveAttribute(
      "href",
      "/tutor/reviews/91/source",
    );
    expect(screen.getByRole("link", { name: "Review answers" })).toHaveAttribute(
      "href",
      "/tutor/reviews/91",
    );
  });

  it("explains when there are no submitted worksheets to review", async () => {
    markingReviews.fetchPendingMarkingReviews.mockResolvedValueOnce([]);
    render(<TutorReviewsPage />);

    expect(
      await screen.findByRole("heading", { name: "No pending reviews" }),
    ).toBeVisible();
  });

  it("refreshes the canonical queue when the Tutor returns to the page", async () => {
    render(<TutorReviewsPage />);
    await screen.findByText("Tara Tan");

    markingReviews.fetchPendingMarkingReviews.mockResolvedValueOnce([]);
    fireEvent.focus(window);

    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "No pending reviews" }),
      ).toBeVisible(),
    );
    expect(markingReviews.fetchPendingMarkingReviews).toHaveBeenCalledTimes(2);
  });
});
