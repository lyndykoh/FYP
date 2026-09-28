import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MasteryMapData } from "@/services/mastery";

vi.mock("@/services/mastery", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/mastery")>()),
  fetchMasteryMap: vi.fn(),
}));

import Page from "./page";
import { fetchMasteryMap } from "@/services/mastery";

const map: MasteryMapData = {
  studentId: 31,
  overallScore: 68,
  nodes: [
    {
      topicId: 1,
      topicCode: "SCI",
      topicName: "Science",
      parentTopicId: null,
      parentDepth: null,
      depth: 0,
      nodeType: "SUBJECT",
      score: 0,
      status: "NOT_STARTED",
      attemptCount: 0,
      calculatedAt: null,
    },
    {
      topicId: 41,
      topicCode: "SCI-1",
      topicName: "Adaptation",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 88,
      status: "MASTERED",
      attemptCount: 3,
      calculatedAt: "2026-08-01T00:00:00",
    },
    {
      topicId: 42,
      topicCode: "SCI-2",
      topicName: "Energy",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 48,
      status: "NEEDS_REVISION",
      attemptCount: 2,
      calculatedAt: "2026-08-01T00:00:00",
    },
    {
      topicId: 43,
      topicCode: "SCI-3",
      topicName: "Forces",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 0,
      status: "NOT_STARTED",
      attemptCount: 0,
      calculatedAt: null,
    },
  ],
};

describe("student progress page", () => {
  beforeEach(() => vi.mocked(fetchMasteryMap).mockReset());

  it("renders all progress metrics from the canonical approved mastery map and makes one initial request", async () => {
    vi.mocked(fetchMasteryMap).mockResolvedValue(map);
    render(<Page />);

    expect(await screen.findByText("Overall mastery")).toBeVisible();
    expect(screen.getByLabelText("Progress summary")).toHaveTextContent(
      "68%Approved attempts5Topics mastered1 / 3Need revision1",
    );
    expect(screen.getByText("2 topics with approved evidence")).toBeVisible();
    expect(
      screen.getByLabelText(
        "Adaptation: Mastered, 88% mastery. Open topic details.",
      ),
    ).toHaveAttribute("href", "/topics/41");
    await waitFor(() => expect(fetchMasteryMap).toHaveBeenCalledTimes(1));
  });

  it("shows a new student's canonical empty state", async () => {
    vi.mocked(fetchMasteryMap).mockResolvedValueOnce({
      ...map,
      overallScore: null,
      nodes: [],
    });
    render(<Page />);
    expect(await screen.findByRole("status")).toHaveTextContent(
      "No active syllabus topics are available yet.",
    );
    expect(
      screen.getByLabelText("Overall mastery: Not calculated"),
    ).toBeVisible();
  });

  it("shows loading and retries an API failure", async () => {
    let resolve!: (value: MasteryMapData) => void;
    vi.mocked(fetchMasteryMap).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const { unmount } = render(<Page />);
    expect(screen.getByLabelText("Loading mastery map")).toBeVisible();
    resolve(map);
    expect(await screen.findByText("Overall mastery")).toBeVisible();
    unmount();

    vi.mocked(fetchMasteryMap)
      .mockRejectedValueOnce(new Error("Progress unavailable"))
      .mockResolvedValueOnce(map);
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Progress unavailable",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Overall mastery")).toBeVisible();
    expect(fetchMasteryMap).toHaveBeenCalledTimes(3);
  });
});
