export type QuestionType =
  | "MULTIPLE_CHOICE"
  | "TRUE_FALSE"
  | "FILL_IN_THE_BLANK"
  | "SHORT_ANSWER"
  | "OPEN_ENDED"
  | "CALCULATION"
  | "DIAGRAM";
export type QuestionDifficulty = "FOUNDATION" | "APPLICATION" | "CHALLENGE";
export type QuestionArchiveState = "ACTIVE" | "ARCHIVED";
export type SyllabusNodeType = "TOPIC" | "SUBTOPIC";

export interface QuestionBankFilters {
  topicId?: number;
  questionType?: QuestionType;
  difficulty?: QuestionDifficulty;
  archiveState?: QuestionArchiveState;
  /** A literal, case- and accent-insensitive search over code, prompt, and keywords. */
  search?: string;
  page?: number;
  size?: number;
}

export interface QuestionBankItem {
  id: number;
  code: string;
  syllabusTopic: {
    id: number;
    code: string;
    name: string;
    nodeType: SyllabusNodeType;
  };
  questionType: QuestionType;
  /** Optional while older test fixtures are migrated; API data always supplies it. */
  difficulty?: QuestionDifficulty;
  prompt: string;
  totalMarks: number;
  archiveState: QuestionArchiveState;
}

export interface QuestionBankPage {
  items: QuestionBankItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface QuestionMarkingComponent {
  position: number;
  description: string;
  marks: number;
  /** Explicit answer evidence for this component; descriptions are never scored. */
  keywords: string[];
}

export interface QuestionImage {
  id: number;
  filename: string;
  contentType: "image/png" | "image/jpeg";
  width: number;
  height: number;
}

/** Complete Tutor-only shape used for question creation and editing. */
export interface TutorQuestion extends QuestionBankItem {
  modelAnswer: string;
  markingComponents: QuestionMarkingComponent[];
  keywords: string[];
  /** Empty for text-only and legacy question responses. */
  images?: QuestionImage[];
  createdAt: string;
  updatedAt: string;
}

export interface QuestionMutationRequest {
  code: string;
  syllabusTopicId: number;
  questionType: QuestionType;
  difficulty?: QuestionDifficulty;
  prompt: string;
  totalMarks: number;
  modelAnswer: string;
  archiveState: QuestionArchiveState;
  markingComponents: Array<{
    description: string;
    marks: number;
    keywords: string[];
  }>;
  keywords: string[];
}

export class QuestionApiError extends Error {
  readonly status: number;
  readonly fields: Record<string, string>;

  constructor(
    message: string,
    status: number,
    fields: Record<string, string> = {},
  ) {
    super(message);
    this.name = "QuestionApiError";
    this.status = status;
    this.fields = fields;
  }
}

const LEARNING_API_URL =
  process.env.NEXT_PUBLIC_LEARNING_API_URL || "http://localhost:8083";
const GRADING_API_URL =
  process.env.NEXT_PUBLIC_GRADING_API_URL || "http://localhost:8082";
const QUESTION_BANK_PATH = "/api/learning/tutor/questions";
const QUESTION_IMPORT_PATH = "/api/learning/tutor/question-imports";
const QUESTION_TYPES: readonly QuestionType[] = [
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN_THE_BLANK",
  "SHORT_ANSWER",
  "OPEN_ENDED",
  "CALCULATION",
  "DIAGRAM",
];
const QUESTION_DIFFICULTIES: readonly QuestionDifficulty[] = [
  "FOUNDATION",
  "APPLICATION",
  "CHALLENGE",
];
const WORKSHEET_DRAFT_QUESTION_IDS_KEY = "worksheet_draft_question_ids";

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function isPositiveId(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isQuestionType(value: unknown): value is QuestionType {
  return (
    typeof value === "string" && QUESTION_TYPES.includes(value as QuestionType)
  );
}

function isQuestionDifficulty(value: unknown): value is QuestionDifficulty {
  return (
    typeof value === "string" &&
    QUESTION_DIFFICULTIES.includes(value as QuestionDifficulty)
  );
}

function isArchiveState(value: unknown): value is QuestionArchiveState {
  return value === "ACTIVE" || value === "ARCHIVED";
}

function isSyllabusTopic(
  value: unknown,
): value is QuestionBankItem["syllabusTopic"] {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveId(candidate.id) &&
    isNonEmptyString(candidate.code) &&
    isNonEmptyString(candidate.name) &&
    (candidate.nodeType === "TOPIC" || candidate.nodeType === "SUBTOPIC")
  );
}

function isQuestionBankItem(value: unknown): value is QuestionBankItem {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveId(candidate.id) &&
    isNonEmptyString(candidate.code) &&
    isSyllabusTopic(candidate.syllabusTopic) &&
    isQuestionType(candidate.questionType) &&
    (candidate.difficulty === undefined ||
      isQuestionDifficulty(candidate.difficulty)) &&
    isNonEmptyString(candidate.prompt) &&
    typeof candidate.totalMarks === "number" &&
    Number.isFinite(candidate.totalMarks) &&
    candidate.totalMarks > 0 &&
    isArchiveState(candidate.archiveState)
  );
}

function isMarkingComponent(value: unknown): value is QuestionMarkingComponent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isNonNegativeInteger(candidate.position) &&
    isNonEmptyString(candidate.description) &&
    typeof candidate.marks === "number" &&
    Number.isFinite(candidate.marks) &&
    candidate.marks > 0 &&
    // Empty is valid for a legacy question; the checker safely awards no marks.
    Array.isArray(candidate.keywords) &&
    candidate.keywords.every(isNonEmptyString)
  );
}

function isQuestionImage(value: unknown): value is QuestionImage {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    isPositiveId(candidate.id) &&
    isNonEmptyString(candidate.filename) &&
    (candidate.contentType === "image/png" ||
      candidate.contentType === "image/jpeg") &&
    isPositiveId(candidate.width) &&
    isPositiveId(candidate.height)
  );
}

function isTutorQuestion(value: unknown): value is TutorQuestion {
  if (!isQuestionBankItem(value)) return false;
  const candidate = value as unknown as Record<string, unknown>;
  return (
    isNonEmptyString(candidate.modelAnswer) &&
    Array.isArray(candidate.markingComponents) &&
    candidate.markingComponents.every(isMarkingComponent) &&
    Array.isArray(candidate.keywords) &&
    candidate.keywords.every(isNonEmptyString) &&
    (candidate.images === undefined ||
      (Array.isArray(candidate.images) &&
        candidate.images.every(isQuestionImage))) &&
    isNonEmptyString(candidate.createdAt) &&
    isNonEmptyString(candidate.updatedAt)
  );
}

export function parseQuestionBankPage(payload: unknown): QuestionBankPage {
  if (typeof payload !== "object" || payload === null)
    throw new Error(
      "The learning service returned an invalid question page. Please try again.",
    );
  const candidate = payload as Record<string, unknown>;
  if (
    !Array.isArray(candidate.items) ||
    !candidate.items.every(isQuestionBankItem) ||
    !isNonNegativeInteger(candidate.page) ||
    !isNonNegativeInteger(candidate.size) ||
    candidate.size < 1 ||
    !isNonNegativeInteger(candidate.totalElements) ||
    !isNonNegativeInteger(candidate.totalPages) ||
    typeof candidate.hasNext !== "boolean"
  ) {
    throw new Error(
      "The learning service returned an invalid question page. Please try again.",
    );
  }
  return candidate as unknown as QuestionBankPage;
}

export function parseTutorQuestion(payload: unknown): TutorQuestion {
  if (!isTutorQuestion(payload)) {
    throw new Error(
      "The learning service returned an invalid question. Please try again.",
    );
  }
  return payload;
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

function mutationHeaders(): HeadersInit {
  return { ...authHeaders(), "Content-Type": "application/json" };
}

function errorFields(payload: Record<string, unknown>): Record<string, string> {
  if (typeof payload.fields !== "object" || payload.fields === null) return {};
  return Object.fromEntries(
    Object.entries(payload.fields).filter((entry): entry is [string, string] =>
      isNonEmptyString(entry[1]),
    ),
  );
}

async function responseError(
  response: Response,
  operation = "load the question bank",
): Promise<QuestionApiError> {
  try {
    const payload = (await response.json()) as unknown;
    if (
      typeof payload === "object" &&
      payload !== null &&
      isNonEmptyString((payload as Record<string, unknown>).message)
    ) {
      const record = payload as Record<string, unknown>;
      return new QuestionApiError(
        record.message as string,
        response.status,
        errorFields(record),
      );
    }
  } catch {
    /* Fallback below for a non-JSON response. */
  }
  return new QuestionApiError(
    `The learning service could not ${operation} (status ${response.status}).`,
    response.status,
  );
}

function queryString(filters: QuestionBankFilters): string {
  const page = filters.page ?? 0;
  const size = filters.size ?? 25;
  if (
    !isNonNegativeInteger(page) ||
    !Number.isSafeInteger(size) ||
    size < 1 ||
    size > 100 ||
    (filters.topicId !== undefined && !isPositiveId(filters.topicId)) ||
    (filters.questionType !== undefined &&
      !isQuestionType(filters.questionType)) ||
    (filters.difficulty !== undefined &&
      !isQuestionDifficulty(filters.difficulty)) ||
    (filters.archiveState !== undefined &&
      !isArchiveState(filters.archiveState)) ||
    (filters.search !== undefined &&
      (typeof filters.search !== "string" ||
        filters.search.trim().length > 120))
  ) {
    throw new QuestionApiError("Question bank filters are invalid.", 400);
  }
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (filters.topicId !== undefined)
    params.set("topicId", String(filters.topicId));
  if (filters.questionType !== undefined)
    params.set("questionType", filters.questionType);
  if (filters.difficulty !== undefined)
    params.set("difficulty", filters.difficulty);
  if (filters.archiveState !== undefined)
    params.set("archiveState", filters.archiveState);
  const search = filters.search?.trim();
  if (search) params.set("search", search);
  return params.toString();
}

export async function fetchTutorQuestions(
  filters: QuestionBankFilters = {},
): Promise<QuestionBankPage> {
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_BANK_PATH}?${queryString(filters)}`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response);
  return parseQuestionBankPage(await response.json());
}

export async function fetchTutorQuestion(
  questionId: number,
): Promise<TutorQuestion> {
  if (!isPositiveId(questionId))
    throw new QuestionApiError("Question reference is invalid.", 400);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_BANK_PATH}/${questionId}`,
    { headers: authHeaders() },
  );
  if (!response.ok) throw await responseError(response, "load this question");
  return parseTutorQuestion(await response.json());
}

function validateMutationRequest(request: QuestionMutationRequest) {
  if (
    !isPositiveId(request.syllabusTopicId) ||
    !isQuestionType(request.questionType) ||
    !isArchiveState(request.archiveState) ||
    !isNonEmptyString(request.code) ||
    !isNonEmptyString(request.prompt) ||
    !isNonEmptyString(request.modelAnswer) ||
    !Number.isFinite(request.totalMarks) ||
    request.totalMarks <= 0 ||
    !Array.isArray(request.markingComponents) ||
    request.markingComponents.length === 0 ||
    !request.markingComponents.every(
      (component) =>
        isNonEmptyString(component.description) &&
        Number.isFinite(component.marks) &&
        component.marks > 0 &&
        Array.isArray(component.keywords) &&
        component.keywords.length > 0 &&
        component.keywords.every(isNonEmptyString),
    ) ||
    !Array.isArray(request.keywords) ||
    !request.keywords.every(isNonEmptyString)
  ) {
    throw new QuestionApiError("Question details are invalid.", 400);
  }
}

async function saveQuestion(
  path: string,
  method: "POST" | "PUT",
  request: QuestionMutationRequest,
): Promise<TutorQuestion> {
  validateMutationRequest(request);
  const response = await fetch(`${LEARNING_API_URL}${path}`, {
    method,
    headers: mutationHeaders(),
    body: JSON.stringify(request),
  });
  if (!response.ok) throw await responseError(response, "save this question");
  return parseTutorQuestion(await response.json());
}

export function createTutorQuestion(
  request: QuestionMutationRequest,
): Promise<TutorQuestion> {
  return saveQuestion(QUESTION_BANK_PATH, "POST", request);
}

export function updateTutorQuestion(
  questionId: number,
  request: QuestionMutationRequest,
): Promise<TutorQuestion> {
  if (!isPositiveId(questionId))
    return Promise.reject(
      new QuestionApiError("Question reference is invalid.", 400),
    );
  return saveQuestion(`${QUESTION_BANK_PATH}/${questionId}`, "PUT", request);
}

function imageHeaders(): HeadersInit {
  const token =
    typeof window === "undefined"
      ? null
      : window.localStorage.getItem("jwt_token");
  return {
    Accept: "image/png, image/jpeg",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export async function uploadQuestionImage(
  questionId: number,
  file: File,
): Promise<QuestionImage> {
  if (!isPositiveId(questionId))
    throw new QuestionApiError("Question reference is invalid.", 400);
  if (
    !(file instanceof File) ||
    !["image/png", "image/jpeg"].includes(file.type) ||
    file.size < 1 ||
    file.size > 8 * 1024 * 1024
  ) {
    throw new QuestionApiError(
      "Choose a PNG or JPEG image no larger than 8 MB.",
      400,
    );
  }
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_BANK_PATH}/${questionId}/images`,
    {
      method: "POST",
      headers: authHeaders(),
      body: form,
    },
  );
  if (!response.ok) throw await responseError(response, "upload this image");
  const image = (await response.json()) as unknown;
  if (!isQuestionImage(image))
    throw new Error("The learning service returned an invalid question image.");
  return image;
}

export async function deleteQuestionImage(
  questionId: number,
  imageId: number,
): Promise<void> {
  if (!isPositiveId(questionId) || !isPositiveId(imageId))
    throw new QuestionApiError("Question image reference is invalid.", 400);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_BANK_PATH}/${questionId}/images/${imageId}`,
    {
      method: "DELETE",
      headers: authHeaders(),
    },
  );
  if (!response.ok) throw await responseError(response, "remove this image");
}

export async function fetchQuestionImageUrl(
  questionId: number,
  imageId: number,
): Promise<string> {
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_BANK_PATH}/${questionId}/images/${imageId}`,
    { headers: imageHeaders() },
  );
  if (!response.ok) throw await responseError(response, "load this image");
  const contentType = response.headers.get("content-type") || "";
  if (!/^image\/(png|jpeg)(?:;|$)/i.test(contentType))
    throw new QuestionApiError(
      "The learning service returned an invalid image.",
      502,
    );
  return URL.createObjectURL(await response.blob());
}

export type QuestionImportCandidateStatus =
  | "READY_FOR_REVIEW"
  | "UNCERTAIN"
  | "FAILED"
  | "IMPORTED"
  | "REJECTED"
  | "SUPERSEDED";

export interface QuestionImportCandidate {
  id: number;
  number: number;
  status: QuestionImportCandidateStatus;
  confidence: number;
  warningMessage: string | null;
  code: string | null;
  syllabusTopicId: number | null;
  prompt: string;
  modelAnswer: string;
  totalMarks: number;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  suggestedTags: string;
  includeSourceImage: boolean;
  source: { pageId: number; filename: string; pageNumber: number };
  diagramCrops?: QuestionImportDiagramCrop[];
  suggestions?: QuestionImportSuggestions;
  confidenceByField?: QuestionImportFieldConfidence;
  lineage?: QuestionImportLineage;
  duplicateWarnings?: QuestionImportDuplicateWarning[];
}

export interface QuestionImportSuggestions {
  prompt: string;
  modelAnswer: string;
  totalMarks: number;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  tags: string;
}

export interface QuestionImportFieldConfidence {
  prompt: number | null;
  modelAnswer: number | null;
  classification: number | null;
  marks: number | null;
}

export interface QuestionImportLineage {
  parentCandidateId: number | null;
  supersededByCandidateId: number | null;
  rejectionReason: string | null;
  isRejected: boolean;
}

export interface QuestionImportDuplicateWarning {
  target: QuestionImportDuplicateTarget;
  signals: QuestionImportDuplicateSignal[];
  strongestSignal: number;
}

export interface QuestionImportDuplicateTarget {
  kind: "IMPORT_DRAFT" | "IMPORTED_QUESTION" | "QUESTION_BANK";
  candidateId: number | null;
  questionId: number | null;
  label: string;
}

export interface QuestionImportDuplicateSignal {
  code:
    | "NORMALIZED_PROMPT_SIMILARITY"
    | "SOURCE_CHECKSUM_PAGE_IDENTITY"
    | "EXACT_PAGE_IMAGE_SHA256"
    | "EXACT_IMAGE_SHA256"
    | "NEAR_IMAGE_PERCEPTUAL_HASH"
    | "MATCHING_MARKS_TYPE_TOPIC";
  strength: number;
  detail: string;
}

export interface QuestionImportDiagramCrop {
  id: number;
  regionId: string;
  subQuestionId: string | null;
  rectangle: { x: number; y: number; width: number; height: number };
  width: number;
  height: number;
  contentType: "image/png";
}

export interface QuestionImportBatch {
  id: number;
  status: "QUEUED" | "RUNNING" | "READY_FOR_REVIEW" | "FAILED" | "IMPORTED";
  originalFilename: string;
  candidates: QuestionImportCandidate[];
}

export interface QuestionImportCandidateUpdate {
  code?: string | null;
  syllabusTopicId?: number | null;
  prompt?: string;
  modelAnswer?: string;
  totalMarks?: number;
  questionType?: QuestionType;
  difficulty?: QuestionDifficulty;
  includeSourceImage: boolean;
}

function isQuestionImportCandidate(
  value: unknown,
): value is QuestionImportCandidate {
  if (typeof value !== "object" || value === null) return false;
  const item = value as Record<string, unknown>;
  const source = item.source as Record<string, unknown> | null;
  return (
    isPositiveId(item.id) &&
    isNonNegativeInteger(item.number) &&
    typeof item.status === "string" &&
    [
      "READY_FOR_REVIEW",
      "UNCERTAIN",
      "FAILED",
      "IMPORTED",
      "REJECTED",
      "SUPERSEDED",
    ].includes(item.status) &&
    isNonNegativeInteger(item.confidence) &&
    item.confidence <= 100 &&
    (item.warningMessage === null || typeof item.warningMessage === "string") &&
    (item.code === null || typeof item.code === "string") &&
    (item.syllabusTopicId === null || isPositiveId(item.syllabusTopicId)) &&
    typeof item.prompt === "string" &&
    typeof item.modelAnswer === "string" &&
    typeof item.totalMarks === "number" &&
    Number.isFinite(item.totalMarks) &&
    item.totalMarks > 0 &&
    isQuestionType(item.questionType) &&
    isQuestionDifficulty(item.difficulty) &&
    typeof item.suggestedTags === "string" &&
    typeof item.includeSourceImage === "boolean" &&
    source !== null &&
    isPositiveId(source.pageId) &&
    isNonEmptyString(source.filename) &&
    isPositiveId(source.pageNumber) &&
    (item.diagramCrops === undefined ||
      (Array.isArray(item.diagramCrops) &&
        item.diagramCrops.every(isQuestionImportDiagramCrop))) &&
    (item.suggestions === undefined ||
      isQuestionImportSuggestions(item.suggestions)) &&
    (item.confidenceByField === undefined ||
      isQuestionImportFieldConfidence(item.confidenceByField)) &&
    (item.lineage === undefined || isQuestionImportLineage(item.lineage)) &&
    (item.duplicateWarnings === undefined ||
      (Array.isArray(item.duplicateWarnings) &&
        item.duplicateWarnings.every(isQuestionImportDuplicateWarning)))
  );
}

function isNullableConfidence(value: unknown): boolean {
  return value === null || (isNonNegativeInteger(value) && value <= 100);
}

function isQuestionImportSuggestions(
  value: unknown,
): value is QuestionImportSuggestions {
  if (typeof value !== "object" || value === null) return false;
  const suggestion = value as Record<string, unknown>;
  return (
    typeof suggestion.prompt === "string" &&
    typeof suggestion.modelAnswer === "string" &&
    typeof suggestion.totalMarks === "number" &&
    Number.isFinite(suggestion.totalMarks) &&
    isQuestionType(suggestion.questionType) &&
    isQuestionDifficulty(suggestion.difficulty) &&
    typeof suggestion.tags === "string"
  );
}

function isQuestionImportFieldConfidence(
  value: unknown,
): value is QuestionImportFieldConfidence {
  if (typeof value !== "object" || value === null) return false;
  const confidence = value as Record<string, unknown>;
  return (
    isNullableConfidence(confidence.prompt) &&
    isNullableConfidence(confidence.modelAnswer) &&
    isNullableConfidence(confidence.classification) &&
    isNullableConfidence(confidence.marks)
  );
}

function isQuestionImportLineage(
  value: unknown,
): value is QuestionImportLineage {
  if (typeof value !== "object" || value === null) return false;
  const lineage = value as Record<string, unknown>;
  return (
    (lineage.parentCandidateId === null ||
      isPositiveId(lineage.parentCandidateId)) &&
    (lineage.supersededByCandidateId === null ||
      isPositiveId(lineage.supersededByCandidateId)) &&
    (lineage.rejectionReason === null ||
      typeof lineage.rejectionReason === "string") &&
    typeof lineage.isRejected === "boolean"
  );
}

function isQuestionImportDuplicateWarning(
  value: unknown,
): value is QuestionImportDuplicateWarning {
  if (typeof value !== "object" || value === null) return false;
  const warning = value as Record<string, unknown>;
  return (
    isQuestionImportDuplicateTarget(warning.target) &&
    Array.isArray(warning.signals) &&
    warning.signals.length > 0 &&
    warning.signals.every(isQuestionImportDuplicateSignal) &&
    isNonNegativeInteger(warning.strongestSignal) &&
    warning.strongestSignal <= 100
  );
}

function isQuestionImportDuplicateTarget(
  value: unknown,
): value is QuestionImportDuplicateTarget {
  if (typeof value !== "object" || value === null) return false;
  const target = value as Record<string, unknown>;
  return (
    typeof target.kind === "string" &&
    ["IMPORT_DRAFT", "IMPORTED_QUESTION", "QUESTION_BANK"].includes(
      target.kind,
    ) &&
    (target.candidateId === null || isPositiveId(target.candidateId)) &&
    (target.questionId === null || isPositiveId(target.questionId)) &&
    isNonEmptyString(target.label)
  );
}

function isQuestionImportDuplicateSignal(
  value: unknown,
): value is QuestionImportDuplicateSignal {
  if (typeof value !== "object" || value === null) return false;
  const signal = value as Record<string, unknown>;
  return (
    typeof signal.code === "string" &&
    [
      "NORMALIZED_PROMPT_SIMILARITY",
      "SOURCE_CHECKSUM_PAGE_IDENTITY",
      "EXACT_PAGE_IMAGE_SHA256",
      "EXACT_IMAGE_SHA256",
      "NEAR_IMAGE_PERCEPTUAL_HASH",
      "MATCHING_MARKS_TYPE_TOPIC",
    ].includes(signal.code) &&
    isNonNegativeInteger(signal.strength) &&
    signal.strength <= 100 &&
    isNonEmptyString(signal.detail)
  );
}

function isQuestionImportDiagramCrop(
  value: unknown,
): value is QuestionImportDiagramCrop {
  if (typeof value !== "object" || value === null) return false;
  const crop = value as Record<string, unknown>;
  const rectangle = crop.rectangle as Record<string, unknown> | null;
  return (
    isPositiveId(crop.id) &&
    isNonEmptyString(crop.regionId) &&
    (crop.subQuestionId === null || isNonEmptyString(crop.subQuestionId)) &&
    rectangle !== null &&
    isNonNegativeInteger(rectangle.x) &&
    isNonNegativeInteger(rectangle.y) &&
    isPositiveId(rectangle.width) &&
    isPositiveId(rectangle.height) &&
    isPositiveId(crop.width) &&
    isPositiveId(crop.height) &&
    crop.contentType === "image/png"
  );
}

function parseQuestionImportBatch(payload: unknown): QuestionImportBatch {
  if (typeof payload !== "object" || payload === null)
    throw new Error("The learning service returned an invalid import batch.");
  const batch = payload as Record<string, unknown>;
  if (
    !isPositiveId(batch.id) ||
    typeof batch.status !== "string" ||
    !["QUEUED", "RUNNING", "READY_FOR_REVIEW", "FAILED", "IMPORTED"].includes(
      batch.status,
    ) ||
    !isNonEmptyString(batch.originalFilename) ||
    !Array.isArray(batch.candidates) ||
    !batch.candidates.every(isQuestionImportCandidate)
  ) {
    throw new Error("The learning service returned an invalid import batch.");
  }
  return batch as unknown as QuestionImportBatch;
}

export async function fetchQuestionImportBatch(
  batchId: number,
): Promise<QuestionImportBatch> {
  if (!isPositiveId(batchId))
    throw new QuestionApiError("Import batch reference is invalid.", 400);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}`,
    { headers: authHeaders() },
  );
  if (!response.ok)
    throw await responseError(response, "load this import batch");
  return parseQuestionImportBatch(await response.json());
}

export async function uploadQuestionImport(
  files: File[],
): Promise<QuestionImportBatch> {
  if (
    !Array.isArray(files) ||
    files.length < 1 ||
    files.length > 20 ||
    files.some(
      (file) =>
        !(file instanceof File) ||
        file.size < 1 ||
        file.size > 25 * 1024 * 1024 ||
        !["application/pdf", "image/png", "image/jpeg"].includes(file.type),
    )
  ) {
    throw new QuestionApiError(
      "Choose up to 20 PDF, PNG, or JPEG files no larger than 25 MB each.",
      400,
    );
  }
  const form = new FormData();
  files.forEach((file) => form.append("files", file));
  const response = await fetch(`${LEARNING_API_URL}${QUESTION_IMPORT_PATH}`, {
    method: "POST",
    headers: authHeaders(),
    body: form,
  });
  if (!response.ok) throw await responseError(response, "process this import");
  return parseQuestionImportBatch(await response.json());
}

export async function updateQuestionImportCandidate(
  batchId: number,
  candidateId: number,
  update: QuestionImportCandidateUpdate,
): Promise<QuestionImportCandidate> {
  if (
    !isPositiveId(batchId) ||
    !isPositiveId(candidateId) ||
    !Number.isFinite(update.totalMarks ?? 1) ||
    (update.totalMarks ?? 1) <= 0
  ) {
    throw new QuestionApiError("Question import details are invalid.", 400);
  }
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}/candidates/${candidateId}`,
    {
      method: "PUT",
      headers: mutationHeaders(),
      body: JSON.stringify(update),
    },
  );
  if (!response.ok)
    throw await responseError(response, "save this import draft");
  const candidate = (await response.json()) as unknown;
  if (!isQuestionImportCandidate(candidate))
    throw new Error("The learning service returned an invalid import draft.");
  return candidate;
}

export async function importQuestionImportCandidates(
  batchId: number,
  candidateIds: number[],
): Promise<{ questionIds: number[]; message: string }> {
  if (
    !isPositiveId(batchId) ||
    !Array.isArray(candidateIds) ||
    candidateIds.length < 1 ||
    !candidateIds.every(isPositiveId)
  ) {
    throw new QuestionApiError("Choose at least one valid import draft.", 400);
  }
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}/import`,
    {
      method: "POST",
      headers: mutationHeaders(),
      body: JSON.stringify({ candidateIds }),
    },
  );
  if (!response.ok)
    throw await responseError(response, "import reviewed questions");
  const result = (await response.json()) as unknown;
  if (typeof result !== "object" || result === null) {
    throw new Error("The learning service returned an invalid import result.");
  }
  const resultRecord = result as Record<string, unknown>;
  const questionIds = resultRecord.questionIds;
  if (
    !Array.isArray(questionIds) ||
    !questionIds.every(isPositiveId) ||
    !isNonEmptyString(resultRecord.message)
  ) {
    throw new Error("The learning service returned an invalid import result.");
  }
  return result as { questionIds: number[]; message: string };
}

async function updateQuestionImportBatch(
  batchId: number,
  path: string,
  body?: unknown,
): Promise<QuestionImportBatch> {
  if (!isPositiveId(batchId))
    throw new QuestionApiError("Import batch reference is invalid.", 400);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}${path}`,
    {
      method: "POST",
      headers: mutationHeaders(),
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    },
  );
  if (!response.ok)
    throw await responseError(response, "update this import review");
  return parseQuestionImportBatch(await response.json());
}

export function rejectQuestionImportCandidate(
  batchId: number,
  candidateId: number,
  reason?: string,
): Promise<QuestionImportBatch> {
  if (!isPositiveId(candidateId))
    throw new QuestionApiError("Import draft reference is invalid.", 400);
  return updateQuestionImportBatch(
    batchId,
    `/candidates/${candidateId}/reject`,
    { reason: reason?.trim() || null },
  );
}

export function restoreQuestionImportCandidate(
  batchId: number,
  candidateId: number,
): Promise<QuestionImportBatch> {
  if (!isPositiveId(candidateId))
    throw new QuestionApiError("Import draft reference is invalid.", 400);
  return updateQuestionImportBatch(
    batchId,
    `/candidates/${candidateId}/restore`,
  );
}

export function moveQuestionImportDiagramCrops(
  batchId: number,
  cropIds: number[],
  targetCandidateId: number,
): Promise<QuestionImportBatch> {
  if (
    !cropIds.length ||
    !cropIds.every(isPositiveId) ||
    !isPositiveId(targetCandidateId)
  ) {
    throw new QuestionApiError(
      "Diagram attachment references are invalid.",
      400,
    );
  }
  return updateQuestionImportBatch(batchId, "/diagram-crops/move", {
    cropIds,
    targetCandidateId,
  });
}

export function mergeQuestionImportCandidates(
  batchId: number,
  targetCandidateId: number,
  candidateIds: number[],
): Promise<QuestionImportBatch> {
  if (
    !isPositiveId(targetCandidateId) ||
    candidateIds.length < 2 ||
    !candidateIds.every(isPositiveId)
  ) {
    throw new QuestionApiError(
      "Select at least two valid drafts to merge.",
      400,
    );
  }
  return updateQuestionImportBatch(batchId, "/candidates/merge", {
    targetCandidateId,
    candidateIds,
  });
}

export function splitQuestionImportCandidate(
  batchId: number,
  candidateId: number,
  drafts: Array<{ prompt: string; modelAnswer: string }>,
): Promise<QuestionImportBatch> {
  if (
    !isPositiveId(candidateId) ||
    drafts.length < 2 ||
    drafts.some((draft) => !draft.prompt.trim() || !draft.modelAnswer.trim())
  ) {
    throw new QuestionApiError(
      "Provide question text and a model answer for every split draft.",
      400,
    );
  }
  return updateQuestionImportBatch(
    batchId,
    `/candidates/${candidateId}/split`,
    { drafts },
  );
}

export async function fetchQuestionImportSourcePageUrl(
  batchId: number,
  pageId: number,
): Promise<string> {
  if (!isPositiveId(batchId) || !isPositiveId(pageId))
    throw new QuestionApiError("Source page reference is invalid.", 400);
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}/source-pages/${pageId}/image`,
    { headers: imageHeaders() },
  );
  if (!response.ok)
    throw await responseError(response, "load this source page");
  const contentType = response.headers.get("content-type") || "";
  if (!/^image\/(png|jpeg)(?:;|$)/i.test(contentType))
    throw new QuestionApiError(
      "The learning service returned an invalid source image.",
      502,
    );
  return URL.createObjectURL(await response.blob());
}

export async function fetchQuestionImportDiagramCropUrl(
  batchId: number,
  cropId: number,
): Promise<string> {
  if (!isPositiveId(batchId) || !isPositiveId(cropId)) {
    throw new QuestionApiError("Diagram crop reference is invalid.", 400);
  }
  const response = await fetch(
    `${LEARNING_API_URL}${QUESTION_IMPORT_PATH}/${batchId}/diagram-crops/${cropId}/image`,
    { headers: imageHeaders() },
  );
  if (!response.ok)
    throw await responseError(response, "load this diagram crop");
  const contentType = response.headers.get("content-type") || "";
  if (!/^image\/png(?:;|$)/i.test(contentType)) {
    throw new QuestionApiError(
      "The learning service returned an invalid diagram crop.",
      502,
    );
  }
  return URL.createObjectURL(await response.blob());
}

export interface QuestionRuleCheckResult {
  awardedMarks: number;
  maximumMarks: number;
  matchedKeywords: string[];
  missingKeywords: string[];
  explanation: string;
  componentResults: Array<{
    position: number;
    description: string;
    maximumMarks: number;
    matched: boolean;
    matchedTargets: string[];
    missingTargets: string[];
    feedback: string;
  }>;
}

function isRuleCheckResult(value: unknown): value is QuestionRuleCheckResult {
  if (typeof value !== "object" || value === null) return false;
  const result = value as Record<string, unknown>;
  const textList = (candidate: unknown) =>
    Array.isArray(candidate) && candidate.every(isNonEmptyString);
  const component = (candidate: unknown) => {
    if (typeof candidate !== "object" || candidate === null) return false;
    const item = candidate as Record<string, unknown>;
    return (
      isNonNegativeInteger(item.position) &&
      isNonEmptyString(item.description) &&
      typeof item.maximumMarks === "number" &&
      Number.isFinite(item.maximumMarks) &&
      item.maximumMarks > 0 &&
      typeof item.matched === "boolean" &&
      textList(item.matchedTargets) &&
      textList(item.missingTargets) &&
      isNonEmptyString(item.feedback)
    );
  };
  return (
    typeof result.awardedMarks === "number" &&
    Number.isFinite(result.awardedMarks) &&
    result.awardedMarks >= 0 &&
    typeof result.maximumMarks === "number" &&
    Number.isFinite(result.maximumMarks) &&
    result.maximumMarks > 0 &&
    textList(result.matchedKeywords) &&
    textList(result.missingKeywords) &&
    isNonEmptyString(result.explanation) &&
    Array.isArray(result.componentResults) &&
    result.componentResults.every(component)
  );
}

/** Runs the Tutor-only deterministic checker. It is a preview and never persists a score. */
export async function checkTutorQuestionAnswer(
  questionId: number,
  answer: string,
): Promise<QuestionRuleCheckResult> {
  if (!isPositiveId(questionId) || !isNonEmptyString(answer)) {
    throw new QuestionApiError("Enter an answer to check.", 400);
  }
  const response = await fetch(
    `${GRADING_API_URL}/api/grading/tutor/questions/${questionId}/rule-check`,
    {
      method: "POST",
      headers: mutationHeaders(),
      body: JSON.stringify({ answer: answer.trim() }),
    },
  );
  if (!response.ok) throw await responseError(response, "check this answer");
  const payload = (await response.json()) as unknown;
  if (!isRuleCheckResult(payload)) {
    throw new QuestionApiError(
      "The grading service returned an invalid answer check. Please try again.",
      502,
    );
  }
  return payload;
}

/**
 * Keeps a Tutor's in-progress question selection available to the worksheet
 * editor once that Phase 4 flow is connected. It is deliberately browser-only:
 * a selection is not a worksheet, assignment, or server-side mutation.
 */
function readWorksheetDraftQuestionIds(): number[] {
  if (typeof window === "undefined") return [];
  try {
    const stored = JSON.parse(
      window.sessionStorage.getItem(WORKSHEET_DRAFT_QUESTION_IDS_KEY) ?? "[]",
    ) as unknown;
    if (!Array.isArray(stored)) return [];
    return [...new Set(stored.filter(isPositiveId))];
  } catch {
    return [];
  }
}

export function isQuestionInWorksheetDraft(questionId: number): boolean {
  return (
    isPositiveId(questionId) &&
    readWorksheetDraftQuestionIds().includes(questionId)
  );
}

/** Adds one active question to the local worksheet draft selection. */
export function addQuestionToWorksheetDraft(questionId: number): {
  ids: number[];
  added: boolean;
  storageUnavailable?: boolean;
} {
  if (!isPositiveId(questionId))
    throw new QuestionApiError("Question reference is invalid.", 400);
  const ids = readWorksheetDraftQuestionIds();
  if (ids.includes(questionId)) return { ids, added: false };
  const next = [...ids, questionId];
  try {
    if (typeof window !== "undefined")
      window.sessionStorage.setItem(
        WORKSHEET_DRAFT_QUESTION_IDS_KEY,
        JSON.stringify(next),
      );
  } catch {
    return { ids, added: false, storageUnavailable: true };
  }
  return { ids: next, added: true };
}
