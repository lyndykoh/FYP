import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  StudentApiError,
  createTutorNote,
  createTutorStudent,
  deleteTutorNote,
  fetchTutorNotes,
  fetchTutorStudents,
  fetchAvailableStudentAccounts,
  fetchTutorStudentProfile,
  fetchStudentSelfProfile,
  parseTutorStudent,
  parseTutorStudentProfile,
  parseStudentSelfProfile,
  parseTutorStudents,
  parseAvailableStudentAccounts,
  parseTutorNote,
  parseTutorNotes,
  updateTutorNote,
  updateTutorStudent,
} from "./students";

const students = [
  {
    id: 31,
    tutorId: 7,
    fullName: "Bella Tan",
    loginUserId: null,
    classes: [
      {
        id: 12,
        className: "Primary 5 Science",
        subject: "Science",
        level: "Primary 5",
      },
    ],
    createdAt: "2026-09-02T10:00:00",
    updatedAt: "2026-09-02T10:00:00",
  },
];

const mutation = { fullName: "Bella Tan", classIds: [12] };
const notes = [
  {
    id: 44,
    studentId: 31,
    content: "Check whether revision support helped.",
    createdAt: "2026-09-02T10:00:00",
    updatedAt: "2026-09-02T11:00:00",
  },
];

const profile = {
  id: 31,
  fullName: "Bella Tan",
  classes: [
    {
      id: 12,
      className: "Primary 5 Science",
      subject: "Science",
      level: "Primary 5",
      status: "ACTIVE",
    },
  ],
  metrics: {
    averageMastery: 68,
    topicCount: 2,
    totalAttempts: 6,
    lastCalculatedAt: "2026-09-02T10:00:00",
  },
  mastery: [
    {
      topicId: 41,
      topicCode: "SCI-P5-01",
      topicName: "Adaptation",
      score: 68,
      status: "IMPROVING",
      attemptCount: 4,
      calculatedAt: "2026-09-02T10:00:00",
    },
  ],
  learningProfile: {
    strengths: [
      { topicId: 42, topicName: "Energy", score: 86, status: "MASTERED" },
    ],
    focusAreas: [
      { topicId: 41, topicName: "Adaptation", score: 68, status: "IMPROVING" },
    ],
  },
  history: [
    {
      topicId: 41,
      topicName: "Adaptation",
      previousScore: 62,
      newScore: 68,
      previousStatus: "LEARNING",
      newStatus: "IMPROVING",
      reason: "Approved marking",
      occurredAt: "2026-09-02T10:00:00",
    },
  ],
  worksheets: [
    {
      worksheetId: 9,
      title: "Adaptation practice",
      assignmentType: "CLASS",
      classId: 12,
      assignedAt: "2026-09-01T10:00:00",
      dueAt: "2026-09-08T10:00:00",
    },
  ],
  tutorOnly: {
    activeAlerts: [
      {
        id: 3,
        type: "MASTERY",
        severity: "MEDIUM",
        status: "OPEN",
        title: "Adaptation needs practice",
        createdAt: "2026-09-02T10:00:00",
      },
    ],
    reports: [
      {
        id: 6,
        reportCode: "P5-SEP",
        status: "DRAFT",
        periodStart: "2026-09-01",
        periodEnd: "2026-09-30",
        generatedAt: null,
        finalizedAt: null,
      },
    ],
    approvedWorksheetCount: 1,
  },
};

const legacyStudentProfile = {
  id: 32,
  fullName: "Legacy Student",
  classes: [],
  metrics: {
    averageMastery: null,
    topicCount: 1,
    totalAttempts: 0,
    lastCalculatedAt: null,
  },
  mastery: [
    {
      topicId: 43,
      topicCode: "SCI-P5-02",
      topicName: "Energy",
      score: 0,
      status: "NOT_STARTED",
      attemptCount: 0,
      calculatedAt: null,
    },
  ],
  learningProfile: { strengths: [], focusAreas: [] },
  history: [],
  worksheets: [],
  tutorOnly: { activeAlerts: [], reports: [], approvedWorksheetCount: 0 },
} as const;

const newStudentProfile = {
  ...legacyStudentProfile,
  id: 33,
  fullName: "New Student",
  metrics: {
    averageMastery: null,
    topicCount: 0,
    totalAttempts: 0,
    lastCalculatedAt: null,
  },
  mastery: [],
} as const;

describe("tutor student service", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("requests the owner-scoped student list with the stored bearer token", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(students), { status: 200 }),
    );

    await expect(fetchTutorStudents()).resolves.toEqual(students);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/students",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("searches available Student accounts by a server-side query and validates their safe projection", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    const accounts = [
      {
        id: 71,
        fullName: "Bella Tan",
        email: "bella.tan@email.com",
        level: "Primary 6",
      },
    ];
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(accounts), { status: 200 }),
    );

    await expect(fetchAvailableStudentAccounts("bella@")).resolves.toEqual(
      accounts,
    );
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/student-accounts?search=bella%40",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
    expect(parseAvailableStudentAccounts(accounts)).toEqual(accounts);
    expect(() =>
      parseAvailableStudentAccounts([
        { id: 0, fullName: "Bella", email: "bella@example.com", level: null },
      ]),
    ).toThrow("invalid Student account list");
  });

  it("rejects failed owner-scoped requests with the server message", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: "Tutor access is required" }), {
        status: 403,
        headers: { "content-type": "application/json" },
      }),
    );

    await expect(fetchTutorStudents()).rejects.toMatchObject({
      status: 403,
      message: "Tutor access is required",
    });
  });

  it("runtime-validates student and membership response fields", () => {
    expect(parseTutorStudents(students)).toEqual(students);
    expect(() => parseTutorStudents([{ ...students[0], id: 0 }])).toThrow(
      "invalid student list",
    );
    expect(() =>
      parseTutorStudents([
        {
          ...students[0],
          classes: [{ id: 12, className: "P5", subject: "Science" }],
        },
      ]),
    ).toThrow("invalid student list");
    expect(() =>
      parseTutorStudent({ ...students[0], createdAt: null }),
    ).toThrow("invalid student");
    expect(() => parseTutorStudents({ students })).toThrow(
      "invalid student list",
    );
  });

  it("creates a student with unique class memberships in the JSON request", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(students[0]), { status: 201 }),
    );

    await expect(createTutorStudent(mutation)).resolves.toEqual(students[0]);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/students",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(mutation),
        headers: expect.objectContaining({
          "Content-Type": "application/json",
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("updates a selected student and rejects invalid references before a request", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(students[0]), { status: 200 }),
    );
    await expect(updateTutorStudent(31, mutation)).resolves.toEqual(
      students[0],
    );
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/students/31",
      expect.objectContaining({ method: "PUT" }),
    );

    vi.mocked(fetch).mockClear();
    await expect(updateTutorStudent(0, mutation)).rejects.toMatchObject({
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("preserves structured validation, duplicate membership, and wrong-owner errors", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          message: "Each class can be selected only once",
          fields: { classIds: "Remove the duplicate class." },
        }),
        { status: 400, headers: { "content-type": "application/json" } },
      ),
    );
    await expect(
      createTutorStudent({ ...mutation, classIds: [12, 12] }),
    ).rejects.toMatchObject({
      name: "StudentApiError",
      fields: { classIds: "Remove the duplicate class." },
    } satisfies Partial<StudentApiError>);

    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          message: "Student 31 was not found for this tutor",
          fields: {},
        }),
        { status: 404, headers: { "content-type": "application/json" } },
      ),
    );
    await expect(updateTutorStudent(31, mutation)).rejects.toMatchObject({
      status: 404,
      message: "Student 31 was not found for this tutor",
    });
  });

  it("does not trust malformed successful mutation responses", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ id: 31 }), { status: 200 }),
    );
    await expect(createTutorStudent(mutation)).rejects.toThrow(
      "invalid student",
    );
  });

  it("requests the canonical owner-scoped profile and validates the full response", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(profile), { status: 200 }),
    );

    await expect(fetchTutorStudentProfile(31)).resolves.toEqual(profile);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/students/31/profile",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("rejects invalid profile references and malformed canonical profile data", async () => {
    await expect(fetchTutorStudentProfile(0)).rejects.toMatchObject({
      status: 400,
    });
    expect(fetch).not.toHaveBeenCalled();
    expect(parseTutorStudentProfile(profile)).toEqual(profile);
    expect(() =>
      parseTutorStudentProfile({
        ...profile,
        metrics: { ...profile.metrics, averageMastery: 120 },
      }),
    ).toThrow("invalid student profile");
    expect(() =>
      parseTutorStudentProfile({
        ...profile,
        tutorOnly: { activeAlerts: [], reports: [{ id: 6 }] },
      }),
    ).toThrow("invalid student profile");
  });

  it("accepts canonical NOT_STARTED mastery and explicit null/empty fields for a new profile", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify(newStudentProfile), { status: 200 }),
    );
    await expect(fetchTutorStudentProfile(33)).resolves.toEqual(
      newStudentProfile,
    );
    expect(parseTutorStudentProfile(newStudentProfile)).toEqual(
      newStudentProfile,
    );

    expect(parseTutorStudentProfile(legacyStudentProfile)).toEqual(
      legacyStudentProfile,
    );
    const selfProfile = { ...legacyStudentProfile, tutorOnly: null };
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(JSON.stringify(selfProfile), { status: 200 }),
    );
    await expect(fetchStudentSelfProfile()).resolves.toEqual(selfProfile);
    expect(parseStudentSelfProfile(selfProfile)).toEqual(selfProfile);
  });

  it("preserves missing and cross-owner profile errors", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ message: "Student profile was not found" }),
        { status: 404 },
      ),
    );
    await expect(fetchTutorStudentProfile(31)).rejects.toMatchObject({
      status: 404,
      message: "Student profile was not found",
    });
  });

  it("loads only the authenticated student's profile and rejects tutor-only response data", async () => {
    localStorage.setItem("jwt_token", "student-token");
    const selfProfile = { ...profile, tutorOnly: null };
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(selfProfile), { status: 200 }),
    );

    await expect(fetchStudentSelfProfile()).resolves.toEqual(selfProfile);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/student/profile",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer student-token",
        }),
      }),
    );
    expect(parseStudentSelfProfile(selfProfile)).toEqual(selfProfile);
    expect(() => parseStudentSelfProfile(profile)).toThrow(
      "invalid student profile",
    );
  });

  it("preserves an unlinked student profile response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ message: "Student profile was not found" }),
        { status: 404 },
      ),
    );
    await expect(fetchStudentSelfProfile()).rejects.toMatchObject({
      status: 404,
      message: "Student profile was not found",
    });
  });

  it("lists and mutates tutor-only notes through owner-scoped endpoints", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify(notes), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(notes[0]), { status: 201 }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ ...notes[0], content: "Updated follow-up" }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(fetchTutorNotes(31)).resolves.toEqual(notes);
    await expect(
      createTutorNote(31, { content: notes[0].content }),
    ).resolves.toEqual(notes[0]);
    await expect(
      updateTutorNote(31, 44, { content: "Updated follow-up" }),
    ).resolves.toMatchObject({ content: "Updated follow-up" });
    await expect(deleteTutorNote(31, 44)).resolves.toBeUndefined();
    expect(fetch).toHaveBeenNthCalledWith(
      1,
      "http://localhost:8083/api/learning/tutor/students/31/notes",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer stored-token",
        }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      "http://localhost:8083/api/learning/tutor/students/31/notes",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ content: notes[0].content }),
      }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      3,
      "http://localhost:8083/api/learning/tutor/students/31/notes/44",
      expect.objectContaining({ method: "PUT" }),
    );
    expect(fetch).toHaveBeenNthCalledWith(
      4,
      "http://localhost:8083/api/learning/tutor/students/31/notes/44",
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("runtime-validates tutor note payloads and rejects invalid references before requesting", async () => {
    expect(parseTutorNotes(notes)).toEqual(notes);
    expect(parseTutorNote(notes[0])).toEqual(notes[0]);
    expect(() => parseTutorNotes([{ ...notes[0], content: "" }])).toThrow(
      "invalid tutor note list",
    );
    expect(() => parseTutorNote({ ...notes[0], updatedAt: null })).toThrow(
      "invalid tutor note",
    );
    await expect(fetchTutorNotes(0)).rejects.toMatchObject({ status: 400 });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("preserves tutor-note validation and owner-scoped errors", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          message: "Tutor note was not found",
          fields: { content: "Note content is required" },
        }),
        { status: 404, headers: { "content-type": "application/json" } },
      ),
    );
    await expect(
      updateTutorNote(31, 44, { content: "follow up" }),
    ).rejects.toMatchObject({
      status: 404,
      message: "Tutor note was not found",
      fields: { content: "Note content is required" },
    });
  });
});
