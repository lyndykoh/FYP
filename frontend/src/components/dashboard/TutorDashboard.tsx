"use client";

import * as React from "react";
import AddCircleIcon from "@mui/icons-material/AddCircle";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import AssignmentTurnedInOutlinedIcon from "@mui/icons-material/AssignmentTurnedInOutlined";
import GroupsOutlinedIcon from "@mui/icons-material/GroupsOutlined";
import UploadFileIcon from "@mui/icons-material/UploadFile";
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
  fetchTutorDashboard,
  type TutorDashboard as TutorDashboardData,
  type TutorDashboardActivity,
} from "@/services/dashboard";

export interface TutorDashboardProps {
  loadDashboard?: (timeZone: string) => Promise<TutorDashboardData>;
  /** Supplying a zone is useful for deterministic tests and embedded views. */
  timeZone?: string;
  navigate?: (href: string) => void;
}

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;
const buttonBase = {
  minHeight: 40,
  textTransform: "none",
  fontSize: 13.5,
  fontWeight: 500,
  borderRadius: "10px",
  "&:focus-visible": { outline: "3px solid #E08A72", outlineOffset: 2 },
} as const;
const secondaryButton = {
  ...buttonBase,
  borderColor: "#E4DCD0",
  bgcolor: "#FFFDFA",
  color: "#2A2622",
  "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
} as const;

function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

function timeLabel(value: string): string {
  return value.slice(0, 5);
}

function initials(name: string | null): string {
  if (!name) return "—";
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function activityPresentation(activity: TutorDashboardActivity) {
  switch (activity.type) {
    case "WORKSHEET_ASSIGNED":
      return {
        badge: "ASSIGNED",
        bg: "#F3EBDD",
        color: "#7A6238",
        avatar: "#D8B384",
        href: "/tutor/worksheets",
        action: "View worksheets",
      };
    case "REVIEW_REQUESTED":
      return {
        badge: "SUBMITTED",
        bg: "#F7E3DC",
        color: "#9E3A24",
        avatar: "#E3C3B4",
        href: `/tutor/reviews/${activity.sourceId}`,
        action: "Open review",
      };
    case "ALERT_CREATED":
      return {
        badge:
          activity.severity === "CRITICAL" ? "HIGH PRIORITY" : "NEEDS PRACTICE",
        bg: "#F7E3DC",
        color: "#9E3A24",
        avatar: "#C6D0C4",
        href: activity.studentId
          ? `/students/${activity.studentId}`
          : "/tutor/alerts",
        action: activity.studentId ? "Open student" : "Open alerts",
      };
  }
}

function dashboardMetrics(dashboard: TutorDashboardData) {
  return [
    {
      label: "Active classes",
      value: dashboard.metrics.activeClassCount,
      context: "Classes teaching this term",
      href: "/classes",
      tone: "#2A2622",
    },
    {
      label: "Students",
      value: dashboard.metrics.studentCount,
      context: "Students in your roster",
      href: "/students",
      tone: "#2A2622",
    },
    {
      label: "Pending review",
      value: dashboard.metrics.pendingReviewCount,
      context: "Marking decisions awaiting you",
      href: "/tutor/reviews",
      tone: "#B4573F",
    },
    {
      label: "Needs attention",
      value: dashboard.metrics.needsAttentionStudentCount,
      context: "Students with an active alert",
      href: "/tutor/alerts",
      tone: "#B4573F",
    },
    {
      label: "Reports ready",
      value: dashboard.metrics.reportsReadyCount,
      context: "Finalised progress reports",
      href: "/students",
      tone: "#2A2622",
    },
  ];
}

function DashboardSkeleton() {
  return (
    <Box
      data-testid="tutor-dashboard-skeleton"
      aria-label="Loading dashboard"
      sx={{ display: "grid", gap: 2.5 }}
    >
      <Skeleton
        variant="text"
        width="40%"
        height={54}
        sx={{ bgcolor: "#F0EAE0" }}
      />
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
        }}
      >
        {[0, 1, 2, 3, 4].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={138}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        ))}
      </Box>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2.5 }}>
        <Skeleton
          variant="rounded"
          height={260}
          sx={{ flex: "1 1 460px", borderRadius: "14px", bgcolor: "#F0EAE0" }}
        />
        <Skeleton
          variant="rounded"
          height={260}
          sx={{
            flex: "0 1 320px",
            minWidth: 280,
            borderRadius: "14px",
            bgcolor: "#F0EAE0",
          }}
        />
      </Box>
    </Box>
  );
}

function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
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
        Dashboard could not be loaded
      </Typography>
      <Typography
        sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
      >
        {message}
      </Typography>
      <Button onClick={retry} variant="outlined" sx={secondaryButton}>
        Retry loading dashboard
      </Button>
    </Card>
  );
}

function EmptyCard({
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

export default function TutorDashboard({
  loadDashboard = fetchTutorDashboard,
  timeZone,
  navigate,
}: TutorDashboardProps) {
  const router = useRouter();
  const [dashboard, setDashboard] = React.useState<TutorDashboardData | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);
  const [reloadAttempt, setReloadAttempt] = React.useState(0);
  const go = navigate ?? ((href: string) => router.push(href));

  React.useEffect(() => {
    let current = true;
    // Timezone detection happens only after mounting, so server and client
    // initial markup stay identical. The backend remains authoritative.
    void loadDashboard(timeZone?.trim() || browserTimeZone()).then(
      (loaded) => {
        if (current) {
          setDashboard(loaded);
          setError(null);
        }
      },
      (reason: unknown) => {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "Dashboard data could not be loaded. Please try again.",
          );
      },
    );
    return () => {
      current = false;
    };
  }, [loadDashboard, reloadAttempt, timeZone]);

  const retry = () => {
    setDashboard(null);
    setError(null);
    setReloadAttempt((value) => value + 1);
  };

  if (error) return <ErrorState message={error} retry={retry} />;
  if (!dashboard) return <DashboardSkeleton />;

  const metrics = dashboardMetrics(dashboard);
  const emptyTutor =
    metrics.every((metric) => metric.value === 0) &&
    dashboard.todaySchedule.length === 0 &&
    dashboard.recentActivity.length === 0;

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
                fontWeight: 600,
                letterSpacing: ".13em",
                mb: 0.75,
              }}
            >
              TEACHING OVERVIEW
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
              Your teaching day, clearly organised.
            </Typography>
            <Typography sx={{ color: "#8B837A", fontSize: 12.5, mt: 0.75 }}>
              Schedule for {dashboard.today} · {dashboard.timeZone}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
            <Button
              onClick={() => go("/tutor/worksheets/new")}
              startIcon={<AddCircleIcon />}
              sx={{
                ...buttonBase,
                bgcolor: "#E08A72",
                color: "#1B1917",
                px: 2.2,
                "&:hover": { bgcolor: "#D2795F" },
              }}
            >
              Generate Worksheet
            </Button>
            <Button
              onClick={() => go("/upload")}
              startIcon={<UploadFileIcon />}
              variant="outlined"
              sx={{ ...secondaryButton, px: 2.2 }}
            >
              Upload worksheet
            </Button>
          </Stack>
        </Box>

        <Box
          data-testid="dashboard-metric-grid"
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
            gap: 1.75,
            mb: 2.75,
          }}
        >
          {metrics.map((metric) => (
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
                  color: metric.tone,
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

        {emptyTutor && (
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
                Your dashboard is ready
              </Typography>
              <Typography
                sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6 }}
              >
                Create a class or prepare a worksheet to start tracking your
                teaching activity.
              </Typography>
            </Box>
            <Button
              onClick={() => go("/classes/new")}
              variant="outlined"
              sx={secondaryButton}
            >
              Create class
            </Button>
          </Card>
        )}

        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 2.5,
            alignItems: "flex-start",
          }}
        >
          <Box sx={{ flex: "1 1 460px", minWidth: 0 }}>
            <Typography
              component="h2"
              sx={{
                fontFamily: serif,
                fontSize: 21,
                fontWeight: 500,
                mb: 1.25,
              }}
            >
              Today&apos;s teaching focus
            </Typography>
            {dashboard.todaySchedule.length === 0 ? (
              <EmptyCard title="No classes scheduled today">
                Classes scheduled for {dashboard.today} will appear here in
                start-time order.
              </EmptyCard>
            ) : (
              <Stack spacing={1.5}>
                {dashboard.todaySchedule.map((item) => (
                  <Card
                    key={`${item.classId}-${item.startTime}`}
                    component="button"
                    type="button"
                    onClick={() => go(`/classes/${item.classId}`)}
                    variant="outlined"
                    sx={{
                      ...card,
                      width: "100%",
                      cursor: "pointer",
                      textAlign: "left",
                      p: 2.25,
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 2,
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
                    <Box sx={{ minWidth: 0 }}>
                      <Chip
                        label={`${timeLabel(item.startTime)}–${timeLabel(item.endTime)}`}
                        size="small"
                        sx={{
                          bgcolor: "#F4EFE6",
                          color: "#6F675E",
                          fontSize: 11.5,
                          fontWeight: 500,
                          height: 25,
                          mb: 1,
                        }}
                      />
                      <Typography
                        sx={{
                          fontSize: 14.5,
                          fontWeight: 600,
                          color: "#2A2622",
                        }}
                      >
                        {item.className}
                      </Typography>
                      <Typography
                        sx={{ fontSize: 12.5, color: "#8B837A", mt: 0.4 }}
                      >
                        {item.subject} · {item.level}
                      </Typography>
                    </Box>
                    <ArrowForwardIcon
                      aria-hidden="true"
                      sx={{ color: "#B4573F", flex: "0 0 auto" }}
                    />
                  </Card>
                ))}
              </Stack>
            )}
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
            <Card
              component="section"
              aria-labelledby="quick-actions-title"
              variant="outlined"
              sx={{ ...card, p: 2.5 }}
            >
              <Typography
                id="quick-actions-title"
                sx={{
                  color: "#A09488",
                  fontSize: 10.5,
                  fontWeight: 600,
                  letterSpacing: ".13em",
                  mb: 1.5,
                }}
              >
                QUICK ACTIONS
              </Typography>
              <Stack spacing={1.25}>
                <Button
                  onClick={() => go("/tutor/worksheets/new")}
                  startIcon={<AddCircleIcon />}
                  sx={{
                    ...buttonBase,
                    bgcolor: "#9E3A24",
                    color: "#FBF9F5",
                    justifyContent: "flex-start",
                    px: 2,
                    "&:hover": { bgcolor: "#8A3120" },
                  }}
                >
                  Generate Worksheet
                </Button>
                <Button
                  onClick={() => go("/upload")}
                  startIcon={<UploadFileIcon />}
                  variant="outlined"
                  sx={{
                    ...secondaryButton,
                    justifyContent: "flex-start",
                    px: 2,
                  }}
                >
                  Upload completed worksheet
                </Button>
              </Stack>
            </Card>
            <Card
              component="section"
              aria-labelledby="activity-title"
              variant="outlined"
              sx={{ ...card, p: 2.5 }}
            >
              <Typography
                id="activity-title"
                sx={{
                  color: "#A09488",
                  fontSize: 10.5,
                  fontWeight: 600,
                  letterSpacing: ".13em",
                  mb: 1,
                }}
              >
                RECENT ACTIVITY
              </Typography>
              {dashboard.recentActivity.length === 0 ? (
                <Typography
                  sx={{
                    color: "#8B837A",
                    fontSize: 12.5,
                    lineHeight: 1.6,
                    py: 1,
                  }}
                >
                  Assignments, marking reviews, and alerts will appear here when
                  they need your attention.
                </Typography>
              ) : (
                <Box data-testid="dashboard-activity-list">
                  {dashboard.recentActivity.map((activity, index) => {
                    const presentation = activityPresentation(activity);
                    return (
                      <Box
                        key={`${activity.type}-${activity.sourceId}`}
                        component="button"
                        type="button"
                        onClick={() => go(presentation.href)}
                        sx={{
                          width: "100%",
                          appearance: "none",
                          border: 0,
                          bgcolor: "transparent",
                          cursor: "pointer",
                          display: "flex",
                          alignItems: "center",
                          textAlign: "left",
                          gap: 1.25,
                          py: 1.25,
                          px: 0,
                          borderBottom:
                            index < dashboard.recentActivity.length - 1
                              ? "1px solid #F0EAE0"
                              : 0,
                          "&:hover .activity-title": { color: "#9E3A24" },
                          "&:focus-visible": {
                            outline: "3px solid #E08A72",
                            outlineOffset: 2,
                            borderRadius: 1,
                          },
                        }}
                        aria-label={`${presentation.action}: ${activity.title}`}
                      >
                        <Box
                          aria-hidden="true"
                          sx={{
                            width: 28,
                            height: 28,
                            borderRadius: "50%",
                            flex: "0 0 auto",
                            display: "grid",
                            placeItems: "center",
                            bgcolor: presentation.avatar,
                            color: "#3A332C",
                            fontSize: 10.5,
                            fontWeight: 700,
                          }}
                        >
                          {activity.studentName ? (
                            initials(activity.studentName)
                          ) : activity.type === "WORKSHEET_ASSIGNED" ? (
                            <AssignmentTurnedInOutlinedIcon
                              sx={{ fontSize: 16 }}
                            />
                          ) : activity.type === "ALERT_CREATED" ? (
                            <WarningAmberOutlinedIcon sx={{ fontSize: 16 }} />
                          ) : (
                            <GroupsOutlinedIcon sx={{ fontSize: 16 }} />
                          )}
                        </Box>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography
                            className="activity-title"
                            sx={{
                              fontSize: 13,
                              fontWeight: 600,
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                              transition: "color .18s",
                            }}
                          >
                            {activity.title}
                          </Typography>
                          <Typography
                            sx={{
                              color: "#8B837A",
                              fontSize: 11.5,
                              mt: 0.25,
                              overflow: "hidden",
                              textOverflow: "ellipsis",
                              whiteSpace: "nowrap",
                            }}
                          >
                            {activity.studentName ?? activity.detail}
                          </Typography>
                        </Box>
                        <Chip
                          label={presentation.badge}
                          size="small"
                          sx={{
                            height: 24,
                            flex: "0 0 auto",
                            fontSize: 9.5,
                            letterSpacing: ".05em",
                            fontWeight: 700,
                            bgcolor: presentation.bg,
                            color: presentation.color,
                            borderRadius: 20,
                            ".MuiChip-label": { px: 1.1 },
                          }}
                        />
                      </Box>
                    );
                  })}
                </Box>
              )}
            </Card>
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
