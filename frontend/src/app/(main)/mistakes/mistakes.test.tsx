import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Page from "./page";
import {
  fetchStudentMistakes,
  type StudentMistakeReview,
} from "@/services/submissions";

vi.mock("@/services/submissions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/submissions")>()),
  fetchStudentMistakes: vi.fn(),
}));

const history: StudentMistakeReview[] = [
  {
    id: 1,
    worksheetId: 21,
    worksheetQuestionId: 31,
    questionBankId: 41,
    syllabusTopicId: 9,
    syllabusTopicCode: "SCI-9",
    mistakeType: "WRONG_UNITS",
    mistakeLabel: "Wrong units",
    description: "The answer did not include units.",
    recordedAt: "2026-08-28T10:00:00Z",
    subjectId: 8,
    subjectName: "Science",
    topicName: "Forces",
    occurrenceCount: 3,
    status: "CONFIRMED",
  },
  {
    id: 2,
    worksheetId: 22,
    worksheetQuestionId: 32,
    questionBankId: 42,
    syllabusTopicId: 10,
    syllabusTopicCode: "SCI-10",
    mistakeType: "MISSING_KEY_POINT",
    mistakeLabel: "Missing key point",
    description: "The explanation omitted the key term.",
    recordedAt: "2026-08-20T10:00:00Z",
    subjectId: 8,
    subjectName: "Science",
    topicName: "Energy",
    occurrenceCount: 1,
    status: "CONFIRMED",
  },
];

describe("Student mistake review page", () => {
  beforeEach(() => vi.mocked(fetchStudentMistakes).mockReset());

  it("sends each persisted subject, topic, type, worksheet and date filter to the server", async () => {
    vi.mocked(fetchStudentMistakes).mockResolvedValue(history);
    const user = userEvent.setup();
    render(<Page />);
    await screen.findByText("The answer did not include units.");

    await user.selectOptions(screen.getByLabelText("Subject"), "8");
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ subjectId: 8 }),
      ),
    );
    await user.selectOptions(screen.getByLabelText("Topic"), "9");
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ subjectId: 8, topicId: 9 }),
      ),
    );
    await user.selectOptions(
      screen.getByLabelText("Mistake type"),
      "WRONG_UNITS",
    );
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ mistakeType: "WRONG_UNITS" }),
      ),
    );
    await user.selectOptions(screen.getByLabelText("Worksheet"), "21");
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ worksheetId: 21 }),
      ),
    );
    await user.type(screen.getByLabelText("From date"), "2026-08-01");
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ from: "2026-08-01" }),
      ),
    );
    await user.type(screen.getByLabelText("To date"), "2026-08-29");
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ to: "2026-08-29" }),
      ),
    );
  });

  it("shows persisted confirmation and repeated history with worksheet and topic links", async () => {
    vi.mocked(fetchStudentMistakes).mockResolvedValue(history);
    render(<Page />);
    expect(await screen.findByText("Repeated 3 times")).toBeVisible();
    expect(screen.getAllByText("Tutor confirmed")).toHaveLength(2);
    expect(
      screen.getAllByRole("link", { name: "View worksheet" })[0],
    ).toHaveAttribute("href", "/worksheets/21");
    expect(
      screen.getAllByRole("link", { name: "Review this topic" })[0],
    ).toHaveAttribute("href", "/topics/9");
  });

  it("shows loading, an empty filtered state, and a retryable error", async () => {
    let resolve: ((items: StudentMistakeReview[]) => void) | undefined;
    vi.mocked(fetchStudentMistakes).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { unmount } = render(<Page />);
    expect(await screen.findByTestId("mistakes-loading")).toBeVisible();
    resolve?.([]);
    expect(
      await screen.findByRole("heading", { name: "No confirmed mistakes yet" }),
    ).toBeVisible();
    unmount();

    vi.mocked(fetchStudentMistakes)
      .mockRejectedValueOnce(new Error("Review history is unavailable."))
      .mockResolvedValueOnce([]);
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Review history is unavailable.",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(
      await screen.findByRole("heading", { name: "No confirmed mistakes yet" }),
    ).toBeVisible();
  });
});
