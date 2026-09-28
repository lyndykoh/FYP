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
  overallScore: 54,
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
      topicId: 2,
      topicCode: "MAT",
      topicName: "Mathematics",
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
      score: 0,
      status: "NOT_STARTED",
      attemptCount: 0,
      calculatedAt: null,
    },
    {
      topicId: 42,
      topicCode: "SCI-2",
      topicName: "Energy",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 86,
      status: "MASTERED",
      attemptCount: 3,
      calculatedAt: "2026-08-01T00:00:00",
    },
    {
      topicId: 43,
      topicCode: "MAT-1",
      topicName: "Algebra",
      parentTopicId: 2,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 45,
      status: "NEEDS_REVISION",
      attemptCount: 2,
      calculatedAt: "2026-08-01T00:00:00",
    },
  ],
};

describe("student topics page", () => {
  beforeEach(() => vi.mocked(fetchMasteryMap).mockReset());

  it("filters the returned canonical hierarchy by subject, status, and topic text without requesting a different data source", async () => {
    vi.mocked(fetchMasteryMap).mockResolvedValue(map);
    const user = userEvent.setup();
    render(<Page />);
    expect(await screen.findByText("Adaptation")).toBeVisible();
    expect(
      screen.getByLabelText(
        "Adaptation: Not started, 0% mastery. Open topic details.",
      ),
    ).toHaveAttribute("href", "/topics/41");
    expect(screen.queryByText("Locked")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Subject"), "1");
    expect(screen.getByRole("heading", { name: "Science" })).toBeVisible();
    expect(screen.queryByText("Algebra")).not.toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Status"), "MASTERED");
    expect(screen.getByText("Energy")).toBeVisible();
    expect(screen.queryByText("Adaptation")).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Topic contains"), "energy");
    expect(screen.getByText("Energy")).toBeVisible();
    const filters = screen.getByLabelText("Topic filters");
    expect(filters).toContainElement(screen.getByLabelText("Topic contains"));
    const generatedRules = Array.from(document.styleSheets)
      .flatMap((sheet) =>
        Array.from(sheet.cssRules).map((rule) => rule.cssText),
      )
      .join("\n");
    expect(generatedRules).toMatch(
      /@media[^}]*min-width:\s*600px[\s\S]*grid-template-columns/i,
    );
    await waitFor(() => expect(fetchMasteryMap).toHaveBeenCalledTimes(1));
  });

  it("shows loading, a canonical new-student empty state, and a retryable API failure", async () => {
    let resolve!: (value: MasteryMapData) => void;
    vi.mocked(fetchMasteryMap).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const { unmount } = render(<Page />);
    expect(screen.getByLabelText("Loading topic map")).toBeVisible();
    resolve({ ...map, overallScore: null, nodes: [] });
    expect(await screen.findByRole("status")).toHaveTextContent(
      "No active syllabus topics are available yet.",
    );
    unmount();

    vi.mocked(fetchMasteryMap)
      .mockRejectedValueOnce(new Error("Topics unavailable"))
      .mockResolvedValueOnce({ ...map, nodes: [] });
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Topics unavailable",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("status")).toBeVisible();
  });
});
