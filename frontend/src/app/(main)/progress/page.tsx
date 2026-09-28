"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";

import MasteryMap from "@/components/mastery/MasteryMap";
import {
  deriveMasteryMetrics,
  fetchMasteryMap,
  type MasteryMapData,
} from "@/services/mastery";

export default function ProgressPage() {
  const [data, setData] = React.useState<MasteryMapData | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const load = React.useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      setData(await fetchMasteryMap());
    } catch (reason) {
      setData(null);
      setError(
        reason instanceof Error
          ? reason.message
          : "Progress could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  React.useEffect(() => {
    let current = true;
    void fetchMasteryMap().then(
      (loaded) => {
        if (current) {
          setData(loaded);
          setLoading(false);
        }
      },
      (reason: unknown) => {
        if (current) {
          setData(null);
          setError(
            reason instanceof Error
              ? reason.message
              : "Progress could not be loaded.",
          );
          setLoading(false);
        }
      },
    );
    return () => {
      current = false;
    };
  }, []);
  const metrics = data ? deriveMasteryMetrics(data) : null;

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
      <Box sx={{ maxWidth: 1420, mx: "auto" }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          LEARNING PROGRESS
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 40 },
            fontWeight: 500,
            mb: 0.65,
          }}
        >
          My mastery map
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 14, mb: 2.5 }}>
          Your topic progress is based only on approved learning results.
        </Typography>
        {loading ? (
          <Card
            aria-label="Loading mastery map"
            variant="outlined"
            sx={{ borderColor: "#EBE4D9", bgcolor: "#FFFDFA", p: 2.5 }}
          >
            <Skeleton height={34} width="34%" />
            <Skeleton height={92} sx={{ mt: 1 }} />
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
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
                mb: 0.75,
              }}
            >
              Progress could not be loaded
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
        {data && metrics ? (
          <Box sx={{ display: "grid", gap: 2 }}>
            <Box
              component="section"
              aria-label="Progress summary"
              sx={{
                display: "grid",
                gridTemplateColumns: {
                  xs: "repeat(2, minmax(0, 1fr))",
                  md: "repeat(4, minmax(0, 1fr))",
                },
                gap: 1.25,
              }}
            >
              <Metric
                label="Overall mastery"
                value={
                  data.overallScore === null
                    ? "Not calculated"
                    : `${Math.round(data.overallScore)}%`
                }
              />
              <Metric
                label="Approved attempts"
                value={metrics.approvedAttempts.toString()}
              />
              <Metric
                label="Topics mastered"
                value={`${metrics.masteredTopics} / ${metrics.totalTopics}`}
              />
              <Metric
                label="Need revision"
                value={metrics.needsRevisionTopics.toString()}
                detail={`${metrics.attemptedTopics} topics with approved evidence`}
              />
            </Box>
            <MasteryMap data={data} heading="Your topic mastery" />
          </Box>
        ) : null}
      </Box>
    </Box>
  );
}

function Metric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail?: string;
}) {
  return (
    <Card
      variant="outlined"
      sx={{
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
        borderRadius: "12px",
        p: { xs: 1.5, sm: 1.75 },
        minWidth: 0,
      }}
    >
      <Typography
        sx={{
          color: "#8B837A",
          fontSize: 10.5,
          fontWeight: 700,
          letterSpacing: ".06em",
          textTransform: "uppercase",
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontSize: { xs: 21, sm: 25 },
          fontWeight: 700,
          fontVariantNumeric: "tabular-nums",
          mt: 0.45,
        }}
      >
        {value}
      </Typography>
      {detail ? (
        <Typography sx={{ color: "#6F675E", fontSize: 10.5, mt: 0.35 }}>
          {detail}
        </Typography>
      ) : null}
    </Card>
  );
}
