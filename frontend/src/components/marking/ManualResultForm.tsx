"use client";

import * as React from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import {
  createManualResults,
  type ManualResultBatchRequest,
  type MarkingReview,
} from "@/services/submissions";
import type { TutorWorksheet } from "@/services/worksheets";

export type ManualResultStudent = { id: number; fullName: string };
type DraftEntry = { answer: string; marks: string; feedback: string };

type Props = {
  worksheet: TutorWorksheet;
  students: ManualResultStudent[];
  existingResults?: MarkingReview[];
  submit?: (input: ManualResultBatchRequest) => Promise<MarkingReview[]>;
  onCreated?: (reviews: MarkingReview[]) => void;
};

const blankDraft = (): DraftEntry => ({ answer: "", marks: "", feedback: "" });

type ManualResultEntry = ManualResultBatchRequest["entries"][number];

function buildManualResultEntries(
  questions: TutorWorksheet["questions"],
  drafts: Record<number, DraftEntry>,
): ManualResultEntry[] {
  return questions.map((question) => {
    const draft = drafts[question.id] ?? blankDraft();

    return {
      questionBankId: question.id,
      answer: draft.answer.trim(),
      marks: Number(draft.marks),
      feedback: draft.feedback.trim(),
    };
  });
}

function findInvalidEntry(
  entries: ManualResultEntry[],
  questions: TutorWorksheet["questions"],
) {
  return entries.find(
    (entry, index) =>
      !entry.answer ||
      !entry.feedback ||
      !Number.isFinite(entry.marks) ||
      entry.marks < 0 ||
      entry.marks > questions[index]!.totalMarks,
  );
}

/** Tutor-entered fallback that records one selected Student's result atomically. */
export default function ManualResultForm({
  worksheet,
  students,
  existingResults = [],
  submit = createManualResults,
  onCreated,
}: Props) {
  const [studentId, setStudentId] = React.useState("");
  const [drafts, setDrafts] = React.useState<Record<number, DraftEntry>>({});
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const selectedStudentId = Number(studentId);
  const existingByQuestion = React.useMemo(
    () =>
      new Map(
        existingResults
          .filter((result) => result.studentId === selectedStudentId)
          .map((result) => [result.questionBankId, result]),
      ),
    [existingResults, selectedStudentId],
  );
  const pendingQuestions = worksheet.questions.filter(
    (question) => !existingByQuestion.has(question.id),
  );

  const setDraft = (questionId: number, patch: Partial<DraftEntry>) => {
    setDrafts((current) => ({
      ...current,
      [questionId]: { ...(current[questionId] ?? blankDraft()), ...patch },
    }));
  };
  const submitForm = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!Number.isSafeInteger(selectedStudentId) || selectedStudentId <= 0) {
      setError("Choose the student whose result you are approving.");
      return;
    }
    if (pendingQuestions.length === 0) {
      setError(
        "Every worksheet question already has an approved result. Open a result to revise it.",
      );
      return;
    }
    const entries = buildManualResultEntries(pendingQuestions, drafts);
    const invalid = findInvalidEntry(entries, pendingQuestions);
    if (invalid) {
      const question = pendingQuestions.find(
        (item) => item.id === invalid.questionBankId,
      );
      setError(
        `${question?.code ?? "This question"} needs an answer, tutor feedback and marks within its allocation.`,
      );
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onCreated?.(
        await submit({
          worksheetId: worksheet.id,
          studentId: selectedStudentId,
          entries,
        }),
      );
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The manual results could not be saved.",
      );
    } finally {
      setBusy(false);
    }
  };

  const isUnavailable =
    worksheet.status !== "APPROVED" ||
    students.length === 0 ||
    worksheet.questions.length === 0;
  return (
    <Card
      component="section"
      variant="outlined"
      sx={{
        maxWidth: 920,
        mx: "auto",
        p: { xs: 2, sm: 3 },
        borderRadius: "14px",
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
      }}
    >
      <Typography
        component="h1"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: { xs: 28, sm: 36 },
          mb: 0.5,
        }}
      >
        Enter results manually
      </Typography>
      <Typography sx={{ color: "#6F675E", mb: 2 }}>
        {worksheet.title}
      </Typography>
      <Alert severity="info" sx={{ mb: 2 }}>
        Tutor-entered marks are approved immediately and recorded with their
        audit history. Each submitted question is saved as one atomic approval
        set; OCR pages are never created or changed here.
      </Alert>
      {isUnavailable ? (
        <Alert severity="warning" role="alert">
          {worksheet.status !== "APPROVED"
            ? "Approve and assign this worksheet before entering results."
            : "This worksheet needs at least one assigned student and question."}
        </Alert>
      ) : (
        <Box
          component="form"
          noValidate
          onSubmit={(event) => void submitForm(event)}
        >
          <Stack spacing={2.25}>
            {error && (
              <Alert severity="error" role="alert">
                {error}
              </Alert>
            )}
            <FormControl fullWidth required>
              <InputLabel id="manual-result-student-label">Student</InputLabel>
              <Select
                aria-label="Student"
                labelId="manual-result-student-label"
                id="manual-result-student"
                value={studentId}
                label="Student"
                onChange={(event) => {
                  setStudentId(event.target.value);
                  setError(null);
                }}
                disabled={busy}
              >
                {students.map((student) => (
                  <MenuItem key={student.id} value={String(student.id)}>
                    {student.fullName}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {!studentId ? (
              <Alert severity="info">
                Select a student to enter their complete worksheet result.
              </Alert>
            ) : (
              <Stack spacing={2}>
                <Typography
                  aria-live="polite"
                  sx={{ color: "#5F574E", fontSize: 14 }}
                >
                  {existingByQuestion.size} of {worksheet.questions.length}{" "}
                  questions already approved for this student.
                </Typography>
                {worksheet.questions.map((question) => {
                  const existing = existingByQuestion.get(question.id);
                  const draft = drafts[question.id] ?? blankDraft();
                  return (
                    <Card
                      key={question.id}
                      variant="outlined"
                      sx={{
                        p: { xs: 1.75, sm: 2.25 },
                        borderColor: "#EBE4D9",
                        bgcolor: existing ? "#FBF9F5" : "#FFFDFA",
                      }}
                    >
                      <Stack spacing={1.5}>
                        <Stack
                          direction="row"
                          sx={{
                            justifyContent: "space-between",
                            alignItems: "center",
                            gap: 1,
                            flexWrap: "wrap",
                          }}
                        >
                          <Box sx={{ minWidth: 0 }}>
                            <Typography
                              sx={{
                                color: "#9E3A24",
                                fontSize: 12,
                                fontWeight: 700,
                                letterSpacing: ".08em",
                              }}
                            >
                              {question.code}
                            </Typography>
                            <Typography
                              sx={{ color: "#2A2622", fontWeight: 600 }}
                            >
                              {question.prompt}
                            </Typography>
                            <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
                              {question.topicName} ·{" "}
                              {question.totalMarks.toFixed(2)} marks
                            </Typography>
                          </Box>
                          {existing && (
                            <Chip
                              label={`Approved · ${existing.approvedMarks?.toFixed(2) ?? "—"}/${question.totalMarks.toFixed(2)}`}
                              size="small"
                              sx={{ bgcolor: "#E9EEE8", color: "#4A6B50" }}
                            />
                          )}
                        </Stack>
                        <Divider />
                        {existing ? (
                          <Stack
                            direction="row"
                            sx={{
                              justifyContent: "space-between",
                              alignItems: "center",
                              gap: 1,
                              flexWrap: "wrap",
                            }}
                          >
                            <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
                              An approved entry already exists. Use the review
                              screen to revise it; a duplicate will not be
                              created.
                            </Typography>
                            <Button
                              component={Link}
                              href={`/tutor/reviews/${existing.id}`}
                              size="small"
                              sx={{ color: "#9E3A24", textTransform: "none" }}
                            >
                              Review or edit result
                            </Button>
                          </Stack>
                        ) : (
                          <Stack spacing={1.5}>
                            <TextField
                              id={`manual-answer-${question.id}`}
                              label={`Student answer or observation for ${question.code}`}
                              value={draft.answer}
                              onChange={(event) =>
                                setDraft(question.id, {
                                  answer: event.target.value,
                                })
                              }
                              multiline
                              minRows={3}
                              required
                              disabled={busy}
                              slotProps={{
                                htmlInput: {
                                  "aria-label": `Student answer or observation for ${question.code}`,
                                },
                              }}
                            />
                            <TextField
                              id={`manual-marks-${question.id}`}
                              label={`Marks for ${question.code} (out of ${question.totalMarks})`}
                              value={draft.marks}
                              onChange={(event) =>
                                setDraft(question.id, {
                                  marks: event.target.value,
                                })
                              }
                              type="number"
                              required
                              disabled={busy}
                              slotProps={{
                                htmlInput: {
                                  "aria-label": `Marks for ${question.code} (out of ${question.totalMarks})`,
                                  min: 0,
                                  max: question.totalMarks,
                                  step: 0.01,
                                },
                              }}
                            />
                            <TextField
                              id={`manual-feedback-${question.id}`}
                              label={`Tutor feedback for ${question.code}`}
                              value={draft.feedback}
                              onChange={(event) =>
                                setDraft(question.id, {
                                  feedback: event.target.value,
                                })
                              }
                              multiline
                              minRows={2}
                              required
                              disabled={busy}
                              helperText="Stored with the approved result."
                              slotProps={{
                                htmlInput: {
                                  "aria-label": `Tutor feedback for ${question.code}`,
                                },
                              }}
                            />
                          </Stack>
                        )}
                      </Stack>
                    </Card>
                  );
                })}
                <Button
                  type="submit"
                  disabled={busy || pendingQuestions.length === 0}
                  sx={{
                    alignSelf: "start",
                    bgcolor: "#9E3A24",
                    color: "#FFFDFA",
                    textTransform: "none",
                    "&:hover": { bgcolor: "#7E2E1D" },
                  }}
                >
                  {busy
                    ? "Saving approved results…"
                    : `Save ${pendingQuestions.length} approved result${pendingQuestions.length === 1 ? "" : "s"}`}
                </Button>
              </Stack>
            )}
          </Stack>
        </Box>
      )}
    </Card>
  );
}
