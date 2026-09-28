"use client";

import * as React from "react";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import SyllabusPicker from "@/components/syllabus/SyllabusPicker";
import {
  QuestionApiError,
  uploadQuestionImage,
  deleteQuestionImage,
  type QuestionArchiveState,
  type QuestionDifficulty,
  type QuestionMutationRequest,
  type QuestionType,
  type TutorQuestion,
} from "@/services/questions";
import type { SyllabusTree } from "@/services/syllabus";

const questionTypes: readonly { value: QuestionType; label: string }[] = [
  { value: "MULTIPLE_CHOICE", label: "Multiple choice" },
  { value: "TRUE_FALSE", label: "True / false" },
  { value: "FILL_IN_THE_BLANK", label: "Fill in the blank" },
  { value: "SHORT_ANSWER", label: "Short answer" },
  { value: "OPEN_ENDED", label: "Open ended" },
  { value: "CALCULATION", label: "Calculation" },
  { value: "DIAGRAM", label: "Diagram" },
];
const difficulties: readonly { value: QuestionDifficulty; label: string }[] = [
  { value: "FOUNDATION", label: "Foundation" },
  { value: "APPLICATION", label: "Application" },
  { value: "CHALLENGE", label: "Challenge" },
];

type MarkingCriterion = {
  description: string;
  marks: string;
  keywords: string;
};
type PendingImage = { file: File; previewUrl: string };
type FormValues = {
  code: string;
  syllabusTopicId: string;
  questionType: QuestionType;
  difficulty: QuestionDifficulty;
  prompt: string;
  totalMarks: string;
  modelAnswer: string;
  archiveState: QuestionArchiveState;
  markingComponents: MarkingCriterion[];
  keywords: string;
};
type FieldErrors = Record<string, string>;

export interface QuestionFormProps {
  mode: "create" | "edit";
  initialQuestion?: TutorQuestion;
  submitQuestion: (request: QuestionMutationRequest) => Promise<TutorQuestion>;
  onComplete: (question: TutorQuestion) => void;
  cancelHref?: string;
  loadSyllabus?: () => Promise<SyllabusTree>;
}

const fieldSx = {
  ".MuiOutlinedInput-root": {
    bgcolor: "#FBF9F5",
    borderRadius: "9px",
    fontSize: 13.5,
    "& fieldset": { borderColor: "#E4DCD0" },
    "&.Mui-focused fieldset": { borderColor: "#E08A72" },
  },
  ".MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
  ".MuiFormHelperText-root": {
    color: "#B4573F",
    fontSize: 11.5,
    lineHeight: 1.4,
    mx: 0,
    mt: 0.7,
  },
} as const;

function blankValues(): FormValues {
  return {
    code: "",
    syllabusTopicId: "",
    questionType: "OPEN_ENDED",
    difficulty: "FOUNDATION",
    prompt: "",
    totalMarks: "",
    modelAnswer: "",
    archiveState: "ACTIVE",
    markingComponents: [{ description: "", marks: "", keywords: "" }],
    keywords: "",
  };
}

function initialValues(question?: TutorQuestion): FormValues {
  if (!question) return blankValues();
  return {
    code: question.code,
    syllabusTopicId: String(question.syllabusTopic.id),
    questionType: question.questionType,
    difficulty: question.difficulty ?? "FOUNDATION",
    prompt: question.prompt,
    totalMarks: String(question.totalMarks),
    modelAnswer: question.modelAnswer,
    archiveState: question.archiveState,
    markingComponents: question.markingComponents.map((component) => ({
      description: component.description,
      marks: String(component.marks),
      keywords: component.keywords.join(", "),
    })),
    keywords: question.keywords.join(", "),
  };
}

function preciseNumber(value: string): number | null {
  const trimmed = value.trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

export function validateQuestionForm(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (!values.code.trim()) errors.code = "Question reference code is required.";
  else if (values.code.trim().length > 120)
    errors.code = "Question reference code must be 120 characters or fewer.";
  const topicId = Number(values.syllabusTopicId);
  if (!Number.isSafeInteger(topicId) || topicId <= 0)
    errors.syllabusTopicId = "Choose an existing syllabus topic or subtopic.";
  if (!values.prompt.trim()) errors.prompt = "Question prompt is required.";
  else if (values.prompt.trim().length > 4000)
    errors.prompt = "Question prompt must be 4,000 characters or fewer.";
  if (!values.modelAnswer.trim())
    errors.modelAnswer = "Model answer is required.";
  else if (values.modelAnswer.trim().length > 4000)
    errors.modelAnswer = "Model answer must be 4,000 characters or fewer.";
  const totalMarks = preciseNumber(values.totalMarks);
  if (totalMarks === null || totalMarks <= 0 || totalMarks > 9999.99)
    errors.totalMarks =
      "Enter marks greater than zero with up to two decimal places.";
  if (values.markingComponents.length === 0)
    errors.markingComponents = "Add at least one marking criterion.";
  let markedTotal = 0;
  values.markingComponents.forEach((component, index) => {
    if (!component.description.trim())
      errors[`markingComponents.${index}.description`] =
        "Criterion description is required.";
    else if (component.description.trim().length > 1000)
      errors[`markingComponents.${index}.description`] =
        "Criterion description must be 1,000 characters or fewer.";
    const marks = preciseNumber(component.marks);
    if (marks === null || marks <= 0 || marks > 9999.99)
      errors[`markingComponents.${index}.marks`] =
        "Use a positive number with up to two decimal places.";
    else markedTotal += marks;
    const componentKeywords = component.keywords
      .split(",")
      .map((keyword) => keyword.trim())
      .filter(Boolean);
    if (componentKeywords.length === 0)
      errors[`markingComponents.${index}.keywords`] =
        "Add at least one approved answer keyword.";
    else if (componentKeywords.length > 100)
      errors[`markingComponents.${index}.keywords`] =
        "Use at most 100 keywords per criterion.";
    else if (componentKeywords.some((keyword) => keyword.length > 80))
      errors[`markingComponents.${index}.keywords`] =
        "Each keyword must be 80 characters or fewer.";
    else if (
      new Set(componentKeywords.map((keyword) => keyword.toLowerCase()))
        .size !== componentKeywords.length
    )
      errors[`markingComponents.${index}.keywords`] =
        "Keywords must be unique within this criterion.";
  });
  if (
    totalMarks !== null &&
    Math.round(markedTotal * 100) !== Math.round(totalMarks * 100)
  )
    errors.markingComponents =
      "Criteria marks must exactly equal the total marks.";
  const keywords = values.keywords
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  if (keywords.length > 100) errors.keywords = "Use at most 100 keywords.";
  else if (keywords.some((keyword) => keyword.length > 80))
    errors.keywords = "Each keyword must be 80 characters or fewer.";
  else if (
    new Set(keywords.map((keyword) => keyword.toLowerCase())).size !==
    keywords.length
  )
    errors.keywords = "Keywords must be unique.";
  return errors;
}

function requestFor(values: FormValues): QuestionMutationRequest {
  return {
    code: values.code.trim(),
    syllabusTopicId: Number(values.syllabusTopicId),
    questionType: values.questionType,
    difficulty: values.difficulty,
    prompt: values.prompt.trim(),
    totalMarks: Number(values.totalMarks),
    modelAnswer: values.modelAnswer.trim(),
    archiveState: values.archiveState,
    markingComponents: values.markingComponents.map((component) => ({
      description: component.description.trim(),
      marks: Number(component.marks),
      keywords: component.keywords
        .split(",")
        .map((keyword) => keyword.trim())
        .filter(Boolean),
    })),
    keywords: values.keywords
      .split(",")
      .map((keyword) => keyword.trim())
      .filter(Boolean),
  };
}

export default function QuestionForm({
  mode,
  initialQuestion,
  submitQuestion,
  onComplete,
  cancelHref = "/questions",
  loadSyllabus,
}: QuestionFormProps) {
  const [values, setValues] = React.useState<FormValues>(() =>
    initialValues(initialQuestion),
  );
  const [errors, setErrors] = React.useState<FieldErrors>({});
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [pendingImages, setPendingImages] = React.useState<PendingImage[]>([]);
  const pendingImageUrls = React.useRef(new Set<string>());
  const submissionInFlight = React.useRef(false);
  const update = <Field extends keyof Omit<FormValues, "markingComponents">>(
    field: Field,
    value: FormValues[Field],
  ) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: "" }));
  };
  const updateCriterion = (
    index: number,
    field: keyof MarkingCriterion,
    value: string,
  ) => {
    setValues((current) => ({
      ...current,
      markingComponents: current.markingComponents.map(
        (criterion, criterionIndex) =>
          criterionIndex === index
            ? { ...criterion, [field]: value }
            : criterion,
      ),
    }));
    setErrors((current) => ({
      ...current,
      [`markingComponents.${index}.${field}`]: "",
      markingComponents: "",
    }));
  };
  const addCriterion = () =>
    setValues((current) =>
      current.markingComponents.length >= 100
        ? current
        : {
            ...current,
            markingComponents: [
              ...current.markingComponents,
              { description: "", marks: "", keywords: "" },
            ],
          },
    );
  const removeCriterion = (index: number) => {
    setValues((current) => ({
      ...current,
      markingComponents: current.markingComponents.filter(
        (_, criterionIndex) => criterionIndex !== index,
      ),
    }));
    setErrors({});
  };
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionInFlight.current) return;
    const nextErrors = validateQuestionForm(values);
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      setSubmitError(null);
      return;
    }
    submissionInFlight.current = true;
    setSubmitting(true);
    setErrors({});
    setSubmitError(null);
    try {
      const saved = await submitQuestion(requestFor(values));
      await Promise.all(
        pendingImages.map(({ file }) => uploadQuestionImage(saved.id, file)),
      );
      onComplete(saved);
    } catch (reason) {
      if (reason instanceof QuestionApiError) {
        setErrors(reason.fields);
        setSubmitError(reason.message);
      } else
        setSubmitError(
          reason instanceof Error
            ? reason.message
            : "This question could not be saved. Please try again.",
        );
    } finally {
      submissionInFlight.current = false;
      setSubmitting(false);
    }
  };
  React.useEffect(
    () => () => {
      pendingImageUrls.current.forEach((url) => URL.revokeObjectURL(url));
    },
    [],
  );
  const submitLabel = mode === "create" ? "Create question" : "Save changes";
  return (
    <Card
      component="form"
      noValidate
      onSubmit={submit}
      variant="outlined"
      sx={{
        maxWidth: 940,
        p: { xs: 2.25, sm: 3 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        boxShadow: "none",
      }}
    >
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            sm: "minmax(0, 1fr) minmax(0, 1fr)",
          },
          gap: 2,
        }}
      >
        <TextField
          required
          fullWidth
          label="Question reference code"
          value={values.code}
          onChange={(event) => update("code", event.target.value)}
          error={Boolean(errors.code)}
          helperText={
            errors.code ||
            "A unique, tutor-entered reference for question lookup and saved worksheet snapshots. Stored in uppercase (for example, SCI-P6-SYS-000123)."
          }
          slotProps={{
            htmlInput: {
              maxLength: 120,
              "aria-label": "Question reference code",
            },
          }}
          sx={fieldSx}
        />
        <Box sx={{ minWidth: 0 }}>
          <SyllabusPicker
            value={Number(values.syllabusTopicId) || null}
            onChange={(topicId) =>
              update("syllabusTopicId", topicId ? String(topicId) : "")
            }
            label="Curriculum classification"
            required
            error={errors.syllabusTopicId}
            helperText="Choose the existing Subject, Level, Theme, and Topic; optionally refine it to a Subtopic. The final Topic or Subtopic is saved with the question."
            loadSyllabus={loadSyllabus}
          />
        </Box>
        <TextField
          select
          fullWidth
          required
          label="Question type"
          value={values.questionType}
          onChange={(event) =>
            update("questionType", event.target.value as QuestionType)
          }
          sx={fieldSx}
        >
          {questionTypes.map((type) => (
            <MenuItem key={type.value} value={type.value}>
              {type.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          fullWidth
          required
          label="Difficulty"
          value={values.difficulty}
          onChange={(event) =>
            update("difficulty", event.target.value as QuestionDifficulty)
          }
          sx={fieldSx}
        >
          {difficulties.map((item) => (
            <MenuItem key={item.value} value={item.value}>
              {item.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          select
          fullWidth
          required
          label="Availability"
          value={values.archiveState}
          onChange={(event) =>
            update("archiveState", event.target.value as QuestionArchiveState)
          }
          sx={fieldSx}
        >
          <MenuItem value="ACTIVE">Active — can be used in worksheets</MenuItem>
          <MenuItem value="ARCHIVED">Archived — kept for records only</MenuItem>
        </TextField>
        <TextField
          required
          fullWidth
          multiline
          minRows={4}
          label="Question prompt"
          value={values.prompt}
          onChange={(event) => update("prompt", event.target.value)}
          error={Boolean(errors.prompt)}
          helperText={errors.prompt}
          slotProps={{
            htmlInput: { maxLength: 4000, "aria-label": "Question prompt" },
          }}
          sx={{ ...fieldSx, gridColumn: { sm: "1 / -1" } }}
        />
        <Box
          sx={{
            gridColumn: { sm: "1 / -1" },
            p: 1.5,
            border: "1px solid #E4DCD0",
            borderRadius: "10px",
            bgcolor: "#FBF9F5",
          }}
        >
          <Typography
            sx={{ color: "#2A2622", fontSize: 13.5, fontWeight: 600 }}
          >
            Diagrams and images
          </Typography>
          <Typography
            sx={{ color: "#6F675E", fontSize: 12, lineHeight: 1.5, mt: 0.4 }}
          >
            Attach up to 10 PNG or JPEG diagrams (8 MB each). They are copied
            into generated worksheets.
          </Typography>
          <Button
            component="label"
            type="button"
            sx={{
              mt: 1,
              minHeight: 36,
              border: "1px solid #E4DCD0",
              borderRadius: "9px",
              color: "#2A2622",
              textTransform: "none",
            }}
          >
            Choose images
            <input
              hidden
              type="file"
              accept="image/png,image/jpeg"
              multiple
              onChange={(event) => {
                const selected = Array.from(event.target.files ?? []);
                const invalid = selected.find(
                  (file) =>
                    !["image/png", "image/jpeg"].includes(file.type) ||
                    file.size < 1 ||
                    file.size > 8 * 1024 * 1024,
                );
                if (invalid) {
                  setSubmitError(
                    "Choose PNG or JPEG images no larger than 8 MB.",
                  );
                  return;
                }
                setPendingImages((current) => {
                  const accepted = selected
                    .slice(0, 10 - current.length)
                    .map((file) => {
                      const previewUrl = URL.createObjectURL(file);
                      pendingImageUrls.current.add(previewUrl);
                      return { file, previewUrl };
                    });
                  return [...current, ...accepted];
                });
                event.currentTarget.value = "";
              }}
            />
          </Button>
          {initialQuestion && (initialQuestion.images?.length ?? 0) > 0 ? (
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.75, mt: 1 }}>
              <Typography
                sx={{ color: "#6F675E", fontSize: 12, alignSelf: "center" }}
              >
                Saved images:
              </Typography>
              {initialQuestion.images?.map((image) => (
                <Button
                  key={image.id}
                  type="button"
                  onClick={() =>
                    void deleteQuestionImage(initialQuestion.id, image.id).then(
                      () => window.location.reload(),
                    )
                  }
                  sx={{
                    minHeight: 30,
                    border: "1px solid #E4DCD0",
                    borderRadius: "8px",
                    color: "#9E3A24",
                    textTransform: "none",
                    fontSize: 11,
                  }}
                >
                  Remove {image.filename}
                </Button>
              ))}
            </Box>
          ) : null}
          {pendingImages.length > 0 ? (
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mt: 1 }}>
              {pendingImages.map((pending, index) => (
                <Box
                  key={`${pending.file.name}-${index}`}
                  sx={{ position: "relative", width: 116 }}
                >
                  <Box
                    component="img"
                    src={pending.previewUrl}
                    alt={`Selected diagram: ${pending.file.name}`}
                    sx={{
                      display: "block",
                      width: 116,
                      height: 76,
                      objectFit: "contain",
                      bgcolor: "#fff",
                      border: "1px solid #E4DCD0",
                      borderRadius: "7px",
                    }}
                  />
                  <Button
                    type="button"
                    onClick={() =>
                      setPendingImages((current) => {
                        const removed = current[index];
                        if (removed) {
                          URL.revokeObjectURL(removed.previewUrl);
                          pendingImageUrls.current.delete(removed.previewUrl);
                        }
                        return current.filter(
                          (_, fileIndex) => fileIndex !== index,
                        );
                      })
                    }
                    sx={{
                      minHeight: 26,
                      mt: 0.25,
                      px: 0.5,
                      color: "#9E3A24",
                      textTransform: "none",
                      fontSize: 11,
                    }}
                  >
                    Remove
                  </Button>
                </Box>
              ))}
            </Box>
          ) : null}
        </Box>
        <TextField
          required
          fullWidth
          label="Total marks"
          value={values.totalMarks}
          onChange={(event) => update("totalMarks", event.target.value)}
          error={Boolean(errors.totalMarks)}
          helperText={errors.totalMarks || "Use up to two decimal places."}
          slotProps={{
            htmlInput: { inputMode: "decimal", "aria-label": "Total marks" },
          }}
          sx={fieldSx}
        />
        <TextField
          fullWidth
          label="Keywords"
          value={values.keywords}
          onChange={(event) => update("keywords", event.target.value)}
          error={Boolean(errors.keywords)}
          helperText={
            errors.keywords || "Optional; separate terms with commas."
          }
          slotProps={{ htmlInput: { "aria-label": "Keywords" } }}
          sx={fieldSx}
        />
        <TextField
          required
          fullWidth
          multiline
          minRows={4}
          label="Model answer"
          value={values.modelAnswer}
          onChange={(event) => update("modelAnswer", event.target.value)}
          error={Boolean(errors.modelAnswer)}
          helperText={
            errors.modelAnswer || "This is Tutor-only marking guidance."
          }
          slotProps={{
            htmlInput: { maxLength: 4000, "aria-label": "Model answer" },
          }}
          sx={{ ...fieldSx, gridColumn: { sm: "1 / -1" } }}
        />
      </Box>
      <Box
        component="section"
        aria-labelledby="marking-criteria-title"
        sx={{ mt: 3, pt: 2.5, borderTop: "1px solid #F0EAE0" }}
      >
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "flex-start",
            justifyContent: "space-between",
            gap: 1.5,
            mb: 1.5,
          }}
        >
          <Box>
            <Typography
              id="marking-criteria-title"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 21,
                fontWeight: 500,
              }}
            >
              Marking criteria
            </Typography>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 12.5,
                lineHeight: 1.55,
                mt: 0.5,
              }}
            >
              Allocate every mark so the criteria total matches the question.
            </Typography>
          </Box>
          <Button
            type="button"
            onClick={addCriterion}
            disabled={values.markingComponents.length >= 100}
            startIcon={<AddIcon />}
            sx={{
              minHeight: 40,
              border: "1px solid #E4DCD0",
              borderRadius: "10px",
              color: "#2A2622",
              textTransform: "none",
              fontWeight: 500,
            }}
          >
            Add criterion
          </Button>
        </Box>
        {errors.markingComponents && (
          <Typography
            role="alert"
            sx={{ color: "#B4573F", fontSize: 11.5, mb: 1 }}
          >
            {errors.markingComponents}
          </Typography>
        )}
        <Box sx={{ display: "grid", gap: 1.25 }}>
          {values.markingComponents.map((criterion, index) => (
            <Box
              key={index}
              sx={{
                display: "grid",
                gridTemplateColumns: {
                  xs: "1fr",
                  sm: "minmax(0, 1fr) 150px auto",
                },
                gap: 1.25,
                alignItems: "start",
                p: 1.5,
                border: "1px solid #F0EAE0",
                borderRadius: "12px",
                bgcolor: "#FBF9F5",
              }}
            >
              <TextField
                required
                fullWidth
                label={`Criterion ${index + 1}`}
                value={criterion.description}
                onChange={(event) =>
                  updateCriterion(index, "description", event.target.value)
                }
                error={Boolean(
                  errors[`markingComponents.${index}.description`],
                )}
                helperText={errors[`markingComponents.${index}.description`]}
                slotProps={{
                  htmlInput: {
                    maxLength: 1000,
                    "aria-label": `Criterion ${index + 1}`,
                  },
                }}
                sx={fieldSx}
              />
              <TextField
                required
                fullWidth
                label={`Criterion ${index + 1} marks`}
                value={criterion.marks}
                onChange={(event) =>
                  updateCriterion(index, "marks", event.target.value)
                }
                error={Boolean(errors[`markingComponents.${index}.marks`])}
                helperText={errors[`markingComponents.${index}.marks`]}
                slotProps={{
                  htmlInput: {
                    inputMode: "decimal",
                    "aria-label": `Criterion ${index + 1} marks`,
                  },
                }}
                sx={fieldSx}
              />
              <Button
                type="button"
                onClick={() => removeCriterion(index)}
                disabled={values.markingComponents.length === 1}
                aria-label={`Remove criterion ${index + 1}`}
                sx={{
                  minWidth: 40,
                  minHeight: 40,
                  mt: { xs: 0, sm: 0.5 },
                  border: "1px solid #EBE4D9",
                  borderRadius: "9px",
                  color: "#B4573F",
                }}
              >
                <DeleteOutlineIcon aria-hidden="true" sx={{ fontSize: 18 }} />
              </Button>
              <TextField
                required
                fullWidth
                label={`Criterion ${index + 1} answer keywords`}
                value={criterion.keywords}
                onChange={(event) =>
                  updateCriterion(index, "keywords", event.target.value)
                }
                error={Boolean(errors[`markingComponents.${index}.keywords`])}
                helperText={
                  errors[`markingComponents.${index}.keywords`] ||
                  "Comma-separated exact terms that can earn this criterion's marks."
                }
                slotProps={{
                  htmlInput: {
                    "aria-label": `Criterion ${index + 1} answer keywords`,
                  },
                }}
                sx={{ ...fieldSx, gridColumn: { sm: "1 / -1" } }}
              />
            </Box>
          ))}
        </Box>
      </Box>
      {submitError && (
        <Box
          role="alert"
          sx={{
            mt: 2.5,
            p: 1.75,
            borderLeft: "3px solid #B4573F",
            borderRadius: "0 10px 10px 0",
            bgcolor: "#F6EFE6",
          }}
        >
          <Typography sx={{ color: "#5A544C", fontSize: 13, lineHeight: 1.55 }}>
            {submitError}
          </Typography>
        </Box>
      )}
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 1.25,
          mt: 3,
        }}
      >
        <Button
          type="submit"
          disabled={submitting}
          sx={{
            minHeight: 42,
            borderRadius: "10px",
            bgcolor: "#9E3A24",
            color: "#FBF9F5",
            textTransform: "none",
            fontWeight: 500,
            px: 2.25,
            "&:hover": { bgcolor: "#8A3120" },
            "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#B5AA9C" },
          }}
        >
          {submitting
            ? mode === "create"
              ? "Creating question…"
              : "Saving changes…"
            : submitLabel}
        </Button>
        <Button
          component={Link}
          href={cancelHref}
          sx={{
            minHeight: 42,
            border: "1px solid #E4DCD0",
            borderRadius: "10px",
            color: "#2A2622",
            textTransform: "none",
            fontWeight: 500,
            px: 2,
          }}
        >
          Cancel
        </Button>
      </Box>
    </Card>
  );
}
