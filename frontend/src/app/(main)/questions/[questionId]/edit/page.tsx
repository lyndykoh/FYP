"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import QuestionForm from "@/components/questions/QuestionForm";
import {
  fetchTutorQuestion,
  type TutorQuestion,
  updateTutorQuestion,
} from "@/services/questions";

function MissingQuestion({ message }: { message: string }) {
  return (
    <Card
      component="section"
      role="alert"
      variant="outlined"
      sx={{
        maxWidth: 620,
        p: { xs: 2.5, sm: 3 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        borderLeft: "3px solid #B4573F",
        boxShadow: "none",
      }}
    >
      <Typography
        component="h2"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: 21,
          fontWeight: 500,
          mb: 0.75,
        }}
      >
        Question cannot be edited
      </Typography>
      <Typography
        sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
      >
        {message}
      </Typography>
      <Button
        component={Link}
        href="/questions"
        sx={{
          minHeight: 40,
          border: "1px solid #E4DCD0",
          borderRadius: "10px",
          color: "#2A2622",
          textTransform: "none",
          fontWeight: 500,
        }}
      >
        Back to Question Bank
      </Button>
    </Card>
  );
}

export default function EditQuestionPage() {
  const params = useParams<{ questionId: string }>();
  const router = useRouter();
  const questionId = Number(params.questionId);
  const validQuestionId = Number.isSafeInteger(questionId) && questionId > 0;
  const [question, setQuestion] = React.useState<TutorQuestion | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  React.useEffect(() => {
    let current = true;
    if (!validQuestionId)
      return () => {
        current = false;
      };
    void fetchTutorQuestion(questionId)
      .then((loaded) => {
        if (current) setQuestion(loaded);
      })
      .catch((reason: unknown) => {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "This question could not be loaded.",
          );
      });
    return () => {
      current = false;
    };
  }, [questionId, validQuestionId]);
  const displayError = !validQuestionId
    ? "This question reference is invalid. Return to the Question Bank and choose a question to edit."
    : error;
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
        <Box
          component={Link}
          href="/questions"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 0.9,
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            textDecoration: "none",
            mb: 2.5,
          }}
        >
          <ArrowBackIcon aria-hidden="true" sx={{ fontSize: 14 }} />
          QUESTION BANK
        </Box>
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
          Edit question
        </Typography>
        <Typography
          sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mb: 3 }}
        >
          Update the prompt, marking guidance, and availability.
        </Typography>
        {!question && !displayError ? (
          <Card
            data-testid="edit-question-skeleton"
            variant="outlined"
            sx={{
              maxWidth: 940,
              p: 3,
              borderRadius: "14px",
              bgcolor: "#FFFDFA",
              borderColor: "#EBE4D9",
              boxShadow: "none",
            }}
          >
            <Skeleton height={48} sx={{ bgcolor: "#F0EAE0" }} />
            <Skeleton height={120} sx={{ bgcolor: "#F0EAE0", mt: 2 }} />
          </Card>
        ) : displayError || !question ? (
          <MissingQuestion
            message={displayError ?? "This question is not available."}
          />
        ) : (
          <QuestionForm
            mode="edit"
            initialQuestion={question}
            submitQuestion={(request) =>
              updateTutorQuestion(question.id, request)
            }
            onComplete={() => router.push("/questions")}
          />
        )}
      </Box>
    </Box>
  );
}
