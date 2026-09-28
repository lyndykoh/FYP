export type ClassStatus = "ACTIVE" | "INACTIVE";

export interface ClassSchedule {
  dayOfWeek:
    | "MONDAY"
    | "TUESDAY"
    | "WEDNESDAY"
    | "THURSDAY"
    | "FRIDAY"
    | "SATURDAY"
    | "SUNDAY";
  startTime: string;
  endTime: string;
}

export interface TutorClass {
  id: number;
  tutorId: number;
  className: string;
  subject: string;
  level: string;
  status: ClassStatus;
  schedules: ClassSchedule[];
}

export interface ClassDetailStudent {
  id: number;
  fullName: string;
  /** Null means the student has not received a mastery calculation yet. */
  overallMastery: number | null;
  masteryRecordCount: number;
}

/**
 * An existing authenticated Student account which a Tutor may enrol in one of
 * their classes.  It deliberately uses the auth-service user ID rather than a
 * caller-created student profile ID.
 */
export interface EligibleClassStudent {
  loginUserId: number;
  fullName: string;
  email: string;
  /** The account directory does not currently maintain a teaching level. */
  level: string | null;
}

export interface ClassMasterySummary {
  /** Null means this class does not have any mastery records yet. */
  averageScore: number | null;
  recordCount: number;
  studentsWithMastery: number;
}

export interface ClassWeakArea {
  topicId: number;
  topicName: string;
  averageScore: number;
  affectedStudentCount: number;
}

export interface ClassInsightAvailability {
  status: ClassInsightStatus;
  message: string;
}

export type ClassInsightStatus = "FRESH" | "STALE" | "REFRESHING" | "FAILED";

/**
 * A persisted, aggregate-only item. These values describe the class; they do
 * not expose individual student evidence or change any learning record.
 */
export interface ClassInsightItem {
  topicId: number;
  topicName: string;
  averageMasteryPercent: number;
  activeStudentCount: number;
  assessedStudentCount: number;
  affectedStudentCount: number;
  weak: boolean;
  suggestedAction: string;
  /** A tutor-controlled display priority, when one has been saved. */
  displayRank: number | null;
  rankingNote: string | null;
}

export interface ClassInsightFeedback {
  id: number;
  feedback: string;
  createdAt: string;
}

/**
 * A read-only persisted insight snapshot. `dataAsOf` is null while the first
 * background refresh is pending or has failed before producing a snapshot.
 */
export interface ClassInsightSnapshot {
  status: ClassInsightStatus;
  message: string;
  dataAsOf: string | null;
  items: ClassInsightItem[];
  feedback: ClassInsightFeedback[];
}

export type ClassWorksheetStatus = "DRAFT" | "APPROVED" | "ARCHIVED";

export interface ClassWorksheet {
  id: number;
  title: string;
  status: ClassWorksheetStatus;
  dueAt: string | null;
  assignedAt: string | null;
}

export interface TutorClassDetail extends TutorClass {
  students: ClassDetailStudent[];
  mastery: ClassMasterySummary;
  weakAreas: ClassWeakArea[];
  insight: ClassInsightAvailability;
  worksheets: ClassWorksheet[];
}

/** The JSON body accepted by the tutor class create and update endpoints. */
export interface ClassMutationRequest {
  className: string;
  subject: string;
  level: string;
  schedules?: ClassSchedule[];
  status?: ClassStatus;
}

export class ClassApiError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields: Record<string, string> = {},
  ) {
    super(message);
    this.name = "ClassApiError";
    this.status = status;
    this.fields = fields;
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const CLASS_LIST_PATH = "/api/learning/tutor/classes";

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isPositiveNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value > 0;
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

function isOptionalDateTime(value: unknown): value is string | null {
  return value === null || isNonEmptyString(value);
}

function isInsightStatus(value: unknown): value is ClassInsightStatus {
  return (
    value === "FRESH" ||
    value === "STALE" ||
    value === "REFRESHING" ||
    value === "FAILED"
  );
}

function isSchedule(value: unknown): value is ClassSchedule {
  if (typeof value !== "object" || value === null) return false;
  const schedule = value as Record<string, unknown>;
  return (
    [
      "MONDAY",
      "TUESDAY",
      "WEDNESDAY",
      "THURSDAY",
      "FRIDAY",
      "SATURDAY",
      "SUNDAY",
    ].includes(schedule.dayOfWeek as string) &&
    isNonEmptyString(schedule.startTime) &&
    isNonEmptyString(schedule.endTime)
  );
}

function isTutorClass(value: unknown): value is TutorClass {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as unknown as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isPositiveNumber(candidate.tutorId) &&
    isNonEmptyString(candidate.className) &&
    isNonEmptyString(candidate.subject) &&
    isNonEmptyString(candidate.level) &&
    (candidate.status === "ACTIVE" || candidate.status === "INACTIVE") &&
    Array.isArray(candidate.schedules) &&
    candidate.schedules.every(isSchedule)
  );
}

export function parseTutorClass(payload: unknown): TutorClass {
  if (!isTutorClass(payload)) {
    throw new Error(
      "The learning service returned an invalid class. Please try again.",
    );
  }

  return payload;
}

export function parseTutorClasses(payload: unknown): TutorClass[] {
  if (!Array.isArray(payload) || !payload.every(isTutorClass)) {
    throw new Error(
      "The learning service returned an invalid class list. Please try again.",
    );
  }

  return payload;
}

function isClassDetailStudent(value: unknown): value is ClassDetailStudent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.fullName) &&
    (candidate.overallMastery === null ||
      isPercentage(candidate.overallMastery)) &&
    isNonNegativeInteger(candidate.masteryRecordCount)
  );
}

function isEligibleClassStudent(value: unknown): value is EligibleClassStudent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.loginUserId) &&
    isNonEmptyString(candidate.fullName) &&
    isNonEmptyString(candidate.email) &&
    (candidate.level === null || isNonEmptyString(candidate.level))
  );
}

export function parseEligibleClassStudents(
  payload: unknown,
): EligibleClassStudent[] {
  if (!Array.isArray(payload) || !payload.every(isEligibleClassStudent)) {
    throw new Error(
      "The learning service returned an invalid eligible student list. Please try again.",
    );
  }

  return payload;
}

function isClassMasterySummary(value: unknown): value is ClassMasterySummary {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    (candidate.averageScore === null || isPercentage(candidate.averageScore)) &&
    isNonNegativeInteger(candidate.recordCount) &&
    isNonNegativeInteger(candidate.studentsWithMastery)
  );
}

function isClassWeakArea(value: unknown): value is ClassWeakArea {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.topicId) &&
    isNonEmptyString(candidate.topicName) &&
    isPercentage(candidate.averageScore) &&
    isNonNegativeInteger(candidate.affectedStudentCount)
  );
}

function isClassInsightAvailability(
  value: unknown,
): value is ClassInsightAvailability {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isInsightStatus(candidate.status) && isNonEmptyString(candidate.message)
  );
}

function isClassInsightItem(value: unknown): value is ClassInsightItem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.topicId) &&
    isNonEmptyString(candidate.topicName) &&
    isPercentage(candidate.averageMasteryPercent) &&
    isNonNegativeInteger(candidate.activeStudentCount) &&
    isNonNegativeInteger(candidate.assessedStudentCount) &&
    isNonNegativeInteger(candidate.affectedStudentCount) &&
    typeof candidate.weak === "boolean" &&
    isNonEmptyString(candidate.suggestedAction) &&
    (candidate.displayRank === null ||
      (isNonNegativeInteger(candidate.displayRank) &&
        candidate.displayRank > 0)) &&
    (candidate.rankingNote === null || isNonEmptyString(candidate.rankingNote))
  );
}

function isClassInsightFeedback(value: unknown): value is ClassInsightFeedback {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.feedback) &&
    isNonEmptyString(candidate.createdAt)
  );
}

function isClassInsightSnapshot(value: unknown): value is ClassInsightSnapshot {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isInsightStatus(candidate.status) &&
    isNonEmptyString(candidate.message) &&
    isOptionalDateTime(candidate.dataAsOf) &&
    Array.isArray(candidate.items) &&
    candidate.items.every(isClassInsightItem) &&
    Array.isArray(candidate.feedback) &&
    candidate.feedback.every(isClassInsightFeedback)
  );
}

function isClassWorksheet(value: unknown): value is ClassWorksheet {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveNumber(candidate.id) &&
    isNonEmptyString(candidate.title) &&
    (candidate.status === "DRAFT" ||
      candidate.status === "APPROVED" ||
      candidate.status === "ARCHIVED") &&
    isOptionalDateTime(candidate.dueAt) &&
    isOptionalDateTime(candidate.assignedAt)
  );
}

/**
 * Spring omits null record fields from JSON. The client model deliberately
 * represents those missing learning values as null so every UI consumer has a
 * stable value without treating a valid empty class as a malformed response.
 */
function normalizeTutorClassDetail(payload: unknown): unknown {
  if (typeof payload !== "object" || payload === null) return payload;
  const detail = payload as Record<string, unknown>;
  return {
    ...detail,
    students: Array.isArray(detail.students)
      ? detail.students.map((student) =>
          typeof student === "object" && student !== null
            ? {
                ...(student as Record<string, unknown>),
                overallMastery:
                  (student as Record<string, unknown>).overallMastery ?? null,
              }
            : student,
        )
      : detail.students,
    mastery:
      typeof detail.mastery === "object" && detail.mastery !== null
        ? {
            ...(detail.mastery as Record<string, unknown>),
            averageScore:
              (detail.mastery as Record<string, unknown>).averageScore ?? null,
          }
        : detail.mastery,
    worksheets: Array.isArray(detail.worksheets)
      ? detail.worksheets.map((worksheet) =>
          typeof worksheet === "object" && worksheet !== null
            ? {
                ...(worksheet as Record<string, unknown>),
                assignedAt:
                  (worksheet as Record<string, unknown>).assignedAt ?? null,
                dueAt: (worksheet as Record<string, unknown>).dueAt ?? null,
              }
            : worksheet,
        )
      : detail.worksheets,
  };
}

function isTutorClassDetail(value: unknown): value is TutorClassDetail {
  if (!isTutorClass(value)) return false;
  const candidate = value as unknown as Record<string, unknown>;
  return (
    Array.isArray(candidate.students) &&
    candidate.students.every(isClassDetailStudent) &&
    isClassMasterySummary(candidate.mastery) &&
    Array.isArray(candidate.weakAreas) &&
    candidate.weakAreas.every(isClassWeakArea) &&
    isClassInsightAvailability(candidate.insight) &&
    Array.isArray(candidate.worksheets) &&
    candidate.worksheets.every(isClassWorksheet)
  );
}

export function parseTutorClassDetail(payload: unknown): TutorClassDetail {
  const normalized = normalizeTutorClassDetail(payload);
  if (!isTutorClassDetail(normalized)) {
    throw new Error(
      "The learning service returned invalid class details. Please try again.",
    );
  }

  return normalized;
}

export function parseTutorClassInsights(
  payload: unknown,
): ClassInsightSnapshot {
  if (!isClassInsightSnapshot(payload)) {
    throw new Error(
      "The learning service returned invalid class insights. Please try again.",
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

async function responseError(response: Response): Promise<ClassApiError> {
  try {
    const payload = (await response.json()) as unknown;
    if (typeof payload === "object" && payload !== null) {
      const record = payload as Record<string, unknown>;
      const message = record.message;
      if (isNonEmptyString(message))
        return new ClassApiError(message, response.status, errorFields(record));
    }
  } catch {
    // Use the status fallback for non-JSON or empty error responses.
  }
  return new ClassApiError(
    `The learning service could not complete your class request (status ${response.status}).`,
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

export async function fetchTutorClasses(): Promise<TutorClass[]> {
  const response = await fetch(`${LEARNING_API_URL}${CLASS_LIST_PATH}`, {
    headers: authHeaders(),
  });

  if (!response.ok) {
    throw await responseError(response);
  }

  return parseTutorClasses(await response.json());
}

export async function fetchTutorClassDetail(
  classId: number,
): Promise<TutorClassDetail> {
  if (!Number.isSafeInteger(classId) || classId <= 0) {
    throw new ClassApiError("The class reference is invalid.", 400);
  }

  const response = await fetch(
    `${LEARNING_API_URL}${CLASS_LIST_PATH}/${classId}`,
    {
      headers: authHeaders(),
    },
  );

  if (!response.ok) {
    throw await responseError(response);
  }

  return parseTutorClassDetail(await response.json());
}

function assertValidClassId(classId: number) {
  if (!Number.isSafeInteger(classId) || classId <= 0) {
    throw new ClassApiError("The class reference is invalid.", 400);
  }
}

export async function fetchEligibleClassStudents(
  classId: number,
): Promise<EligibleClassStudent[]> {
  assertValidClassId(classId);
  const response = await fetch(
    `${LEARNING_API_URL}${CLASS_LIST_PATH}/${classId}/eligible-students`,
    {
      headers: authHeaders(),
    },
  );

  if (!response.ok) {
    throw await responseError(response);
  }

  return parseEligibleClassStudents(await response.json());
}

export async function addStudentToTutorClass(
  classId: number,
  loginUserId: number,
): Promise<void> {
  assertValidClassId(classId);
  if (!Number.isSafeInteger(loginUserId) || loginUserId <= 0) {
    throw new ClassApiError("The student reference is invalid.", 400);
  }

  const response = await fetch(
    `${LEARNING_API_URL}${CLASS_LIST_PATH}/${classId}/students`,
    {
      method: "POST",
      headers: authHeaders(true),
      body: JSON.stringify({ loginUserId }),
    },
  );

  if (!response.ok) {
    throw await responseError(response);
  }
}

export async function removeStudentFromTutorClass(
  classId: number,
  studentProfileId: number,
): Promise<void> {
  assertValidClassId(classId);
  if (!Number.isSafeInteger(studentProfileId) || studentProfileId <= 0) {
    throw new ClassApiError("The student reference is invalid.", 400);
  }

  const response = await fetch(
    `${LEARNING_API_URL}${CLASS_LIST_PATH}/${classId}/students/${studentProfileId}`,
    {
      method: "DELETE",
      headers: authHeaders(),
    },
  );

  if (!response.ok) {
    throw await responseError(response);
  }
}

export async function fetchTutorClassInsights(
  classId: number,
): Promise<ClassInsightSnapshot> {
  assertValidClassId(classId);

  const response = await fetch(
    `${LEARNING_API_URL}${CLASS_LIST_PATH}/${classId}/insights`,
    {
      headers: authHeaders(),
    },
  );

  if (!response.ok) {
    throw await responseError(response);
  }

  return parseTutorClassInsights(await response.json());
}

async function mutateTutorClass(
  path: string,
  method: "POST" | "PUT",
  request: ClassMutationRequest,
): Promise<TutorClass> {
  const response = await fetch(`${LEARNING_API_URL}${path}`, {
    method,
    headers: authHeaders(true),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw await responseError(response);
  }

  return parseTutorClass(await response.json());
}

export function createTutorClass(
  request: ClassMutationRequest,
): Promise<TutorClass> {
  return mutateTutorClass(CLASS_LIST_PATH, "POST", request);
}

export function updateTutorClass(
  classId: number,
  request: ClassMutationRequest,
): Promise<TutorClass> {
  return mutateTutorClass(`${CLASS_LIST_PATH}/${classId}`, "PUT", request);
}
