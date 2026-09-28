"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useParams } from "next/navigation";
import MarkingReview from "@/components/marking/MarkingReview";
import {
  fetchMarkingReview,
  type MarkingReview as MarkingReviewData,
} from "@/services/submissions";

export default function Page() {
  const { submissionId } = useParams<{ submissionId: string }>();
  const id = Number(submissionId);
  const valid = Number.isSafeInteger(id) && id > 0;
  const [review, setReview] = React.useState<MarkingReviewData | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  React.useEffect(() => {
    if (!valid) return;
    let active = true;
    fetchMarkingReview(id)
      .then((value) => {
        if (active) setReview(value);
      })
      .catch((caught: unknown) => {
        if (active)
          setError(
            caught instanceof Error
              ? caught.message
              : "The marking review could not be opened.",
          );
      });
    return () => {
      active = false;
    };
  }, [id, valid]);
  if (!valid || error)
    return (
      <Box sx={{ p: 3 }}>
        <Typography role="alert">
          {error || "The marking review could not be opened."}
        </Typography>
      </Box>
    );
  if (!review)
    return (
      <Box sx={{ p: 3 }}>
        <Typography>Loading marking review…</Typography>
      </Box>
    );
  return <MarkingReview review={review} />;
}
