"use client";

import * as React from "react";
import ArrowBackOutlinedIcon from "@mui/icons-material/ArrowBackOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";

import { fetchMasteryTopic, type MasteryTopicDetail } from "@/services/mastery";

function validId(value: string | null | undefined) {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function barColour(score: number) {
  return score < 55 ? "#B4573F" : score < 72 ? "#D8B384" : "#93A896";
}

export default function TopicDetailPage() {
  const params = useParams<{ topicId: string }>();
  const search = useSearchParams();
  const topicId = validId(params.topicId);
  const studentId = validId(search.get("studentId"));
  const [detail, setDetail] = React.useState<MasteryTopicDetail | null>(null);
  const [error, setError] = React.useState<string | null>(
    topicId ? null : "This topic reference is invalid.",
  );
  const [loading, setLoading] = React.useState(Boolean(topicId));
  const requestVersion = React.useRef(0);
  const load = React.useCallback(async () => {
    if (!topicId) return;
    const version = ++requestVersion.current;
    setDetail(null);
    setError(null);
    setLoading(true);
    try {
      const loaded = await fetchMasteryTopic(topicId, studentId ?? undefined);
      if (requestVersion.current === version) setDetail(loaded);
    } catch (reason) {
      if (requestVersion.current === version) {
        setDetail(null);
        setError(
          reason instanceof Error
            ? reason.message
            : "Topic mastery could not be loaded.",
        );
      }
    } finally {
      if (requestVersion.current === version) setLoading(false);
    }
  }, [studentId, topicId]);
  React.useEffect(() => {
    const version = ++requestVersion.current;
    if (!topicId) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- route changes must clear the prior topic before requesting the next one.
      setDetail(null);
      setLoading(false);
      setError("This topic reference is invalid.");
      return;
    }
    let current = true;
    setDetail(null);
    setError(null);
    setLoading(true);
    void fetchMasteryTopic(topicId, studentId ?? undefined).then(
      (loaded) => {
        if (current && requestVersion.current === version) {
          setDetail(loaded);
          setLoading(false);
        }
      },
      (reason: unknown) => {
        if (current && requestVersion.current === version) {
          setDetail(null);
          setError(
            reason instanceof Error
              ? reason.message
              : "Topic mastery could not be loaded.",
          );
          setLoading(false);
        }
      },
    );
    return () => {
      current = false;
    };
  }, [studentId, topicId]);

  const backHref = studentId ? `/students/${studentId}` : "/topics";
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
      <Box sx={{ maxWidth: 980, mx: "auto" }}>
        <Button
          component={Link}
          href={backHref}
          startIcon={<ArrowBackOutlinedIcon aria-hidden="true" />}
          sx={{
            mb: 1.5,
            color: "#6F675E",
            textTransform: "none",
            fontWeight: 600,
          }}
        >
          Back to {studentId ? "student" : "topics"}
        </Button>
        {loading ? (
          <Card
            aria-label="Loading topic mastery"
            variant="outlined"
            sx={{ borderColor: "#EBE4D9", bgcolor: "#FFFDFA", p: 2.5 }}
          >
            <Skeleton height={42} width="48%" />
            <Skeleton height={12} sx={{ mt: 2 }} />
          </Card>
        ) : null}
        {error ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{ borderColor: "#F0DCD4", bgcolor: "#FDF6F3", p: 2.5 }}
          >
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 26,
                mb: 0.75,
              }}
            >
              Topic could not be loaded
            </Typography>
            <Typography sx={{ color: "#6F675E", fontSize: 13.5, mb: 1.5 }}>
              {error}
            </Typography>
            <Button
              onClick={() => void load()}
              variant="outlined"
              sx={{
                borderColor: "#E4DCD0",
                color: "#2A2622",
                textTransform: "none",
              }}
            >
              Try again
            </Button>
          </Card>
        ) : null}
        {detail ? (
          <Box sx={{ display: "grid", gap: 2 }}>
            <Card
              component="section"
              variant="outlined"
              sx={{
                borderColor: "#EBE4D9",
                bgcolor: "#FFFDFA",
                borderRadius: "14px",
                p: { xs: 2, sm: 3 },
              }}
            >
              <Typography
                sx={{
                  color: "#A09488",
                  fontSize: 10.5,
                  fontWeight: 700,
                  letterSpacing: ".1em",
                  mb: 0.75,
                }}
              >
                {detail.node.topicCode}
              </Typography>
              <Box
                sx={{
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "baseline",
                  justifyContent: "space-between",
                  gap: 1.25,
                }}
              >
                <Typography
                  component="h1"
                  sx={{
                    fontFamily: "'Playfair Display', Georgia, serif",
                    fontSize: { xs: 30, sm: 38 },
                    fontWeight: 500,
                  }}
                >
                  {detail.node.topicName}
                </Typography>
                <Chip
                  label={detail.node.status.replaceAll("_", " ")}
                  size="small"
                  sx={{
                    bgcolor: "#F0EAE0",
                    color: "#6F675E",
                    fontWeight: 700,
                    fontSize: 10,
                  }}
                />
              </Box>
              <Box
                sx={{
                  mt: 2.5,
                  display: "flex",
                  alignItems: "center",
                  gap: 1.5,
                }}
              >
                <Typography
                  aria-label={`${Math.round(detail.node.score)} percent mastery`}
                  sx={{
                    fontSize: 27,
                    fontWeight: 700,
                    fontVariantNumeric: "tabular-nums",
                  }}
                >
                  {Math.round(detail.node.score)}%
                </Typography>
                <Box sx={{ flex: 1 }}>
                  <LinearProgress
                    aria-label={`${detail.node.topicName} mastery`}
                    variant="determinate"
                    value={detail.node.score}
                    sx={{
                      height: 8,
                      borderRadius: 20,
                      bgcolor: "#F0EAE0",
                      ".MuiLinearProgress-bar": {
                        bgcolor: barColour(detail.node.score),
                        borderRadius: 20,
                      },
                    }}
                  />
                </Box>
              </Box>
              <Typography sx={{ color: "#6F675E", fontSize: 12.5, mt: 1.1 }}>
                {detail.node.attemptCount === 1
                  ? "1 approved attempt"
                  : `${detail.node.attemptCount} approved attempts`}
              </Typography>
            </Card>
            <Card
              component="section"
              aria-labelledby="mastery-history-heading"
              variant="outlined"
              sx={{
                borderColor: "#EBE4D9",
                bgcolor: "#FFFDFA",
                borderRadius: "14px",
                p: { xs: 2, sm: 2.5 },
              }}
            >
              <Typography
                id="mastery-history-heading"
                component="h2"
                sx={{
                  fontFamily: "'Playfair Display', Georgia, serif",
                  fontSize: 22,
                  fontWeight: 500,
                  mb: 1.25,
                }}
              >
                Approved evidence history
              </Typography>
              {detail.history.length ? (
                <Box sx={{ display: "grid", gap: 1 }}>
                  {detail.history.map((item, index) => (
                    <Box
                      key={`${item.occurredAt ?? "history"}-${index}`}
                      sx={{
                        display: "flex",
                        flexWrap: "wrap",
                        justifyContent: "space-between",
                        gap: 1,
                        py: 1,
                        borderBottom:
                          index === detail.history.length - 1
                            ? 0
                            : "1px solid #F0EAE0",
                      }}
                    >
                      <Box>
                        <Typography sx={{ fontSize: 13, fontWeight: 600 }}>
                          {item.reason}
                        </Typography>
                        <Typography
                          sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.25 }}
                        >
                          {item.occurredAt
                            ? new Date(item.occurredAt).toLocaleDateString()
                            : "Date not recorded"}
                        </Typography>
                      </Box>
                      <Typography
                        sx={{
                          fontSize: 13,
                          fontWeight: 700,
                          fontVariantNumeric: "tabular-nums",
                        }}
                      >
                        {Math.round(item.previousScore)}% →{" "}
                        {Math.round(item.newScore)}%
                      </Typography>
                    </Box>
                  ))}
                </Box>
              ) : (
                <Typography
                  role="status"
                  sx={{ color: "#6F675E", fontSize: 13 }}
                >
                  No approved evidence has been recorded for this topic yet.
                </Typography>
              )}
            </Card>
          </Box>
        ) : null}
      </Box>
    </Box>
  );
}
