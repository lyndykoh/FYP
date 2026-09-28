import { beforeEach, describe, expect, it, vi } from "vitest";

import { fetchLearningProfile, parseLearningProfile } from "./insights";

const profile = {
  studentId: 31,
  strengths: [
    {
      topicId: 41,
      topicName: "Adaptation",
      score: 86,
      status: "MASTERED",
      attemptCount: 4,
    },
  ],
  growthAreas: [
    {
      topicId: 42,
      topicName: "Energy",
      score: 48,
      status: "PRACTISING",
      attemptCount: 2,
    },
  ],
  improvements: [
    {
      topicId: 43,
      topicName: "Forces",
      score: 76,
      status: "IMPROVING",
      attemptCount: 2,
    },
  ],
  dimensions: ["CONCEPT", "KEYWORD", "EXPRESSION", "APPLICATION"].map(
    (category) => ({ category, evidenceCount: 0, evidence: [] }),
  ),
  findings: [
    {
      type: "REPEATED_WEAKNESS",
      title: "Repeated difficulty in Energy",
      summary: "48% mastery across 2 approved attempts.",
      suggestedAction: "Use tutor-led correction.",
      evidence: [
        {
          topicId: 42,
          topicName: "Energy",
          score: 48,
          status: "PRACTISING",
          attemptCount: 2,
          sourceReason: "Approved result",
          occurredAt: "2026-08-28T10:00:00",
        },
      ],
    },
  ],
  dataAsOf: "2026-08-28T10:00:00",
  source: "DETERMINISTIC",
};

describe("learning insights client", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    localStorage.clear();
  });

  it("loads the self and Tutor owner-scoped learning profile endpoints", async () => {
    localStorage.setItem("jwt_token", "token");
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(profile), { status: 200 }),
    );
    await expect(fetchLearningProfile()).resolves.toEqual(profile);
    expect(fetch).toHaveBeenLastCalledWith(
      "http://localhost:8083/api/learning/student/learning-profile",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer token" }),
      }),
    );
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(profile), { status: 200 }),
    );
    await expect(fetchLearningProfile(31)).resolves.toEqual(profile);
    expect(fetch).toHaveBeenLastCalledWith(
      "http://localhost:8083/api/learning/tutor/students/31/learning-profile",
      expect.anything(),
    );
  });

  it("rejects invalid IDs and malformed findings instead of rendering unsupported evidence", async () => {
    await expect(fetchLearningProfile(0)).rejects.toThrow(
      "Student reference is invalid",
    );
    expect(fetch).not.toHaveBeenCalled();
    expect(() =>
      parseLearningProfile({
        ...profile,
        findings: [{ ...profile.findings[0], evidence: [] }],
      }),
    ).toThrow("learning profile response is invalid");
    expect(() =>
      parseLearningProfile({
        ...profile,
        dimensions: profile.dimensions.slice(1),
      }),
    ).toThrow("learning profile response is invalid");
    expect(() =>
      parseLearningProfile({
        ...profile,
        dimensions: [{ ...profile.dimensions[0], evidenceCount: 1 }],
      }),
    ).toThrow("learning profile response is invalid");
    expect(() => parseLearningProfile({ ...profile, source: "AI" })).toThrow(
      "learning profile response is invalid",
    );
  });
});
