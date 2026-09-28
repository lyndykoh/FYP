"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import PlaylistAddOutlinedIcon from "@mui/icons-material/PlaylistAddOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  addQuestionToWorksheetDraft,
  checkTutorQuestionAnswer,
  fetchTutorQuestion,
  fetchQuestionImageUrl,
  isQuestionInWorksheetDraft,
  type QuestionRuleCheckResult,
  type TutorQuestion,
} from "@/services/questions";
import SecureImageGallery from "@/components/questions/SecureImageGallery";

export interface QuestionDetailProps {
  questionId: number;
  loadQuestion?: (questionId: number) => Promise<TutorQuestion>;
  addToWorksheetDraft?: (questionId: number) => {
    ids: number[];
    added: boolean;
    storageUnavailable?: boolean;
  };
  isInWorksheetDraft?: (questionId: number) => boolean;
  checkAnswer?: (
    questionId: number,
    answer: string,
  ) => Promise<QuestionRuleCheckResult>;
}

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;
const secondaryButton = {
  minHeight: 40,
  borderColor: "#E4DCD0",
  bgcolor: "#FFFDFA",
  color: "#2A2622",
  textTransform: "none",
  fontWeight: 500,
  borderRadius: "10px",
  px: 1.8,
  "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
} as const;
const primaryButton = {
  minHeight: 42,
  borderRadius: "10px",
  px: 1.8,
  bgcolor: "#9E3A24",
  color: "#FFFDFA",
  textTransform: "none",
  fontWeight: 600,
  "&:hover": { bgcolor: "#7F2E1C" },
  "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#8B837A" },
} as const;

function typeLabel(type: TutorQuestion["questionType"]) {
  return type
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function QuestionDetailSkeleton() {
  return (
    <Box
      data-testid="question-detail-skeleton"
      aria-label="Loading question details"
      sx={{ display: "grid", gap: 2 }}
    >
      <Skeleton
        variant="text"
        width="34%"
        height={52}
        sx={{ bgcolor: "#F0EAE0" }}
      />
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          gap: 2.5,
          alignItems: "flex-start",
        }}
      >
        <Box sx={{ flex: "1 1 460px", minWidth: 0, display: "grid", gap: 2.5 }}>
          <Skeleton
            variant="rounded"
            height={230}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
          <Skeleton
            variant="rounded"
            height={200}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        </Box>
        <Box sx={{ flex: "0 1 320px", minWidth: 0 }}>
          <Skeleton
            variant="rounded"
            height={170}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        </Box>
      </Box>
    </Box>
  );
}

function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <Card
      component="section"
      role="alert"
      variant="outlined"
      sx={{ ...card, maxWidth: 620, p: 3, borderLeft: "3px solid #B4573F" }}
    >
      <Typography
        component="h1"
        sx={{ fontFamily: serif, fontSize: 24, fontWeight: 500, mb: 0.75 }}
      >
        Question could not be opened
      </Typography>
      <Typography
        sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
      >
        {message}
      </Typography>
      {onRetry && (
        <Button onClick={onRetry} variant="outlined" sx={secondaryButton}>
          Retry loading question
        </Button>
      )}
    </Card>
  );
}

export default function QuestionDetail({
  questionId,
  loadQuestion = fetchTutorQuestion,
  addToWorksheetDraft = addQuestionToWorksheetDraft,
  isInWorksheetDraft = isQuestionInWorksheetDraft,
  checkAnswer = checkTutorQuestionAnswer,
}: QuestionDetailProps) {
  const [question, setQuestion] = React.useState<TutorQuestion | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [inDraft, setInDraft] = React.useState(false);
  const [draftMessage, setDraftMessage] = React.useState<string | null>(null);
  const [answerToCheck, setAnswerToCheck] = React.useState("");
  const [checkResult, setCheckResult] =
    React.useState<QuestionRuleCheckResult | null>(null);
  const [checkError, setCheckError] = React.useState<string | null>(null);
  const [checking, setChecking] = React.useState(false);
  const requestId = React.useRef(0);
  const validQuestionId = Number.isSafeInteger(questionId) && questionId > 0;

  const load = React.useCallback(async () => {
    if (!validQuestionId) return;
    const currentRequest = ++requestId.current;
    // Defer the reset so mounting the effect does not synchronously cascade a render.
    await Promise.resolve();
    if (requestId.current !== currentRequest) return;
    setQuestion(null);
    setError(null);
    setDraftMessage(null);
    void loadQuestion(questionId).then(
      (loaded) => {
        if (requestId.current !== currentRequest) return;
        setQuestion(loaded);
        setInDraft(
          loaded.archiveState === "ACTIVE" && isInWorksheetDraft(questionId),
        );
      },
      (reason: unknown) => {
        if (requestId.current === currentRequest)
          setError(
            reason instanceof Error
              ? reason.message
              : "Question details could not be loaded. Please try again.",
          );
      },
    );
  }, [isInWorksheetDraft, loadQuestion, questionId, validQuestionId]);

  React.useEffect(() => {
    if (!validQuestionId) return undefined;
    void load();
    return () => {
      requestId.current += 1;
    };
  }, [load, validQuestionId]);

  const addToDraft = () => {
    if (!question || question.archiveState !== "ACTIVE") return;
    const result = addToWorksheetDraft(question.id);
    if (result.storageUnavailable) {
      setDraftMessage(
        "Your browser could not save this worksheet selection. Check browser storage permissions and try again.",
      );
      return;
    }
    setInDraft(true);
    setDraftMessage(
      result.added
        ? "Added to the local worksheet draft selection. You can continue choosing questions while the worksheet editor is being connected."
        : "This question is already in the local worksheet draft selection.",
    );
  };

  const runRuleCheck = async () => {
    if (!question || !answerToCheck.trim() || checking) return;
    setChecking(true);
    setCheckError(null);
    setCheckResult(null);
    try {
      setCheckResult(await checkAnswer(question.id, answerToCheck));
    } catch (reason) {
      setCheckError(
        reason instanceof Error
          ? reason.message
          : "This answer could not be checked. Please try again.",
      );
    } finally {
      setChecking(false);
    }
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
            "&:hover": { color: "#B4573F" },
            "&:focus-visible": {
              outline: "3px solid #E08A72",
              outlineOffset: 3,
              borderRadius: 1,
            },
          }}
        >
          <ArrowBackIcon aria-hidden="true" sx={{ fontSize: 14 }} />
          QUESTION BANK
        </Box>
        {!validQuestionId ? (
          <ErrorState message="This question reference is invalid. Return to the Question Bank and choose a question to open." />
        ) : error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : !question ? (
          <QuestionDetailSkeleton />
        ) : (
          <>
            <Box
              sx={{
                display: "flex",
                flexWrap: "wrap",
                justifyContent: "space-between",
                alignItems: "flex-end",
                gap: 2,
                mb: 3,
              }}
            >
              <Box sx={{ minWidth: 0 }}>
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
                    fontFamily: serif,
                    fontSize: { xs: 32, sm: 38 },
                    fontWeight: 500,
                    lineHeight: 1.1,
                    letterSpacing: "-.02em",
                    textWrap: "pretty",
                  }}
                >
                  {question.code}
                </Typography>
                <Typography sx={{ color: "#6F675E", fontSize: 14, mt: 0.8 }}>
                  {typeLabel(question.questionType)} ·{" "}
                  {question.totalMarks.toFixed(1)} marks
                </Typography>
              </Box>
              <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                <Button
                  component={Link}
                  href={`/questions/${question.id}/edit`}
                  startIcon={<EditOutlinedIcon />}
                  variant="outlined"
                  sx={secondaryButton}
                >
                  Edit question
                </Button>
                <Button
                  onClick={addToDraft}
                  disabled={question.archiveState !== "ACTIVE" || inDraft}
                  startIcon={<PlaylistAddOutlinedIcon />}
                  sx={primaryButton}
                >
                  {inDraft
                    ? "Added to worksheet draft"
                    : "Add to worksheet draft"}
                </Button>
              </Box>
            </Box>
            <Box
              sx={{
                display: "flex",
                flexWrap: "wrap",
                gap: 2.5,
                alignItems: "flex-start",
              }}
            >
              <Box
                sx={{
                  flex: "1 1 460px",
                  minWidth: 0,
                  display: "grid",
                  gap: 2.5,
                }}
              >
                <Card
                  component="section"
                  aria-labelledby="question-prompt-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
                >
                  <Box
                    sx={{
                      display: "flex",
                      flexWrap: "wrap",
                      alignItems: "center",
                      gap: 0.75,
                      mb: 1.5,
                    }}
                  >
                    <Chip
                      label={typeLabel(question.questionType).toUpperCase()}
                      size="small"
                      sx={{
                        height: 23,
                        bgcolor: "#F0EAE0",
                        color: "#6F675E",
                        fontSize: 9.5,
                        fontWeight: 700,
                        letterSpacing: ".05em",
                      }}
                    />
                    <Chip
                      label={question.archiveState}
                      size="small"
                      sx={{
                        height: 23,
                        bgcolor:
                          question.archiveState === "ACTIVE"
                            ? "#E9EEE8"
                            : "#F0EAE0",
                        color:
                          question.archiveState === "ACTIVE"
                            ? "#4A6B50"
                            : "#6F675E",
                        fontSize: 9.5,
                        fontWeight: 700,
                        letterSpacing: ".05em",
                      }}
                    />
                  </Box>
                  <Typography
                    id="question-prompt-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.25,
                    }}
                  >
                    Question
                  </Typography>
                  <Typography
                    sx={{
                      color: "#2A2622",
                      fontSize: 15,
                      lineHeight: 1.75,
                      whiteSpace: "pre-wrap",
                    }}
                  >
                    {question.prompt}
                  </Typography>
                  <SecureImageGallery
                    images={question.images ?? []}
                    loadImage={(imageId) =>
                      fetchQuestionImageUrl(question.id, imageId)
                    }
                  />
                </Card>
                <Card
                  component="section"
                  aria-labelledby="marking-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
                >
                  <Typography
                    id="marking-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.5,
                    }}
                  >
                    Marking guidance
                  </Typography>
                  <Box
                    component="ol"
                    sx={{ m: 0, pl: 2.5, display: "grid", gap: 1.1 }}
                  >
                    {question.markingComponents.map((component) => (
                      <Box
                        component="li"
                        key={`${component.position}-${component.description}`}
                        sx={{ pl: 0.35, color: "#2A2622" }}
                      >
                        <Box
                          sx={{
                            display: "flex",
                            justifyContent: "space-between",
                            gap: 1.5,
                            borderBottom: "1px solid #F0EAE0",
                            pb: 1.1,
                          }}
                        >
                          <Typography sx={{ fontSize: 13.5, lineHeight: 1.55 }}>
                            {component.description}
                          </Typography>
                          <Typography
                            sx={{
                              color: "#6F675E",
                              fontSize: 12.5,
                              fontWeight: 600,
                              whiteSpace: "nowrap",
                              fontVariantNumeric: "tabular-nums",
                            }}
                          >
                            {component.marks.toFixed(1)} marks
                          </Typography>
                        </Box>
                      </Box>
                    ))}
                  </Box>
                  <Box sx={{ mt: 2, pt: 1.75, borderTop: "1px solid #EFE8DE" }}>
                    <Typography
                      sx={{
                        color: "#A09488",
                        fontSize: 10.5,
                        fontWeight: 600,
                        letterSpacing: ".09em",
                        mb: 0.6,
                      }}
                    >
                      MODEL ANSWER
                    </Typography>
                    <Typography
                      sx={{
                        color: "#4A443D",
                        fontSize: 13.5,
                        lineHeight: 1.65,
                        whiteSpace: "pre-wrap",
                      }}
                    >
                      {question.modelAnswer}
                    </Typography>
                  </Box>
                </Card>
              </Box>
              <Box
                sx={{
                  flex: "0 1 320px",
                  minWidth: 0,
                  display: "grid",
                  gap: 2.5,
                }}
              >
                <Card
                  component="aside"
                  aria-labelledby="syllabus-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
                >
                  <Typography
                    id="syllabus-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.25,
                    }}
                  >
                    Syllabus link
                  </Typography>
                  <Typography
                    sx={{
                      color: "#2A2622",
                      fontSize: 14,
                      fontWeight: 600,
                      lineHeight: 1.45,
                    }}
                  >
                    {question.syllabusTopic.name}
                  </Typography>
                  <Typography
                    sx={{
                      color: "#8B837A",
                      fontSize: 11.5,
                      lineHeight: 1.6,
                      mt: 0.55,
                    }}
                  >
                    {question.syllabusTopic.code} ·{" "}
                    {question.syllabusTopic.nodeType.toLowerCase()}
                  </Typography>
                </Card>
                <Card
                  component="section"
                  aria-labelledby="keywords-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
                >
                  <Typography
                    id="keywords-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.25,
                    }}
                  >
                    Key terms
                  </Typography>
                  {question.keywords.length === 0 ? (
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}
                    >
                      No key terms have been recorded.
                    </Typography>
                  ) : (
                    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.65 }}>
                      {question.keywords.map((keyword) => (
                        <Chip
                          key={keyword}
                          label={keyword}
                          size="small"
                          sx={{
                            height: 27,
                            bgcolor: "#F4EFE6",
                            color: "#5A544C",
                            fontSize: 11.5,
                          }}
                        />
                      ))}
                    </Box>
                  )}
                </Card>
                <Card
                  component="section"
                  aria-labelledby="rule-check-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
                >
                  <Typography
                    id="rule-check-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 20,
                      fontWeight: 500,
                      mb: 0.75,
                    }}
                  >
                    Answer-check preview
                  </Typography>
                  <Typography
                    sx={{
                      color: "#8B837A",
                      fontSize: 12.5,
                      lineHeight: 1.6,
                      mb: 1.25,
                    }}
                  >
                    Check only against the approved keywords above. This does
                    not save a grade.
                  </Typography>
                  <TextField
                    fullWidth
                    multiline
                    minRows={3}
                    label="Sample student answer"
                    value={answerToCheck}
                    onChange={(event) => {
                      setAnswerToCheck(event.target.value);
                      setCheckError(null);
                    }}
                    slotProps={{
                      htmlInput: {
                        maxLength: 4000,
                        "aria-label": "Sample student answer",
                      },
                    }}
                    sx={{
                      ".MuiOutlinedInput-root": {
                        bgcolor: "#FBF9F5",
                        borderRadius: "9px",
                        fontSize: 13,
                        "& fieldset": { borderColor: "#E4DCD0" },
                      },
                      ".MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
                    }}
                  />
                  <Button
                    onClick={() => void runRuleCheck()}
                    disabled={!answerToCheck.trim() || checking}
                    sx={{ ...primaryButton, width: "100%", mt: 1.25 }}
                  >
                    {checking ? "Checking…" : "Check answer"}
                  </Button>
                  {checkError && (
                    <Typography
                      role="alert"
                      sx={{
                        color: "#B4573F",
                        fontSize: 12,
                        lineHeight: 1.55,
                        mt: 1,
                      }}
                    >
                      {checkError}
                    </Typography>
                  )}
                  {checkResult && (
                    <Box
                      role="status"
                      aria-live="polite"
                      sx={{
                        mt: 1.25,
                        p: 1.25,
                        borderRadius: "9px",
                        bgcolor: "#F4EFE6",
                      }}
                    >
                      <Typography
                        sx={{ color: "#2A2622", fontSize: 13, fontWeight: 600 }}
                      >
                        {checkResult.awardedMarks.toFixed(2)} /{" "}
                        {checkResult.maximumMarks.toFixed(2)} marks
                      </Typography>
                      <Typography
                        sx={{
                          color: "#6F675E",
                          fontSize: 11.5,
                          lineHeight: 1.55,
                          mt: 0.35,
                        }}
                      >
                        {checkResult.explanation}
                      </Typography>
                      <Box
                        component="ul"
                        sx={{ m: "8px 0 0", pl: 2, display: "grid", gap: 0.45 }}
                      >
                        {checkResult.componentResults.map((component) => (
                          <Typography
                            component="li"
                            key={component.position}
                            sx={{
                              color: component.matched ? "#4A6B50" : "#8B837A",
                              fontSize: 11.5,
                              lineHeight: 1.45,
                            }}
                          >
                            {component.description}: {component.feedback}
                          </Typography>
                        ))}
                      </Box>
                    </Box>
                  )}
                </Card>
                <Card
                  component="section"
                  aria-labelledby="worksheet-selection-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
                >
                  <Typography
                    id="worksheet-selection-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 20,
                      fontWeight: 500,
                      mb: 0.75,
                    }}
                  >
                    Worksheet selection
                  </Typography>
                  {question.archiveState === "ARCHIVED" ? (
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}
                    >
                      Archived questions cannot be added to a worksheet draft.
                    </Typography>
                  ) : (
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}
                    >
                      Choose this question now; the Phase 4 worksheet editor
                      will use the saved local selection.
                    </Typography>
                  )}
                  <Typography
                    role="status"
                    aria-live="polite"
                    sx={{
                      color: "#4A6B50",
                      fontSize: 12,
                      lineHeight: 1.55,
                      mt: 1,
                    }}
                  >
                    {draftMessage ??
                      (inDraft
                        ? "This question is in the local worksheet draft selection."
                        : "")}
                  </Typography>
                </Card>
              </Box>
            </Box>
          </>
        )}
      </Box>
    </Box>
  );
}
