const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const ALERTS_PATH = "/api/learning/tutor/alerts";

export type AlertType =
  "WEAK_TOPIC" | "REPEATED_MISTAKE" | "PENDING_REVIEW" | "REPORT_READY";
export type AlertSeverity = "INFO" | "WARNING" | "CRITICAL";
export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "DISMISSED";

export interface TutorAlert {
  id: number;
  studentId: number;
  studentName: string;
  type: AlertType;
  severity: AlertSeverity;
  status: AlertStatus;
  title: string;
  message: string;
  createdAt: string;
}

export class AlertApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "AlertApiError";
  }
}

const ALERT_TYPES: readonly AlertType[] = [
  "WEAK_TOPIC",
  "REPEATED_MISTAKE",
  "PENDING_REVIEW",
  "REPORT_READY",
];
const ALERT_SEVERITIES: readonly AlertSeverity[] = [
  "INFO",
  "WARNING",
  "CRITICAL",
];
const ALERT_STATUSES: readonly AlertStatus[] = [
  "OPEN",
  "ACKNOWLEDGED",
  "RESOLVED",
  "DISMISSED",
];

function isPositiveIdentifier(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isTutorAlert(value: unknown): value is TutorAlert {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const alert = value as Record<string, unknown>;
  return (
    isPositiveIdentifier(alert.id) &&
    isPositiveIdentifier(alert.studentId) &&
    isNonEmptyString(alert.studentName) &&
    ALERT_TYPES.includes(alert.type as AlertType) &&
    ALERT_SEVERITIES.includes(alert.severity as AlertSeverity) &&
    ALERT_STATUSES.includes(alert.status as AlertStatus) &&
    isNonEmptyString(alert.title) &&
    isNonEmptyString(alert.message) &&
    isNonEmptyString(alert.createdAt)
  );
}

export function parseTutorAlerts(value: unknown): TutorAlert[] {
  if (!Array.isArray(value) || !value.every(isTutorAlert)) {
    throw new AlertApiError("The alerts response is invalid.", 0);
  }

  return value;
}

function alertRequestHeaders(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");

  return {
    Accept: "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function requestAlert(
  path: string,
  init?: RequestInit,
): Promise<unknown> {
  const response = await fetch(`${LEARNING_API_URL}${ALERTS_PATH}${path}`, {
    ...init,
    headers: {
      ...alertRequestHeaders(),
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      message?: unknown;
    } | null;
    const message =
      body && isNonEmptyString(body.message)
        ? body.message
        : "Alerts could not be updated.";

    throw new AlertApiError(message, response.status);
  }

  return response.json();
}

export async function fetchTutorAlerts(): Promise<TutorAlert[]> {
  return parseTutorAlerts(await requestAlert(""));
}

async function updateTutorAlert(
  id: number,
  operation: "resolve" | "dismiss",
): Promise<TutorAlert> {
  if (!isPositiveIdentifier(id)) {
    throw new AlertApiError("The alert reference is invalid.", 400);
  }

  const alert = await requestAlert(`/${id}/${operation}`, { method: "POST" });
  return parseTutorAlerts([alert])[0];
}

export const resolveTutorAlert = (id: number) =>
  updateTutorAlert(id, "resolve");
export const dismissTutorAlert = (id: number) =>
  updateTutorAlert(id, "dismiss");
