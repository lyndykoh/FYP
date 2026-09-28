"use client";

import * as React from "react";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import AssignmentOutlinedIcon from "@mui/icons-material/AssignmentOutlined";
import FileUploadOutlinedIcon from "@mui/icons-material/FileUploadOutlined";
import InsightsOutlinedIcon from "@mui/icons-material/InsightsOutlined";
import TrendingUpOutlinedIcon from "@mui/icons-material/TrendingUpOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useRouter } from "next/navigation";
import {
  fetchStudentDashboard,
  type StudentDashboardData,
  type StudentDashboardTopic,
} from "@/services/student-dashboard";

export interface StudentDashboardProps {
  loadDashboard?: () => Promise<StudentDashboardData>;
  navigate?: (href: string) => void;
}
const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;
const button = {
  minHeight: 42,
  textTransform: "none",
  fontSize: 13.5,
  fontWeight: 600,
  borderRadius: "10px",
  "&:focus-visible": { outline: "3px solid #E08A72", outlineOffset: 2 },
} as const;
function masteryColour(score: number): string {
  return score < 55 ? "#B4573F" : score < 72 ? "#D8B384" : "#93A896";
}
function statusLabel(status: StudentDashboardTopic["status"]): string {
  return status
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
function dateTimeLabel(value: string | null): string | null {
  if (!value) return null;
  const parsed = new Date(`${value}Z`);
  return Number.isNaN(parsed.getTime())
    ? null
    : new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "numeric",
      }).format(parsed);
}
function dashboardMetrics(metrics: StudentDashboardData["metrics"]) {
  return [
    {
      label: "Overall mastery",
      value:
        metrics.overallMastery === null
          ? "—"
          : `${Math.round(metrics.overallMastery)}%`,
      context:
        metrics.overallMastery === null
          ? "No approved topic records yet"
          : "Across tracked topics",
      href: "/progress",
    },
    {
      label: "Topics tracked",
      value: metrics.trackedTopicCount,
      context: "Topics with approved progress",
      href: "/topics",
    },
    {
      label: "Approved attempts",
      value: metrics.totalAttempts,
      context: "Included in your mastery",
      href: "/progress",
    },
    {
      label: "Approved assignments",
      value: metrics.approvedAssignmentCount,
      context: "Approved work assigned to you",
      href: "/worksheets",
    },
  ];
}
function DashboardSkeleton() {
  return (
    <Box
      data-testid="student-dashboard-skeleton"
      aria-label="Loading your dashboard"
      sx={{ display: "grid", gap: 2.5 }}
    >
      <Skeleton
        variant="text"
        width="45%"
        height={58}
        sx={{ bgcolor: "#F0EAE0" }}
      />
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
        }}
      >
        {[0, 1, 2, 3].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={138}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        ))}
      </Box>
      <Skeleton
        variant="rounded"
        height={260}
        sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
      />
    </Box>
  );
}
function EmptySection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card
      component="section"
      variant="outlined"
      sx={{
        ...card,
        borderStyle: "dashed",
        borderColor: "#DCCFBE",
        p: 3,
        textAlign: "center",
        minHeight: 176,
        display: "grid",
        placeItems: "center",
      }}
    >
      <Box>
        <Typography
          component="h2"
          sx={{ fontFamily: serif, fontSize: 21, fontWeight: 500, mb: 0.75 }}
        >
          {title}
        </Typography>
        <Typography sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6 }}>
          {children}
        </Typography>
      </Box>
    </Card>
  );
}
function TopicCard({
  title,
  topic,
  focus,
  go,
}: {
  title: string;
  topic: StudentDashboardTopic | null;
  focus?: boolean;
  go: (href: string) => void;
}) {
  if (!topic)
    return (
      <EmptySection title={title}>
        Your approved topic progress will appear after an assignment is
        reviewed.
      </EmptySection>
    );
  const tone = masteryColour(topic.score);
  return (
    <Card
      component="section"
      variant="outlined"
      sx={{
        ...card,
        p: 2.5,
        bgcolor: focus && topic.score < 55 ? "#FDF6F3" : "#FFFDFA",
        borderColor: focus && topic.score < 55 ? "#F0DCD4" : "#EBE4D9",
      }}
    >
      <Typography
        sx={{
          color: "#A09488",
          fontSize: 10.5,
          fontWeight: 700,
          letterSpacing: ".13em",
          mb: 1,
        }}
      >
        {title.toUpperCase()}
      </Typography>
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", justifyContent: "space-between", gap: 1 }}
      >
        <Typography
          component="h2"
          sx={{
            fontFamily: serif,
            fontSize: 22,
            lineHeight: 1.15,
            minWidth: 0,
          }}
        >
          {topic.topicName}
        </Typography>
        <Chip
          label={statusLabel(topic.status)}
          size="small"
          sx={{
            flex: "0 0 auto",
            height: 24,
            bgcolor: "#F4EFE6",
            color: "#5A544C",
            fontWeight: 600,
            fontSize: 10,
          }}
        />
      </Stack>
      <Stack
        direction="row"
        spacing={1.25}
        sx={{ alignItems: "baseline", mt: 2 }}
      >
        <Typography
          sx={{
            fontFamily: serif,
            color: tone,
            fontVariantNumeric: "tabular-nums",
            fontSize: 35,
            lineHeight: 1,
          }}
        >
          {Math.round(topic.score)}%
        </Typography>
        <Typography sx={{ color: "#8B837A", fontSize: 12.5 }}>
          from {topic.attemptCount} approved{" "}
          {topic.attemptCount === 1 ? "attempt" : "attempts"}
        </Typography>
      </Stack>
      <Box
        aria-label={`${topic.topicName} mastery ${Math.round(topic.score)} percent`}
        sx={{
          height: 6,
          bgcolor: "#F0EAE0",
          borderRadius: 20,
          overflow: "hidden",
          mt: 1.25,
        }}
      >
        <Box
          sx={{
            height: "100%",
            width: `${topic.score}%`,
            bgcolor: tone,
            borderRadius: 20,
            transition: "width .6s ease",
          }}
        />
      </Box>
      <Button
        onClick={() => go(`/topics/${topic.topicId}`)}
        endIcon={<ArrowForwardIcon />}
        sx={{ ...button, mt: 1.5, px: 0, color: "#9E3A24", minHeight: 36 }}
      >
        Open topic
      </Button>
    </Card>
  );
}
export default function StudentDashboard({
  loadDashboard = fetchStudentDashboard,
  navigate,
}: StudentDashboardProps) {
  const router = useRouter();
  const [dashboard, setDashboard] = React.useState<StudentDashboardData | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);
  const [reload, setReload] = React.useState(0);
  const go = navigate ?? ((href: string) => router.push(href));
  React.useEffect(() => {
    let active = true;
    void loadDashboard().then(
      (loaded) => {
        if (active) {
          setDashboard(loaded);
          setError(null);
        }
      },
      (reason: unknown) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : "Dashboard data could not be loaded. Please try again.",
          );
      },
    );
    return () => {
      active = false;
    };
  }, [loadDashboard, reload]);
  if (error)
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
          Your dashboard could not be loaded
        </Typography>
        <Typography
          sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
        >
          {error}
        </Typography>
        <Button
          onClick={() => {
            setDashboard(null);
            setError(null);
            setReload((value) => value + 1);
          }}
          variant="outlined"
          sx={{ ...button, borderColor: "#E4DCD0", color: "#2A2622" }}
        >
          Retry loading dashboard
        </Button>
      </Card>
    );
  if (!dashboard) return <DashboardSkeleton />;
  const { metrics } = dashboard;
  const newStudent =
    metrics.trackedTopicCount === 0 &&
    metrics.totalAttempts === 0 &&
    metrics.approvedAssignmentCount === 0;
  const assignment = dashboard.nextAssignment ?? dashboard.latestAssignment;
  const metricsCards = dashboardMetrics(metrics);
  const approvedTopicResult = dashboard.latestApprovedTopicResult;
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
          sx={{
            display: "flex",
            flexWrap: "wrap",
            justifyContent: "space-between",
            alignItems: "flex-end",
            gap: 2,
            mb: 3,
          }}
        >
          <Box>
            <Typography
              sx={{
                color: "#A09488",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".13em",
                mb: 0.75,
              }}
            >
              LEARNING OVERVIEW
            </Typography>
            <Typography
              component="h1"
              sx={{
                fontFamily: serif,
                fontSize: { xs: 32, sm: 38 },
                fontWeight: 500,
                lineHeight: 1.1,
                letterSpacing: "-.02em",
              }}
            >
              Welcome back, {dashboard.studentName}.
            </Typography>
            <Typography sx={{ color: "#8B837A", fontSize: 12.5, mt: 0.75 }}>
              Your approved learning data · {dashboard.today} ·{" "}
              {dashboard.timeZone}
            </Typography>
          </Box>
          <Button
            onClick={() => go("/upload")}
            startIcon={<FileUploadOutlinedIcon />}
            sx={{
              ...button,
              bgcolor: "#9E3A24",
              color: "#FBF9F5",
              px: 2.2,
              "&:hover": { bgcolor: "#8A3120" },
            }}
          >
            Upload completed work
          </Button>
        </Box>
        <Box
          data-testid="student-dashboard-metric-grid"
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
            gap: 1.75,
            mb: 2.75,
          }}
        >
          {metricsCards.map((metric) => (
            <Card
              key={metric.label}
              component="button"
              type="button"
              onClick={() => go(metric.href)}
              variant="outlined"
              sx={{
                ...card,
                textAlign: "left",
                cursor: "pointer",
                p: "16px 18px 18px",
                minHeight: 138,
                transition: "border-color .18s, transform .18s",
                "&:hover": {
                  borderColor: "#DCCFBE",
                  transform: "translateY(-2px)",
                },
                "&:focus-visible": {
                  outline: "3px solid #E08A72",
                  outlineOffset: 2,
                },
              }}
            >
              <Typography
                sx={{
                  fontSize: 11.5,
                  color: "#6F675E",
                  fontWeight: 500,
                  mb: 1.25,
                }}
              >
                {metric.label}
              </Typography>
              <Typography
                sx={{
                  fontFamily: serif,
                  fontSize: 34,
                  lineHeight: 1,
                  fontWeight: 500,
                  color: "#2A2622",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {metric.value}
              </Typography>
              <Typography sx={{ fontSize: 11, color: "#A09488", mt: 1 }}>
                {metric.context}
              </Typography>
            </Card>
          ))}
        </Box>
        {newStudent ? (
          <Card
            component="section"
            variant="outlined"
            sx={{
              ...card,
              borderStyle: "dashed",
              borderColor: "#DCCFBE",
              p: 3,
              mb: 2.75,
              display: "flex",
              flexWrap: "wrap",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 2,
            }}
          >
            <Box>
              <Typography
                component="h2"
                sx={{
                  fontFamily: serif,
                  fontSize: 21,
                  fontWeight: 500,
                  mb: 0.5,
                }}
              >
                Your learning dashboard is ready
              </Typography>
              <Typography
                sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6 }}
              >
                Your approved assignments and topic progress will appear here
                when your tutor shares work with you.
              </Typography>
            </Box>
            <Button
              onClick={() => go("/worksheets")}
              variant="outlined"
              sx={{ ...button, borderColor: "#E4DCD0", color: "#2A2622" }}
            >
              View worksheets
            </Button>
          </Card>
        ) : null}
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 2.5,
            alignItems: "flex-start",
          }}
        >
          <Box
            sx={{ flex: "1 1 460px", minWidth: 0, display: "grid", gap: 2.5 }}
          >
            <Card
              component="section"
              variant="outlined"
              sx={{ ...card, p: 2.75 }}
            >
              <Stack
                direction="row"
                spacing={1.25}
                sx={{ alignItems: "center", mb: 1.25 }}
              >
                <AssignmentOutlinedIcon
                  aria-hidden="true"
                  sx={{ color: "#9E3A24" }}
                />
                <Typography
                  component="h2"
                  sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500 }}
                >
                  Your assignment
                </Typography>
              </Stack>
              {assignment ? (
                <>
                  <Typography
                    sx={{ fontSize: 14, color: "#4A443D", lineHeight: 1.6 }}
                  >
                    An approved{" "}
                    {assignment.assignmentType === "CLASS"
                      ? "class"
                      : "personal"}{" "}
                    assignment is ready for you.
                  </Typography>
                  <Typography sx={{ fontSize: 12.5, color: "#8B837A", mt: 1 }}>
                    Assigned {dateTimeLabel(assignment.assignedAt)}
                    {assignment.dueAt
                      ? ` · Due ${dateTimeLabel(assignment.dueAt)}`
                      : ""}
                  </Typography>
                  <Button
                    onClick={() => go(`/worksheets/${assignment.worksheetId}`)}
                    endIcon={<ArrowForwardIcon />}
                    sx={{
                      ...button,
                      mt: 1.5,
                      px: 0,
                      color: "#9E3A24",
                      minHeight: 36,
                    }}
                  >
                    Open assignment
                  </Button>
                </>
              ) : (
                <Typography
                  sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6 }}
                >
                  When your tutor assigns approved work, it will appear here.
                </Typography>
              )}
            </Card>
            <Card
              component="section"
              variant="outlined"
              sx={{ ...card, p: 2.75 }}
            >
              <Stack
                direction="row"
                spacing={1.25}
                sx={{ alignItems: "center", mb: 1.25 }}
              >
                <InsightsOutlinedIcon
                  aria-hidden="true"
                  sx={{ color: "#9E3A24" }}
                />
                <Typography
                  component="h2"
                  sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500 }}
                >
                  Latest approved topic result
                </Typography>
              </Stack>
              {approvedTopicResult ? (
                <>
                  <Typography sx={{ fontSize: 16, fontWeight: 600 }}>
                    {approvedTopicResult.topicName}
                  </Typography>
                  <Typography
                    sx={{
                      fontFamily: serif,
                      color: "#2A2622",
                      fontSize: 34,
                      lineHeight: 1,
                      mt: 1,
                    }}
                  >
                    {approvedTopicResult.approvedMarks} /{" "}
                    {approvedTopicResult.availableMarks}
                  </Typography>
                  <Typography sx={{ color: "#8B837A", fontSize: 12.5, mt: 1 }}>
                    Approved {dateTimeLabel(approvedTopicResult.reviewedAt)} ·
                    This is a topic-level result, not a worksheet score.
                  </Typography>
                  <Button
                    onClick={() => go(`/topics/${approvedTopicResult.topicId}`)}
                    endIcon={<ArrowForwardIcon />}
                    sx={{
                      ...button,
                      mt: 1.5,
                      px: 0,
                      color: "#9E3A24",
                      minHeight: 36,
                    }}
                  >
                    Review topic
                  </Button>
                </>
              ) : (
                <Typography
                  sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6 }}
                >
                  Your latest tutor-approved topic mark will appear here after
                  review.
                </Typography>
              )}
            </Card>
          </Box>
          <Box
            sx={{
              flex: "0 1 320px",
              minWidth: { xs: 0, sm: 280 },
              width: { xs: "100%", sm: "auto" },
              display: "grid",
              gap: 2.5,
            }}
          >
            <TopicCard
              title="Strongest topic"
              topic={dashboard.strongestTopic}
              go={go}
            />
            <TopicCard
              title="Focus topic"
              topic={dashboard.focusTopic}
              focus
              go={go}
            />
            <Card
              component="section"
              variant="outlined"
              sx={{ ...card, p: 2.5 }}
            >
              <Typography
                sx={{
                  color: "#A09488",
                  fontSize: 10.5,
                  fontWeight: 700,
                  letterSpacing: ".13em",
                  mb: 1.5,
                }}
              >
                QUICK ACTIONS
              </Typography>
              <Stack spacing={1.25}>
                <Button
                  onClick={() => go("/upload")}
                  startIcon={<FileUploadOutlinedIcon />}
                  sx={{
                    ...button,
                    bgcolor: "#9E3A24",
                    color: "#FBF9F5",
                    justifyContent: "flex-start",
                    px: 2,
                    "&:hover": { bgcolor: "#8A3120" },
                  }}
                >
                  Upload completed work
                </Button>
                <Button
                  onClick={() => go("/progress")}
                  startIcon={<TrendingUpOutlinedIcon />}
                  variant="outlined"
                  sx={{
                    ...button,
                    borderColor: "#E4DCD0",
                    color: "#2A2622",
                    justifyContent: "flex-start",
                    px: 2,
                  }}
                >
                  View my progress
                </Button>
                <Button
                  onClick={() => go("/worksheets")}
                  startIcon={<AssignmentOutlinedIcon />}
                  variant="outlined"
                  sx={{
                    ...button,
                    borderColor: "#E4DCD0",
                    color: "#2A2622",
                    justifyContent: "flex-start",
                    px: 2,
                  }}
                >
                  View worksheets
                </Button>
                <Button
                  onClick={() => go("/mistakes")}
                  startIcon={<WarningAmberOutlinedIcon />}
                  variant="outlined"
                  sx={{
                    ...button,
                    borderColor: "#E4DCD0",
                    color: "#2A2622",
                    justifyContent: "flex-start",
                    px: 2,
                  }}
                >
                  Review mistakes
                </Button>
              </Stack>
            </Card>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
