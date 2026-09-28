const gradingUrl =
  process.env.NEXT_PUBLIC_GRADING_API_URL || "http://localhost:8082";

export type MistakeType =
  | "CONCEPT_MISUNDERSTANDING"
  | "CALCULATION_ERROR"
  | "MISREAD_QUESTION"
  | "INCOMPLETE_WORKING"
  | "INCORRECT_FORMULA"
  | "CARELESS_MISTAKE"
  | "WEAK_EXPLANATION"
  | "MISSING_KEY_POINT"
  | "WRONG_UNITS"
  | "ANSWER_FORMAT_ISSUE";

export type MistakeHistoryItem = {
  id: number;
  worksheetId: number;
  worksheetQuestionId: number;
  questionBankId: number;
  syllabusTopicId: number | null;
  syllabusTopicCode: string | null;
  mistakeType: MistakeType;
  mistakeLabel: string;
  description: string;
  recordedAt: string;
};

export class MistakeApiError extends Error {
  constructor(
    message: string,
    readonly status = 0,
  ) {
    super(message);
    this.name = "MistakeApiError";
  }
}

const mistakeTypes = new Set<MistakeType>([
  "CONCEPT_MISUNDERSTANDING",
  "CALCULATION_ERROR",
  "MISREAD_QUESTION",
  "INCOMPLETE_WORKING",
  "INCORRECT_FORMULA",
  "CARELESS_MISTAKE",
  "WEAK_EXPLANATION",
  "MISSING_KEY_POINT",
  "WRONG_UNITS",
  "ANSWER_FORMAT_ISSUE",
]);

function headers(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const numberValue = (value: unknown) =>
  typeof value === "number" && Number.isSafeInteger(value) && value > 0
    ? value
    : null;
const nullableNumber = (value: unknown) =>
  value === null ? null : numberValue(value);
const nullableText = (value: unknown) =>
  value === null
    ? null
    : typeof value === "string" && value.trim()
      ? value
      : null;

export function parseMistakeHistory(value: unknown): MistakeHistoryItem[] {
  if (!Array.isArray(value))
    throw new MistakeApiError("The mistake-history response is invalid.");
  return value.map((entry) => {
    if (!entry || typeof entry !== "object")
      throw new MistakeApiError("The mistake-history response is invalid.");
    const item = entry as Record<string, unknown>;
    const id = numberValue(item.id);
    const worksheetId = numberValue(item.worksheetId);
    const worksheetQuestionId = numberValue(item.worksheetQuestionId);
    const questionBankId = numberValue(item.questionBankId);
    const syllabusTopicId = nullableNumber(item.syllabusTopicId);
    const syllabusTopicCode = nullableText(item.syllabusTopicCode);
    if (
      id === null ||
      worksheetId === null ||
      worksheetQuestionId === null ||
      questionBankId === null ||
      !mistakeTypes.has(item.mistakeType as MistakeType) ||
      typeof item.mistakeLabel !== "string" ||
      !item.mistakeLabel.trim() ||
      typeof item.description !== "string" ||
      !item.description.trim() ||
      typeof item.recordedAt !== "string"
    ) {
      throw new MistakeApiError("The mistake-history response is invalid.");
    }
    return {
      id,
      worksheetId,
      worksheetQuestionId,
      questionBankId,
      syllabusTopicId,
      syllabusTopicCode,
      mistakeType: item.mistakeType as MistakeType,
      mistakeLabel: item.mistakeLabel,
      description: item.description,
      recordedAt: item.recordedAt,
    };
  });
}

async function request(path: string): Promise<MistakeHistoryItem[]> {
  const response = await fetch(`${gradingUrl}/api/grading/mistakes${path}`, {
    headers: headers(),
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;
    throw new MistakeApiError(
      body?.error || "Mistake history could not be loaded.",
      response.status,
    );
  }
  return parseMistakeHistory(await response.json());
}

/** Student-safe endpoint: identity is resolved by the signed-in backend principal. */
export function fetchMyMistakes(): Promise<MistakeHistoryItem[]> {
  return request("/me");
}

/** Tutor endpoint: backend returns the same 404 for foreign and absent students. */
export function fetchStudentMistakes(
  studentId: number,
): Promise<MistakeHistoryItem[]> {
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    return Promise.reject(
      new MistakeApiError("The student id is invalid.", 400),
    );
  }
  return request(`/students/${studentId}`);
}
