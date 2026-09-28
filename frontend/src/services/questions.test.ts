import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  QuestionApiError,
  addQuestionToWorksheetDraft,
  checkTutorQuestionAnswer,
  createTutorQuestion,
  fetchTutorQuestion,
  fetchTutorQuestions,
  importQuestionImportCandidates,
  isQuestionInWorksheetDraft,
  mergeQuestionImportCandidates,
  moveQuestionImportDiagramCrops,
  parseQuestionBankPage,
  parseTutorQuestion,
  rejectQuestionImportCandidate,
  restoreQuestionImportCandidate,
  splitQuestionImportCandidate,
  updateQuestionImportCandidate,
  updateTutorQuestion,
  uploadQuestionImport,
} from "./questions";

const response = {
  items: [
    {
      id: 7,
      code: "SCI-WATER-001",
      syllabusTopic: {
        id: 14,
        code: "SCI_P5_WATER",
        name: "Water",
        nodeType: "SUBTOPIC",
      },
      questionType: "OPEN_ENDED" as const,
      prompt: "Explain why evaporation happens faster on a hot day.",
      totalMarks: 2,
      archiveState: "ACTIVE" as const,
    },
  ],
  page: 1,
  size: 12,
  totalElements: 13,
  totalPages: 2,
  hasNext: false,
};

const detailResponse = {
  ...response.items[0],
  modelAnswer: "Evaporation happens when water gains enough energy.",
  markingComponents: [
    {
      position: 0,
      description: "Explains energy gain",
      marks: 2,
      keywords: ["energy gain"],
    },
  ],
  keywords: ["evaporation"],
  createdAt: "2026-08-27T08:00:00",
  updatedAt: "2026-08-27T08:00:00",
};

const mutation = {
  code: "SCI-WATER-001",
  syllabusTopicId: 14,
  questionType: "OPEN_ENDED" as const,
  prompt: "Explain evaporation.",
  totalMarks: 2,
  modelAnswer: "Energy gain.",
  archiveState: "ACTIVE" as const,
  markingComponents: [
    { description: "Explains energy", marks: 2, keywords: ["energy"] },
  ],
  keywords: ["evaporation"],
};

describe("question bank service", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    sessionStorage.clear();
    localStorage.clear();
  });

  it("serializes combined filters and the Tutor bearer token", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(response), { status: 200 }),
    );

    await expect(
      fetchTutorQuestions({
        topicId: 14,
        questionType: "OPEN_ENDED",
        archiveState: "ARCHIVED",
        search: "  Evaporation & state  ",
        page: 1,
        size: 12,
      }),
    ).resolves.toEqual(response);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/questions?page=1&size=12&topicId=14&questionType=OPEN_ENDED&archiveState=ARCHIVED&search=Evaporation+%26+state",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
          Accept: "application/json",
        }),
      }),
    );
  });

  it("uses the documented default page and size", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ ...response, page: 0, size: 25 }), {
        status: 200,
      }),
    );
    await fetchTutorQuestions();
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/questions?page=0&size=25",
      expect.anything(),
    );
  });

  it("rejects invalid client filters before issuing an API request", async () => {
    await expect(fetchTutorQuestions({ topicId: 0 })).rejects.toMatchObject({
      name: "QuestionApiError",
      status: 400,
    } satisfies Partial<QuestionApiError>);
    await expect(fetchTutorQuestions({ size: 101 })).rejects.toMatchObject({
      status: 400,
    });
    await expect(
      fetchTutorQuestions({ questionType: "INVALID" as never }),
    ).rejects.toMatchObject({ status: 400 });
    await expect(
      fetchTutorQuestions({ search: "x".repeat(121) }),
    ).rejects.toMatchObject({ status: 400 });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("runtime-validates shallow question page payloads", () => {
    expect(parseQuestionBankPage(response)).toEqual(response);
    expect(() =>
      parseQuestionBankPage({
        ...response,
        items: [{ ...response.items[0], totalMarks: 0 }],
      }),
    ).toThrow("invalid question page");
    expect(() =>
      parseQuestionBankPage({
        ...response,
        items: [{ ...response.items[0], syllabusTopic: { id: 14 } }],
      }),
    ).toThrow("invalid question page");
    expect(() =>
      parseQuestionBankPage({ ...response, hasNext: "false" }),
    ).toThrow("invalid question page");
  });

  it("keeps structured errors from the service", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          message: "Question filters are invalid",
          fields: { page: "must be zero or greater" },
        }),
        { status: 400, headers: { "content-type": "application/json" } },
      ),
    );
    await expect(fetchTutorQuestions()).rejects.toMatchObject({
      status: 400,
      fields: { page: "must be zero or greater" },
    });
  });

  it("loads and saves the full Tutor-only question payload", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify(detailResponse), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(detailResponse), { status: 201 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(detailResponse), { status: 200 }),
      );
    await expect(fetchTutorQuestion(7)).resolves.toEqual(detailResponse);
    await expect(createTutorQuestion(mutation)).resolves.toEqual(
      detailResponse,
    );
    await expect(updateTutorQuestion(7, mutation)).resolves.toEqual(
      detailResponse,
    );
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8083/api/learning/tutor/questions/7",
      expect.objectContaining({ headers: expect.anything() }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8083/api/learning/tutor/questions",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(mutation),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8083/api/learning/tutor/questions/7",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify(mutation),
      }),
    );
  });

  it("rejects malformed full payloads and invalid mutation values", async () => {
    expect(parseTutorQuestion(detailResponse)).toEqual(detailResponse);
    expect(() =>
      parseTutorQuestion({ ...detailResponse, modelAnswer: "" }),
    ).toThrow("invalid question");
    await expect(
      createTutorQuestion({ ...mutation, markingComponents: [] }),
    ).rejects.toMatchObject({ status: 400 });
    await expect(updateTutorQuestion(0, mutation)).rejects.toMatchObject({
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("calls the Tutor-only rule-check endpoint without persisting a score", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    const result = {
      awardedMarks: 2,
      maximumMarks: 2,
      matchedKeywords: ["Explains energy gain"],
      missingKeywords: [],
      explanation: "Matched 1 of 1 weighted marking components.",
      componentResults: [
        {
          position: 0,
          description: "Explains energy gain",
          maximumMarks: 2,
          matched: true,
          matchedTargets: ["energy gain"],
          missingTargets: [],
          feedback: "Matched an approved component keyword.",
        },
      ],
    };
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(result), { status: 200 }),
    );

    await expect(
      checkTutorQuestionAnswer(7, "Water gains energy."),
    ).resolves.toEqual(result);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8082/api/grading/tutor/questions/7/rule-check",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
        body: JSON.stringify({ answer: "Water gains energy." }),
      }),
    );
  });

  it("keeps worksheet-draft question selections locally and de-duplicates retries", () => {
    expect(isQuestionInWorksheetDraft(7)).toBe(false);
    expect(addQuestionToWorksheetDraft(7)).toEqual({ ids: [7], added: true });
    expect(addQuestionToWorksheetDraft(7)).toEqual({ ids: [7], added: false });
    expect(isQuestionInWorksheetDraft(7)).toBe(true);
    expect(() => addQuestionToWorksheetDraft(0)).toThrow(
      "Question reference is invalid",
    );
  });

  it("reports browser storage failures without throwing from a worksheet selection", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementationOnce(() => {
      throw new Error("Storage blocked");
    });
    expect(addQuestionToWorksheetDraft(7)).toEqual({
      ids: [],
      added: false,
      storageUnavailable: true,
    });
    expect(isQuestionInWorksheetDraft(7)).toBe(false);
  });

  it("uploads only supported import files and keeps reviewed drafts on Tutor endpoints", async () => {
    const file = new File(["scan"], "scan.png", { type: "image/png" });
    const batch = {
      id: 12,
      status: "READY_FOR_REVIEW",
      originalFilename: "Question import",
      candidates: [
        {
          id: 31,
          number: 1,
          status: "UNCERTAIN",
          confidence: 0,
          warningMessage: "Review",
          code: null,
          syllabusTopicId: null,
          prompt: "",
          modelAnswer: "",
          totalMarks: 1,
          questionType: "OPEN_ENDED",
          difficulty: "FOUNDATION",
          suggestedTags: "image-import",
          includeSourceImage: true,
          source: { pageId: 22, filename: "scan.png", pageNumber: 1 },
        },
      ],
    };
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify(batch), { status: 201 }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            ...batch.candidates[0],
            prompt: "Explain evaporation.",
            modelAnswer: "Gains heat.",
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ questionIds: [90], message: "Pending review." }),
          { status: 200 },
        ),
      );

    await expect(uploadQuestionImport([file])).resolves.toEqual(batch);
    await expect(
      updateQuestionImportCandidate(12, 31, {
        prompt: "Explain evaporation.",
        modelAnswer: "Gains heat.",
        syllabusTopicId: 14,
        totalMarks: 1,
        questionType: "OPEN_ENDED",
        difficulty: "FOUNDATION",
        includeSourceImage: true,
      }),
    ).resolves.toMatchObject({ id: 31 });
    await expect(importQuestionImportCandidates(12, [31])).resolves.toEqual({
      questionIds: [90],
      message: "Pending review.",
    });
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8083/api/learning/tutor/question-imports",
      expect.objectContaining({ method: "POST", body: expect.any(FormData) }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8083/api/learning/tutor/question-imports/12/candidates/31",
      expect.objectContaining({ method: "PUT" }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8083/api/learning/tutor/question-imports/12/import",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ candidateIds: [31] }),
      }),
    );
    await expect(
      uploadQuestionImport([
        new File(["x"], "not-supported.gif", { type: "image/gif" }),
      ]),
    ).rejects.toMatchObject({ status: 400 });
  });

  it("keeps review workflow mutations batch-scoped and explicit", async () => {
    const batch = {
      id: 12,
      status: "READY_FOR_REVIEW",
      originalFilename: "Question import",
      candidates: [
        {
          id: 31,
          number: 1,
          status: "READY_FOR_REVIEW",
          confidence: 95,
          warningMessage: null,
          code: null,
          syllabusTopicId: null,
          prompt: "Explain evaporation.",
          modelAnswer: "Gains heat.",
          totalMarks: 1,
          questionType: "OPEN_ENDED",
          difficulty: "FOUNDATION",
          suggestedTags: "water",
          includeSourceImage: true,
          source: { pageId: 22, filename: "scan.png", pageNumber: 1 },
          diagramCrops: [],
          suggestions: {
            prompt: "Explain evaporation.",
            modelAnswer: "Gains heat.",
            totalMarks: 1,
            questionType: "OPEN_ENDED",
            difficulty: "FOUNDATION",
            tags: "water",
          },
          confidenceByField: {
            prompt: 95,
            modelAnswer: null,
            classification: 90,
            marks: null,
          },
          lineage: {
            parentCandidateId: null,
            supersededByCandidateId: null,
            rejectionReason: null,
            isRejected: false,
          },
          duplicateWarnings: [
            {
              target: {
                kind: "QUESTION_BANK",
                candidateId: null,
                questionId: 77,
                label: "Question Bank item SCI-WATER-77",
              },
              signals: [
                {
                  code: "NORMALIZED_PROMPT_SIMILARITY",
                  strength: 100,
                  detail: "The normalized question text matches exactly.",
                },
                {
                  code: "MATCHING_MARKS_TYPE_TOPIC",
                  strength: 70,
                  detail:
                    "Marks, reviewed question type, and syllabus topic all match.",
                },
              ],
              strongestSignal: 100,
            },
          ],
        },
        {
          id: 32,
          number: 2,
          status: "READY_FOR_REVIEW",
          confidence: 95,
          warningMessage: null,
          code: null,
          syllabusTopicId: null,
          prompt: "Explain evaporation.",
          modelAnswer: "Gains heat.",
          totalMarks: 1,
          questionType: "OPEN_ENDED",
          difficulty: "FOUNDATION",
          suggestedTags: "water",
          includeSourceImage: true,
          source: { pageId: 22, filename: "scan.png", pageNumber: 1 },
          diagramCrops: [],
        },
      ],
    };
    vi.mocked(fetch).mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify(batch), { status: 200 })),
    );

    await rejectQuestionImportCandidate(12, 31, "Duplicate scan");
    await restoreQuestionImportCandidate(12, 31);
    await moveQuestionImportDiagramCrops(12, [71], 32);
    await mergeQuestionImportCandidates(12, 31, [31, 32]);
    await splitQuestionImportCandidate(12, 31, [
      { prompt: "Part A", modelAnswer: "A" },
      { prompt: "Part B", modelAnswer: "B" },
    ]);

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8083/api/learning/tutor/question-imports/12/candidates/31/reject",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ reason: "Duplicate scan" }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8083/api/learning/tutor/question-imports/12/candidates/31/restore",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8083/api/learning/tutor/question-imports/12/diagram-crops/move",
      expect.objectContaining({
        body: JSON.stringify({ cropIds: [71], targetCandidateId: 32 }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      4,
      "http://localhost:8083/api/learning/tutor/question-imports/12/candidates/merge",
      expect.objectContaining({
        body: JSON.stringify({ targetCandidateId: 31, candidateIds: [31, 32] }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      5,
      "http://localhost:8083/api/learning/tutor/question-imports/12/candidates/31/split",
      expect.objectContaining({
        body: JSON.stringify({
          drafts: [
            { prompt: "Part A", modelAnswer: "A" },
            { prompt: "Part B", modelAnswer: "B" },
          ],
        }),
      }),
    );
  });
});
