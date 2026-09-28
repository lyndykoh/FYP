import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MasteryMapData, MasteryTopicDetail } from "@/services/mastery";
import type { LearningProfile } from "@/services/insights";

const navigation = vi.hoisted(() => ({
  topicId: "41",
  studentId: null as string | null,
}));
vi.mock("next/navigation", () => ({
  useParams: () => ({ topicId: navigation.topicId, studentId: "31" }),
  useSearchParams: () => ({
    get: (key: string) => (key === "studentId" ? navigation.studentId : null),
  }),
}));
vi.mock("@/components/students/StudentProfile", () => ({
  default: ({ studentId }: { studentId: number }) => (
    <div>Profile {studentId}</div>
  ),
}));
vi.mock("@/services/mastery", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/mastery")>();
  return { ...actual, fetchMasteryMap: vi.fn(), fetchMasteryTopic: vi.fn() };
});
vi.mock("@/services/insights", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/insights")>();
  return { ...actual, fetchLearningProfile: vi.fn() };
});

import ProgressPage from "./progress/page";
import StudentProfilePage from "./students/[studentId]/page";
import TopicDetailPage from "./topics/[topicId]/page";
import TopicsPage from "./topics/page";
import { fetchMasteryMap, fetchMasteryTopic } from "@/services/mastery";
import { fetchLearningProfile } from "@/services/insights";

const map: MasteryMapData = {
  studentId: 31,
  overallScore: null,
  nodes: [],
};
const detail: MasteryTopicDetail = {
  studentId: 31,
  node: {
    topicId: 41,
    topicCode: "SCI-P5-01",
    topicName: "Adaptation",
    parentTopicId: 1,
    parentDepth: 0,
    depth: 3,
    nodeType: "TOPIC",
    score: 65,
    status: "PRACTISING",
    attemptCount: 2,
    calculatedAt: null,
  },
  history: [],
};
const insightProfile: LearningProfile = {
  studentId: 31,
  strengths: [],
  growthAreas: [],
  improvements: [],
  dimensions: ["CONCEPT", "KEYWORD", "EXPRESSION", "APPLICATION"].map(
    (category) => ({ category, evidenceCount: 0, evidence: [] }),
  ) as LearningProfile["dimensions"],
  findings: [],
  dataAsOf: null,
  source: "DETERMINISTIC",
};

describe("mastery-backed pages", () => {
  beforeEach(() => {
    vi.mocked(fetchMasteryMap).mockReset();
    vi.mocked(fetchMasteryTopic).mockReset();
    vi.mocked(fetchLearningProfile).mockReset();
    vi.mocked(fetchLearningProfile).mockResolvedValue(insightProfile);
    navigation.topicId = "41";
    navigation.studentId = null;
  });

  it("shows loading then the canonical empty map on the progress page", async () => {
    let complete!: (value: MasteryMapData) => void;
    vi.mocked(fetchMasteryMap).mockReturnValue(
      new Promise((resolve) => {
        complete = resolve;
      }),
    );
    render(<ProgressPage />);
    expect(screen.getByLabelText("Loading mastery map")).toBeVisible();
    complete(map);
    expect(await screen.findByRole("status")).toHaveTextContent(
      "No active syllabus topics are available yet.",
    );
  });

  it("shows a retryable API failure on the topics page", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchMasteryMap)
      .mockRejectedValueOnce(new Error("Topics unavailable"))
      .mockResolvedValueOnce(map);
    render(<TopicsPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Topics unavailable",
    );
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("status")).toBeVisible();
    expect(fetchMasteryMap).toHaveBeenCalledTimes(2);
  });

  it("loads self-scoped topic drill-down and rejects an invalid route without a request", async () => {
    vi.mocked(fetchMasteryTopic).mockResolvedValue(detail);
    render(<TopicDetailPage />);
    expect(
      await screen.findByRole("heading", { name: "Adaptation" }),
    ).toBeVisible();
    expect(fetchMasteryTopic).toHaveBeenCalledWith(41, undefined);

    navigation.topicId = "invalid";
    vi.mocked(fetchMasteryTopic).mockClear();
    render(<TopicDetailPage />);
    expect(await screen.findByRole("alert")).toHaveTextContent("invalid");
    await waitFor(() => expect(fetchMasteryTopic).not.toHaveBeenCalled());
  });

  it("requests the owner-scoped map when viewing a Tutor student profile", async () => {
    vi.mocked(fetchMasteryMap).mockResolvedValue(map);
    render(<StudentProfilePage />);
    expect(await screen.findByText("Profile 31")).toBeVisible();
    await waitFor(() => expect(fetchMasteryMap).toHaveBeenCalledWith(31));
    await waitFor(() => expect(fetchLearningProfile).toHaveBeenCalledWith(31));
  });
});
