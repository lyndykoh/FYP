export type SyllabusNodeType =
  "SUBJECT" | "LEVEL" | "THEME" | "TOPIC" | "SUBTOPIC";

export interface SyllabusNode {
  id: number;
  code: string;
  name: string;
  nodeType: SyllabusNodeType;
  parentId: number | null;
  children: SyllabusNode[];
}

export interface SyllabusTree {
  items: SyllabusNode[];
}

export class SyllabusApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "SyllabusApiError";
    this.status = status;
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const TREE_PATH = "/api/learning/shared/syllabus/tree";
const NODE_TYPES: readonly SyllabusNodeType[] = [
  "SUBJECT",
  "LEVEL",
  "THEME",
  "TOPIC",
  "SUBTOPIC",
];

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isPositiveId(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isNodeType(value: unknown): value is SyllabusNodeType {
  return (
    typeof value === "string" && NODE_TYPES.includes(value as SyllabusNodeType)
  );
}

function isSyllabusNode(
  value: unknown,
  expectedType?: SyllabusNodeType,
): value is SyllabusNode {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveId(candidate.id) &&
    isNonEmptyString(candidate.code) &&
    isNonEmptyString(candidate.name) &&
    isNodeType(candidate.nodeType) &&
    (expectedType === undefined || candidate.nodeType === expectedType) &&
    (candidate.parentId === null || isPositiveId(candidate.parentId)) &&
    Array.isArray(candidate.children) &&
    candidate.children.every((child) => isSyllabusNode(child))
  );
}

function validateHierarchy(
  nodes: SyllabusNode[],
  expectedType: SyllabusNodeType = "SUBJECT",
  parentId: number | null = null,
): boolean {
  return nodes.every(
    (node) =>
      node.nodeType === expectedType &&
      node.parentId === parentId &&
      isSyllabusNode(node, expectedType) &&
      (expectedType === "SUBTOPIC" ||
        validateHierarchy(
          node.children,
          NODE_TYPES[NODE_TYPES.indexOf(expectedType) + 1]!,
          node.id,
        )),
  );
}

export function parseSyllabusTree(payload: unknown): SyllabusTree {
  if (
    typeof payload !== "object" ||
    payload === null ||
    !Array.isArray((payload as Record<string, unknown>).items)
  ) {
    throw new Error(
      "The learning service returned an invalid syllabus tree. Please try again.",
    );
  }
  const items = (payload as Record<string, unknown>).items as unknown[];
  if (!validateHierarchy(items as SyllabusNode[])) {
    throw new Error(
      "The learning service returned an invalid syllabus tree. Please try again.",
    );
  }
  return { items: items as SyllabusNode[] };
}

function authHeaders(): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function fetchSyllabusTree(): Promise<SyllabusTree> {
  const response = await fetch(`${LEARNING_API_URL}${TREE_PATH}`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    let message = `The learning service could not load the syllabus (status ${response.status}).`;
    try {
      const payload = (await response.json()) as Record<string, unknown>;
      if (isNonEmptyString(payload.message)) message = payload.message;
    } catch {
      /* Keep the useful HTTP fallback. */
    }
    throw new SyllabusApiError(message, response.status);
  }
  return parseSyllabusTree(await response.json());
}
