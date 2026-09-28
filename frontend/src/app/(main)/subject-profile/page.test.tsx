import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { LearningProfile } from "@/services/insights";

vi.mock("@/services/insights", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/insights")>();
  return { ...actual, fetchLearningProfile: vi.fn() };
});

import SubjectProfilePage from "./page";
import { fetchLearningProfile } from "@/services/insights";

const dimensions: LearningProfile["dimensions"] = [
  {
    category: "CONCEPT",
    evidenceCount: 1,
    evidence: [
      {
        topicId: 2,
        topicName: "Heat",
        score: 48,
        status: "PRACTISING",
        attemptCount: 2,
        sourceReason: "Tutor confirmed a concept gap.",
        occurredAt: "2026-08-28T10:00:00",
      },
    ],
  },
  { category: "KEYWORD", evidenceCount: 0, evidence: [] },
  { category: "EXPRESSION", evidenceCount: 0, evidence: [] },
  { category: "APPLICATION", evidenceCount: 0, evidence: [] },
];
const profile: LearningProfile = {
  studentId: 31,
  dataAsOf: null,
  source: "DETERMINISTIC",
  strengths: [
    {
      topicId: 1,
      topicName: "Water cycle",
      score: 90,
      status: "MASTERED",
      attemptCount: 3,
    },
  ],
  growthAreas: [],
  improvements: [
    {
      topicId: 3,
      topicName: "Forces",
      score: 76,
      status: "IMPROVING",
      attemptCount: 2,
    },
  ],
  dimensions,
  findings: [],
};

describe("SubjectProfilePage", () => {
  beforeEach(() => {
    vi.mocked(fetchLearningProfile).mockReset();
  });

  it("renders canonical strengths rather than static subject mock data", async () => {
    vi.mocked(fetchLearningProfile).mockResolvedValue(profile);
    render(<SubjectProfilePage />);
    expect(await screen.findByText("Water cycle")).toBeVisible();
    expect(screen.getByText("90% mastery · 3 approved attempts")).toBeVisible();
    expect(screen.getByText("Making progress")).toBeVisible();
    expect(screen.getByText("Forces")).toBeVisible();
    expect(screen.getByText("Learning dimensions")).toBeVisible();
    expect(screen.getByText("Concept")).toBeVisible();
    expect(
      screen.getByText("1 tutor-confirmed diagnostic record."),
    ).toBeVisible();
    expect(
      screen.getAllByText("No tutor-confirmed diagnostic evidence yet."),
    ).toHaveLength(3);
  });

  it("keeps a failed profile request recoverable", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchLearningProfile)
      .mockRejectedValueOnce(new Error("Service unavailable"))
      .mockResolvedValueOnce(profile);
    render(<SubjectProfilePage />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Service unavailable",
    );
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Water cycle")).toBeVisible();
  });

  it("renders evidence-backed common mistakes and accurate empty states", async () => {
    vi.mocked(fetchLearningProfile).mockResolvedValue({
      ...profile,
      strengths: [],
      improvements: [],
      dimensions: dimensions.map((item) => ({
        ...item,
        evidenceCount: 0,
        evidence: [],
      })),
    });
    render(<SubjectProfilePage />);
    expect(
      await screen.findAllByText("No tutor-confirmed diagnostic evidence yet."),
    ).toHaveLength(4);
    expect(
      screen.getByText("Strengths will appear after more approved evidence."),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Improvement will appear after an approved mastery update.",
      ),
    ).toBeVisible();
  });

  it("shows loading feedback before the profile request settles", async () => {
    let resolve: (value: LearningProfile) => void = () => undefined;
    vi.mocked(fetchLearningProfile).mockReturnValue(
      new Promise((done) => {
        resolve = done;
      }),
    );
    render(<SubjectProfilePage />);
    expect(screen.getByLabelText("Loading subject profile")).toBeVisible();
    resolve(profile);
    expect(await screen.findByText("Water cycle")).toBeVisible();
  });

  it("renders all tutor-confirmed dimensions and common-mistake evidence", async () => {
    const allDimensions: LearningProfile["dimensions"] = [
      {
        category: "CONCEPT",
        evidenceCount: 1,
        evidence: [dimensions[0].evidence[0]],
      },
      {
        category: "KEYWORD",
        evidenceCount: 1,
        evidence: [
          {
            ...dimensions[0].evidence[0],
            topicId: 4,
            topicName: "Cells",
            sourceReason: "Tutor confirmed missing keywords.",
          },
        ],
      },
      {
        category: "EXPRESSION",
        evidenceCount: 1,
        evidence: [
          {
            ...dimensions[0].evidence[0],
            topicId: 5,
            topicName: "Energy",
            sourceReason: "Tutor confirmed weak explanation.",
          },
        ],
      },
      {
        category: "APPLICATION",
        evidenceCount: 1,
        evidence: [
          {
            ...dimensions[0].evidence[0],
            topicId: 6,
            topicName: "Forces",
            sourceReason: "Tutor confirmed application error.",
          },
        ],
      },
    ];
    vi.mocked(fetchLearningProfile).mockResolvedValue({
      ...profile,
      dimensions: allDimensions,
      findings: [
        {
          type: "KEYWORD_WEAKNESS",
          title: "Keyword evidence in Cells",
          summary: "Tutor-confirmed missing keyword: nucleus.",
          suggestedAction:
            "Practise using the required keyword in a complete answer.",
          evidence: [allDimensions[1].evidence[0]],
        },
      ],
    });
    render(<SubjectProfilePage />);
    expect(await screen.findByText("Keywords")).toBeVisible();
    expect(screen.getByText("Expression")).toBeVisible();
    expect(screen.getByText("Application")).toBeVisible();
    expect(screen.getByText("Keyword evidence in Cells")).toBeVisible();
    expect(screen.getByText("Evidence: Cells · 48%")).toBeVisible();
  });
});
