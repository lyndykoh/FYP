import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { TutorStudentProfile } from "@/services/students";
import StudentProfile from "./StudentProfile";

const profile: TutorStudentProfile = {
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
      score: 48,
      status: "NEEDS_REVISION",
      attemptCount: 4,
      calculatedAt: "2026-09-02T10:00:00",
    },
    {
      topicId: 42,
      topicCode: "SCI-P5-02",
      topicName: "Energy",
      score: 86,
      status: "MASTERED",
      attemptCount: 2,
      calculatedAt: null,
    },
  ],
  learningProfile: {
    strengths: [
      { topicId: 42, topicName: "Energy", score: 86, status: "MASTERED" },
    ],
    focusAreas: [
      {
        topicId: 41,
        topicName: "Adaptation",
        score: 48,
        status: "NEEDS_REVISION",
      },
    ],
  },
  history: [
    {
      topicId: 41,
      topicName: "Adaptation",
      previousScore: 42,
      newScore: 48,
      previousStatus: "LEARNING",
      newStatus: "NEEDS_REVISION",
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

describe("StudentProfile", () => {
  it("renders loading then a full responsive canonical tutor profile with safe action links", async () => {
    let resolve!: (value: TutorStudentProfile) => void;
    render(
      <StudentProfile
        studentId={31}
        loadProfile={() =>
          new Promise<TutorStudentProfile>((complete) => {
            resolve = complete;
          })
        }
      />,
    );
    expect(screen.getByTestId("student-profile-skeleton")).toBeVisible();
    resolve(profile);
    expect(
      await screen.findByRole("heading", { name: "Bella Tan" }),
    ).toBeVisible();
    expect(screen.getByText("Assigned worksheets")).toBeVisible();
    expect(screen.getByText("Tutor records")).toBeVisible();
    expect(screen.getByText("Adaptation practice")).toBeVisible();
    expect(screen.getByRole("link", { name: "Edit student" })).toHaveAttribute(
      "href",
      "/students/31/edit",
    );
    expect(
      screen.getByRole("link", { name: "Upload completed worksheet" }),
    ).toHaveAttribute("href", "/upload?studentId=31&classId=12");
    expect(
      screen.getByRole("link", { name: "Generate worksheet" }),
    ).toHaveAttribute("href", "/tutor/worksheets/new?classId=12&studentId=31");
    expect(screen.getByText("WORKSHEETS WITH APPROVED RESULTS")).toBeVisible();
    expect(
      screen.getByText("One worksheet has an approved result"),
    ).toBeVisible();
    expect(
      screen.getByRole("link", { name: /Primary 5 Science/ }),
    ).toHaveAttribute("href", "/classes/12");
    expect(screen.getByText("48%")).toBeVisible();
    expect(screen.getByText("+6%")).toBeVisible();
  });

  it("shows safe partial and new-profile states without fabricated values", async () => {
    render(
      <StudentProfile
        studentId={31}
        loadProfile={async () => ({
          ...profile,
          classes: [],
          metrics: {
            averageMastery: null,
            topicCount: 0,
            totalAttempts: 0,
            lastCalculatedAt: null,
          },
          mastery: [],
          learningProfile: { strengths: [], focusAreas: [] },
          history: [],
          worksheets: [],
          tutorOnly: {
            activeAlerts: [],
            reports: [],
            approvedWorksheetCount: 0,
          },
        })}
      />,
    );
    expect(await screen.findByText("No mastery data yet")).toBeVisible();
    expect(screen.getByText("No classes assigned")).toBeVisible();
    expect(screen.getByText("No worksheets assigned")).toBeVisible();
    expect(screen.getByText("No active tutor alerts.")).toBeVisible();
    expect(screen.getByText("Worksheets with approved results")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Generate worksheet" }),
    ).toBeDisabled();
  });

  it("shows recoverable invalid, missing, and server errors", async () => {
    const { rerender } = render(<StudentProfile studentId={0} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This student reference is invalid.",
    );
    rerender(
      <StudentProfile
        studentId={31}
        loadProfile={async () => {
          throw new Error("Student profile was not found");
        }}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Student profile was not found",
    );
    expect(
      screen.getByRole("button", { name: "Retry loading profile" }),
    ).toBeVisible();
  });

  it("does not render or request private notes for a student-facing profile shape", async () => {
    render(
      <StudentProfile
        studentId={31}
        loadProfile={async () => ({ ...profile, tutorOnly: null })}
      />,
    );
    await screen.findByRole("heading", { name: "Bella Tan" });
    expect(
      screen.queryByRole("heading", { name: "Private tutor notes" }),
    ).not.toBeInTheDocument();
  });
});
