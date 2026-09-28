"use client";

import Box from "@mui/material/Box";
import Link from "next/link";

import QuestionImportReview from "@/components/questions/QuestionImportReview";

export default function QuestionImportPage() {
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
        <Box
          component={Link}
          href="/questions"
          sx={{
            display: "inline-block",
            color: "#8B837A",
            fontSize: 12,
            textDecoration: "none",
            mb: 2,
          }}
        >
          ← Question Bank
        </Box>
        <QuestionImportReview />
      </Box>
    </Box>
  );
}
