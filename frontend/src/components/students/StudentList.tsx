"use client";

import * as React from "react";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import SearchIcon from "@mui/icons-material/Search";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import MenuItem from "@mui/material/MenuItem";
import Skeleton from "@mui/material/Skeleton";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import { fetchTutorStudents, type TutorStudent } from "@/services/students";

export interface StudentListProps {
  classId?: number;
  loadStudents?: (classId?: number) => Promise<TutorStudent[]>;
}

const AVATAR_BACKGROUNDS = [
  "#D8B384",
  "#C6D0C4",
  "#E3C3B4",
  "#CFC0D6",
  "#D9CBA8",
  "#BFD0D6",
];

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function StudentCard({
  student,
  index,
}: {
  student: TutorStudent;
  index: number;
}) {
  const classSummary =
    student.classes.length === 0
      ? "Not assigned to a class yet"
      : student.classes.map((item) => item.className).join(" · ");
  return (
    <Card
      component="article"
      variant="outlined"
      sx={{
        p: { xs: 2.25, sm: 2.5 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        boxShadow: "none",
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
        minWidth: 0,
        transition: "border-color .18s, transform .18s",
        "&:hover": { borderColor: "#DCCFBE", transform: "translateY(-2px)" },
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "flex-start",
          gap: 1.25,
          minWidth: 0,
        }}
      >
        <Avatar
          sx={{
            width: 34,
            height: 34,
            flex: "0 0 auto",
            bgcolor: AVATAR_BACKGROUNDS[index % AVATAR_BACKGROUNDS.length],
            color: "#3A332C",
            fontSize: 11.5,
            fontWeight: 700,
          }}
        >
          {initials(student.fullName)}
        </Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 21,
              fontWeight: 500,
              lineHeight: 1.2,
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {student.fullName}
          </Typography>
          <Typography sx={{ color: "#8B837A", fontSize: 12, mt: 0.55 }}>
            {student.loginUserId
              ? "Linked student account"
              : "Tutor-managed profile"}
          </Typography>
        </Box>
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".11em",
            mb: 0.85,
          }}
        >
          CLASSES
        </Typography>
        {student.classes.length ? (
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.65 }}>
            {student.classes.slice(0, 3).map((item) => (
              <Chip
                key={item.id}
                label={item.className}
                size="small"
                sx={{
                  height: 24,
                  borderRadius: 20,
                  bgcolor: "#F4EFE6",
                  color: "#6F675E",
                  fontSize: 10.5,
                  fontWeight: 500,
                }}
              />
            ))}
            {student.classes.length > 3 ? (
              <Chip
                label={`+${student.classes.length - 3}`}
                size="small"
                sx={{
                  height: 24,
                  borderRadius: 20,
                  bgcolor: "#F0EAE0",
                  color: "#6F675E",
                  fontSize: 10.5,
                  fontWeight: 600,
                }}
              />
            ) : null}
          </Box>
        ) : (
          <Typography
            sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.55 }}
          >
            {classSummary}
          </Typography>
        )}
      </Box>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 1,
          mt: "auto",
        }}
      >
        <Typography sx={{ color: "#A09488", fontSize: 11.5 }}>
          Updated{" "}
          {new Intl.DateTimeFormat("en", {
            month: "short",
            day: "numeric",
            year: "numeric",
          }).format(new Date(student.updatedAt))}
        </Typography>
        <Box sx={{ display: "flex", alignItems: "center", gap: 0.25 }}>
          <Button
            component={Link}
            href={`/students/${student.id}`}
            aria-label={`View ${student.fullName}'s profile`}
            sx={{
              minHeight: 34,
              color: "#B4573F",
              textTransform: "none",
              fontSize: 12.5,
              fontWeight: 600,
              px: 0.5,
              "&:hover": { bgcolor: "#FDF6F3" },
            }}
          >
            View profile
          </Button>
          <Button
            component={Link}
            href={`/students/${student.id}/edit`}
            aria-label={`Edit ${student.fullName}`}
            endIcon={
              <ArrowForwardIcon aria-hidden="true" sx={{ fontSize: 17 }} />
            }
            sx={{
              minHeight: 34,
              color: "#6F675E",
              textTransform: "none",
              fontSize: 12.5,
              fontWeight: 600,
              px: 0.5,
              "&:hover": { bgcolor: "#F4EFE6" },
            }}
          >
            Edit
          </Button>
        </Box>
      </Box>
    </Card>
  );
}

function StudentListSkeleton() {
  return (
    <Box
      data-testid="student-list-skeleton"
      aria-label="Loading students"
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(310px, 1fr))",
        gap: 2,
      }}
    >
      {[0, 1, 2].map((index) => (
        <Card
          key={index}
          variant="outlined"
          sx={{
            p: 2.5,
            minHeight: 190,
            borderRadius: "14px",
            bgcolor: "#FFFDFA",
            borderColor: "#EBE4D9",
            boxShadow: "none",
          }}
        >
          <Skeleton
            variant="rounded"
            width="55%"
            height={28}
            sx={{ bgcolor: "#F0EAE0" }}
          />
          <Skeleton
            variant="text"
            width="78%"
            height={24}
            sx={{ bgcolor: "#F0EAE0", mt: 1.5 }}
          />
          <Skeleton
            variant="rounded"
            width="42%"
            height={24}
            sx={{ bgcolor: "#F0EAE0", mt: 1.5 }}
          />
        </Card>
      ))}
    </Box>
  );
}

export default function StudentList({
  classId,
  loadStudents = fetchTutorStudents,
}: StudentListProps) {
  const [students, setStudents] = React.useState<TutorStudent[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [query, setQuery] = React.useState("");
  const [classFilter, setClassFilter] = React.useState(
    classId ? String(classId) : "ALL",
  );

  const load = React.useCallback(async () => {
    setStudents(null);
    setError(null);
    try {
      setStudents(await loadStudents(classId));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Your students could not be loaded. Please try again.",
      );
    }
  }, [classId, loadStudents]);

  React.useEffect(() => {
    let current = true;
    const requestStudents = async () => {
      try {
        const loaded = await loadStudents(classId);
        if (current) setStudents(loaded);
      } catch (reason) {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "Your students could not be loaded. Please try again.",
          );
      }
    };
    void requestStudents();
    return () => {
      current = false;
    };
  }, [classId, loadStudents]);

  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the selected filter follows the route parameter.
    setClassFilter(classId ? String(classId) : "ALL");
  }, [classId]);

  const availableClasses = React.useMemo(() => {
    const items = new Map<number, string>();
    (students ?? []).forEach((student) =>
      student.classes.forEach((item) => items.set(item.id, item.className)),
    );
    return [...items.entries()].sort((left, right) =>
      left[1].localeCompare(right[1]),
    );
  }, [students]);
  const visibleStudents = React.useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (students ?? []).filter((student) => {
      const matchesQuery =
        !normalizedQuery ||
        [
          student.fullName,
          ...student.classes.map((item) => item.className),
        ].some((value) => value.toLowerCase().includes(normalizedQuery));
      const matchesClass =
        classFilter === "ALL" ||
        (classFilter === "UNASSIGNED"
          ? student.classes.length === 0
          : student.classes.some((item) => item.id === Number(classFilter)));
      return matchesQuery && matchesClass;
    });
  }, [students, classFilter, query]);

  if (students === null && !error) return <StudentListSkeleton />;
  if (error)
    return (
      <Card
        component="section"
        role="alert"
        variant="outlined"
        sx={{
          maxWidth: 560,
          p: 3,
          borderRadius: "14px",
          bgcolor: "#FFFDFA",
          borderColor: "#EBE4D9",
          borderLeft: "3px solid #B4573F",
          boxShadow: "none",
        }}
      >
        <Typography
          component="h2"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 21,
            fontWeight: 500,
            mb: 0.75,
          }}
        >
          Students could not be loaded
        </Typography>
        <Typography
          sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
        >
          {error}
        </Typography>
        <Button
          onClick={() => void load()}
          variant="outlined"
          sx={{
            minHeight: 40,
            borderColor: "#E4DCD0",
            bgcolor: "#FFFDFA",
            color: "#2A2622",
            textTransform: "none",
            fontWeight: 500,
            borderRadius: "10px",
            px: 2,
            "&:hover": { bgcolor: "#F4EFE6" },
          }}
        >
          Retry loading students
        </Button>
      </Card>
    );

  return (
    <Box component="section" aria-labelledby="student-list-title">
      <Typography
        id="student-list-title"
        sx={{
          position: "absolute",
          width: 1,
          height: 1,
          overflow: "hidden",
          clip: "rect(0 0 0 0)",
        }}
      >
        Student list
      </Typography>
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 1,
          mb: 2,
        }}
      >
        <TextField
          label="Search students"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          slotProps={{
            input: {
              startAdornment: (
                <SearchIcon
                  aria-hidden="true"
                  sx={{ fontSize: 18, color: "#A09488", mr: 1 }}
                />
              ),
            },
          }}
          sx={{
            flex: "1 1 240px",
            minWidth: 0,
            ".MuiOutlinedInput-root": {
              bgcolor: "#FFFDFA",
              borderRadius: "9px",
              fontSize: 13,
              "& fieldset": { borderColor: "#E4DCD0" },
              "&.Mui-focused fieldset": { borderColor: "#E08A72" },
            },
            ".MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
          }}
        />
        <TextField
          select
          label="Class filter"
          value={classFilter}
          onChange={(event) => setClassFilter(event.target.value)}
          sx={{
            minWidth: { xs: "100%", sm: 220 },
            ".MuiOutlinedInput-root": {
              bgcolor: "#FFFDFA",
              borderRadius: "9px",
              fontSize: 13,
              "& fieldset": { borderColor: "#E4DCD0" },
              "&.Mui-focused fieldset": { borderColor: "#E08A72" },
            },
            ".MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
          }}
        >
          <MenuItem value="ALL">All classes</MenuItem>
          <MenuItem value="UNASSIGNED">Unassigned</MenuItem>
          {availableClasses.map(([id, name]) => (
            <MenuItem key={id} value={id}>
              {name}
            </MenuItem>
          ))}
        </TextField>
      </Box>
      {students?.length === 0 ? (
        <Card
          variant="outlined"
          sx={{
            display: "grid",
            placeItems: "center",
            textAlign: "center",
            minHeight: 300,
            p: 3,
            borderRadius: "14px",
            borderStyle: "dashed",
            borderColor: "#DCCFBE",
            bgcolor: "#FFFDFA",
            boxShadow: "none",
          }}
        >
          <Box sx={{ maxWidth: 420 }}>
            <Box
              aria-hidden="true"
              sx={{
                width: 46,
                height: 46,
                mx: "auto",
                mb: 1.5,
                borderRadius: "50%",
                bgcolor: "#F4EFE6",
                color: "#B4573F",
                display: "grid",
                placeItems: "center",
                fontSize: 22,
              }}
            >
              +
            </Box>
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 21,
                fontWeight: 500,
                mb: 0.75,
              }}
            >
              No students yet
            </Typography>
            <Typography
              sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6, mb: 2 }}
            >
              Add a student, then select the classes they attend.
            </Typography>
            <Button
              component={Link}
              href="/students/new"
              sx={{
                minHeight: 40,
                borderRadius: "10px",
                bgcolor: "#9E3A24",
                color: "#FBF9F5",
                textTransform: "none",
                fontWeight: 500,
                px: 2,
              }}
            >
              Create student
            </Button>
          </Box>
        </Card>
      ) : null}
      {students && students.length > 0 && visibleStudents.length === 0 ? (
        <Card
          variant="outlined"
          sx={{
            p: 3,
            borderRadius: "14px",
            borderColor: "#EBE4D9",
            bgcolor: "#FFFDFA",
            boxShadow: "none",
          }}
        >
          <Typography
            component="h2"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 21,
              fontWeight: 500,
              mb: 0.75,
            }}
          >
            No students match these filters
          </Typography>
          <Typography
            sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6, mb: 2 }}
          >
            Try another name or class.
          </Typography>
          <Button
            onClick={() => {
              setQuery("");
              setClassFilter("ALL");
            }}
            variant="outlined"
            sx={{
              minHeight: 40,
              borderColor: "#E4DCD0",
              color: "#2A2622",
              textTransform: "none",
              fontWeight: 500,
              borderRadius: "10px",
              px: 2,
            }}
          >
            Clear filters
          </Button>
        </Card>
      ) : null}
      {visibleStudents.length > 0 ? (
        <Box
          data-testid="student-grid"
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(310px, 1fr))",
            gap: 2,
          }}
        >
          {visibleStudents.map((student, index) => (
            <StudentCard key={student.id} student={student} index={index} />
          ))}
        </Box>
      ) : null}
    </Box>
  );
}
