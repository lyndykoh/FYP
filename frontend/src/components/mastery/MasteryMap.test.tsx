import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { MasteryMapData } from "@/services/mastery";

import MasteryMap from "./MasteryMap";

const data: MasteryMapData = {
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
      topicCode: "SCI-P5-01",
      topicName: "Adaptation",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 48,
      status: "NEEDS_REVISION",
      attemptCount: 2,
      calculatedAt: "2026-09-02T10:00:00",
    },
    {
      topicId: 42,
      topicCode: "SCI-P5-02",
      topicName: "Energy",
      parentTopicId: 1,
      parentDepth: 0,
      depth: 3,
      nodeType: "TOPIC",
      score: 86,
      status: "MASTERED",
      attemptCount: 4,
      calculatedAt: "2026-09-02T10:00:00",
    },
  ],
};

describe("MasteryMap", () => {
  it("renders text status, precise score, and a focus area without relying on colour", () => {
    render(<MasteryMap data={data} />);
    expect(screen.getByText("Adaptation")).toBeVisible();
    expect(screen.getByText("Needs revision")).toBeVisible();
    expect(screen.getByText("FOCUS AREA")).toBeVisible();
    expect(
      screen.getByLabelText(
        "Adaptation: Needs revision, 48% mastery. Open topic details.",
      ),
    ).toHaveAttribute("href", "/topics/41");
    expect(screen.getByLabelText("Adaptation mastery 48%")).toBeVisible();
  });

  it("uses the tutor student reference in accessible topic drill-down links", () => {
    render(<MasteryMap data={data} studentId={31} />);
    expect(
      screen.getByLabelText(
        "Energy: Mastered, 86% mastery. Open topic details.",
      ),
    ).toHaveAttribute("href", "/topics/42?studentId=31");
    expect(screen.getByRole("heading", { name: "Science" })).toBeVisible();
    expect(screen.getByLabelText("subject Science")).toContainElement(
      screen.getByText("Adaptation"),
    );
  });

  it("presents every API status as visible text", () => {
    const allStatuses: MasteryMapData = {
      ...data,
      nodes: [
        data.nodes[0],
        ...(
          [
            "NOT_STARTED",
            "LEARNING",
            "PRACTISING",
            "IMPROVING",
            "MASTERED",
            "NEEDS_REVISION",
          ] as const
        ).map((status, index) => ({
          ...data.nodes[1],
          topicId: 100 + index,
          topicName: `Topic ${index}`,
          status,
          score: index * 15,
        })),
      ],
    };
    render(<MasteryMap data={allStatuses} />);
    for (const label of [
      "Not started",
      "Learning",
      "Practising",
      "Improving",
      "Mastered",
      "Needs revision",
    ]) {
      expect(screen.getByText(label)).toBeVisible();
    }
  });

  it("renders an accessible empty state when the active syllabus is empty", () => {
    render(<MasteryMap data={{ ...data, overallScore: null, nodes: [] }} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "No active syllabus topics are available yet.",
    );
    expect(
      screen.getByLabelText("Overall mastery: Not calculated"),
    ).toBeVisible();
  });
});
