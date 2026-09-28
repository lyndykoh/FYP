"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams } from "next/navigation";

import {
  fetchSubmittedWorksheetSource,
  fetchSubmittedWorksheetSourcePageUrl,
  type SubmittedWorksheetSourcePage,
  type SubmittedWorksheetSource,
} from "@/services/marking-reviews";
import {
  fetchMarkingReview,
  type MarkingReview as MarkingReviewData,
} from "@/services/submissions";

const card = {
  p: { xs: 2, sm: 2.5 },
  borderColor: "#E8DFD3",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
};

function validSubmissionId(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function SubmittedSourcePage({
  submissionId,
  page,
}: {
  submissionId: number;
  page: SubmittedWorksheetSourcePage;
}) {
  const [url, setUrl] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let active = true;
    let objectUrl: string | null = null;

    void fetchSubmittedWorksheetSourcePageUrl(submissionId, page.id)
      .then((loadedUrl) => {
        if (!active) {
          URL.revokeObjectURL(loadedUrl);
          return;
        }
        objectUrl = loadedUrl;
        setUrl(loadedUrl);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        setError(
          reason instanceof Error
            ? reason.message
            : "The submitted worksheet page could not be displayed.",
        );
      });

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [page.id, submissionId]);

  return (
    <Card component="section" variant="outlined" sx={card}>
      <Typography sx={{ fontWeight: 700 }}>
        {page.mediaType === "application/pdf"
          ? "Submitted PDF"
          : `Submitted page ${page.pageNumber}`}
      </Typography>
      <Typography sx={{ color: "#6F675E", fontSize: 13, mt: 0.45 }}>
        {page.originalFilename}
      </Typography>
      {error ? (
        <Typography role="alert" sx={{ color: "#A43D25", mt: 1.25 }}>
          {error}
        </Typography>
      ) : url === null ? (
        <Typography role="status" sx={{ color: "#8B837A", mt: 1.25 }}>
          Loading submitted worksheet…
        </Typography>
      ) : page.mediaType === "application/pdf" ? (
        <Box
          component="iframe"
          title={`Submitted worksheet PDF: ${page.originalFilename}`}
          src={url}
          sx={{ mt: 1.5, width: "100%", minHeight: 620, border: 0 }}
        />
      ) : (
        <Box
          component="img"
          src={url}
          alt={`Submitted worksheet page ${page.pageNumber}`}
          sx={{
            display: "block",
            maxWidth: "100%",
            height: "auto",
            maxHeight: 900,
            mt: 1.5,
            mx: "auto",
            border: "1px solid #EBE4D9",
            borderRadius: "8px",
          }}
        />
      )}
    </Card>
  );
}

export default function SubmittedWorksheetSourcePage() {
  const { submissionId: rawSubmissionId } = useParams<{ submissionId: string }>();
  const submissionId = validSubmissionId(rawSubmissionId);
  const [review, setReview] = React.useState<MarkingReviewData | null>(null);
  const [source, setSource] = React.useState<SubmittedWorksheetSource | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(
    submissionId === null ? "The submission id is invalid." : null,
  );

  React.useEffect(() => {
    if (submissionId === null) return;
    let active = true;

    void Promise.all([
      fetchSubmittedWorksheetSource(submissionId),
      fetchMarkingReview(submissionId),
    ])
      .then(([loadedSource, loadedReview]) => {
        if (!active) return;
        setSource(loadedSource);
        setReview(loadedReview);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        setError(
          reason instanceof Error
            ? reason.message
            : "The submitted worksheet could not be opened.",
        );
      });

    return () => {
      active = false;
    };
  }, [submissionId]);

  if (error) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography role="alert">{error}</Typography>
      </Box>
    );
  }

  if (!source || !review || submissionId === null) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography role="status">Loading submitted worksheet…</Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 4 },
        py: 4,
        color: "#2A2622",
      }}
    >
      <Box sx={{ maxWidth: 1120, mx: "auto" }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          TUTOR WORKFLOW
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 38 },
          }}
        >
          Submitted worksheet
        </Typography>
        <Typography sx={{ color: "#6F675E", mt: 0.75, mb: 2.5 }}>
          Student #{source.studentId} · Worksheet #{source.worksheetId}
        </Typography>

        <Stack sx={{ gap: 2 }}>
          <Card variant="outlined" sx={card}>
            <Typography sx={{ color: "#81786E", fontSize: 11, fontWeight: 700 }}>
              EXTRACTED STUDENT ANSWER
            </Typography>
            <Typography sx={{ whiteSpace: "pre-wrap", lineHeight: 1.65, mt: 0.8 }}>
              {review.extractedAnswer || "No readable answer was extracted."}
            </Typography>
          </Card>

          {source.pages.map((page) => (
            <SubmittedSourcePage
              key={page.id}
              submissionId={submissionId}
              page={page}
            />
          ))}

          <Stack direction={{ xs: "column", sm: "row" }} sx={{ gap: 1.25 }}>
            <Button component={Link} href="/tutor/reviews">
              Back to Tutor View
            </Button>
            <Button
              component={Link}
              href={`/tutor/reviews/${submissionId}`}
              sx={{
                bgcolor: "#9E3A24",
                color: "#FFFDFA",
                textTransform: "none",
                "&:hover": { bgcolor: "#8A3120" },
              }}
            >
              Continue to Review
            </Button>
          </Stack>
        </Stack>
      </Box>
    </Box>
  );
}
