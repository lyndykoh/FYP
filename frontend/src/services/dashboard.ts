export interface TutorDashboardMetrics {
  activeClassCount: number;
  studentCount: number;
  pendingReviewCount: number;
  needsAttentionStudentCount: number;
  reportsReadyCount: number;
}

export interface TutorDashboardScheduleItem {
  classId: number;
  className: string;
  subject: string;
  level: string;
  startTime: string;
  endTime: string;
}

export type TutorDashboardActivityType =
  "WORKSHEET_ASSIGNED" | "REVIEW_REQUESTED" | "ALERT_CREATED";
export type TutorDashboardActivitySeverity = "INFO" | "WARNING" | "CRITICAL";

export interface TutorDashboardActivity {
  type: TutorDashboardActivityType;
  sourceId: number;
  studentId: number | null;
  studentName: string | null;
  title: string;
  detail: string;
  occurredAt: string;
  severity: TutorDashboardActivitySeverity | null;
}

export interface TutorDashboard {
  timeZone: string;
  today: string;
  metrics: TutorDashboardMetrics;
  todaySchedule: TutorDashboardScheduleItem[];
  recentActivity: TutorDashboardActivity[];
}

export class DashboardApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "DashboardApiError";
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const ACTIVITY_TYPES: readonly TutorDashboardActivityType[] = [
  "WORKSHEET_ASSIGNED",
  "REVIEW_REQUESTED",
  "ALERT_CREATED",
];
const SEVERITIES: readonly TutorDashboardActivitySeverity[] = [
  "INFO",
  "WARNING",
  "CRITICAL",
];

function nonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function nonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function positiveInteger(value: unknown): value is number {
  return nonNegativeInteger(value) && value > 0;
}

function time(value: unknown): value is string {
  if (typeof value !== "string") return false;
  const match = /^(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
  if (!match) return false;
  const [, hour, minute, second = "00"] = match;
  return Number(hour) <= 23 && Number(minute) <= 59 && Number(second) <= 59;
}

function date(value: unknown): value is string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value))
    return false;
  const parsed = new Date(`${value}T00:00:00.000Z`);
  return (
    !Number.isNaN(parsed.getTime()) &&
    parsed.toISOString().slice(0, 10) === value
  );
}

function localDateTime(value: unknown): value is string {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(value)
  )
    return false;
  const [day, timeValue] = value.split("T");
  const normalizedTime = timeValue.split(".")[0];
  return (
    date(day) &&
    time(normalizedTime) &&
    !Number.isNaN(new Date(`${value}Z`).getTime())
  );
}

function laterTime(endTime: string, startTime: string): boolean {
  return endTime > startTime;
}

function isMetrics(value: unknown): value is TutorDashboardMetrics {
  if (!value || typeof value !== "object") return false;
  const metrics = value as Record<string, unknown>;
  return (
    nonNegativeInteger(metrics.activeClassCount) &&
    nonNegativeInteger(metrics.studentCount) &&
    nonNegativeInteger(metrics.pendingReviewCount) &&
    nonNegativeInteger(metrics.needsAttentionStudentCount) &&
    nonNegativeInteger(metrics.reportsReadyCount)
  );
}

function isScheduleItem(value: unknown): value is TutorDashboardScheduleItem {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return (
    positiveInteger(item.classId) &&
    nonEmptyString(item.className) &&
    nonEmptyString(item.subject) &&
    nonEmptyString(item.level) &&
    time(item.startTime) &&
    time(item.endTime) &&
    laterTime(item.endTime, item.startTime)
  );
}

function isActivity(value: unknown): value is TutorDashboardActivity {
  if (!value || typeof value !== "object") return false;
  const activity = value as Record<string, unknown>;
  return (
    ACTIVITY_TYPES.includes(activity.type as TutorDashboardActivityType) &&
    positiveInteger(activity.sourceId) &&
    (activity.studentId === null || positiveInteger(activity.studentId)) &&
    (activity.studentName === null || nonEmptyString(activity.studentName)) &&
    nonEmptyString(activity.title) &&
    nonEmptyString(activity.detail) &&
    localDateTime(activity.occurredAt) &&
    (activity.severity === null ||
      SEVERITIES.includes(activity.severity as TutorDashboardActivitySeverity))
  );
}

export function parseTutorDashboard(value: unknown): TutorDashboard {
  if (!value || typeof value !== "object") {
    throw new Error("The dashboard response is invalid. Please try again.");
  }
  const dashboard = value as Record<string, unknown>;
  if (
    !nonEmptyString(dashboard.timeZone) ||
    !date(dashboard.today) ||
    !isMetrics(dashboard.metrics) ||
    !Array.isArray(dashboard.todaySchedule) ||
    !dashboard.todaySchedule.every(isScheduleItem) ||
    !Array.isArray(dashboard.recentActivity) ||
    !dashboard.recentActivity.every(isActivity)
  ) {
    throw new Error("The dashboard response is invalid. Please try again.");
  }
  return dashboard as unknown as TutorDashboard;
}

function headers(): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function responseError(response: Response): Promise<DashboardApiError> {
  try {
    const payload = (await response.json()) as { message?: unknown };
    if (nonEmptyString(payload.message))
      return new DashboardApiError(payload.message, response.status);
  } catch {
    // A structured server message is optional; retain a useful safe fallback.
  }
  return new DashboardApiError(
    "Dashboard data could not be loaded. Please try again.",
    response.status,
  );
}

export async function fetchTutorDashboard(
  timeZone = "UTC",
): Promise<TutorDashboard> {
  const requestedTimeZone = nonEmptyString(timeZone) ? timeZone : "UTC";
  const response = await fetch(
    `${LEARNING_API_URL}/api/learning/tutor/dashboard?timeZone=${encodeURIComponent(requestedTimeZone)}`,
    { headers: headers() },
  );
  if (!response.ok) throw await responseError(response);
  return parseTutorDashboard(await response.json());
}
