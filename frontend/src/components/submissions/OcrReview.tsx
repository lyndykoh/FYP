"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";

import type { OcrAnswerMapping, OcrPage } from "@/services/submissions";

export type OcrQuestionOption = { id: number; prompt: string };

const EMPTY_QUESTIONS: OcrQuestionOption[] = [];

type OcrReviewProps = {
  pages: OcrPage[];
  questions?: OcrQuestionOption[];
  onCorrect: (id: number, text: string) => Promise<void>;
  onSubmitForReview?: (answers: OcrAnswerMapping[]) => Promise<void>;
};

function initialTextByExtractionId(pages: OcrPage[]) {
  return Object.fromEntries(
    pages.map((page) => [page.extractionId, page.text]),
  ) as Record<number, string>;
}

function initialQuestionByExtractionId(
  pages: OcrPage[],
  questions: OcrQuestionOption[],
) {
  const defaultQuestionId = questions.length === 1 ? questions[0]!.id : "";

  return Object.fromEntries(
    pages.map((page) => [page.extractionId, defaultQuestionId]),
  ) as Record<number, number | "">;
}

function isReadyForTutorReview(
  pages: OcrPage[],
  questionIds: Record<number, number | "">,
) {
  return (
    pages.length > 0 &&
    pages.every(
      (page) => page.status === "READY" && questionIds[page.extractionId],
    )
  );
}

export default function OcrReview({
  pages,
  questions = EMPTY_QUESTIONS,
  onCorrect,
  onSubmitForReview,
}: OcrReviewProps) {
  const [textByExtractionId, setTextByExtractionId] = React.useState(() =>
    initialTextByExtractionId(pages),
  );
  const [questionByExtractionId, setQuestionByExtractionId] = React.useState(
    () => initialQuestionByExtractionId(pages, questions),
  );
  const [savingExtractionId, setSavingExtractionId] = React.useState<
    number | null
  >(null);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [submitError, setSubmitError] = React.useState<string | null>(null);

  const canSubmit =
    Boolean(onSubmitForReview) &&
    isReadyForTutorReview(pages, questionByExtractionId);

  const updateExtractedText = (extractionId: number, text: string) => {
    setTextByExtractionId((currentText) => ({
      ...currentText,
      [extractionId]: text,
    }));
  };

  const selectQuestion = (extractionId: number, questionId: string) => {
    setQuestionByExtractionId((currentQuestionIds) => ({
      ...currentQuestionIds,
      [extractionId]: Number(questionId) || "",
    }));
  };

  const saveCorrection = async (page: OcrPage) => {
    setSavingExtractionId(page.extractionId);

    try {
      await onCorrect(
        page.extractionId,
        textByExtractionId[page.extractionId] || "",
      );
    } finally {
      setSavingExtractionId(null);
    }
  };

  const submitForTutorReview = async () => {
    if (!onSubmitForReview || !canSubmit) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const answerMappings = pages.map((page) => ({
        extractionId: page.extractionId,
        questionBankId: Number(questionByExtractionId[page.extractionId]),
      }));

      await onSubmitForReview(answerMappings);
    } catch (error) {
      setSubmitError(
        error instanceof Error
          ? error.message
          : "The OCR submission could not be sent for Tutor review.",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Box>
      <Typography component="h2">Review extracted text</Typography>
      {pages.map((page, index) => {
        const pageNumber = index + 1;
        const extractionNeedsReview = page.status !== "READY";

        return (
          <Card
            key={page.extractionId}
            variant="outlined"
            sx={{
              p: 2,
              my: 1.5,
              borderColor: extractionNeedsReview ? "#D79B63" : "#EBE4D9",
            }}
          >
            <Typography sx={{ fontWeight: 700 }}>
              Page {pageNumber} · {Math.round(page.confidence * 100)}%
              confidence
            </Typography>
            <TextField
              label={`Page ${pageNumber} text`}
              multiline
              minRows={3}
              fullWidth
              value={textByExtractionId[page.extractionId] || ""}
              onChange={(event) =>
                updateExtractedText(page.extractionId, event.target.value)
              }
              sx={{ my: 1 }}
            />
            {extractionNeedsReview && (
              <Typography role="status" sx={{ color: "#7A6238", mb: 1 }}>
                Please check and save this low-confidence extraction.
              </Typography>
            )}
            <Button
              disabled={savingExtractionId === page.extractionId}
              onClick={() => void saveCorrection(page)}
            >
              Save correction
            </Button>
            {onSubmitForReview && (
              <TextField
                select
                fullWidth
                label={`Page ${pageNumber} answer belongs to`}
                value={questionByExtractionId[page.extractionId] ?? ""}
                onChange={(event) =>
                  selectQuestion(page.extractionId, event.target.value)
                }
                sx={{ mt: 2 }}
              >
                <MenuItem value="">Choose worksheet question</MenuItem>
                {questions.map((question, questionIndex) => (
                  <MenuItem key={question.id} value={question.id}>
                    Question {questionIndex + 1}: {question.prompt}
                  </MenuItem>
                ))}
              </TextField>
            )}
          </Card>
        );
      })}
      {onSubmitForReview && (
        <Box sx={{ mt: 3 }}>
          <Typography sx={{ color: "#6F675E", mb: 1 }}>
            Confirming creates the worksheet answer records and sends them to
            your Tutor for review. AI suggestions remain private until Tutor
            approval.
          </Typography>
          {submitError && (
            <Typography role="alert" sx={{ color: "#9E3A24", mb: 1 }}>
              {submitError}
            </Typography>
          )}
          <Button
            disabled={!canSubmit || isSubmitting}
            onClick={() => void submitForTutorReview()}
            sx={{
              bgcolor: "#9E3A24",
              color: "#FFFDFA",
              "&:hover": { bgcolor: "#8A3120" },
            }}
          >
            {isSubmitting ? "Submitting…" : "Submit for Tutor Review"}
          </Button>
          {!canSubmit && (
            <Typography sx={{ color: "#7A6238", fontSize: 13, mt: 1 }}>
              Correct every flagged page and choose its worksheet question
              before submitting.
            </Typography>
          )}
        </Box>
      )}
    </Box>
  );
}
