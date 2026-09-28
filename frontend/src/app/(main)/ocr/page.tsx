"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import OcrReview from "@/components/submissions/OcrReview";
import Stack from "@/components/lumina-stack";
import { getBrowserSession } from "@/lib/auth";
import {
  correctOcrExtraction,
  fetchSubmissionDocument,
  SubmissionApiError,
  submitOcrForTutorReview,
  type SubmissionDocument,
} from "@/services/submissions";
import {
  fetchStudentWorksheet,
  fetchTutorWorksheet,
  type WorksheetQuestion,
} from "@/services/worksheets";

function submissionId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function OcrPage() {
  const params = useSearchParams();
  const id = submissionId(params.get("submissionId"));
  const isStudent = getBrowserSession()?.role === "STUDENT";
  const router = useRouter();
  const returnPath = isStudent ? "/worksheets" : "/upload";
  const completionPath = isStudent ? "/worksheets" : "/tutor/worksheets";
  const [document, setDocument] = React.useState<SubmissionDocument | null>(
    null,
  );
  const [questions, setQuestions] = React.useState<WorksheetQuestion[]>([]);
  const [error, setError] = React.useState<string | null>(
    id === null ? "Choose a saved submission before reviewing OCR." : null,
  );
  const [loading, setLoading] = React.useState(id !== null);
  const [reload, setReload] = React.useState(0);

  React.useEffect(() => {
    if (id === null) return;
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoading(true);
        setError(null);
        setDocument(null);
        setQuestions([]);
      }
    });
    void fetchSubmissionDocument(id)
      .then(async (loaded) => {
        const detail = isStudent
          ? await fetchStudentWorksheet(loaded.worksheetId)
          : await fetchTutorWorksheet(loaded.worksheetId);
        if (active) setQuestions(detail.questions);
        if (active) setDocument(loaded);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        if (reason instanceof SubmissionApiError && reason.status === 404)
          setError("This submission was not found.");
        else if (reason instanceof SubmissionApiError && reason.status === 403)
          setError("You are not authorised to review this submission.");
        else
          setError(
            reason instanceof Error
              ? reason.message
              : "OCR review could not be loaded. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, isStudent, reload]);

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
          sx={{ fontSize: 12, letterSpacing: ".1em", color: "#6F675E", mb: 3 }}
        >
          OCR REVIEW
        </Typography>
        {loading ? (
          <Stack alignItems="center" gap={2} sx={{ py: 7 }}>
            <CircularProgress aria-label="Loading saved submission" />
            <Typography>Loading saved submission…</Typography>
          </Stack>
        ) : null}
        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{ p: 3, borderColor: "#D79B63", bgcolor: "#FFFDFA" }}
          >
            <Typography sx={{ fontWeight: 700, color: "#9E3A24" }}>
              {error}
            </Typography>
            <Stack direction="row" gap={1} sx={{ mt: 2 }}>
              <Button onClick={() => setReload((value) => value + 1)}>
                Retry
              </Button>
              <Button component={Link} href={returnPath}>
                {isStudent ? "Return to My Worksheets" : "Return to upload"}
              </Button>
            </Stack>
          </Card>
        ) : null}
        {document ? (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
                mb: 1,
              }}
            >
              Review extracted text
            </Typography>
            <Typography sx={{ color: "#6F675E", mb: 2 }}>
              This review is tied to the saved submission, not temporary browser
              data.
            </Typography>
            <Card
              variant="outlined"
              sx={{ p: 2, mb: 2, bgcolor: "#FFFDFA", borderColor: "#EBE4D9" }}
            >
              <Typography sx={{ fontWeight: 700 }}>
                Submission #{document.id}
              </Typography>
              <Typography>
                Class #{document.classId} · Student #{document.studentId} ·
                Worksheet #{document.worksheetId}
              </Typography>
              <Typography sx={{ fontSize: 13, color: "#6F675E", mt: 0.5 }}>
                {document.pages.length} uploaded page
                {document.pages.length === 1 ? "" : "s"} · {document.status}
              </Typography>
            </Card>
            {document.status === "SUBMITTED_FOR_REVIEW" ? (
              <Card
                role="status"
                variant="outlined"
                sx={{ p: 2, borderColor: "#87A878", bgcolor: "#F7FBF4" }}
              >
                <Typography sx={{ fontWeight: 700 }}>
                  Submitted for Tutor Review
                </Typography>
                <Typography sx={{ color: "#506348", mt: 0.5 }}>
                  {isStudent
                    ? "Your OCR answers are saved and waiting for Tutor review."
                    : "The confirmed answers are saved in the Tutor review queue."}
                </Typography>
                <Button
                  component={Link}
                  href={
                    isStudent
                      ? `/worksheets/${document.worksheetId}/results`
                      : "/tutor/worksheets"
                  }
                  sx={{ mt: 1 }}
                >
                  {isStudent
                    ? "View worksheet status"
                    : "Open worksheet reviews"}
                </Button>
              </Card>
            ) : (
              <OcrReview
                pages={document.pages}
                questions={questions.map((question) => ({
                  id: question.id,
                  prompt: question.prompt,
                }))}
                onCorrect={async (extractionId, text) => {
                  const corrected = await correctOcrExtraction(
                    extractionId,
                    text,
                  );
                  setDocument((current) =>
                    current === null
                      ? current
                      : {
                          ...current,
                          pages: current.pages.map((page) =>
                            page.extractionId === extractionId
                              ? { ...page, ...corrected, pageId: page.pageId }
                              : page,
                          ),
                        },
                  );
                }}
                onSubmitForReview={
                  questions.length > 0
                    ? async (answers) => {
                        const result = await submitOcrForTutorReview(
                          document.id,
                          answers,
                        );
                        router.replace(
                          isStudent
                            ? `/worksheets/${document.worksheetId}/results`
                            : `/tutor/reviews/${result.submissionIds[0]}`,
                        );
                      }
                    : undefined
                }
              />
            )}
            <Stack direction="row" justifyContent="flex-end" sx={{ mt: 3 }}>
              <Button
                component={Link}
                href={isStudent ? "/worksheets" : completionPath}
                sx={
                  !isStudent
                    ? {
                        bgcolor: "#9E3A24",
                        color: "#FFFDFA",
                        "&:hover": { bgcolor: "#8A3120" },
                      }
                    : undefined
                }
              >
                {isStudent
                  ? "Return to My Worksheets"
                  : "Continue to worksheets"}
              </Button>
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
      <OcrPage />
    </React.Suspense>
  );
}
