const GRADING_API_URL =
  process.env.NEXT_PUBLIC_GRADING_API_URL || "http://localhost:8082";
const PENDING_MARKING_REVIEWS_PATH = "/api/grading/tutor/reviews";

export interface PendingMarkingReview {
  submissionId: number;
  submissionDocumentId: number;
  studentId: number;
  studentName: string;
  worksheetId: number;
  worksheetQuestionId: number;
  requestedAt: string;
  sourceAvailable: boolean;
}

export interface SubmittedWorksheetSourcePage {
  id: number;
  pageNumber: number;
  originalFilename: string;
  mediaType: "application/pdf" | "image/jpeg" | "image/png";
  byteSize: number;
}

export interface SubmittedWorksheetSource {
  submissionId: number;
  submissionDocumentId: number;
  studentId: number;
  worksheetId: number;
  sourceType: "PDF" | "IMAGES";
  status: "SUBMITTED_FOR_REVIEW";
  pages: SubmittedWorksheetSourcePage[];
}

export class MarkingReviewQueueApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "MarkingReviewQueueApiError";
  }
}

function isPositiveIdentifier(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isLocalDateTime(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/.test(value) &&
    !Number.isNaN(new Date(`${value}Z`).getTime())
  );
}

function isPendingMarkingReview(value: unknown): value is PendingMarkingReview {
  if (!value || typeof value !== "object") return false;

  const review = value as Record<string, unknown>;
  return (
    isPositiveIdentifier(review.submissionId) &&
    isPositiveIdentifier(review.submissionDocumentId) &&
    isPositiveIdentifier(review.studentId) &&
    isNonEmptyString(review.studentName) &&
    isPositiveIdentifier(review.worksheetId) &&
    isPositiveIdentifier(review.worksheetQuestionId) &&
    isLocalDateTime(review.requestedAt) &&
    typeof review.sourceAvailable === "boolean"
  );
}

export function parsePendingMarkingReviews(
  value: unknown,
): PendingMarkingReview[] {
  if (!Array.isArray(value) || !value.every(isPendingMarkingReview)) {
    throw new MarkingReviewQueueApiError(
      "The pending reviews response is invalid.",
      0,
    );
  }

  return value;
}

function isSourcePage(value: unknown): value is SubmittedWorksheetSourcePage {
  if (!value || typeof value !== "object") return false;

  const page = value as Record<string, unknown>;
  return (
    isPositiveIdentifier(page.id) &&
    isPositiveIdentifier(page.pageNumber) &&
    isNonEmptyString(page.originalFilename) &&
    (page.mediaType === "application/pdf" ||
      page.mediaType === "image/jpeg" ||
      page.mediaType === "image/png") &&
    isPositiveIdentifier(page.byteSize)
  );
}

export function parseSubmittedWorksheetSource(
  value: unknown,
): SubmittedWorksheetSource {
  if (!value || typeof value !== "object") {
    throw new MarkingReviewQueueApiError(
      "The submitted worksheet source response is invalid.",
      0,
    );
  }

  const source = value as Record<string, unknown>;
  const isValid =
    isPositiveIdentifier(source.submissionId) &&
    isPositiveIdentifier(source.submissionDocumentId) &&
    isPositiveIdentifier(source.studentId) &&
    isPositiveIdentifier(source.worksheetId) &&
    (source.sourceType === "PDF" || source.sourceType === "IMAGES") &&
    source.status === "SUBMITTED_FOR_REVIEW" &&
    Array.isArray(source.pages) &&
    source.pages.length > 0 &&
    source.pages.every(isSourcePage);

  if (!isValid) {
    throw new MarkingReviewQueueApiError(
      "The submitted worksheet source response is invalid.",
      0,
    );
  }

  return source as unknown as SubmittedWorksheetSource;
}

function markingReviewRequestHeaders(accept = "application/json"): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: accept,
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function throwQueueError(
  response: Response,
  fallbackMessage: string,
): Promise<never> {
  const payload = (await response.json().catch(() => null)) as {
    error?: unknown;
    message?: unknown;
  } | null;
  const message =
    payload && isNonEmptyString(payload.error)
      ? payload.error
      : payload && isNonEmptyString(payload.message)
        ? payload.message
        : fallbackMessage;
  throw new MarkingReviewQueueApiError(message, response.status);
}

export async function fetchPendingMarkingReviews(): Promise<
  PendingMarkingReview[]
> {
  const response = await fetch(
    `${GRADING_API_URL}${PENDING_MARKING_REVIEWS_PATH}`,
    { headers: markingReviewRequestHeaders() },
  );
  if (!response.ok) {
    return throwQueueError(
      response,
      "Pending reviews could not be loaded. Please try again.",
    );
  }
  return parsePendingMarkingReviews(await response.json());
}

export async function fetchSubmittedWorksheetSource(
  submissionId: number,
): Promise<SubmittedWorksheetSource> {
  if (!isPositiveIdentifier(submissionId)) {
    throw new MarkingReviewQueueApiError("The submission id is invalid.", 400);
  }

  const response = await fetch(
    `${GRADING_API_URL}${PENDING_MARKING_REVIEWS_PATH}/${submissionId}/source`,
    { headers: markingReviewRequestHeaders() },
  );
  if (!response.ok) {
    return throwQueueError(
      response,
      "The submitted worksheet could not be loaded. Please try again.",
    );
  }
  return parseSubmittedWorksheetSource(await response.json());
}

export async function fetchSubmittedWorksheetSourcePageUrl(
  submissionId: number,
  pageId: number,
): Promise<string> {
  if (!isPositiveIdentifier(submissionId) || !isPositiveIdentifier(pageId)) {
    throw new MarkingReviewQueueApiError(
      "The submitted worksheet page reference is invalid.",
      400,
    );
  }

  const response = await fetch(
    `${GRADING_API_URL}${PENDING_MARKING_REVIEWS_PATH}/${submissionId}/source/pages/${pageId}`,
    {
      headers: markingReviewRequestHeaders(
        "application/pdf, image/jpeg, image/png",
      ),
    },
  );
  if (!response.ok) {
    return throwQueueError(
      response,
      "The submitted worksheet page could not be loaded. Please try again.",
    );
  }

  const mediaType = response.headers.get("content-type") || "";
  if (!/^(application\/pdf|image\/(jpeg|png))(?:;|$)/i.test(mediaType)) {
    throw new MarkingReviewQueueApiError(
      "The submitted worksheet page response is invalid.",
      502,
    );
  }
  return URL.createObjectURL(await response.blob());
}
