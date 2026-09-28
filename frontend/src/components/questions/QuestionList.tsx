"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import SyllabusPicker from "@/components/syllabus/SyllabusPicker";
import {
  fetchTutorQuestions,
  type QuestionArchiveState,
  type QuestionBankFilters,
  type QuestionBankItem,
  type QuestionBankPage,
  type QuestionDifficulty,
  type QuestionType,
} from "@/services/questions";
import type { SyllabusTree } from "@/services/syllabus";

export interface QuestionListProps {
  loadQuestions?: (filters: QuestionBankFilters) => Promise<QuestionBankPage>;
  loadSyllabus?: () => Promise<SyllabusTree>;
}

const serif = "'Playfair Display', Georgia, serif";
const questionTypes: readonly { value: QuestionType; label: string }[] = [
  { value: "MULTIPLE_CHOICE", label: "MCQ" },
  { value: "TRUE_FALSE", label: "True / false" },
  { value: "FILL_IN_THE_BLANK", label: "Fill in the blank" },
  { value: "SHORT_ANSWER", label: "Short answer" },
  { value: "OPEN_ENDED", label: "Open ended" },
  { value: "CALCULATION", label: "Calculation" },
  { value: "DIAGRAM", label: "Diagram" },
];
const difficulties: readonly { value: QuestionDifficulty; label: string }[] = [
  { value: "FOUNDATION", label: "Foundation" },
  { value: "APPLICATION", label: "Application" },
  { value: "CHALLENGE", label: "Challenge" },
];

function typeLabel(type: QuestionType) {
  return (
    questionTypes.find((item) => item.value === type)?.label ??
    type.replaceAll("_", " ")
  );
}

function QuestionListSkeleton() {
  return (
    <Box
      data-testid="question-list-skeleton"
      aria-label="Loading question bank"
      sx={{ display: "grid", gap: 1.25 }}
    >
      {[1, 2, 3].map((item) => (
        <Card
          key={item}
          variant="outlined"
          sx={{
            p: 2.25,
            borderRadius: "12px",
            bgcolor: "#FFFDFA",
            borderColor: "#EBE4D9",
            boxShadow: "none",
          }}
        >
          <Skeleton width="26%" height={22} sx={{ bgcolor: "#F0EAE0" }} />
          <Skeleton
            width="92%"
            height={42}
            sx={{ bgcolor: "#F0EAE0", mt: 1 }}
          />
          <Skeleton
            width="44%"
            height={21}
            sx={{ bgcolor: "#F0EAE0", mt: 1 }}
          />
        </Card>
      ))}
    </Box>
  );
}

function chipStyle(selected: boolean) {
  return {
    minHeight: 34,
    borderRadius: 20,
    px: 1.5,
    color: selected ? "#9E3A24" : "#5A544C",
    bgcolor: selected ? "#F4E4DE" : "#FBF9F5",
    border: selected ? "1px solid #E0B9AC" : "1px solid #E4DCD0",
    textTransform: "none",
    fontSize: 12.5,
    fontWeight: 500,
    "&:hover": { bgcolor: selected ? "#F4E4DE" : "#F4EFE6" },
  } as const;
}

function QuestionCard({
  item,
  selected,
  onToggle,
}: {
  item: QuestionBankItem;
  selected: boolean;
  onToggle: () => void;
}) {
  const selectable = item.archiveState === "ACTIVE";
  return (
    <Card
      component="article"
      variant="outlined"
      sx={{
        p: { xs: 2, sm: 2.25 },
        borderRadius: "12px",
        bgcolor: selected ? "#FDF6F3" : "#FFFDFA",
        borderColor: selected ? "#E0B9AC" : "#EBE4D9",
        boxShadow: "none",
        display: "flex",
        gap: 1.5,
        alignItems: "flex-start",
      }}
    >
      <Checkbox
        checked={selected}
        disabled={!selectable}
        onChange={onToggle}
        slotProps={{
          input: {
            "aria-label": selectable
              ? `Select ${item.code}`
              : `${item.code} is archived and unavailable for worksheets`,
          },
        }}
        sx={{
          mt: -0.75,
          ml: -0.8,
          color: "#A09488",
          "&.Mui-checked": { color: "#9E3A24" },
        }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            gap: 0.75,
            mb: 0.9,
          }}
        >
          <Chip
            label={typeLabel(item.questionType).toUpperCase()}
            size="small"
            sx={{
              height: 23,
              bgcolor: "#F0EAE0",
              color: "#6F675E",
              fontSize: 9.5,
              fontWeight: 700,
              letterSpacing: ".05em",
            }}
          />
          {item.difficulty && (
            <Chip
              label={item.difficulty}
              size="small"
              sx={{
                height: 23,
                bgcolor: "#F4E4DE",
                color: "#9E3A24",
                fontSize: 9.5,
                fontWeight: 700,
                letterSpacing: ".05em",
              }}
            />
          )}
          <Chip
            label={item.archiveState}
            size="small"
            sx={{
              height: 23,
              bgcolor: item.archiveState === "ACTIVE" ? "#E9EEE8" : "#F0EAE0",
              color: item.archiveState === "ACTIVE" ? "#4A6B50" : "#6F675E",
              fontSize: 9.5,
              fontWeight: 700,
              letterSpacing: ".05em",
            }}
          />
          <Typography sx={{ color: "#A09488", fontSize: 11.5 }}>
            {item.syllabusTopic.name}
          </Typography>
        </Box>
        <Typography
          sx={{
            fontSize: 13.5,
            lineHeight: 1.6,
            color: "#2A2622",
            whiteSpace: "pre-wrap",
          }}
        >
          {item.prompt}
        </Typography>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 1,
            mt: 0.8,
          }}
        >
          <Typography
            sx={{
              color: "#8B837A",
              fontSize: 11.5,
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {item.totalMarks.toFixed(1)} marks · {item.code} ·{" "}
            {item.syllabusTopic.nodeType.toLowerCase()}
          </Typography>
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.25 }}>
            <Button
              component={Link}
              href={`/questions/${item.id}`}
              size="small"
              sx={{
                minHeight: 32,
                color: "#5A544C",
                textTransform: "none",
                fontWeight: 500,
              }}
            >
              View question
            </Button>
            <Button
              component={Link}
              href={`/questions/${item.id}/edit`}
              size="small"
              sx={{
                minHeight: 32,
                color: "#9E3A24",
                textTransform: "none",
                fontWeight: 500,
              }}
            >
              Edit question
            </Button>
          </Box>
        </Box>
      </Box>
    </Card>
  );
}

export default function QuestionList({
  loadQuestions = fetchTutorQuestions,
  loadSyllabus,
}: QuestionListProps) {
  const [pageData, setPageData] = React.useState<QuestionBankPage | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [isLoading, setIsLoading] = React.useState(false);
  const [topicId, setTopicId] = React.useState<number | undefined>();
  const [questionType, setQuestionType] = React.useState<
    QuestionType | undefined
  >();
  const [difficulty, setDifficulty] = React.useState<
    QuestionDifficulty | undefined
  >();
  const [archiveState, setArchiveState] =
    React.useState<QuestionArchiveState>("ACTIVE");
  const [searchInput, setSearchInput] = React.useState("");
  const [search, setSearch] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [selectedIds, setSelectedIds] = React.useState<Set<number>>(
    () => new Set(),
  );
  const latestRequest = React.useRef(0);
  const initialSearchEffect = React.useRef(true);

  const filters = React.useMemo<QuestionBankFilters>(
    () => ({
      topicId,
      questionType,
      difficulty,
      archiveState,
      search: search || undefined,
      page,
      size: 12,
    }),
    [archiveState, difficulty, page, questionType, search, topicId],
  );
  const load = React.useCallback(async () => {
    const request = ++latestRequest.current;
    setError(null);
    setIsLoading(true);
    try {
      const response = await loadQuestions(filters);
      if (request === latestRequest.current) setPageData(response);
    } catch (reason) {
      if (request === latestRequest.current)
        setError(
          reason instanceof Error
            ? reason.message
            : "The question bank could not be loaded. Please try again.",
        );
    } finally {
      if (request === latestRequest.current) setIsLoading(false);
    }
  }, [filters, loadQuestions]);

  React.useEffect(() => {
    queueMicrotask(() => {
      void load();
    });
  }, [load]);
  React.useEffect(() => {
    if (initialSearchEffect.current) {
      initialSearchEffect.current = false;
      return;
    }
    const debounce = window.setTimeout(() => {
      const nextSearch = searchInput.trim();
      setSearch(nextSearch);
      setPage(0);
    }, 300);
    return () => window.clearTimeout(debounce);
  }, [searchInput]);

  const toggleSelection = (id: number) => {
    setSelectedIds((current) => {
      const next = new Set(current);

      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }

      return next;
    });
  };
  const clearFilters = () => {
    const alreadyDefault =
      topicId === undefined &&
      questionType === undefined &&
      difficulty === undefined &&
      archiveState === "ACTIVE" &&
      !searchInput &&
      !search &&
      page === 0;

    setTopicId(undefined);
    setQuestionType(undefined);
    setDifficulty(undefined);
    setArchiveState("ACTIVE");
    setSearchInput("");
    setSearch("");
    setPage(0);
    if (alreadyDefault) void load();
  };
  const changeArchive = (value: QuestionArchiveState) => {
    setArchiveState(value);
    setPage(0);
  };
  const changeType = (value: QuestionType | undefined) => {
    setQuestionType(value);
    setPage(0);
  };
  const changeDifficulty = (value: QuestionDifficulty | undefined) => {
    setDifficulty(value);
    setPage(0);
  };

  if (!pageData && !error) return <QuestionListSkeleton />;
  if (error)
    return (
      <Card
        component="section"
        role="alert"
        variant="outlined"
        sx={{
          maxWidth: 600,
          p: 3,
          borderRadius: "14px",
          borderColor: "#EBE4D9",
          borderLeft: "3px solid #B4573F",
          bgcolor: "#FFFDFA",
          boxShadow: "none",
        }}
      >
        <Typography
          component="h2"
          sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500, mb: 0.75 }}
        >
          Question bank could not be loaded
        </Typography>
        <Typography
          sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
        >
          {error}
        </Typography>
        <Button
          onClick={() => void load()}
          sx={{
            minHeight: 40,
            border: "1px solid #E4DCD0",
            borderRadius: "10px",
            color: "#2A2622",
            textTransform: "none",
            fontWeight: 500,
          }}
        >
          Retry loading questions
        </Button>
      </Card>
    );
  if (!pageData) return null;

  return (
    <Box
      component="section"
      aria-labelledby="question-list-title"
      aria-busy={isLoading}
    >
      <Typography
        id="question-list-title"
        sx={{
          position: "absolute",
          width: 1,
          height: 1,
          overflow: "hidden",
          clip: "rect(0 0 0 0)",
        }}
      >
        Question bank list
      </Typography>
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 1,
          mb: 1.25,
        }}
      >
        <Button
          onClick={() => changeArchive("ACTIVE")}
          aria-pressed={archiveState === "ACTIVE"}
          sx={chipStyle(archiveState === "ACTIVE")}
        >
          Active
        </Button>
        <Button
          onClick={() => changeArchive("ARCHIVED")}
          aria-pressed={archiveState === "ARCHIVED"}
          sx={chipStyle(archiveState === "ARCHIVED")}
        >
          Archived
        </Button>
        <Box sx={{ flex: 1, minWidth: { xs: "100%", sm: 120 } }} />
        <Typography
          aria-live="polite"
          sx={{ color: "#6F675E", fontSize: 12.5 }}
        >
          {selectedIds.size} selected
        </Typography>
        <Button
          component={Link}
          href="/questions/import"
          sx={{
            minHeight: 36,
            border: "1px solid #E4DCD0",
            borderRadius: "9px",
            color: "#5A544C",
            textTransform: "none",
            fontWeight: 500,
          }}
        >
          Import questions
        </Button>
        <Button
          component={Link}
          href="/questions/new"
          sx={{
            minHeight: 36,
            border: "1px solid #E0B9AC",
            borderRadius: "9px",
            bgcolor: "#FDF6F3",
            color: "#9E3A24",
            textTransform: "none",
            fontWeight: 500,
          }}
        >
          Add question
        </Button>
      </Box>
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "flex-end",
          gap: 1,
          mb: 2,
        }}
      >
        <Box sx={{ flex: "1 1 100%", minWidth: 0 }}>
          <TextField
            fullWidth
            label="Search questions"
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Search code, prompt, or keyword"
            slotProps={{ htmlInput: { maxLength: 120, type: "search" } }}
            helperText="Search matches code, prompt, and keywords."
            sx={{
              "& .MuiOutlinedInput-root": {
                bgcolor: "#FFFDFA",
                borderRadius: "20px",
                "& fieldset": { borderColor: "#EBE4D9" },
                "&:hover fieldset": { borderColor: "#DCCFBE" },
                "&.Mui-focused fieldset": { borderColor: "#E08A72" },
              },
              "& .MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
              "& .MuiFormHelperText-root": { color: "#8B837A", fontSize: 11.5 },
            }}
          />
        </Box>
        <Box sx={{ flex: "1 1 420px", minWidth: 0 }}>
          <SyllabusPicker
            value={topicId ?? null}
            onChange={(nextTopicId) => {
              setTopicId(nextTopicId ?? undefined);
              setPage(0);
            }}
            label="Syllabus filter"
            helperText="Filter questions by a topic or subtopic."
            loadSyllabus={loadSyllabus}
          />
        </Box>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 0.65,
            flex: "2 1 370px",
          }}
        >
          <Button
            onClick={() => changeType(undefined)}
            aria-pressed={!questionType}
            sx={chipStyle(!questionType)}
          >
            All types
          </Button>
          {questionTypes.map((type) => (
            <Button
              key={type.value}
              onClick={() => changeType(type.value)}
              aria-pressed={questionType === type.value}
              sx={chipStyle(questionType === type.value)}
            >
              {type.label}
            </Button>
          ))}
        </Box>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 0.65,
            flex: "1 1 260px",
          }}
        >
          <Button
            onClick={() => changeDifficulty(undefined)}
            aria-pressed={!difficulty}
            sx={chipStyle(!difficulty)}
          >
            All levels
          </Button>
          {difficulties.map((item) => (
            <Button
              key={item.value}
              onClick={() => changeDifficulty(item.value)}
              aria-pressed={difficulty === item.value}
              sx={chipStyle(difficulty === item.value)}
            >
              {item.label}
            </Button>
          ))}
        </Box>
      </Box>
      {pageData.items.length === 0 ? (
        <Card
          variant="outlined"
          sx={{
            minHeight: 260,
            display: "grid",
            placeItems: "center",
            textAlign: "center",
            p: 3,
            borderRadius: "14px",
            borderStyle: "dashed",
            borderColor: "#DCCFBE",
            bgcolor: "#FFFDFA",
            boxShadow: "none",
          }}
        >
          <Box sx={{ maxWidth: 430 }}>
            <Typography
              component="h2"
              sx={{
                fontFamily: serif,
                fontSize: 22,
                fontWeight: 500,
                mb: 0.75,
              }}
            >
              No questions match these filters
            </Typography>
            <Typography
              sx={{ color: "#8B837A", fontSize: 13, lineHeight: 1.6, mb: 1.75 }}
            >
              Try another topic, question type, or archive state.
            </Typography>
            <Button
              onClick={clearFilters}
              sx={{
                minHeight: 40,
                border: "1px solid #E4DCD0",
                borderRadius: "10px",
                color: "#2A2622",
                textTransform: "none",
                fontWeight: 500,
              }}
            >
              Clear filters
            </Button>
          </Box>
        </Card>
      ) : (
        <>
          <Box sx={{ display: "grid", gap: 1.25 }}>
            {pageData.items.map((item) => (
              <QuestionCard
                key={item.id}
                item={item}
                selected={selectedIds.has(item.id)}
                onToggle={() => toggleSelection(item.id)}
              />
            ))}
          </Box>
          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              justifyContent: "space-between",
              alignItems: "center",
              gap: 1.25,
              mt: 2,
            }}
          >
            <Typography sx={{ color: "#8B837A", fontSize: 12.5 }}>
              Page {pageData.page + 1} of {Math.max(pageData.totalPages, 1)} ·{" "}
              {pageData.totalElements} questions
            </Typography>
            <Box sx={{ display: "flex", gap: 0.75 }}>
              <Button
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={pageData.page === 0}
                sx={{
                  minHeight: 38,
                  border: "1px solid #E4DCD0",
                  borderRadius: "9px",
                  color: "#2A2622",
                  textTransform: "none",
                  "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#B5AA9C" },
                }}
              >
                Previous
              </Button>
              <Button
                onClick={() => setPage((current) => current + 1)}
                disabled={!pageData.hasNext}
                sx={{
                  minHeight: 38,
                  border: "1px solid #E4DCD0",
                  borderRadius: "9px",
                  color: "#2A2622",
                  textTransform: "none",
                  "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#B5AA9C" },
                }}
              >
                Next
              </Button>
            </Box>
          </Box>
        </>
      )}
      {isLoading && pageData ? (
        <Typography
          role="status"
          sx={{
            position: "absolute",
            width: 1,
            height: 1,
            overflow: "hidden",
            clip: "rect(0 0 0 0)",
          }}
        >
          Updating question results
        </Typography>
      ) : null}
    </Box>
  );
}
