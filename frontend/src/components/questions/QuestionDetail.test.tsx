import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { TutorQuestion } from "@/services/questions";

import QuestionDetail from "./QuestionDetail";

const question: TutorQuestion = {
  id: 7,
  code: "SCI-WATER-001",
  syllabusTopic: {
    id: 14,
    code: "SCI_P5_WATER",
    name: "Water",
    nodeType: "SUBTOPIC",
  },
  questionType: "OPEN_ENDED",
  prompt: "Explain why evaporation happens faster on a hot day.",
  totalMarks: 3,
  modelAnswer: "Water gains thermal energy and escapes as water vapour.",
  archiveState: "ACTIVE",
  markingComponents: [
    {
      position: 0,
      description: "Identifies thermal energy gain",
      marks: 1,
      keywords: ["thermal energy"],
    },
    {
      position: 1,
      description: "Explains faster particle escape",
      marks: 2,
      keywords: ["particle escape"],
    },
  ],
  keywords: ["evaporation", "thermal energy"],
  createdAt: "2026-08-27T08:00:00",
  updatedAt: "2026-08-27T08:00:00",
};

describe("QuestionDetail", () => {
  it("shows a loading skeleton before rendering the full syllabus and marking detail", async () => {
    let resolve!: (value: TutorQuestion) => void;
    render(
      <QuestionDetail
        questionId={7}
        loadQuestion={() =>
          new Promise<TutorQuestion>((complete) => {
            resolve = complete;
          })
        }
      />,
    );
    expect(screen.getByTestId("question-detail-skeleton")).toBeVisible();
    await waitFor(() => expect(resolve).toBeTypeOf("function"));
    await act(async () => {
      resolve(question);
    });
    expect(
      await screen.findByRole("heading", { name: "SCI-WATER-001" }),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Syllabus link" }),
    ).toBeVisible();
    expect(screen.getByText("SCI_P5_WATER · subtopic")).toBeVisible();
    expect(screen.getByText("Identifies thermal energy gain")).toBeVisible();
    expect(screen.getByText("Explains faster particle escape")).toBeVisible();
    expect(
      screen.getByText(
        "Water gains thermal energy and escapes as water vapour.",
      ),
    ).toBeVisible();
    expect(screen.getByText("thermal energy")).toBeVisible();
  });

  it("provides accessible edit and local worksheet-draft selection actions", async () => {
    const user = userEvent.setup();
    const addToWorksheetDraft = vi
      .fn()
      .mockReturnValue({ ids: [7], added: true });
    render(
      <QuestionDetail
        questionId={7}
        loadQuestion={async () => question}
        addToWorksheetDraft={addToWorksheetDraft}
        isInWorksheetDraft={() => false}
      />,
    );
    expect(
      await screen.findByRole("link", { name: "Edit question" }),
    ).toHaveAttribute("href", "/questions/7/edit");
    expect(
      screen.getByRole("link", { name: /Question bank/i }),
    ).toHaveAttribute("href", "/questions");
    await user.click(
      screen.getByRole("button", { name: "Add to worksheet draft" }),
    );
    expect(addToWorksheetDraft).toHaveBeenCalledWith(7);
    expect(
      screen.getByRole("button", { name: "Added to worksheet draft" }),
    ).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Added to the local worksheet draft selection",
    );
  });

  it("shows archived questions but prevents them from entering a worksheet draft", async () => {
    render(
      <QuestionDetail
        questionId={7}
        loadQuestion={async () => ({ ...question, archiveState: "ARCHIVED" })}
      />,
    );
    expect(await screen.findByText("ARCHIVED")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Add to worksheet draft" }),
    ).toBeDisabled();
    expect(
      screen.getByText(
        "Archived questions cannot be added to a worksheet draft.",
      ),
    ).toBeVisible();
  });

  it("shows a retryable missing or loading error", async () => {
    const user = userEvent.setup();
    const loadQuestion = vi
      .fn()
      .mockRejectedValueOnce(new Error("Question was not found"))
      .mockResolvedValueOnce(question);
    render(<QuestionDetail questionId={7} loadQuestion={loadQuestion} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Question was not found",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading question" }),
    );
    expect(
      await screen.findByText(
        "Explain why evaporation happens faster on a hot day.",
      ),
    ).toBeVisible();
  });

  it("does not load an invalid question reference", () => {
    const loadQuestion = vi.fn();
    render(<QuestionDetail questionId={0} loadQuestion={loadQuestion} />);
    expect(screen.getByRole("alert")).toHaveTextContent("reference is invalid");
    expect(
      screen.queryByRole("button", { name: "Retry loading question" }),
    ).not.toBeInTheDocument();
    expect(loadQuestion).not.toHaveBeenCalled();
  });

  it("reports an unavailable browser draft store without marking the question selected", async () => {
    const user = userEvent.setup();
    render(
      <QuestionDetail
        questionId={7}
        loadQuestion={async () => question}
        addToWorksheetDraft={() => ({
          ids: [7],
          added: false,
          storageUnavailable: true,
        })}
        isInWorksheetDraft={() => false}
      />,
    );
    await user.click(
      await screen.findByRole("button", { name: "Add to worksheet draft" }),
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "could not save this worksheet selection",
    );
    expect(
      screen.getByRole("button", { name: "Add to worksheet draft" }),
    ).toBeEnabled();
  });

  it("runs a non-persistent tutor answer-check preview", async () => {
    const user = userEvent.setup();
    const checkAnswer = vi.fn().mockResolvedValue({
      awardedMarks: 3,
      maximumMarks: 3,
      matchedKeywords: [
        "Identifies thermal energy gain",
        "Explains faster particle escape",
      ],
      missingKeywords: [],
      explanation: "Matched 2 of 2 weighted marking components.",
      componentResults: [
        {
          position: 0,
          description: "Identifies thermal energy gain",
          maximumMarks: 1,
          matched: true,
          matchedTargets: ["thermal energy"],
          missingTargets: [],
          feedback: "Matched an approved component keyword.",
        },
        {
          position: 1,
          description: "Explains faster particle escape",
          maximumMarks: 2,
          matched: true,
          matchedTargets: ["particle escape"],
          missingTargets: [],
          feedback: "Matched an approved component keyword.",
        },
      ],
    });
    render(
      <QuestionDetail
        questionId={7}
        loadQuestion={async () => question}
        checkAnswer={checkAnswer}
      />,
    );
    await user.type(
      await screen.findByLabelText("Sample student answer"),
      "It gains thermal energy and particles escape.",
    );
    await user.click(screen.getByRole("button", { name: "Check answer" }));
    expect(checkAnswer).toHaveBeenCalledWith(
      7,
      "It gains thermal energy and particles escape.",
    );
    expect(await screen.findByText("3.00 / 3.00 marks")).toBeVisible();
    expect(screen.getByText(/This does not save a grade\./)).toBeVisible();
  });
});
