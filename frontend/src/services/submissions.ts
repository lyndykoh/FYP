export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
const ACCEPTED_TYPES = new Set(["application/pdf", "image/jpeg", "image/png"]);
const gradingUrl =
  process.env.NEXT_PUBLIC_GRADING_API_URL || "http://localhost:8082";

export type UploadPage = {
  id: string;
  file: File;
  previewUrl: string | null;
  rotation: 0 | 90 | 180 | 270;
  warning: string | null;
};

export type ImageQualityWarningCode =
  | "LOW_RESOLUTION"
  | "LOW_CONTRAST"
  | "BLURRY"
  | "WRITING_TOO_SMALL";

export type EstimatedWritingSize = {
  coverage: number;
  widthFraction: number;
  heightFraction: number;
};

export type ImageQualityMetrics = {
  contrast: number;
  sharpness: number;
  estimatedWritingSize: EstimatedWritingSize;
  warnings: ImageQualityWarningCode[];
};

export type UploadImagePreflight = {
  assessmentStatus: "ready" | "retake_recommended" | "unavailable";
  mediaType: string;
  width: number | null;
  height: number | null;
  quality: ImageQualityMetrics | null;
  guidance: string[];
};

export type ImagePixelData = {
  width: number;
  height: number;
  data: Uint8ClampedArray;
};

export type ImageDimensions = {
  width: number;
  height: number;
};

const QUALITY_THRESHOLDS = {
  minimumWidth: 900,
  minimumHeight: 900,
  minimumPixels: 1_000_000,
  minimumContrast: 18,
  minimumSharpness: 30,
  minimumWritingCoverage: 0.001,
  minimumWritingWidth: 0.08,
  minimumWritingHeight: 0.025,
} as const;

const MAX_ANALYSIS_EDGE = 1_600;

const QUALITY_GUIDANCE: Record<ImageQualityWarningCode, string> = {
  LOW_RESOLUTION: "Move closer so the worksheet and writing are larger in the photo.",
  LOW_CONTRAST: "Use brighter, even lighting and avoid shadows across the writing.",
  BLURRY: "Hold the camera steady and tap the writing to focus before taking the photo.",
  WRITING_TOO_SMALL: "Move closer or crop to the worksheet so the writing is easier to read.",
};

export type OcrPage = {
  pageId: number;
  extractionId: number;
  text: string;
  confidence: number;
  status: "READY" | "REQUIRES_REVIEW" | "UNREADABLE";
};

export class SubmissionApiError extends Error {
  constructor(
    message: string,
    readonly status = 0,
  ) {
    super(message);
    this.name = "SubmissionApiError";
  }
}

function getFileFingerprint(file: File): string {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function createUploadPageId(file: File, index: number): string {
  return `${getFileFingerprint(file)}:${index}`;
}

export function isAcceptedUpload(file: File) {
  return ACCEPTED_TYPES.has(file.type);
}

export function validateUploadFiles(
  files: File[],
  existing: UploadPage[] = [],
): { pages: UploadPage[]; errors: string[] } {
  const seen = new Set(existing.map((page) => getFileFingerprint(page.file)));
  const pages: UploadPage[] = [];
  const errors: string[] = [];

  files.forEach((file, index) => {
    if (!isAcceptedUpload(file)) {
      errors.push(`${file.name}: use a JPG, PNG or PDF.`);
    } else if (file.size === 0) {
      errors.push(`${file.name}: this file is empty.`);
    } else if (file.size > MAX_UPLOAD_BYTES) {
      errors.push(`${file.name}: files must be 20 MB or smaller.`);
    } else if (seen.has(getFileFingerprint(file))) {
      errors.push(`${file.name}: this page was already added.`);
    } else {
      seen.add(getFileFingerprint(file));
      const isImage = file.type.startsWith("image/");

      pages.push({
        id: createUploadPageId(file, index),
        file,
        previewUrl: isImage ? URL.createObjectURL(file) : null,
        rotation: 0,
        warning: null,
      });
    }
  });

  return { pages, errors };
}

export function releasePagePreview(page: UploadPage) {
  if (page.previewUrl) {
    URL.revokeObjectURL(page.previewUrl);
  }
}

function grayscaleAt(data: Uint8ClampedArray, pixelIndex: number): number {
  const dataIndex = pixelIndex * 4;
  return (
    data[dataIndex] * 0.2126 +
    data[dataIndex + 1] * 0.7152 +
    data[dataIndex + 2] * 0.0722
  );
}

function calculateSampleStep(width: number, height: number): number {
  const totalPixels = width * height;
  return Math.max(1, Math.ceil(Math.sqrt(totalPixels / 250_000)));
}

function createQualityGuidance(
  warnings: ImageQualityWarningCode[],
): string[] {
  return warnings.map((warning) => QUALITY_GUIDANCE[warning]);
}

/**
 * Measures pixel-level image quality only. It does not identify student answers
 * and it never stores, uploads, or logs the supplied pixels.
 */
export function analyzeImagePixels(
  image: ImagePixelData,
  sourceDimensions: ImageDimensions = image,
): ImageQualityMetrics {
  const { data, width, height } = image;
  const expectedLength = width * height * 4;

  if (
    !Number.isSafeInteger(width) ||
    !Number.isSafeInteger(height) ||
    width <= 0 ||
    height <= 0 ||
    !Number.isSafeInteger(sourceDimensions.width) ||
    !Number.isSafeInteger(sourceDimensions.height) ||
    sourceDimensions.width <= 0 ||
    sourceDimensions.height <= 0 ||
    data.length < expectedLength
  ) {
    throw new Error("Image pixel data is invalid.");
  }

  const sampleStep = calculateSampleStep(width, height);
  let sampleCount = 0;
  let grayscaleSum = 0;
  let grayscaleSquaredSum = 0;
  let darkPixelCount = 0;
  let contentMinX = width;
  let contentMinY = height;
  let contentMaxX = -1;
  let contentMaxY = -1;
  let sharpnessCount = 0;
  let laplacianSquaredSum = 0;

  for (let y = 0; y < height; y += sampleStep) {
    for (let x = 0; x < width; x += sampleStep) {
      const pixelIndex = y * width + x;
      const grayscale = grayscaleAt(data, pixelIndex);
      sampleCount += 1;
      grayscaleSum += grayscale;
      grayscaleSquaredSum += grayscale * grayscale;

      // This deliberately estimates visible ink/content, not the student's
      // answer. Answer bounds require worksheet-aware OCR.
      if (grayscale < 180) {
        darkPixelCount += 1;
        contentMinX = Math.min(contentMinX, x);
        contentMinY = Math.min(contentMinY, y);
        contentMaxX = Math.max(contentMaxX, x);
        contentMaxY = Math.max(contentMaxY, y);
      }

      if (
        x >= sampleStep &&
        y >= sampleStep &&
        x + sampleStep < width &&
        y + sampleStep < height &&
        grayscale < 220
      ) {
        const left = grayscaleAt(data, pixelIndex - sampleStep);
        const right = grayscaleAt(data, pixelIndex + sampleStep);
        const above = grayscaleAt(data, pixelIndex - sampleStep * width);
        const below = grayscaleAt(data, pixelIndex + sampleStep * width);
        const laplacian = 4 * grayscale - left - right - above - below;

        // Ignore smooth shadows: they are dark but do not describe whether
        // strokes are in focus. A focused pen stroke has a strong local edge.
        if (Math.abs(laplacian) >= 10) {
          sharpnessCount += 1;
          laplacianSquaredSum += laplacian * laplacian;
        }
      }
    }
  }

  const mean = grayscaleSum / sampleCount;
  const contrast = Math.sqrt(
    Math.max(0, grayscaleSquaredSum / sampleCount - mean * mean),
  );
  const sharpness = sharpnessCount
    ? laplacianSquaredSum / sharpnessCount
    : 0;
  const hasContent = contentMaxX >= contentMinX && contentMaxY >= contentMinY;
  const estimatedWritingSize = hasContent
    ? {
        coverage: darkPixelCount / sampleCount,
        widthFraction: (contentMaxX - contentMinX + sampleStep) / width,
        heightFraction: (contentMaxY - contentMinY + sampleStep) / height,
      }
    : { coverage: 0, widthFraction: 0, heightFraction: 0 };

  const warnings: ImageQualityWarningCode[] = [];
  if (
    sourceDimensions.width < QUALITY_THRESHOLDS.minimumWidth ||
    sourceDimensions.height < QUALITY_THRESHOLDS.minimumHeight ||
    sourceDimensions.width * sourceDimensions.height <
      QUALITY_THRESHOLDS.minimumPixels
  ) {
    warnings.push("LOW_RESOLUTION");
  }
  if (contrast < QUALITY_THRESHOLDS.minimumContrast) {
    warnings.push("LOW_CONTRAST");
  }
  if (sharpness < QUALITY_THRESHOLDS.minimumSharpness) {
    warnings.push("BLURRY");
  }
  if (
    estimatedWritingSize.coverage < QUALITY_THRESHOLDS.minimumWritingCoverage ||
    estimatedWritingSize.widthFraction < QUALITY_THRESHOLDS.minimumWritingWidth ||
    estimatedWritingSize.heightFraction < QUALITY_THRESHOLDS.minimumWritingHeight
  ) {
    warnings.push("WRITING_TOO_SMALL");
  }

  return { contrast, sharpness, estimatedWritingSize, warnings };
}

function createCanvas(width: number, height: number): HTMLCanvasElement {
  if (typeof document === "undefined") {
    throw new Error("Image quality checks require a browser.");
  }

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

async function decodeImagePixels(file: File): Promise<{
  width: number;
  height: number;
  pixels: ImagePixelData;
}> {
  if (typeof createImageBitmap !== "function") {
    throw new Error("Image quality checks are unavailable in this browser.");
  }

  const bitmap = await createImageBitmap(file);

  try {
    const scale = Math.min(
      1,
      MAX_ANALYSIS_EDGE / Math.max(bitmap.width, bitmap.height),
    );
    const analysisWidth = Math.max(1, Math.round(bitmap.width * scale));
    const analysisHeight = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = createCanvas(analysisWidth, analysisHeight);
    const context = canvas.getContext("2d", { willReadFrequently: true });

    if (!context) {
      throw new Error("Image quality checks are unavailable in this browser.");
    }

    context.drawImage(bitmap, 0, 0, analysisWidth, analysisHeight);

    return {
      width: bitmap.width,
      height: bitmap.height,
      pixels: {
        width: analysisWidth,
        height: analysisHeight,
        data: context.getImageData(0, 0, analysisWidth, analysisHeight).data,
      },
    };
  } finally {
    bitmap.close();
  }
}

export async function preflightUploadImage(
  file: File,
): Promise<UploadImagePreflight> {
  if (file.type === "application/pdf") {
    return {
      assessmentStatus: "unavailable",
      mediaType: file.type,
      width: null,
      height: null,
      quality: null,
      guidance: ["Photo quality is not assessed for this PDF."],
    };
  }

  if (file.type !== "image/jpeg" && file.type !== "image/png") {
    return {
      assessmentStatus: "unavailable",
      mediaType: file.type,
      width: null,
      height: null,
      quality: null,
      guidance: ["This file type cannot be checked before upload."],
    };
  }

  try {
    const decoded = await decodeImagePixels(file);
    const quality = analyzeImagePixels(decoded.pixels, decoded);

    return {
      assessmentStatus:
        quality.warnings.length > 0 ? "retake_recommended" : "ready",
      mediaType: file.type,
      width: decoded.width,
      height: decoded.height,
      quality,
      guidance: createQualityGuidance(quality.warnings),
    };
  } catch {
    return {
      assessmentStatus: "unavailable",
      mediaType: file.type,
      width: null,
      height: null,
      quality: null,
      guidance: [
        "Photo quality could not be checked on this device. You can still upload it.",
      ],
    };
  }
}

function getAuthorizationHeaders(): HeadersInit {
  const token =
    typeof window === "undefined" ? null : localStorage.getItem("jwt_token");

  return token ? { Authorization: `Bearer ${token}` } : {};
}
export type SubmissionDocument = {
  id: number;
  classId: number | null;
  studentId: number;
  worksheetId: number;
  uploadedByTutorId: number | null;
  status: "UPLOADING" | "READY" | "SUBMITTED_FOR_REVIEW";
  createdAt: string;
  pages: OcrPage[];
};

function parseSubmissionDocument(value: unknown): SubmissionDocument {
  if (!value || typeof value !== "object") {
    throw new SubmissionApiError(
      "The submission document response is invalid.",
    );
  }

  const raw = value as Record<string, unknown>;

  const hasInvalidDocumentDetails =
    !Number.isSafeInteger(raw.id) ||
    (raw.id as number) <= 0 ||
    !Number.isSafeInteger(raw.studentId) ||
    (raw.studentId as number) <= 0 ||
    !Number.isSafeInteger(raw.worksheetId) ||
    (raw.worksheetId as number) <= 0 ||
    (raw.classId !== null &&
      (!Number.isSafeInteger(raw.classId) || (raw.classId as number) <= 0)) ||
    (raw.uploadedByTutorId !== null &&
      (!Number.isSafeInteger(raw.uploadedByTutorId) ||
        (raw.uploadedByTutorId as number) <= 0)) ||
    (raw.status !== "UPLOADING" &&
      raw.status !== "READY" &&
      raw.status !== "SUBMITTED_FOR_REVIEW") ||
    typeof raw.createdAt !== "string" ||
    !Array.isArray(raw.pages);

  if (hasInvalidDocumentDetails) {
    throw new SubmissionApiError(
      "The submission document response is invalid.",
    );
  }

  const pageIds = new Set<number>();
  const pages = (raw.pages as unknown[]).map((value): OcrPage => {
    if (!value || typeof value !== "object") {
      throw new SubmissionApiError(
        "The submission document response is invalid.",
      );
    }

    const page = value as Record<string, unknown>;
    const hasInvalidPageDetails =
      !Number.isSafeInteger(page.id) ||
      (page.id as number) <= 0 ||
      pageIds.has(page.id as number) ||
      !Number.isSafeInteger(page.extractionId) ||
      (page.extractionId as number) <= 0 ||
      typeof page.text !== "string" ||
      typeof page.confidence !== "number" ||
      (page.status !== "READY" &&
        page.status !== "REQUIRES_REVIEW" &&
        page.status !== "UNREADABLE");

    if (hasInvalidPageDetails) {
      throw new SubmissionApiError(
        "The submission document response is invalid.",
      );
    }

    pageIds.add(page.id as number);

    return {
      pageId: page.id as number,
      extractionId: page.extractionId as number,
      text: page.text as string,
      confidence: page.confidence as number,
      status: page.status as OcrPage["status"],
    };
  });

  return {
    id: raw.id as number,
    classId: raw.classId as number | null,
    studentId: raw.studentId as number,
    worksheetId: raw.worksheetId as number,
    uploadedByTutorId: raw.uploadedByTutorId as number | null,
    status: raw.status as SubmissionDocument["status"],
    createdAt: raw.createdAt as string,
    pages,
  };
}

export async function createOcrDocument(input: {
  classId?: number;
  studentId: number;
  worksheetId: number;
  worksheetQuestionId?: number;
  pages: UploadPage[];
}): Promise<SubmissionDocument> {
  const hasInvalidContext =
    (input.classId !== undefined &&
      (!Number.isSafeInteger(input.classId) || input.classId <= 0)) ||
    !Number.isSafeInteger(input.studentId) ||
    input.studentId <= 0 ||
    !Number.isSafeInteger(input.worksheetId) ||
    input.worksheetId <= 0 ||
    !input.pages.length;

  if (hasInvalidContext) {
    throw new SubmissionApiError("Submission details are invalid.", 400);
  }

  const form = new FormData();
  form.append("studentId", String(input.studentId));
  form.append("worksheetId", String(input.worksheetId));

  if (input.classId !== undefined) {
    form.append("classId", String(input.classId));
  }

  if (input.worksheetQuestionId) {
    form.append("worksheetQuestionId", String(input.worksheetQuestionId));
  }

  input.pages.forEach((page) => form.append("files", page.file));

  const response = await fetch(
    `${gradingUrl}/api/grading/submission-documents`,
    {
      method: "POST",
      headers: getAuthorizationHeaders(),
      body: form,
    },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;

    throw new SubmissionApiError(
      body?.error || "OCR could not be started.",
      response.status,
    );
  }

  return parseSubmissionDocument(await response.json());
}

export async function fetchSubmissionDocument(
  documentId: number,
): Promise<SubmissionDocument> {
  if (!Number.isSafeInteger(documentId) || documentId <= 0) {
    throw new SubmissionApiError("The submission ID is invalid.", 400);
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/submission-documents/${documentId}`,
    { headers: getAuthorizationHeaders() },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;

    throw new SubmissionApiError(
      body?.error || "Submission document could not be loaded.",
      response.status,
    );
  }

  return parseSubmissionDocument(await response.json());
}

export async function correctOcrExtraction(
  extractionId: number,
  correctedText: string,
): Promise<OcrPage> {
  const response = await fetch(
    `${gradingUrl}/api/grading/ocr-extractions/${extractionId}`,
    {
      method: "PATCH",
      headers: {
        ...getAuthorizationHeaders(),
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ correctedText }),
    },
  );

  if (!response.ok) {
    throw new SubmissionApiError(
      "OCR correction could not be saved.",
      response.status,
    );
  }

  const body = (await response.json()) as {
    id: number;
    text: string;
    confidence: number;
    status: OcrPage["status"];
  };

  return {
    pageId: 0,
    extractionId: body.id,
    text: body.text,
    confidence: body.confidence,
    status: body.status,
  };
}

export type OcrAnswerMapping = {
  extractionId: number;
  questionBankId: number;
};

export type SubmissionForTutorReview = {
  submissionDocumentId: number;
  submissionIds: number[];
  status: "PENDING_REVIEW";
};
export async function submitOcrForTutorReview(
  documentId: number,
  answers: OcrAnswerMapping[],
): Promise<SubmissionForTutorReview> {
  const hasInvalidAnswer = answers.some(
    (answer) =>
      !Number.isSafeInteger(answer.extractionId) ||
      answer.extractionId <= 0 ||
      !Number.isSafeInteger(answer.questionBankId) ||
      answer.questionBankId <= 0,
  );

  if (
    !Number.isSafeInteger(documentId) ||
    documentId <= 0 ||
    !answers.length ||
    hasInvalidAnswer
  ) {
    throw new SubmissionApiError("OCR submission details are invalid.", 400);
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/submission-documents/${documentId}/submit-for-review`,
    {
      method: "POST",
      headers: {
        ...getAuthorizationHeaders(),
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ answers }),
    },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;

    throw new SubmissionApiError(
      body?.error || "The OCR submission could not be sent for Tutor review.",
      response.status,
    );
  }

  const body = (await response.json()) as Record<string, unknown>;
  const hasInvalidResponse =
    !Number.isSafeInteger(body.submissionDocumentId) ||
    body.submissionDocumentId !== documentId ||
    body.status !== "PENDING_REVIEW" ||
    !Array.isArray(body.submissionIds) ||
    !body.submissionIds.length ||
    body.submissionIds.some(
      (id) => !Number.isSafeInteger(id) || (id as number) <= 0,
    );

  if (hasInvalidResponse) {
    throw new SubmissionApiError("The OCR submission response is invalid.");
  }

  return {
    submissionDocumentId: body.submissionDocumentId as number,
    submissionIds: body.submissionIds as number[],
    status: "PENDING_REVIEW",
  };
}

/** OCR and typed answers are persisted through the same canonical submission records. */
export type ManualAnswerEntry = {
  questionBankId: number;
  answer: string;
};
export type ManualAnswerSubmission = {
  studentId: number;
  worksheetId: number;
  classId?: number;
  answers: ManualAnswerEntry[];
  submit: boolean;
};
export type ManualAnswerResponse = {
  submissionDocumentId: number;
  submissionIds: number[];
  status: "DRAFT" | "PENDING_REVIEW";
  inputMethod: "MANUAL";
};
export type ManualAnswerDraft = {
  submissionDocumentId: number | null;
  answers: ManualAnswerEntry[];
  status: "DRAFT" | "PENDING_REVIEW";
  inputMethod: "MANUAL";
};

function parseManualAnswerDraft(value: unknown): ManualAnswerDraft {
  if (!value || typeof value !== "object") {
    throw new SubmissionApiError("The manual answer response is invalid.");
  }

  const body = value as Record<string, unknown>;
  const validId = (item: unknown) =>
    Number.isSafeInteger(item) && (item as number) > 0;
  const hasInvalidAnswer =
    Array.isArray(body.answers) &&
    body.answers.some((item) => {
      if (!item || typeof item !== "object") {
        return true;
      }

      const answer = item as Record<string, unknown>;
      return (
        !validId(answer.questionBankId) || typeof answer.answer !== "string"
      );
    });

  const hasInvalidResponse =
    !(
      body.submissionDocumentId === null || validId(body.submissionDocumentId)
    ) ||
    !Array.isArray(body.answers) ||
    hasInvalidAnswer ||
    body.inputMethod !== "MANUAL" ||
    (body.status !== "DRAFT" && body.status !== "PENDING_REVIEW");

  if (hasInvalidResponse) {
    throw new SubmissionApiError("The manual answer response is invalid.");
  }

  return {
    submissionDocumentId: body.submissionDocumentId as number | null,
    answers: (body.answers as unknown[]).map((item) => {
      const answer = item as Record<string, unknown>;

      return {
        questionBankId: answer.questionBankId as number,
        answer: answer.answer as string,
      };
    }),
    status: body.status as ManualAnswerDraft["status"],
    inputMethod: "MANUAL",
  };
}

export async function fetchManualAnswerDraft(
  input: Pick<ManualAnswerSubmission, "studentId" | "worksheetId" | "classId">,
): Promise<ManualAnswerDraft> {
  const validId = (value: unknown) =>
    Number.isSafeInteger(value) && (value as number) > 0;
  const hasInvalidContext =
    !validId(input.studentId) ||
    !validId(input.worksheetId) ||
    (input.classId !== undefined && !validId(input.classId));

  if (hasInvalidContext) {
    throw new SubmissionApiError("Manual answer details are invalid.", 400);
  }

  const query = new URLSearchParams({
    studentId: String(input.studentId),
    worksheetId: String(input.worksheetId),
  });

  if (input.classId !== undefined) {
    query.set("classId", String(input.classId));
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/submission-documents/manual-answers?${query.toString()}`,
    { headers: getAuthorizationHeaders() },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;

    throw new SubmissionApiError(
      body?.error || "Your answers could not be loaded. Please try again.",
      response.status,
    );
  }

  return parseManualAnswerDraft(await response.json());
}

export async function saveManualAnswers(
  input: ManualAnswerSubmission,
): Promise<ManualAnswerResponse> {
  const validId = (value: unknown) =>
    Number.isSafeInteger(value) && (value as number) > 0;
  const hasInvalidContext =
    !validId(input.studentId) ||
    !validId(input.worksheetId) ||
    (input.classId !== undefined && !validId(input.classId));
  const hasInvalidAnswers =
    !Array.isArray(input.answers) ||
    input.answers.length === 0 ||
    input.answers.some(
      (entry) =>
        !validId(entry.questionBankId) || typeof entry.answer !== "string",
    );
  const hasDuplicateQuestion =
    Array.isArray(input.answers) &&
    new Set(input.answers.map((entry) => entry.questionBankId)).size !==
      input.answers.length;

  if (hasInvalidContext || hasInvalidAnswers || hasDuplicateQuestion) {
    throw new SubmissionApiError("Manual answer details are invalid.", 400);
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/submission-documents/manual-answers`,
    {
      method: "POST",
      headers: {
        ...getAuthorizationHeaders(),
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    },
  );

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      error?: string;
    } | null;

    throw new SubmissionApiError(
      body?.error || "Your answers could not be saved. Please try again.",
      response.status,
    );
  }

  const body = (await response.json()) as Record<string, unknown>;
  const hasInvalidResponse =
    !validId(body.submissionDocumentId) ||
    !Array.isArray(body.submissionIds) ||
    body.submissionIds.some((value) => !validId(value)) ||
    body.inputMethod !== "MANUAL" ||
    (body.status !== "DRAFT" && body.status !== "PENDING_REVIEW");

  if (hasInvalidResponse) {
    throw new SubmissionApiError("The manual answer response is invalid.");
  }

  return {
    submissionDocumentId: body.submissionDocumentId as number,
    submissionIds: body.submissionIds as number[],
    status: body.status as ManualAnswerResponse["status"],
    inputMethod: "MANUAL",
  };
}

export type MarkingReviewStatus = "PENDING_REVIEW" | "FLAGGED" | "APPROVED";
export type DiagnosticCategory =
  "CONCEPT" | "KEYWORD" | "EXPRESSION" | "APPLICATION";
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
export type DiagnosticEvidence = {
  mistakeType: MistakeType;
  category: DiagnosticCategory;
  description: string;
  missingKeywords: string[];
};
export type DiagnosticEvidenceInput = Pick<
  DiagnosticEvidence,
  "mistakeType" | "description" | "missingKeywords"
>;
export type ManualResultRequest = {
  worksheetId: number;
  studentId: number;
  questionBankId: number;
  answer: string;
  marks: number;
  feedback: string;
};
export type ManualResultEntry = {
  questionBankId: number;
  answer: string;
  marks: number;
  feedback: string;
};
export type ManualResultBatchRequest = {
  worksheetId: number;
  studentId: number;
  entries: ManualResultEntry[];
};
export type MarkingReview = {
  id: number;
  studentId: number;
  worksheetId: number;
  worksheetQuestionId: number;
  questionBankId: number;
  extractedAnswer: string;
  modelAnswer: string;
  maxMarks: number;
  aiSuggestedMarks: number | null;
  aiSuggestedOutcome: string | null;
  aiErrorCategory: string | null;
  missingKeywords: string[];
  aiSuggestedFeedback: string | null;
  reviewStatus: MarkingReviewStatus;
  approvedMarks: number | null;
  approvedFeedback: string | null;
  reviewedByUserId: number | null;
  reviewedAt: string | null;
  providerResponseValid: boolean | null;
  diagnosticEvidence: DiagnosticEvidence[];
  history: Array<{
    id: number;
    action: "APPROVED" | "REVISED" | "FLAGGED" | "RESET_TO_AI";
    reviewerUserId: number;
    previousStatus: MarkingReviewStatus;
    newStatus: MarkingReviewStatus;
    previousMarks: number | null;
    newMarks: number | null;
    previousFeedback: string | null;
    newFeedback: string | null;
    createdAt: string;
  }>;
};
export type ManualResultStudentProgress = {
  studentId: number;
  completedQuestions: number;
  results: MarkingReview[];
};
export type ManualResultsResponse = {
  worksheetId: number;
  students: ManualResultStudentProgress[];
};

/** A Student-visible marking result. Final marks and feedback are supplied only after Tutor approval. */
export type StudentWorksheetResultOutcome =
  "CORRECT" | "PARTIAL" | "INCORRECT" | "REVIEW_NEEDED";
export type StudentWorksheetResult = {
  submissionId: number;
  worksheetQuestionId: number;
  questionBankId: number;
  answer: string;
  modelAnswer: string | null;
  maximumMarks: number;
  reviewStatus: MarkingReviewStatus;
  outcome: StudentWorksheetResultOutcome;
  awardedMarks: number | null;
  explanation: string | null;
  reviewedAt: string | null;
};
export type StudentWorksheetResultsResponse = {
  worksheetId: number;
  results: StudentWorksheetResult[];
};

/** A Tutor-confirmed diagnostic item shown in the authenticated Student's review history. */
export type StudentMistakeReview = {
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
  subjectId: number | null;
  subjectName: string | null;
  topicName: string | null;
  occurrenceCount: number;
  status: "CONFIRMED";
};
export type StudentMistakeFilters = {
  subjectId?: number;
  topicId?: number;
  mistakeType?: MistakeType;
  worksheetId?: number;
  from?: string;
  to?: string;
};

const reviewStatuses = new Set<MarkingReviewStatus>([
  "PENDING_REVIEW",
  "FLAGGED",
  "APPROVED",
]);
const studentResultOutcomes = new Set<StudentWorksheetResultOutcome>([
  "CORRECT",
  "PARTIAL",
  "INCORRECT",
  "REVIEW_NEEDED",
]);
const diagnosticCategories = new Set<DiagnosticCategory>([
  "CONCEPT",
  "KEYWORD",
  "EXPRESSION",
  "APPLICATION",
]);
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

type ErrorResponse = { error?: string } | null;

function getFiniteNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function getString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function isNonNegativeNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function isPositiveSafeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function getRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object"
    ? (value as Record<string, unknown>)
    : null;
}

function getInvalidMarkingReviewError(): SubmissionApiError {
  return new SubmissionApiError("The marking review response is invalid.");
}

function parseDiagnosticEvidence(value: unknown): DiagnosticEvidence {
  const evidence = getRecord(value);

  const hasInvalidEvidence =
    !evidence ||
    !mistakeTypes.has(evidence.mistakeType as MistakeType) ||
    !diagnosticCategories.has(evidence.category as DiagnosticCategory) ||
    typeof evidence.description !== "string" ||
    !evidence.description.trim() ||
    !Array.isArray(evidence.missingKeywords) ||
    evidence.missingKeywords.some(
      (keyword) => typeof keyword !== "string" || !keyword.trim(),
    );

  if (hasInvalidEvidence) {
    throw new SubmissionApiError("The marking diagnostic evidence is invalid.");
  }

  return {
    mistakeType: evidence.mistakeType as MistakeType,
    category: evidence.category as DiagnosticCategory,
    description: evidence.description as string,
    missingKeywords: evidence.missingKeywords as string[],
  };
}

function parseMarkingReviewHistoryEntry(
  value: unknown,
): MarkingReview["history"][number] {
  const historyEntry = getRecord(value);

  if (!historyEntry) {
    throw new SubmissionApiError("The marking review history is invalid.");
  }

  const id = getFiniteNumber(historyEntry.id);
  const reviewerUserId = getFiniteNumber(historyEntry.reviewerUserId);
  const hasInvalidHistoryEntry =
    id === null ||
    reviewerUserId === null ||
    !reviewStatuses.has(historyEntry.previousStatus as MarkingReviewStatus) ||
    !reviewStatuses.has(historyEntry.newStatus as MarkingReviewStatus) ||
    typeof historyEntry.action !== "string" ||
    typeof historyEntry.createdAt !== "string";

  if (hasInvalidHistoryEntry) {
    throw new SubmissionApiError("The marking review history is invalid.");
  }

  return {
    id,
    action: historyEntry.action as MarkingReview["history"][number]["action"],
    reviewerUserId,
    previousStatus: historyEntry.previousStatus as MarkingReviewStatus,
    newStatus: historyEntry.newStatus as MarkingReviewStatus,
    previousMarks: getFiniteNumber(historyEntry.previousMarks),
    newMarks: getFiniteNumber(historyEntry.newMarks),
    previousFeedback: getString(historyEntry.previousFeedback),
    newFeedback: getString(historyEntry.newFeedback),
    createdAt: historyEntry.createdAt as string,
  };
}

export function parseMarkingReview(value: unknown): MarkingReview {
  const review = getRecord(value);

  if (!review) {
    throw getInvalidMarkingReviewError();
  }

  const reviewIds = [
    review.id,
    review.studentId,
    review.worksheetId,
    review.worksheetQuestionId,
    review.questionBankId,
  ].map(getFiniteNumber);

  const hasInvalidReview =
    reviewIds.some((id) => id === null || id <= 0) ||
    !reviewStatuses.has(review.reviewStatus as MarkingReviewStatus) ||
    typeof review.extractedAnswer !== "string" ||
    typeof review.modelAnswer !== "string" ||
    getFiniteNumber(review.maxMarks) === null ||
    !Array.isArray(review.missingKeywords) ||
    !Array.isArray(review.diagnosticEvidence) ||
    !Array.isArray(review.history);

  if (hasInvalidReview) {
    throw getInvalidMarkingReviewError();
  }

  return {
    id: reviewIds[0]!,
    studentId: reviewIds[1]!,
    worksheetId: reviewIds[2]!,
    worksheetQuestionId: reviewIds[3]!,
    questionBankId: reviewIds[4]!,
    extractedAnswer: review.extractedAnswer as string,
    modelAnswer: review.modelAnswer as string,
    maxMarks: getFiniteNumber(review.maxMarks)!,
    aiSuggestedMarks: getFiniteNumber(review.aiSuggestedMarks),
    aiSuggestedOutcome: getString(review.aiSuggestedOutcome),
    aiErrorCategory: getString(review.aiErrorCategory),
    missingKeywords: (review.missingKeywords as unknown[]).filter(
      (keyword): keyword is string => typeof keyword === "string",
    ),
    aiSuggestedFeedback: getString(review.aiSuggestedFeedback),
    reviewStatus: review.reviewStatus as MarkingReviewStatus,
    approvedMarks: getFiniteNumber(review.approvedMarks),
    approvedFeedback: getString(review.approvedFeedback),
    reviewedByUserId: getFiniteNumber(review.reviewedByUserId),
    reviewedAt: getString(review.reviewedAt),
    providerResponseValid:
      typeof review.providerResponseValid === "boolean"
        ? review.providerResponseValid
        : null,
    diagnosticEvidence: (review.diagnosticEvidence as unknown[]).map(
      parseDiagnosticEvidence,
    ),
    history: (review.history as unknown[]).map(parseMarkingReviewHistoryEntry),
  };
}

export function parseManualResultsResponse(
  value: unknown,
): ManualResultsResponse {
  const response = getRecord(value);

  if (
    !response ||
    !isPositiveSafeInteger(response.worksheetId) ||
    !Array.isArray(response.students)
  ) {
    throw new SubmissionApiError("The manual result response is invalid.");
  }

  const seenStudentIds = new Set<number>();
  const students = response.students.map(
    (value): ManualResultStudentProgress => {
      const progress = getRecord(value);
      const hasInvalidProgress =
        !progress ||
        !isPositiveSafeInteger(progress.studentId) ||
        !Number.isSafeInteger(progress.completedQuestions) ||
        (progress.completedQuestions as number) < 0 ||
        !Array.isArray(progress.results) ||
        seenStudentIds.has(progress.studentId as number);

      if (hasInvalidProgress) {
        throw new SubmissionApiError("The manual result response is invalid.");
      }

      const studentId = progress.studentId as number;
      const completedQuestions = progress.completedQuestions as number;
      seenStudentIds.add(studentId);

      const results = (progress.results as unknown[]).map(parseMarkingReview);
      const hasInvalidResults =
        results.length !== completedQuestions ||
        results.some(
          (result) =>
            result.studentId !== studentId ||
            result.worksheetId !== response.worksheetId,
        );

      if (hasInvalidResults) {
        throw new SubmissionApiError("The manual result response is invalid.");
      }

      return { studentId, completedQuestions, results };
    },
  );

  return { worksheetId: response.worksheetId, students };
}

function parseStudentWorksheetResult(
  value: unknown,
  seenSubmissionIds: Set<number>,
): StudentWorksheetResult {
  const result = getRecord(value);

  if (!result) {
    throw new SubmissionApiError(
      "The student worksheet results response is invalid.",
    );
  }

  const resultIds = [
    result.submissionId,
    result.worksheetQuestionId,
    result.questionBankId,
  ];
  const hasInvalidResult =
    !resultIds.every(isPositiveSafeInteger) ||
    seenSubmissionIds.has(result.submissionId as number) ||
    typeof result.answer !== "string" ||
    (result.modelAnswer !== null && typeof result.modelAnswer !== "string") ||
    !isNonNegativeNumber(result.maximumMarks) ||
    !reviewStatuses.has(result.reviewStatus as MarkingReviewStatus) ||
    !studentResultOutcomes.has(
      result.outcome as StudentWorksheetResultOutcome,
    ) ||
    (result.awardedMarks !== null &&
      (!isNonNegativeNumber(result.awardedMarks) ||
        (result.awardedMarks as number) > (result.maximumMarks as number))) ||
    (result.explanation !== null && typeof result.explanation !== "string") ||
    (result.reviewedAt !== null && typeof result.reviewedAt !== "string");

  if (hasInvalidResult) {
    throw new SubmissionApiError(
      "The student worksheet results response is invalid.",
    );
  }

  const isApproved = result.reviewStatus === "APPROVED";
  const hasLeakedOrMissingApprovalData =
    (isApproved &&
      (result.awardedMarks === null || result.explanation === null)) ||
    (!isApproved &&
      (result.awardedMarks !== null ||
        result.explanation !== null ||
        result.modelAnswer !== null));

  if (hasLeakedOrMissingApprovalData) {
    throw new SubmissionApiError(
      "The student worksheet results response is invalid.",
    );
  }

  const submissionId = result.submissionId as number;
  seenSubmissionIds.add(submissionId);

  return {
    submissionId,
    worksheetQuestionId: result.worksheetQuestionId as number,
    questionBankId: result.questionBankId as number,
    answer: result.answer as string,
    modelAnswer: result.modelAnswer as string | null,
    maximumMarks: result.maximumMarks as number,
    reviewStatus: result.reviewStatus as MarkingReviewStatus,
    outcome: result.outcome as StudentWorksheetResultOutcome,
    awardedMarks: result.awardedMarks as number | null,
    explanation: result.explanation as string | null,
    reviewedAt: result.reviewedAt as string | null,
  };
}

export function parseStudentWorksheetResultsResponse(
  value: unknown,
): StudentWorksheetResultsResponse {
  const response = getRecord(value);

  if (
    !response ||
    !isPositiveSafeInteger(response.worksheetId) ||
    !Array.isArray(response.results)
  ) {
    throw new SubmissionApiError(
      "The student worksheet results response is invalid.",
    );
  }

  const seenSubmissionIds = new Set<number>();
  const results = response.results.map((result) =>
    parseStudentWorksheetResult(result, seenSubmissionIds),
  );

  return { worksheetId: response.worksheetId, results };
}

function getNullablePositiveInteger(value: unknown): number | null {
  if (value === null) {
    return null;
  }

  return isPositiveSafeInteger(value) ? value : null;
}

function getNullableNonBlankText(value: unknown): string | null {
  if (value === null) {
    return null;
  }

  return typeof value === "string" && value.trim() ? value : null;
}

/** Strictly validates the Student mistake-history contract before it reaches the UI. */
export function parseStudentMistakeReviews(
  value: unknown,
): StudentMistakeReview[] {
  if (!Array.isArray(value)) {
    throw new SubmissionApiError("The mistake review response is invalid.");
  }

  return value.map((value) => {
    const record = getRecord(value);

    if (!record) {
      throw new SubmissionApiError("The mistake review response is invalid.");
    }

    const id = getNullablePositiveInteger(record.id);
    const worksheetId = getNullablePositiveInteger(record.worksheetId);
    const worksheetQuestionId = getNullablePositiveInteger(
      record.worksheetQuestionId,
    );
    const questionBankId = getNullablePositiveInteger(record.questionBankId);
    const syllabusTopicId = getNullablePositiveInteger(record.syllabusTopicId);
    const subjectId = getNullablePositiveInteger(record.subjectId);
    const occurrenceCount = record.occurrenceCount;
    const hasInvalidRecord =
      id === null ||
      worksheetId === null ||
      worksheetQuestionId === null ||
      questionBankId === null ||
      !mistakeTypes.has(record.mistakeType as MistakeType) ||
      typeof record.mistakeLabel !== "string" ||
      !record.mistakeLabel.trim() ||
      typeof record.description !== "string" ||
      !record.description.trim() ||
      typeof record.recordedAt !== "string" ||
      !record.recordedAt.trim() ||
      !Number.isSafeInteger(occurrenceCount) ||
      (occurrenceCount as number) < 1 ||
      record.status !== "CONFIRMED" ||
      (record.syllabusTopicId !== null && syllabusTopicId === null) ||
      (record.subjectId !== null && subjectId === null) ||
      (record.syllabusTopicCode !== null &&
        getNullableNonBlankText(record.syllabusTopicCode) === null) ||
      (record.subjectName !== null &&
        getNullableNonBlankText(record.subjectName) === null) ||
      (record.topicName !== null &&
        getNullableNonBlankText(record.topicName) === null);

    if (hasInvalidRecord) {
      throw new SubmissionApiError("The mistake review response is invalid.");
    }

    return {
      id,
      worksheetId,
      worksheetQuestionId,
      questionBankId,
      syllabusTopicId,
      syllabusTopicCode: getNullableNonBlankText(record.syllabusTopicCode),
      mistakeType: record.mistakeType as MistakeType,
      mistakeLabel: record.mistakeLabel as string,
      description: record.description as string,
      recordedAt: record.recordedAt as string,
      subjectId,
      subjectName: getNullableNonBlankText(record.subjectName),
      topicName: getNullableNonBlankText(record.topicName),
      occurrenceCount: occurrenceCount as number,
      status: "CONFIRMED",
    };
  });
}

async function getErrorResponse(response: Response): Promise<ErrorResponse> {
  return response.json().catch(() => null) as Promise<ErrorResponse>;
}

function getJsonRequestHeaders(init?: RequestInit): HeadersInit {
  return {
    ...getAuthorizationHeaders(),
    "Content-Type": "application/json",
    ...(init?.headers || {}),
  };
}

async function requestMarkingReview(
  path: string,
  init?: RequestInit,
): Promise<MarkingReview> {
  const response = await fetch(
    `${gradingUrl}/api/grading/tutor/reviews${path}`,
    {
      ...init,
      headers: getJsonRequestHeaders(init),
    },
  );

  if (!response.ok) {
    const body = await getErrorResponse(response);
    throw new SubmissionApiError(
      body?.error || "The marking review could not be updated.",
      response.status,
    );
  }

  return parseMarkingReview(await response.json());
}

function hasValidSubmissionReviewContext(input: {
  submissionDocumentId: number;
  worksheetQuestionId: number;
  questionBankId: number;
}): boolean {
  return [
    input.submissionDocumentId,
    input.worksheetQuestionId,
    input.questionBankId,
  ].every(isPositiveSafeInteger);
}

export function createMarkingReview(input: {
  submissionDocumentId: number;
  worksheetQuestionId: number;
  questionBankId: number;
}): Promise<MarkingReview> {
  if (!hasValidSubmissionReviewContext(input)) {
    return Promise.reject(
      new SubmissionApiError("The submission review context is invalid.", 400),
    );
  }

  return requestMarkingReview("", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

/** Creates a Tutor-approved fallback result without pretending that OCR source pages exist. */
export function createManualResult(
  input: ManualResultRequest,
): Promise<MarkingReview> {
  const hasInvalidResult =
    ![input.worksheetId, input.studentId, input.questionBankId].every(
      isPositiveSafeInteger,
    ) ||
    !Number.isFinite(input.marks) ||
    input.marks < 0 ||
    !input.answer.trim() ||
    !input.feedback.trim();

  if (hasInvalidResult) {
    return Promise.reject(
      new SubmissionApiError(
        "Student, question, answer, marks and tutor feedback are required.",
        400,
      ),
    );
  }

  return requestMarkingReview("/manual", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

function isValidManualResultEntry(entry: ManualResultEntry): boolean {
  return (
    isPositiveSafeInteger(entry.questionBankId) &&
    Number.isFinite(entry.marks) &&
    entry.marks >= 0 &&
    Boolean(entry.answer.trim()) &&
    Boolean(entry.feedback.trim())
  );
}

function hasValidManualResultBatch(input: ManualResultBatchRequest): boolean {
  if (!Array.isArray(input.entries)) {
    return false;
  }

  const hasDuplicateQuestionBankIds =
    new Set(input.entries.map((entry) => entry.questionBankId)).size !==
    input.entries.length;

  return (
    isPositiveSafeInteger(input.worksheetId) &&
    isPositiveSafeInteger(input.studentId) &&
    input.entries.length > 0 &&
    input.entries.every(isValidManualResultEntry) &&
    !hasDuplicateQuestionBankIds
  );
}

/** Saves all entered question marks for one assigned Student atomically. */
export async function createManualResults(
  input: ManualResultBatchRequest,
): Promise<MarkingReview[]> {
  if (!hasValidManualResultBatch(input)) {
    return Promise.reject(
      new SubmissionApiError(
        "Every entered question needs an answer, valid marks and tutor feedback.",
        400,
      ),
    );
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/tutor/reviews/manual/batch`,
    {
      method: "POST",
      headers: getJsonRequestHeaders(),
      body: JSON.stringify(input),
    },
  );

  if (!response.ok) {
    const body = await getErrorResponse(response);
    throw new SubmissionApiError(
      body?.error || "The manual results could not be saved.",
      response.status,
    );
  }

  const payload = await response.json();

  if (!Array.isArray(payload)) {
    throw new SubmissionApiError("The manual result response is invalid.");
  }

  return payload.map(parseMarkingReview);
}

/** Returns only the current Tutor's manual results, after Learning has owner-scoped the worksheet. */
export async function fetchManualResults(
  worksheetId: number,
): Promise<ManualResultsResponse> {
  if (!isPositiveSafeInteger(worksheetId)) {
    throw new SubmissionApiError("The worksheet id is invalid.", 400);
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/tutor/reviews/manual/worksheets/${worksheetId}`,
    { headers: getAuthorizationHeaders() },
  );

  if (!response.ok) {
    const body = await getErrorResponse(response);
    throw new SubmissionApiError(
      body?.error || "The manual results could not be loaded.",
      response.status,
    );
  }

  return parseManualResultsResponse(await response.json());
}

/** Loads the authenticated Student's worksheet results; no student id is ever accepted from the browser. */
export async function fetchStudentWorksheetResults(
  worksheetId: number,
): Promise<StudentWorksheetResultsResponse> {
  if (!isPositiveSafeInteger(worksheetId)) {
    throw new SubmissionApiError("The worksheet id is invalid.", 400);
  }

  const response = await fetch(
    `${gradingUrl}/api/grading/student/worksheets/${worksheetId}/results`,
    { headers: getAuthorizationHeaders() },
  );

  if (!response.ok) {
    const body = await getErrorResponse(response);
    throw new SubmissionApiError(
      body?.error || "The worksheet results could not be loaded.",
      response.status,
    );
  }

  return parseStudentWorksheetResultsResponse(await response.json());
}

function createStudentMistakeQuery(
  filters: StudentMistakeFilters,
): URLSearchParams {
  const query = new URLSearchParams();

  for (const [name, value] of Object.entries(filters)) {
    if (value === undefined || value === "") {
      continue;
    }

    const isIdentifierFilter =
      name === "subjectId" || name === "topicId" || name === "worksheetId";

    if (isIdentifierFilter && !isPositiveSafeInteger(value)) {
      throw new SubmissionApiError(`The ${name} filter is invalid.`, 400);
    }

    if (name === "mistakeType" && !mistakeTypes.has(value as MistakeType)) {
      throw new SubmissionApiError("The mistake type filter is invalid.", 400);
    }

    query.set(name, String(value));
  }

  return query;
}

/** Loads the signed-in Student's persisted, Tutor-confirmed mistake history with server-side filters. */
export async function fetchStudentMistakes(
  filters: StudentMistakeFilters = {},
): Promise<StudentMistakeReview[]> {
  const query = createStudentMistakeQuery(filters);
  const querySuffix = query.size ? `?${query}` : "";
  const response = await fetch(
    `${gradingUrl}/api/grading/student/mistakes${querySuffix}`,
    { headers: getAuthorizationHeaders() },
  );

  if (!response.ok) {
    const body = await getErrorResponse(response);
    throw new SubmissionApiError(
      body?.error || "Mistake history could not be loaded.",
      response.status,
    );
  }

  return parseStudentMistakeReviews(await response.json());
}

export function fetchMarkingReview(
  submissionId: number,
): Promise<MarkingReview> {
  if (!isPositiveSafeInteger(submissionId)) {
    return Promise.reject(
      new SubmissionApiError("The submission id is invalid.", 400),
    );
  }

  return requestMarkingReview(`/${submissionId}`);
}

function hasValidDiagnosticEvidence(
  diagnosticEvidence: DiagnosticEvidenceInput[],
): boolean {
  return (
    Array.isArray(diagnosticEvidence) &&
    !diagnosticEvidence.some(
      (item) =>
        !mistakeTypes.has(item.mistakeType) ||
        !item.description.trim() ||
        item.missingKeywords.some((keyword) => !keyword.trim()),
    )
  );
}

export function approveMarkingReview(
  submissionId: number,
  marks: number,
  feedback: string,
  diagnosticEvidence: DiagnosticEvidenceInput[] = [],
): Promise<MarkingReview> {
  const hasInvalidApproval =
    !Number.isFinite(marks) ||
    marks < 0 ||
    !feedback.trim() ||
    !hasValidDiagnosticEvidence(diagnosticEvidence);

  if (hasInvalidApproval) {
    return Promise.reject(
      new SubmissionApiError(
        "Marks, tutor feedback and diagnostic evidence are invalid.",
        400,
      ),
    );
  }

  return requestMarkingReview(`/${submissionId}/approve`, {
    method: "POST",
    body: JSON.stringify({ marks, feedback, diagnosticEvidence }),
  });
}

export function flagMarkingReview(
  submissionId: number,
  reason: string,
): Promise<MarkingReview> {
  if (!reason.trim()) {
    return Promise.reject(
      new SubmissionApiError("A flag reason is required.", 400),
    );
  }

  return requestMarkingReview(`/${submissionId}/flag`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export function resetMarkingReview(
  submissionId: number,
): Promise<MarkingReview> {
  return requestMarkingReview(`/${submissionId}/reset`, {
    method: "POST",
    body: "{}",
  });
}
