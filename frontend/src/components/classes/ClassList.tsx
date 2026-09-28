"use client";

import * as React from "react";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import SearchIcon from "@mui/icons-material/Search";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  fetchTutorClasses,
  type ClassStatus,
  type TutorClass,
} from "@/services/classes";

type StatusFilter = "ALL" | ClassStatus;

export interface ClassListProps {
  loadClasses?: () => Promise<TutorClass[]>;
}

const visuallyHidden = {
  position: "absolute",
  width: 1,
  height: 1,
  overflow: "hidden",
  clip: "rect(0 0 0 0)",
} as const;

function ClassStatusBadge({ status }: { status: ClassStatus }) {
  const active = status === "ACTIVE";
  return (
    <Chip
      label={status}
      size="small"
      sx={{
        height: 24,
        borderRadius: 20,
        bgcolor: active ? "#E4EDE4" : "#F0EAE0",
        color: active ? "#4A6B50" : "#6F675E",
        fontSize: 9.5,
        fontWeight: 700,
        letterSpacing: ".05em",
        ".MuiChip-label": { px: 1.1 },
      }}
    />
  );
}

function ClassCard({ item }: { item: TutorClass }) {
  const schedule =
    item.schedules.length === 0
      ? "Schedule to be confirmed"
      : item.schedules
          .map(
            ({ dayOfWeek, startTime, endTime }) =>
              `${dayOfWeek[0]}${dayOfWeek.slice(1).toLowerCase()} ${startTime}–${endTime}`,
          )
          .join(" · ");

  return (
    <Card
      component="article"
      variant="outlined"
      sx={{
        p: { xs: 2.25, sm: 2.5 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        color: "#2A2622",
        boxShadow: "none",
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
        minWidth: 0,
        transition: "border-color .18s, transform .18s",
        "&:hover": { borderColor: "#DCCFBE", transform: "translateY(-2px)" },
        "&:focus-visible": { outline: "3px solid #E08A72", outlineOffset: 2 },
      }}
    >
      <Stack
        direction="row"
        sx={{
          gap: 1,
          alignItems: "flex-start",
          justifyContent: "space-between",
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 21,
              fontWeight: 500,
              lineHeight: 1.2,
              textWrap: "pretty",
            }}
          >
            {item.className}
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 13, mt: 0.7 }}>
            {item.level}
          </Typography>
        </Box>
        <ClassStatusBadge status={item.status} />
      </Stack>
      <Box>
        <Chip
          label={item.subject.toUpperCase()}
          size="small"
          sx={{
            bgcolor:
              item.subject.toLowerCase() === "science" ? "#EAEDE7" : "#E6EAEF",
            color:
              item.subject.toLowerCase() === "science" ? "#4A6B50" : "#4E5C6E",
            fontSize: 9.5,
            fontWeight: 700,
            letterSpacing: ".06em",
            height: 24,
            borderRadius: 20,
          }}
        />
        <Typography
          sx={{
            color: "#8B837A",
            fontSize: 12.5,
            lineHeight: 1.55,
            mt: 1.25,
            textWrap: "pretty",
          }}
        >
          {schedule}
        </Typography>
      </Box>
      <Stack
        direction="row"
        sx={{
          flexWrap: "wrap",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 1,
          mt: "auto",
        }}
      >
        <Button
          component={Link}
          href={`/classes/${item.id}`}
          aria-label={`Open ${item.className}`}
          endIcon={
            <ArrowForwardIcon aria-hidden="true" sx={{ fontSize: 17 }} />
          }
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
          Open class summary
        </Button>
        <Button
          component={Link}
          href={`/classes/${item.id}/edit`}
          aria-label={`Edit ${item.className}`}
          sx={{
            minHeight: 34,
            border: "1px solid #E4DCD0",
            borderRadius: "9px",
            color: "#2A2622",
            bgcolor: "#FFFDFA",
            textTransform: "none",
            fontSize: 12.5,
            fontWeight: 500,
            px: 1.25,
            "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
          }}
        >
          Edit class
        </Button>
      </Stack>
    </Card>
  );
}

function ClassListSkeleton() {
  return (
    <Box
      data-testid="class-list-skeleton"
      aria-label="Loading classes"
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
            minHeight: 200,
            borderRadius: "14px",
            bgcolor: "#FFFDFA",
            borderColor: "#EBE4D9",
            boxShadow: "none",
          }}
        >
          <Skeleton
            variant="text"
            width="62%"
            height={34}
            sx={{ bgcolor: "#F0EAE0" }}
          />
          <Skeleton
            variant="text"
            width="38%"
            height={24}
            sx={{ bgcolor: "#F0EAE0", mt: 0.5 }}
          />
          <Skeleton
            variant="rounded"
            width="76"
            height={24}
            sx={{ bgcolor: "#F0EAE0", mt: 2.5 }}
          />
          <Skeleton
            variant="text"
            width="94%"
            height={24}
            sx={{ bgcolor: "#F0EAE0", mt: 1.5 }}
          />
        </Card>
      ))}
    </Box>
  );
}

export default function ClassList({
  loadClasses = fetchTutorClasses,
}: ClassListProps) {
  const [classes, setClasses] = React.useState<TutorClass[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [status, setStatus] = React.useState<StatusFilter>("ALL");
  const [query, setQuery] = React.useState("");

  const load = React.useCallback(async () => {
    setClasses(null);
    setError(null);
    try {
      setClasses(await loadClasses());
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Your classes could not be loaded. Please try again.",
      );
    }
  }, [loadClasses]);

  React.useEffect(() => {
    let current = true;
    const requestInitialClasses = async () => {
      try {
        const loaded = await loadClasses();
        if (current) setClasses(loaded);
      } catch (reason) {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "Your classes could not be loaded. Please try again.",
          );
      }
    };
    void requestInitialClasses();
    return () => {
      current = false;
    };
  }, [loadClasses]);

  const visibleClasses = React.useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (classes ?? []).filter((item) => {
      const matchesStatus = status === "ALL" || item.status === status;
      const matchesQuery =
        !normalizedQuery ||
        [item.className, item.subject, item.level].some((value) =>
          value.toLowerCase().includes(normalizedQuery),
        );
      return matchesStatus && matchesQuery;
    });
  }, [classes, query, status]);

  const clearFilters = () => {
    setStatus("ALL");
    setQuery("");
  };

  if (classes === null && !error) return <ClassListSkeleton />;

  if (error) {
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
          Classes could not be loaded
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
            "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
          }}
        >
          Retry loading classes
        </Button>
      </Card>
    );
  }

  return (
    <Box component="section" aria-labelledby="class-list-title">
      <Typography id="class-list-title" sx={visuallyHidden}>
        Class list
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
        {(["ALL", "ACTIVE", "INACTIVE"] as const).map((filter) => {
          const selected = status === filter;
          return (
            <Button
              key={filter}
              onClick={() => setStatus(filter)}
              aria-pressed={selected}
              sx={{
                minHeight: 34,
                borderRadius: 20,
                px: 1.75,
                color: selected ? "#9E3A24" : "#5A544C",
                bgcolor: selected ? "#F4E4DE" : "#FBF9F5",
                border: selected ? "1px solid #E0B9AC" : "1px solid #E4DCD0",
                textTransform: "none",
                fontSize: 12.5,
                fontWeight: 500,
                "&:hover": { bgcolor: selected ? "#F4E4DE" : "#F4EFE6" },
              }}
            >
              {filter === "ALL"
                ? "All classes"
                : `${filter[0]}${filter.slice(1).toLowerCase()}`}
            </Button>
          );
        })}
        <Box sx={{ flex: 1, minWidth: { xs: "100%", sm: 190 } }} />
        <TextField
          label="Search classes"
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
            minWidth: { xs: "100%", sm: 260 },
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
      </Box>

      {classes?.length === 0 ? (
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
              No classes yet
            </Typography>
            <Typography
              sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6, mb: 2 }}
            >
              Classes created for your account will appear here with their
              current schedule.
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
              Refresh classes
            </Button>
          </Box>
        </Card>
      ) : visibleClasses.length === 0 ? (
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
            No classes match these filters
          </Typography>
          <Typography
            sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6, mb: 2 }}
          >
            Try another search or include all class statuses.
          </Typography>
          <Button
            onClick={clearFilters}
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
            Clear filters
          </Button>
        </Card>
      ) : (
        <Box
          data-testid="class-grid"
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(310px, 1fr))",
            gap: 2,
          }}
        >
          {visibleClasses.map((item) => (
            <ClassCard key={item.id} item={item} />
          ))}
        </Box>
      )}
    </Box>
  );
}
