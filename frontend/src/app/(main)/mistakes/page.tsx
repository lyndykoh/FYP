"use client";

import * as React from "react";
import Link from "next/link";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutlined";
import HistoryIcon from "@mui/icons-material/History";
import {
  fetchStudentMistakes,
  type MistakeType,
  type StudentMistakeFilters,
  type StudentMistakeReview,
} from "@/services/submissions";

const card = {
  borderColor: "#EBE4D9",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
  boxShadow: "none",
};
const typeTone = {
  bgcolor: "#F7E3DC",
  color: "#9E3A24",
  borderColor: "#E0B9AC",
};
type Filters = {
  subjectId: string;
  topicId: string;
  mistakeType: string;
  worksheetId: string;
  from: string;
  to: string;
};
const initialFilters: Filters = {
  subjectId: "",
  topicId: "",
  mistakeType: "",
  worksheetId: "",
  from: "",
  to: "",
};

function dateLabel(value: string) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? "Recorded recently"
    : parsed.toLocaleDateString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
      });
}

function queryFor(filters: Filters): StudentMistakeFilters {
  const numberFilter = (value: string) => (value ? Number(value) : undefined);
  return {
    subjectId: numberFilter(filters.subjectId),
    topicId: numberFilter(filters.topicId),
    worksheetId: numberFilter(filters.worksheetId),
    mistakeType: filters.mistakeType
      ? (filters.mistakeType as MistakeType)
      : undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
  };
}

function SelectFilter({
  label,
  value,
  onChange,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: React.ReactNode;
}) {
  return (
    <label
      style={{
        display: "grid",
        gap: 5,
        minWidth: 152,
        color: "#6F675E",
        fontSize: 12,
        fontWeight: 700,
      }}
    >
      {label}
      <select
        aria-label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        style={{
          minHeight: 36,
          borderRadius: 7,
          border: "1px solid #DCCFBE",
          background: "#FFFDFA",
          color: "#2A2622",
          padding: "0 8px",
        }}
      >
        {children}
      </select>
    </label>
  );
}

export default function Page() {
  const [items, setItems] = React.useState<StudentMistakeReview[] | null>(null);
  const [facetItems, setFacetItems] = React.useState<StudentMistakeReview[]>(
    [],
  );
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [filters, setFilters] = React.useState<Filters>(initialFilters);
  const request = React.useMemo(() => queryFor(filters), [filters]);

  const load = React.useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const result = await fetchStudentMistakes(request);
      setItems(result);
      setFacetItems((previous) => {
        const known = new Map(previous.map((item) => [item.id, item]));
        result.forEach((item) => known.set(item.id, item));
        return [...known.values()];
      });
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Mistake history could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, [request]);

  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- filters intentionally refetch their persisted server-side view.
    void load();
  }, [load]);

  const updateFilter = (name: keyof Filters, value: string) =>
    setFilters((previous) => ({ ...previous, [name]: value }));
  const subjects = React.useMemo(
    () =>
      Array.from(
        new Map(
          facetItems
            .filter((item) => item.subjectId !== null && item.subjectName)
            .map((item) => [item.subjectId!, item.subjectName!]),
        ).entries(),
      ),
    [facetItems],
  );
  const topics = React.useMemo(
    () =>
      Array.from(
        new Map(
          facetItems
            .filter((item) => item.syllabusTopicId !== null)
            .map((item) => [
              item.syllabusTopicId!,
              `${item.syllabusTopicCode ? `${item.syllabusTopicCode} · ` : ""}${item.topicName ?? "Linked topic"}`,
            ]),
        ).entries(),
      ),
    [facetItems],
  );
  const types = React.useMemo(
    () =>
      Array.from(
        new Map(
          facetItems.map((item) => [item.mistakeType, item.mistakeLabel]),
        ).entries(),
      ),
    [facetItems],
  );
  const worksheets = React.useMemo(
    () =>
      Array.from(new Set(facetItems.map((item) => item.worksheetId))).sort(
        (a, b) => a - b,
      ),
    [facetItems],
  );
  const hasFilters = Object.values(filters).some(Boolean);

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        py: { xs: 2.5, sm: 4 },
        px: { xs: 1.5, sm: 3 },
      }}
    >
      <Box sx={{ maxWidth: 1040, mx: "auto" }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 700,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          APPROVED LEARNING HISTORY
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            color: "#2A2622",
            fontSize: { xs: 31, sm: 40 },
            lineHeight: 1.1,
          }}
        >
          Mistake review
        </Typography>
        <Typography
          sx={{
            color: "#6F675E",
            mt: 0.75,
            mb: 2,
            maxWidth: 670,
            lineHeight: 1.6,
          }}
        >
          These are the mistakes your Tutor has confirmed. Filter your real
          review history and revisit the linked work.
        </Typography>

        <Card
          variant="outlined"
          sx={{ ...card, p: 1.5, mb: 2.25 }}
          aria-label="Mistake history filters"
        >
          <Stack
            direction="row"
            spacing={1.25}
            useFlexGap
            sx={{ alignItems: "end", flexWrap: "wrap" }}
          >
            <SelectFilter
              label="Subject"
              value={filters.subjectId}
              onChange={(value) => updateFilter("subjectId", value)}
            >
              <option value="">All subjects</option>
              {subjects.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </SelectFilter>
            <SelectFilter
              label="Topic"
              value={filters.topicId}
              onChange={(value) => updateFilter("topicId", value)}
            >
              <option value="">All topics</option>
              {topics.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </SelectFilter>
            <SelectFilter
              label="Mistake type"
              value={filters.mistakeType}
              onChange={(value) => updateFilter("mistakeType", value)}
            >
              <option value="">All types</option>
              {types.map(([type, label]) => (
                <option key={type} value={type}>
                  {label}
                </option>
              ))}
            </SelectFilter>
            <SelectFilter
              label="Worksheet"
              value={filters.worksheetId}
              onChange={(value) => updateFilter("worksheetId", value)}
            >
              <option value="">All worksheets</option>
              {worksheets.map((id) => (
                <option key={id} value={id}>
                  Worksheet {id}
                </option>
              ))}
            </SelectFilter>
            <label
              style={{
                display: "grid",
                gap: 5,
                color: "#6F675E",
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              From date
              <input
                aria-label="From date"
                type="date"
                value={filters.from}
                onChange={(event) => updateFilter("from", event.target.value)}
                style={{
                  minHeight: 34,
                  borderRadius: 7,
                  border: "1px solid #DCCFBE",
                  padding: "0 8px",
                }}
              />
            </label>
            <label
              style={{
                display: "grid",
                gap: 5,
                color: "#6F675E",
                fontSize: 12,
                fontWeight: 700,
              }}
            >
              To date
              <input
                aria-label="To date"
                type="date"
                value={filters.to}
                onChange={(event) => updateFilter("to", event.target.value)}
                style={{
                  minHeight: 34,
                  borderRadius: 7,
                  border: "1px solid #DCCFBE",
                  padding: "0 8px",
                }}
              />
            </label>
            {hasFilters ? (
              <Button
                onClick={() => setFilters(initialFilters)}
                sx={{ color: "#9E3A24", textTransform: "none" }}
              >
                Clear filters
              </Button>
            ) : null}
          </Stack>
        </Card>

        {items === null && loading && !error ? (
          <Stack
            spacing={1.25}
            aria-label="Loading approved mistake history"
            data-testid="mistakes-loading"
          >
            {[1, 2, 3].map((index) => (
              <Card
                key={index}
                variant="outlined"
                sx={{ ...card, height: 140, bgcolor: "#F0EAE0" }}
              />
            ))}
          </Stack>
        ) : null}
        {error ? (
          <Card
            variant="outlined"
            sx={{
              ...card,
              borderLeft: "3px solid #B4573F",
              borderRadius: "0 10px 10px 0",
              p: 2.5,
              maxWidth: 620,
            }}
            role="alert"
          >
            <Typography sx={{ color: "#4A443D", lineHeight: 1.6 }}>
              {error}
            </Typography>
            <Button
              onClick={() => void load()}
              variant="outlined"
              sx={{
                mt: 1.5,
                borderColor: "#E4DCD0",
                color: "#2A2622",
                textTransform: "none",
              }}
            >
              Try again
            </Button>
          </Card>
        ) : null}
        {items !== null && !error ? (
          <>
            {loading ? (
              <Typography
                aria-live="polite"
                sx={{ color: "#8B837A", fontSize: 13, mb: 1 }}
              >
                Updating review history…
              </Typography>
            ) : null}
            {items.length === 0 ? (
              <Card
                variant="outlined"
                sx={{
                  ...card,
                  borderStyle: "dashed",
                  borderColor: "#DCCFBE",
                  px: 3,
                  py: 6,
                  textAlign: "center",
                }}
              >
                <CheckCircleOutlineIcon
                  aria-hidden="true"
                  sx={{ color: "#5C7A63", fontSize: 42, mb: 1 }}
                />
                <Typography
                  component="h2"
                  sx={{
                    fontFamily: "'Playfair Display', Georgia, serif",
                    fontSize: 22,
                    color: "#2A2622",
                  }}
                >
                  {hasFilters
                    ? "No mistakes match these filters"
                    : "No confirmed mistakes yet"}
                </Typography>
                <Typography sx={{ color: "#8B837A", mt: 0.75 }}>
                  {hasFilters
                    ? "Try changing or clearing a filter."
                    : "Your Tutor-approved learning history will appear here after a review."}
                </Typography>
                {hasFilters ? (
                  <Button
                    onClick={() => setFilters(initialFilters)}
                    sx={{ mt: 1.5, color: "#9E3A24", textTransform: "none" }}
                  >
                    Clear filters
                  </Button>
                ) : null}
              </Card>
            ) : (
              <Stack spacing={1.5}>
                {items.map((item) => (
                  <Card
                    key={item.id}
                    variant="outlined"
                    sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
                  >
                    <Stack
                      direction={{ xs: "column", sm: "row" }}
                      spacing={1}
                      sx={{ alignItems: { sm: "center" }, mb: 1.25 }}
                    >
                      <Chip
                        label={item.mistakeLabel}
                        size="small"
                        variant="outlined"
                        sx={{
                          ...typeTone,
                          fontWeight: 700,
                          alignSelf: "flex-start",
                        }}
                      />
                      <Typography sx={{ color: "#8B837A", fontSize: 12.5 }}>
                        {item.subjectName ? `${item.subjectName} · ` : ""}
                        {item.syllabusTopicCode ??
                          item.topicName ??
                          "Linked topic"}{" "}
                        · {dateLabel(item.recordedAt)}
                      </Typography>
                      <Chip
                        icon={<HistoryIcon />}
                        label="Tutor confirmed"
                        size="small"
                        sx={{
                          ml: { sm: "auto" },
                          alignSelf: "flex-start",
                          bgcolor: "#E4EDE4",
                          color: "#4A6B50",
                          fontWeight: 600,
                        }}
                      />
                    </Stack>
                    <Typography sx={{ color: "#4A443D", lineHeight: 1.65 }}>
                      {item.description}
                    </Typography>
                    <Typography
                      sx={{ color: "#6F675E", fontSize: 13, mt: 0.75 }}
                    >
                      {item.occurrenceCount === 1
                        ? "Recorded once"
                        : `Repeated ${item.occurrenceCount} times`}
                    </Typography>
                    <Stack direction="row" spacing={2} sx={{ mt: 0.5 }}>
                      <Button
                        component={Link}
                        href={`/worksheets/${item.worksheetId}`}
                        sx={{
                          px: 0,
                          minHeight: 32,
                          color: "#9E3A24",
                          textTransform: "none",
                        }}
                      >
                        View worksheet
                      </Button>
                      {item.syllabusTopicId ? (
                        <Button
                          component={Link}
                          href={`/topics/${item.syllabusTopicId}`}
                          sx={{
                            px: 0,
                            minHeight: 32,
                            color: "#9E3A24",
                            textTransform: "none",
                          }}
                        >
                          Review this topic
                        </Button>
                      ) : null}
                    </Stack>
                  </Card>
                ))}
              </Stack>
            )}
          </>
        ) : null}
      </Box>
    </Box>
  );
}
