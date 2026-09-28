import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import MarkingReview from "./MarkingReview";

const pending = {
  id: 1,
  studentId: 2,
  worksheetId: 3,
  worksheetQuestionId: 4,
  questionBankId: 5,
  extractedAnswer: "Metal gets hot.",
  modelAnswer: "Metal conducts heat.",
  maxMarks: 2,
  aiSuggestedMarks: 1,
  aiSuggestedOutcome: "Partially correct",
  aiErrorCategory: null,
  missingKeywords: ["conductor"],
  aiSuggestedFeedback: "Explain heat transfer.",
  reviewStatus: "PENDING_REVIEW" as const,
  approvedMarks: null,
  approvedFeedback: null,
  reviewedByUserId: null,
  reviewedAt: null,
  providerResponseValid: true,
  diagnosticEvidence: [],
  history: [],
};

describe("MarkingReview", () => {
  it("allows a Tutor to edit and approve an advisory result", async () => {
    const approve = vi
      .fn()
      .mockResolvedValue({
        ...pending,
        reviewStatus: "APPROVED",
        approvedMarks: 2,
        approvedFeedback: "Clear explanation.",
        history: [
          {
            id: 1,
            action: "APPROVED",
            reviewerUserId: 10,
            previousStatus: "PENDING_REVIEW",
            newStatus: "APPROVED",
            previousMarks: 1,
            newMarks: 2,
            previousFeedback: "Explain heat transfer.",
            newFeedback: "Clear explanation.",
            createdAt: "2026-08-27T10:00:00",
          },
        ],
      });
    const user = userEvent.setup();
    render(<MarkingReview review={pending} approve={approve} />);
    await user.clear(screen.getByLabelText("Final marks"));
    await user.type(screen.getByLabelText("Final marks"), "2");
    await user.clear(screen.getByLabelText("Tutor feedback"));
    await user.type(
      screen.getByLabelText("Tutor feedback"),
      "Clear explanation.",
    );
    await user.click(
      screen.getByRole("button", { name: "Approve final result" }),
    );
    expect(approve).toHaveBeenCalledWith(1, 2, "Clear explanation.", []);
    expect((await screen.findAllByText(/APPROVED/)).length).toBeGreaterThan(0);
  });

  it("requires and sends Tutor-confirmed diagnostic evidence only when selected", async () => {
    const approve = vi
      .fn()
      .mockResolvedValue({
        ...pending,
        reviewStatus: "APPROVED",
        diagnosticEvidence: [
          {
            mistakeType: "CONCEPT_MISUNDERSTANDING",
            category: "CONCEPT",
            description: "Heat transfer concept is incomplete.",
            missingKeywords: ["conduction"],
          },
        ],
      });
    const user = userEvent.setup();
    render(<MarkingReview review={pending} approve={approve} />);
    await user.click(
      screen.getByRole("checkbox", {
        name: "Include Tutor-confirmed diagnostic evidence",
      }),
    );
    await user.click(screen.getByLabelText("Mistake type"));
    await user.click(
      await screen.findByRole("option", { name: "Wrong units" }),
    );
    await user.type(
      await screen.findByLabelText(/Tutor diagnostic rationale/),
      "Heat transfer concept is incomplete.",
    );
    await user.type(
      screen.getByLabelText("Supporting missing words or phrases"),
      "conduction",
    );
    await user.click(
      screen.getByRole("button", { name: "Approve final result" }),
    );
    expect(approve).toHaveBeenCalledWith(1, 1, "Explain heat transfer.", [
      {
        mistakeType: "WRONG_UNITS",
        description: "Heat transfer concept is incomplete.",
        missingKeywords: ["conduction"],
      },
    ]);
  });

  it("supports flagging, resetting, and prevents invalid local approval", async () => {
    const flag = vi
      .fn()
      .mockResolvedValue({
        ...pending,
        reviewStatus: "FLAGGED",
        history: [
          {
            id: 1,
            action: "FLAGGED",
            reviewerUserId: 10,
            previousStatus: "PENDING_REVIEW",
            newStatus: "FLAGGED",
            previousMarks: 1,
            newMarks: null,
            previousFeedback: "",
            newFeedback: "Needs review",
            createdAt: "2026-08-27T10:00:00",
          },
        ],
      });
    const reset = vi.fn().mockResolvedValue(pending);
    const approve = vi.fn();
    const user = userEvent.setup();
    render(
      <MarkingReview
        review={pending}
        flag={flag}
        reset={reset}
        approve={approve}
      />,
    );
    await user.type(screen.getByLabelText("Flag reason"), "Needs review");
    await user.click(screen.getByRole("button", { name: "Flag for later" }));
    expect(flag).toHaveBeenCalledWith(1, "Needs review");
    await screen.findByText("FLAGGED");
    await user.click(screen.getByRole("button", { name: "Reset to AI" }));
    expect(reset).toHaveBeenCalledWith(1);
    await screen.findByText("PENDING REVIEW");
    await user.clear(screen.getByLabelText("Final marks"));
    await user.type(screen.getByLabelText("Final marks"), "4");
    await user.click(
      screen.getByRole("button", { name: "Approve final result" }),
    );
    expect(approve).not.toHaveBeenCalled();
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter marks");
  });
});
