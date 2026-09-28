import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as React from "react";

import Page from "./results/page";
import {
  fetchStudentWorksheetResults,
  type StudentWorksheetResultsResponse,
} from "@/services/submissions";

vi.mock("@/services/submissions", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/services/submissions")>();
  return { ...actual, fetchStudentWorksheetResults: vi.fn() };
});

const result = (overrides: Record<string, unknown> = {}) => ({
  submissionId: 101,
  worksheetQuestionId: 201,
  questionBankId: 301,
  answer: "Water evaporates when heated.",
  modelAnswer: "Water changes into vapour when it is heated.",
  maximumMarks: 2,
  reviewStatus: "APPROVED" as const,
  outcome: "CORRECT" as const,
  awardedMarks: 2,
  explanation: "You named both heating and the change into vapour.",
  reviewedAt: "2026-08-29T09:00:00",
  ...overrides,
});

const mixedResults: StudentWorksheetResultsResponse = {
  worksheetId: 12,
  results: [
    result(),
    result({
      submissionId: 102,
      worksheetQuestionId: 202,
      questionBankId: 302,
      outcome: "PARTIAL",
      awardedMarks: 1,
      explanation: "Name the gas as water vapour.",
    }),
    result({
      submissionId: 103,
      worksheetQuestionId: 203,
      questionBankId: 303,
      outcome: "INCORRECT",
      awardedMarks: 0,
      explanation: "Review the difference between melting and evaporation.",
    }),
    result({
      submissionId: 104,
      worksheetQuestionId: 204,
      questionBankId: 304,
      answer: "My working is unclear.",
      modelAnswer: null,
      reviewStatus: "PENDING_REVIEW",
      outcome: "REVIEW_NEEDED",
      awardedMarks: null,
      explanation: null,
      reviewedAt: null,
    }),
  ],
};

function renderPage(worksheetId: string, key?: string) {
  return render(
    <React.Suspense fallback={<div>Loading route</div>}>
      <Page key={key} params={Promise.resolve({ worksheetId })} />
    </React.Suspense>,
  );
}

describe("Student worksheet results page", () => {
  beforeEach(() => vi.mocked(fetchStudentWorksheetResults).mockReset());

  it("renders mixed approved outcomes, hides unapproved final information, and expands an explanation", async () => {
    vi.mocked(fetchStudentWorksheetResults).mockResolvedValue(mixedResults);
    renderPage("12");

    expect(
      await screen.findByRole("heading", { name: "Your submitted answers" }),
    ).toBeVisible();
    expect(screen.getByText("Correct")).toBeVisible();
    expect(screen.getByText("Partially correct")).toBeVisible();
    expect(screen.getByText("Incorrect")).toBeVisible();
    expect(screen.getByText("Awaiting tutor review")).toBeVisible();
    expect(screen.getByText("Final score pending")).toBeVisible();
    expect(screen.getAllByText("Pending")).toHaveLength(1);
    expect(
      screen.queryByText("You named both heating and the change into vapour."),
    ).not.toBeInTheDocument();

    await userEvent
      .setup()
      .click(screen.getAllByRole("button", { name: "Read explanation" })[0]!);
    expect(
      screen.getByText("You named both heating and the change into vapour."),
    ).toBeVisible();
    expect(
      screen.getAllByText("Water changes into vapour when it is heated."),
    ).toHaveLength(3);
  });

  it("shows loading, a retriable API error, and does not fall back for a missing or invalid id", async () => {
    let resolve: ((value: StudentWorksheetResultsResponse) => void) | undefined;
    vi.mocked(fetchStudentWorksheetResults).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { rerender } = renderPage("12");
    expect(await screen.findByTestId("student-results-loading")).toBeVisible();
    await waitFor(() =>
      expect(fetchStudentWorksheetResults).toHaveBeenCalledWith(12),
    );
    resolve?.({ worksheetId: 12, results: [] });
    expect(
      await screen.findByRole("heading", { name: "No submitted answers yet" }),
    ).toBeVisible();

    vi.mocked(fetchStudentWorksheetResults).mockRejectedValueOnce(
      new Error("Worksheet not found."),
    );
    rerender(
      <React.Suspense fallback={<div>Loading route</div>}>
        <Page key="error" params={Promise.resolve({ worksheetId: "99" })} />
      </React.Suspense>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Worksheet not found.",
    );
    expect(
      screen.getByRole("button", { name: "Retry loading results" }),
    ).toBeVisible();

    rerender(
      <React.Suspense fallback={<div>Loading route</div>}>
        <Page key="invalid" params={Promise.resolve({ worksheetId: "ws1" })} />
      </React.Suspense>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "worksheet link is invalid",
    );
    expect(fetchStudentWorksheetResults).toHaveBeenCalledTimes(2);
  });

  it("renders the review status returned after reload instead of keeping local review state", async () => {
    vi.mocked(fetchStudentWorksheetResults)
      .mockResolvedValueOnce({
        ...mixedResults,
        results: [mixedResults.results[3]!],
      })
      .mockResolvedValueOnce({
        ...mixedResults,
        results: [
          result({
            submissionId: 104,
            worksheetQuestionId: 204,
            questionBankId: 304,
            outcome: "PARTIAL",
            awardedMarks: 1,
            explanation: "Tutor approved this after review.",
          }),
        ],
      });
    const { rerender } = renderPage("12");
    expect(await screen.findByText("Awaiting tutor review")).toBeVisible();

    rerender(
      <React.Suspense fallback={<div>Loading route</div>}>
        <Page key="reload" params={Promise.resolve({ worksheetId: "12" })} />
      </React.Suspense>,
    );
    expect(await screen.findByText("Partially correct")).toBeVisible();
    expect(screen.getByLabelText("Final worksheet score")).toBeVisible();
    expect(fetchStudentWorksheetResults).toHaveBeenCalledTimes(2);
  });
});
