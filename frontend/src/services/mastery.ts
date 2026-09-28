export type MasteryStatus =
  | "NOT_STARTED"
  | "LEARNING"
  | "PRACTISING"
  | "IMPROVING"
  | "MASTERED"
  | "NEEDS_REVISION";
export type SyllabusNodeType =
  "SUBJECT" | "LEVEL" | "THEME" | "TOPIC" | "SUBTOPIC";

export interface MasteryNode {
  topicId: number;
  topicCode: string;
  topicName: string;
  parentTopicId: number | null;
  parentDepth: number | null;
  depth: number;
  nodeType: SyllabusNodeType;
  score: number;
  status: MasteryStatus;
  attemptCount: number;
  calculatedAt: string | null;
}

export interface MasteryMapData {
  studentId: number;
  overallScore: number | null;
  nodes: MasteryNode[];
}

/**
 * Display-only totals calculated from the canonical mastery-map response.
 * A syllabus container (subject, level, or theme) is never treated as a
 * learnable topic or as an attempt in its own right.
 */
export interface MasteryMetrics {
  totalTopics: number;
  attemptedTopics: number;
  approvedAttempts: number;
  masteredTopics: number;
  needsRevisionTopics: number;
}

export interface MasteryHistoryItem {
  previousScore: number;
  newScore: number;
  previousStatus: MasteryStatus;
  newStatus: MasteryStatus;
  reason: string;
  occurredAt: string | null;
}

export interface MasteryTopicDetail {
  studentId: number;
  node: MasteryNode;
  history: MasteryHistoryItem[];
}

export class MasteryApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "MasteryApiError";
    this.status = status;
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const STATUSES: readonly MasteryStatus[] = [
  "NOT_STARTED",
  "LEARNING",
  "PRACTISING",
  "IMPROVING",
  "MASTERED",
  "NEEDS_REVISION",
];
const NODE_TYPES: readonly SyllabusNodeType[] = [
  "SUBJECT",
  "LEVEL",
  "THEME",
  "TOPIC",
  "SUBTOPIC",
];

function positive(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function nonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function percentage(value: unknown): value is number {
  return (
    typeof value === "number" &&
    Number.isFinite(value) &&
    value >= 0 &&
    value <= 100
  );
}

function string(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function nullableDateTime(value: unknown): value is string | null {
  return value === null || string(value);
}

function optionalPositive(value: unknown): value is number | null {
  return value === null || positive(value);
}

function status(value: unknown): value is MasteryStatus {
  return typeof value === "string" && STATUSES.includes(value as MasteryStatus);
}

function nodeType(value: unknown): value is SyllabusNodeType {
  return (
    typeof value === "string" && NODE_TYPES.includes(value as SyllabusNodeType)
  );
}

export function parseMasteryNode(value: unknown): MasteryNode {
  if (!value || typeof value !== "object")
    throw new Error("The mastery response is invalid.");
  const node = value as Record<string, unknown>;
  if (
    !positive(node.topicId) ||
    !string(node.topicCode) ||
    !string(node.topicName) ||
    !optionalPositive(node.parentTopicId) ||
    !(node.parentDepth === null || nonNegativeInteger(node.parentDepth)) ||
    !nonNegativeInteger(node.depth) ||
    !nodeType(node.nodeType) ||
    !percentage(node.score) ||
    !status(node.status) ||
    !nonNegativeInteger(node.attemptCount) ||
    !nullableDateTime(node.calculatedAt)
  ) {
    throw new Error("The mastery response is invalid.");
  }
  return node as unknown as MasteryNode;
}

export function parseMasteryMap(value: unknown): MasteryMapData {
  if (!value || typeof value !== "object")
    throw new Error("The mastery response is invalid.");
  const map = value as Record<string, unknown>;
  if (
    !positive(map.studentId) ||
    !(map.overallScore === null || percentage(map.overallScore)) ||
    !Array.isArray(map.nodes)
  ) {
    throw new Error("The mastery response is invalid.");
  }
  return {
    studentId: map.studentId,
    overallScore: map.overallScore,
    nodes: map.nodes.map(parseMasteryNode),
  };
}

export function deriveMasteryMetrics(data: MasteryMapData): MasteryMetrics {
  const topics = data.nodes.filter(
    (node) => node.nodeType === "TOPIC" || node.nodeType === "SUBTOPIC",
  );
  return {
    totalTopics: topics.length,
    attemptedTopics: topics.filter((node) => node.attemptCount > 0).length,
    approvedAttempts: topics.reduce(
      (total, node) => total + node.attemptCount,
      0,
    ),
    masteredTopics: topics.filter((node) => node.status === "MASTERED").length,
    needsRevisionTopics: topics.filter(
      (node) => node.status === "NEEDS_REVISION",
    ).length,
  };
}

export function parseMasteryTopicDetail(value: unknown): MasteryTopicDetail {
  if (!value || typeof value !== "object")
    throw new Error("The mastery response is invalid.");
  const detail = value as Record<string, unknown>;
  if (!positive(detail.studentId) || !Array.isArray(detail.history))
    throw new Error("The mastery response is invalid.");
  const history = detail.history.map((item) => {
    if (!item || typeof item !== "object")
      throw new Error("The mastery response is invalid.");
    const row = item as Record<string, unknown>;
    if (
      !percentage(row.previousScore) ||
      !percentage(row.newScore) ||
      !status(row.previousStatus) ||
      !status(row.newStatus) ||
      !string(row.reason) ||
      !nullableDateTime(row.occurredAt)
    ) {
      throw new Error("The mastery response is invalid.");
    }
    return row as unknown as MasteryHistoryItem;
  });
  return {
    studentId: detail.studentId,
    node: parseMasteryNode(detail.node),
    history,
  };
}

function requestHeaders(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function request(path: string): Promise<unknown> {
  const response = await fetch(`${LEARNING_API_URL}${path}`, {
    headers: requestHeaders(),
  });
  if (!response.ok) {
    let message = "Mastery could not be loaded.";
    try {
      const error = (await response.json()) as { message?: unknown };
      if (string(error.message)) message = error.message;
    } catch {
      /* keep safe generic message */
    }
    throw new MasteryApiError(message, response.status);
  }
  return response.json();
}

function validStudentId(studentId: number): void {
  if (!positive(studentId))
    throw new MasteryApiError("Student reference is invalid.", 400);
}

function validTopicId(topicId: number): void {
  if (!positive(topicId))
    throw new MasteryApiError("Topic reference is invalid.", 400);
}

export async function fetchMasteryMap(
  studentId?: number,
): Promise<MasteryMapData> {
  if (studentId !== undefined) validStudentId(studentId);
  const path =
    studentId === undefined
      ? "/api/learning/student/mastery-map"
      : `/api/learning/tutor/students/${studentId}/mastery-map`;
  return parseMasteryMap(await request(path));
}

export async function fetchMasteryTopic(
  topicId: number,
  studentId?: number,
): Promise<MasteryTopicDetail> {
  validTopicId(topicId);
  if (studentId !== undefined) validStudentId(studentId);
  const path =
    studentId === undefined
      ? `/api/learning/student/mastery-map/topics/${topicId}`
      : `/api/learning/tutor/students/${studentId}/mastery-map/topics/${topicId}`;
  return parseMasteryTopicDetail(await request(path));
}
