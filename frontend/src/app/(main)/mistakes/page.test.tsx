import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Page from "./page";
import { fetchStudentMistakes } from "@/services/submissions";

vi.mock("@/services/submissions", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/submissions")>()),
  fetchStudentMistakes: vi.fn(),
}));

const history = [
  {
    id: 1,
    worksheetId: 2,
    worksheetQuestionId: 3,
    questionBankId: 4,
    syllabusTopicId: 5,
    syllabusTopicCode: "SCI-5",
    mistakeType: "WRONG_UNITS" as const,
    mistakeLabel: "Wrong units",
    description: "The unit was omitted.",
    recordedAt: "2026-08-29T10:00:00",
    subjectId: 1,
    subjectName: "Science",
    topicName: "Units",
    occurrenceCount: 1,
    status: "CONFIRMED" as const,
  },
  {
    id: 2,
    worksheetId: 2,
    worksheetQuestionId: 4,
    questionBankId: 5,
    syllabusTopicId: 6,
    syllabusTopicCode: "SCI-6",
    mistakeType: "MISSING_KEY_POINT" as const,
    mistakeLabel: "Missing key point",
    description: "The required phrase was omitted.",
    recordedAt: "2026-08-28T10:00:00",
    subjectId: 1,
    subjectName: "Science",
    topicName: "Phrases",
    occurrenceCount: 2,
    status: "CONFIRMED" as const,
  },
];

describe("Mistakes page", () => {
  beforeEach(() => vi.mocked(fetchStudentMistakes).mockReset());

  it("renders persisted canonical records and filters by their Tutor-selected type", async () => {
    vi.mocked(fetchStudentMistakes).mockResolvedValue(history);
    const user = userEvent.setup();
    render(<Page />);
    expect(
      await screen.findByText("The unit was omitted."),
    ).toBeInTheDocument();
    await user.selectOptions(
      screen.getByLabelText("Mistake type"),
      "WRONG_UNITS",
    );
    await waitFor(() =>
      expect(fetchStudentMistakes).toHaveBeenLastCalledWith(
        expect.objectContaining({ mistakeType: "WRONG_UNITS" }),
      ),
    );
    expect(screen.getByText("The unit was omitted.")).toBeInTheDocument();
  });

  it("renders empty and recoverable error states", async () => {
    vi.mocked(fetchStudentMistakes).mockResolvedValueOnce([]);
    const { unmount } = render(<Page />);
    expect(
      await screen.findByText("No confirmed mistakes yet"),
    ).toBeInTheDocument();
    unmount();
    vi.mocked(fetchStudentMistakes).mockRejectedValueOnce(
      new Error("History is unavailable."),
    );
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "History is unavailable.",
    );
  });
});
