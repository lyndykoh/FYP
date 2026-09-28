"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import {
  approveWorksheet,
  downloadWorksheetPdf,
  fetchWorksheetImageUrl,
  updateWorksheet,
  type TutorWorksheet,
  type UpdateWorksheetRequest,
} from "@/services/worksheets";
import {
  fetchTutorQuestions,
  type QuestionBankItem,
} from "@/services/questions";
import SecureImageGallery from "@/components/questions/SecureImageGallery";

type Props = {
  worksheet: TutorWorksheet;
  approve?: typeof approveWorksheet;
  update?: typeof updateWorksheet;
  downloadPdf?: typeof downloadWorksheetPdf;
  loadQuestions?: typeof fetchTutorQuestions;
  onApproved?: (worksheet: TutorWorksheet) => void;
};

const lightCard = {
  borderColor: "#EBE4D9",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
};

function assignmentLabel(worksheet: TutorWorksheet): string {
  if (worksheet.assignments.length === 0) return "Not assigned yet";
  if (worksheet.targetMode === "CLASS")
    return `Class #${worksheet.assignments[0].classId}`;
  return `${worksheet.assignments.length} selected student${worksheet.assignments.length === 1 ? "" : "s"}`;
}

function datetimeLocal(value: string | null): string {
  return value ? value.slice(0, 16) : "";
}

function formatDueDate(value: string | null): string {
  if (!value) return "No due date";
  const parsed = new Date(`${value}Z`);
  return Number.isNaN(parsed.getTime())
    ? "No due date"
    : `Due ${parsed.toLocaleString()}`;
}

function statusPresentation(worksheet: TutorWorksheet): {
  label: string;
  sx: Record<string, string>;
} {
  if (worksheet.status === "DRAFT")
    return { label: "GENERATED", sx: { bgcolor: "#F0EAE0", color: "#6F675E" } };
  if (worksheet.status === "APPROVED")
    return { label: "ASSIGNED", sx: { bgcolor: "#F3EBDD", color: "#7A6238" } };
  return { label: "ARCHIVED", sx: { bgcolor: "#F0EAE0", color: "#6F675E" } };
}

function questionTypeLabel(value: string): string {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

/** Owner-only worksheet management, deliberately separate from the Student result route. */
export default function TutorWorksheetDetail({
  worksheet,
  approve = approveWorksheet,
  update = updateWorksheet,
  downloadPdf = downloadWorksheetPdf,
  loadQuestions = fetchTutorQuestions,
  onApproved,
}: Props) {
  const [current, setCurrent] = React.useState(worksheet);
  const [editing, setEditing] = React.useState(false);
  const [title, setTitle] = React.useState(worksheet.title);
  const [instructions, setInstructions] = React.useState(
    worksheet.instructions ?? "",
  );
  const [questionIds, setQuestionIds] = React.useState(
    worksheet.questions.map((question) => question.id),
  );
  const [dueAt, setDueAt] = React.useState(datetimeLocal(worksheet.dueAt));
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const approvalInProgress = React.useRef(false);
  const [questionBank, setQuestionBank] = React.useState<
    QuestionBankItem[] | null
  >(null);
  const [loadingQuestionBank, setLoadingQuestionBank] = React.useState(false);
  const [replacingQuestionId, setReplacingQuestionId] = React.useState<
    number | null
  >(null);

  const resetForm = React.useCallback((value: TutorWorksheet) => {
    setTitle(value.title);
    setInstructions(value.instructions ?? "");
    setQuestionIds(value.questions.map((question) => question.id));
    setDueAt(datetimeLocal(value.dueAt));
  }, []);
  const run = async (operation: () => Promise<TutorWorksheet>) => {
    setBusy(true);
    setError(null);
    try {
      const next = await operation();
      setCurrent(next);
      resetForm(next);
      setEditing(false);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Worksheet could not be updated.",
      );
    } finally {
      setBusy(false);
    }
  };
  const approveDraft = async () => {
    if (current.status !== "DRAFT" || approvalInProgress.current) return;

    approvalInProgress.current = true;
    setBusy(true);
    setError(null);

    try {
      const approvedWorksheet = await approve(current.id, dueAt || undefined);
      setCurrent(approvedWorksheet);
      resetForm(approvedWorksheet);
      setEditing(false);
      onApproved?.(approvedWorksheet);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Worksheet could not be approved.",
      );
    } finally {
      approvalInProgress.current = false;
      setBusy(false);
    }
  };
  const saveDraft = () =>
    run(() =>
      update(current.id, {
        title: title.trim(),
        instructions: instructions.trim() || null,
        questionIds,
      } satisfies UpdateWorksheetRequest),
    );
  const moveQuestion = (index: number, direction: -1 | 1) => {
    const destination = index + direction;
    if (destination < 0 || destination >= questionIds.length) return;
    setQuestionIds((ids) => {
      const reordered = [...ids];
      [reordered[index], reordered[destination]] = [
        reordered[destination],
        reordered[index],
      ];
      return reordered;
    });
  };
  const loadActiveQuestionBank = async () => {
    setLoadingQuestionBank(true);
    setError(null);
    try {
      const topicIds = [
        ...new Set(current.questions.map((question) => question.topicId)),
      ];
      const pages = await Promise.all(
        topicIds.length
          ? topicIds.map((topicId) =>
              loadQuestions({ topicId, archiveState: "ACTIVE", size: 100 }),
            )
          : [loadQuestions({ archiveState: "ACTIVE", size: 100 })],
      );
      setQuestionBank([
        ...new Map(
          pages
            .flatMap((page) => page.items)
            .map((question) => [question.id, question]),
        ).values(),
      ]);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The active question bank could not be loaded.",
      );
    } finally {
      setLoadingQuestionBank(false);
    }
  };
  const removeQuestion = (questionId: number) => {
    if (questionIds.length <= 1) return;
    setQuestionIds((ids) => ids.filter((id) => id !== questionId));
    if (replacingQuestionId === questionId) setReplacingQuestionId(null);
  };
  const addOrReplaceQuestion = (questionId: number) => {
    if (replacingQuestionId === null) {
      setQuestionIds((ids) =>
        ids.includes(questionId) ? ids : [...ids, questionId],
      );
      return;
    }
    setQuestionIds((ids) =>
      ids.map((id) => (id === replacingQuestionId ? questionId : id)),
    );
    setReplacingQuestionId(null);
  };
  const questionById = new Map<number, TutorWorksheet["questions"][number]>();
  current.questions.forEach((question) =>
    questionById.set(question.id, question),
  );
  questionBank?.forEach((question) =>
    questionById.set(question.id, {
      id: question.id,
      code: question.code,
      prompt: question.prompt,
      totalMarks: question.totalMarks,
      questionType: question.questionType,
      topicId: question.syllabusTopic.id,
      topicName: question.syllabusTopic.name,
    }),
  );
  const orderedQuestions = questionIds
    .map((id) => questionById.get(id))
    .filter((question): question is TutorWorksheet["questions"][number] =>
      Boolean(question),
    );
  const totalMarks = orderedQuestions.reduce(
    (sum, question) => sum + question.totalMarks,
    0,
  );
  const topics = [
    ...new Set(orderedQuestions.map((question) => question.topicName)),
  ];
  const availableQuestions =
    questionBank?.filter((question) => !questionIds.includes(question.id)) ??
    [];
  const status = statusPresentation(current);
  const editable = current.status === "DRAFT";
  const approved = current.status === "APPROVED";
  const exportPdf = async () => {
    setBusy(true);
    setError(null);
    try {
      const blob = await downloadPdf(current.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${current.code || current.title}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Worksheet PDF could not be created.",
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box
      data-testid="tutor-worksheet-detail"
      sx={{
        maxWidth: 1420,
        mx: "auto",
        py: { xs: 2, sm: 3 },
        px: { xs: 1, sm: 0 },
      }}
    >
      <Button
        component={Link}
        href="/tutor/worksheets"
        sx={{ textTransform: "none", color: "#6F675E", mb: 1 }}
      >
        Back to worksheets
      </Button>
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          justifyContent: "space-between",
          alignItems: "start",
          gap: 2,
          mb: 2.5,
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Stack
            direction="row"
            spacing={1}
            useFlexGap
            sx={{ mb: 1, flexWrap: "wrap" }}
          >
            <Box
              component="span"
              sx={{
                px: 1.1,
                py: 0.5,
                borderRadius: "20px",
                fontSize: 10,
                lineHeight: 1,
                fontWeight: 700,
                letterSpacing: ".05em",
                whiteSpace: "nowrap",
                ...status.sx,
              }}
            >
              {status.label}
            </Box>
            <Typography
              component="span"
              sx={{ fontSize: 12, color: "#8B837A", pt: 0.2 }}
            >
              {current.worksheetType ?? "STANDARD"} worksheet
            </Typography>
          </Stack>
          <Typography
            component="h1"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: { xs: 30, sm: 38 },
              lineHeight: 1.15,
            }}
          >
            {current.title}
          </Typography>
          <Typography sx={{ color: "#6F675E", mt: 0.75 }}>
            {current.subject || "Subject not recorded"} ·{" "}
            {current.questions.length} question
            {current.questions.length === 1 ? "" : "s"} ·{" "}
            {totalMarks.toFixed(1)} marks
          </Typography>
        </Box>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1}
          sx={{ flex: "0 0 auto" }}
        >
          {editable && (
            <>
              <Button
                onClick={() => {
                  resetForm(current);
                  setQuestionBank(null);
                  setReplacingQuestionId(null);
                  setError(null);
                  setEditing(true);
                }}
                disabled={busy || editing}
                sx={{
                  border: "1px solid #E4DCD0",
                  color: "#2A2622",
                  textTransform: "none",
                  bgcolor: "#FFFDFA",
                }}
              >
                Edit worksheet
              </Button>
              <Button
                onClick={() => void approveDraft()}
                disabled={busy || current.questions.length === 0}
                sx={{
                  bgcolor: "#9E3A24",
                  color: "#FFFDFA",
                  textTransform: "none",
                  "&:hover": { bgcolor: "#8A3120" },
                }}
              >
                Approve & assign worksheet
              </Button>
            </>
          )}
          {approved && (
            <>
              <Button
                component={Link}
                href={`/tutor/worksheets/${current.id}/results/new`}
                sx={{
                  border: "1px solid #E4DCD0",
                  color: "#2A2622",
                  textTransform: "none",
                  bgcolor: "#FFFDFA",
                }}
              >
                Enter result manually
              </Button>
              <Button
                component={Link}
                href={`/upload?worksheetId=${current.id}${current.sourceClassId ? `&classId=${current.sourceClassId}` : ""}`}
                sx={{
                  border: "1px solid #E4DCD0",
                  color: "#2A2622",
                  textTransform: "none",
                  bgcolor: "#FFFDFA",
                }}
              >
                Upload student work
              </Button>
              <Button
                onClick={() => void exportPdf()}
                disabled={busy}
                sx={{
                  bgcolor: "#9E3A24",
                  color: "#FFFDFA",
                  textTransform: "none",
                  "&:hover": { bgcolor: "#8A3120" },
                }}
              >
                Download PDF
              </Button>
            </>
          )}
        </Stack>
      </Box>
      {current.status === "ARCHIVED" && (
        <Box
          role="status"
          sx={{
            bgcolor: "#F6EFE6",
            borderLeft: "3px solid #B4573F",
            borderRadius: "0 10px 10px 0",
            p: 1.5,
            mb: 2.5,
            color: "#5A544C",
            fontSize: 13,
          }}
        >
          This worksheet is archived and remains available as a read-only
          record.
        </Box>
      )}
      {editable && current.questions.length === 0 && (
        <Box
          role="status"
          sx={{
            bgcolor: "#F6EFE6",
            borderLeft: "3px solid #B4573F",
            borderRadius: "0 10px 10px 0",
            p: 1.5,
            mb: 2.5,
            color: "#5A544C",
            fontSize: 13,
          }}
        >
          Add at least one question before approval can assign this worksheet.
        </Box>
      )}
      {error && (
        <Box
          role="alert"
          sx={{
            bgcolor: "#F6EFE6",
            borderLeft: "3px solid #B4573F",
            borderRadius: "0 10px 10px 0",
            p: 1.5,
            mb: 2.5,
            color: "#5A544C",
            fontSize: 13,
          }}
        >
          {error}
        </Box>
      )}
      {editing && editable && (
        <Card
          component="section"
          variant="outlined"
          sx={{ ...lightCard, p: { xs: 2, sm: 3 }, mb: 2.5 }}
        >
          <Typography
            component="h2"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 23,
              mb: 0.5,
            }}
          >
            Edit generated draft
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 13, mb: 2 }}>
            Change the content and question order before the Tutor approves
            assignment.
          </Typography>
          <Stack spacing={2}>
            <TextField
              label="Title"
              value={title}
              required
              onChange={(event) => setTitle(event.target.value)}
              disabled={busy}
            />
            <TextField
              label="Instructions"
              value={instructions}
              multiline
              minRows={3}
              onChange={(event) => setInstructions(event.target.value)}
              disabled={busy}
            />
            <TextField
              label="Assignment due date"
              type="datetime-local"
              value={dueAt}
              onChange={(event) => setDueAt(event.target.value)}
              disabled={busy}
              slotProps={{ inputLabel: { shrink: true } }}
              helperText="One deadline applies to every assigned student."
            />
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ flexWrap: "wrap" }}
            >
              <Button
                onClick={() => void saveDraft()}
                disabled={busy || !title.trim() || questionIds.length === 0}
                sx={{
                  bgcolor: "#9E3A24",
                  color: "#FFFDFA",
                  textTransform: "none",
                  "&:hover": { bgcolor: "#8A3120" },
                }}
              >
                {busy ? "Saving draft…" : "Save worksheet draft"}
              </Button>
              <Button
                onClick={() => {
                  resetForm(current);
                  setQuestionBank(null);
                  setReplacingQuestionId(null);
                  setEditing(false);
                  setError(null);
                }}
                disabled={busy}
                sx={{
                  border: "1px solid #E4DCD0",
                  color: "#6F675E",
                  textTransform: "none",
                }}
              >
                Cancel edit
              </Button>
            </Stack>
          </Stack>
        </Card>
      )}
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2.5 }}>
        <Box sx={{ flex: "1 1 460px", minWidth: 0 }}>
          <Card
            component="section"
            variant="outlined"
            sx={{ ...lightCard, p: { xs: 2, sm: 3 } }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 23,
                mb: 0.5,
              }}
            >
              Questions
            </Typography>
            <Typography sx={{ color: "#6F675E", fontSize: 13, mb: 2 }}>
              {topics.length
                ? `Topics: ${topics.join(" · ")}`
                : "Topics are unavailable for this legacy worksheet."}
            </Typography>
            {orderedQuestions.length === 0 ? (
              <Box
                sx={{
                  border: "1px dashed #DCCFBE",
                  borderRadius: "12px",
                  p: 2,
                  color: "#6F675E",
                  fontSize: 13,
                }}
              >
                No questions are attached to this worksheet yet.
              </Box>
            ) : (
              <Box component="ol" sx={{ m: 0, pl: 3.5 }}>
                {orderedQuestions.map((question, index) => (
                  <Box
                    component="li"
                    key={question.id}
                    sx={{
                      py: 1.5,
                      borderBottom:
                        index === orderedQuestions.length - 1
                          ? "none"
                          : "1px solid #F0EAE0",
                    }}
                  >
                    <Box
                      sx={{
                        display: "flex",
                        flexWrap: "wrap",
                        justifyContent: "space-between",
                        alignItems: "start",
                        gap: 1,
                      }}
                    >
                      <Box sx={{ minWidth: 0, flex: "1 1 300px" }}>
                        <Typography
                          sx={{
                            fontSize: 13.5,
                            fontWeight: 600,
                            lineHeight: 1.45,
                          }}
                        >
                          {question.prompt}
                        </Typography>
                        <SecureImageGallery
                          images={question.images ?? []}
                          loadImage={(imageId) =>
                            fetchWorksheetImageUrl("tutor", current.id, imageId)
                          }
                        />
                        <Typography
                          sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.5 }}
                        >
                          {question.code} · {question.topicName} ·{" "}
                          {questionTypeLabel(question.questionType)} ·{" "}
                          {question.totalMarks.toFixed(1)} marks
                        </Typography>
                      </Box>
                      {editing && (
                        <Stack
                          direction="row"
                          spacing={0.5}
                          useFlexGap
                          sx={{ flexWrap: "wrap" }}
                        >
                          <Button
                            size="small"
                            aria-label={`Move question ${index + 1} up`}
                            onClick={() => moveQuestion(index, -1)}
                            disabled={index === 0 || busy}
                            sx={{ minWidth: 34, color: "#6F675E" }}
                          >
                            Up
                          </Button>
                          <Button
                            size="small"
                            aria-label={`Move question ${index + 1} down`}
                            onClick={() => moveQuestion(index, 1)}
                            disabled={
                              index === orderedQuestions.length - 1 || busy
                            }
                            sx={{ minWidth: 34, color: "#6F675E" }}
                          >
                            Down
                          </Button>
                          <Button
                            size="small"
                            aria-label={`Replace question ${index + 1}`}
                            onClick={() => setReplacingQuestionId(question.id)}
                            disabled={
                              busy ||
                              loadingQuestionBank ||
                              questionBank === null
                            }
                            sx={{ minWidth: 34, color: "#6F675E" }}
                          >
                            Replace
                          </Button>
                          <Button
                            size="small"
                            aria-label={`Remove question ${index + 1}`}
                            onClick={() => removeQuestion(question.id)}
                            disabled={busy || questionIds.length <= 1}
                            sx={{ minWidth: 34, color: "#B4573F" }}
                          >
                            Remove
                          </Button>
                        </Stack>
                      )}
                    </Box>
                  </Box>
                ))}
              </Box>
            )}
            {editing && (
              <Box
                component="section"
                aria-label="Draft question controls"
                sx={{ mt: 2, pt: 2, borderTop: "1px solid #F0EAE0" }}
              >
                <Typography sx={{ fontWeight: 700, fontSize: 14 }}>
                  Draft question controls
                </Typography>
                <Typography sx={{ color: "#6F675E", fontSize: 12.5, mt: 0.5 }}>
                  Remove, replace, or add active question-bank items. Changes
                  are saved only when you save this draft.
                </Typography>
                {questionBank === null ? (
                  <Button
                    onClick={() => void loadActiveQuestionBank()}
                    disabled={loadingQuestionBank || busy}
                    sx={{
                      mt: 1,
                      border: "1px solid #E4DCD0",
                      color: "#2A2622",
                      textTransform: "none",
                    }}
                  >
                    {loadingQuestionBank
                      ? "Loading active questions…"
                      : "Load active question bank"}
                  </Button>
                ) : (
                  <Box sx={{ mt: 1.25 }}>
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 12, mb: 0.75 }}
                    >
                      {replacingQuestionId === null
                        ? "Add an active question"
                        : "Choose an active replacement question"}
                    </Typography>
                    {availableQuestions.length ? (
                      <Stack
                        direction="row"
                        spacing={0.75}
                        useFlexGap
                        sx={{ flexWrap: "wrap" }}
                      >
                        {availableQuestions.map((question) => (
                          <Button
                            key={question.id}
                            size="small"
                            onClick={() => addOrReplaceQuestion(question.id)}
                            disabled={busy}
                            sx={{
                              border: "1px solid #E4DCD0",
                              color: "#2A2622",
                              textTransform: "none",
                            }}
                          >
                            {replacingQuestionId === null
                              ? `Add ${question.code}`
                              : `Replace with ${question.code}`}
                          </Button>
                        ))}
                      </Stack>
                    ) : (
                      <Typography sx={{ color: "#8B837A", fontSize: 12 }}>
                        No unselected active questions are available for these
                        worksheet topics.
                      </Typography>
                    )}
                    {replacingQuestionId !== null && (
                      <Button
                        size="small"
                        onClick={() => setReplacingQuestionId(null)}
                        disabled={busy}
                        sx={{
                          mt: 0.75,
                          color: "#6F675E",
                          textTransform: "none",
                        }}
                      >
                        Cancel replacement
                      </Button>
                    )}
                  </Box>
                )}
              </Box>
            )}
          </Card>
        </Box>
        <Box sx={{ flex: "0 1 320px", minWidth: 0 }}>
          <Stack spacing={2.5}>
            <Card
              component="aside"
              variant="outlined"
              sx={{ ...lightCard, p: { xs: 2, sm: 3 } }}
            >
              <Typography
                component="h2"
                sx={{
                  fontFamily: "'Playfair Display', Georgia, serif",
                  fontSize: 23,
                  mb: 1,
                }}
              >
                Assignment
              </Typography>
              <Typography sx={{ fontWeight: 600 }}>
                {assignmentLabel(current)}
              </Typography>
              <Typography sx={{ color: "#6F675E", mt: 0.75, fontSize: 13 }}>
                {formatDueDate(current.dueAt)}
              </Typography>
              <Typography sx={{ color: "#8B837A", mt: 1.5, fontSize: 12 }}>
                {approved
                  ? "Approved and assigned by the Tutor."
                  : editable
                    ? "Awaiting Tutor approval before assignment."
                    : "Archived after its approval lifecycle."}
              </Typography>
              {current.instructions && (
                <>
                  <Typography
                    component="h3"
                    sx={{ fontWeight: 700, mt: 2.5, mb: 0.5, fontSize: 14 }}
                  >
                    Instructions
                  </Typography>
                  <Typography sx={{ fontSize: 13, lineHeight: 1.55 }}>
                    {current.instructions}
                  </Typography>
                </>
              )}
            </Card>
            <Box
              component="section"
              aria-label="Generation provenance"
              sx={{ bgcolor: "#1B1917", borderRadius: "12px", p: 2.25 }}
            >
              <Typography
                sx={{
                  color: "#E08A72",
                  fontSize: 10.5,
                  fontWeight: 700,
                  letterSpacing: ".1em",
                }}
              >
                GENERATION PROVENANCE
              </Typography>
              <Typography
                sx={{
                  color: "#E8E2D9",
                  fontWeight: 600,
                  fontSize: 13.5,
                  mt: 0.75,
                }}
              >
                {current.worksheetType === "DIAGNOSTIC"
                  ? "Diagnostic evidence selection"
                  : "Tutor-configured generation"}
              </Typography>
              <Typography
                sx={{
                  color: "#B5ADA2",
                  fontSize: 12.5,
                  lineHeight: 1.55,
                  mt: 0.75,
                }}
              >
                {current.generationRequestId
                  ? `Generated from request #${current.generationRequestId}. Question snapshots preserve the selected prompt, type, and marks.`
                  : "This legacy worksheet has no generation request. Its available question snapshots are shown above."}
              </Typography>
              <Typography sx={{ color: "#7A7268", fontSize: 11, mt: 1 }}>
                Generation is a suggestion; Tutor approval is the authoritative
                assignment decision.
              </Typography>
            </Box>
          </Stack>
        </Box>
      </Box>
    </Box>
  );
}
