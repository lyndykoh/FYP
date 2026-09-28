import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { StudentSelfProfile } from "@/services/students";

vi.mock("@/services/students", () => ({ fetchStudentSelfProfile: vi.fn() }));

import ProfilePage from "./page";
import { fetchStudentSelfProfile } from "@/services/students";

const profile: StudentSelfProfile = {
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
  mastery: [],
  learningProfile: { strengths: [], focusAreas: [] },
  history: [],
  worksheets: [],
  tutorOnly: null,
};

describe("Student self profile", () => {
  beforeEach(() => {
    vi.mocked(fetchStudentSelfProfile).mockReset();
  });

  it("uses the authenticated profile data rather than the retired mock profile", async () => {
    vi.mocked(fetchStudentSelfProfile).mockResolvedValue(profile);
    render(<ProfilePage />);
    expect(screen.getByTestId("profile-skeleton")).toBeVisible();
    expect(
      await screen.findByRole("heading", { name: "Bella Tan" }),
    ).toBeVisible();
    expect(screen.getByText("Primary 5 Science")).toBeVisible();
    expect(screen.getByText("68%")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Open Primary 5 Science" }),
    ).toHaveAttribute("href", "/classes/12");
    expect(screen.queryByText("LUM-2026-0148")).not.toBeInTheDocument();
  });

  it("renders a recoverable no-profile state", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchStudentSelfProfile)
      .mockRejectedValueOnce(new Error("Student profile was not found"))
      .mockResolvedValueOnce(profile);
    render(<ProfilePage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Student profile was not found",
    );
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(
      await screen.findByRole("heading", { name: "Bella Tan" }),
    ).toBeVisible();
  });

  it("handles an empty authenticated profile without fabricating enrollment or account details", async () => {
    vi.mocked(fetchStudentSelfProfile).mockResolvedValue({
      ...profile,
      classes: [],
      metrics: {
        averageMastery: null,
        topicCount: 0,
        totalAttempts: 0,
        lastCalculatedAt: null,
      },
      worksheets: [],
    });
    render(<ProfilePage />);
    expect(await screen.findByText("No classes yet")).toBeVisible();
    expect(screen.getByText("No mastery records yet")).toBeVisible();
    expect(screen.queryByText("PASSWORD & SECURITY")).not.toBeInTheDocument();
  });
});
