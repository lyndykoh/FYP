import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  fetchPendingMarkingReviews,
  fetchSubmittedWorksheetSource,
  fetchSubmittedWorksheetSourcePageUrl,
  parsePendingMarkingReviews,
  parseSubmittedWorksheetSource,
} from "./marking-reviews";

const review = {
  submissionId: 91,
  submissionDocumentId: 34,
  studentId: 7,
  studentName: "Bella Tan",
  worksheetId: 8,
  worksheetQuestionId: 22,
  requestedAt: "2026-08-31T10:00:00",
  sourceAvailable: true,
};

const source = {
  submissionId: 91,
  submissionDocumentId: 34,
  studentId: 7,
  worksheetId: 8,
  sourceType: "PDF",
  status: "SUBMITTED_FOR_REVIEW",
  pages: [
    {
      id: 15,
      pageNumber: 1,
      originalFilename: "student-work.pdf",
      mediaType: "application/pdf",
      byteSize: 3000,
    },
  ],
};

describe("pending marking reviews client", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("fetches and validates the tutor-scoped pending review queue", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify([review]), { status: 200 }),
    );

    await expect(fetchPendingMarkingReviews()).resolves.toEqual([review]);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/grading/tutor/reviews"),
      expect.objectContaining({ headers: expect.any(Object) }),
    );
  });

  it("rejects malformed review records", () => {
    expect(() => parsePendingMarkingReviews([{ submissionId: 91 }])).toThrow(
      /invalid/i,
    );
  });

  it("loads the submitted source metadata before allowing a Tutor to view it", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify(source), { status: 200 }),
    );

    await expect(fetchSubmittedWorksheetSource(91)).resolves.toEqual(source);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/grading/tutor/reviews/91/source"),
      expect.objectContaining({ headers: expect.any(Object) }),
    );
  });

  it("rejects invalid source metadata and non-file source responses", async () => {
    expect(() => parseSubmittedWorksheetSource({ pages: [] })).toThrow(
      /invalid/i,
    );

    vi.mocked(fetch).mockResolvedValueOnce(
      new Response("not a worksheet", {
        status: 200,
        headers: { "content-type": "text/plain" },
      }),
    );

    await expect(fetchSubmittedWorksheetSourcePageUrl(91, 15)).rejects.toThrow(
      /invalid/i,
    );
  });
});
