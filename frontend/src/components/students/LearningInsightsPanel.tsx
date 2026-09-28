"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";

import {
  fetchLearningProfile,
  type LearningProfile,
} from "@/services/insights";

function loadErrorMessage(reason: unknown) {
  return reason instanceof Error
    ? reason.message
    : "Learning insights could not be loaded.";
}

export default function LearningInsightsPanel({
  studentId,
  loadProfile = fetchLearningProfile,
}: {
  studentId?: number;
  loadProfile?: (studentId?: number) => Promise<LearningProfile>;
}) {
  const [profile, setProfile] = React.useState<LearningProfile | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const load = React.useCallback(async () => {
    setError(null);
    try {
      setProfile(await loadProfile(studentId));
    } catch (reason) {
      setError(loadErrorMessage(reason));
    }
  }, [loadProfile, studentId]);

  React.useEffect(() => {
    let isCurrentRequest = true;

    const loadInitialProfile = async () => {
      try {
        const loadedProfile = await loadProfile(studentId);
        if (isCurrentRequest) {
          setProfile(loadedProfile);
        }
      } catch (reason) {
        if (isCurrentRequest) {
          setError(loadErrorMessage(reason));
        }
      }
    };

    void loadInitialProfile();

    return () => {
      isCurrentRequest = false;
    };
  }, [loadProfile, studentId]);

  if (!profile && !error)
    return (
      <Card
        aria-label="Loading learning insights"
        variant="outlined"
        sx={{
          borderColor: "#EBE4D9",
          bgcolor: "#FFFDFA",
          borderRadius: "14px",
          p: 2.5,
        }}
      >
        <Skeleton height={28} width="42%" />
        <Skeleton height={78} sx={{ mt: 1 }} />
      </Card>
    );
  if (error)
    return (
      <Card
        component="section"
        role="alert"
        variant="outlined"
        sx={{
          borderColor: "#F0DCD4",
          bgcolor: "#FDF6F3",
          borderRadius: "14px",
          p: 2.5,
        }}
      >
        <Typography
          component="h2"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 21,
            mb: 0.6,
          }}
        >
          Learning insights could not be loaded
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 13, mb: 1.25 }}>
          {error}
        </Typography>
        <Button
          onClick={() => void load()}
          variant="outlined"
          sx={{
            minHeight: 40,
            borderColor: "#E4DCD0",
            color: "#2A2622",
            textTransform: "none",
          }}
        >
          Try again
        </Button>
      </Card>
    );
  if (!profile) return null;
  return (
    <Card
      component="section"
      aria-labelledby="learning-insights-heading"
      variant="outlined"
      sx={{
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
        borderRadius: "14px",
        p: { xs: 2, sm: 2.5 },
        boxShadow: "none",
      }}
    >
      <Typography
        sx={{
          color: "#A09488",
          fontSize: 10,
          fontWeight: 700,
          letterSpacing: ".11em",
          mb: 0.65,
        }}
      >
        EVIDENCE-LED PRIORITIES
      </Typography>
      <Typography
        id="learning-insights-heading"
        component="h2"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: 22,
          fontWeight: 500,
          mb: 0.55,
        }}
      >
        Learning profile
      </Typography>
      <Typography
        sx={{ color: "#6F675E", fontSize: 12.5, lineHeight: 1.55, mb: 1.5 }}
      >
        Derived from tutor-approved mastery and history. Suggestions are not
        saved decisions.
      </Typography>
      {profile.findings.length ? (
        <Box sx={{ display: "grid", gap: 1.15 }}>
          {profile.findings.map((finding) => (
            <Box
              key={`${finding.type}-${finding.evidence[0].topicId}`}
              sx={{
                p: 1.4,
                border: "1px solid #EFE8DE",
                borderRadius: "10px",
                bgcolor: "#FBF9F5",
              }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 700 }}>
                {finding.title}
              </Typography>
              <Typography
                sx={{
                  color: "#5A544C",
                  fontSize: 12.5,
                  lineHeight: 1.55,
                  mt: 0.35,
                }}
              >
                {finding.summary}
              </Typography>
              <Typography
                sx={{
                  color: "#9E3A24",
                  fontSize: 12,
                  fontWeight: 600,
                  lineHeight: 1.5,
                  mt: 0.65,
                }}
              >
                {finding.suggestedAction}
              </Typography>
              <Typography sx={{ color: "#8B837A", fontSize: 10.5, mt: 0.8 }}>
                Evidence:{" "}
                {finding.evidence
                  .map(
                    (item) => `${item.topicName} · ${Math.round(item.score)}%`,
                  )
                  .join("; ")}
              </Typography>
            </Box>
          ))}
        </Box>
      ) : (
        <Typography role="status" sx={{ color: "#6F675E", fontSize: 13 }}>
          No approved evidence is available for learning priorities yet.
        </Typography>
      )}
    </Card>
  );
}
