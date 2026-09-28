"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import UploadFileOutlinedIcon from "@mui/icons-material/UploadFileOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import TutorNotes from "@/components/students/TutorNotes";

import {
  fetchTutorStudentProfile,
  type StudentProfileTopic,
  type TutorStudentProfile,
} from "@/services/students";

export interface StudentProfileProps {
  studentId: number;
  loadProfile?: (studentId: number) => Promise<TutorStudentProfile>;
}

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderColor: "#EBE4D9",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
  boxShadow: "none",
} as const;
const avatarBackgrounds = [
  "#D8B384",
  "#C6D0C4",
  "#E3C3B4",
  "#CFC0D6",
  "#D9CBA8",
  "#BFD0D6",
];

function initials(fullName: string) {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function percent(value: number | null) {
  return value === null ? "—" : `${Math.round(value)}%`;
}

function dateLabel(value: string | null) {
  if (!value) return "Not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Not recorded"
    : new Intl.DateTimeFormat("en", {
        month: "short",
        day: "numeric",
        year: "numeric",
      }).format(date);
}

function masteryColour(score: number) {
  return score < 55 ? "#B4573F" : score < 72 ? "#D8B384" : "#93A896";
}

function EmptySection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Box
      sx={{
        p: 2.25,
        border: "1px dashed #DCCFBE",
        borderRadius: "12px",
        bgcolor: "#FBF9F5",
      }}
    >
      <Typography
        sx={{ fontFamily: serif, fontSize: 18, fontWeight: 500, mb: 0.55 }}
      >
        {title}
      </Typography>
      <Typography sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}>
        {children}
      </Typography>
    </Box>
  );
}

function TopicRow({ topic }: { topic: StudentProfileTopic }) {
  const focus = topic.score < 55;
  return (
    <Box
      sx={{
        border: focus ? "1px solid #F0DCD4" : "1px solid #EFE8DE",
        bgcolor: focus ? "#FDF6F3" : "#FFFDFA",
        borderRadius: "10px",
        p: "13px 16px",
        display: "flex",
        alignItems: "center",
        gap: 1.75,
      }}
    >
      <Typography
        sx={{
          width: 44,
          flex: "0 0 auto",
          color: focus ? "#9E3A24" : "#4A443D",
          fontSize: 14,
          fontWeight: 600,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        {percent(topic.score)}
      </Typography>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            gap: 0.75,
            mb: 0.75,
          }}
        >
          <Typography sx={{ minWidth: 0, fontSize: 13, fontWeight: 500 }}>
            {topic.topicName}
          </Typography>
          {focus ? (
            <Chip
              label="FOCUS AREA"
              size="small"
              sx={{
                height: 21,
                bgcolor: "#F1D9D1",
                color: "#9E3A24",
                fontSize: 9.5,
                fontWeight: 700,
                letterSpacing: ".05em",
              }}
            />
          ) : null}
        </Box>
        <LinearProgress
          aria-label={`${topic.topicName} mastery ${percent(topic.score)}`}
          variant="determinate"
          value={topic.score}
          sx={{
            height: 5,
            borderRadius: 20,
            bgcolor: "#F0EAE0",
            ".MuiLinearProgress-bar": {
              bgcolor: masteryColour(topic.score),
              borderRadius: 20,
            },
          }}
        />
      </Box>
    </Box>
  );
}

function ProfileSkeleton() {
  return (
    <Box
      data-testid="student-profile-skeleton"
      aria-label="Loading student profile"
      sx={{ display: "grid", gap: 2 }}
    >
      <Card variant="outlined" sx={{ ...card, p: 3 }}>
        <Skeleton
          variant="rounded"
          height={68}
          width="70%"
          sx={{ bgcolor: "#F0EAE0" }}
        />
      </Card>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
        }}
      >
        {[1, 2, 3, 4].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={115}
            sx={{ bgcolor: "#F0EAE0", borderRadius: "14px" }}
          />
        ))}
      </Box>
      <Skeleton
        variant="rounded"
        height={290}
        sx={{ bgcolor: "#F0EAE0", borderRadius: "14px" }}
      />
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
  detail: string;
}) {
  return (
    <Card variant="outlined" sx={{ ...card, p: 2.15 }}>
      <Typography
        sx={{
          color: "#A09488",
          fontSize: 10,
          fontWeight: 600,
          letterSpacing: ".11em",
          mb: 1,
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontFamily: serif,
          fontSize: 29,
          fontWeight: 500,
          lineHeight: 1,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        {value}
      </Typography>
      <Typography
        sx={{ color: "#8B837A", fontSize: 11.5, lineHeight: 1.45, mt: 1 }}
      >
        {detail}
      </Typography>
    </Card>
  );
}

function ProfileContent({ profile }: { profile: TutorStudentProfile }) {
  const displayMastery = [...profile.mastery].sort(
    (left, right) =>
      right.score - left.score || left.topicName.localeCompare(right.topicName),
  );
  const avatarIndex = profile.id % avatarBackgrounds.length;
  const recentImprovement =
    profile.history
      .filter((item) => item.previousScore !== null && item.newScore !== null)
      .map((item) => item.newScore! - item.previousScore!)
      .at(0) ?? null;
  const generationClassId = profile.classes[0]?.id;
  return (
    <>
      <Card
        component="section"
        aria-label="Student profile summary"
        variant="outlined"
        sx={{
          ...card,
          p: { xs: 2.25, sm: 3 },
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 2,
          mb: 2,
        }}
      >
        <Box
          aria-hidden="true"
          sx={{
            width: 66,
            height: 66,
            borderRadius: "50%",
            flex: "0 0 auto",
            bgcolor: avatarBackgrounds[avatarIndex],
            display: "grid",
            placeItems: "center",
            fontFamily: serif,
            fontSize: 24,
            fontWeight: 600,
            color: "#3A332C",
          }}
        >
          {initials(profile.fullName)}
        </Box>
        <Box sx={{ flex: "1 1 260px", minWidth: 0 }}>
          <Typography
            component="h1"
            sx={{
              fontFamily: serif,
              fontSize: { xs: 31, sm: 36 },
              fontWeight: 500,
              lineHeight: 1.1,
              letterSpacing: "-.02em",
            }}
          >
            {profile.fullName}
          </Typography>
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.65, mt: 1 }}>
            {profile.classes.length ? (
              profile.classes.map((item) => (
                <Chip
                  key={item.id}
                  label={`${item.level} · ${item.subject}`}
                  size="small"
                  sx={{
                    height: 24,
                    bgcolor: "#F4EFE6",
                    color: "#6F675E",
                    fontSize: 10.5,
                    fontWeight: 500,
                  }}
                />
              ))
            ) : (
              <Typography sx={{ color: "#8B837A", fontSize: 13 }}>
                No class memberships yet
              </Typography>
            )}
          </Box>
        </Box>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
          <Button
            component={Link}
            href={`/upload?studentId=${profile.id}${profile.classes.length === 1 ? `&classId=${profile.classes[0].id}` : ""}`}
            startIcon={<UploadFileOutlinedIcon aria-hidden="true" />}
            sx={{
              minHeight: 42,
              borderRadius: "10px",
              bgcolor: "#E08A72",
              color: "#1B1917",
              textTransform: "none",
              fontWeight: 600,
              px: 2,
              "&:hover": { bgcolor: "#D2795F" },
            }}
          >
            Upload completed worksheet
          </Button>
          {generationClassId ? (
            <Button
              component={Link}
              href={`/tutor/worksheets/new?classId=${generationClassId}&studentId=${profile.id}`}
              sx={{
                minHeight: 42,
                borderRadius: "10px",
                bgcolor: "#9E3A24",
                color: "#FFFDFA",
                textTransform: "none",
                fontWeight: 600,
                px: 2,
                "&:hover": { bgcolor: "#8A3120" },
              }}
            >
              Generate worksheet
            </Button>
          ) : (
            <Button
              disabled
              aria-describedby="generate-worksheet-help"
              sx={{
                minHeight: 42,
                borderRadius: "10px",
                bgcolor: "#EDE6DB",
                color: "#B5AA9C",
                textTransform: "none",
                fontWeight: 600,
                px: 2,
              }}
            >
              Generate worksheet
            </Button>
          )}
          <Button
            component={Link}
            href={`/students/${profile.id}/edit`}
            startIcon={<EditOutlinedIcon aria-hidden="true" />}
            sx={{
              minHeight: 42,
              border: "1px solid #E4DCD0",
              borderRadius: "10px",
              bgcolor: "#FFFDFA",
              color: "#2A2622",
              textTransform: "none",
              fontWeight: 500,
              px: 2,
              "&:hover": { bgcolor: "#F4EFE6" },
            }}
          >
            Edit student
          </Button>
        </Box>
        {!generationClassId ? (
          <Typography
            id="generate-worksheet-help"
            sx={{ width: "100%", color: "#8B837A", fontSize: 11.5 }}
          >
            Add this student to a class before generating an individual
            worksheet.
          </Typography>
        ) : null}
      </Card>

      <Box
        component="section"
        aria-label="Learning metrics"
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
          mb: 2.5,
        }}
      >
        <Metric
          label="OVERALL MASTERY"
          value={percent(profile.metrics.averageMastery)}
          detail={
            profile.metrics.topicCount
              ? `Across ${profile.metrics.topicCount} topics`
              : "No mastery records yet"
          }
        />
        <Metric
          label="LEARNING ATTEMPTS"
          value={String(profile.metrics.totalAttempts)}
          detail={
            profile.metrics.totalAttempts === 1
              ? "Recorded attempt"
              : "Recorded attempts"
          }
        />
        {profile.tutorOnly ? (
          <Metric
            label="WORKSHEETS WITH APPROVED RESULTS"
            value={String(profile.tutorOnly.approvedWorksheetCount)}
            detail={
              profile.tutorOnly.approvedWorksheetCount === 1
                ? "One worksheet has an approved result"
                : "Worksheets with approved results"
            }
          />
        ) : null}
        <Metric
          label="RECENT IMPROVEMENT"
          value={
            recentImprovement === null
              ? "—"
              : `${recentImprovement >= 0 ? "+" : ""}${Math.round(recentImprovement)}%`
          }
          detail={
            recentImprovement === null
              ? "No scored change recorded"
              : "Latest recorded topic change"
          }
        />
        <Metric
          label="FOCUS AREAS"
          value={String(profile.learningProfile.focusAreas.length)}
          detail={
            profile.learningProfile.focusAreas.length
              ? "Topics needing practice"
              : "No focus topics identified"
          }
        />
        <Metric
          label="LAST UPDATED"
          value={
            profile.metrics.lastCalculatedAt
              ? dateLabel(profile.metrics.lastCalculatedAt)
              : "—"
          }
          detail={
            profile.metrics.lastCalculatedAt
              ? "Latest mastery calculation"
              : "No calculation yet"
          }
        />
      </Box>

      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          gap: 2.5,
          alignItems: "flex-start",
        }}
      >
        <Box sx={{ flex: "1 1 460px", minWidth: 0, display: "grid", gap: 2.5 }}>
          <Card
            component="section"
            aria-labelledby="student-mastery-heading"
            variant="outlined"
            sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
          >
            <Typography
              id="student-mastery-heading"
              component="h2"
              sx={{
                fontFamily: serif,
                fontSize: 22,
                fontWeight: 500,
                mb: 0.55,
              }}
            >
              Topic mastery
            </Typography>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 12.5,
                lineHeight: 1.55,
                mb: 1.75,
              }}
            >
              Scores are calculated from recorded learning evidence.
            </Typography>
            {displayMastery.length ? (
              <Box sx={{ display: "grid", gap: 1.15 }}>
                {displayMastery.map((topic) => (
                  <TopicRow key={topic.topicId} topic={topic} />
                ))}
              </Box>
            ) : (
              <EmptySection title="No mastery data yet">
                Mastery will appear after completed learning evidence is
                calculated.
              </EmptySection>
            )}
          </Card>

          <Card
            component="section"
            aria-labelledby="student-history-heading"
            variant="outlined"
            sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
          >
            <Typography
              id="student-history-heading"
              component="h2"
              sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500, mb: 1.5 }}
            >
              Mastery history
            </Typography>
            {profile.history.length ? (
              <Box sx={{ display: "grid", gap: 1 }}>
                {profile.history.slice(0, 8).map((item, index) => (
                  <Box
                    key={`${item.topicId}-${item.occurredAt ?? index}`}
                    sx={{
                      display: "flex",
                      flexWrap: "wrap",
                      alignItems: "baseline",
                      justifyContent: "space-between",
                      gap: 1,
                      py: 1.1,
                      borderBottom:
                        index === Math.min(profile.history.length, 8) - 1
                          ? 0
                          : "1px solid #F0EAE0",
                    }}
                  >
                    <Box>
                      <Typography sx={{ fontSize: 13, fontWeight: 500 }}>
                        {item.topicName}
                      </Typography>
                      <Typography
                        sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.3 }}
                      >
                        {item.reason ?? "Mastery recalculated"}
                      </Typography>
                    </Box>
                    <Box sx={{ textAlign: "right" }}>
                      <Typography
                        sx={{
                          fontSize: 13,
                          fontWeight: 600,
                          fontVariantNumeric: "tabular-nums",
                        }}
                      >
                        {percent(item.previousScore)} → {percent(item.newScore)}
                      </Typography>
                      <Typography
                        sx={{ color: "#A09488", fontSize: 10.5, mt: 0.3 }}
                      >
                        {dateLabel(item.occurredAt)}
                      </Typography>
                    </Box>
                  </Box>
                ))}
              </Box>
            ) : (
              <EmptySection title="No mastery history yet">
                Updates will appear after a topic score changes.
              </EmptySection>
            )}
          </Card>
        </Box>

        <Box sx={{ flex: "0 1 320px", minWidth: 0, display: "grid", gap: 2.5 }}>
          <Card
            component="section"
            aria-labelledby="student-learning-profile-heading"
            variant="outlined"
            sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
          >
            <Typography
              id="student-learning-profile-heading"
              component="h2"
              sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500, mb: 1.5 }}
            >
              Learning profile
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
              <Box sx={{ flex: "1 1 200px" }}>
                <Typography
                  sx={{
                    color: "#5C7A63",
                    fontSize: 10,
                    fontWeight: 700,
                    letterSpacing: ".1em",
                    mb: 1,
                  }}
                >
                  STRENGTHS
                </Typography>
                {profile.learningProfile.strengths.length ? (
                  <Box sx={{ display: "grid", gap: 1 }}>
                    {profile.learningProfile.strengths
                      .slice(0, 3)
                      .map((item) => (
                        <Box
                          key={item.topicId}
                          sx={{ borderLeft: "2px solid #DCE4DC", pl: 1.25 }}
                        >
                          <Typography sx={{ fontSize: 12.5, fontWeight: 500 }}>
                            {item.topicName}
                          </Typography>
                          <Typography
                            sx={{ color: "#6F675E", fontSize: 11.5, mt: 0.2 }}
                          >
                            {percent(item.score)} mastery
                          </Typography>
                        </Box>
                      ))}
                  </Box>
                ) : (
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.55 }}
                  >
                    Strengths will appear with more evidence.
                  </Typography>
                )}
              </Box>
              <Box sx={{ flex: "1 1 200px" }}>
                <Typography
                  sx={{
                    color: "#9E3A24",
                    fontSize: 10,
                    fontWeight: 700,
                    letterSpacing: ".1em",
                    mb: 1,
                  }}
                >
                  GROWTH AREAS
                </Typography>
                {profile.learningProfile.focusAreas.length ? (
                  <Box sx={{ display: "grid", gap: 1 }}>
                    {profile.learningProfile.focusAreas
                      .slice(0, 3)
                      .map((item) => (
                        <Box
                          key={item.topicId}
                          sx={{ borderLeft: "2px solid #EDD9D2", pl: 1.25 }}
                        >
                          <Typography sx={{ fontSize: 12.5, fontWeight: 500 }}>
                            {item.topicName}
                          </Typography>
                          <Typography
                            sx={{ color: "#6F675E", fontSize: 11.5, mt: 0.2 }}
                          >
                            {percent(item.score)} mastery
                          </Typography>
                        </Box>
                      ))}
                  </Box>
                ) : (
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.55 }}
                  >
                    No focus areas are identified yet.
                  </Typography>
                )}
              </Box>
            </Box>
          </Card>

          <Card
            component="section"
            aria-labelledby="student-classes-heading"
            variant="outlined"
            sx={{ ...card, overflow: "hidden" }}
          >
            <Typography
              id="student-classes-heading"
              component="h2"
              sx={{
                p: "17px 20px",
                borderBottom: "1px solid #EFE8DE",
                fontFamily: serif,
                fontSize: 21,
                fontWeight: 500,
              }}
            >
              Classes
            </Typography>
            {profile.classes.length ? (
              profile.classes.map((item, index) => (
                <Button
                  key={item.id}
                  component={Link}
                  href={`/classes/${item.id}`}
                  endIcon={
                    <ArrowForwardIcon
                      aria-hidden="true"
                      sx={{ fontSize: 17 }}
                    />
                  }
                  sx={{
                    width: "100%",
                    minHeight: 64,
                    justifyContent: "space-between",
                    px: 2.25,
                    py: 1.2,
                    textAlign: "left",
                    color: "#2A2622",
                    textTransform: "none",
                    borderBottom:
                      index === profile.classes.length - 1
                        ? 0
                        : "1px solid #F0EAE0",
                    borderRadius: 0,
                    "&:hover": { bgcolor: "#FBF7F1" },
                  }}
                >
                  <Box sx={{ minWidth: 0 }}>
                    <Typography
                      sx={{
                        fontSize: 13.5,
                        fontWeight: 600,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                      }}
                    >
                      {item.className}
                    </Typography>
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.25 }}
                    >
                      {item.level} · {item.subject}
                    </Typography>
                  </Box>
                </Button>
              ))
            ) : (
              <Box sx={{ p: 2.25 }}>
                <EmptySection title="No classes assigned">
                  Add a class membership when editing this student.
                </EmptySection>
              </Box>
            )}
          </Card>

          <Card
            component="section"
            aria-labelledby="student-worksheets-heading"
            variant="outlined"
            sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
          >
            <Typography
              id="student-worksheets-heading"
              component="h2"
              sx={{
                fontFamily: serif,
                fontSize: 22,
                fontWeight: 500,
                mb: 1.25,
              }}
            >
              Assigned worksheets
            </Typography>
            {profile.worksheets.length ? (
              <Box sx={{ display: "grid", gap: 1.15 }}>
                {profile.worksheets.slice(0, 5).map((item) => (
                  <Box
                    key={item.worksheetId}
                    sx={{
                      p: 1.35,
                      border: "1px solid #EFE8DE",
                      borderRadius: "10px",
                      bgcolor: "#FBF9F5",
                    }}
                  >
                    <Typography sx={{ fontSize: 12.5, fontWeight: 600 }}>
                      {item.title}
                    </Typography>
                    <Typography
                      sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.45 }}
                    >
                      {item.assignmentType === "CLASS"
                        ? "Class assignment"
                        : "Individual assignment"}{" "}
                      · Due {dateLabel(item.dueAt)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            ) : (
              <EmptySection title="No worksheets assigned">
                Approved assignments will appear here.
              </EmptySection>
            )}
          </Card>

          {profile.tutorOnly ? (
            <>
              <Card
                component="section"
                aria-labelledby="student-tutor-records-heading"
                variant="outlined"
                sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
              >
                <Typography
                  id="student-tutor-records-heading"
                  component="h2"
                  sx={{
                    fontFamily: serif,
                    fontSize: 22,
                    fontWeight: 500,
                    mb: 1.25,
                  }}
                >
                  Tutor records
                </Typography>
                <Typography
                  sx={{
                    color: "#A09488",
                    fontSize: 10,
                    fontWeight: 600,
                    letterSpacing: ".1em",
                    mb: 0.75,
                  }}
                >
                  ACTIVE ALERTS
                </Typography>
                {profile.tutorOnly.activeAlerts.length ? (
                  <Box sx={{ display: "grid", gap: 0.75, mb: 1.5 }}>
                    {profile.tutorOnly.activeAlerts.map((item) => (
                      <Box
                        key={item.id}
                        sx={{
                          p: 1.2,
                          borderLeft: "3px solid #B4573F",
                          bgcolor: "#FDF6F3",
                        }}
                      >
                        <Typography sx={{ fontSize: 12.5, fontWeight: 600 }}>
                          {item.title}
                        </Typography>
                        <Typography sx={{ color: "#8B837A", fontSize: 11 }}>
                          {item.severity.replaceAll("_", " ")} ·{" "}
                          {dateLabel(item.createdAt)}
                        </Typography>
                      </Box>
                    ))}
                  </Box>
                ) : (
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 12.5, mb: 1.5 }}
                  >
                    No active tutor alerts.
                  </Typography>
                )}
                <Typography
                  sx={{
                    color: "#A09488",
                    fontSize: 10,
                    fontWeight: 600,
                    letterSpacing: ".1em",
                    mb: 0.75,
                  }}
                >
                  REPORTS
                </Typography>
                {profile.tutorOnly.reports.length ? (
                  <Box sx={{ display: "grid", gap: 0.75 }}>
                    {profile.tutorOnly.reports.map((item) => (
                      <Box
                        key={item.id}
                        sx={{
                          display: "flex",
                          justifyContent: "space-between",
                          gap: 1,
                          fontSize: 12.5,
                        }}
                      >
                        <Typography sx={{ fontSize: 12.5, fontWeight: 500 }}>
                          {item.reportCode}
                        </Typography>
                        <Typography sx={{ color: "#8B837A", fontSize: 11.5 }}>
                          {item.status.replaceAll("_", " ")}
                        </Typography>
                      </Box>
                    ))}
                  </Box>
                ) : (
                  <Typography sx={{ color: "#8B837A", fontSize: 12.5 }}>
                    No reports recorded.
                  </Typography>
                )}
              </Card>
              <TutorNotes studentId={profile.id} />
            </>
          ) : null}
        </Box>
      </Box>
    </>
  );
}

export default function StudentProfile({
  studentId,
  loadProfile = fetchTutorStudentProfile,
}: StudentProfileProps) {
  const validId = Number.isSafeInteger(studentId) && studentId > 0;
  const [profile, setProfile] = React.useState<TutorStudentProfile | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(
    validId ? null : "This student reference is invalid.",
  );

  const load = React.useCallback(async () => {
    if (!validId) return;
    setProfile(null);
    setError(null);
    try {
      setProfile(await loadProfile(studentId));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "The student profile could not be loaded. Please try again.",
      );
    }
  }, [loadProfile, studentId, validId]);

  React.useEffect(() => {
    if (!validId) return;
    let current = true;
    const requestProfile = async () => {
      try {
        const profileRequest = loadProfile(studentId);
        queueMicrotask(() => {
          if (current) {
            setProfile(null);
            setError(null);
          }
        });
        const loaded = await profileRequest;
        if (current) setProfile(loaded);
      } catch (reason) {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "The student profile could not be loaded. Please try again.",
          );
      }
    };
    void requestProfile();
    return () => {
      current = false;
    };
  }, [loadProfile, studentId, validId]);

  const displayError = !validId ? "This student reference is invalid." : error;
  if (!profile && !displayError) return <ProfileSkeleton />;
  if (displayError)
    return (
      <Card
        component="section"
        role="alert"
        variant="outlined"
        sx={{ ...card, maxWidth: 620, p: 3, borderLeft: "3px solid #B4573F" }}
      >
        <Typography
          component="h1"
          sx={{ fontFamily: serif, fontSize: 25, fontWeight: 500, mb: 0.75 }}
        >
          Student profile could not be loaded
        </Typography>
        <Typography
          sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
        >
          {displayError}
        </Typography>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
          <Button
            onClick={() => void load()}
            variant="outlined"
            sx={{
              minHeight: 40,
              borderColor: "#E4DCD0",
              color: "#2A2622",
              textTransform: "none",
              fontWeight: 500,
            }}
          >
            Retry loading profile
          </Button>
          <Button
            component={Link}
            href="/students"
            startIcon={<ArrowBackIcon aria-hidden="true" />}
            sx={{
              minHeight: 40,
              color: "#B4573F",
              textTransform: "none",
              fontWeight: 600,
            }}
          >
            Back to students
          </Button>
        </Box>
      </Card>
    );
  if (!profile) return null;
  return <ProfileContent profile={profile} />;
}
