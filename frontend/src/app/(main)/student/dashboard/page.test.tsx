import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import StudentDashboardPage from "./page";
import {
  fetchStudentDashboard,
  type StudentDashboardData,
} from "@/services/student-dashboard";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));
vi.mock("@/services/student-dashboard", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/services/student-dashboard")>();
  return { ...actual, fetchStudentDashboard: vi.fn() };
});

const dashboard: StudentDashboardData = {
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

describe("Student dashboard page", () => {
  beforeEach(() => {
    navigation.push.mockReset();
    vi.mocked(fetchStudentDashboard).mockReset();
  });

  it("renders canonical metrics, assignment, topic result, and accessible text status", async () => {
    vi.mocked(fetchStudentDashboard).mockResolvedValue(dashboard);
    render(<StudentDashboardPage />);

    expect(
      await screen.findByRole("heading", { name: "Welcome back, Asha Lee." }),
    ).toBeVisible();
    expect(screen.getByText("73%", { exact: true })).toBeVisible();
    expect(screen.getByText("Approved assignments")).toBeVisible();
    expect(screen.getByText(/personal assignment is ready/i)).toBeVisible();
    expect(
      screen.getByText(/topic-level result, not a worksheet score/i),
    ).toBeVisible();
    expect(screen.getByText("Improving")).toBeVisible();
    expect(screen.getByLabelText("Forces mastery 42 percent")).toBeVisible();
    expect(screen.getByTestId("student-dashboard-metric-grid")).toBeVisible();
  });

  it("navigates every metric, dashboard action, and topic drill-down to its canonical destination", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchStudentDashboard).mockResolvedValue(dashboard);
    render(<StudentDashboardPage />);
    await screen.findByRole("heading", { name: "Welcome back, Asha Lee." });

    await user.click(screen.getByRole("button", { name: /Overall mastery/ }));
    await user.click(screen.getByRole("button", { name: /Topics tracked/ }));
    await user.click(screen.getByRole("button", { name: /Approved attempts/ }));
    await user.click(
      screen.getByRole("button", { name: /Approved assignments/ }),
    );
    await user.click(
      screen.getAllByRole("button", { name: "Upload completed work" }).at(-1)!,
    );
    await user.click(screen.getByRole("button", { name: "View my progress" }));
    await user.click(screen.getByRole("button", { name: "View worksheets" }));
    await user.click(screen.getByRole("button", { name: "Review mistakes" }));
    await user.click(screen.getByRole("button", { name: "Open assignment" }));
    await user.click(screen.getByRole("button", { name: "Review topic" }));
    const topicButtons = screen.getAllByRole("button", { name: "Open topic" });
    await user.click(topicButtons[0]);
    await user.click(topicButtons[1]);

    expect(navigation.push.mock.calls.map(([href]) => href)).toEqual([
      "/progress",
      "/topics",
      "/progress",
      "/worksheets",
      "/upload",
      "/progress",
      "/worksheets",
      "/mistakes",
      "/worksheets/19",
      "/topics/8",
      "/topics/3",
      "/topics/8",
    ]);
  });

  it("keeps the metric rail fluid at a mobile viewport", async () => {
    const previousWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: 320,
    });
    vi.mocked(fetchStudentDashboard).mockResolvedValue(dashboard);
    render(<StudentDashboardPage />);

    const grid = await screen.findByTestId("student-dashboard-metric-grid");
    const styles = [...document.querySelectorAll("style")]
      .map((style) => style.textContent ?? "")
      .join("\n");
    expect(grid).toBeVisible();
    expect(styles).toMatch(
      /grid-template-columns:repeat\(auto-fit,\s*minmax\(168px,\s*1fr\)\)/,
    );

    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: previousWidth,
    });
  });

  it("renders loading, partial, and new-student states without inventing progress", async () => {
    let resolve: ((value: StudentDashboardData) => void) | undefined;
    vi.mocked(fetchStudentDashboard).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { rerender } = render(<StudentDashboardPage />);
    expect(screen.getByTestId("student-dashboard-skeleton")).toBeVisible();
    resolve?.({
      ...dashboard,
      latestAssignment: null,
      nextAssignment: null,
      strongestTopic: null,
      focusTopic: null,
      latestApprovedTopicResult: null,
    });
    expect(
      await screen.findByText(/when your tutor assigns approved work/i),
    ).toBeVisible();
    vi.mocked(fetchStudentDashboard).mockResolvedValue({
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
    });
    rerender(<StudentDashboardPage key="new" />);
    expect(
      await screen.findByText("Your learning dashboard is ready"),
    ).toBeVisible();
    expect(screen.getByText("—")).toBeVisible();
  });

  it("offers a retry after a recoverable loading error", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchStudentDashboard)
      .mockRejectedValueOnce(new Error("Service unavailable"))
      .mockResolvedValueOnce(dashboard);
    render(<StudentDashboardPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Service unavailable",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading dashboard" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "Welcome back, Asha Lee." }),
      ).toBeVisible(),
    );
  });
});
