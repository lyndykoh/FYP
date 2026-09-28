import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  DashboardApiError,
  fetchTutorDashboard,
  parseTutorDashboard,
} from "./dashboard";

const dashboard = {
  timeZone: "Asia/Singapore",
  today: "2026-08-25",
  metrics: {
    activeClassCount: 2,
    studentCount: 12,
    pendingReviewCount: 3,
    needsAttentionStudentCount: 2,
    reportsReadyCount: 1,
  },
  todaySchedule: [
    {
      classId: 8,
      className: "Primary 5 Science",
      subject: "Science",
      level: "Primary 5",
      startTime: "16:00:00",
      endTime: "17:30:00",
    },
  ],
  recentActivity: [
    {
      type: "REVIEW_REQUESTED" as const,
      sourceId: 91,
      studentId: 7,
      studentName: "Bella Tan",
      title: "Marking review requested",
      detail: "Submission 91 is pending tutor review.",
      occurredAt: "2026-08-25T08:30:00",
      severity: null,
    },
  ],
};

describe("Tutor dashboard service", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.clear();
  });

  it("requests the owner-scoped dashboard with its bearer token and encoded timezone", async () => {
    localStorage.setItem("jwt_token", "stored-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(dashboard), { status: 200 }),
    );

    await expect(fetchTutorDashboard("Asia/Singapore")).resolves.toEqual(
      dashboard,
    );

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/dashboard?timeZone=Asia%2FSingapore",
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: "application/json",
          Authorization: "Bearer stored-token",
        }),
      }),
    );
  });

  it("uses UTC for an empty timezone instead of sending an invalid client request", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ ...dashboard, timeZone: "UTC" }), {
        status: 200,
      }),
    );

    await fetchTutorDashboard(" ");

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8083/api/learning/tutor/dashboard?timeZone=UTC",
      expect.anything(),
    );
  });

  it("preserves a structured server failure for retry UI", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "DASHBOARD_DATABASE_UNAVAILABLE",
          message: "Dashboard data is temporarily unavailable.",
        }),
        {
          status: 503,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    await expect(fetchTutorDashboard()).rejects.toMatchObject({
      name: "DashboardApiError",
      status: 503,
      message: "Dashboard data is temporarily unavailable.",
    } satisfies Partial<DashboardApiError>);
  });

  it("does not trust malformed successful responses", () => {
    expect(parseTutorDashboard(dashboard)).toEqual(dashboard);
    expect(
      parseTutorDashboard({
        ...dashboard,
        todaySchedule: [],
        recentActivity: [],
      }),
    ).toMatchObject({ todaySchedule: [], recentActivity: [] });
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        metrics: { ...dashboard.metrics, studentCount: -1 },
      }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        recentActivity: [{ ...dashboard.recentActivity[0], type: "UNKNOWN" }],
      }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        todaySchedule: [{ ...dashboard.todaySchedule[0], classId: 0 }],
      }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({ ...dashboard, today: "2026-99-99" }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        todaySchedule: [
          { ...dashboard.todaySchedule[0], startTime: "99:99:00" },
        ],
      }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        todaySchedule: [{ ...dashboard.todaySchedule[0], endTime: "16:00:00" }],
      }),
    ).toThrow("dashboard response is invalid");
    expect(() =>
      parseTutorDashboard({
        ...dashboard,
        recentActivity: [
          { ...dashboard.recentActivity[0], occurredAt: "not-a-timestamp" },
        ],
      }),
    ).toThrow("dashboard response is invalid");
  });
});
