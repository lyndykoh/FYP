import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { TutorDashboard as TutorDashboardData } from "@/services/dashboard";

import TutorDashboard from "./TutorDashboard";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const dashboard: TutorDashboardData = {
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
      type: "ALERT_CREATED",
      sourceId: 101,
      studentId: 7,
      studentName: "Bella Tan",
      title: "Adaptation needs attention",
      detail: "Bella needs practice.",
      occurredAt: "2026-08-25T11:00:00",
      severity: "WARNING",
    },
    {
      type: "REVIEW_REQUESTED",
      sourceId: 91,
      studentId: 7,
      studentName: "Bella Tan",
      title: "Marking review requested",
      detail: "Submission 91 is pending tutor review.",
      occurredAt: "2026-08-25T10:00:00",
      severity: null,
    },
    {
      type: "WORKSHEET_ASSIGNED",
      sourceId: 82,
      studentId: null,
      studentName: null,
      title: "Energy forms revision",
      detail: "Assigned to class 8",
      occurredAt: "2026-08-25T09:00:00",
      severity: null,
    },
  ],
};

describe("TutorDashboard", () => {
  it("shows a skeleton while the owner-scoped dashboard is loading", async () => {
    let resolve!: (value: TutorDashboardData) => void;
    const loadDashboard = vi.fn(
      () =>
        new Promise<TutorDashboardData>((complete) => {
          resolve = complete;
        }),
    );

    render(<TutorDashboard loadDashboard={loadDashboard} timeZone="UTC" />);

    expect(screen.getByTestId("tutor-dashboard-skeleton")).toBeVisible();
    await waitFor(() => expect(loadDashboard).toHaveBeenCalledWith("UTC"));
    resolve(dashboard);
    expect(
      await screen.findByRole("heading", {
        name: "Your teaching day, clearly organised.",
      }),
    ).toBeVisible();
  });

  it("renders every live metric, schedule, and supplied activity order in a responsive grid", async () => {
    render(
      <TutorDashboard
        loadDashboard={async () => dashboard}
        timeZone="Asia/Singapore"
      />,
    );

    expect(await screen.findByText("Active classes")).toBeVisible();
    expect(screen.getByText("12")).toBeVisible();
    expect(screen.getByText("Primary 5 Science")).toBeVisible();
    expect(screen.getByText("16:00–17:30")).toBeVisible();
    expect(screen.getByTestId("dashboard-metric-grid")).toHaveStyle({
      gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
    });

    const activity = screen.getByTestId("dashboard-activity-list");
    expect(
      within(activity)
        .getAllByRole("button")
        .map((item) => item.textContent),
    ).toEqual([
      expect.stringContaining("Adaptation needs attention"),
      expect.stringContaining("Marking review requested"),
      expect.stringContaining("Energy forms revision"),
    ]);
  });

  it("renders an actionable empty state for a new Tutor", async () => {
    const go = vi.fn();
    const empty: TutorDashboardData = {
      ...dashboard,
      metrics: {
        activeClassCount: 0,
        studentCount: 0,
        pendingReviewCount: 0,
        needsAttentionStudentCount: 0,
        reportsReadyCount: 0,
      },
      todaySchedule: [],
      recentActivity: [],
    };
    const user = userEvent.setup();
    render(
      <TutorDashboard
        loadDashboard={async () => empty}
        timeZone="UTC"
        navigate={go}
      />,
    );

    expect(
      await screen.findByRole("heading", { name: "Your dashboard is ready" }),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "No classes scheduled today" }),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Create class" }));
    expect(go).toHaveBeenCalledWith("/classes/new");
  });

  it("keeps partial data useful when no schedule is available", async () => {
    const partial: TutorDashboardData = {
      ...dashboard,
      todaySchedule: [],
      recentActivity: [dashboard.recentActivity[1]],
    };
    render(
      <TutorDashboard loadDashboard={async () => partial} timeZone="UTC" />,
    );

    expect(
      await screen.findByRole("heading", {
        name: "No classes scheduled today",
      }),
    ).toBeVisible();
    expect(screen.getByText("Marking review requested")).toBeVisible();
    expect(screen.queryByText("Energy forms revision")).not.toBeInTheDocument();
  });

  it("provides a retryable error when dashboard data is unavailable", async () => {
    const loadDashboard = vi
      .fn()
      .mockRejectedValueOnce(
        new Error("Dashboard data is temporarily unavailable."),
      )
      .mockResolvedValueOnce(dashboard);
    const user = userEvent.setup();
    render(<TutorDashboard loadDashboard={loadDashboard} timeZone="UTC" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Dashboard data is temporarily unavailable.",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading dashboard" }),
    );
    expect(await screen.findByText("Primary 5 Science")).toBeVisible();
    expect(loadDashboard).toHaveBeenCalledTimes(2);
  });

  it("navigates quick actions, schedules, and activity types to valid application routes", async () => {
    const go = vi.fn();
    const user = userEvent.setup();
    render(
      <TutorDashboard
        loadDashboard={async () => dashboard}
        timeZone="UTC"
        navigate={go}
      />,
    );

    await screen.findByText("Primary 5 Science");
    await user.click(
      screen.getAllByRole("button", { name: "Generate Worksheet" })[0],
    );
    await user.click(screen.getByRole("button", { name: "Upload worksheet" }));
    await user.click(screen.getByRole("button", { name: /Pending review/ }));
    await user.click(screen.getByRole("button", { name: /Primary 5 Science/ }));
    await user.click(
      screen.getByRole("button", {
        name: "Open student: Adaptation needs attention",
      }),
    );
    await user.click(
      screen.getByRole("button", {
        name: "Open review: Marking review requested",
      }),
    );
    await user.click(
      screen.getByRole("button", {
        name: "View worksheets: Energy forms revision",
      }),
    );

    expect(go).toHaveBeenCalledWith("/tutor/worksheets/new");
    expect(go).toHaveBeenCalledWith("/upload");
    expect(go).toHaveBeenCalledWith("/tutor/reviews");
    expect(go).toHaveBeenCalledWith("/classes/8");
    expect(go).toHaveBeenCalledWith("/students/7");
    expect(go).toHaveBeenCalledWith("/tutor/reviews/91");
    expect(go).toHaveBeenCalledWith("/tutor/worksheets");
  });
});
