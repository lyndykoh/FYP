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

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderColor: "#EBE4D9",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
  p: 2.5,
  boxShadow: "none",
} as const;

const dimensionLabels = {
  CONCEPT: "Concept",
  KEYWORD: "Keywords",
  EXPRESSION: "Expression",
  APPLICATION: "Application",
} as const;

type ProfileTopic = LearningProfile["strengths"][number];

function errorMessage(reason: unknown): string {
  return reason instanceof Error
    ? reason.message
    : "Subject profile could not be loaded.";
}

function TopicSummary({
  title,
  topics,
  emptyMessage,
  accentColor,
  scoreColor = "#6F675E",
  showAttempts = false,
}: {
  title: string;
  topics: ProfileTopic[];
  emptyMessage: string;
  accentColor: string;
  scoreColor?: string;
  showAttempts?: boolean;
}) {
  return (
    <Card
      component="section"
      variant="outlined"
      sx={{ ...card, flex: "1 1 320px" }}
    >
      <Typography
        component="h2"
        sx={{ fontFamily: serif, fontSize: 22, mb: 1.25 }}
      >
        {title}
      </Typography>
      {topics.length ? (
        <Box sx={{ display: "grid", gap: 1 }}>
          {topics.map((topic) => (
            <Box
              key={topic.topicId}
              sx={{ borderLeft: `2px solid ${accentColor}`, pl: 1.25 }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 600 }}>
                {topic.topicName}
              </Typography>
              <Typography sx={{ color: scoreColor, fontSize: 12 }}>
                {Math.round(topic.score)}% mastery
                {showAttempts
                  ? ` · ${topic.attemptCount} approved attempts`
                  : ""}
              </Typography>
            </Box>
          ))}
        </Box>
      ) : (
        <Typography role="status" sx={{ color: "#6F675E", fontSize: 13 }}>
          {emptyMessage}
        </Typography>
      )}
    </Card>
  );
}

function LoadingProfile() {
  return (
    <Card aria-label="Loading subject profile" variant="outlined" sx={card}>
      <Skeleton height={34} width="38%" />
      <Skeleton height={130} sx={{ mt: 1 }} />
    </Card>
  );
}

export default function SubjectProfilePage() {
  const [profile, setProfile] = React.useState<LearningProfile | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    setError(null);

    try {
      setProfile(await fetchLearningProfile());
    } catch (reason) {
      setError(errorMessage(reason));
    }
  }, []);

  React.useEffect(() => {
    let isCurrent = true;

    void fetchLearningProfile().then(
      (loadedProfile) => {
        if (isCurrent) setProfile(loadedProfile);
      },
      (reason: unknown) => {
        if (isCurrent) setError(errorMessage(reason));
      },
    );

    return () => {
      isCurrent = false;
    };
  }, []);

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
          SUBJECT PROFILE
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: serif,
            fontSize: { xs: 32, sm: 40 },
            fontWeight: 500,
            mb: 0.65,
          }}
        >
          My learning profile
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 14, mb: 2.5 }}>
          A factual summary of tutor-approved learning evidence.
        </Typography>

        {!profile && !error ? <LoadingProfile /> : null}

        {error ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{ ...card, borderColor: "#F0DCD4", bgcolor: "#FDF6F3" }}
          >
            <Typography
              component="h2"
              sx={{ fontFamily: serif, fontSize: 22, mb: 0.7 }}
            >
              Subject profile could not be loaded
            </Typography>
            <Typography sx={{ color: "#6F675E", fontSize: 13, mb: 1.2 }}>
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

        {profile ? <SubjectProfileContent profile={profile} /> : null}
      </Box>
    </Box>
  );
}

function SubjectProfileContent({ profile }: { profile: LearningProfile }) {
  return (
    <Box
      sx={{
        display: "flex",
        flexWrap: "wrap",
        gap: 2.5,
        alignItems: "flex-start",
      }}
    >
      <TopicSummary
        title="Strengths"
        topics={profile.strengths}
        emptyMessage="Strengths will appear after more approved evidence."
        accentColor="#DCE4DC"
        showAttempts
      />
      <TopicSummary
        title="Topics to focus on"
        topics={profile.growthAreas}
        emptyMessage="No focus topics are identified yet."
        accentColor="#EDD9D2"
        scoreColor="#9E3A24"
      />
      <TopicSummary
        title="Making progress"
        topics={profile.improvements}
        emptyMessage="Improvement will appear after an approved mastery update."
        accentColor="#D8E4EF"
        scoreColor="#4B667C"
        showAttempts
      />

      <Card
        component="section"
        aria-labelledby="learning-dimensions-heading"
        variant="outlined"
        sx={{ ...card, flex: "1 1 100%" }}
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
          TUTOR-CONFIRMED DIAGNOSTICS
        </Typography>
        <Typography
          id="learning-dimensions-heading"
          component="h2"
          sx={{ fontFamily: serif, fontSize: 22, mb: 1.25 }}
        >
          Learning dimensions
        </Typography>
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))",
            gap: 1,
          }}
        >
          {profile.dimensions.map((dimension) => (
            <Box
              key={dimension.category}
              sx={{
                p: 1.35,
                border: "1px solid #EFE8DE",
                borderRadius: "10px",
                bgcolor: "#FBF9F5",
              }}
            >
              <Typography sx={{ fontSize: 13, fontWeight: 700 }}>
                {dimensionLabels[dimension.category]}
              </Typography>
              <Typography sx={{ color: "#5A544C", fontSize: 12.5, mt: 0.3 }}>
                {dimension.evidenceCount
                  ? `${dimension.evidenceCount} tutor-confirmed diagnostic ${dimension.evidenceCount === 1 ? "record" : "records"}.`
                  : "No tutor-confirmed diagnostic evidence yet."}
              </Typography>
              {dimension.evidence.length ? (
                <Typography sx={{ color: "#8B837A", fontSize: 11, mt: 0.7 }}>
                  Latest evidence: {dimension.evidence[0].topicName} ·{" "}
                  {dimension.evidence[0].sourceReason}
                </Typography>
              ) : null}
            </Box>
          ))}
        </Box>
      </Card>

      <Card
        component="section"
        aria-labelledby="subject-priorities-heading"
        variant="outlined"
        sx={{ ...card, flex: "1 1 100%" }}
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
          id="subject-priorities-heading"
          component="h2"
          sx={{ fontFamily: serif, fontSize: 22, mb: 1.25 }}
        >
          What to practise next
        </Typography>
        {profile.findings.length ? (
          <Box sx={{ display: "grid", gap: 1.1 }}>
            {profile.findings.map((finding) => (
              <Box
                key={`${finding.type}-${finding.evidence[0].topicId}`}
                sx={{
                  p: 1.35,
                  border: "1px solid #EFE8DE",
                  borderRadius: "10px",
                  bgcolor: "#FBF9F5",
                }}
              >
                <Typography sx={{ fontSize: 13, fontWeight: 700 }}>
                  {finding.title}
                </Typography>
                <Typography sx={{ color: "#5A544C", fontSize: 12.5, mt: 0.3 }}>
                  {finding.summary}
                </Typography>
                <Typography
                  sx={{
                    color: "#9E3A24",
                    fontSize: 12,
                    fontWeight: 600,
                    mt: 0.6,
                  }}
                >
                  {finding.suggestedAction}
                </Typography>
                <Typography sx={{ color: "#8B837A", fontSize: 10.5, mt: 0.75 }}>
                  Evidence:{" "}
                  {finding.evidence
                    .map(
                      (item) =>
                        `${item.topicName} · ${Math.round(item.score)}%`,
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
    </Box>
  );
}
