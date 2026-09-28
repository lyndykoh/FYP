import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Page from "./page";
import { getBrowserSession } from "@/lib/auth";
import {
  fetchSubmissionDocument,
  submitOcrForTutorReview,
} from "@/services/submissions";
import {
  fetchStudentWorksheet,
  fetchTutorWorksheet,
} from "@/services/worksheets";

const navigation = vi.hoisted(() => ({
  params: new URLSearchParams("submissionId=55"),
  replace: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => navigation.params,
  useRouter: () => ({ replace: navigation.replace }),
}));
vi.mock("@/lib/auth", () => ({ getBrowserSession: vi.fn() }));
vi.mock("@/services/submissions", () => ({
  correctOcrExtraction: vi.fn(),
  fetchSubmissionDocument: vi.fn(),
  submitOcrForTutorReview: vi.fn(),
  SubmissionApiError: class SubmissionApiError extends Error {},
}));
vi.mock("@/services/worksheets", () => ({
  fetchStudentWorksheet: vi.fn(),
  fetchTutorWorksheet: vi.fn(),
}));
vi.mock("@/components/submissions/OcrReview", () => ({
  default: ({
    onSubmitForReview,
  }: {
    onSubmitForReview?: (
      answers: Array<{ extractionId: number; questionBankId: number }>,
    ) => Promise<void>;
  }) => (
    <>
      <div>OCR pages</div>
      {onSubmitForReview ? (
        <button
          onClick={() =>
            void onSubmitForReview([{ extractionId: 1, questionBankId: 4 }])
          }
        >
          Submit for Tutor Review
        </button>
      ) : null}
    </>
  ),
}));

describe("Student OCR review", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    navigation.replace.mockReset();
    vi.mocked(getBrowserSession).mockReturnValue({
      token: "token",
      email: "student@example.test",
      role: "STUDENT",
      expiresAt: Date.now() + 60_000,
    });
    vi.mocked(fetchSubmissionDocument).mockResolvedValue({
      id: 55,
      classId: null,
      studentId: 7,
      worksheetId: 42,
      uploadedByTutorId: null,
      status: "READY",
      createdAt: "2026-08-30T09:00:00",
      pages: [],
    });
    vi.mocked(fetchStudentWorksheet).mockResolvedValue({
      id: 42,
      code: "WS-42",
      title: "Science",
      instructions: "",
      subject: "Science",
      assignedAt: "2026-08-30T09:00:00",
      dueAt: null,
      questions: [],
    });
    vi.mocked(fetchTutorWorksheet).mockResolvedValue({
      id: 42,
      code: "WS-42",
      title: "Science",
      instructions: "",
      subject: "Science",
      worksheetType: "STANDARD",
      targetMode: "CLASS",
      status: "APPROVED",
      generationRequestId: null,
      dueAt: null,
      questions: [
        {
          id: 4,
          code: "Q-4",
          prompt: "Explain.",
          totalMarks: 2,
          questionType: "OPEN_ENDED",
          topicName: "Topic",
        },
      ],
      assignments: [],
    });
    vi.mocked(submitOcrForTutorReview).mockResolvedValue({
      submissionDocumentId: 55,
      submissionIds: [91],
      status: "PENDING_REVIEW",
    });
  });

  it("renders the saved Student submission and returns to the Student worksheet library", async () => {
    render(<Page />);
    expect(await screen.findByText("Submission #55")).toBeVisible();
    expect(fetchSubmissionDocument).toHaveBeenCalledWith(55);
    expect(fetchStudentWorksheet).toHaveBeenCalledWith(42);
    expect(
      screen.getByRole("link", { name: "Return to My Worksheets" }),
    ).toHaveAttribute("href", "/worksheets");
  });

  it("submits a Student's confirmed OCR answers into their Tutor review workflow", async () => {
    vi.mocked(fetchStudentWorksheet).mockResolvedValue({
      id: 42,
      code: "WS-42",
      title: "Science",
      instructions: "",
      subject: "Science",
      assignedAt: "2026-08-30T09:00:00",
      dueAt: null,
      questions: [
        {
          id: 4,
          code: "Q-4",
          prompt: "Explain.",
          totalMarks: 2,
          questionType: "OPEN_ENDED",
          topicName: "Topic",
        },
      ],
    });
    render(<Page />);
    await screen.findByRole("button", { name: "Submit for Tutor Review" });
    await screen
      .getByRole("button", { name: "Submit for Tutor Review" })
      .click();
    expect(submitOcrForTutorReview).toHaveBeenCalledWith(55, [
      { extractionId: 1, questionBankId: 4 },
    ]);
    expect(navigation.replace).toHaveBeenCalledWith("/worksheets/42/results");
  });

  it("lets a Tutor submit confirmed OCR answers and opens the real marking review", async () => {
    vi.mocked(getBrowserSession).mockReturnValue({
      token: "token",
      email: "tutor@example.test",
      role: "TUTOR",
      expiresAt: Date.now() + 60_000,
    });
    render(<Page />);
    await screen.findByText("Submission #55");
    expect(fetchTutorWorksheet).toHaveBeenCalledWith(42);
    await screen.findByRole("button", { name: "Submit for Tutor Review" });
    await screen
      .getByRole("button", { name: "Submit for Tutor Review" })
      .click();
    expect(submitOcrForTutorReview).toHaveBeenCalledWith(55, [
      { extractionId: 1, questionBankId: 4 },
    ]);
    expect(navigation.replace).toHaveBeenCalledWith("/tutor/reviews/91");
  });
});
