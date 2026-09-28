"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import SearchIcon from "@mui/icons-material/Search";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import Link from "next/link";

import {
  fetchStudentWorksheets,
  type StudentWorksheet,
  type StudentWorksheetStatus,
} from "@/services/worksheets";

const INK = "#2A2622";
const MUTED = "#6F675E";
const BORDER = "#EBE4D9";
const CARD_BG = "#FFFDFA";
const ACTIVE_FILTER = {
  bgcolor: "#F4E4DE",
  borderColor: "#E0B9AC",
  color: "#9E3A24",
};
const INACTIVE_FILTER = {
  bgcolor: "#FBF9F5",
  borderColor: "#E4DCD0",
  color: "#5A544C",
};

type StatusFilter = "ALL" | StudentWorksheetStatus;

const statusFilters: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "ASSIGNED", label: "To do" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "MARKED", label: "Marked" },
];

function displayDate(value: string | null): string {
  if (value === null) return "No due date";
  const date = new Date(`${value}Z`);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, {
        day: "numeric",
        month: "short",
        year: "numeric",
      }).format(date);
}

function assignedDate(value: string): string {
  return value.slice(0, 10);
}

function statusBadge(status: StudentWorksheetStatus): {
  label: string;
  styles: Record<string, string>;
} {
  if (status === "MARKED")
    return {
      label: "MARKED",
      styles: { bgcolor: "#E4EDE4", color: "#4A6B50" },
    };
  if (status === "SUBMITTED")
    return {
      label: "SUBMITTED",
      styles: { bgcolor: "#F7E3DC", color: "#9E3A24" },
    };
  return {
    label: "ASSIGNED",
    styles: { bgcolor: "#F3EBDD", color: "#7A6238" },
  };
}

function matchesFilters(
  worksheet: StudentWorksheet,
  filters: {
    search: string;
    subjectId: string;
    topicId: string;
    status: StatusFilter;
    assignedFrom: string;
    assignedTo: string;
  },
): boolean {
  const search = filters.search.trim().toLocaleLowerCase();
  const titleAndMetadata =
    `${worksheet.title} ${worksheet.code} ${worksheet.subjects.map((subject) => subject.name).join(" ")} ${worksheet.topics.map((topic) => topic.name).join(" ")}`.toLocaleLowerCase();
  const assignmentDay = assignedDate(worksheet.assignedAt);
  return (
    (!search || titleAndMetadata.includes(search)) &&
    (filters.subjectId === "ALL" ||
      worksheet.subjects.some(
        (subject) => String(subject.id) === filters.subjectId,
      )) &&
    (filters.topicId === "ALL" ||
      worksheet.topics.some((topic) => String(topic.id) === filters.topicId)) &&
    (filters.status === "ALL" || worksheet.status === filters.status) &&
    (!filters.assignedFrom || assignmentDay >= filters.assignedFrom) &&
    (!filters.assignedTo || assignmentDay <= filters.assignedTo)
  );
}

function LoadingCards() {
  return (
    <Stack
      spacing={1.75}
      data-testid="student-worksheet-skeleton"
      aria-label="Loading worksheets"
    >
      {[1, 2, 3].map((item) => (
        <Card
          key={item}
          variant="outlined"
          sx={{
            borderColor: BORDER,
            bgcolor: CARD_BG,
            borderRadius: "12px",
            p: { xs: 2.25, sm: 2.75 },
          }}
        >
          <Box
            sx={{
              width: "28%",
              height: 18,
              borderRadius: 1,
              bgcolor: "#F0EAE0",
              mb: 1.25,
            }}
          />
          <Box
            sx={{
              width: "55%",
              height: 28,
              borderRadius: 1,
              bgcolor: "#F0EAE0",
              mb: 1,
            }}
          />
          <Box
            sx={{
              width: "72%",
              height: 16,
              borderRadius: 1,
              bgcolor: "#F0EAE0",
            }}
          />
        </Card>
      ))}
    </Stack>
  );
}

export default function StudentWorksheetsPage() {
  const [worksheets, setWorksheets] = React.useState<StudentWorksheet[] | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);
  const [search, setSearch] = React.useState("");
  const [subjectId, setSubjectId] = React.useState("ALL");
  const [topicId, setTopicId] = React.useState("ALL");
  const [status, setStatus] = React.useState<StatusFilter>("ALL");
  const [assignedFrom, setAssignedFrom] = React.useState("");
  const [assignedTo, setAssignedTo] = React.useState("");

  const load = React.useCallback(async () => {
    setWorksheets(null);
    setError(null);
    try {
      setWorksheets(await fetchStudentWorksheets());
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Your worksheets could not be loaded. Please try again.",
      );
    }
  }, []);

  React.useEffect(() => {
    void Promise.resolve().then(load);
  }, [load]);

  const subjects = React.useMemo(() => {
    const unique = new Map<number, string>();
    for (const worksheet of worksheets ?? [])
      worksheet.subjects.forEach((subject) =>
        unique.set(subject.id, subject.name),
      );
    return [...unique.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [worksheets]);
  const topics = React.useMemo(() => {
    const unique = new Map<number, string>();
    for (const worksheet of worksheets ?? [])
      worksheet.topics.forEach((topic) => unique.set(topic.id, topic.name));
    return [...unique.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [worksheets]);
  const filtered = React.useMemo(
    () =>
      (worksheets ?? []).filter((worksheet) =>
        matchesFilters(worksheet, {
          search,
          subjectId,
          topicId,
          status,
          assignedFrom,
          assignedTo,
        }),
      ),
    [worksheets, search, subjectId, topicId, status, assignedFrom, assignedTo],
  );
  const filtersActive = Boolean(
    search ||
    subjectId !== "ALL" ||
    topicId !== "ALL" ||
    status !== "ALL" ||
    assignedFrom ||
    assignedTo,
  );
  const clearFilters = () => {
    setSearch("");
    setSubjectId("ALL");
    setTopicId("ALL");
    setStatus("ALL");
    setAssignedFrom("");
    setAssignedTo("");
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 3.75 },
        py: { xs: 3, sm: 3.75 },
        color: INK,
      }}
    >
      <Box sx={{ maxWidth: 1120, mx: "auto" }}>
        <Typography
          component="p"
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          LEARNING LIBRARY
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 40 },
            fontWeight: 500,
            lineHeight: 1.12,
            letterSpacing: "-.025em",
            mb: 0.75,
          }}
        >
          My Worksheets
        </Typography>
        <Typography sx={{ fontSize: 14, color: MUTED, mb: 3.25 }}>
          Find assigned work, upload completed pages, and revisit approved
          results.
        </Typography>

        <Card
          variant="outlined"
          sx={{
            p: { xs: 1.5, sm: 2 },
            borderColor: BORDER,
            bgcolor: CARD_BG,
            borderRadius: "12px",
            boxShadow: "none",
            mb: 2.5,
          }}
        >
          <Stack spacing={1.5}>
            <Stack
              direction="row"
              sx={{ gap: 0.75, flexWrap: "wrap", alignItems: "center" }}
              aria-label="Worksheet status filter"
            >
              {statusFilters.map((filter) => (
                <Button
                  key={filter.value}
                  onClick={() => setStatus(filter.value)}
                  aria-pressed={status === filter.value}
                  sx={{
                    minHeight: 36,
                    border: "1px solid",
                    borderRadius: "20px",
                    px: 1.75,
                    textTransform: "none",
                    fontSize: 12.5,
                    fontWeight: 600,
                    ...(status === filter.value
                      ? ACTIVE_FILTER
                      : INACTIVE_FILTER),
                    "&:hover":
                      status === filter.value
                        ? { bgcolor: "#F1D9D1" }
                        : { bgcolor: "#F9F4EC" },
                  }}
                >
                  {filter.label}
                </Button>
              ))}
            </Stack>
            <Box
              data-testid="student-worksheet-filter-grid"
              sx={{
                display: "grid",
                gridTemplateColumns: {
                  xs: "1fr",
                  sm: "minmax(210px, 1.4fr) repeat(2, minmax(150px, 1fr))",
                },
                gap: 1.25,
              }}
            >
              <TextField
                label="Search worksheets"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                size="small"
                slotProps={{
                  input: {
                    startAdornment: (
                      <SearchIcon
                        aria-hidden="true"
                        sx={{ color: "#A09488", fontSize: 18, mr: 0.75 }}
                      />
                    ),
                  },
                }}
                sx={{
                  "& .MuiOutlinedInput-root": {
                    bgcolor: "#FBF9F5",
                    borderRadius: "9px",
                  },
                }}
              />
              <FormControl size="small">
                <InputLabel id="worksheet-subject-label">Subject</InputLabel>
                <Select
                  labelId="worksheet-subject-label"
                  label="Subject"
                  value={subjectId}
                  onChange={(event) => setSubjectId(event.target.value)}
                  sx={{ bgcolor: "#FBF9F5", borderRadius: "9px" }}
                >
                  <MenuItem value="ALL">All subjects</MenuItem>
                  {subjects.map((subject) => (
                    <MenuItem key={subject.id} value={String(subject.id)}>
                      {subject.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small">
                <InputLabel id="worksheet-topic-label">Topic</InputLabel>
                <Select
                  labelId="worksheet-topic-label"
                  label="Topic"
                  value={topicId}
                  onChange={(event) => setTopicId(event.target.value)}
                  sx={{ bgcolor: "#FBF9F5", borderRadius: "9px" }}
                >
                  <MenuItem value="ALL">All topics</MenuItem>
                  {topics.map((topic) => (
                    <MenuItem key={topic.id} value={String(topic.id)}>
                      {topic.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>
            <Stack
              direction={{ xs: "column", sm: "row" }}
              sx={{ gap: 1.25, alignItems: { sm: "center" } }}
            >
              <TextField
                label="Assigned from"
                type="date"
                value={assignedFrom}
                onChange={(event) => setAssignedFrom(event.target.value)}
                size="small"
                slotProps={{ inputLabel: { shrink: true } }}
                sx={{
                  width: { sm: 180 },
                  "& .MuiOutlinedInput-root": {
                    bgcolor: "#FBF9F5",
                    borderRadius: "9px",
                  },
                }}
              />
              <TextField
                label="Assigned to"
                type="date"
                value={assignedTo}
                onChange={(event) => setAssignedTo(event.target.value)}
                size="small"
                slotProps={{ inputLabel: { shrink: true } }}
                sx={{
                  width: { sm: 180 },
                  "& .MuiOutlinedInput-root": {
                    bgcolor: "#FBF9F5",
                    borderRadius: "9px",
                  },
                }}
              />
              <Box sx={{ flex: 1 }} />
              {filtersActive && (
                <Button
                  onClick={clearFilters}
                  sx={{
                    minHeight: 36,
                    alignSelf: { xs: "flex-start", sm: "center" },
                    color: "#9E3A24",
                    textTransform: "none",
                    fontSize: 12.5,
                    fontWeight: 600,
                  }}
                >
                  Clear filters
                </Button>
              )}
            </Stack>
          </Stack>
        </Card>

        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              borderColor: BORDER,
              borderLeft: "3px solid #B4573F",
              borderRadius: "0 12px 12px 0",
              bgcolor: "#F6EFE6",
              p: 2.5,
            }}
          >
            <Typography sx={{ color: "#5A544C", fontSize: 14, mb: 1.25 }}>
              {error}
            </Typography>
            <Button
              onClick={() => void load()}
              sx={{
                minHeight: 36,
                color: "#9E3A24",
                textTransform: "none",
                fontWeight: 600,
              }}
            >
              Retry loading worksheets
            </Button>
          </Card>
        ) : worksheets === null ? (
          <LoadingCards />
        ) : worksheets.length === 0 ? (
          <Card
            variant="outlined"
            sx={{
              border: "1px dashed #DCCFBE",
              borderRadius: "12px",
              bgcolor: CARD_BG,
              py: 7,
              px: 3,
              textAlign: "center",
            }}
          >
            <Box
              sx={{
                width: 46,
                height: 46,
                mx: "auto",
                mb: 1.75,
                borderRadius: "14px",
                bgcolor: "#F4EFE6",
                display: "grid",
                placeItems: "center",
                color: "#8B837A",
              }}
            >
              <DescriptionOutlinedIcon />
            </Box>
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
              }}
            >
              No worksheets have been assigned yet
            </Typography>
            <Typography sx={{ color: "#8B837A", fontSize: 13, mt: 0.75 }}>
              When your tutor approves work for you, it will appear here.
            </Typography>
          </Card>
        ) : filtered.length === 0 ? (
          <Card
            variant="outlined"
            sx={{
              border: "1px dashed #DCCFBE",
              borderRadius: "12px",
              bgcolor: CARD_BG,
              py: 6,
              px: 3,
              textAlign: "center",
            }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
              }}
            >
              No worksheets match those filters
            </Typography>
            <Typography
              sx={{ color: "#8B837A", fontSize: 13, mt: 0.75, mb: 1.5 }}
            >
              Try another subject, topic, status, or assigned date.
            </Typography>
            <Button
              onClick={clearFilters}
              sx={{
                minHeight: 36,
                color: "#9E3A24",
                textTransform: "none",
                fontWeight: 600,
              }}
            >
              Clear filters
            </Button>
          </Card>
        ) : (
          <Stack
            spacing={1.5}
            aria-label="Worksheet library"
            data-testid="student-worksheet-list"
          >
            {filtered.map((worksheet) => {
              const badge = statusBadge(worksheet.status);
              return (
                <Card
                  key={worksheet.id}
                  component="article"
                  variant="outlined"
                  sx={{
                    borderColor: BORDER,
                    borderLeft:
                      worksheet.status === "MARKED"
                        ? "3px solid #93A896"
                        : worksheet.status === "SUBMITTED"
                          ? "3px solid #E08A72"
                          : "3px solid #D8B77A",
                    borderRadius: "12px",
                    bgcolor: CARD_BG,
                    boxShadow: "none",
                    px: { xs: 2, sm: 2.75 },
                    py: { xs: 2, sm: 2.25 },
                    display: "flex",
                    gap: 2,
                    alignItems: { sm: "center" },
                    flexWrap: "wrap",
                  }}
                >
                  <Box sx={{ flex: "1 1 340px", minWidth: 0 }}>
                    <Stack
                      direction="row"
                      sx={{
                        alignItems: "center",
                        flexWrap: "wrap",
                        gap: 0.75,
                        mb: 0.75,
                      }}
                    >
                      <Chip
                        label={badge.label}
                        size="small"
                        sx={{
                          ...badge.styles,
                          fontSize: 9.5,
                          fontWeight: 700,
                          letterSpacing: ".05em",
                          height: 24,
                        }}
                      />
                      <Typography sx={{ color: "#8B837A", fontSize: 12 }}>
                        {worksheet.subjects
                          .map((subject) => subject.name)
                          .join(", ")}{" "}
                        ·{" "}
                        {worksheet.topics.map((topic) => topic.name).join(", ")}
                      </Typography>
                    </Stack>
                    <Typography
                      component={Link}
                      href={`/worksheets/${worksheet.id}`}
                      sx={{
                        display: "inline-block",
                        fontFamily: "'Playfair Display', Georgia, serif",
                        fontSize: { xs: 21, sm: 23 },
                        fontWeight: 500,
                        lineHeight: 1.2,
                        color: INK,
                        textDecoration: "none",
                        "&:hover": {
                          color: "#9E3A24",
                          textDecoration: "underline",
                        },
                      }}
                    >
                      {worksheet.title}
                    </Typography>
                    <Typography sx={{ color: MUTED, fontSize: 12.5, mt: 0.9 }}>
                      Assigned {displayDate(worksheet.assignedAt)} ·{" "}
                      {worksheet.dueAt
                        ? `Due ${displayDate(worksheet.dueAt)}`
                        : "No due date"}
                    </Typography>
                    {worksheet.status === "SUBMITTED" && (
                      <Typography
                        sx={{ color: "#9E3A24", fontSize: 12.5, mt: 0.65 }}
                      >
                        Submitted {displayDate(worksheet.submittedAt)} · Waiting
                        for tutor review
                      </Typography>
                    )}
                    {worksheet.status === "MARKED" && (
                      <Typography
                        sx={{ color: "#4A6B50", fontSize: 12.5, mt: 0.65 }}
                      >
                        Reviewed {displayDate(worksheet.reviewedAt)}
                      </Typography>
                    )}
                  </Box>
                  {worksheet.status === "MARKED" && worksheet.score && (
                    <Box
                      sx={{
                        minWidth: 94,
                        textAlign: { xs: "left", sm: "center" },
                      }}
                    >
                      <Typography
                        sx={{
                          fontFamily: "'Playfair Display', Georgia, serif",
                          color: INK,
                          fontSize: 29,
                          lineHeight: 1,
                        }}
                      >
                        {Math.round(worksheet.score.percent)}%
                      </Typography>
                      <Typography
                        sx={{ color: MUTED, fontSize: 11.5, mt: 0.35 }}
                      >
                        {worksheet.score.earned}/{worksheet.score.available}{" "}
                        marks
                      </Typography>
                    </Box>
                  )}
                  <Stack
                    direction="row"
                    useFlexGap
                    spacing={1}
                    sx={{ alignItems: "center", flexWrap: "wrap" }}
                  >
                    <Button
                      component={Link}
                      href={`/worksheets/${worksheet.id}`}
                      sx={{
                        minHeight: 40,
                        borderRadius: "9px",
                        border: "1px solid #E4DCD0",
                        color: "#5A544C",
                        px: 2,
                        textTransform: "none",
                        fontWeight: 600,
                        "&:hover": { bgcolor: "#F9F4EC" },
                      }}
                    >
                      Open worksheet
                    </Button>
                    {worksheet.status === "ASSIGNED" ? (
                      <Button
                        component={Link}
                        href={`/upload?ws=${worksheet.id}`}
                        startIcon={<UploadFileIcon />}
                        sx={{
                          minHeight: 40,
                          borderRadius: "9px",
                          bgcolor: "#9E3A24",
                          color: "#FFFDFA",
                          px: 2,
                          textTransform: "none",
                          fontWeight: 600,
                          "&:hover": { bgcolor: "#7F2D1D" },
                        }}
                      >
                        Upload work
                      </Button>
                    ) : null}
                    {worksheet.status === "MARKED" ? (
                      <Button
                        component={Link}
                        href={`/worksheets/${worksheet.id}/results`}
                        sx={{
                          minHeight: 40,
                          color: "#9E3A24",
                          px: 0.5,
                          textTransform: "none",
                          fontWeight: 600,
                        }}
                      >
                        View results
                      </Button>
                    ) : null}
                    {worksheet.status === "SUBMITTED" ? (
                      <Typography
                        role="status"
                        sx={{
                          color: "#9E3A24",
                          fontSize: 12.5,
                          fontWeight: 600,
                        }}
                      >
                        Awaiting tutor review
                      </Typography>
                    ) : null}
                  </Stack>
                </Card>
              );
            })}
          </Stack>
        )}
      </Box>
    </Box>
  );
}
