import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import {
  ClassApiError,
  type ClassInsightSnapshot,
  type TutorClassDetail,
} from "@/services/classes";

import ClassDetail from "./ClassDetail";

const detail: TutorClassDetail = {
  id: 12,
  tutorId: 7,
  className: "Primary 5 Science",
  subject: "Science",
  level: "Primary 5",
  status: "ACTIVE",
  schedules: [{ dayOfWeek: "MONDAY", startTime: "16:00", endTime: "17:30" }],
  students: [
    {
      id: 21,
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
  insight: { status: "REFRESHING", message: "Insights are being refreshed" },
  worksheets: [
    {
      id: 31,
      title: "P5 Science — Adaptation Mini Test",
      status: "APPROVED",
      dueAt: "2026-09-15T23:59:00+08:00",
      assignedAt: "2026-09-01T10:00:00+08:00",
    },
  ],
};

const insights: ClassInsightSnapshot = {
  status: "FRESH",
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
      id: 9,
      feedback: "Review keywords before the next worksheet.",
      createdAt: "2026-09-02T10:30:00",
    },
  ],
};

describe("ClassDetail", () => {
  it("shows a loading skeleton before it renders the owner-scoped detail", async () => {
    let resolve!: (value: TutorClassDetail) => void;
    render(
      <ClassDetail
        classId={12}
        loadClass={() =>
          new Promise<TutorClassDetail>((complete) => {
            resolve = complete;
          })
        }
        loadInsights={async () => insights}
      />,
    );
    expect(screen.getByTestId("class-detail-skeleton")).toBeVisible();
    await waitFor(() => expect(resolve).toBeTypeOf("function"));
    await act(async () => {
      resolve(detail);
    });
    expect(
      await screen.findByRole("heading", { name: "Primary 5 Science" }),
    ).toBeVisible();
  });

  it("renders students, mastery, weak areas, persisted insight evidence, worksheets, and responsive sections", async () => {
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => detail}
        loadInsights={async () => insights}
      />,
    );
    expect(await screen.findByText("Bella Tan")).toBeVisible();
    expect(screen.getByText("Tutor account #7")).toBeVisible();
    expect(screen.getAllByText("68%")).toHaveLength(2);
    expect(screen.getAllByText("Adaptation")).toHaveLength(2);
    expect(
      screen.getByRole("heading", { name: "Class insight" }),
    ).toBeVisible();
    expect(screen.getByText("Insights are current")).toBeVisible();
    expect(
      screen.getByText("3 of 4 students need support; average mastery is 45%."),
    ).toBeVisible();
    expect(screen.getByText("TUTOR PRIORITY 1")).toBeVisible();
    expect(
      screen.getByText("Tutor note: Prioritise before Friday."),
    ).toBeVisible();
    expect(
      screen.getByText("Review keywords before the next worksheet."),
    ).toBeVisible();
    expect(screen.getByText("P5 Science — Adaptation Mini Test")).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Class schedule" }),
    ).toBeVisible();
    expect(screen.getByText("Monday")).toBeVisible();
    expect(screen.getByText("16:00–17:30")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Generate Worksheet" }),
    ).toHaveAttribute("href", "/tutor/worksheets/new?classId=12");
    expect(screen.getByRole("link", { name: "Edit class" })).toHaveAttribute(
      "href",
      "/classes/12/edit",
    );
    expect(screen.getByRole("link", { name: "View students" })).toHaveAttribute(
      "href",
      "/students?classId=12",
    );
    expect(
      screen.getByRole("link", { name: "View worksheets" }),
    ).toHaveAttribute("href", "/tutor/worksheets?classId=12");
    expect(
      screen.getByRole("link", { name: "Open Bella Tan's profile" }),
    ).toHaveAttribute("href", "/students/21");
  });

  it("shows actionable empty states for a class without learning data", async () => {
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => ({
          ...detail,
          schedules: [],
          students: [],
          mastery: {
            averageScore: null,
            recordCount: 0,
            studentsWithMastery: 0,
          },
          weakAreas: [],
          worksheets: [],
        })}
        loadInsights={async () => ({ ...insights, items: [] })}
      />,
    );
    expect(await screen.findByText("No students in this class")).toBeVisible();
    expect(screen.getByText("No weak areas identified")).toBeVisible();
    expect(screen.getByText("No worksheets assigned")).toBeVisible();
    expect(screen.getByText("No regular schedule yet")).toBeVisible();
  });

  it("shows an actionable not-found state for a missing class", async () => {
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => {
          throw new ClassApiError("Class 12 was not found", 404);
        }}
        loadInsights={async () => insights}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Class not found",
    );
    expect(screen.getByText(/no longer exists/i)).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Back to classes" }),
    ).toHaveAttribute("href", "/classes");
  });

  it("shows an access-denied state for a class owned by another Tutor", async () => {
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => {
          throw new ClassApiError("Class access is forbidden", 403);
        }}
        loadInsights={async () => insights}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You do not have access to this class",
    );
    expect(
      screen.getByText(/Only the Tutor who owns this class/i),
    ).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Back to classes" }),
    ).toHaveAttribute("href", "/classes");
  });

  it("shows a retryable server failure without presenting it as a missing class", async () => {
    const loadClass = vi
      .fn()
      .mockRejectedValueOnce(new ClassApiError("Database unavailable", 503))
      .mockResolvedValueOnce(detail);
    const user = userEvent.setup();
    render(
      <ClassDetail
        classId={12}
        loadClass={loadClass}
        loadInsights={async () => insights}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Class service is unavailable",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading class" }),
    );
    expect(await screen.findByText("Bella Tan")).toBeVisible();
  });

  it("does not request invalid class references", () => {
    const loadClass = vi.fn();
    render(
      <ClassDetail classId={0} loadClass={loadClass} loadInsights={vi.fn()} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("reference is invalid");
    expect(loadClass).not.toHaveBeenCalled();
  });

  it("keeps class data visible when the persisted insight snapshot cannot be loaded, then retries it", async () => {
    const loadInsights = vi
      .fn()
      .mockRejectedValueOnce(
        new Error("Insight data is temporarily unavailable"),
      )
      .mockResolvedValueOnce({
        ...insights,
        status: "STALE",
        message: "Insight data is refreshing",
      });
    const user = userEvent.setup();
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => detail}
        loadInsights={loadInsights}
      />,
    );

    expect(await screen.findByText("Bella Tan")).toBeVisible();
    expect(
      screen.getByText("Insight data is temporarily unavailable"),
    ).toBeVisible();
    await user.click(
      screen.getByRole("button", { name: "Retry insight refresh" }),
    );
    expect(await screen.findByText("STALE")).toBeVisible();
    expect(screen.getByText("Insight data is refreshing")).toBeVisible();
  });

  it("names an in-progress background refresh without presenting it as current", async () => {
    render(
      <ClassDetail
        classId={12}
        loadClass={async () => detail}
        loadInsights={async () => ({
          ...insights,
          status: "REFRESHING",
          message: "Insights are being refreshed",
          dataAsOf: null,
          items: [],
          feedback: [],
        })}
      />,
    );
    expect(await screen.findByText("REFRESHING")).toBeVisible();
    expect(screen.getByText("Insights are being refreshed")).toBeVisible();
    expect(
      screen.getByText(
        "No covered topic currently meets this class’s weak-area threshold.",
      ),
    ).toBeVisible();
  });
});
