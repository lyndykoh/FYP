import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  MAX_UPLOAD_BYTES,
  analyzeImagePixels,
  approveMarkingReview,
  createManualResult,
  createManualResults,
  createMarkingReview,
  createOcrDocument,
  fetchManualAnswerDraft,
  fetchManualResults,
  fetchMarkingReview,
  fetchStudentMistakes,
  fetchStudentWorksheetResults,
  parseMarkingReview,
  parseStudentMistakeReviews,
  parseStudentWorksheetResultsResponse,
  saveManualAnswers,
  submitOcrForTutorReview,
  preflightUploadImage,
  validateUploadFiles,
} from "./submissions";

const file = (name: string, type = "image/jpeg", size = 4) =>
  new File([new Uint8Array(size)], name, { type, lastModified: 1 });

function createSyntheticImage(
  width: number,
  height: number,
  background = 255,
): { width: number; height: number; data: Uint8ClampedArray } {
  const data = new Uint8ClampedArray(width * height * 4);

  for (let pixelIndex = 0; pixelIndex < width * height; pixelIndex += 1) {
    const dataIndex = pixelIndex * 4;
    data[dataIndex] = background;
    data[dataIndex + 1] = background;
    data[dataIndex + 2] = background;
    data[dataIndex + 3] = 255;
  }

  return { width, height, data };
}

function drawWriting(
  image: { width: number; height: number; data: Uint8ClampedArray },
  left: number,
  top: number,
  writingWidth: number,
  writingHeight: number,
  color = 20,
): void {
  const lineSpacing = 20;

  for (let y = top; y < top + writingHeight; y += lineSpacing) {
    for (let strokeY = y; strokeY < y + 3; strokeY += 1) {
      for (let x = left; x < left + writingWidth; x += 1) {
        const dataIndex = (strokeY * image.width + x) * 4;
        image.data[dataIndex] = color;
        image.data[dataIndex + 1] = color;
        image.data[dataIndex + 2] = color;
      }
    }
  }
}

function stubBrowserApis(): void {
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn(() => "blob:page"),
    revokeObjectURL: vi.fn(),
  });
  vi.stubGlobal("fetch", vi.fn());
}

const pendingReview = {
  id: 1,
  studentId: 2,
  worksheetId: 3,
  worksheetQuestionId: 4,
  questionBankId: 5,
  extractedAnswer: "Answer",
  modelAnswer: "Model",
  maxMarks: 2,
  aiSuggestedMarks: 1,
  aiSuggestedOutcome: "Partial",
  aiErrorCategory: null,
  missingKeywords: [],
  aiSuggestedFeedback: "Feedback",
  reviewStatus: "PENDING_REVIEW",
  approvedMarks: null,
  approvedFeedback: null,
  reviewedByUserId: null,
  reviewedAt: null,
  providerResponseValid: true,
  diagnosticEvidence: [],
  history: [],
};

describe("submissions service", () => {
  beforeEach(stubBrowserApis);

  it("accepts images and PDFs while rejecting invalid, duplicate, empty and oversized files", () => {
    const first = file("one.jpg");
    const acceptedPages = validateUploadFiles([
      first,
      file("two.pdf", "application/pdf"),
    ]).pages;
    const duplicatePages = validateUploadFiles([first]).pages;
    const duplicateError = validateUploadFiles([first], duplicatePages)
      .errors[0];
    const invalidFiles = validateUploadFiles([
      file("bad.gif", "image/gif"),
      file("empty.jpg", "image/jpeg", 0),
      file("large.jpg", "image/jpeg", MAX_UPLOAD_BYTES + 1),
    ]);

    expect(acceptedPages).toHaveLength(2);
    expect(duplicateError).toMatch(/already/);
    expect(invalidFiles.errors).toHaveLength(3);
  });

  it("creates one durable submission document with its selected context and pages", async () => {
    const pages = validateUploadFiles([
      file("one.jpg"),
      file("two.png", "image/png"),
    ]).pages;
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 7,
          classId: 3,
          studentId: 2,
          worksheetId: 1,
          uploadedByTutorId: 9,
          status: "READY",
          createdAt: "2026-08-30T09:00:00",
          pages: [
            {
              id: 11,
              extractionId: 12,
              text: "answer",
              confidence: 0.8,
              status: "READY",
            },
          ],
        }),
        { status: 201 },
      ),
    );

    await expect(
      createOcrDocument({ classId: 3, studentId: 2, worksheetId: 1, pages }),
    ).resolves.toMatchObject({
      id: 7,
      classId: 3,
      studentId: 2,
      worksheetId: 1,
    });

    const [, request] = vi.mocked(fetch).mock.calls[0]!;
    const form = (request as RequestInit).body as FormData;

    expect(request).toMatchObject({ method: "POST" });
    expect((request as RequestInit).headers).not.toHaveProperty("Content-Type");
    expect(form.get("classId")).toBe("3");
    expect(form.getAll("files")).toHaveLength(2);
  });

  it("loads and validates tutor marking reviews before approval", async () => {
    const approvedReview = {
      ...pendingReview,
      reviewStatus: "APPROVED",
      approvedMarks: 2,
      approvedFeedback: "Tutor feedback",
    };
    const diagnosticEvidence = [
      {
        mistakeType: "CONCEPT_MISUNDERSTANDING" as const,
        description: "Tutor confirmed a concept gap.",
        missingKeywords: ["heat transfer"],
      },
    ];
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify(pendingReview), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(approvedReview), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(pendingReview), { status: 201 }),
      );

    await expect(fetchMarkingReview(1)).resolves.toMatchObject({
      id: 1,
      reviewStatus: "PENDING_REVIEW",
    });
    await expect(
      approveMarkingReview(1, 2, "Tutor feedback", diagnosticEvidence),
    ).resolves.toMatchObject({ diagnosticEvidence: [] });

    expect(fetch).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("/approve"),
      expect.objectContaining({
        body: JSON.stringify({
          marks: 2,
          feedback: "Tutor feedback",
          diagnosticEvidence,
        }),
      }),
    );

    await expect(
      createMarkingReview({
        submissionDocumentId: 8,
        worksheetQuestionId: 4,
        questionBankId: 5,
      }),
    ).resolves.toMatchObject({ id: 1 });
    expect(fetch).toHaveBeenLastCalledWith(
      expect.stringContaining("/tutor/reviews"),
      expect.objectContaining({ method: "POST" }),
    );
    expect(() => parseMarkingReview({ id: 1 })).toThrow(/invalid/i);
  });
});

describe("image quality preflight", () => {
  it("passes a sufficiently large, sharp worksheet photo", () => {
    const image = createSyntheticImage(1536, 2048);
    drawWriting(image, 180, 300, 1_000, 280);

    const quality = analyzeImagePixels(image);

    expect(quality.warnings).toEqual([]);
    expect(quality.estimatedWritingSize.widthFraction).toBeGreaterThan(0.5);
    expect(quality.estimatedWritingSize.heightFraction).toBeGreaterThan(0.1);
  });

  it("flags low resolution using native dimensions, not a bounded analysis canvas", () => {
    const image = createSyntheticImage(1_600, 800);
    drawWriting(image, 160, 180, 1_000, 260);

    const quality = analyzeImagePixels(image, { width: 2_000, height: 1_000 });

    expect(quality.warnings).not.toContain("LOW_RESOLUTION");

    const lowResolutionQuality = analyzeImagePixels(image, {
      width: 640,
      height: 480,
    });

    expect(lowResolutionQuality.warnings).toContain("LOW_RESOLUTION");
  });

  it("recommends a retake for low contrast, blurred, or tiny visible writing", () => {
    const lowContrast = createSyntheticImage(1_200, 1_400, 235);
    drawWriting(lowContrast, 100, 300, 900, 250, 210);
    const blurred = createSyntheticImage(1_200, 1_400, 200);
    const tinyWriting = createSyntheticImage(1_200, 1_400);
    drawWriting(tinyWriting, 580, 680, 20, 12);

    const lowContrastQuality = analyzeImagePixels(lowContrast);
    const blurredQuality = analyzeImagePixels(blurred);
    const tinyWritingQuality = analyzeImagePixels(tinyWriting);

    expect(lowContrastQuality.warnings).toContain("LOW_CONTRAST");
    expect(blurredQuality.warnings).toContain("BLURRY");
    expect(tinyWritingQuality.warnings).toContain("WRITING_TOO_SMALL");
  });

  it("leaves PDFs available for the existing upload workflow without pixel analysis", async () => {
    await expect(
      preflightUploadImage(file("worksheet.pdf", "application/pdf")),
    ).resolves.toEqual({
      assessmentStatus: "unavailable",
      mediaType: "application/pdf",
      width: null,
      height: null,
      quality: null,
      guidance: ["Photo quality is not assessed for this PDF."],
    });
  });

  it("does not mistake an unavailable browser image decoder for bad handwriting", async () => {
    await expect(preflightUploadImage(file("worksheet.jpg"))).resolves.toMatchObject({
      assessmentStatus: "unavailable",
      quality: null,
      guidance: [expect.stringMatching(/can still upload/i)],
    });
  });
});

describe("Student submission document client", () => {
  beforeEach(() => {
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:page"),
      revokeObjectURL: vi.fn(),
    });
    vi.stubGlobal("fetch", vi.fn());
    localStorage.setItem("jwt_token", "student-token");
  });

  it("omits class context for a Student-owned assigned-worksheet upload", async () => {
    const pages = validateUploadFiles([file("answer.png", "image/png")]).pages;
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 8,
          classId: null,
          studentId: 7,
          worksheetId: 42,
          uploadedByTutorId: null,
          status: "READY",
          createdAt: "2026-08-30T09:00:00",
          pages: [
            {
              id: 11,
              extractionId: 12,
              text: "answer",
              confidence: 0.8,
              status: "REQUIRES_REVIEW",
            },
          ],
        }),
        { status: 201 },
      ),
    );
    await expect(
      createOcrDocument({ studentId: 7, worksheetId: 42, pages }),
    ).resolves.toMatchObject({ id: 8, studentId: 7, worksheetId: 42 });
    const [, request] = vi.mocked(fetch).mock.calls[0]!;
    const form = (request as RequestInit).body as FormData;
    expect((request as RequestInit).headers).toEqual({
      Authorization: "Bearer student-token",
    });
    expect(form.has("classId")).toBe(false);
    expect(form.get("studentId")).toBe("7");
    expect(form.get("worksheetId")).toBe("42");
  });

  it("submits confirmed OCR page mappings to the protected review endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          submissionDocumentId: 8,
          submissionIds: [20],
          status: "PENDING_REVIEW",
        }),
        { status: 201 },
      ),
    );
    await expect(
      submitOcrForTutorReview(8, [{ extractionId: 12, questionBankId: 30 }]),
    ).resolves.toMatchObject({
      submissionDocumentId: 8,
      status: "PENDING_REVIEW",
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/submission-documents/8/submit-for-review"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          answers: [{ extractionId: 12, questionBankId: 30 }],
        }),
      }),
    );
    await expect(submitOcrForTutorReview(0, [])).rejects.toMatchObject({
      status: 400,
    });
  });

  it("loads and saves typed answers through the canonical manual-answer contract", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            submissionDocumentId: 8,
            submissionIds: [20],
            answers: [{ questionBankId: 30, answer: "Evaporation" }],
            status: "DRAFT",
            inputMethod: "MANUAL",
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            submissionDocumentId: 8,
            submissionIds: [20],
            answers: [{ questionBankId: 30, answer: "Evaporation" }],
            status: "PENDING_REVIEW",
            inputMethod: "MANUAL",
          }),
          { status: 200 },
        ),
      );

    await expect(
      fetchManualAnswerDraft({ studentId: 7, worksheetId: 42 }),
    ).resolves.toMatchObject({
      submissionDocumentId: 8,
      answers: [{ questionBankId: 30, answer: "Evaporation" }],
    });
    await expect(
      saveManualAnswers({
        studentId: 7,
        worksheetId: 42,
        answers: [{ questionBankId: 30, answer: "Evaporation" }],
        submit: true,
      }),
    ).resolves.toMatchObject({
      submissionDocumentId: 8,
      submissionIds: [20],
      status: "PENDING_REVIEW",
    });

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining("/manual-answers?studentId=7&worksheetId=42"),
      expect.objectContaining({
        headers: { Authorization: "Bearer student-token" },
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("/manual-answers"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          studentId: 7,
          worksheetId: 42,
          answers: [{ questionBankId: 30, answer: "Evaporation" }],
          submit: true,
        }),
      }),
    );
  });
});

describe("student worksheet results client", () => {
  const approved = {
    submissionId: 10,
    worksheetQuestionId: 20,
    questionBankId: 30,
    answer: "Student answer",
    modelAnswer: "Model answer",
    maximumMarks: 2,
    reviewStatus: "APPROVED",
    outcome: "PARTIAL",
    awardedMarks: 1,
    explanation: "Use the subject term.",
    reviewedAt: "2026-08-29T09:00:00",
  };

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.clear();
  });

  it("uses the authenticated Student results endpoint and rejects leaked final data before approval", async () => {
    localStorage.setItem("jwt_token", "student-token");
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify({ worksheetId: 7, results: [approved] }), {
        status: 200,
      }),
    );
    await expect(fetchStudentWorksheetResults(7)).resolves.toMatchObject({
      worksheetId: 7,
      results: [{ outcome: "PARTIAL", reviewStatus: "APPROVED" }],
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/grading/student/worksheets/7/results"),
      expect.objectContaining({
        headers: { Authorization: "Bearer student-token" },
      }),
    );
    expect(() =>
      parseStudentWorksheetResultsResponse({
        worksheetId: 7,
        results: [
          {
            ...approved,
            reviewStatus: "PENDING_REVIEW",
            awardedMarks: null,
            explanation: null,
            modelAnswer: "Leaked answer",
          },
        ],
      }),
    ).toThrow(/invalid/i);
  });
});

describe("student mistake review client", () => {
  const confirmed = {
    id: 1,
    worksheetId: 2,
    worksheetQuestionId: 3,
    questionBankId: 4,
    syllabusTopicId: 5,
    syllabusTopicCode: "SCI-5",
    mistakeType: "WRONG_UNITS",
    mistakeLabel: "Wrong units",
    description: "Units were omitted.",
    recordedAt: "2026-08-29T09:00:00Z",
    subjectId: 6,
    subjectName: "Science",
    topicName: "Forces",
    occurrenceCount: 2,
    status: "CONFIRMED",
  };
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.clear();
  });

  it("uses authenticated server filters and rejects malformed or non-confirmed review records", async () => {
    localStorage.setItem("jwt_token", "student-token");
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify([confirmed]), { status: 200 }),
    );
    await expect(
      fetchStudentMistakes({
        subjectId: 6,
        topicId: 5,
        mistakeType: "WRONG_UNITS",
        worksheetId: 2,
        from: "2026-08-01",
        to: "2026-08-31",
      }),
    ).resolves.toMatchObject([{ occurrenceCount: 2, status: "CONFIRMED" }]);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(
        "/api/grading/student/mistakes?subjectId=6&topicId=5&mistakeType=WRONG_UNITS&worksheetId=2&from=2026-08-01&to=2026-08-31",
      ),
      expect.objectContaining({
        headers: { Authorization: "Bearer student-token" },
      }),
    );
    expect(() =>
      parseStudentMistakeReviews([{ ...confirmed, status: "PENDING_REVIEW" }]),
    ).toThrow(/invalid/i);
    await expect(
      fetchStudentMistakes({ worksheetId: 0 }),
    ).rejects.toMatchObject({ status: 400 });
  });
});

describe("manual result client", () => {
  it("posts a validated manual result and rejects missing required values locally", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              id: 1,
              studentId: 2,
              worksheetId: 3,
              worksheetQuestionId: 5,
              questionBankId: 5,
              extractedAnswer: "Answer",
              modelAnswer: "Model",
              maxMarks: 2,
              aiSuggestedMarks: null,
              aiSuggestedOutcome: null,
              aiErrorCategory: null,
              missingKeywords: [],
              aiSuggestedFeedback: null,
              reviewStatus: "APPROVED",
              approvedMarks: 1.5,
              approvedFeedback: "Tutor feedback",
              reviewedByUserId: 1,
              reviewedAt: "2026-08-27T10:00:00",
              providerResponseValid: null,
              diagnosticEvidence: [],
              history: [],
            }),
            { status: 201 },
          ),
        ),
    );
    await expect(
      createManualResult({
        worksheetId: 3,
        studentId: 2,
        questionBankId: 5,
        answer: "Answer",
        marks: 1.5,
        feedback: "Tutor feedback",
      }),
    ).resolves.toMatchObject({ reviewStatus: "APPROVED" });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/tutor/reviews/manual"),
      expect.objectContaining({ method: "POST" }),
    );
    await expect(
      createManualResult({
        worksheetId: 3,
        studentId: 2,
        questionBankId: 5,
        answer: "",
        marks: 1.5,
        feedback: "Tutor feedback",
      }),
    ).rejects.toMatchObject({ status: 400 });
  });

  it("uses the batch contract and validates the owner-scoped progress response", async () => {
    const review = {
      id: 1,
      studentId: 2,
      worksheetId: 3,
      worksheetQuestionId: 5,
      questionBankId: 5,
      extractedAnswer: "Answer",
      modelAnswer: "Model",
      maxMarks: 2,
      aiSuggestedMarks: null,
      aiSuggestedOutcome: null,
      aiErrorCategory: null,
      missingKeywords: [],
      aiSuggestedFeedback: null,
      reviewStatus: "APPROVED",
      approvedMarks: 1.5,
      approvedFeedback: "Tutor feedback",
      reviewedByUserId: 1,
      reviewedAt: "2026-08-27T10:00:00",
      providerResponseValid: null,
      diagnosticEvidence: [],
      history: [],
    };
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(JSON.stringify([review]), { status: 201 }),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              worksheetId: 3,
              students: [
                { studentId: 2, completedQuestions: 1, results: [review] },
              ],
            }),
            { status: 200 },
          ),
        ),
    );
    await expect(
      createManualResults({
        worksheetId: 3,
        studentId: 2,
        entries: [
          {
            questionBankId: 5,
            answer: "Answer",
            marks: 1.5,
            feedback: "Tutor feedback",
          },
        ],
      }),
    ).resolves.toHaveLength(1);
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      expect.stringContaining("/tutor/reviews/manual/batch"),
      expect.objectContaining({ method: "POST" }),
    );
    await expect(fetchManualResults(3)).resolves.toMatchObject({
      worksheetId: 3,
      students: [{ studentId: 2, completedQuestions: 1 }],
    });
    await expect(
      createManualResults({ worksheetId: 3, studentId: 2, entries: [] }),
    ).rejects.toMatchObject({ status: 400 });
  });
});
