"use client";

import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useRouter } from "next/navigation";

import QuestionForm from "@/components/questions/QuestionForm";
import { createTutorQuestion } from "@/services/questions";

export default function NewQuestionPage() {
  const router = useRouter();
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
      <Box
        sx={{ maxWidth: 1420, mx: "auto", animation: "fadeUp .35s ease both" }}
      >
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          WORKSHEET PREPARATION
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 38 },
            fontWeight: 500,
            lineHeight: 1.1,
            letterSpacing: "-.02em",
            mb: 1,
          }}
        >
          Add a question
        </Typography>
        <Typography
          sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mb: 3 }}
        >
          Create a syllabus-linked question with clear marking guidance.
        </Typography>
        <QuestionForm
          mode="create"
          submitQuestion={createTutorQuestion}
          onComplete={() => router.push("/questions")}
        />
      </Box>
    </Box>
  );
}
