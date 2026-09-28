import type { TutorClass } from "@/services/classes";
import type { MasteryStatus } from "@/services/mastery";

export interface StudentClassMembership {
  id: number;
  className: string;
  subject: string;
  level: string;
}

export interface TutorStudent {
  id: number;
  tutorId: number;
  fullName: string;
  loginUserId: number | null;
  classes: StudentClassMembership[];
  createdAt: string;
  updatedAt: string;
}

export interface StudentMutationRequest {
  fullName: string;
  loginUserId?: number | null;
  classIds: number[];
}

/** Existing auth-service Student account available for a Tutor to claim. */
export interface AvailableStudentAccount {
  id: number;
  fullName: string;
  email: string;
  level: string | null;
}

export interface StudentProfileClass {
  id: number;
  className: string;
  subject: string;
  level: string;
  status: "ACTIVE" | "INACTIVE";
}

export interface StudentProfileMetrics {
  averageMastery: number | null;
  topicCount: number;
  totalAttempts: number;
  lastCalculatedAt: string | null;
}

export interface StudentProfileTopic {
  topicId: number;
  topicName: string;
  score: number;
  status: MasteryStatus;
}

export interface StudentProfileMasteryTopic extends StudentProfileTopic {
  topicCode: string;
  attemptCount: number;
  calculatedAt: string | null;
}

export interface StudentProfileHistoryItem {
  topicId: number;
  topicName: string;
  previousScore: number | null;
  newScore: number | null;
  previousStatus: MasteryStatus | null;
  newStatus: MasteryStatus | null;
  reason: string | null;
  occurredAt: string | null;
}

export interface StudentProfileWorksheet {
  worksheetId: number;
  title: string;
  assignmentType: "CLASS" | "STUDENT";
  classId: number | null;
  assignedAt: string | null;
  dueAt: string | null;
}

export interface StudentProfileAlert {
  id: number;
  type: string;
  severity: string;
  status: string;
  title: string;
  createdAt: string | null;
}

export interface StudentProfileReport {
  id: number;
  reportCode: string;
  status: string;
  periodStart: string | null;
  periodEnd: string | null;
  generatedAt: string | null;
  finalizedAt: string | null;
}

export interface TutorOnlyStudentProfile {
  activeAlerts: StudentProfileAlert[];
  reports: StudentProfileReport[];
  /** Distinct worksheets with at least one active Tutor-approved result. */
  approvedWorksheetCount: number;
}

export interface TutorStudentProfile {
  id: number;
  fullName: string;
  classes: StudentProfileClass[];
  metrics: StudentProfileMetrics;
  mastery: StudentProfileMasteryTopic[];
  learningProfile: {
    strengths: StudentProfileTopic[];
    focusAreas: StudentProfileTopic[];
  };
  history: StudentProfileHistoryItem[];
  worksheets: StudentProfileWorksheet[];
  /** Null is returned by the student self-profile endpoint, never by the tutor endpoint. */
  tutorOnly: TutorOnlyStudentProfile | null;
}

/**
 * The student endpoint deliberately uses the same factual profile shape as the
 * tutor endpoint, but never exposes tutor-only alerts, reports, or notes.
 */
export interface StudentSelfProfile extends Omit<
  TutorStudentProfile,
  "tutorOnly"
> {
  tutorOnly: null;
}

export interface TutorNote {
  id: number;
  studentId: number;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface TutorNoteMutationRequest {
  content: string;
}

export class StudentApiError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields: Record<string, string> = {},
  ) {
    super(message);
    this.name = "StudentApiError";
    this.status = status;
    this.fields = fields;
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const STUDENT_LIST_PATH = "/api/learning/tutor/students";

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isPositiveNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isOptionalPositiveNumber(value: unknown): value is number | null {
  return value === null || isPositiveNumber(value);
}

function isDateTime(value: unknown): value is string {
  return isNonEmptyString(value);
}

function isOptionalDateTime(value: unknown): value is string | null {
  return value === null || isDateTime(value);
}

function isNonNegativeInteger(value: unknown): value is number {
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

function isOptionalPercentage(value: unknown): value is number | null {
  return value === null || isPercentage(value);
}

function isMasteryStatus(value: unknown): value is MasteryStatus {
  return (
    value === "NOT_STARTED" ||
    value === "MASTERED" ||
    value === "IMPROVING" ||
    value === "PRACTISING" ||
    value === "LEARNING" ||
    value === "NEEDS_REVISION"
  );
}

function isOptionalMasteryStatus(
  value: unknown,
): value is MasteryStatus | null {
  return value === null || isMasteryStatus(value);
}

function isStudentClassMembership(
  value: unknown,
): value is StudentClassMembership {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.className) &&
    isNonEmptyString(candidate.subject) &&
    isNonEmptyString(candidate.level)
  );
}

function isTutorStudent(value: unknown): value is TutorStudent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isPositiveNumber(candidate.tutorId) &&
    isNonEmptyString(candidate.fullName) &&
    isOptionalPositiveNumber(candidate.loginUserId) &&
    Array.isArray(candidate.classes) &&
    candidate.classes.every(isStudentClassMembership) &&
    isDateTime(candidate.createdAt) &&
    isDateTime(candidate.updatedAt)
  );
}

function isAvailableStudentAccount(
  value: unknown,
): value is AvailableStudentAccount {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.fullName) &&
    isNonEmptyString(candidate.email) &&
    (candidate.level === null || isNonEmptyString(candidate.level))
  );
}

function isStudentProfileClass(value: unknown): value is StudentProfileClass {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.className) &&
    isNonEmptyString(candidate.subject) &&
    isNonEmptyString(candidate.level) &&
    (candidate.status === "ACTIVE" || candidate.status === "INACTIVE")
  );
}

function isStudentProfileMetrics(
  value: unknown,
): value is StudentProfileMetrics {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isOptionalPercentage(candidate.averageMastery) &&
    isNonNegativeInteger(candidate.topicCount) &&
    isNonNegativeInteger(candidate.totalAttempts) &&
    isOptionalDateTime(candidate.lastCalculatedAt)
  );
}

function isStudentProfileTopic(value: unknown): value is StudentProfileTopic {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.topicId) &&
    isNonEmptyString(candidate.topicName) &&
    isPercentage(candidate.score) &&
    isMasteryStatus(candidate.status)
  );
}

function isStudentProfileMasteryTopic(
  value: unknown,
): value is StudentProfileMasteryTopic {
  if (!isStudentProfileTopic(value)) return false;
  const candidate = value as unknown as Record<string, unknown>;
  return (
    isNonEmptyString(candidate.topicCode) &&
    isNonNegativeInteger(candidate.attemptCount) &&
    isOptionalDateTime(candidate.calculatedAt)
  );
}

function isStudentProfileHistoryItem(
  value: unknown,
): value is StudentProfileHistoryItem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.topicId) &&
    isNonEmptyString(candidate.topicName) &&
    isOptionalPercentage(candidate.previousScore) &&
    isOptionalPercentage(candidate.newScore) &&
    isOptionalMasteryStatus(candidate.previousStatus) &&
    isOptionalMasteryStatus(candidate.newStatus) &&
    (candidate.reason === null || isNonEmptyString(candidate.reason)) &&
    isOptionalDateTime(candidate.occurredAt)
  );
}

function isStudentProfileWorksheet(
  value: unknown,
): value is StudentProfileWorksheet {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.worksheetId) &&
    isNonEmptyString(candidate.title) &&
    (candidate.assignmentType === "CLASS" ||
      candidate.assignmentType === "STUDENT") &&
    isOptionalPositiveNumber(candidate.classId) &&
    isOptionalDateTime(candidate.assignedAt) &&
    isOptionalDateTime(candidate.dueAt)
  );
}

function isStudentProfileAlert(value: unknown): value is StudentProfileAlert {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.type) &&
    isNonEmptyString(candidate.severity) &&
    isNonEmptyString(candidate.status) &&
    isNonEmptyString(candidate.title) &&
    isOptionalDateTime(candidate.createdAt)
  );
}

function isStudentProfileReport(value: unknown): value is StudentProfileReport {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.reportCode) &&
    isNonEmptyString(candidate.status) &&
    isOptionalDateTime(candidate.periodStart) &&
    isOptionalDateTime(candidate.periodEnd) &&
    isOptionalDateTime(candidate.generatedAt) &&
    isOptionalDateTime(candidate.finalizedAt)
  );
}

function isTutorOnlyStudentProfile(
  value: unknown,
): value is TutorOnlyStudentProfile {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    Array.isArray(candidate.activeAlerts) &&
    candidate.activeAlerts.every(isStudentProfileAlert) &&
    Array.isArray(candidate.reports) &&
    candidate.reports.every(isStudentProfileReport) &&
    isNonNegativeInteger(candidate.approvedWorksheetCount)
  );
}

function isTutorStudentProfile(value: unknown): value is TutorStudentProfile {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  const learningProfile = candidate.learningProfile;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.fullName) &&
    Array.isArray(candidate.classes) &&
    candidate.classes.every(isStudentProfileClass) &&
    isStudentProfileMetrics(candidate.metrics) &&
    Array.isArray(candidate.mastery) &&
    candidate.mastery.every(isStudentProfileMasteryTopic) &&
    typeof learningProfile === "object" &&
    learningProfile !== null &&
    Array.isArray((learningProfile as Record<string, unknown>).strengths) &&
    (learningProfile as Record<string, unknown>).strengths instanceof Array &&
    ((learningProfile as Record<string, unknown>).strengths as unknown[]).every(
      isStudentProfileTopic,
    ) &&
    Array.isArray((learningProfile as Record<string, unknown>).focusAreas) &&
    (
      (learningProfile as Record<string, unknown>).focusAreas as unknown[]
    ).every(isStudentProfileTopic) &&
    Array.isArray(candidate.history) &&
    candidate.history.every(isStudentProfileHistoryItem) &&
    Array.isArray(candidate.worksheets) &&
    candidate.worksheets.every(isStudentProfileWorksheet) &&
    (candidate.tutorOnly === null ||
      isTutorOnlyStudentProfile(candidate.tutorOnly))
  );
}

function isTutorNote(value: unknown): value is TutorNote {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isPositiveNumber(candidate.studentId) &&
    isNonEmptyString(candidate.content) &&
    isDateTime(candidate.createdAt) &&
    isDateTime(candidate.updatedAt)
  );
}

export function parseTutorStudent(payload: unknown): TutorStudent {
  if (!isTutorStudent(payload)) {
    throw new Error(
      "The learning service returned an invalid student. Please try again.",
    );
  }
  return payload;
}

export function parseTutorStudents(payload: unknown): TutorStudent[] {
  if (!Array.isArray(payload) || !payload.every(isTutorStudent)) {
    throw new Error(
      "The learning service returned an invalid student list. Please try again.",
    );
  }
  return payload;
}

export function parseAvailableStudentAccounts(
  payload: unknown,
): AvailableStudentAccount[] {
  if (!Array.isArray(payload) || !payload.every(isAvailableStudentAccount)) {
    throw new Error(
      "The learning service returned an invalid Student account list. Please try again.",
    );
  }
  return payload;
}

export function parseTutorStudentProfile(
  payload: unknown,
): TutorStudentProfile {
  if (!isTutorStudentProfile(payload)) {
    throw new Error(
      "The learning service returned an invalid student profile. Please try again.",
    );
  }
  return payload;
}

export function parseStudentSelfProfile(payload: unknown): StudentSelfProfile {
  const profile = parseTutorStudentProfile(payload);
  if (profile.tutorOnly !== null) {
    throw new Error(
      "The learning service returned an invalid student profile. Please try again.",
    );
  }
  return { ...profile, tutorOnly: null };
}

export function parseTutorNote(payload: unknown): TutorNote {
  if (!isTutorNote(payload)) {
    throw new Error(
      "The learning service returned an invalid tutor note. Please try again.",
    );
  }
  return payload;
}

export function parseTutorNotes(payload: unknown): TutorNote[] {
  if (!Array.isArray(payload) || !payload.every(isTutorNote)) {
    throw new Error(
      "The learning service returned an invalid tutor note list. Please try again.",
    );
  }
  return payload;
}

function errorFields(payload: Record<string, unknown>): Record<string, string> {
  if (typeof payload.fields !== "object" || payload.fields === null) return {};
  return Object.fromEntries(
    Object.entries(payload.fields).filter((entry): entry is [string, string] =>
      isNonEmptyString(entry[1]),
    ),
  );
}

async function responseError(response: Response): Promise<StudentApiError> {
  try {
    const payload = (await response.json()) as unknown;
    if (typeof payload === "object" && payload !== null) {
      const record = payload as Record<string, unknown>;
      if (isNonEmptyString(record.message)) {
        return new StudentApiError(
          record.message,
          response.status,
          errorFields(record),
        );
      }
    }
  } catch {
    // Use the status fallback for non-JSON or empty error responses.
  }
  return new StudentApiError(
    `The learning service could not complete your student request (status ${response.status}).`,
    response.status,
  );
}

function authHeaders(includeJsonContentType = false): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    ...(includeJsonContentType ? { "Content-Type": "application/json" } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function fetchTutorStudents(
  classId?: number,
): Promise<TutorStudent[]> {
  const suffix = classId === undefined ? "" : `?classId=${classId}`;
  const response = await fetch(
    `${LEARNING_API_URL}${STUDENT_LIST_PATH}${suffix}`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseTutorStudents(await response.json());
}

/** Searches real STUDENT-role accounts that are not already claimed by a Tutor. */
export async function fetchAvailableStudentAccounts(
  search = "",
): Promise<AvailableStudentAccount[]> {
  const query = search.trim();
  const suffix = query ? `?search=${encodeURIComponent(query)}` : "";
  const response = await fetch(
    `${LEARNING_API_URL}/api/learning/tutor/student-accounts${suffix}`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseAvailableStudentAccounts(await response.json());
}

export async function fetchTutorStudent(
  studentId: number,
): Promise<TutorStudent> {
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    throw new StudentApiError("The student reference is invalid.", 400);
  }
  const response = await fetch(
    `${LEARNING_API_URL}${STUDENT_LIST_PATH}/${studentId}`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseTutorStudent(await response.json());
}

export async function fetchTutorStudentProfile(
  studentId: number,
): Promise<TutorStudentProfile> {
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    throw new StudentApiError("The student reference is invalid.", 400);
  }
  const response = await fetch(
    `${LEARNING_API_URL}${STUDENT_LIST_PATH}/${studentId}/profile`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseTutorStudentProfile(await response.json());
}

/** Loads the profile belonging to the authenticated Student; no student id is client-controlled. */
export async function fetchStudentSelfProfile(): Promise<StudentSelfProfile> {
  const response = await fetch(
    `${LEARNING_API_URL}/api/learning/student/profile`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseStudentSelfProfile(await response.json());
}

function notePath(studentId: number, noteId?: number) {
  if (
    !Number.isSafeInteger(studentId) ||
    studentId <= 0 ||
    (noteId !== undefined && (!Number.isSafeInteger(noteId) || noteId <= 0))
  ) {
    throw new StudentApiError("The student or note reference is invalid.", 400);
  }
  return `${STUDENT_LIST_PATH}/${studentId}/notes${noteId === undefined ? "" : `/${noteId}`}`;
}

export async function fetchTutorNotes(studentId: number): Promise<TutorNote[]> {
  const response = await fetch(`${LEARNING_API_URL}${notePath(studentId)}`, {
    headers: authHeaders(),
  });
  if (!response.ok) throw await responseError(response);
  return parseTutorNotes(await response.json());
}

async function mutateTutorNote(
  studentId: number,
  noteId: number | undefined,
  method: "POST" | "PUT",
  request: TutorNoteMutationRequest,
): Promise<TutorNote> {
  const response = await fetch(
    `${LEARNING_API_URL}${notePath(studentId, noteId)}`,
    {
      method,
      headers: authHeaders(true),
      body: JSON.stringify(request),
    },
  );
  if (!response.ok) throw await responseError(response);
  return parseTutorNote(await response.json());
}

export function createTutorNote(
  studentId: number,
  request: TutorNoteMutationRequest,
): Promise<TutorNote> {
  return mutateTutorNote(studentId, undefined, "POST", request);
}

export function updateTutorNote(
  studentId: number,
  noteId: number,
  request: TutorNoteMutationRequest,
): Promise<TutorNote> {
  return mutateTutorNote(studentId, noteId, "PUT", request);
}

export async function deleteTutorNote(
  studentId: number,
  noteId: number,
): Promise<void> {
  const response = await fetch(
    `${LEARNING_API_URL}${notePath(studentId, noteId)}`,
    {
      method: "DELETE",
      headers: authHeaders(),
    },
  );
  if (!response.ok) throw await responseError(response);
}

async function mutateTutorStudent(
  path: string,
  method: "POST" | "PUT",
  request: StudentMutationRequest,
): Promise<TutorStudent> {
  const response = await fetch(`${LEARNING_API_URL}${path}`, {
    method,
    headers: authHeaders(true),
    body: JSON.stringify(request),
  });
  if (!response.ok) throw await responseError(response);
  return parseTutorStudent(await response.json());
}

export function createTutorStudent(
  request: StudentMutationRequest,
): Promise<TutorStudent> {
  return mutateTutorStudent(STUDENT_LIST_PATH, "POST", request);
}

export function updateTutorStudent(
  studentId: number,
  request: StudentMutationRequest,
): Promise<TutorStudent> {
  if (!Number.isSafeInteger(studentId) || studentId <= 0) {
    return Promise.reject(
      new StudentApiError("The student reference is invalid.", 400),
    );
  }
  return mutateTutorStudent(
    `${STUDENT_LIST_PATH}/${studentId}`,
    "PUT",
    request,
  );
}

/** Convert a class response into the membership shape used by student responses. */
export function classMembershipFor(
  tutorClass: TutorClass,
): StudentClassMembership {
  return {
    id: tutorClass.id,
    className: tutorClass.className,
    subject: tutorClass.subject,
    level: tutorClass.level,
  };
}
