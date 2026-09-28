import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchMyMistakes,
  fetchStudentMistakes,
  parseMistakeHistory,
} from "./mistakes";

const item = {
  id: 1,
  worksheetId: 2,
  worksheetQuestionId: 3,
  questionBankId: 4,
  syllabusTopicId: 5,
  syllabusTopicCode: "SCI-5",
  mistakeType: "WRONG_UNITS",
  mistakeLabel: "Wrong units",
  description: "The unit was omitted.",
  recordedAt: "2026-08-29T10:00:00",
};

describe("mistake history service", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));

  it("loads the signed-in student's canonical history and validates it", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify([item]), { status: 200 }),
    );
    await expect(fetchMyMistakes()).resolves.toEqual([item]);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/grading/mistakes/me"),
      expect.any(Object),
    );
    expect(() =>
      parseMistakeHistory([{ ...item, mistakeType: "unknown" }]),
    ).toThrow(/invalid/i);
  });

  it("uses a protected Tutor route and rejects invalid identifiers locally", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200 }),
    );
    await expect(fetchStudentMistakes(22)).resolves.toEqual([]);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/students/22"),
      expect.any(Object),
    );
    await expect(fetchStudentMistakes(0)).rejects.toMatchObject({
      status: 400,
    });
  });
});
