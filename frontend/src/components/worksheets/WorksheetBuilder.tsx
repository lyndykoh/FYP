"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { fetchTutorClasses, type TutorClass } from "@/services/classes";
import {
  fetchTutorQuestions,
  type QuestionBankItem,
  type QuestionDifficulty,
  type QuestionType,
} from "@/services/questions";
import {
  fetchSyllabusTree,
  type SyllabusNode,
  type SyllabusTree,
} from "@/services/syllabus";
import { fetchTutorStudents, type TutorStudent } from "@/services/students";
import {
  approveWorksheet,
  fetchDiagnosticRecommendations,
  generateDiagnosticWorksheet,
  generateWorksheet,
  updateWorksheet,
  type DiagnosticRecommendations,
  type TutorWorksheet,
  type WorksheetTargetMode,
} from "@/services/worksheets";

const steps = ["Select", "Configure", "AI Preview", "Edit", "Export"];
const ANY_FILTER = "ANY";
const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;
const secondary = {
  border: "1px solid #E4DCD0",
  borderRadius: "9px",
  color: "#2A2622",
  textTransform: "none",
  fontWeight: 500,
  bgcolor: "#FFFDFA",
  "&:hover": { bgcolor: "#F4EFE6" },
} as const;
const randomKey = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `worksheet-${Date.now()}-${Math.random().toString(36).slice(2)}`;

function recommendationCopy(
  item: DiagnosticRecommendations["recommendations"][number],
) {
  if (item.reason === "NEW_TOPIC")
    return `${item.topicName} is covered but has no approved attempts yet.`;
  if (item.reason === "LOW_MASTERY")
    return `${item.topicName} is at ${Math.round(item.masteryPercent ?? 0)}% after ${item.attemptCount} approved attempt${item.attemptCount === 1 ? "" : "s"}.`;
  return `${item.topicName} needs a retrieval check after ${item.attemptCount} approved attempt${item.attemptCount === 1 ? "" : "s"}.`;
}

function findSyllabusNode(
  nodes: SyllabusNode[],
  nodeId: number,
): SyllabusNode | null {
  for (const node of nodes) {
    if (node.id === nodeId) return node;
    const child = findSyllabusNode(node.children, nodeId);
    if (child) return child;
  }
  return null;
}

function syllabusNodesOfType(
  nodes: SyllabusNode[],
  nodeType: SyllabusNode["nodeType"],
): SyllabusNode[] {
  return nodes.flatMap((node) => [
    ...(node.nodeType === nodeType ? [node] : []),
    ...syllabusNodesOfType(node.children, nodeType),
  ]);
}

function questionTopicNodes(nodes: SyllabusNode[]): SyllabusNode[] {
  return nodes.flatMap((node) => {
    const childTopics = questionTopicNodes(node.children);
    if (childTopics.length > 0) return childTopics;
    return node.nodeType === "TOPIC" || node.nodeType === "SUBTOPIC"
      ? [node]
      : [];
  });
}

/** A tutor-controlled draft: machine/evidence output remains distinct from approval. */
export function WorksheetBuilder({
  classId,
  generate = generateWorksheet,
  generateDiagnostic = generateDiagnosticWorksheet,
  approve = approveWorksheet,
  update = updateWorksheet,
  loadSyllabus,
  loadStudents = fetchTutorStudents,
  loadClasses = fetchTutorClasses,
  loadDiagnostic = fetchDiagnosticRecommendations,
  loadQuestions = fetchTutorQuestions,
  initialStudentId,
  onApproved,
}: {
  classId?: number;
  generate?: typeof generateWorksheet;
  generateDiagnostic?: typeof generateDiagnosticWorksheet;
  approve?: typeof approveWorksheet;
  update?: typeof updateWorksheet;
  loadSyllabus?: () => Promise<SyllabusTree>;
  loadStudents?: (classId?: number) => Promise<TutorStudent[]>;
  loadClasses?: () => Promise<TutorClass[]>;
  loadDiagnostic?: (classId: number) => Promise<DiagnosticRecommendations>;
  loadQuestions?: typeof fetchTutorQuestions;
  initialStudentId?: number;
  onApproved?: (worksheet: TutorWorksheet) => void;
}) {
  const [syllabus, setSyllabus] = React.useState<SyllabusTree | null>(null);
  const [syllabusError, setSyllabusError] = React.useState<string | null>(null);
  const [themeId, setThemeId] = React.useState<number | null>(null);
  const [topicId, setTopicId] = React.useState<number | null>(null);
  const [questionType, setQuestionType] = React.useState<QuestionType | "">("");
  const [difficulty, setDifficulty] = React.useState<QuestionDifficulty | "">(
    "",
  );
  const [count, setCount] = React.useState("15");
  const [title, setTitle] = React.useState("");
  const [instructions, setInstructions] = React.useState("");
  const [dueAt, setDueAt] = React.useState("");
  const validInitialClassId =
    Number.isSafeInteger(classId) && (classId ?? 0) > 0
      ? (classId ?? null)
      : null;
  const validInitialStudentId =
    Number.isSafeInteger(initialStudentId) && (initialStudentId ?? 0) > 0
      ? initialStudentId
      : null;
  const [targetMode, setTargetMode] = React.useState<WorksheetTargetMode>(
    validInitialStudentId ? "STUDENTS" : "CLASS",
  );
  const [selectedClassId, setSelectedClassId] = React.useState<number | null>(
    validInitialClassId,
  );
  const [classes, setClasses] = React.useState<TutorClass[]>([]);
  const [classLoadState, setClassLoadState] = React.useState<
    "loading" | "ready" | "error"
  >("loading");
  const [classError, setClassError] = React.useState<string | null>(null);
  const [classLoadAttempt, setClassLoadAttempt] = React.useState(0);
  const [students, setStudents] = React.useState<TutorStudent[]>([]);
  const [studentLoadState, setStudentLoadState] = React.useState<
    "idle" | "loading" | "ready" | "error"
  >("idle");
  const [studentError, setStudentError] = React.useState<string | null>(null);
  const [studentLoadAttempt, setStudentLoadAttempt] = React.useState(0);
  const [selectedStudents, setSelectedStudents] = React.useState<number[]>(
    validInitialStudentId ? [validInitialStudentId] : [],
  );
  const [configurationOpen, setConfigurationOpen] = React.useState(false);
  const [diagnostic, setDiagnostic] = React.useState(false);
  const [recommendations, setRecommendations] =
    React.useState<DiagnosticRecommendations | null>(null);
  const [draft, setDraft] = React.useState<TutorWorksheet | null>(null);
  const [bank, setBank] = React.useState<QuestionBankItem[]>([]);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const approvalInProgress = React.useRef(false);

  React.useEffect(() => {
    let current = true;
    void Promise.resolve().then(async () => {
      try {
        const loaded = await (loadSyllabus ?? fetchSyllabusTree)();
        if (current && loaded) setSyllabus(loaded);
      } catch (reason) {
        if (!current) return;
        setSyllabusError(
          reason instanceof Error
            ? reason.message
            : "Syllabus filters could not be loaded. You can still generate from all questions.",
        );
      }
    });
    return () => {
      current = false;
    };
  }, [loadSyllabus]);

  React.useEffect(() => {
    let current = true;
    void Promise.resolve().then(async () => {
      if (!current) return;
      setClassLoadState("loading");
      setClassError(null);
      try {
        const loaded = await loadClasses();
        if (!current) return;
        setClasses(loaded);
        setClassLoadState("ready");
        if (
          validInitialClassId &&
          !loaded.some((item) => item.id === validInitialClassId)
        ) {
          setSelectedClassId(null);
          setClassError(
            "The requested class is unavailable. Choose one of your current classes.",
          );
        }
      } catch (reason) {
        if (!current) return;
        setClasses([]);
        setClassLoadState("error");
        setClassError(
          reason instanceof Error
            ? reason.message
            : "Classes could not be loaded.",
        );
      }
    });
    return () => {
      current = false;
    };
  }, [classLoadAttempt, loadClasses, validInitialClassId]);

  React.useEffect(() => {
    let current = true;
    if (targetMode !== "STUDENTS" || !selectedClassId) {
      void Promise.resolve().then(() => {
        if (!current) return;
        setStudents([]);
        setStudentLoadState("idle");
        setStudentError(null);
      });
      return () => {
        current = false;
      };
    }
    void Promise.resolve().then(async () => {
      if (!current) return;
      setStudentLoadState("loading");
      setStudentError(null);
      try {
        const loaded = await loadStudents(selectedClassId);
        if (!current) return;
        setStudents(loaded);
        setStudentLoadState("ready");
        if (
          validInitialStudentId &&
          !loaded.some((student) => student.id === validInitialStudentId)
        ) {
          setSelectedStudents((selected) =>
            selected.filter((studentId) => studentId !== validInitialStudentId),
          );
          setStudentError(
            "This student is not an active member of the selected class.",
          );
        }
      } catch (reason) {
        if (!current) return;
        setStudents([]);
        setStudentLoadState("error");
        setStudentError(
          reason instanceof Error
            ? reason.message
            : "Students could not be loaded.",
        );
      }
    });
    return () => {
      current = false;
    };
  }, [
    loadStudents,
    selectedClassId,
    studentLoadAttempt,
    targetMode,
    validInitialStudentId,
  ]);
  const themeOptions = React.useMemo(
    () => syllabusNodesOfType(syllabus?.items ?? [], "THEME"),
    [syllabus],
  );
  const topicOptions = React.useMemo(() => {
    const nodes =
      themeId === null
        ? (syllabus?.items ?? [])
        : (findSyllabusNode(syllabus?.items ?? [], themeId)?.children ?? []);
    return questionTopicNodes(nodes);
  }, [syllabus, themeId]);
  const topicIds = React.useMemo(() => {
    if (topicId !== null) return [topicId];
    if (themeId === null) return [];
    return topicOptions.map((topic) => topic.id);
  }, [themeId, topicId, topicOptions]);
  const toggleStudent = (id: number) =>
    setSelectedStudents((value) =>
      value.includes(id) ? value.filter((item) => item !== id) : [...value, id],
    );
  const selectClass = (nextClassId: number) => {
    setSelectedClassId(nextClassId);
    setSelectedStudents(validInitialStudentId ? [validInitialStudentId] : []);
    setClassError(null);
    setStudentError(null);
  };
  const continueToConfiguration = () => {
    if (
      !selectedClassId ||
      !classes.some((item) => item.id === selectedClassId)
    ) {
      setClassError("Choose a valid class before continuing.");
      return;
    }
    if (targetMode === "STUDENTS") {
      if (studentLoadState === "loading") {
        setStudentError(
          "Wait for active class members to load before continuing.",
        );
        return;
      }
      if (studentLoadState === "error") {
        setStudentError(studentError ?? "Students could not be loaded.");
        return;
      }
      if (!selectedStudents.length) {
        setStudentError(
          "Choose at least one student target before continuing.",
        );
        return;
      }
    }
    setConfigurationOpen(true);
    setError(null);
  };
  const showDiagnostic = async () => {
    if (!selectedClassId) {
      setError("Choose a valid class before generating a worksheet.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const value = await loadDiagnostic(selectedClassId);
      setRecommendations(value);
      setDiagnostic(true);
      if (value.status === "READY" && topicId === null)
        setTopicId(value.recommendations[0]?.topicId ?? null);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Diagnostic evidence could not be loaded.",
      );
    } finally {
      setBusy(false);
    }
  };
  const submit = async () => {
    const questionCount = Number(count);
    if (!selectedClassId) {
      setError("Choose a valid class before generating a worksheet.");
      return;
    }
    if (
      !Number.isSafeInteger(questionCount) ||
      questionCount < topicIds.length ||
      questionCount > 100
    ) {
      setError(
        "Choose at least one question for every selected topic, up to 100 questions.",
      );
      return;
    }
    if (targetMode === "STUDENTS" && !selectedStudents.length) {
      setError("Choose at least one student target.");
      return;
    }
    setBusy(true);
    setError(null);
    const input = {
      targetMode,
      ...(targetMode === "STUDENTS" ? { studentIds: selectedStudents } : {}),
      ...(topicIds.length ? { topicIds } : {}),
      questionCount,
      ...(questionType ? { questionType } : {}),
      ...(difficulty ? { difficulty } : {}),
      ...(dueAt ? { dueAt } : {}),
      ...(title ? { title } : {}),
      ...(instructions ? { instructions } : {}),
    };
    try {
      const response = diagnostic
        ? await generateDiagnostic(
            selectedClassId,
            { ...input, topicIds },
            randomKey(),
          )
        : await generate(selectedClassId, input, randomKey());
      if (!response.worksheet)
        throw new Error(
          response.message || "Worksheet generation did not produce a draft.",
        );
      setDraft(response.worksheet);
      const questionFilters = {
        questionType: questionType || undefined,
        difficulty: difficulty || undefined,
        archiveState: "ACTIVE" as const,
        size: 100,
      };
      const pages = await Promise.all(
        topicIds.length
          ? topicIds.map((selectedTopicId) =>
              loadQuestions({ ...questionFilters, topicId: selectedTopicId }),
            )
          : [loadQuestions(questionFilters)],
      );
      setBank(pages.flatMap((page) => page.items));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Worksheet generation could not be started.",
      );
    } finally {
      setBusy(false);
    }
  };
  const saveQuestions = async (questionIds: number[]) => {
    if (!draft || !questionIds.length) {
      setError("A worksheet needs at least one question.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setDraft(
        await update(draft.id, {
          title: title || draft.title,
          instructions: instructions || draft.instructions,
          questionIds,
        }),
      );
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "The draft could not be updated.",
      );
    } finally {
      setBusy(false);
    }
  };
  const move = (index: number, direction: -1 | 1) => {
    if (
      !draft ||
      index + direction < 0 ||
      index + direction >= draft.questions.length
    )
      return;
    const ids = draft.questions.map((item) => item.id);
    [ids[index], ids[index + direction]] = [ids[index + direction], ids[index]];
    void saveQuestions(ids);
  };
  const replace = (questionId: number) => {
    if (!draft) return;
    const currentQuestion = draft.questions.find(
      (question) => question.id === questionId,
    );
    const replacement =
      bank.find(
        (item) =>
          item.syllabusTopic.id === currentQuestion?.topicId &&
          !draft.questions.some((question) => question.id === item.id),
      ) ??
      bank.find(
        (item) =>
          currentQuestion?.topicId === undefined &&
          !draft.questions.some((question) => question.id === item.id),
      );
    if (!replacement) {
      setError(
        "No unused active question is available for replacement in this topic.",
      );
      return;
    }
    void saveQuestions(
      draft.questions.map((question) =>
        question.id === questionId ? replacement.id : question.id,
      ),
    );
  };
  const approveDraft = async () => {
    if (!draft || draft.status !== "DRAFT" || approvalInProgress.current)
      return;

    approvalInProgress.current = true;
    setBusy(true);
    setError(null);

    try {
      const approvedWorksheet = await approve(draft.id, dueAt || undefined);
      setDraft(approvedWorksheet);
      onApproved?.(approvedWorksheet);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Worksheet could not be approved.",
      );
    } finally {
      approvalInProgress.current = false;
      setBusy(false);
    }
  };
  const current = draft ? 3 : configurationOpen ? 1 : 0;

  return (
    <Box sx={{ maxWidth: 1120, mx: "auto", py: 3 }}>
      <Typography
        component="h1"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: { xs: 30, sm: 38 },
          mb: 2,
        }}
      >
        Build worksheet
      </Typography>
      <Box
        aria-label="Worksheet generation steps"
        sx={{
          display: "flex",
          alignItems: "flex-start",
          overflowX: "auto",
          mb: 3,
        }}
      >
        {steps.map((name, index) => (
          <Box
            key={name}
            sx={{ display: "flex", alignItems: "center", flex: "1 0 116px" }}
          >
            <Box sx={{ display: "grid", justifyItems: "center", gap: 0.7 }}>
              <Box
                aria-current={index === current ? "step" : undefined}
                sx={{
                  width: 29,
                  height: 29,
                  borderRadius: "50%",
                  display: "grid",
                  placeItems: "center",
                  fontSize: 12,
                  fontWeight: 700,
                  bgcolor:
                    index < current
                      ? "#9E3A24"
                      : index === current
                        ? "#FFFDFA"
                        : "#F4EFE6",
                  color:
                    index < current
                      ? "#FFFDFA"
                      : index === current
                        ? "#9E3A24"
                        : "#A09488",
                  border: "2px solid",
                  borderColor: index <= current ? "#9E3A24" : "#E4DCD0",
                }}
              >
                {index < current ? "✓" : index + 1}
              </Box>
              <Typography
                sx={{
                  fontSize: 11.5,
                  fontWeight: index === current ? 600 : 400,
                  whiteSpace: "nowrap",
                }}
              >
                {name}
              </Typography>
            </Box>
            {index < 4 && (
              <Box
                sx={{
                  height: 2,
                  bgcolor: index < current ? "#9E3A24" : "#E4DCD0",
                  flex: 1,
                  mt: -2.7,
                }}
              />
            )}
          </Box>
        ))}
      </Box>
      {!draft && !configurationOpen ? (
        <Card variant="outlined" sx={{ ...card, p: { xs: 2, sm: 3 } }}>
          <Typography
            component="h2"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 24,
            }}
          >
            Who is this worksheet for?
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 13.5, mb: 2 }}>
            Choose a class and target before configuring the worksheet.
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2, minmax(0, 1fr))",
              },
              gap: 1,
              mb: 2,
            }}
          >
            {(["CLASS", "STUDENTS"] as const).map((mode) => (
              <Button
                key={mode}
                aria-pressed={targetMode === mode}
                onClick={() => setTargetMode(mode)}
                sx={{
                  justifyContent: "flex-start",
                  textAlign: "left",
                  minHeight: 76,
                  border: "1.5px solid",
                  borderColor: targetMode === mode ? "#9E3A24" : "#EBE4D9",
                  bgcolor: targetMode === mode ? "#FDF6F3" : "#FFFDFA",
                  color: "#2A2622",
                  textTransform: "none",
                  borderRadius: "10px",
                  p: 1.5,
                }}
              >
                <Box>
                  <Typography
                    sx={{
                      fontFamily: "'Playfair Display', Georgia, serif",
                      fontSize: 18,
                    }}
                  >
                    {mode === "CLASS" ? "Whole class" : "Selected students"}
                  </Typography>
                  <Typography sx={{ color: "#6F675E", fontSize: 12.5 }}>
                    {mode === "CLASS"
                      ? "One worksheet for the class."
                      : "Target active members of this class."}
                  </Typography>
                </Box>
              </Button>
            ))}
          </Box>
          {classLoadState === "loading" ? (
            <Typography
              role="status"
              aria-live="polite"
              sx={{ color: "#8B837A", fontSize: 13, mb: 2 }}
            >
              Loading your classes…
            </Typography>
          ) : classLoadState === "error" ? (
            <Box sx={{ mb: 2 }}>
              <Typography role="alert" sx={{ color: "#B4573F", fontSize: 13 }}>
                {classError}
              </Typography>
              <Button
                onClick={() => setClassLoadAttempt((value) => value + 1)}
                sx={{ ...secondary, mt: 1 }}
              >
                Retry classes
              </Button>
            </Box>
          ) : classes.length === 0 ? (
            <Typography
              role="status"
              sx={{ color: "#8B837A", fontSize: 13, mb: 2 }}
            >
              You do not have any classes yet. Create a class before generating
              a worksheet.
            </Typography>
          ) : (
            <TextField
              select
              label="Class"
              value={selectedClassId?.toString() ?? ""}
              onChange={(event) => selectClass(Number(event.target.value))}
              fullWidth
              sx={{ mb: 2 }}
              helperText="Only classes belonging to your Tutor account are shown."
            >
              <MenuItem value="" disabled>
                Choose a class
              </MenuItem>
              {classes.map((item) => (
                <MenuItem key={item.id} value={item.id}>
                  {item.className} · {item.subject} · {item.level}
                </MenuItem>
              ))}
            </TextField>
          )}
          {classError && classLoadState === "ready" && (
            <Typography
              role="alert"
              sx={{ color: "#B4573F", fontSize: 13, mb: 2 }}
            >
              {classError}
            </Typography>
          )}
          {targetMode === "STUDENTS" && selectedClassId && (
            <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, mb: 2 }}>
              <Typography
                component="legend"
                sx={{ color: "#6F675E", fontSize: 11.5, fontWeight: 600 }}
              >
                Choose students
              </Typography>
              {studentLoadState === "loading" ? (
                <Typography
                  role="status"
                  aria-live="polite"
                  sx={{ color: "#8B837A", fontSize: 12.5 }}
                >
                  Loading active class members…
                </Typography>
              ) : studentLoadState === "error" ? (
                <Box>
                  <Typography
                    role="alert"
                    sx={{ color: "#B4573F", fontSize: 13 }}
                  >
                    {studentError}
                  </Typography>
                  <Button
                    onClick={() => setStudentLoadAttempt((value) => value + 1)}
                    sx={{ ...secondary, mt: 1 }}
                  >
                    Retry students
                  </Button>
                </Box>
              ) : students.length ? (
                students.map((student) => (
                  <FormControlLabel
                    key={student.id}
                    control={
                      <Checkbox
                        checked={selectedStudents.includes(student.id)}
                        onChange={() => toggleStudent(student.id)}
                      />
                    }
                    label={student.fullName}
                  />
                ))
              ) : (
                <Typography
                  role="status"
                  sx={{ color: "#8B837A", fontSize: 12.5 }}
                >
                  This class has no active students to target.
                </Typography>
              )}
              {studentError && studentLoadState === "ready" && (
                <Typography
                  role="alert"
                  sx={{ color: "#B4573F", fontSize: 13, mt: 1 }}
                >
                  {studentError}
                </Typography>
              )}
            </Box>
          )}
          <Button
            onClick={continueToConfiguration}
            disabled={
              classLoadState === "loading" ||
              classLoadState === "error" ||
              !classes.length
            }
            sx={{
              mt: 1,
              bgcolor: "#E08A72",
              color: "#1B1917",
              textTransform: "none",
              minHeight: 42,
              fontWeight: 600,
              borderRadius: "10px",
            }}
          >
            Continue to configuration
          </Button>
        </Card>
      ) : !draft ? (
        <Card variant="outlined" sx={{ ...card, p: { xs: 2, sm: 3 } }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              gap: 1,
              flexWrap: "wrap",
              mb: 2,
            }}
          >
            <Box>
              <Typography
                component="h2"
                sx={{
                  fontFamily: "'Playfair Display', Georgia, serif",
                  fontSize: 24,
                }}
              >
                Configure worksheet
              </Typography>
              <Typography sx={{ color: "#6F675E", fontSize: 13.5 }}>
                {targetMode === "CLASS"
                  ? "Whole class"
                  : `${selectedStudents.length} selected student${selectedStudents.length === 1 ? "" : "s"}`}{" "}
                ·{" "}
                {classes.find((item) => item.id === selectedClassId)?.className}
              </Typography>
            </Box>
            <Button
              onClick={() => setConfigurationOpen(false)}
              disabled={busy}
              sx={secondary}
            >
              Change target
            </Button>
          </Box>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              gap: 1,
              flexWrap: "wrap",
              mb: 1,
            }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
              }}
            >
              What should it practise?
            </Typography>
            <Button
              onClick={() => void showDiagnostic()}
              disabled={busy}
              sx={{
                bgcolor: "#E08A72",
                color: "#1B1917",
                textTransform: "none",
                minHeight: 38,
                borderRadius: "9px",
                fontWeight: 600,
              }}
            >
              Get diagnostic suggestions
            </Button>
          </Box>
          {recommendations && (
            <Box
              sx={{
                bgcolor: "#1B1917",
                color: "#E8E2D9",
                borderRadius: "12px",
                p: 2,
                mb: 2,
              }}
            >
              <Typography
                sx={{
                  color: "#E08A72",
                  fontSize: 10.5,
                  fontWeight: 700,
                  letterSpacing: ".1em",
                }}
              >
                DIAGNOSTIC RECOMMENDATION
              </Typography>
              <Typography sx={{ color: "#CFC7BC", fontSize: 13, mt: 0.5 }}>
                {recommendations.message}
              </Typography>
              {recommendations.recommendations.slice(0, 5).map((item) => (
                <Typography
                  key={`${item.studentId ?? "class"}-${item.topicId}`}
                  sx={{ color: "#A8A096", fontSize: 12, mt: 0.6 }}
                >
                  • {recommendationCopy(item)}
                </Typography>
              ))}
              <Typography sx={{ color: "#7A7268", fontSize: 10.5, mt: 1 }}>
                Suggestion only — not saved or assigned.
              </Typography>
            </Box>
          )}
          <TextField
            label="Worksheet title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            fullWidth
            sx={{ mb: 2 }}
          />
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.25 }}>
            <TextField
              select
              label="Theme"
              value={themeId?.toString() ?? ANY_FILTER}
              onChange={(event) => {
                setThemeId(
                  event.target.value === ANY_FILTER
                    ? null
                    : Number(event.target.value),
                );
                setTopicId(null);
              }}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value={ANY_FILTER}>Any Theme</MenuItem>
              {themeOptions.map((theme) => (
                <MenuItem key={theme.id} value={theme.id}>
                  {theme.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Topic"
              value={topicId?.toString() ?? ANY_FILTER}
              onChange={(event) =>
                setTopicId(
                  event.target.value === ANY_FILTER
                    ? null
                    : Number(event.target.value),
                )
              }
              sx={{ minWidth: 180 }}
            >
              <MenuItem value={ANY_FILTER}>Any Topic</MenuItem>
              {topicOptions.map((topic) => (
                <MenuItem key={topic.id} value={topic.id}>
                  {topic.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Question count"
              type="number"
              value={count}
              onChange={(event) => setCount(event.target.value)}
              slotProps={{ htmlInput: { min: 1, max: 100 } }}
            />
            <TextField
              select
              label="Question type"
              value={questionType || ANY_FILTER}
              onChange={(event) =>
                setQuestionType(
                  event.target.value === ANY_FILTER
                    ? ""
                    : (event.target.value as QuestionType),
                )
              }
              sx={{ minWidth: 180 }}
            >
              <MenuItem value={ANY_FILTER}>Any Question Type</MenuItem>
              <MenuItem value="MULTIPLE_CHOICE">MCQ</MenuItem>
              <MenuItem value="SHORT_ANSWER">Structured</MenuItem>
              <MenuItem value="OPEN_ENDED">Open ended</MenuItem>
            </TextField>
            <TextField
              select
              label="Difficulty"
              value={difficulty || ANY_FILTER}
              onChange={(event) =>
                setDifficulty(
                  event.target.value === ANY_FILTER
                    ? ""
                    : (event.target.value as QuestionDifficulty),
                )
              }
              sx={{ minWidth: 180 }}
            >
              <MenuItem value={ANY_FILTER}>Any Difficulty</MenuItem>
              <MenuItem value="FOUNDATION">Foundation</MenuItem>
              <MenuItem value="APPLICATION">Application</MenuItem>
              <MenuItem value="CHALLENGE">Challenge</MenuItem>
            </TextField>
            <TextField
              label="Due date"
              type="datetime-local"
              value={dueAt}
              onChange={(event) => setDueAt(event.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Box>
          {syllabusError && (
            <Typography
              role="alert"
              sx={{ color: "#B4573F", fontSize: 12, mt: 1 }}
            >
              {syllabusError}
            </Typography>
          )}
          <Typography sx={{ color: "#8B837A", fontSize: 11.5, mt: 1 }}>
            Leave Theme and Topic as Any to use all active eligible Question
            Bank questions.
          </Typography>
          <TextField
            label="Tutor instructions"
            value={instructions}
            onChange={(event) => setInstructions(event.target.value)}
            fullWidth
            multiline
            minRows={3}
            sx={{ mt: 2 }}
          />
          {error && (
            <Typography role="alert" sx={{ color: "#B4573F", mt: 2 }}>
              {error}
            </Typography>
          )}
          <Button
            onClick={() => void submit()}
            disabled={busy}
            sx={{
              mt: 2,
              bgcolor: "#E08A72",
              color: "#1B1917",
              textTransform: "none",
              minHeight: 42,
              fontWeight: 600,
              borderRadius: "10px",
            }}
          >
            {busy
              ? "Creating draft…"
              : diagnostic
                ? "Generate diagnostic draft"
                : "Generate worksheet draft"}
          </Button>
        </Card>
      ) : (
        <Card variant="outlined" sx={{ ...card, p: { xs: 2, sm: 3 } }}>
          <Typography
            component="h2"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 25,
            }}
          >
            {draft.title}
          </Typography>
          <Typography sx={{ color: "#6F675E", mb: 1.5 }}>
            {draft.worksheetType === "DIAGNOSTIC"
              ? "Diagnostic draft"
              : "Draft"}{" "}
            — Tutor review required before assignment.
          </Typography>
          <Box sx={{ bgcolor: "#1B1917", borderRadius: "12px", p: 2, mb: 2 }}>
            <Typography
              sx={{
                color: "#E08A72",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".1em",
              }}
            >
              AI PREVIEW
            </Typography>
            <Typography sx={{ color: "#CFC7BC", fontSize: 13, mt: 0.5 }}>
              {draft.instructions ||
                "The selected question mix is ready for your review."}
            </Typography>
            <Typography sx={{ color: "#7A7268", fontSize: 10.5, mt: 1 }}>
              Suggestion only — edit and approval remain tutor decisions.
            </Typography>
          </Box>
          <Typography
            component="h3"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 22,
              mb: 1,
            }}
          >
            Edit question order
          </Typography>
          <Box sx={{ display: "grid", gap: 1.1 }}>
            {draft.questions.map((question, index) => (
              <Box
                key={question.id}
                sx={{
                  display: "flex",
                  gap: 1.2,
                  p: 1.5,
                  border: "1px solid #EBE4D9",
                  borderRadius: "12px",
                }}
              >
                <Box sx={{ display: "grid", gap: 0.35, alignContent: "start" }}>
                  <Button
                    aria-label={`Move question ${index + 1} up`}
                    onClick={() => move(index, -1)}
                    disabled={busy || index === 0}
                    sx={{
                      minWidth: 30,
                      width: 30,
                      height: 30,
                      p: 0,
                      ...secondary,
                    }}
                  >
                    ↑
                  </Button>
                  <Typography
                    sx={{
                      textAlign: "center",
                      fontFamily: "'Playfair Display', Georgia, serif",
                    }}
                  >
                    {index + 1}
                  </Typography>
                  <Button
                    aria-label={`Move question ${index + 1} down`}
                    onClick={() => move(index, 1)}
                    disabled={busy || index === draft.questions.length - 1}
                    sx={{
                      minWidth: 30,
                      width: 30,
                      height: 30,
                      p: 0,
                      ...secondary,
                    }}
                  >
                    ↓
                  </Button>
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ color: "#8B837A", fontSize: 11.5 }}>
                    {question.questionType.replaceAll("_", " ")} ·{" "}
                    {question.topicName}
                  </Typography>
                  <Typography sx={{ fontSize: 13.5, lineHeight: 1.55 }}>
                    {question.prompt}
                  </Typography>
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.4 }}
                  >
                    {question.totalMarks.toFixed(1)} marks
                  </Typography>
                </Box>
                <Box sx={{ display: "grid", gap: 0.5 }}>
                  <Button
                    aria-label={`Replace question ${index + 1}`}
                    onClick={() => replace(question.id)}
                    disabled={busy}
                    sx={{
                      minWidth: 34,
                      width: 34,
                      height: 34,
                      p: 0,
                      ...secondary,
                    }}
                  >
                    ↻
                  </Button>
                  <Button
                    aria-label={`Remove question ${index + 1}`}
                    onClick={() =>
                      void saveQuestions(
                        draft.questions
                          .filter((item) => item.id !== question.id)
                          .map((item) => item.id),
                      )
                    }
                    disabled={busy || draft.questions.length === 1}
                    sx={{
                      minWidth: 34,
                      width: 34,
                      height: 34,
                      p: 0,
                      border: "1px solid #EBE4D9",
                      color: "#B4573F",
                      borderRadius: "8px",
                    }}
                  >
                    ×
                  </Button>
                </Box>
              </Box>
            ))}
          </Box>
          {bank
            .filter(
              (item) =>
                !draft.questions.some((question) => question.id === item.id),
            )
            .slice(0, 4)
            .map((item) => (
              <Button
                key={item.id}
                onClick={() =>
                  void saveQuestions([
                    ...draft.questions.map((question) => question.id),
                    item.id,
                  ])
                }
                disabled={busy}
                sx={{ ...secondary, mr: 0.75, mt: 1.5 }}
              >
                Add {item.code}
              </Button>
            ))}
          {error && (
            <Typography role="alert" sx={{ color: "#B4573F", mt: 1.5 }}>
              {error}
            </Typography>
          )}
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1.25, mt: 2 }}>
            <Button
              onClick={() => void approveDraft()}
              disabled={
                busy || draft.questions.length === 0 || draft.status !== "DRAFT"
              }
              sx={{
                bgcolor: "#9E3A24",
                color: "#FFFDFA",
                textTransform: "none",
                minHeight: 42,
                borderRadius: "10px",
              }}
            >
              {draft.status === "APPROVED"
                ? "Worksheet approved"
                : "Approve & assign worksheet"}
            </Button>
            <Button
              onClick={() => setDraft(null)}
              disabled={busy || draft.status !== "DRAFT"}
              sx={secondary}
            >
              Back to configuration
            </Button>
          </Box>
        </Card>
      )}
    </Box>
  );
}
