"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import {
  fetchStudentWorksheetResults,
  type StudentWorksheetResult,
  type StudentWorksheetResultOutcome,
  type StudentWorksheetResultsResponse,
} from "@/services/submissions";

const INK = "rgb(24,21,18)";
const MUTED = "rgb(126,117,111)";
const BORDER = "rgb(232,226,217)";
const CARD_BG = "rgb(250,247,242)";

const outcomeMeta: Record<
  StudentWorksheetResultOutcome,
  { label: string; background: string; color: string }
> = {
  CORRECT: {
    label: "Correct",
    background: "rgb(233,238,233)",
    color: "rgb(50,66,50)",
  },
  PARTIAL: {
    label: "Partially correct",
    background: "rgb(248,239,220)",
    color: "rgb(116,82,31)",
  },
  INCORRECT: {
    label: "Incorrect",
    background: "rgb(248,232,226)",
    color: "rgb(155,68,48)",
  },
  REVIEW_NEEDED: {
    label: "Review needed",
    background: "rgb(238,235,232)",
    color: "rgb(77,69,64)",
  },
};

function isWorksheetId(value: string): boolean {
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
}

function reviewLabel(result: StudentWorksheetResult): string {
  if (result.reviewStatus === "APPROVED")
    return outcomeMeta[result.outcome].label;
  return result.reviewStatus === "FLAGGED"
    ? "Flagged for tutor review"
    : "Awaiting tutor review";
}

function ResultCard({
  result,
  index,
}: {
  result: StudentWorksheetResult;
  index: number;
}) {
  const [expanded, setExpanded] = React.useState(false);
  const approved = result.reviewStatus === "APPROVED";
  const meta = approved
    ? outcomeMeta[result.outcome]
    : outcomeMeta.REVIEW_NEEDED;
  return (
    <Card
      variant="outlined"
      sx={{
        borderRadius: 3.5,
        borderColor: BORDER,
        backgroundColor: CARD_BG,
        boxShadow: "none",
        overflow: "hidden",
      }}
    >
      <Box sx={{ p: 3 }}>
        <Stack
          direction="row"
          sx={{
            alignItems: "center",
            justifyContent: "space-between",
            gap: 1.5,
            mb: 1.5,
          }}
        >
          <Stack
            direction="row"
            spacing={1.25}
            sx={{ alignItems: "center", minWidth: 0 }}
          >
            <Typography
              sx={{
                fontSize: 12,
                fontWeight: 600,
                letterSpacing: "0.5px",
                textTransform: "uppercase",
                color: MUTED,
              }}
            >
              Question {index + 1}
            </Typography>
            <Chip
              label={reviewLabel(result)}
              size="small"
              sx={{
                fontSize: 12,
                fontWeight: 600,
                backgroundColor: meta.background,
                color: meta.color,
              }}
            />
          </Stack>
          <Typography
            aria-label={
              approved
                ? `Final mark ${result.awardedMarks} out of ${result.maximumMarks}`
                : "Final mark pending tutor review"
            }
            sx={{
              fontSize: 15,
              fontWeight: 600,
              color: INK,
              whiteSpace: "nowrap",
            }}
          >
            {approved
              ? `${result.awardedMarks} / ${result.maximumMarks}`
              : "Pending"}
          </Typography>
        </Stack>
        <Box
          sx={{
            backgroundColor: "rgb(247,243,241)",
            border: `1px solid ${BORDER}`,
            borderRadius: 2.5,
            p: 1.75,
          }}
        >
          <Typography
            sx={{
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: "0.5px",
              textTransform: "uppercase",
              color: MUTED,
              mb: 0.625,
            }}
          >
            Your answer
          </Typography>
          <Typography
            sx={{
              fontSize: 14,
              color: "rgb(45,41,38)",
              lineHeight: 1.45,
              whiteSpace: "pre-wrap",
            }}
          >
            {result.answer || "No answer submitted."}
          </Typography>
        </Box>
        {approved ? (
          <>
            <Box
              sx={{
                mt: 1.5,
                backgroundColor: "rgb(247,243,241)",
                border: `1px solid ${BORDER}`,
                borderRadius: 2.5,
                p: 1.75,
              }}
            >
              <Typography
                sx={{
                  fontSize: 11,
                  fontWeight: 600,
                  letterSpacing: "0.5px",
                  textTransform: "uppercase",
                  color: MUTED,
                  mb: 0.625,
                }}
              >
                Model answer
              </Typography>
              <Typography
                sx={{
                  fontSize: 14,
                  color: "rgb(45,41,38)",
                  lineHeight: 1.45,
                  whiteSpace: "pre-wrap",
                }}
              >
                {result.modelAnswer || "No model answer was supplied."}
              </Typography>
            </Box>
            {result.explanation && (
              <>
                <Button
                  aria-expanded={expanded}
                  onClick={() => setExpanded((current) => !current)}
                  endIcon={
                    <ExpandMoreIcon
                      sx={{
                        fontSize: 15,
                        transform: expanded ? "rotate(180deg)" : "none",
                      }}
                    />
                  }
                  sx={{
                    mt: 1.75,
                    p: 0,
                    minWidth: 0,
                    fontSize: 14,
                    fontWeight: 600,
                    color: "rgb(155,68,48)",
                    textTransform: "none",
                    "&:hover": { backgroundColor: "transparent" },
                  }}
                >
                  {expanded ? "Hide explanation" : "Read explanation"}
                </Button>
                {expanded && (
                  <Box
                    sx={{
                      mt: 1.25,
                      p: 1.75,
                      borderRadius: 2.5,
                      backgroundColor: "rgb(253,248,247)",
                      border: `1px solid ${BORDER}`,
                    }}
                  >
                    <Typography
                      sx={{
                        fontSize: 12,
                        fontWeight: 600,
                        letterSpacing: "0.4px",
                        textTransform: "uppercase",
                        color: "rgb(155,68,48)",
                        mb: 0.625,
                      }}
                    >
                      Tutor feedback
                    </Typography>
                    <Typography
                      sx={{
                        fontSize: 14,
                        color: "rgb(45,41,38)",
                        lineHeight: 1.5,
                        whiteSpace: "pre-wrap",
                      }}
                    >
                      {result.explanation}
                    </Typography>
                  </Box>
                )}
              </>
            )}
          </>
        ) : (
          <Typography
            role="status"
            sx={{ mt: 1.5, fontSize: 14, color: MUTED, lineHeight: 1.45 }}
          >
            Your tutor is still reviewing this answer. Final marks, feedback,
            and model answers will appear when it is approved.
          </Typography>
        )}
      </Box>
    </Card>
  );
}

function Results({ data }: { data: StudentWorksheetResultsResponse }) {
  const approved = data.results.filter(
    (result) => result.reviewStatus === "APPROVED",
  );
  const allApproved =
    data.results.length > 0 && approved.length === data.results.length;
  const earned = approved.reduce(
    (total, result) => total + (result.awardedMarks ?? 0),
    0,
  );
  const available = approved.reduce(
    (total, result) => total + result.maximumMarks,
    0,
  );
  const percentage =
    available === 0 ? 0 : Math.round((earned / available) * 100);
  return (
    <>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{
          justifyContent: "space-between",
          alignItems: { sm: "center" },
          mb: 3.5,
        }}
      >
        <Box>
          <Typography sx={{ fontSize: 13, color: MUTED, mb: 0.5 }}>
            Worksheet results
          </Typography>
          <Typography
            component="h1"
            sx={{
              fontFamily: "'EB Garamond', serif",
              fontWeight: 400,
              fontSize: 38,
              lineHeight: 1.1,
              letterSpacing: "-0.8px",
              color: INK,
            }}
          >
            Your submitted answers
          </Typography>
        </Box>
        <Card
          variant="outlined"
          sx={{
            minWidth: 195,
            borderRadius: 3,
            borderColor: BORDER,
            backgroundColor: CARD_BG,
            boxShadow: "none",
            p: 2,
            textAlign: "center",
          }}
        >
          {allApproved ? (
            <>
              <Typography
                aria-label="Final worksheet score"
                sx={{
                  fontFamily: "'EB Garamond', serif",
                  fontSize: 38,
                  lineHeight: 1,
                  color: INK,
                }}
              >
                {percentage}%
              </Typography>
              <Typography sx={{ fontSize: 13, color: MUTED, mt: 0.5 }}>
                Final score · {earned} / {available}
              </Typography>
            </>
          ) : (
            <>
              <Typography sx={{ fontSize: 15, fontWeight: 600, color: INK }}>
                Final score pending
              </Typography>
              <Typography sx={{ fontSize: 13, color: MUTED, mt: 0.5 }}>
                {approved.length} of {data.results.length} answer
                {data.results.length === 1 ? "" : "s"} approved
              </Typography>
            </>
          )}
        </Card>
      </Stack>
      {data.results.length === 0 ? (
        <Card
          variant="outlined"
          sx={{
            borderRadius: 3.5,
            borderColor: BORDER,
            backgroundColor: CARD_BG,
            boxShadow: "none",
            p: 3,
          }}
        >
          <Typography
            component="h2"
            sx={{ fontSize: 18, fontWeight: 600, color: INK }}
          >
            No submitted answers yet
          </Typography>
          <Typography sx={{ mt: 0.75, fontSize: 14, color: MUTED }}>
            Results will appear here after your work has been submitted and
            reviewed.
          </Typography>
        </Card>
      ) : (
        <Stack spacing={2}>
          {data.results.map((result, index) => (
            <ResultCard
              key={result.submissionId}
              result={result}
              index={index}
            />
          ))}
        </Stack>
      )}
    </>
  );
}

export default function Page({
  params,
}: {
  params: Promise<{ worksheetId: string }>;
}) {
  const [worksheetId, setWorksheetId] = React.useState<string | null>(null);
  const [state, setState] = React.useState<{
    loading: boolean;
    data: StudentWorksheetResultsResponse | null;
    error: string | null;
  }>({ loading: true, data: null, error: null });
  const load = React.useCallback(async () => {
    if (worksheetId === null) return;
    if (!isWorksheetId(worksheetId)) {
      setState({
        loading: false,
        data: null,
        error: "This worksheet link is invalid.",
      });
      return;
    }
    setState({ loading: true, data: null, error: null });
    try {
      const data = await fetchStudentWorksheetResults(Number(worksheetId));
      if (data.worksheetId !== Number(worksheetId))
        throw new Error("The loaded results do not match this worksheet.");
      setState({ loading: false, data, error: null });
    } catch (error) {
      setState({
        loading: false,
        data: null,
        error:
          error instanceof Error
            ? error.message
            : "The worksheet results could not be loaded.",
      });
    }
  }, [worksheetId]);
  React.useEffect(() => {
    let current = true;
    void params.then(({ worksheetId: id }) => {
      if (current) setWorksheetId(id);
    });
    return () => {
      current = false;
    };
  }, [params]);
  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);
  return (
    <Box
      sx={{
        backgroundColor: "rgb(253,251,247)",
        minHeight: "100vh",
        py: 5,
        px: { xs: 2, sm: 4, md: 6 },
      }}
    >
      <Box sx={{ maxWidth: 1000, mx: "auto" }}>
        <Button
          component={Link}
          href={`/worksheets/${worksheetId ?? ""}`}
          startIcon={<ArrowBackIcon sx={{ fontSize: 16 }} />}
          sx={{
            color: "rgb(77,69,64)",
            textTransform: "none",
            fontSize: 14,
            mb: 2.5,
            p: 0,
            minWidth: 0,
            "&:hover": { backgroundColor: "transparent", color: INK },
          }}
        >
          Back to worksheet
        </Button>
        {state.loading && (
          <Stack
            data-testid="student-results-loading"
            sx={{ alignItems: "center", py: 9 }}
            spacing={1.5}
          >
            <CircularProgress size={28} />
            <Typography sx={{ color: MUTED }}>
              Loading worksheet results…
            </Typography>
          </Stack>
        )}
        {state.error && (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              borderRadius: 3.5,
              borderColor: "rgb(238,210,201)",
              backgroundColor: "rgb(253,248,247)",
              boxShadow: "none",
              p: 3,
            }}
          >
            <Typography
              component="h1"
              sx={{ fontSize: 20, fontWeight: 600, color: INK }}
            >
              Worksheet results unavailable
            </Typography>
            <Typography
              sx={{ mt: 0.75, fontSize: 14, color: "rgb(155,68,48)" }}
            >
              {state.error}
            </Typography>
            {worksheetId !== null && isWorksheetId(worksheetId) && (
              <Button
                onClick={() => void load()}
                sx={{ mt: 1.5, textTransform: "none" }}
              >
                Retry loading results
              </Button>
            )}
          </Card>
        )}
        {state.data && <Results data={state.data} />}
      </Box>
    </Box>
  );
}
