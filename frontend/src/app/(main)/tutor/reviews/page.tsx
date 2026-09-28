"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  fetchPendingMarkingReviews,
  type PendingMarkingReview,
} from "@/services/marking-reviews";

function submittedAt(value: string): string {
  return value.replace("T", " at ");
}

export default function TutorReviewsPage() {
  const [reviews, setReviews] = React.useState<PendingMarkingReview[] | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);
  const [reloadAttempt, setReloadAttempt] = React.useState(0);

  const loadReviews = React.useCallback(() => {
    void fetchPendingMarkingReviews().then(
      (pendingReviews) => {
        setReviews(pendingReviews);
        setError(null);
      },
      (reason: unknown) => {
        setError(
          reason instanceof Error
            ? reason.message
            : "Pending reviews could not be loaded. Please try again.",
        );
      },
    );
  }, []);

  React.useEffect(() => {
    let current = true;
    void fetchPendingMarkingReviews().then(
      (pendingReviews) => {
        if (!current) return;
        setReviews(pendingReviews);
        setError(null);
      },
      (reason: unknown) => {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "Pending reviews could not be loaded. Please try again.",
          );
      },
    );
    return () => {
      current = false;
    };
  }, [reloadAttempt]);

  React.useEffect(() => {
    const refreshWhenTutorReturns = () => loadReviews();
    window.addEventListener("focus", refreshWhenTutorReturns);
    return () => window.removeEventListener("focus", refreshWhenTutorReturns);
  }, [loadReviews]);

  const retry = () => {
    setReviews(null);
    setError(null);
    setReloadAttempt((value) => value + 1);
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2.5, sm: 3.75 },
        py: 3.75,
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
            fontWeight: 500,
          }}
        >
          Pending reviews
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 14, mt: 0.75, mb: 3 }}>
          Submitted worksheets that need your marking decision.
        </Typography>

        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              p: 3,
              borderLeft: "3px solid #B4573F",
              borderColor: "#EBE4D9",
              bgcolor: "#FFFDFA",
            }}
          >
            <Typography sx={{ mb: 1 }}>{error}</Typography>
            <Button
              onClick={retry}
              sx={{ color: "#9E3A24", textTransform: "none" }}
            >
              Retry loading reviews
            </Button>
          </Card>
        ) : reviews === null ? (
          <Typography role="status" sx={{ color: "#8B837A" }}>
            Loading pending reviews…
          </Typography>
        ) : reviews.length === 0 ? (
          <Card
            component="section"
            variant="outlined"
            sx={{
              p: 3,
              border: "1px dashed #DCCFBE",
              bgcolor: "#FFFDFA",
              textAlign: "center",
            }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
              }}
            >
              No pending reviews
            </Typography>
            <Typography sx={{ color: "#8B837A", fontSize: 13, mt: 0.75 }}>
              Submitted worksheets will appear here when they are ready for your
              review.
            </Typography>
          </Card>
        ) : (
          <Box
            component="section"
            aria-label="Pending worksheet reviews"
            sx={{ display: "grid", gap: 1.25 }}
          >
            {reviews.map((review) => (
              <Card
                key={review.submissionId}
                variant="outlined"
                sx={{
                  p: { xs: 2, sm: 2.25 },
                  borderRadius: "12px",
                  borderColor: "#EBE4D9",
                  bgcolor: "#FFFDFA",
                  boxShadow: "none",
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "center",
                  gap: 1.25,
                }}
              >
                <Box sx={{ flex: "1 1 260px", minWidth: 0 }}>
                  <Typography sx={{ fontSize: 14, fontWeight: 600 }}>
                    Worksheet #{review.worksheetId}
                  </Typography>
                  <Typography sx={{ color: "#6F675E", fontSize: 13, mt: 0.45 }}>
                    {review.studentName}
                  </Typography>
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.45 }}
                  >
                    Submitted {submittedAt(review.requestedAt)}
                  </Typography>
                </Box>
                <Chip
                  label="SUBMITTED"
                  size="small"
                  sx={{
                    bgcolor: "#F7E3DC",
                    color: "#9E3A24",
                    fontSize: 9.5,
                    fontWeight: 700,
                  }}
                />
                <Button
                  component={Link}
                  href={`/tutor/reviews/${review.submissionId}/source`}
                  sx={{
                    minHeight: 34,
                    color: "#9E3A24",
                    textTransform: "none",
                    fontWeight: 600,
                  }}
                  disabled={!review.sourceAvailable}
                >
                  View submitted worksheet
                </Button>
                <Button
                  component={Link}
                  href={`/tutor/reviews/${review.submissionId}`}
                  sx={{
                    minHeight: 34,
                    bgcolor: "#9E3A24",
                    color: "#FFFDFA",
                    textTransform: "none",
                    fontWeight: 600,
                    "&:hover": { bgcolor: "#8A3120" },
                  }}
                >
                  Review answers
                </Button>
              </Card>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
}
