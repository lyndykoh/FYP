"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CircularProgress from "@mui/material/CircularProgress";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import Stack from "@/components/lumina-stack";
import { getBrowserSession } from "@/lib/auth";
import {
  fetchManualAnswerDraft,
  SubmissionApiError,
  saveManualAnswers,
} from "@/services/submissions";
import { fetchStudentSelfProfile } from "@/services/students";
import {
  fetchStudentWorksheet,
  fetchTutorWorksheet,
  type StudentWorksheetDetail,
  type TutorWorksheet,
  type WorksheetQuestion,
} from "@/services/worksheets";

const primary = {
  bgcolor: "#9E3A24",
  color: "#FFFDFA",
  textTransform: "none",
  "&:hover": { bgcolor: "#8A3120" },
};
const secondary = {
  borderColor: "#DCCFBE",
  color: "#4A443D",
  textTransform: "none",
};
type WorksheetContext =
  | Pick<StudentWorksheetDetail, "id" | "title" | "instructions" | "questions">
  | Pick<TutorWorksheet, "id" | "title" | "instructions" | "questions">;

function positiveId(value: string | null): number | null {
  if (!value || !/^[1-9]\d*$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function questionInput(question: WorksheetQuestion): {
  multiline: boolean;
  label: string;
  helper: string;
} {
  if (
    question.questionType === "MULTIPLE_CHOICE" ||
    question.questionType === "TRUE_FALSE"
  ) {
    return {
      multiline: false,
      label: "Selected answer",
      helper: "Enter the selected option exactly as shown in the question.",
    };
  }
  return {
    multiline: true,
    label: "Student answer",
    helper: "Leave blank if this question is unanswered.",
  };
}

type ChoicePrompt = { stem: string; options: string[] };
function choicePrompt(question: WorksheetQuestion): ChoicePrompt | null {
  if (question.questionType === "TRUE_FALSE")
    return { stem: question.prompt, options: ["True", "False"] };
  if (question.questionType !== "MULTIPLE_CHOICE") return null;
  const firstOption = question.prompt.search(/\sA\.\s+/);
  if (firstOption < 0) return null;
  const stem = question.prompt.slice(0, firstOption).trim();
  const options = Array.from(
    question.prompt
      .slice(firstOption + 1)
      .matchAll(/(?:^|\s)([A-Z])\.\s*(.*?)(?=\s+[A-Z]\.\s+|$)/g),
  )
    .map((match) => `${match[1]}. ${match[2].trim()}`)
    .filter((option) => option.length > 3);
  return stem && options.length >= 2 ? { stem, options } : null;
}

function ManualAnswersPage() {
  const params = useSearchParams();
  const router = useRouter();
  const session = getBrowserSession();
  const isStudent = session?.role === "STUDENT";
  const worksheetId = positiveId(params.get("worksheetId"));
  const requestedStudentId = positiveId(params.get("studentId"));
  const classId = positiveId(params.get("classId"));
  const [context, setContext] = React.useState<WorksheetContext | null>(null);
  const [studentId, setStudentId] = React.useState<number | null>(null);
  const [answers, setAnswers] = React.useState<Record<number, string>>({});
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [saving, setSaving] = React.useState<"draft" | "submit" | null>(null);
  const [saved, setSaved] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (
      worksheetId === null ||
      (!isStudent && (requestedStudentId === null || classId === null))
    ) {
      queueMicrotask(() => {
        setLoading(false);
        setError(
          "This worksheet submission could not be loaded. Please try again.",
        );
      });
      return;
    }
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoading(true);
        setError(null);
        setContext(null);
        setStudentId(null);
      }
    });
    const request = isStudent
      ? Promise.all([
          fetchStudentSelfProfile(),
          fetchStudentWorksheet(worksheetId),
        ])
      : Promise.all([
          Promise.resolve({ id: requestedStudentId! }),
          fetchTutorWorksheet(worksheetId),
        ]);
    void request
      .then(async ([student, worksheet]) => {
        if (!active) return;
        setStudentId(student.id);
        setContext(worksheet);
        const draft = await fetchManualAnswerDraft({
          studentId: student.id,
          worksheetId: worksheet.id,
          ...(isStudent ? {} : { classId: classId! }),
        });
        if (!active) return;
        setAnswers(
          Object.fromEntries(
            worksheet.questions.map((question) => [
              question.id,
              draft.answers.find(
                (answer) => answer.questionBankId === question.id,
              )?.answer || "",
            ]),
          ),
        );
        if (draft.status === "PENDING_REVIEW") {
          setSaved(
            "This manual submission is already in the Tutor review queue.",
          );
        }
      })
      .catch((reason: unknown) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : "This worksheet submission could not be loaded. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [classId, isStudent, requestedStudentId, worksheetId]);

  const usableAnswers =
    context?.questions
      .map((question) => ({
        questionBankId: question.id,
        answer: answers[question.id] || "",
      }))
      .filter((answer) => answer.answer.trim()) ?? [];

  const save = async (submit: boolean) => {
    if (
      worksheetId === null ||
      studentId === null ||
      usableAnswers.length === 0
    ) {
      setError("Enter at least one answer before saving.");
      return;
    }
    setSaving(submit ? "submit" : "draft");
    setError(null);
    setSaved(null);
    try {
      const result = await saveManualAnswers({
        studentId,
        worksheetId,
        ...(isStudent ? {} : { classId: classId! }),
        answers: usableAnswers,
        submit,
      });
      if (result.status === "PENDING_REVIEW") {
        router.replace(
          isStudent
            ? `/worksheets/${worksheetId}/results`
            : `/tutor/reviews/${result.submissionIds[0]}`,
        );
        return;
      }
      setSaved(
        "Draft saved. You can continue editing before submitting for Tutor review.",
      );
    } catch (reason) {
      setError(
        reason instanceof SubmissionApiError
          ? reason.message
          : "Your answers could not be saved. Please try again.",
      );
    } finally {
      setSaving(null);
    }
  };

  const returnPath = isStudent ? "/worksheets" : "/upload";
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 4 },
        py: 4,
      }}
    >
      <Box sx={{ maxWidth: 850, mx: "auto" }}>
        <Typography
          sx={{ fontSize: 12, letterSpacing: ".1em", color: "#6F675E", mb: 2 }}
        >
          MANUAL ANSWER ENTRY
        </Typography>
        {loading ? (
          <Stack alignItems="center" gap={2} sx={{ py: 8 }}>
            <CircularProgress aria-label="Loading worksheet answers" />
            <Typography>Loading worksheet questions…</Typography>
          </Stack>
        ) : null}
        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{ p: 2.5, mb: 2, borderColor: "#D79B63", bgcolor: "#FFFDFA" }}
          >
            <Typography sx={{ color: "#9E3A24", fontWeight: 700 }}>
              {error}
            </Typography>
            <Button component={Link} href={returnPath} sx={{ mt: 1 }}>
              Return to {isStudent ? "My Worksheets" : "upload"}
            </Button>
          </Card>
        ) : null}
        {saved ? (
          <Card
            role="status"
            variant="outlined"
            sx={{ p: 2, mb: 2, borderColor: "#87A878", bgcolor: "#F7FBF4" }}
          >
            <Typography>{saved}</Typography>
          </Card>
        ) : null}
        {context ? (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
                mb: 0.5,
              }}
            >
              Enter student answers
            </Typography>
            <Typography sx={{ color: "#6F675E", mb: 2 }}>
              Manual answers and OCR-confirmed answers are saved against the
              same worksheet questions and enter the same Tutor review queue.
            </Typography>
            <Card
              variant="outlined"
              sx={{ p: 2, mb: 2, bgcolor: "#FFFDFA", borderColor: "#EBE4D9" }}
            >
              <Typography sx={{ fontWeight: 700 }}>{context.title}</Typography>
              {context.instructions ? (
                <Typography
                  sx={{ color: "#6F675E", mt: 0.5, whiteSpace: "pre-wrap" }}
                >
                  {context.instructions}
                </Typography>
              ) : null}
            </Card>
            {context.questions.length === 0 ? (
              <Card role="status" variant="outlined" sx={{ p: 2 }}>
                <Typography>
                  This worksheet has no questions available for answer entry.
                </Typography>
              </Card>
            ) : (
              context.questions.map((question, index) => {
                const input = questionInput(question);
                const choices = choicePrompt(question);
                return (
                  <Card
                    key={question.id}
                    component="section"
                    variant="outlined"
                    sx={{
                      p: 2,
                      mb: 1.5,
                      bgcolor: "#FFFDFA",
                      borderColor: "#EBE4D9",
                    }}
                  >
                    <Typography sx={{ fontWeight: 700 }}>
                      Question {index + 1} · {question.totalMarks.toFixed(1)}{" "}
                      marks
                    </Typography>
                    <Typography sx={{ mt: 0.75, whiteSpace: "pre-wrap" }}>
                      {choices?.stem ?? question.prompt}
                    </Typography>
                    {choices ? (
                      <FormControl component="fieldset" sx={{ mt: 1.25 }}>
                        <FormLabel>Selected answer</FormLabel>
                        <RadioGroup
                          value={answers[question.id] || ""}
                          onChange={(event) =>
                            setAnswers((current) => ({
                              ...current,
                              [question.id]: event.target.value,
                            }))
                          }
                        >
                          {choices.options.map((option) => (
                            <FormControlLabel
                              key={option}
                              value={option}
                              control={<Radio />}
                              label={option}
                            />
                          ))}
                        </RadioGroup>
                      </FormControl>
                    ) : (
                      <TextField
                        fullWidth
                        sx={{ mt: 1.5 }}
                        label={input.label}
                        helperText={input.helper}
                        multiline={input.multiline}
                        minRows={input.multiline ? 3 : undefined}
                        value={answers[question.id] || ""}
                        onChange={(event) =>
                          setAnswers((current) => ({
                            ...current,
                            [question.id]: event.target.value,
                          }))
                        }
                      />
                    )}
                  </Card>
                );
              })
            )}
            <Stack
              direction={{ xs: "column-reverse", sm: "row" }}
              justifyContent="space-between"
              gap={1}
              sx={{ mt: 3 }}
            >
              <Button
                component={Link}
                href={returnPath}
                variant="outlined"
                sx={secondary}
              >
                Cancel
              </Button>
              <Stack direction="row" gap={1}>
                <Button
                  onClick={() => void save(false)}
                  disabled={saving !== null || usableAnswers.length === 0}
                  variant="outlined"
                  sx={secondary}
                >
                  {saving === "draft" ? "Saving…" : "Save draft"}
                </Button>
                <Button
                  onClick={() => void save(true)}
                  disabled={saving !== null || usableAnswers.length === 0}
                  sx={primary}
                >
                  {saving === "submit"
                    ? "Submitting…"
                    : "Submit for Tutor Review"}
                </Button>
              </Stack>
            </Stack>
          </>
        ) : null}
      </Box>
    </Box>
  );
}

export default function Page() {
  return (
    <React.Suspense fallback={null}>
      <ManualAnswersPage />
    </React.Suspense>
  );
}
