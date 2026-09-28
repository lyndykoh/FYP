"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import {
  approveMarkingReview,
  flagMarkingReview,
  resetMarkingReview,
  type MistakeType,
  type MarkingReview as MarkingReviewData,
} from "@/services/submissions";

const card = {
  p: { xs: 2, sm: 2.5 },
  borderColor: "#E8DFD3",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
};
const label = {
  color: "#81786E",
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: ".08em",
};

type Props = {
  review: MarkingReviewData;
  approve?: typeof approveMarkingReview;
  flag?: typeof flagMarkingReview;
  reset?: typeof resetMarkingReview;
};

type DiagnosticDraft = {
  mistakeType: MistakeType;
  description: string;
  keywords: string;
};

function buildDiagnosticEvidence(
  includeDiagnostic: boolean,
  draft: DiagnosticDraft,
) {
  if (!includeDiagnostic) {
    return [];
  }

  return [
    {
      mistakeType: draft.mistakeType,
      description: draft.description.trim(),
      missingKeywords: draft.keywords
        .split(",")
        .map((keyword) => keyword.trim())
        .filter(Boolean),
    },
  ];
}

export default function MarkingReview({
  review: initialReview,
  approve = approveMarkingReview,
  flag = flagMarkingReview,
  reset = resetMarkingReview,
}: Props) {
  const [review, setReview] = React.useState(initialReview);
  const [marks, setMarks] = React.useState(
    String(initialReview.approvedMarks ?? initialReview.aiSuggestedMarks ?? ""),
  );
  const [feedback, setFeedback] = React.useState(
    initialReview.approvedFeedback ?? initialReview.aiSuggestedFeedback ?? "",
  );
  const [flagReason, setFlagReason] = React.useState("");
  const [includeDiagnostic, setIncludeDiagnostic] = React.useState(
    initialReview.diagnosticEvidence.length > 0,
  );
  const [mistakeType, setMistakeType] = React.useState<MistakeType>(
    initialReview.diagnosticEvidence[0]?.mistakeType ??
      "CONCEPT_MISUNDERSTANDING",
  );
  const [diagnosticDescription, setDiagnosticDescription] = React.useState(
    initialReview.diagnosticEvidence[0]?.description ?? "",
  );
  const [diagnosticKeywords, setDiagnosticKeywords] = React.useState(
    initialReview.diagnosticEvidence[0]?.missingKeywords.join(", ") ?? "",
  );
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const runReviewOperation = async (
    operation: () => Promise<MarkingReviewData>,
  ) => {
    setBusy(true);
    setError(null);
    try {
      setReview(await operation());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "The marking review could not be updated.",
      );
    } finally {
      setBusy(false);
    }
  };

  const parsedMarks = Number(marks);
  const validMarks =
    Number.isFinite(parsedMarks) &&
    parsedMarks >= 0 &&
    parsedMarks <= review.maxMarks;
  const statusLabel = review.reviewStatus.replace("_", " ");
  const isManualResult =
    review.aiSuggestedMarks === null &&
    review.aiSuggestedOutcome === null &&
    review.aiSuggestedFeedback === null;
  const diagnosticEvidence = buildDiagnosticEvidence(includeDiagnostic, {
    mistakeType,
    description: diagnosticDescription,
    keywords: diagnosticKeywords,
  });

  const approveFinalResult = () => {
    const hasDiagnosticRationale =
      !includeDiagnostic || diagnosticDescription.trim();

    if (validMarks && feedback.trim() && hasDiagnosticRationale) {
      void runReviewOperation(() =>
        approve(review.id, parsedMarks, feedback, diagnosticEvidence),
      );
      return;
    }

    setError(
      `Enter marks between 0 and ${review.maxMarks.toFixed(2)}, tutor feedback, and a diagnostic rationale when one is included.`,
    );
  };

  const flagForLater = () => {
    if (flagReason.trim()) {
      void runReviewOperation(() => flag(review.id, flagReason));
      return;
    }

    setError("Enter a reason before flagging this review.");
  };

  const restoreAiSuggestion = () => {
    setMarks(String(review.aiSuggestedMarks ?? ""));
    setFeedback(review.aiSuggestedFeedback ?? "");
  };

  return (
    <Box
      sx={{
        maxWidth: 1120,
        mx: "auto",
        py: { xs: 2, sm: 4 },
        px: { xs: 1.5, sm: 2.5 },
      }}
    >
      <Stack
        direction={{ xs: "column", sm: "row" }}
        sx={{ mb: 2.5, gap: 2, justifyContent: "space-between" }}
      >
        <Box>
          <Typography
            component="h1"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: { xs: 30, sm: 39 },
              color: "#2A2622",
            }}
          >
            Tutor marking review
          </Typography>
          <Typography sx={{ color: "#6F675E", mt: 0.5 }}>
            {isManualResult
              ? "This is a Tutor-entered result with a recorded approval history."
              : "AI suggestions are advisory. Student results are final only after your approval."}
          </Typography>
        </Box>
        <Chip
          label={statusLabel}
          sx={{
            alignSelf: "flex-start",
            fontWeight: 700,
            bgcolor:
              review.reviewStatus === "APPROVED"
                ? "#DDEFE5"
                : review.reviewStatus === "FLAGGED"
                  ? "#F7E4D5"
                  : "#EEE8DF",
            color: "#473E36",
          }}
        />
      </Stack>
      {error && (
        <Typography role="alert" sx={{ color: "#A43D25", mb: 2 }}>
          {error}
        </Typography>
      )}

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "1.05fr .95fr" },
          gap: 2,
        }}
      >
        <Stack sx={{ gap: 2 }}>
          <Card variant="outlined" sx={card}>
            <Typography sx={label}>STUDENT ANSWER</Typography>
            <Typography
              sx={{ whiteSpace: "pre-wrap", lineHeight: 1.65, mt: 0.8 }}
            >
              {review.extractedAnswer || "No readable answer was extracted."}
            </Typography>
          </Card>
          <Card variant="outlined" sx={card}>
            <Typography sx={label}>MODEL ANSWER</Typography>
            <Typography
              sx={{
                whiteSpace: "pre-wrap",
                lineHeight: 1.65,
                mt: 0.8,
                color: "#50483F",
              }}
            >
              {review.modelAnswer}
            </Typography>
          </Card>
        </Stack>
        <Stack sx={{ gap: 2 }}>
          <Card variant="outlined" sx={card}>
            <Typography sx={label}>
              {isManualResult ? "MANUAL RESULT" : "AI ADVISORY"}
            </Typography>
            <Typography sx={{ fontWeight: 700, mt: 0.8 }}>
              {isManualResult
                ? "Tutor-entered score"
                : review.aiSuggestedOutcome || "Manual review required"}{" "}
              ·{" "}
              {(isManualResult
                ? review.approvedMarks
                : review.aiSuggestedMarks
              )?.toFixed(2) ?? "—"}{" "}
              / {review.maxMarks.toFixed(2)}
            </Typography>
            <Typography
              sx={{
                color: "#5F574E",
                fontSize: 13.5,
                lineHeight: 1.55,
                mt: 0.8,
              }}
            >
              {isManualResult
                ? review.approvedFeedback
                : review.aiSuggestedFeedback || "No AI feedback is available."}
            </Typography>
            {review.missingKeywords.length > 0 && (
              <Stack
                direction="row"
                sx={{ mt: 1.25, gap: 0.75, flexWrap: "wrap" }}
              >
                {review.missingKeywords.map((keyword) => (
                  <Chip
                    key={keyword}
                    label={keyword}
                    size="small"
                    variant="outlined"
                  />
                ))}
              </Stack>
            )}
            {review.providerResponseValid === false && (
              <Typography sx={{ mt: 1, color: "#9A5D1E", fontSize: 12.5 }}>
                The provider response was unavailable or incomplete; inspect the
                deterministic evidence before approving.
              </Typography>
            )}
          </Card>
          <Card
            component="form"
            noValidate
            variant="outlined"
            sx={card}
            onSubmit={(event) => {
              event.preventDefault();
              approveFinalResult();
            }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 23,
                mb: 1.5,
              }}
            >
              Tutor decision
            </Typography>
            <TextField
              label="Final marks"
              value={marks}
              onChange={(event) => setMarks(event.target.value)}
              type="number"
              fullWidth
              slotProps={{
                htmlInput: { min: 0, max: review.maxMarks, step: 0.01 },
              }}
              helperText={`Maximum ${review.maxMarks.toFixed(2)} marks`}
              sx={{ mb: 1.5 }}
              disabled={busy}
            />
            <TextField
              label="Tutor feedback"
              value={feedback}
              onChange={(event) => setFeedback(event.target.value)}
              fullWidth
              multiline
              minRows={3}
              disabled={busy}
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={includeDiagnostic}
                  onChange={() => setIncludeDiagnostic((current) => !current)}
                  disabled={busy}
                />
              }
              label="Include Tutor-confirmed diagnostic evidence"
              sx={{ mt: 1 }}
            />
            {includeDiagnostic && (
              <Stack sx={{ gap: 1, mt: 0.5 }}>
                <TextField
                  select
                  label="Mistake type"
                  value={mistakeType}
                  onChange={(event) =>
                    setMistakeType(event.target.value as MistakeType)
                  }
                  disabled={busy}
                  helperText="The learning-profile grouping is derived automatically."
                >
                  <MenuItem value="CONCEPT_MISUNDERSTANDING">
                    Concept misunderstanding
                  </MenuItem>
                  <MenuItem value="MISSING_KEY_POINT">
                    Missing key point
                  </MenuItem>
                  <MenuItem value="WEAK_EXPLANATION">Weak explanation</MenuItem>
                  <MenuItem value="WRONG_UNITS">Wrong units</MenuItem>
                  <MenuItem value="ANSWER_FORMAT_ISSUE">
                    Answer format issue
                  </MenuItem>
                  <MenuItem value="CALCULATION_ERROR">
                    Calculation error
                  </MenuItem>
                  <MenuItem value="MISREAD_QUESTION">Misread question</MenuItem>
                  <MenuItem value="INCOMPLETE_WORKING">
                    Incomplete working
                  </MenuItem>
                  <MenuItem value="INCORRECT_FORMULA">
                    Incorrect formula
                  </MenuItem>
                  <MenuItem value="CARELESS_MISTAKE">Careless mistake</MenuItem>
                </TextField>
                <TextField
                  label="Tutor diagnostic rationale"
                  value={diagnosticDescription}
                  onChange={(event) =>
                    setDiagnosticDescription(event.target.value)
                  }
                  fullWidth
                  multiline
                  minRows={2}
                  required
                  disabled={busy}
                  helperText="This evidence is shared with the learning profile only after approval."
                />
                <TextField
                  label="Supporting missing words or phrases"
                  value={diagnosticKeywords}
                  onChange={(event) =>
                    setDiagnosticKeywords(event.target.value)
                  }
                  fullWidth
                  disabled={busy}
                  helperText="Optional; separate entries with commas."
                />
              </Stack>
            )}
            <Stack direction="row" sx={{ mt: 1.5, gap: 1, flexWrap: "wrap" }}>
              <Button
                type="submit"
                disabled={busy}
                sx={{
                  bgcolor: "#9E3A24",
                  color: "#FFFDFA",
                  textTransform: "none",
                  "&:hover": { bgcolor: "#7F2D1C" },
                }}
              >
                Approve final result
              </Button>
              <Button
                type="button"
                onClick={restoreAiSuggestion}
                disabled={busy}
                sx={{ textTransform: "none", color: "#574E45" }}
              >
                Use AI suggestion
              </Button>
            </Stack>
          </Card>
          <Card variant="outlined" sx={card}>
            <Typography sx={label}>REVIEW CONTROLS</Typography>
            <Stack
              direction={{ xs: "column", sm: "row" }}
              sx={{ mt: 1, gap: 1 }}
            >
              <TextField
                label="Flag reason"
                value={flagReason}
                onChange={(event) => setFlagReason(event.target.value)}
                size="small"
                fullWidth
                disabled={busy}
              />
              <Button
                onClick={flagForLater}
                disabled={busy}
                sx={{ textTransform: "none", whiteSpace: "nowrap" }}
              >
                Flag for later
              </Button>
              <Button
                onClick={() => void runReviewOperation(() => reset(review.id))}
                disabled={busy || review.reviewStatus === "PENDING_REVIEW"}
                sx={{ textTransform: "none", whiteSpace: "nowrap" }}
              >
                Reset to AI
              </Button>
            </Stack>
          </Card>
        </Stack>
      </Box>
      {review.history.length > 0 && (
        <Card variant="outlined" sx={{ ...card, mt: 2 }}>
          <Typography sx={label}>AUDIT HISTORY</Typography>
          <Stack sx={{ mt: 1, gap: 0.75 }}>
            {review.history.map((event) => (
              <Typography key={event.id} sx={{ fontSize: 13 }}>
                {event.action.replaceAll("_", " ")} ·{" "}
                {event.previousStatus.replace("_", " ")} →{" "}
                {event.newStatus.replace("_", " ")}
              </Typography>
            ))}
          </Stack>
        </Card>
      )}
    </Box>
  );
}
