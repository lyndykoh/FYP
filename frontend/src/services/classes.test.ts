import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  addStudentToTutorClass,
  ClassApiError,
  createTutorClass,
  fetchEligibleClassStudents,
  fetchTutorClassDetail,
  fetchTutorClassInsights,
  fetchTutorClasses,
  parseTutorClass,
  parseTutorClassDetail,
  parseTutorClassInsights,
  parseTutorClasses,
  parseEligibleClassStudents,
  removeStudentFromTutorClass,
  updateTutorClass,
} from "./classes";

const classes = [
  {
    id: 12,
    tutorId: 7,
    className: "Primary 5 Science",
    subject: "Science",
    level: "Primary 5",
    status: "ACTIVE" as const,
    schedules: [{ dayOfWeek: "MONDAY", startTime: "16:00", endTime: "17:30" }],
  },
];

const mutation = {
  className: "Primary 5 Science",
  subject: "Science",
  level: "Primary 5",
  status: "ACTIVE" as const,
  schedules: [
    { dayOfWeek: "MONDAY" as const, startTime: "16:00", endTime: "17:30" },
  ],
};

const detail = {
  ...classes[0],
  students: [
    {
      id: 80,
      fullName: "Bella Tan",
      overallMastery: 68,
      masteryRecordCount: 4,
    },
  ],
  mastery: { averageScore: 68, recordCount: 4, studentsWithMastery: 1 },
  weakAreas: [
    {
      topicId: 41,
      topicName: "Adaptation",
      averageScore: 45,
      affectedStudentCount: 1,
    },
  ],
  insight: {
    status: "REFRESHING" as const,
    message: "Insights are being refreshed",
  },
  worksheets: [
    {
      id: 31,
      title: "P5 Science — Adaptation Mini Test",
      status: "APPROVED" as const,
      dueAt: "2026-09-15T23:59:00+08:00",
      assignedAt: "2026-09-01T10:00:00+08:00",
    },
  ],
};

const insights = {
  status: "FRESH" as const,
  message: "Insights are current",
  dataAsOf: "2026-09-02T10:00:00",
  items: [
    {
      topicId: 41,
      topicName: "Adaptation",
      averageMasteryPercent: 45,
      activeStudentCount: 4,
      assessedStudentCount: 4,
      affectedStudentCount: 3,
      weak: true,
      suggestedAction: "Prioritise guided practice before the next assessment.",
      displayRank: 1,
      rankingNote: "Prioritise before Friday.",
    },
  ],
  feedback: [
    {
      id: 6,
      feedback: "Revisit keywords first.",
      createdAt: "2026-09-02T10:30:00",
    },
  ],
};

const eligibleStudents = [
  {
    loginUserId: 91,
    fullName: "Ada Learner",
    email: "ada@example.com",
    level: "Primary 5",
  },
];

describe("tutor class service", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("requests the Tutor class list with the stored bearer token", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(classes), { status: 200 }),
    );

    await expect(fetchTutorClasses()).resolves.toEqual(classes);

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes",
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: "application/json",
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("rejects a failed request with a recoverable message", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: "Tutor access is required" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(fetchTutorClasses()).rejects.toThrow(
      "Tutor access is required",
    );
  });

  it("runtime-validates every required class and schedule field", () => {
    expect(parseTutorClasses(classes)).toEqual(classes);
    expect(() => parseTutorClasses([{ ...classes[0], id: 0 }])).toThrow(
      "invalid class list",
    );
    expect(() =>
      parseTutorClasses([{ ...classes[0], status: "ARCHIVED" }]),
    ).toThrow("invalid class list");
    expect(() =>
      parseTutorClasses([
        {
          ...classes[0],
          schedules: [
            { dayOfWeek: "MONDAY", startTime: 1600, endTime: "17:30" },
          ],
        },
      ]),
    ).toThrow("invalid class list");
    expect(() => parseTutorClasses({ classes })).toThrow("invalid class list");
  });

  it("surfaces an invalid successful response instead of trusting it", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify([{ id: 12 }]), { status: 200 }),
    );

    await expect(fetchTutorClasses()).rejects.toThrow("invalid class list");
  });

  it("requests a single owner-scoped class detail with the stored bearer token", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(detail), { status: 200 }),
    );

    await expect(fetchTutorClassDetail(12)).resolves.toEqual(detail);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes/12",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("preserves missing and wrong-owner detail responses", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "CLASS_NOT_FOUND",
          message: "Class 12 was not found for this tutor",
          fields: {},
        }),
        { status: 404, headers: { "content-type": "application/json" } },
      ),
    );

    await expect(fetchTutorClassDetail(12)).rejects.toMatchObject({
      status: 404,
      message: "Class 12 was not found for this tutor",
    });
  });

  it("preserves an explicit forbidden detail response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "CLASS_FORBIDDEN",
          message: "Class access is forbidden",
          fields: {},
        }),
        { status: 403, headers: { "content-type": "application/json" } },
      ),
    );

    await expect(fetchTutorClassDetail(12)).rejects.toMatchObject({
      status: 403,
      message: "Class access is forbidden",
    });
  });

  it("retrieves the server-filtered eligible Student account list", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(eligibleStudents), { status: 200 }),
    );

    await expect(fetchEligibleClassStudents(12)).resolves.toEqual(
      eligibleStudents,
    );
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes/12/eligible-students",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
    expect(
      parseEligibleClassStudents([{ ...eligibleStudents[0], level: null }]),
    ).toHaveLength(1);
    expect(() =>
      parseEligibleClassStudents([{ ...eligibleStudents[0], email: "" }]),
    ).toThrow("invalid eligible student list");
  });

  it("adds and removes a Student through owner-scoped membership endpoints", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ id: 80, loginUserId: 91, fullName: "Ada Learner" }),
          { status: 201 },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(addStudentToTutorClass(12, 91)).resolves.toBeUndefined();
    await expect(removeStudentFromTutorClass(12, 80)).resolves.toBeUndefined();

    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8083/api/learning/tutor/classes/12/students",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ loginUserId: 91 }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8083/api/learning/tutor/classes/12/students/80",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("requests and runtime-validates the owner-scoped persisted insight snapshot", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(insights), { status: 200 }),
    );

    await expect(fetchTutorClassInsights(12)).resolves.toEqual(insights);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes/12/insights",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("runtime-validates full and partial class detail data", () => {
    expect(parseTutorClassDetail(detail)).toEqual(detail);
    expect(
      parseTutorClassDetail({
        ...detail,
        students: [],
        mastery: { averageScore: null, recordCount: 0, studentsWithMastery: 0 },
        weakAreas: [],
        worksheets: [],
      }),
    ).toMatchObject({ students: [], weakAreas: [], worksheets: [] });
    expect(
      parseTutorClassDetail({
        ...detail,
        students: [{ id: 80, fullName: "No Mastery", masteryRecordCount: 0 }],
        mastery: { recordCount: 0, studentsWithMastery: 0 },
        worksheets: [{ id: 31, title: "No due date", status: "APPROVED" }],
      }),
    ).toMatchObject({
      students: [{ overallMastery: null }],
      mastery: { averageScore: null },
      worksheets: [{ assignedAt: null, dueAt: null }],
    });
    expect(() =>
      parseTutorClassDetail({
        ...detail,
        insight: { status: "READY", message: "No" },
      }),
    ).toThrow("invalid class details");
    expect(() =>
      parseTutorClassDetail({
        ...detail,
        weakAreas: [{ ...detail.weakAreas[0], averageScore: 101 }],
      }),
    ).toThrow("invalid class details");
  });

  it("rejects malformed insight evidence and impossible ranking metadata", () => {
    expect(parseTutorClassInsights(insights)).toEqual(insights);
    expect(
      parseTutorClassInsights({ ...insights, status: "STALE", dataAsOf: null }),
    ).toMatchObject({ status: "STALE" });
    expect(() =>
      parseTutorClassInsights({
        ...insights,
        items: [{ ...insights.items[0], averageMasteryPercent: 101 }],
      }),
    ).toThrow("invalid class insights");
    expect(() =>
      parseTutorClassInsights({
        ...insights,
        items: [{ ...insights.items[0], displayRank: 0 }],
      }),
    ).toThrow("invalid class insights");
    expect(() =>
      parseTutorClassInsights({
        ...insights,
        feedback: [{ id: 0, feedback: "No", createdAt: "today" }],
      }),
    ).toThrow("invalid class insights");
  });

  it("rejects invalid class references before making a request", async () => {
    await expect(fetchTutorClassDetail(0)).rejects.toMatchObject({
      status: 400,
    });
    await expect(fetchTutorClassInsights(0)).rejects.toMatchObject({
      status: 400,
    });
    await expect(fetchEligibleClassStudents(0)).rejects.toMatchObject({
      status: 400,
    });
    await expect(addStudentToTutorClass(12, 0)).rejects.toMatchObject({
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("creates a class with the stored Tutor bearer token and complete JSON body", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(classes[0]), { status: 201 }),
    );

    await expect(createTutorClass(mutation)).resolves.toEqual(classes[0]);

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(mutation),
        headers: expect.objectContaining({
          Accept: "application/json",
          "Content-Type": "application/json",
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("updates the selected class through the owner-scoped endpoint", async () => {
    const updated = { ...classes[0], className: "Primary 5 Advanced Science" };
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(updated), { status: 200 }),
    );

    await expect(
      updateTutorClass(12, { ...mutation, className: updated.className }),
    ).resolves.toEqual(updated);

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/classes/12",
      expect.objectContaining({ method: "PUT" }),
    );
  });

  it("preserves structured server rejections, including a wrong-owner response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "CLASS_NOT_FOUND",
          message: "Class 12 was not found for this tutor",
          fields: {},
        }),
        { status: 404, headers: { "content-type": "application/json" } },
      ),
    );

    await expect(updateTutorClass(12, mutation)).rejects.toMatchObject({
      name: "ClassApiError",
      status: 404,
      message: "Class 12 was not found for this tutor",
    } satisfies Partial<ClassApiError>);
  });

  it("exposes structured validation field errors to the form", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "VALIDATION_FAILED",
          message: "Class request is invalid",
          fields: { className: "must not be blank" },
        }),
        { status: 400, headers: { "content-type": "application/json" } },
      ),
    );

    await expect(createTutorClass(mutation)).rejects.toMatchObject({
      fields: { className: "must not be blank" },
    });
  });

  it("runtime-validates successful mutation responses", () => {
    expect(parseTutorClass(classes[0])).toEqual(classes[0]);
    expect(() => parseTutorClass({ id: 12 })).toThrow("invalid class");
  });
});
