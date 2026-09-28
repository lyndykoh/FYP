import type { AuthRole } from "@/lib/auth";

export type ProgressReportStatus = "DRAFT" | "FINAL";
export type ProgressReportSnapshot = Record<string, unknown>;

export interface ProgressReport {
  id: number;
  studentId: number;
  studentName: string;
  reportCode: string;
  periodStart: string;
  periodEnd: string;
  status: ProgressReportStatus;
  snapshot: ProgressReportSnapshot;
  generatedAt: string;
  finalizedAt: string | null;
}

export class ReportApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ReportApiError";
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const statuses: readonly ProgressReportStatus[] = ["DRAFT", "FINAL"];

function nonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function positiveInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function localDate(value: unknown): value is string {
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
  return !Number.isNaN(new Date(`${value}Z`).getTime());
}

function isSnapshot(value: unknown): value is ProgressReportSnapshot {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function parseProgressReport(value: unknown): ProgressReport {
  if (!value || typeof value !== "object") {
    throw new ReportApiError(
      "The progress report response is invalid. Please try again.",
      0,
    );
  }
  const report = value as Record<string, unknown>;
  if (
    !positiveInteger(report.id) ||
    !positiveInteger(report.studentId) ||
    !nonEmptyString(report.studentName) ||
    !nonEmptyString(report.reportCode) ||
    !localDate(report.periodStart) ||
    !localDate(report.periodEnd) ||
    report.periodEnd < report.periodStart ||
    !statuses.includes(report.status as ProgressReportStatus) ||
    !isSnapshot(report.snapshot) ||
    !localDateTime(report.generatedAt) ||
    !(report.finalizedAt === null || localDateTime(report.finalizedAt)) ||
    (report.status === "DRAFT" && report.finalizedAt !== null) ||
    (report.status === "FINAL" && report.finalizedAt === null)
  ) {
    throw new ReportApiError(
      "The progress report response is invalid. Please try again.",
      0,
    );
  }
  return report as unknown as ProgressReport;
}

function headers(accept = "application/json"): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: accept,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function responseError(
  response: Response,
  fallback: string,
): Promise<ReportApiError> {
  try {
    const payload = (await response.json()) as { message?: unknown };
    if (nonEmptyString(payload.message))
      return new ReportApiError(payload.message, response.status);
  } catch {
    // Errors still receive a clear, safe fallback when a proxy strips JSON.
  }
  return new ReportApiError(fallback, response.status);
}

async function fetchReport(
  reportId: number,
  role: AuthRole,
): Promise<ProgressReport> {
  if (!positiveInteger(reportId)) {
    throw new ReportApiError("The progress report reference is invalid.", 400);
  }
  const audience = role === "TUTOR" ? "tutor" : "student";
  const response = await fetch(
    `${LEARNING_API_URL}/api/learning/${audience}/reports/${reportId}`,
    { headers: headers() },
  );
  if (!response.ok)
    throw await responseError(
      response,
      "This progress report could not be loaded. Please try again.",
    );
  return parseProgressReport(await response.json());
}

/**
 * The browser chooses an audience route only; the server independently verifies ownership
 * (Tutor) or the linked Student recipient and intentionally returns a non-enumerating 404.
 */
export function fetchProgressReport(
  reportId: number,
  role: AuthRole,
): Promise<ProgressReport> {
  return fetchReport(reportId, role);
}

export function fetchTutorProgressReport(
  reportId: number,
): Promise<ProgressReport> {
  return fetchReport(reportId, "TUTOR");
}

export function fetchStudentProgressReport(
  reportId: number,
): Promise<ProgressReport> {
  return fetchReport(reportId, "STUDENT");
}

/**
 * Downloads the same immutable snapshot exposed in the report view. The selected route is
 * only an audience hint: the learning service still verifies Tutor ownership or a linked
 * Student recipient before it returns a document.
 */
export async function downloadProgressReportPdf(
  reportId: number,
  role: AuthRole,
): Promise<Blob> {
  if (!positiveInteger(reportId)) {
    throw new ReportApiError("The progress report reference is invalid.", 400);
  }
  const audience = role === "TUTOR" ? "tutor" : "student";
  const response = await fetch(
    `${LEARNING_API_URL}/api/learning/${audience}/reports/${reportId}/pdf`,
    {
      headers: headers("application/pdf"),
    },
  );
  if (!response.ok)
    throw await responseError(
      response,
      "This progress report PDF could not be downloaded. Please try again.",
    );
  const contentType = response.headers.get("content-type");
  if (!contentType || !/^application\/pdf(?:\s*;|$)/i.test(contentType)) {
    throw new ReportApiError(
      "The progress report PDF response is invalid. Please try again.",
      response.status,
    );
  }
  return response.blob();
}
