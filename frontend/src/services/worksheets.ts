import type { QuestionDifficulty, QuestionType } from "./questions";

export type WorksheetTargetMode = "CLASS" | "STUDENTS";
export type WorksheetStatus = "DRAFT" | "APPROVED" | "ARCHIVED";
export type WorksheetType = "STANDARD" | "DIAGNOSTIC";

export interface WorksheetQuestion {
  id: number;
  code: string;
  prompt: string;
  totalMarks: number;
  questionType: QuestionType;
  /** Present for API-loaded drafts; optional only for legacy in-memory test fixtures. */
  topicId?: number;
  topicName: string;
  /** Empty for text-only and legacy worksheet fixtures. */
  images?: WorksheetQuestionImage[];
}

export interface WorksheetQuestionImage {
  id: number;
  filename: string;
  contentType: "image/png" | "image/jpeg";
  width: number;
  height: number;
}

export interface WorksheetAssignment {
  id: number;
  assignmentType: "CLASS" | "STUDENT";
  classId: number | null;
  studentProfileId: number | null;
  assignedAt: string | null;
  dueAt: string | null;
}

export interface TutorWorksheet {
  id: number;
  code: string;
  title: string;
  instructions: string | null;
  subject?: string | null;
  worksheetType?: WorksheetType;
  targetMode: WorksheetTargetMode;
  status: WorksheetStatus;
  generationRequestId: number | null;
  /** The class which supplied this worksheet's target context, when known. */
  sourceClassId?: number | null;
  dueAt: string | null;
  questions: WorksheetQuestion[];
  assignments: WorksheetAssignment[];
}

export interface WorksheetGenerationRequest {
  id: number;
  classId: number;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  worksheet: TutorWorksheet | null;
  message: string;
}

export interface GenerateWorksheetRequest {
  targetMode: WorksheetTargetMode;
  studentIds?: number[];
  /** Omit to generate from all active eligible Question Bank questions. */
  topicIds?: number[];
  questionCount: number;
  questionType?: QuestionType;
  difficulty?: QuestionDifficulty;
  dueAt?: string;
  title?: string;
  instructions?: string;
}

export interface GenerateDiagnosticWorksheetRequest extends GenerateWorksheetRequest {
  topicIds: number[];
}

export type DiagnosticReason = "LOW_MASTERY" | "CONSOLIDATE" | "NEW_TOPIC";
export interface DiagnosticRecommendation {
  studentId: number | null;
  studentName: string | null;
  topicId: number;
  topicName: string;
  masteryPercent: number | null;
  attemptCount: number;
  reason: DiagnosticReason;
}
export interface DiagnosticRecommendations {
  status: "READY" | "INSUFFICIENT_EVIDENCE";
  message: string;
  recommendations: DiagnosticRecommendation[];
}

export interface UpdateWorksheetRequest {
  title: string;
  instructions: string | null;
  questionIds: number[];
}

export type StudentWorksheetStatus = "ASSIGNED" | "SUBMITTED" | "MARKED";

export interface StudentWorksheetTopic {
  id: number;
  name: string;
}

export interface StudentWorksheetSubject {
  id: number;
  name: string;
}

export interface StudentWorksheetScore {
  earned: number;
  available: number;
  percent: number;
}

/**
 * A worksheet assignment visible to the authenticated Student only.
 * The server derives the Student from the bearer token; no student id is accepted here.
 */
export interface StudentWorksheet {
  id: number;
  code: string;
  title: string;
  subjects: StudentWorksheetSubject[];
  topics: StudentWorksheetTopic[];
  assignedAt: string;
  dueAt: string | null;
  status: StudentWorksheetStatus;
  submittedAt: string | null;
  reviewedAt: string | null;
  score: StudentWorksheetScore | null;
}

/** Prompts for one worksheet that has been authorised for the current Student. */
export interface StudentWorksheetDetail {
  id: number;
  code: string;
  title: string;
  instructions: string | null;
  subject: string | null;
  questions: WorksheetQuestion[];
  assignedAt: string;
  dueAt: string | null;
}

export interface StudentWorksheetLibraryFilters {
  subjectId?: number;
  topicId?: number;
  status?: StudentWorksheetStatus;
  assignedFrom?: string;
  assignedTo?: string;
}

export class WorksheetApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "WorksheetApiError";
  }
}

const base =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const questionTypes: readonly QuestionType[] = [
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN_THE_BLANK",
  "SHORT_ANSWER",
  "OPEN_ENDED",
  "CALCULATION",
  "DIAGRAM",
];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function positiveId(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function nonEmpty(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function stringOrNull(value: unknown): value is string | null {
  return value === null || typeof value === "string";
}

function localDateTime(value: unknown): string {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(value)
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet date. Please try again.",
    );
  }
  const parsed = new Date(`${value}Z`);
  const [day, sourceTime] = value.split("T");
  const time = sourceTime.split(".")[0];
  const normalized = `${day}T${time.length === 5 ? `${time}:00` : time}`;
  if (
    Number.isNaN(parsed.getTime()) ||
    parsed.toISOString().slice(0, 19) !== normalized
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet date. Please try again.",
    );
  }
  return value;
}

function localDateTimeOrNull(value: unknown): string | null {
  return value === null ? null : localDateTime(value);
}

function nonNegativeNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isQuestionType(value: unknown): value is QuestionType {
  return (
    typeof value === "string" && questionTypes.includes(value as QuestionType)
  );
}

function parseWorksheetImage(payload: unknown): WorksheetQuestionImage {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.filename) ||
    (payload.contentType !== "image/png" &&
      payload.contentType !== "image/jpeg") ||
    !positiveId(payload.width) ||
    !positiveId(payload.height)
  ) {
    throw new Error(
      "The learning service returned an invalid worksheet image.",
    );
  }
  return {
    id: payload.id,
    filename: payload.filename,
    contentType: payload.contentType,
    width: payload.width,
    height: payload.height,
  };
}

function isWorksheetStatus(value: unknown): value is WorksheetStatus {
  return value === "DRAFT" || value === "APPROVED" || value === "ARCHIVED";
}

function isWorksheetType(value: unknown): value is WorksheetType {
  return value === "STANDARD" || value === "DIAGNOSTIC";
}

function isStudentWorksheetStatus(
  value: unknown,
): value is StudentWorksheetStatus {
  return value === "ASSIGNED" || value === "SUBMITTED" || value === "MARKED";
}

function headers(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function fetchWorksheetImageUrl(
  audience: "tutor" | "student",
  worksheetId: number,
  imageId: number,
): Promise<string> {
  if (!positiveId(worksheetId) || !positiveId(imageId))
    throw new WorksheetApiError("Worksheet image reference is invalid.", 400);
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");
  const response = await fetch(
    `${base}/api/learning/${audience}/worksheets/${worksheetId}/images/${imageId}`,
    {
      headers: {
        Accept: "image/png, image/jpeg",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    },
  );
  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new WorksheetApiError(
      payload?.message ||
        `Worksheet image request failed (${response.status}).`,
      response.status,
    );
  }
  const contentType = response.headers.get("content-type") || "";
  if (!/^image\/(png|jpeg)(?:;|$)/i.test(contentType))
    throw new WorksheetApiError(
      "The learning service returned an invalid image.",
      502,
    );
  return URL.createObjectURL(await response.blob());
}

async function json(response: Response): Promise<unknown> {
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new WorksheetApiError(
      body?.message || `Worksheet request failed (${response.status}).`,
      response.status,
    );
  }
  return response.json();
}

function parseQuestion(payload: unknown): WorksheetQuestion {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.code) ||
    !nonEmpty(payload.prompt) ||
    typeof payload.totalMarks !== "number" ||
    !Number.isFinite(payload.totalMarks) ||
    payload.totalMarks <= 0 ||
    !isQuestionType(payload.questionType) ||
    !positiveId(payload.syllabusTopicId) ||
    !nonEmpty(payload.syllabusTopicName) ||
    !(payload.images === undefined || Array.isArray(payload.images))
  ) {
    throw new Error(
      "The learning service returned an invalid worksheet question.",
    );
  }
  return {
    id: payload.id,
    code: payload.code,
    prompt: payload.prompt,
    totalMarks: payload.totalMarks,
    questionType: payload.questionType,
    topicId: payload.syllabusTopicId,
    topicName: payload.syllabusTopicName,
    images:
      payload.images === undefined
        ? []
        : payload.images.map(parseWorksheetImage),
  };
}

function parseAssignment(payload: unknown): WorksheetAssignment {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    (payload.assignmentType !== "CLASS" &&
      payload.assignmentType !== "STUDENT") ||
    !(payload.classId === null || positiveId(payload.classId)) ||
    !(
      payload.studentProfileId === null || positiveId(payload.studentProfileId)
    ) ||
    !stringOrNull(payload.assignedAt) ||
    !stringOrNull(payload.dueAt)
  ) {
    throw new Error(
      "The learning service returned an invalid worksheet assignment.",
    );
  }
  return {
    id: payload.id,
    assignmentType: payload.assignmentType,
    classId: payload.classId,
    studentProfileId: payload.studentProfileId,
    assignedAt: payload.assignedAt,
    dueAt: payload.dueAt,
  };
}

function parseStudentWorksheetTopic(payload: unknown): StudentWorksheetTopic {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.name)
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet topic. Please try again.",
    );
  }
  return { id: payload.id, name: payload.name };
}

function parseStudentWorksheetSubject(
  payload: unknown,
): StudentWorksheetSubject {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.name)
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet subject. Please try again.",
    );
  }
  return { id: payload.id, name: payload.name };
}

function parseStudentWorksheetScore(payload: unknown): StudentWorksheetScore {
  if (
    !isRecord(payload) ||
    !nonNegativeNumber(payload.earned) ||
    !nonNegativeNumber(payload.available) ||
    payload.available <= 0 ||
    payload.earned > payload.available ||
    !nonNegativeNumber(payload.percent) ||
    payload.percent > 100 ||
    Math.abs(payload.percent - (payload.earned / payload.available) * 100) >
      0.01
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet score. Please try again.",
    );
  }
  return {
    earned: payload.earned,
    available: payload.available,
    percent: payload.percent,
  };
}

/** Strictly validates the self-scoped Student worksheet-library response. */
export function parseStudentWorksheet(payload: unknown): StudentWorksheet {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.code) ||
    !nonEmpty(payload.title) ||
    !Array.isArray(payload.subjects) ||
    payload.subjects.length === 0 ||
    !Array.isArray(payload.topics) ||
    payload.topics.length === 0 ||
    !isStudentWorksheetStatus(payload.status) ||
    !stringOrNull(payload.dueAt) ||
    !stringOrNull(payload.submittedAt) ||
    !stringOrNull(payload.reviewedAt) ||
    !(payload.score === null || isRecord(payload.score))
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet. Please try again.",
    );
  }
  return {
    id: payload.id,
    code: payload.code,
    title: payload.title,
    subjects: payload.subjects.map(parseStudentWorksheetSubject),
    topics: payload.topics.map(parseStudentWorksheetTopic),
    assignedAt: localDateTime(payload.assignedAt),
    dueAt: localDateTimeOrNull(payload.dueAt),
    status: payload.status,
    submittedAt: localDateTimeOrNull(payload.submittedAt),
    reviewedAt: localDateTimeOrNull(payload.reviewedAt),
    score:
      payload.score === null ? null : parseStudentWorksheetScore(payload.score),
  };
}

/** Strictly validates learner-safe detail; answer keys and assignment rosters are never accepted here. */
export function parseStudentWorksheetDetail(
  payload: unknown,
): StudentWorksheetDetail {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.code) ||
    !nonEmpty(payload.title) ||
    !stringOrNull(payload.instructions) ||
    !stringOrNull(payload.subject) ||
    !Array.isArray(payload.questions) ||
    !stringOrNull(payload.dueAt)
  ) {
    throw new Error(
      "The learning service returned an invalid student worksheet detail. Please try again.",
    );
  }
  return {
    id: payload.id,
    code: payload.code,
    title: payload.title,
    instructions: payload.instructions,
    subject: payload.subject,
    questions: payload.questions.map(parseQuestion),
    assignedAt: localDateTime(payload.assignedAt),
    dueAt: localDateTimeOrNull(payload.dueAt),
  };
}

/** Validates and normalises the owner-scoped worksheet detail response. */
export function parseTutorWorksheet(payload: unknown): TutorWorksheet {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !nonEmpty(payload.code) ||
    !nonEmpty(payload.title) ||
    !stringOrNull(payload.instructions) ||
    !(payload.subject === undefined || stringOrNull(payload.subject)) ||
    !(
      payload.worksheetType === undefined ||
      isWorksheetType(payload.worksheetType)
    ) ||
    (payload.audienceType !== "CLASS" && payload.audienceType !== "STUDENT") ||
    !isWorksheetStatus(payload.status) ||
    !(
      payload.generationRequestId === null ||
      positiveId(payload.generationRequestId)
    ) ||
    !(
      payload.sourceClassId === undefined ||
      payload.sourceClassId === null ||
      positiveId(payload.sourceClassId)
    ) ||
    !Array.isArray(payload.questions) ||
    !Array.isArray(payload.assignments)
  ) {
    throw new Error(
      "The learning service returned an invalid worksheet. Please try again.",
    );
  }
  const assignments = payload.assignments.map(parseAssignment);
  return {
    id: payload.id,
    code: payload.code,
    title: payload.title,
    instructions: payload.instructions,
    subject: payload.subject === undefined ? null : payload.subject,
    worksheetType:
      payload.worksheetType === undefined ? "STANDARD" : payload.worksheetType,
    targetMode: payload.audienceType === "CLASS" ? "CLASS" : "STUDENTS",
    status: payload.status,
    generationRequestId: payload.generationRequestId,
    sourceClassId:
      payload.sourceClassId === undefined ? null : payload.sourceClassId,
    dueAt:
      assignments.find((assignment) => assignment.dueAt !== null)?.dueAt ??
      null,
    questions: payload.questions.map(parseQuestion),
    assignments,
  };
}

function parseDiagnosticRecommendation(
  payload: unknown,
): DiagnosticRecommendation {
  if (!isRecord(payload)) {
    throw new Error(
      "The learning service returned an invalid diagnostic recommendation. Please try again.",
    );
  }
  const {
    studentId,
    studentName,
    topicId,
    topicName,
    masteryPercent,
    attemptCount,
    reason,
  } = payload;
  if (
    !positiveId(topicId) ||
    !nonEmpty(topicName) ||
    !(studentId === null || positiveId(studentId)) ||
    !(studentName === null || nonEmpty(studentName)) ||
    !(masteryPercent === null || nonNegativeNumber(masteryPercent)) ||
    !isNonNegativeInteger(attemptCount) ||
    (reason !== "LOW_MASTERY" &&
      reason !== "CONSOLIDATE" &&
      reason !== "NEW_TOPIC")
  ) {
    throw new Error(
      "The learning service returned an invalid diagnostic recommendation. Please try again.",
    );
  }
  return {
    studentId,
    studentName,
    topicId,
    topicName,
    masteryPercent,
    attemptCount,
    reason,
  };
}

export function parseDiagnosticRecommendations(
  payload: unknown,
): DiagnosticRecommendations {
  if (
    !isRecord(payload) ||
    (payload.status !== "READY" &&
      payload.status !== "INSUFFICIENT_EVIDENCE") ||
    !nonEmpty(payload.message) ||
    !Array.isArray(payload.recommendations)
  ) {
    throw new Error(
      "The learning service returned an invalid diagnostic recommendation. Please try again.",
    );
  }
  return {
    status: payload.status,
    message: payload.message,
    recommendations: payload.recommendations.map(parseDiagnosticRecommendation),
  };
}

function parseGenerationRequest(payload: unknown): WorksheetGenerationRequest {
  if (
    !isRecord(payload) ||
    !positiveId(payload.id) ||
    !positiveId(payload.classId) ||
    (payload.status !== "QUEUED" &&
      payload.status !== "RUNNING" &&
      payload.status !== "SUCCEEDED" &&
      payload.status !== "FAILED" &&
      payload.status !== "CANCELLED") ||
    !(payload.worksheet === null || isRecord(payload.worksheet))
  ) {
    throw new Error(
      "The learning service returned an invalid generation request. Please try again.",
    );
  }
  return {
    id: payload.id,
    classId: payload.classId,
    status: payload.status,
    worksheet:
      payload.worksheet === null
        ? null
        : parseTutorWorksheet(payload.worksheet),
    message:
      typeof payload.failureMessage === "string" ? payload.failureMessage : "",
  };
}

function requireId(value: number, message: string): void {
  if (!positiveId(value)) throw new WorksheetApiError(message, 400);
}

export async function generateWorksheet(
  classId: number,
  request: GenerateWorksheetRequest,
  idempotencyKey: string,
): Promise<WorksheetGenerationRequest> {
  requireId(classId, "Class reference is invalid.");
  if (
    (request.topicIds !== undefined &&
      (!Array.isArray(request.topicIds) ||
        !request.topicIds.every(positiveId))) ||
    !Number.isSafeInteger(request.questionCount) ||
    request.questionCount < 1 ||
    request.questionCount > 100 ||
    (request.targetMode !== "CLASS" && request.targetMode !== "STUDENTS") ||
    !nonEmpty(idempotencyKey)
  ) {
    throw new WorksheetApiError("Worksheet configuration is invalid.", 400);
  }
  const payload = await json(
    await fetch(
      `${base}/api/learning/tutor/classes/${classId}/worksheet-generation-requests`,
      {
        method: "POST",
        headers: { ...headers(), "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(request),
      },
    ),
  );
  return parseGenerationRequest(payload);
}

/** Fetches evidence-only diagnostic suggestions; it does not create or assign anything. */
export async function fetchDiagnosticRecommendations(
  classId: number,
): Promise<DiagnosticRecommendations> {
  requireId(classId, "Class reference is invalid.");
  return parseDiagnosticRecommendations(
    await json(
      await fetch(
        `${base}/api/learning/tutor/classes/${classId}/worksheet-recommendations`,
        { headers: headers() },
      ),
    ),
  );
}

export async function generateDiagnosticWorksheet(
  classId: number,
  request: GenerateDiagnosticWorksheetRequest,
  idempotencyKey: string,
): Promise<WorksheetGenerationRequest> {
  requireId(classId, "Class reference is invalid.");
  if (
    !Array.isArray(request.topicIds) ||
    request.topicIds.length === 0 ||
    !request.topicIds.every(positiveId) ||
    !Number.isSafeInteger(request.questionCount) ||
    request.questionCount < 1 ||
    request.questionCount > 100 ||
    (request.targetMode !== "CLASS" && request.targetMode !== "STUDENTS") ||
    !nonEmpty(idempotencyKey)
  ) {
    throw new WorksheetApiError(
      "Diagnostic worksheet configuration is invalid.",
      400,
    );
  }
  const payload = await json(
    await fetch(
      `${base}/api/learning/tutor/classes/${classId}/diagnostic-worksheet-generation-requests`,
      {
        method: "POST",
        headers: { ...headers(), "Idempotency-Key": idempotencyKey },
        body: JSON.stringify(request),
      },
    ),
  );
  return parseGenerationRequest(payload);
}

export async function fetchGenerationRequest(
  classId: number,
  requestId: number,
): Promise<WorksheetGenerationRequest> {
  requireId(classId, "Class reference is invalid.");
  requireId(requestId, "Generation request reference is invalid.");
  return parseGenerationRequest(
    await json(
      await fetch(
        `${base}/api/learning/tutor/classes/${classId}/worksheet-generation-requests/${requestId}`,
        { headers: headers() },
      ),
    ),
  );
}

export async function fetchTutorWorksheet(
  worksheetId: number,
): Promise<TutorWorksheet> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  return parseTutorWorksheet(
    await json(
      await fetch(`${base}/api/learning/tutor/worksheets/${worksheetId}`, {
        headers: headers(),
      }),
    ),
  );
}

/** Lists only worksheets owned by the current Tutor; class filtering is owner-scoped server-side. */
export async function fetchTutorWorksheets(
  classId?: number,
): Promise<TutorWorksheet[]> {
  if (classId !== undefined) requireId(classId, "Class reference is invalid.");
  const suffix = classId === undefined ? "" : `?classId=${classId}`;
  const payload = await json(
    await fetch(`${base}/api/learning/tutor/worksheets${suffix}`, {
      headers: headers(),
    }),
  );
  if (!Array.isArray(payload))
    throw new Error(
      "The learning service returned an invalid worksheet list. Please try again.",
    );
  return payload.map(parseTutorWorksheet);
}

/**
 * Lists approved worksheets that the current Tutor may submit for one exact
 * class/student membership. The server enforces the relationship.
 */
export async function fetchSubmissionWorksheets(
  classId: number,
  studentId: number,
): Promise<TutorWorksheet[]> {
  requireId(classId, "Class reference is invalid.");
  requireId(studentId, "Student reference is invalid.");
  const payload = await json(
    await fetch(
      `${base}/api/learning/tutor/classes/${classId}/students/${studentId}/submission-worksheets`,
      { headers: headers() },
    ),
  );
  if (!Array.isArray(payload))
    throw new Error(
      "The learning service returned an invalid submission worksheet list. Please try again.",
    );
  return payload.map(parseTutorWorksheet);
}

function studentWorksheetQuery(
  filters: StudentWorksheetLibraryFilters,
): string {
  const query = new URLSearchParams();
  if (filters.subjectId !== undefined) {
    if (!positiveId(filters.subjectId))
      throw new WorksheetApiError("Worksheet subject filter is invalid.", 400);
    query.set("subjectId", String(filters.subjectId));
  }
  if (filters.topicId !== undefined) {
    if (!positiveId(filters.topicId))
      throw new WorksheetApiError("Worksheet topic filter is invalid.", 400);
    query.set("topicId", String(filters.topicId));
  }
  if (filters.status !== undefined) {
    if (!isStudentWorksheetStatus(filters.status))
      throw new WorksheetApiError("Worksheet status filter is invalid.", 400);
    query.set("status", filters.status);
  }
  for (const [key, value] of [
    ["assignedFrom", filters.assignedFrom],
    ["assignedTo", filters.assignedTo],
  ] as const) {
    if (value !== undefined) {
      const parsed = new Date(`${value}T00:00:00Z`);
      if (
        !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
        Number.isNaN(parsed.getTime()) ||
        parsed.toISOString().slice(0, 10) !== value
      ) {
        throw new WorksheetApiError("Worksheet date filter is invalid.", 400);
      }
      query.set(key, value);
    }
  }
  return query.toString();
}

/** Lists worksheet assignments belonging to the current Student only. */
export async function fetchStudentWorksheets(
  filters: StudentWorksheetLibraryFilters = {},
): Promise<StudentWorksheet[]> {
  const query = studentWorksheetQuery(filters);
  const payload = await json(
    await fetch(
      `${base}/api/learning/student/worksheets${query ? `?${query}` : ""}`,
      { headers: headers() },
    ),
  );
  if (!Array.isArray(payload))
    throw new Error(
      "The learning service returned an invalid student worksheet list. Please try again.",
    );
  return payload.map(parseStudentWorksheet);
}

/** Loads the prompts for one assignment belonging to the authenticated Student only. */
export async function fetchStudentWorksheet(
  worksheetId: number,
): Promise<StudentWorksheetDetail> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  return parseStudentWorksheetDetail(
    await json(
      await fetch(`${base}/api/learning/student/worksheets/${worksheetId}`, {
        headers: headers(),
      }),
    ),
  );
}

/** Downloads the PDF for one assignment belonging to the authenticated Student only. */
export async function downloadStudentWorksheetPdf(
  worksheetId: number,
): Promise<Blob> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  const response = await fetch(
    `${base}/api/learning/student/worksheets/${worksheetId}/pdf`,
    {
      headers: { ...headers(), Accept: "application/pdf" },
    },
  );
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new WorksheetApiError(
      body?.message || "Worksheet PDF could not be downloaded.",
      response.status,
    );
  }
  const contentType = response.headers.get("content-type");
  if (!contentType || !/^application\/pdf(?:\s*;|$)/i.test(contentType)) {
    throw new WorksheetApiError(
      "The worksheet PDF response is invalid. Please try again.",
      response.status,
    );
  }
  return response.blob();
}

export async function updateWorksheet(
  worksheetId: number,
  request: UpdateWorksheetRequest,
): Promise<TutorWorksheet> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  if (
    !nonEmpty(request.title) ||
    !Array.isArray(request.questionIds) ||
    request.questionIds.length === 0 ||
    !request.questionIds.every(positiveId)
  ) {
    throw new WorksheetApiError("Worksheet details are invalid.", 400);
  }
  return parseTutorWorksheet(
    await json(
      await fetch(`${base}/api/learning/tutor/worksheets/${worksheetId}`, {
        method: "PATCH",
        headers: headers(),
        body: JSON.stringify(request),
      }),
    ),
  );
}

export async function approveWorksheet(
  worksheetId: number,
  dueAt?: string,
): Promise<TutorWorksheet> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  return parseTutorWorksheet(
    await json(
      await fetch(
        `${base}/api/learning/tutor/worksheets/${worksheetId}/approve`,
        {
          method: "POST",
          headers: headers(),
          body: JSON.stringify({ dueAt: dueAt || null }),
        },
      ),
    ),
  );
}

export async function downloadWorksheetPdf(worksheetId: number): Promise<Blob> {
  requireId(worksheetId, "Worksheet reference is invalid.");
  const response = await fetch(
    `${base}/api/learning/tutor/worksheets/${worksheetId}/pdf`,
    { headers: headers() },
  );
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new WorksheetApiError(
      body?.message || "Worksheet PDF could not be created.",
      response.status,
    );
  }
  return response.blob();
}
