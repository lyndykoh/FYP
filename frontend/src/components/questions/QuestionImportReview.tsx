"use client";

import * as React from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import SyllabusPicker from "@/components/syllabus/SyllabusPicker";
import {
  fetchQuestionImportBatch,
  fetchQuestionImportDiagramCropUrl,
  fetchQuestionImportSourcePageUrl,
  importQuestionImportCandidates,
  mergeQuestionImportCandidates,
  moveQuestionImportDiagramCrops,
  rejectQuestionImportCandidate,
  restoreQuestionImportCandidate,
  splitQuestionImportCandidate,
  updateQuestionImportCandidate,
  uploadQuestionImport,
  type QuestionDifficulty,
  type QuestionImportBatch,
  type QuestionImportCandidate,
  type QuestionImportDiagramCrop,
  type QuestionType,
} from "@/services/questions";

const questionTypes: QuestionType[] = [
  "MULTIPLE_CHOICE",
  "TRUE_FALSE",
  "FILL_IN_THE_BLANK",
  "SHORT_ANSWER",
  "OPEN_ENDED",
  "CALCULATION",
  "DIAGRAM",
];
const difficulties: QuestionDifficulty[] = [
  "FOUNDATION",
  "APPLICATION",
  "CHALLENGE",
];
const editable = (candidate: QuestionImportCandidate) =>
  candidate.status === "READY_FOR_REVIEW" || candidate.status === "UNCERTAIN";
const cardSx = {
  p: { xs: 2, sm: 2.5 },
  borderRadius: 3,
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
};

function ImagePreview({
  batchId,
  candidate,
  crop,
}: {
  batchId: number;
  candidate: QuestionImportCandidate;
  crop?: QuestionImportDiagramCrop;
}) {
  const [url, setUrl] = React.useState<string | null>(null);
  React.useEffect(() => {
    let live = true;
    let objectUrl: string | null = null;
    const load = crop
      ? fetchQuestionImportDiagramCropUrl(batchId, crop.id)
      : fetchQuestionImportSourcePageUrl(batchId, candidate.source.pageId);
    void load
      .then((nextUrl) => {
        objectUrl = nextUrl;
        if (live) setUrl(nextUrl);
        else URL.revokeObjectURL(nextUrl);
      })
      .catch(() => {
        if (live) setUrl(null);
      });
    return () => {
      live = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [batchId, candidate.source.pageId, crop]);
  const label = crop
    ? `Diagram ${crop.regionId}`
    : `Original page: ${candidate.source.filename}, page ${candidate.source.pageNumber}`;
  return (
    <Box component="figure" sx={{ m: 0 }}>
      <Typography
        component="figcaption"
        sx={{ color: "#8B837A", fontSize: 11.5, mb: 0.5 }}
      >
        {label}
      </Typography>
      {url ? (
        <Box
          component="img"
          src={url}
          alt={label}
          sx={{
            display: "block",
            maxWidth: "100%",
            maxHeight: crop ? 160 : 260,
            border: "1px solid #E4DCD0",
            borderRadius: 2,
          }}
        />
      ) : (
        <Typography sx={{ color: "#8B837A", fontSize: 12 }}>
          Preview unavailable.
        </Typography>
      )}
    </Box>
  );
}

function Suggestions({ candidate }: { candidate: QuestionImportCandidate }) {
  const suggestion = candidate.suggestions;
  if (!suggestion)
    return (
      <Typography sx={{ color: "#8B837A", fontSize: 11.5 }}>
        No separate extractor suggestion was retained for this older draft.
      </Typography>
    );
  return (
    <Box
      component="section"
      aria-label={`Extractor suggestions for draft ${candidate.number}`}
      sx={{ bgcolor: "#F8F4EE", borderRadius: 2, p: 1.25 }}
    >
      <Typography sx={{ fontSize: 12, fontWeight: 700 }}>
        Extractor suggestions (read-only)
      </Typography>
      <Typography
        sx={{ color: "#6F675E", fontSize: 12, whiteSpace: "pre-wrap", mt: 0.5 }}
      >
        {suggestion.prompt || "No question text suggested."}
      </Typography>
      <Typography
        sx={{ color: "#6F675E", fontSize: 12, whiteSpace: "pre-wrap", mt: 0.5 }}
      >
        Answer: {suggestion.modelAnswer || "Not suggested"}
      </Typography>
      <Typography sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.5 }}>
        Type: {suggestion.questionType.replaceAll("_", " ")} · Difficulty:{" "}
        {suggestion.difficulty} · Marks: {suggestion.totalMarks} · Tags:{" "}
        {suggestion.tags || "none"}
      </Typography>
    </Box>
  );
}

function Confidence({ candidate }: { candidate: QuestionImportCandidate }) {
  const fields = candidate.confidenceByField;
  if (!fields)
    return (
      <Typography sx={{ color: "#8B837A", fontSize: 11.5 }}>
        Field confidence was not provided by the extractor.
      </Typography>
    );
  return (
    <Box
      aria-label={`Field confidence for draft ${candidate.number}`}
      sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mt: 0.75 }}
    >
      {[
        ["Question text", fields.prompt],
        ["Answer", fields.modelAnswer],
        ["Classification", fields.classification],
        ["Marks", fields.marks],
      ].map(([label, value]) => {
        const confidence = value as number | null;
        return (
          <Chip
            key={label as string}
            size="small"
            label={`${label}: ${confidence === null ? "not assessed" : `${confidence}%`}`}
            sx={{
              height: 22,
              fontSize: 10,
              bgcolor:
                confidence !== null && confidence < 70 ? "#FDF1DF" : "#F0EAE0",
              color:
                confidence !== null && confidence < 70 ? "#8C5A14" : "#6F675E",
            }}
          />
        );
      })}
    </Box>
  );
}

function SplitEditor({
  candidate,
  onSubmit,
  onCancel,
}: {
  candidate: QuestionImportCandidate;
  onSubmit: (drafts: Array<{ prompt: string; modelAnswer: string }>) => void;
  onCancel: () => void;
}) {
  const [drafts, setDrafts] = React.useState([
    { prompt: candidate.prompt, modelAnswer: candidate.modelAnswer },
    { prompt: "", modelAnswer: "" },
  ]);
  const update = (
    index: number,
    key: "prompt" | "modelAnswer",
    value: string,
  ) =>
    setDrafts((current) =>
      current.map((draft, draftIndex) =>
        draftIndex === index ? { ...draft, [key]: value } : draft,
      ),
    );
  return (
    <Box
      sx={{ display: "grid", gap: 1, borderTop: "1px solid #E4DCD0", pt: 1.25 }}
    >
      <Typography sx={{ fontSize: 12, fontWeight: 700 }}>
        Split into reviewed drafts
      </Typography>
      {drafts.map((draft, index) => (
        <Box key={index} sx={{ display: "grid", gap: 0.75 }}>
          <TextField
            label={`Split question ${index + 1}`}
            value={draft.prompt}
            multiline
            minRows={2}
            onChange={(event) => update(index, "prompt", event.target.value)}
          />
          <TextField
            label={`Split answer ${index + 1}`}
            value={draft.modelAnswer}
            multiline
            minRows={2}
            onChange={(event) =>
              update(index, "modelAnswer", event.target.value)
            }
          />
        </Box>
      ))}
      <Box sx={{ display: "flex", gap: 1 }}>
        <Button
          size="small"
          onClick={() =>
            setDrafts((current) => [
              ...current,
              { prompt: "", modelAnswer: "" },
            ])
          }
          disabled={drafts.length >= 10}
          sx={{ textTransform: "none" }}
        >
          Add split
        </Button>
        <Button
          size="small"
          onClick={() => onSubmit(drafts)}
          sx={{ textTransform: "none" }}
        >
          Save split
        </Button>
        <Button size="small" onClick={onCancel} sx={{ textTransform: "none" }}>
          Cancel
        </Button>
      </Box>
    </Box>
  );
}

interface CardProps {
  batchId: number;
  candidate: QuestionImportCandidate;
  candidates: QuestionImportCandidate[];
  selected: boolean;
  isWorking: boolean;
  onSelect: (checked: boolean) => void;
  onChange: (candidate: QuestionImportCandidate) => void;
  onSave: (candidate: QuestionImportCandidate) => void;
  onReject: (candidateId: number) => void;
  onRestore: (candidateId: number) => void;
  onMoveCrop: (cropId: number, targetId: number) => void;
  onSplit: (
    candidateId: number,
    drafts: Array<{ prompt: string; modelAnswer: string }>,
  ) => void;
}

function CandidateCard(props: CardProps) {
  const {
    batchId,
    candidate,
    candidates,
    selected,
    isWorking,
    onSelect,
    onChange,
    onSave,
    onReject,
    onRestore,
    onMoveCrop,
    onSplit,
  } = props;
  const [splitting, setSplitting] = React.useState(false);
  const [cropTargets, setCropTargets] = React.useState<Record<number, string>>(
    {},
  );
  const canEdit = editable(candidate);
  const change = <Key extends keyof QuestionImportCandidate>(
    key: Key,
    value: QuestionImportCandidate[Key],
  ) => onChange({ ...candidate, [key]: value });
  const targets = candidates.filter(
    (item) =>
      item.id !== candidate.id &&
      editable(item) &&
      item.source.pageId === candidate.source.pageId,
  );
  return (
    <Card
      component="article"
      variant="outlined"
      sx={{ ...cardSx, opacity: candidate.status === "SUPERSEDED" ? 0.7 : 1 }}
    >
      <Box
        sx={{ display: "flex", alignItems: "flex-start", gap: 0.75, mb: 1.25 }}
      >
        <Checkbox
          checked={selected}
          disabled={!canEdit || isWorking}
          onChange={(event) => onSelect(event.target.checked)}
          slotProps={{
            input: { "aria-label": `Select import draft ${candidate.number}` },
          }}
          sx={{
            mt: -0.9,
            ml: -0.8,
            color: "#A09488",
            "&.Mui-checked": { color: "#9E3A24" },
          }}
        />
        <Box sx={{ flex: 1 }}>
          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              gap: 0.75,
              alignItems: "center",
            }}
          >
            <Typography component="h2" sx={{ fontSize: 16, fontWeight: 600 }}>
              Draft {candidate.number}
            </Typography>
            <Chip
              label={candidate.status.replaceAll("_", " ")}
              size="small"
              sx={{
                height: 23,
                fontSize: 10,
                fontWeight: 700,
                bgcolor: canEdit ? "#E9EEE8" : "#F0EAE0",
              }}
            />
            <Chip
              label={`${candidate.confidence}% overall confidence`}
              size="small"
              sx={{
                height: 23,
                bgcolor: candidate.confidence < 70 ? "#FDF1DF" : "#F0EAE0",
                color: candidate.confidence < 70 ? "#8C5A14" : "#6F675E",
                fontSize: 10,
              }}
            />
          </Box>
          <Confidence candidate={candidate} />
          {candidate.warningMessage ? (
            <Alert
              severity="warning"
              sx={{
                mt: 0.85,
                py: 0,
                "& .MuiAlert-message": { py: 0.35, fontSize: 12.5 },
              }}
            >
              {candidate.warningMessage}
            </Alert>
          ) : null}
          {candidate.duplicateWarnings?.map((warning) => (
            <Alert
              key={`${warning.target.kind}-${warning.target.candidateId ?? warning.target.questionId}`}
              severity="warning"
              sx={{
                mt: 0.85,
                py: 0,
                "& .MuiAlert-message": { py: 0.35, fontSize: 12.5 },
              }}
            >
              Possible duplicate with {warning.target.label} — advisory only.{" "}
              {warning.signals.map((signal) => signal.detail).join(" ")} You can
              still edit and explicitly import this draft.
            </Alert>
          ))}
          {candidate.lineage?.rejectionReason ? (
            <Typography sx={{ color: "#8B837A", fontSize: 12, mt: 0.5 }}>
              Rejection note: {candidate.lineage.rejectionReason}
            </Typography>
          ) : null}
        </Box>
      </Box>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            md: "minmax(0, 1.4fr) minmax(240px, .8fr)",
          },
          gap: 2,
        }}
      >
        <Box sx={{ display: "grid", gap: 1.25 }}>
          <Suggestions candidate={candidate} />
          <TextField
            label="Question code (optional)"
            value={candidate.code ?? ""}
            onChange={(event) => change("code", event.target.value)}
            disabled={!canEdit || isWorking}
            slotProps={{ htmlInput: { maxLength: 120 } }}
          />
          <Box
            sx={{
              pointerEvents: canEdit && !isWorking ? "auto" : "none",
              opacity: canEdit && !isWorking ? 1 : 0.65,
            }}
          >
            <SyllabusPicker
              value={candidate.syllabusTopicId}
              onChange={(topicId) => change("syllabusTopicId", topicId)}
              label="Syllabus topic"
              helperText="Required before import."
            />
          </Box>
          <TextField
            label="Reviewed question text"
            value={candidate.prompt}
            onChange={(event) => change("prompt", event.target.value)}
            disabled={!canEdit || isWorking}
            multiline
            minRows={4}
            slotProps={{ htmlInput: { maxLength: 4000 } }}
          />
          <TextField
            label="Reviewed model answer / solution"
            value={candidate.modelAnswer}
            onChange={(event) => change("modelAnswer", event.target.value)}
            disabled={!canEdit || isWorking}
            multiline
            minRows={3}
            slotProps={{ htmlInput: { maxLength: 4000 } }}
          />
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr 120px" },
              gap: 1,
            }}
          >
            <TextField
              select
              label="Reviewed question type"
              value={candidate.questionType}
              onChange={(event) =>
                change("questionType", event.target.value as QuestionType)
              }
              disabled={!canEdit || isWorking}
            >
              {questionTypes.map((type) => (
                <MenuItem key={type} value={type}>
                  {type.replaceAll("_", " ")}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Reviewed difficulty"
              value={candidate.difficulty}
              onChange={(event) =>
                change("difficulty", event.target.value as QuestionDifficulty)
              }
              disabled={!canEdit || isWorking}
            >
              {difficulties.map((difficulty) => (
                <MenuItem key={difficulty} value={difficulty}>
                  {difficulty}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Marks"
              type="number"
              value={candidate.totalMarks}
              onChange={(event) =>
                change("totalMarks", Number(event.target.value))
              }
              disabled={!canEdit || isWorking}
              slotProps={{ htmlInput: { min: 0.01, step: 0.5 } }}
            />
          </Box>
          <FormControlLabel
            control={
              <Checkbox
                checked={candidate.includeSourceImage}
                onChange={(event) =>
                  change("includeSourceImage", event.target.checked)
                }
                disabled={!canEdit || isWorking}
              />
            }
            label="Attach current diagram crops as question evidence"
          />
          {canEdit ? (
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              <Button
                size="small"
                disabled={isWorking}
                onClick={() => onSave(candidate)}
                sx={{ textTransform: "none" }}
              >
                Save review
              </Button>
              <Button
                size="small"
                disabled={isWorking}
                onClick={() => setSplitting(true)}
                sx={{ textTransform: "none" }}
              >
                Split draft
              </Button>
              <Button
                size="small"
                color="warning"
                disabled={isWorking}
                onClick={() => onReject(candidate.id)}
                sx={{ textTransform: "none" }}
              >
                Reject draft
              </Button>
            </Box>
          ) : null}
          {candidate.status === "REJECTED" ? (
            <Button
              size="small"
              disabled={isWorking}
              onClick={() => onRestore(candidate.id)}
              sx={{ justifySelf: "start", textTransform: "none" }}
            >
              Restore to review
            </Button>
          ) : null}
          {splitting ? (
            <SplitEditor
              candidate={candidate}
              onCancel={() => setSplitting(false)}
              onSubmit={(drafts) => onSplit(candidate.id, drafts)}
            />
          ) : null}
        </Box>
        <Box sx={{ display: "grid", alignContent: "start", gap: 1.25 }}>
          <ImagePreview batchId={batchId} candidate={candidate} />
          <Box
            component="section"
            aria-label={`Diagram crops for draft ${candidate.number}`}
            sx={{ display: "grid", gap: 1 }}
          >
            {(candidate.diagramCrops ?? []).length ? (
              candidate.diagramCrops?.map((crop) => (
                <Box key={crop.id}>
                  <ImagePreview
                    batchId={batchId}
                    candidate={candidate}
                    crop={crop}
                  />
                  {canEdit && targets.length ? (
                    <Box sx={{ display: "flex", gap: 0.75, mt: 0.75 }}>
                      <TextField
                        select
                        size="small"
                        label="Move to"
                        value={cropTargets[crop.id] ?? ""}
                        onChange={(event) =>
                          setCropTargets((current) => ({
                            ...current,
                            [crop.id]: event.target.value,
                          }))
                        }
                        sx={{ minWidth: 145 }}
                      >
                        {targets.map((target) => (
                          <MenuItem key={target.id} value={target.id}>
                            Draft {target.number}
                          </MenuItem>
                        ))}
                      </TextField>
                      <Button
                        size="small"
                        disabled={!cropTargets[crop.id] || isWorking}
                        onClick={() =>
                          onMoveCrop(crop.id, Number(cropTargets[crop.id]))
                        }
                        sx={{ textTransform: "none" }}
                      >
                        Move
                      </Button>
                    </Box>
                  ) : null}
                </Box>
              ))
            ) : (
              <Typography sx={{ color: "#8B837A", fontSize: 12 }}>
                No diagram regions were detected for this draft.
              </Typography>
            )}
          </Box>
        </Box>
      </Box>
    </Card>
  );
}

export default function QuestionImportReview() {
  const [batch, setBatch] = React.useState<QuestionImportBatch | null>(null);
  const [files, setFiles] = React.useState<File[]>([]);
  const [selectedIds, setSelectedIds] = React.useState<Set<number>>(new Set());
  const [isWorking, setIsWorking] = React.useState(false);
  const [message, setMessage] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const replaceCandidate = (candidate: QuestionImportCandidate) =>
    setBatch((current) =>
      current
        ? {
            ...current,
            candidates: current.candidates.map((item) =>
              item.id === candidate.id ? candidate : item,
            ),
          }
        : current,
    );
  const refresh = async (id: number) =>
    setBatch(await fetchQuestionImportBatch(id));
  const run = async (work: () => Promise<void>) => {
    setError(null);
    setMessage(null);
    setIsWorking(true);
    try {
      await work();
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "The import review could not be updated.",
      );
    } finally {
      setIsWorking(false);
    }
  };
  React.useEffect(() => {
    if (
      !batch ||
      ["READY_FOR_REVIEW", "FAILED", "IMPORTED"].includes(batch.status)
    )
      return;
    const timer = window.setTimeout(() => {
      void run(() => refresh(batch.id));
    }, 2500);
    return () => window.clearTimeout(timer);
  }, [batch]);
  const save = async (candidate: QuestionImportCandidate) => {
    if (!batch) return;
    const saved = await updateQuestionImportCandidate(batch.id, candidate.id, {
      code: candidate.code,
      syllabusTopicId: candidate.syllabusTopicId,
      prompt: candidate.prompt,
      modelAnswer: candidate.modelAnswer,
      totalMarks: candidate.totalMarks,
      questionType: candidate.questionType,
      difficulty: candidate.difficulty,
      includeSourceImage: candidate.includeSourceImage,
    });
    replaceCandidate(saved);
    setMessage(`Saved draft ${candidate.number}.`);
  };
  const importSelected = () =>
    run(async () => {
      if (!batch) return;
      const selected = batch.candidates.filter(
        (candidate) => selectedIds.has(candidate.id) && editable(candidate),
      );
      if (!selected.length)
        throw new Error(
          "Select at least one ready or uncertain draft to import.",
        );
      for (const candidate of selected) await save(candidate);
      const result = await importQuestionImportCandidates(
        batch.id,
        selected.map((candidate) => candidate.id),
      );
      await refresh(batch.id);
      setSelectedIds(new Set());
      setMessage(
        `${result.questionIds.length} question${result.questionIds.length === 1 ? "" : "s"} imported. ${result.message}`,
      );
    });
  const mergeSelected = () =>
    run(async () => {
      if (!batch) return;
      const selected = batch.candidates.filter(
        (candidate) => selectedIds.has(candidate.id) && editable(candidate),
      );
      if (selected.length < 2)
        throw new Error(
          "Select at least two ready or uncertain drafts to merge.",
        );
      const updated = await mergeQuestionImportCandidates(
        batch.id,
        selected[0].id,
        selected.map((candidate) => candidate.id),
      );
      setBatch(updated);
      setSelectedIds(new Set());
      setMessage(`Merged into draft ${selected[0].number}.`);
    });
  return (
    <Box component="section" aria-labelledby="question-import-title">
      <Typography
        id="question-import-title"
        component="h1"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: { xs: 30, sm: 38 },
          fontWeight: 500,
          mb: 1,
        }}
      >
        Import questions
      </Typography>
      <Typography
        sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mb: 2.5 }}
      >
        Upload PDFs or page images. Extractor output is advisory: compare it
        with the original page, correct the reviewed fields, then explicitly
        choose which drafts to import.
      </Typography>
      {error ? (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      ) : null}
      {message ? (
        <Alert severity="success" sx={{ mb: 2 }}>
          {message}
        </Alert>
      ) : null}
      {!batch ? (
        <Card variant="outlined" sx={{ ...cardSx, maxWidth: 720 }}>
          <Button
            component="label"
            variant="outlined"
            disabled={isWorking}
            sx={{
              textTransform: "none",
              borderColor: "#DCCFBE",
              color: "#2A2622",
            }}
          >
            Choose PDF or page images
            <input
              hidden
              type="file"
              accept="application/pdf,image/png,image/jpeg"
              multiple
              onChange={(event) =>
                setFiles(Array.from(event.target.files ?? []))
              }
            />
          </Button>
          {files.length ? (
            <Typography sx={{ color: "#5A544C", fontSize: 13, mt: 1.25 }}>
              {files.length} file{files.length === 1 ? "" : "s"}:{" "}
              {files.map((file) => file.name).join(", ")}
            </Typography>
          ) : (
            <Typography sx={{ color: "#8B837A", fontSize: 13, mt: 1.25 }}>
              Up to 20 PDF, PNG, or JPEG files; 25 MB each.
            </Typography>
          )}
          <Box sx={{ mt: 2 }}>
            <Button
              onClick={() =>
                void run(async () =>
                  setBatch(await uploadQuestionImport(files)),
                )
              }
              disabled={!files.length || isWorking}
              sx={{
                minHeight: 40,
                bgcolor: "#9E3A24",
                color: "white",
                textTransform: "none",
              }}
            >
              {isWorking ? "Processing pages…" : "Create review drafts"}
            </Button>
          </Box>
        </Card>
      ) : (
        <>
          {batch.status !== "READY_FOR_REVIEW" ? (
            <Alert
              severity={batch.status === "FAILED" ? "error" : "info"}
              sx={{ mb: 2 }}
            >
              Batch status: {batch.status.replaceAll("_", " ")}.{" "}
              {batch.status === "QUEUED" || batch.status === "RUNNING"
                ? "Refreshing while extraction completes…"
                : "Review mutations are unavailable."}
            </Alert>
          ) : null}
          <Typography
            aria-live="polite"
            sx={{ color: "#6F675E", fontSize: 13, mb: 1.5 }}
          >
            {batch.candidates.length} detected draft
            {batch.candidates.length === 1 ? "" : "s"}; {selectedIds.size}{" "}
            selected.
          </Typography>
          <Box sx={{ display: "grid", gap: 1.5 }}>
            {batch.candidates.map((candidate) => (
              <CandidateCard
                key={candidate.id}
                batchId={batch.id}
                candidate={candidate}
                candidates={batch.candidates}
                selected={selectedIds.has(candidate.id)}
                isWorking={isWorking}
                onSelect={(checked) =>
                  setSelectedIds((current) => {
                    const next = new Set(current);
                    if (checked) next.add(candidate.id);
                    else next.delete(candidate.id);
                    return next;
                  })
                }
                onChange={replaceCandidate}
                onSave={(candidate) => void run(() => save(candidate))}
                onReject={(id) =>
                  void run(async () => {
                    setBatch(await rejectQuestionImportCandidate(batch.id, id));
                    setSelectedIds((current) => {
                      const next = new Set(current);
                      next.delete(id);
                      return next;
                    });
                  })
                }
                onRestore={(id) =>
                  void run(async () =>
                    setBatch(
                      await restoreQuestionImportCandidate(batch.id, id),
                    ),
                  )
                }
                onMoveCrop={(cropId, targetId) =>
                  void run(async () =>
                    setBatch(
                      await moveQuestionImportDiagramCrops(
                        batch.id,
                        [cropId],
                        targetId,
                      ),
                    ),
                  )
                }
                onSplit={(id, drafts) =>
                  void run(async () => {
                    setBatch(
                      await splitQuestionImportCandidate(batch.id, id, drafts),
                    );
                    setSelectedIds((current) => {
                      const next = new Set(current);
                      next.delete(id);
                      return next;
                    });
                  })
                }
              />
            ))}
          </Box>
          <Box
            sx={{
              position: "sticky",
              bottom: 12,
              display: "flex",
              justifyContent: "flex-end",
              flexWrap: "wrap",
              gap: 1,
              mt: 2,
            }}
          >
            <Button
              onClick={() => void mergeSelected()}
              disabled={
                selectedIds.size < 2 ||
                isWorking ||
                batch.status !== "READY_FOR_REVIEW"
              }
              sx={{ minHeight: 44, textTransform: "none" }}
            >
              Merge selected (first selected is destination)
            </Button>
            <Button
              onClick={() => void importSelected()}
              disabled={
                !selectedIds.size ||
                isWorking ||
                batch.status !== "READY_FOR_REVIEW"
              }
              sx={{
                minHeight: 44,
                px: 2,
                bgcolor: "#9E3A24",
                color: "white",
                textTransform: "none",
                "&.Mui-disabled": { bgcolor: "#E4DCD0" },
              }}
            >
              {isWorking
                ? "Saving review…"
                : `Import ${selectedIds.size} selected draft${selectedIds.size === 1 ? "" : "s"}`}
            </Button>
          </Box>
        </>
      )}
    </Box>
  );
}
