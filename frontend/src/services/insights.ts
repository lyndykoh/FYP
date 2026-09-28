import type { MasteryStatus } from "@/services/mastery";

export type LearningFindingType =
  | "CONCEPT_WEAKNESS"
  | "KEYWORD_WEAKNESS"
  | "EXPRESSION_WEAKNESS"
  | "APPLICATION_WEAKNESS"
  | "REPEATED_WEAKNESS"
  | "REGRESSED"
  | "MASTERY_GAP";
export type LearningDimensionCategory =
  "CONCEPT" | "KEYWORD" | "EXPRESSION" | "APPLICATION";

export interface LearningTopicSummary {
  topicId: number;
  topicName: string;
  score: number;
  status: MasteryStatus;
  attemptCount: number;
}

export interface LearningEvidence extends LearningTopicSummary {
  sourceReason: string | null;
  occurredAt: string | null;
}

export interface LearningFinding {
  type: LearningFindingType;
  title: string;
  summary: string;
  suggestedAction: string;
  evidence: LearningEvidence[];
}
/** The four categories are derived only from tutor-confirmed diagnostic evidence. */
export interface LearningDimension {
  category: LearningDimensionCategory;
  evidenceCount: number;
  evidence: LearningEvidence[];
}

export interface LearningProfile {
  studentId: number;
  strengths: LearningTopicSummary[];
  growthAreas: LearningTopicSummary[];
  improvements: LearningTopicSummary[];
  dimensions: LearningDimension[];
  findings: LearningFinding[];
  dataAsOf: string | null;
  source: "DETERMINISTIC";
}

const API = process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const MASTERY_STATUSES: readonly MasteryStatus[] = [
  "NOT_STARTED",
  "LEARNING",
  "PRACTISING",
  "IMPROVING",
  "MASTERED",
  "NEEDS_REVISION",
];
const FINDING_TYPES: readonly LearningFindingType[] = [
  "CONCEPT_WEAKNESS",
  "KEYWORD_WEAKNESS",
  "EXPRESSION_WEAKNESS",
  "APPLICATION_WEAKNESS",
  "REPEATED_WEAKNESS",
  "REGRESSED",
  "MASTERY_GAP",
];
const DIMENSION_CATEGORIES: readonly LearningDimensionCategory[] = [
  "CONCEPT",
  "KEYWORD",
  "EXPRESSION",
  "APPLICATION",
];

function isPositiveIdentifier(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isCount(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isPercentage(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isFinite(value) &&
    value >= 0 &&
    value <= 100
  );
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isOptionalDate(value: unknown): value is string | null {
  return value === null || isNonEmptyString(value);
}

function isMasteryStatus(value: unknown): value is MasteryStatus {
  return (
    typeof value === "string" &&
    MASTERY_STATUSES.includes(value as MasteryStatus)
  );
}

function invalidLearningProfile(): Error {
  return new Error("The learning profile response is invalid.");
}

function parseTopicSummary(value: unknown): LearningTopicSummary {
  if (!value || typeof value !== "object") throw invalidLearningProfile();
  const item = value as Record<string, unknown>;
  if (
    !isPositiveIdentifier(item.topicId) ||
    !isNonEmptyString(item.topicName) ||
    !isPercentage(item.score) ||
    !isMasteryStatus(item.status) ||
    !isCount(item.attemptCount)
  ) {
    throw invalidLearningProfile();
  }
  return item as unknown as LearningTopicSummary;
}

function parseLearningEvidence(value: unknown): LearningEvidence {
  const item = parseTopicSummary(value) as LearningEvidence;
  const raw = value as Record<string, unknown>;
  if (
    !(raw.sourceReason === null || isNonEmptyString(raw.sourceReason)) ||
    !isOptionalDate(raw.occurredAt)
  ) {
    throw invalidLearningProfile();
  }
  return item;
}

function parseLearningDimension(value: unknown): LearningDimension {
  if (!value || typeof value !== "object") throw invalidLearningProfile();
  const item = value as Record<string, unknown>;
  if (
    typeof item.category !== "string" ||
    !DIMENSION_CATEGORIES.includes(
      item.category as LearningDimensionCategory,
    ) ||
    !isCount(item.evidenceCount) ||
    !Array.isArray(item.evidence)
  ) {
    throw invalidLearningProfile();
  }

  const evidence = item.evidence.map(parseLearningEvidence);
  if (evidence.length !== item.evidenceCount) throw invalidLearningProfile();

  return {
    category: item.category as LearningDimensionCategory,
    evidenceCount: item.evidenceCount,
    evidence,
  };
}

function parseLearningFinding(value: unknown): LearningFinding {
  if (!value || typeof value !== "object") throw invalidLearningProfile();
  const item = value as Record<string, unknown>;
  if (
    typeof item.type !== "string" ||
    !FINDING_TYPES.includes(item.type as LearningFindingType) ||
    !isNonEmptyString(item.title) ||
    !isNonEmptyString(item.summary) ||
    !isNonEmptyString(item.suggestedAction) ||
    !Array.isArray(item.evidence) ||
    item.evidence.length === 0
  ) {
    throw invalidLearningProfile();
  }

  return {
    type: item.type as LearningFindingType,
    title: item.title,
    summary: item.summary,
    suggestedAction: item.suggestedAction,
    evidence: item.evidence.map(parseLearningEvidence),
  };
}

export function parseLearningProfile(value: unknown): LearningProfile {
  if (!value || typeof value !== "object") throw invalidLearningProfile();
  const profile = value as Record<string, unknown>;
  if (
    !isPositiveIdentifier(profile.studentId) ||
    !Array.isArray(profile.strengths) ||
    !Array.isArray(profile.growthAreas) ||
    !Array.isArray(profile.improvements) ||
    !Array.isArray(profile.dimensions) ||
    !Array.isArray(profile.findings) ||
    !isOptionalDate(profile.dataAsOf) ||
    profile.source !== "DETERMINISTIC"
  ) {
    throw invalidLearningProfile();
  }

  const dimensions = profile.dimensions.map(parseLearningDimension);
  const hasExpectedDimensionOrder =
    dimensions.length === DIMENSION_CATEGORIES.length &&
    dimensions.every(
      (item, index) => item.category === DIMENSION_CATEGORIES[index],
    );
  if (!hasExpectedDimensionOrder) throw invalidLearningProfile();

  return {
    studentId: profile.studentId,
    strengths: profile.strengths.map(parseTopicSummary),
    growthAreas: profile.growthAreas.map(parseTopicSummary),
    improvements: profile.improvements.map(parseTopicSummary),
    dimensions,
    findings: profile.findings.map(parseLearningFinding),
    dataAsOf: profile.dataAsOf,
    source: "DETERMINISTIC",
  };
}

function learningProfileRequestHeaders(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function loadLearningProfile(path: string): Promise<LearningProfile> {
  const response = await fetch(`${API}${path}`, {
    headers: learningProfileRequestHeaders(),
  });
  if (!response.ok) {
    let message = "Learning insights could not be loaded.";
    try {
      const error = (await response.json()) as { message?: unknown };
      if (isNonEmptyString(error.message)) message = error.message;
    } catch {
      /* The generic error remains safe. */
    }
    throw new Error(message);
  }
  return parseLearningProfile(await response.json());
}

export async function fetchLearningProfile(
  studentId?: number,
): Promise<LearningProfile> {
  if (studentId !== undefined && !isPositiveIdentifier(studentId)) {
    throw new Error("Student reference is invalid.");
  }

  const path =
    studentId === undefined
      ? "/api/learning/student/learning-profile"
      : `/api/learning/tutor/students/${studentId}/learning-profile`;
  return loadLearningProfile(path);
}
