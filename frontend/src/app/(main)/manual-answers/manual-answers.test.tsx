import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Page from "./page";
import { getBrowserSession } from "@/lib/auth";
import {
  fetchManualAnswerDraft,
  saveManualAnswers,
} from "@/services/submissions";
import { fetchStudentSelfProfile } from "@/services/students";
import {
  fetchStudentWorksheet,
  fetchTutorWorksheet,
} from "@/services/worksheets";

const navigation = vi.hoisted(() => ({
  params: new URLSearchParams("worksheetId=42"),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => navigation.params,
  useRouter: () => ({ replace: navigation.replace }),
}));
vi.mock("@/lib/auth", () => ({ getBrowserSession: vi.fn() }));
vi.mock("@/services/submissions", () => ({
  SubmissionApiError: class SubmissionApiError extends Error {},
  fetchManualAnswerDraft: vi.fn(),
  saveManualAnswers: vi.fn(),
}));
vi.mock("@/services/students", () => ({ fetchStudentSelfProfile: vi.fn() }));
vi.mock("@/services/worksheets", () => ({
  fetchStudentWorksheet: vi.fn(),
  fetchTutorWorksheet: vi.fn(),
}));

const studentWorksheet = {
  id: 42,
  code: "WS-42",
  title: "Water cycle",
  instructions: "Answer every question.",
  subject: "Science",
  assignedAt: "2026-08-30T09:00:00",
  dueAt: null,
  questions: [
    {
      id: 4,
      code: "Q-4",
      prompt: "Which source is needed? A. Sunlight B. Soil C. Salt D. Smoke",
      totalMarks: 1,
      questionType: "MULTIPLE_CHOICE" as const,
      topicName: "Water",
    },
    {
      id: 5,
      code: "Q-5",
      prompt: "Explain evaporation.",
      totalMarks: 2,
      questionType: "OPEN_ENDED" as const,
      topicName: "Water",
    },
  ],
};

describe("Manual answer entry", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    navigation.params = new URLSearchParams("worksheetId=42");
    navigation.replace.mockReset();
    vi.mocked(getBrowserSession).mockReturnValue({
      token: "token",
      email: "student@example.test",
      role: "STUDENT",
      expiresAt: Date.now() + 60_000,
    });
    vi.mocked(fetchStudentSelfProfile).mockResolvedValue({ id: 7 } as never);
    vi.mocked(fetchStudentWorksheet).mockResolvedValue(studentWorksheet);
    vi.mocked(fetchManualAnswerDraft).mockResolvedValue({
      submissionDocumentId: null,
      answers: [],
      status: "DRAFT",
      inputMethod: "MANUAL",
    });
    vi.mocked(saveManualAnswers).mockResolvedValue({
      submissionDocumentId: 70,
      submissionIds: [71, 72],
      status: "DRAFT",
      inputMethod: "MANUAL",
    });
  });

  it("renders every assigned question and saves typed answers as a draft", async () => {
    const user = userEvent.setup();
    render(<Page />);
    expect(await screen.findByText("Question 1 · 1.0 marks")).toBeVisible();
    expect(screen.getByText("Question 2 · 2.0 marks")).toBeVisible();
    await user.click(screen.getByRole("radio", { name: "A. Sunlight" }));
    await user.type(
      screen.getByLabelText("Student answer"),
      "Water becomes vapour.",
    );
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() =>
      expect(saveManualAnswers).toHaveBeenCalledWith({
        studentId: 7,
        worksheetId: 42,
        answers: [
          { questionBankId: 4, answer: "A. Sunlight" },
          { questionBankId: 5, answer: "Water becomes vapour." },
        ],
        submit: false,
      }),
    );
    expect(screen.getByRole("status")).toHaveTextContent("Draft saved");
  });

  it("restores a draft and submits it to the Student results workflow", async () => {
    vi.mocked(fetchManualAnswerDraft).mockResolvedValue({
      submissionDocumentId: 70,
      answers: [{ questionBankId: 5, answer: "Saved answer" }],
      status: "DRAFT",
      inputMethod: "MANUAL",
    });
    vi.mocked(saveManualAnswers).mockResolvedValue({
      submissionDocumentId: 70,
      submissionIds: [72],
      status: "PENDING_REVIEW",
      inputMethod: "MANUAL",
    });
    const user = userEvent.setup();
    render(<Page />);
    expect(await screen.findByDisplayValue("Saved answer")).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Submit for Tutor Review" }),
    );
    await waitFor(() =>
      expect(navigation.replace).toHaveBeenCalledWith("/worksheets/42/results"),
    );
  });

  it("uses the selected Tutor class and student context before opening the real review", async () => {
    navigation.params = new URLSearchParams(
      "worksheetId=42&studentId=7&classId=3",
    );
    vi.mocked(getBrowserSession).mockReturnValue({
      token: "token",
      email: "tutor@example.test",
      role: "TUTOR",
      expiresAt: Date.now() + 60_000,
    });
    vi.mocked(fetchTutorWorksheet).mockResolvedValue({
      ...studentWorksheet,
      worksheetType: "STANDARD",
      targetMode: "CLASS",
      status: "APPROVED",
      generationRequestId: null,
      assignments: [],
    } as never);
    vi.mocked(fetchManualAnswerDraft).mockResolvedValue({
      submissionDocumentId: 70,
      answers: [{ questionBankId: 4, answer: "A. Sunlight" }],
      status: "DRAFT",
      inputMethod: "MANUAL",
    });
    vi.mocked(saveManualAnswers).mockResolvedValue({
      submissionDocumentId: 70,
      submissionIds: [91],
      status: "PENDING_REVIEW",
      inputMethod: "MANUAL",
    });

    const user = userEvent.setup();
    render(<Page />);
    await screen.findByRole("radio", { name: "A. Sunlight", checked: true });
    await user.click(
      screen.getByRole("button", { name: "Submit for Tutor Review" }),
    );
    await waitFor(() =>
      expect(saveManualAnswers).toHaveBeenCalledWith({
        studentId: 7,
        worksheetId: 42,
        classId: 3,
        answers: [{ questionBankId: 4, answer: "A. Sunlight" }],
        submit: true,
      }),
    );
    expect(navigation.replace).toHaveBeenCalledWith("/tutor/reviews/91");
  });

  it("requires Tutor context before loading a Tutor manual entry form", async () => {
    vi.mocked(getBrowserSession).mockReturnValue({
      token: "token",
      email: "tutor@example.test",
      role: "TUTOR",
      expiresAt: Date.now() + 60_000,
    });
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "could not be loaded",
    );
    expect(fetchTutorWorksheet).not.toHaveBeenCalled();
  });
});
