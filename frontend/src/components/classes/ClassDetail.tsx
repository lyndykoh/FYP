"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import GroupsOutlinedIcon from "@mui/icons-material/GroupsOutlined";
import MenuBookOutlinedIcon from "@mui/icons-material/MenuBookOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  ClassApiError,
  fetchTutorClassDetail,
  fetchTutorClassInsights,
  type ClassInsightAvailability,
  type ClassInsightSnapshot,
  type ClassWorksheet,
  type TutorClassDetail,
} from "@/services/classes";

export interface ClassDetailProps {
  classId: number;
  loadClass?: (classId: number) => Promise<TutorClassDetail>;
  loadInsights?: (classId: number) => Promise<ClassInsightSnapshot>;
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
const worksheetStatus = {
  DRAFT: { label: "GENERATED", bg: "#F0EAE0", color: "#6F675E" },
  APPROVED: { label: "ASSIGNED", bg: "#F3EBDD", color: "#7A6238" },
  ARCHIVED: { label: "ARCHIVED", bg: "#F0EAE0", color: "#6F675E" },
} as const;

function percent(value: number | null) {
  return value === null ? "—" : `${Math.round(value)}%`;
}

function barColor(value: number) {
  return value < 55 ? "#B4573F" : value < 72 ? "#D8B384" : "#93A896";
}

function ClassDetailSkeleton() {
  return (
    <Box
      data-testid="class-detail-skeleton"
      aria-label="Loading class details"
      sx={{ display: "grid", gap: 2 }}
    >
      <Skeleton
        variant="text"
        width="38%"
        height={52}
        sx={{ bgcolor: "#F0EAE0" }}
      />
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
        }}
      >
        {[0, 1, 2].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={116}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        ))}
      </Box>
      <Skeleton
        variant="rounded"
        height={250}
        sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
      />
    </Box>
  );
}

function classDetailErrorCopy(error: Error) {
  if (error instanceof ClassApiError && error.status === 404) {
    return {
      title: "Class not found",
      message: "This class no longer exists, or its link is no longer valid.",
      retryLabel: "Back to classes",
    };
  }
  if (error instanceof ClassApiError && error.status === 403) {
    return {
      title: "You do not have access to this class",
      message:
        "Only the Tutor who owns this class can open its detail and roster.",
      retryLabel: "Back to classes",
    };
  }
  if (error instanceof ClassApiError && error.status >= 500) {
    return {
      title: "Class service is unavailable",
      message: "The class service could not respond. Please try again shortly.",
      retryLabel: "Retry loading class",
    };
  }
  return {
    title: "Class details could not be loaded",
    message:
      "The class detail request could not be completed. Check your connection and try again.",
    retryLabel: "Retry loading class",
  };
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
        border: "1px dashed #DCCFBE",
        borderRadius: "12px",
        p: 2,
        textAlign: "center",
        bgcolor: "#FFFDFA",
      }}
    >
      <Typography sx={{ fontSize: 13.5, fontWeight: 600, mb: 0.5 }}>
        {title}
      </Typography>
      <Typography sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}>
        {children}
      </Typography>
    </Box>
  );
}

function WorksheetRow({ worksheet }: { worksheet: ClassWorksheet }) {
  const status = worksheetStatus[worksheet.status];
  return (
    <Box
      sx={{
        border: "1px solid #EBE4D9",
        borderLeft: `3px solid ${worksheet.status === "APPROVED" ? "#93A896" : "#D8B384"}`,
        borderRadius: "12px",
        bgcolor: "#FFFDFA",
        p: "14px 16px",
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: 1.25,
      }}
    >
      <Box sx={{ minWidth: 0, flex: "1 1 210px" }}>
        <Typography sx={{ fontSize: 13.5, fontWeight: 600, lineHeight: 1.35 }}>
          {worksheet.title}
        </Typography>
        <Typography sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.6 }}>
          {worksheet.dueAt
            ? `Due ${new Date(worksheet.dueAt).toLocaleDateString()}`
            : "No due date"}
        </Typography>
      </Box>
      <Chip
        label={status.label}
        size="small"
        sx={{
          height: 24,
          bgcolor: status.bg,
          color: status.color,
          fontSize: 9.5,
          fontWeight: 700,
          letterSpacing: ".05em",
        }}
      />
      <Button
        component={Link}
        href={`/worksheets/${worksheet.id}`}
        variant="outlined"
        sx={{ ...secondaryButton, minHeight: 34, py: 0, fontSize: 12.5 }}
      >
        View worksheet
      </Button>
    </Box>
  );
}

function scheduleDay(
  dayOfWeek: TutorClassDetail["schedules"][number]["dayOfWeek"],
) {
  return `${dayOfWeek[0]}${dayOfWeek.slice(1).toLowerCase()}`;
}

function ClassSchedule({
  schedules,
}: {
  schedules: TutorClassDetail["schedules"];
}) {
  return (
    <Card
      component="section"
      aria-labelledby="class-schedule-heading"
      variant="outlined"
      sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
    >
      <Typography
        id="class-schedule-heading"
        component="h2"
        sx={{ fontFamily: serif, fontSize: 21, fontWeight: 500, mb: 0.5 }}
      >
        Class schedule
      </Typography>
      <Typography
        sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.55, mb: 1.5 }}
      >
        Regular teaching times for this class.
      </Typography>
      {schedules.length === 0 ? (
        <EmptySection title="No regular schedule yet">
          Add teaching times when the class timetable is confirmed.
        </EmptySection>
      ) : (
        <Box
          component="ul"
          sx={{ listStyle: "none", m: 0, p: 0, display: "grid", gap: 1 }}
        >
          {schedules.map((schedule) => (
            <Box
              component="li"
              key={`${schedule.dayOfWeek}-${schedule.startTime}-${schedule.endTime}`}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 1.25,
                border: "1px solid #EBE4D9",
                borderRadius: "10px",
                bgcolor: "#FFFDFA",
                px: 1.5,
                py: 1.25,
              }}
            >
              <Typography
                sx={{ color: "#4A443D", fontSize: 13, fontWeight: 600 }}
              >
                {scheduleDay(schedule.dayOfWeek)}
              </Typography>
              <Typography
                sx={{
                  color: "#6F675E",
                  fontSize: 12.5,
                  fontWeight: 500,
                  fontVariantNumeric: "tabular-nums",
                  whiteSpace: "nowrap",
                }}
              >
                {schedule.startTime}–{schedule.endTime}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Card>
  );
}

const insightStatus = {
  FRESH: { label: "CURRENT", bg: "#22301F", color: "#9FC0A2" },
  STALE: { label: "STALE", bg: "#3A2119", color: "#E0A692" },
  REFRESHING: { label: "REFRESHING", bg: "#33302A", color: "#B5ADA2" },
  FAILED: { label: "REFRESH FAILED", bg: "#3A2119", color: "#E0A692" },
} as const;

function snapshotTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
}

function ClassInsightPanel({
  availability,
  insight,
  loading,
  error,
  onRetry,
}: {
  availability: ClassInsightAvailability;
  insight: ClassInsightSnapshot | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}) {
  const status = insight?.status ?? availability.status;
  const statusStyle = insightStatus[status];
  const weakItems =
    insight?.items.filter((item) => item.weak).slice(0, 3) ?? [];

  return (
    <Card
      component="section"
      aria-labelledby="insight-heading"
      sx={{
        borderRadius: "14px",
        bgcolor: "#1B1917",
        color: "#E8E2D9",
        p: { xs: 2.25, sm: 2.75 },
        boxShadow: "none",
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 1,
          mb: 1.25,
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Box
            aria-hidden="true"
            sx={{
              width: 20,
              height: 20,
              borderRadius: "50%",
              bgcolor: "#E08A72",
              color: "#1B1917",
              display: "grid",
              placeItems: "center",
            }}
          >
            <AutoAwesomeIcon sx={{ fontSize: 12 }} />
          </Box>
          <Typography
            id="insight-heading"
            component="h2"
            sx={{
              fontFamily: serif,
              fontSize: 19,
              fontWeight: 500,
              color: "#FBF9F5",
            }}
          >
            Class insight
          </Typography>
        </Box>
        <Chip
          label={statusStyle.label}
          size="small"
          sx={{
            height: 22,
            bgcolor: statusStyle.bg,
            color: statusStyle.color,
            fontSize: 9.5,
            fontWeight: 700,
            letterSpacing: ".05em",
          }}
        />
      </Box>
      <Typography
        aria-live="polite"
        sx={{
          color: "#CFC7BC",
          fontSize: 13.5,
          lineHeight: 1.65,
          maxWidth: "52ch",
        }}
      >
        {error ?? insight?.message ?? availability.message}
      </Typography>
      {loading && (
        <Typography
          role="status"
          sx={{ color: "#A8A096", fontSize: 12, lineHeight: 1.55, mt: 1 }}
        >
          Refreshing recorded class mastery…
        </Typography>
      )}
      {error && (
        <Button
          onClick={onRetry}
          variant="text"
          sx={{
            minHeight: 34,
            mt: 0.75,
            px: 0,
            color: "#E08A72",
            textTransform: "none",
            fontWeight: 600,
          }}
        >
          Retry insight refresh
        </Button>
      )}
      {insight?.dataAsOf && (
        <Typography
          sx={{ color: "#8F877D", fontSize: 11.5, lineHeight: 1.55, mt: 1 }}
        >
          Evidence recorded {snapshotTime(insight.dataAsOf)}.
        </Typography>
      )}
      {!loading &&
        !error &&
        insight &&
        (weakItems.length === 0 ? (
          <Box sx={{ mt: 1.5, borderTop: "1px solid #2C2925", pt: 1.5 }}>
            <Typography
              sx={{ color: "#CFC7BC", fontSize: 12.5, lineHeight: 1.6 }}
            >
              No covered topic currently meets this class’s weak-area threshold.
            </Typography>
          </Box>
        ) : (
          <Box sx={{ display: "grid", gap: 1, mt: 1.5 }}>
            {weakItems.map((item) => (
              <Box
                key={item.topicId}
                sx={{
                  bgcolor: "#232120",
                  borderRadius: "10px",
                  p: "13px 14px",
                }}
              >
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: 1,
                    mb: 0.75,
                  }}
                >
                  <Typography
                    sx={{
                      color: "#FBF9F5",
                      fontSize: 13,
                      fontWeight: 600,
                      lineHeight: 1.35,
                    }}
                  >
                    {item.topicName}
                  </Typography>
                  {item.displayRank !== null && (
                    <Chip
                      label={`TUTOR PRIORITY ${item.displayRank}`}
                      size="small"
                      sx={{
                        height: 20,
                        bgcolor: "#33302A",
                        color: "#B5ADA2",
                        fontSize: 8.5,
                        fontWeight: 700,
                        letterSpacing: ".04em",
                      }}
                    />
                  )}
                </Box>
                <Typography
                  sx={{ color: "#A8A096", fontSize: 12, lineHeight: 1.55 }}
                >
                  {item.affectedStudentCount} of {item.activeStudentCount}{" "}
                  students need support; average mastery is{" "}
                  {Math.round(item.averageMasteryPercent)}%.
                </Typography>
                <Typography
                  sx={{
                    color: "#CFC7BC",
                    fontSize: 11.5,
                    lineHeight: 1.55,
                    mt: 0.75,
                  }}
                >
                  {item.suggestedAction}
                </Typography>
                {item.rankingNote && (
                  <Typography
                    sx={{
                      color: "#8F877D",
                      fontSize: 11,
                      lineHeight: 1.5,
                      borderTop: "1px solid #2C2925",
                      mt: 0.9,
                      pt: 0.8,
                    }}
                  >
                    Tutor note: {item.rankingNote}
                  </Typography>
                )}
              </Box>
            ))}
          </Box>
        ))}
      {insight && (
        <Box sx={{ borderTop: "1px solid #2C2925", mt: 1.5, pt: 1.25 }}>
          <Typography
            sx={{
              color: "#8F877D",
              fontSize: 10.5,
              fontWeight: 600,
              letterSpacing: ".09em",
            }}
          >
            TUTOR CONTEXT
          </Typography>
          {insight.feedback.length > 0 ? (
            insight.feedback.slice(-2).map((feedback) => (
              <Typography
                key={feedback.id}
                sx={{
                  color: "#CFC7BC",
                  fontSize: 11.5,
                  lineHeight: 1.55,
                  mt: 0.65,
                }}
              >
                {feedback.feedback}
              </Typography>
            ))
          ) : (
            <Typography
              sx={{
                color: "#8F877D",
                fontSize: 11.5,
                lineHeight: 1.55,
                mt: 0.65,
              }}
            >
              No tutor feedback has been recorded for this snapshot.
            </Typography>
          )}
          <Typography
            sx={{ color: "#6E665D", fontSize: 10.5, lineHeight: 1.5, mt: 0.9 }}
          >
            Saved tutor priorities and feedback are shown here. Editing them is
            not available in this view yet.
          </Typography>
        </Box>
      )}
      <Typography
        sx={{ color: "#6E665D", fontSize: 10.5, lineHeight: 1.5, mt: 1.25 }}
      >
        Derived from recorded class mastery. It is not a student record or an
        automatic assignment.
      </Typography>
    </Card>
  );
}

export default function ClassDetail({
  classId,
  loadClass = fetchTutorClassDetail,
  loadInsights = fetchTutorClassInsights,
}: ClassDetailProps) {
  const [detail, setDetail] = React.useState<TutorClassDetail | null>(null);
  const [error, setError] = React.useState<Error | null>(null);
  const [insight, setInsight] = React.useState<ClassInsightSnapshot | null>(
    null,
  );
  const [insightError, setInsightError] = React.useState<string | null>(null);
  const [insightLoading, setInsightLoading] = React.useState(false);
  const requestId = React.useRef(0);
  const insightRequestId = React.useRef(0);
  const validClassId = Number.isSafeInteger(classId) && classId > 0;
  const refreshInsights = React.useCallback(async () => {
    if (!validClassId) return;
    const currentRequest = ++insightRequestId.current;
    setInsight(null);
    setInsightError(null);
    setInsightLoading(true);
    try {
      const loaded = await loadInsights(classId);
      if (insightRequestId.current === currentRequest) setInsight(loaded);
    } catch (reason) {
      if (insightRequestId.current === currentRequest)
        setInsightError(
          reason instanceof Error
            ? reason.message
            : "Class insights could not be loaded. Please try again.",
        );
    } finally {
      if (insightRequestId.current === currentRequest) setInsightLoading(false);
    }
  }, [classId, loadInsights, validClassId]);
  const load = React.useCallback(async () => {
    if (!validClassId) return;
    const currentRequest = ++requestId.current;
    // Defer state reset so the mount effect begins an asynchronous request rather
    // than synchronously cascading a render.
    await Promise.resolve();
    if (requestId.current !== currentRequest) return;
    setDetail(null);
    setError(null);
    void loadClass(classId).then(
      (loaded) => {
        if (requestId.current === currentRequest) setDetail(loaded);
      },
      (reason: unknown) => {
        if (requestId.current === currentRequest)
          setError(
            reason instanceof Error
              ? reason
              : new Error("The class detail request could not be completed."),
          );
      },
    );
    void refreshInsights();
  }, [classId, loadClass, refreshInsights, validClassId]);

  React.useEffect(() => {
    if (!validClassId) return undefined;
    void load();
    return () => {
      requestId.current += 1;
      insightRequestId.current += 1;
    };
  }, [load, validClassId]);

  const heading = detail?.className ?? "Class details";
  const detailError = error ? classDetailErrorCopy(error) : null;
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
          href="/classes"
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
          ALL CLASSES
        </Box>
        {!validClassId ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{
              ...card,
              maxWidth: 620,
              p: 3,
              borderLeft: "3px solid #B4573F",
            }}
          >
            <Typography
              component="h1"
              sx={{
                fontFamily: serif,
                fontSize: 24,
                fontWeight: 500,
                mb: 0.75,
              }}
            >
              Class cannot be opened
            </Typography>
            <Typography
              sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6 }}
            >
              This class reference is invalid. Return to your classes and choose
              a class to open.
            </Typography>
          </Card>
        ) : detailError ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{
              ...card,
              maxWidth: 620,
              p: 3,
              borderLeft: "3px solid #B4573F",
            }}
          >
            <Typography
              component="h1"
              sx={{
                fontFamily: serif,
                fontSize: 24,
                fontWeight: 500,
                mb: 0.75,
              }}
            >
              {detailError.title}
            </Typography>
            <Typography
              sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
            >
              {detailError.message}
            </Typography>
            {detailError.retryLabel === "Back to classes" ? (
              <Button
                component={Link}
                href="/classes"
                variant="outlined"
                sx={secondaryButton}
              >
                Back to classes
              </Button>
            ) : (
              <Button
                onClick={() => void load()}
                variant="outlined"
                sx={secondaryButton}
              >
                {detailError.retryLabel}
              </Button>
            )}
          </Card>
        ) : !detail ? (
          <ClassDetailSkeleton />
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
                  CLASS SUMMARY
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
                  {heading}
                </Typography>
                <Typography sx={{ color: "#6F675E", fontSize: 14, mt: 0.8 }}>
                  {detail.level} · {detail.subject} ·{" "}
                  {detail.status.toLowerCase()}
                </Typography>
                <Typography sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.55 }}>
                  Tutor account #{detail.tutorId}
                </Typography>
              </Box>
              <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
                <Button
                  component={Link}
                  href={`/classes/${detail.id}/edit`}
                  startIcon={<EditOutlinedIcon />}
                  variant="outlined"
                  sx={secondaryButton}
                >
                  Edit class
                </Button>
                <Button
                  component={Link}
                  href={`/students?classId=${detail.id}`}
                  startIcon={<GroupsOutlinedIcon />}
                  variant="outlined"
                  sx={secondaryButton}
                >
                  View students
                </Button>
                <Button
                  component={Link}
                  href={`/tutor/worksheets?classId=${detail.id}`}
                  startIcon={<MenuBookOutlinedIcon />}
                  variant="outlined"
                  sx={secondaryButton}
                >
                  View worksheets
                </Button>
              </Box>
            </Box>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
                gap: 1.75,
                mb: 2.5,
              }}
            >
              {[
                { label: "STUDENTS", value: detail.students.length },
                {
                  label: "CLASS MASTERY",
                  value: percent(detail.mastery.averageScore),
                },
                { label: "MASTERY RECORDS", value: detail.mastery.recordCount },
              ].map((metric) => (
                <Card
                  key={metric.label}
                  variant="outlined"
                  sx={{ ...card, p: 2.25 }}
                >
                  <Typography
                    sx={{
                      color: "#A09488",
                      fontSize: 10.5,
                      fontWeight: 600,
                      letterSpacing: ".09em",
                    }}
                  >
                    {metric.label}
                  </Typography>
                  <Typography
                    sx={{
                      fontFamily: serif,
                      fontSize: 32,
                      fontWeight: 500,
                      mt: 0.5,
                      fontVariantNumeric: "tabular-nums",
                    }}
                  >
                    {metric.value}
                  </Typography>
                </Card>
              ))}
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
                  flex: "1 1 520px",
                  minWidth: 0,
                  display: "grid",
                  gap: 2.5,
                }}
              >
                <Card
                  component="section"
                  aria-labelledby="students-heading"
                  variant="outlined"
                  sx={{ ...card, overflow: "hidden" }}
                >
                  <Typography
                    id="students-heading"
                    component="h2"
                    sx={{
                      p: "18px 22px",
                      borderBottom: "1px solid #EFE8DE",
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                    }}
                  >
                    Students
                  </Typography>
                  {detail.students.length === 0 ? (
                    <Box sx={{ p: 2.25 }}>
                      <EmptySection title="No students in this class">
                        Add or assign students to see their class mastery here.
                      </EmptySection>
                    </Box>
                  ) : (
                    <Box>
                      {detail.students.slice(0, 5).map((student, index) => (
                        <Box
                          component={Link}
                          href={`/students/${student.id}`}
                          key={student.id}
                          aria-label={`Open ${student.fullName}'s profile`}
                          sx={{
                            display: "flex",
                            alignItems: "center",
                            gap: 1.25,
                            p: "13px 22px",
                            color: "inherit",
                            textDecoration: "none",
                            borderBottom:
                              index === Math.min(detail.students.length, 5) - 1
                                ? "none"
                                : "1px solid #F3EDE4",
                            "&:hover": { bgcolor: "#FBF7F1" },
                            "&:focus-visible": {
                              outline: "3px solid #E08A72",
                              outlineOffset: -3,
                            },
                          }}
                        >
                          <Box
                            aria-hidden="true"
                            sx={{
                              width: 30,
                              height: 30,
                              borderRadius: "50%",
                              bgcolor: [
                                "#D8B384",
                                "#C6D0C4",
                                "#E3C3B4",
                                "#CFC0D6",
                                "#D9CBA8",
                              ][index % 5],
                              color: "#3A332C",
                              display: "grid",
                              placeItems: "center",
                              fontSize: 11,
                              fontWeight: 700,
                              flex: "0 0 auto",
                            }}
                          >
                            {student.fullName
                              .split(/\s+/)
                              .map((part) => part[0])
                              .slice(0, 2)
                              .join("")}
                          </Box>
                          <Box sx={{ minWidth: 0, flex: 1 }}>
                            <Typography
                              sx={{
                                fontSize: 13.5,
                                fontWeight: 500,
                                whiteSpace: "nowrap",
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                              }}
                            >
                              {student.fullName}
                            </Typography>
                            <Typography
                              sx={{ color: "#8B837A", fontSize: 11.5 }}
                            >
                              {student.masteryRecordCount} mastery{" "}
                              {student.masteryRecordCount === 1
                                ? "record"
                                : "records"}
                            </Typography>
                          </Box>
                          <Typography
                            sx={{
                              color: "#4A443D",
                              fontSize: 13,
                              fontWeight: 600,
                              fontVariantNumeric: "tabular-nums",
                            }}
                          >
                            {percent(student.overallMastery)}
                          </Typography>
                        </Box>
                      ))}
                      {detail.students.length > 5 && (
                        <Button
                          component={Link}
                          href={`/students?classId=${detail.id}`}
                          sx={{
                            minHeight: 40,
                            ml: 1.75,
                            mb: 1,
                            color: "#B4573F",
                            textTransform: "none",
                            fontSize: 12.5,
                            fontWeight: 600,
                          }}
                        >
                          View all {detail.students.length} students
                        </Button>
                      )}
                    </Box>
                  )}
                </Card>
                <Card
                  component="section"
                  aria-labelledby="weak-areas-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
                >
                  <Typography
                    id="weak-areas-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.5,
                    }}
                  >
                    Weak areas
                  </Typography>
                  {detail.weakAreas.length === 0 ? (
                    <EmptySection title="No weak areas identified">
                      Mastery results will surface focus topics when enough
                      class data is available.
                    </EmptySection>
                  ) : (
                    <Box sx={{ display: "grid", gap: 1.25 }}>
                      {detail.weakAreas.map((area) => (
                        <Box
                          key={area.topicId}
                          sx={{
                            border: "1px solid #F0DCD4",
                            bgcolor: "#FDF6F3",
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
                              color: "#9E3A24",
                              fontSize: 14,
                              fontWeight: 600,
                              fontVariantNumeric: "tabular-nums",
                            }}
                          >
                            {percent(area.averageScore)}
                          </Typography>
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography sx={{ fontSize: 13, fontWeight: 500 }}>
                              {area.topicName}
                            </Typography>
                            <LinearProgress
                              aria-label={`${area.topicName} average mastery ${percent(area.averageScore)}`}
                              variant="determinate"
                              value={area.averageScore}
                              sx={{
                                height: 5,
                                borderRadius: 20,
                                bgcolor: "#F0EAE0",
                                mt: 0.9,
                                ".MuiLinearProgress-bar": {
                                  bgcolor: barColor(area.averageScore),
                                  borderRadius: 20,
                                },
                              }}
                            />
                          </Box>
                          <Chip
                            label={`${area.affectedStudentCount} AFFECTED`}
                            size="small"
                            sx={{
                              height: 22,
                              bgcolor: "#F1D9D1",
                              color: "#9E3A24",
                              fontSize: 9.5,
                              fontWeight: 700,
                              letterSpacing: ".05em",
                            }}
                          />
                        </Box>
                      ))}
                    </Box>
                  )}
                </Card>
              </Box>
              <Box
                sx={{
                  flex: "0 1 330px",
                  minWidth: 0,
                  display: "grid",
                  gap: 2.5,
                }}
              >
                <ClassSchedule schedules={detail.schedules} />
                <ClassInsightPanel
                  availability={detail.insight}
                  insight={insight}
                  loading={insightLoading}
                  error={insightError}
                  onRetry={() => void refreshInsights()}
                />
                <Card
                  component="section"
                  aria-labelledby="worksheets-heading"
                  variant="outlined"
                  sx={{ ...card, p: { xs: 2, sm: 2.25 } }}
                >
                  <Typography
                    id="worksheets-heading"
                    component="h2"
                    sx={{
                      fontFamily: serif,
                      fontSize: 21,
                      fontWeight: 500,
                      mb: 1.5,
                    }}
                  >
                    Worksheets
                  </Typography>
                  {detail.worksheets.length === 0 ? (
                    <EmptySection title="No worksheets assigned">
                      Approved worksheets for this class will appear here.
                    </EmptySection>
                  ) : (
                    <Box sx={{ display: "grid", gap: 1.25 }}>
                      {detail.worksheets.map((worksheet) => (
                        <WorksheetRow
                          key={worksheet.id}
                          worksheet={worksheet}
                        />
                      ))}
                    </Box>
                  )}
                  <Box sx={{ mt: 1.5 }}>
                    <Button
                      component={Link}
                      href={`/tutor/worksheets/new?classId=${detail.id}`}
                      startIcon={<AutoAwesomeIcon />}
                      sx={{
                        minHeight: 40,
                        borderRadius: "10px",
                        bgcolor: "#E08A72",
                        color: "#1B1917",
                        textTransform: "none",
                        fontWeight: 600,
                        px: 1.75,
                        "&:hover": { bgcolor: "#D2795F" },
                      }}
                    >
                      Generate Worksheet
                    </Button>
                    <Typography
                      sx={{
                        color: "#8B837A",
                        fontSize: 11.5,
                        lineHeight: 1.55,
                        mt: 0.8,
                      }}
                    >
                      Choose covered topics and review the generated draft
                      before it is assigned.
                    </Typography>
                  </Box>
                </Card>
              </Box>
            </Box>
          </>
        )}
      </Box>
    </Box>
  );
}
