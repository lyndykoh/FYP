import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  StudentDashboardApiError,
  fetchStudentDashboard,
  parseStudentDashboard,
} from "./student-dashboard";

const dashboard = {
  studentName: "Asha Lee",
  timeZone: "Asia/Singapore",
  today: "2026-08-28",
  metrics: {
    overallMastery: 72.5,
    trackedTopicCount: 4,
    totalAttempts: 9,
    approvedAssignmentCount: 2,
  },
  latestAssignment: {
    worksheetId: 17,
    assignmentType: "CLASS" as const,
    assignedAt: "2026-08-28T09:30:00",
    dueAt: "2026-09-02T23:59:00",
  },
  nextAssignment: {
    worksheetId: 19,
    assignmentType: "STUDENT" as const,
    assignedAt: "2026-08-28T10:00:00",
    dueAt: null,
  },
  strongestTopic: {
    topicId: 3,
    topicName: "Plants",
    score: 84,
    status: "IMPROVING" as const,
    attemptCount: 3,
    calculatedAt: "2026-08-28T08:00:00",
  },
  focusTopic: {
    topicId: 8,
    topicName: "Forces",
    score: 42,
    status: "LEARNING" as const,
    attemptCount: 2,
    calculatedAt: "2026-08-28T08:00:00",
  },
  latestApprovedTopicResult: {
    topicId: 8,
    topicName: "Forces",
    approvedMarks: 3,
    availableMarks: 5,
    reviewedAt: "2026-08-28T09:00:00",
  },
};

describe("Student dashboard service", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.clear();
  });

  it("requests only the self-scoped endpoint with the browser bearer token", async () => {
    localStorage.setItem("jwt_token", "student-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(dashboard), { status: 200 }),
    );

    await expect(fetchStudentDashboard()).resolves.toEqual(dashboard);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringMatching(
        /^http:\/\/localhost:8083\/api\/learning\/student\/dashboard\?timeZone=/,
      ),
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: "application/json",
          Authorization: "Bearer student-token",
        }),
      }),
    );
  });

  it("preserves structured server errors for a recoverable UI", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ message: "Dashboard temporarily unavailable." }),
        { status: 503 },
      ),
    );

    await expect(fetchStudentDashboard()).rejects.toMatchObject({
      name: "StudentDashboardApiError",
      status: 503,
      message: "Dashboard temporarily unavailable.",
    } satisfies Partial<StudentDashboardApiError>);
  });

  it("accepts partial and new-student data, but rejects malformed canonical data", () => {
    expect(
      parseStudentDashboard({
        ...dashboard,
        latestAssignment: null,
        nextAssignment: null,
        strongestTopic: null,
        focusTopic: null,
        latestApprovedTopicResult: null,
      }),
    ).toMatchObject({ studentName: "Asha Lee" });
    expect(
      parseStudentDashboard({
        ...dashboard,
        metrics: {
          overallMastery: null,
          trackedTopicCount: 0,
          totalAttempts: 0,
          approvedAssignmentCount: 0,
        },
        latestAssignment: null,
        nextAssignment: null,
        strongestTopic: null,
        focusTopic: null,
        latestApprovedTopicResult: null,
      }),
    ).toMatchObject({ metrics: { trackedTopicCount: 0 } });
    expect(() =>
      parseStudentDashboard({
        ...dashboard,
        metrics: { ...dashboard.metrics, approvedAssignmentCount: -1 },
      }),
    ).toThrow("student dashboard response is invalid");
    expect(() =>
      parseStudentDashboard({
        ...dashboard,
        latestAssignment: {
          ...dashboard.latestAssignment,
          assignmentType: "UNKNOWN",
        },
      }),
    ).toThrow("student dashboard response is invalid");
    expect(() =>
      parseStudentDashboard({
        ...dashboard,
        latestApprovedTopicResult: {
          ...dashboard.latestApprovedTopicResult,
          approvedMarks: 6,
        },
      }),
    ).toThrow("student dashboard response is invalid");
  });
});
