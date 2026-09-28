import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MasteryTopicDetail } from "@/services/mastery";

const navigation = vi.hoisted(() => ({
  topicId: "41",
  studentId: null as string | null,
}));
vi.mock("next/navigation", () => ({
  useParams: () => ({ topicId: navigation.topicId }),
  useSearchParams: () => ({
    get: (key: string) => (key === "studentId" ? navigation.studentId : null),
  }),
}));
vi.mock("@/services/mastery", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/mastery")>()),
  fetchMasteryTopic: vi.fn(),
}));

import Page from "./page";
import { fetchMasteryTopic } from "@/services/mastery";

const detail: MasteryTopicDetail = {
  studentId: 31,
  node: {
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
  history: [],
};

describe("student topic detail", () => {
  beforeEach(() => {
    vi.mocked(fetchMasteryTopic).mockReset();
    navigation.topicId = "41";
    navigation.studentId = null;
  });

  it("drills into the self-scoped canonical topic and renders coherent approved evidence history", async () => {
    vi.mocked(fetchMasteryTopic).mockResolvedValue({
      ...detail,
      node: { ...detail.node, score: 65, status: "LEARNING", attemptCount: 1 },
      history: [
        {
          previousScore: 0,
          newScore: 65,
          previousStatus: "NOT_STARTED",
          newStatus: "LEARNING",
          reason: "Tutor-approved worksheet",
          occurredAt: "2026-08-01T00:00:00Z",
        },
      ],
    });
    render(<Page />);
    expect(
      await screen.findByRole("heading", { name: "Adaptation" }),
    ).toBeVisible();
    expect(screen.getByText("LEARNING")).toBeVisible();
    expect(screen.getByText("1 approved attempt")).toBeVisible();
    expect(screen.getByText("Tutor-approved worksheet")).toBeVisible();
    expect(screen.getByText("0% → 65%")).toBeVisible();
    expect(
      screen.getByRole("link", { name: /Back to topics/i }),
    ).toHaveAttribute("href", "/topics");
    await waitFor(() => expect(fetchMasteryTopic).toHaveBeenCalledTimes(1));
  });

  it("clears stale topic detail while a changed route is loading", async () => {
    let resolveNext!: (value: MasteryTopicDetail) => void;
    vi.mocked(fetchMasteryTopic)
      .mockResolvedValueOnce(detail)
      .mockReturnValueOnce(
        new Promise((done) => {
          resolveNext = done;
        }),
      );
    const { rerender } = render(<Page />);
    expect(
      await screen.findByRole("heading", { name: "Adaptation" }),
    ).toBeVisible();

    navigation.topicId = "42";
    rerender(<Page />);
    expect(await screen.findByLabelText("Loading topic mastery")).toBeVisible();
    expect(
      screen.queryByRole("heading", { name: "Adaptation" }),
    ).not.toBeInTheDocument();
    expect(fetchMasteryTopic).toHaveBeenLastCalledWith(42, undefined);

    resolveNext({
      ...detail,
      node: { ...detail.node, topicId: 42, topicName: "Evolution" },
    });
    expect(
      await screen.findByRole("heading", { name: "Evolution" }),
    ).toBeVisible();
  });

  it("does not let a retried former route overwrite the newer topic", async () => {
    let resolveRetry!: (value: MasteryTopicDetail) => void;
    vi.mocked(fetchMasteryTopic)
      .mockRejectedValueOnce(new Error("Adaptation unavailable"))
      .mockReturnValueOnce(
        new Promise((done) => {
          resolveRetry = done;
        }),
      )
      .mockResolvedValueOnce({
        ...detail,
        node: { ...detail.node, topicId: 42, topicName: "Evolution" },
      });
    const { rerender } = render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Adaptation unavailable",
    );

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByLabelText("Loading topic mastery")).toBeVisible();
    navigation.topicId = "42";
    rerender(<Page />);
    expect(
      await screen.findByRole("heading", { name: "Evolution" }),
    ).toBeVisible();

    resolveRetry(detail);
    await waitFor(() =>
      expect(screen.getByRole("heading", { name: "Evolution" })).toBeVisible(),
    );
    expect(
      screen.queryByRole("heading", { name: "Adaptation" }),
    ).not.toBeInTheDocument();
  });

  it("shows loading and retries a topic API failure", async () => {
    let resolve!: (value: MasteryTopicDetail) => void;
    vi.mocked(fetchMasteryTopic).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    const { unmount } = render(<Page />);
    expect(screen.getByLabelText("Loading topic mastery")).toBeVisible();
    resolve(detail);
    expect(
      await screen.findByRole("heading", { name: "Adaptation" }),
    ).toBeVisible();
    unmount();

    vi.mocked(fetchMasteryTopic)
      .mockRejectedValueOnce(new Error("Topic unavailable"))
      .mockResolvedValueOnce(detail);
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Topic unavailable",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Try again" }));
    expect(
      await screen.findByRole("heading", { name: "Adaptation" }),
    ).toBeVisible();
    expect(fetchMasteryTopic).toHaveBeenCalledTimes(3);
  });

  it("reports a missing topic and rejects an invalid topic reference without requesting data", async () => {
    vi.mocked(fetchMasteryTopic).mockRejectedValueOnce(
      new Error("Topic was not found."),
    );
    const { unmount } = render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Topic was not found.",
    );

    navigation.topicId = "missing";
    vi.mocked(fetchMasteryTopic).mockClear();
    unmount();
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent("invalid");
    await waitFor(() => expect(fetchMasteryTopic).not.toHaveBeenCalled());
  });
});
