import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ManualResultForm from "./ManualResultForm";
import type { MarkingReview } from "@/services/submissions";
import type { TutorWorksheet } from "@/services/worksheets";

const worksheet: TutorWorksheet = {
  id: 3,
  code: "SCI-1",
  title: "Heat transfer",
  instructions: null,
  targetMode: "CLASS",
  status: "APPROVED",
  generationRequestId: null,
  dueAt: null,
  questions: [
    {
      id: 5,
      code: "Q-1",
      prompt: "Why does metal feel hot?",
      totalMarks: 2,
      questionType: "SHORT_ANSWER",
      topicName: "Heat",
    },
    {
      id: 6,
      code: "Q-2",
      prompt: "Name a conductor.",
      totalMarks: 1,
      questionType: "SHORT_ANSWER",
      topicName: "Heat",
    },
  ],
  assignments: [
    {
      id: 1,
      assignmentType: "CLASS",
      classId: 7,
      studentProfileId: null,
      assignedAt: null,
      dueAt: null,
    },
  ],
};
const review = (id: number, questionBankId: number): MarkingReview => ({
  id,
  studentId: 2,
  worksheetId: 3,
  worksheetQuestionId: questionBankId,
  questionBankId,
  extractedAnswer: "Answer",
  modelAnswer: "Model",
  maxMarks: questionBankId === 5 ? 2 : 1,
  aiSuggestedMarks: null,
  aiSuggestedOutcome: null,
  aiErrorCategory: null,
  missingKeywords: [],
  aiSuggestedFeedback: null,
  reviewStatus: "APPROVED",
  approvedMarks: 1,
  approvedFeedback: "Tutor feedback",
  reviewedByUserId: 1,
  reviewedAt: "2026-08-27T10:00:00",
  providerResponseValid: null,
  diagnosticEvidence: [],
  history: [],
});

async function chooseStudent(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("combobox", { name: "Student" }));
  await user.click(screen.getByRole("option", { name: "Ada Learner" }));
}
async function fillAll(user: ReturnType<typeof userEvent.setup>) {
  await user.type(
    screen.getByLabelText("Student answer or observation for Q-1"),
    "Metal conducts heat.",
  );
  await user.type(screen.getByLabelText("Marks for Q-1 (out of 2)"), "1.5");
  await user.type(
    screen.getByLabelText("Tutor feedback for Q-1"),
    "Add mechanism.",
  );
  await user.type(
    screen.getByLabelText("Student answer or observation for Q-2"),
    "Copper.",
  );
  await user.type(screen.getByLabelText("Marks for Q-2 (out of 1)"), "1");
  await user.type(screen.getByLabelText("Tutor feedback for Q-2"), "Correct.");
}

describe("ManualResultForm", () => {
  it("submits all unrecorded questions as one approved batch", async () => {
    const user = userEvent.setup();
    const submit = vi.fn().mockResolvedValue([review(10, 5), review(11, 6)]);
    const onCreated = vi.fn();
    render(
      <ManualResultForm
        worksheet={worksheet}
        students={[{ id: 2, fullName: "Ada Learner" }]}
        submit={submit}
        onCreated={onCreated}
      />,
    );
    await chooseStudent(user);
    await fillAll(user);
    fireEvent.submit(
      screen
        .getByRole("button", { name: "Save 2 approved results" })
        .closest("form")!,
    );
    expect(submit).toHaveBeenCalledWith({
      worksheetId: 3,
      studentId: 2,
      entries: [
        {
          questionBankId: 5,
          answer: "Metal conducts heat.",
          marks: 1.5,
          feedback: "Add mechanism.",
        },
        {
          questionBankId: 6,
          answer: "Copper.",
          marks: 1,
          feedback: "Correct.",
        },
      ],
    });
    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith([review(10, 5), review(11, 6)]),
    );
  });

  it("blocks incomplete and out-of-range question rows before submission", async () => {
    const user = userEvent.setup();
    const submit = vi.fn();
    render(
      <ManualResultForm
        worksheet={worksheet}
        students={[{ id: 2, fullName: "Ada Learner" }]}
        submit={submit}
      />,
    );
    await chooseStudent(user);
    fireEvent.submit(
      screen
        .getByRole("button", { name: "Save 2 approved results" })
        .closest("form")!,
    );
    expect(await screen.findByText(/Q-1 needs an answer/)).toBeInTheDocument();
    await fillAll(user);
    await user.clear(screen.getByLabelText("Marks for Q-1 (out of 2)"));
    await user.type(screen.getByLabelText("Marks for Q-1 (out of 2)"), "3");
    await user.click(
      screen.getByRole("button", { name: "Save 2 approved results" }),
    );
    expect(await screen.findByText(/Q-1 needs an answer/)).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });

  it("shows prior approved entries with an edit affordance instead of resubmitting them", async () => {
    const user = userEvent.setup();
    render(
      <ManualResultForm
        worksheet={worksheet}
        students={[{ id: 2, fullName: "Ada Learner" }]}
        existingResults={[review(10, 5)]}
      />,
    );
    await chooseStudent(user);
    expect(
      screen.getByText("1 of 2 questions already approved for this student."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Review or edit result" }),
    ).toHaveAttribute("href", "/tutor/reviews/10");
    expect(
      screen.getByRole("button", { name: "Save 1 approved result" }),
    ).toBeEnabled();
  });

  it("explains when the worksheet cannot accept a manual result", () => {
    render(
      <ManualResultForm
        worksheet={{ ...worksheet, status: "DRAFT" }}
        students={[{ id: 2, fullName: "Ada Learner" }]}
      />,
    );
    expect(
      screen.getByText(
        "Approve and assign this worksheet before entering results.",
      ),
    ).toBeInTheDocument();
  });
});
