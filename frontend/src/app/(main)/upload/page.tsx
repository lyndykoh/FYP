"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import LinearProgress from "@mui/material/LinearProgress";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import CameraAltOutlinedIcon from "@mui/icons-material/CameraAltOutlined";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import Stack from "@/components/lumina-stack";
import PageReview from "@/components/submissions/PageReview";
import { getBrowserSession } from "@/lib/auth";
import {
  createOcrDocument,
  preflightUploadImage,
  releasePagePreview,
  validateUploadFiles,
  type UploadPage,
} from "@/services/submissions";
import { fetchTutorClasses, type TutorClass } from "@/services/classes";
import {
  fetchStudentSelfProfile,
  fetchTutorStudents,
  type StudentSelfProfile,
  type TutorStudent,
} from "@/services/students";
import {
  fetchStudentWorksheets,
  fetchSubmissionWorksheets,
  fetchTutorWorksheet,
  type StudentWorksheet,
  type TutorWorksheet,
} from "@/services/worksheets";

const button = { minHeight: 42, textTransform: "none", borderRadius: "10px" };
const primary = {
  ...button,
  bgcolor: "#9E3A24",
  color: "#FFFDFA",
  "&:hover": { bgcolor: "#8A3120" },
};
const secondary = {
  ...button,
  borderColor: "#E4DCD0",
  color: "#2A2622",
  bgcolor: "#FFFDFA",
};

function positiveId(value: string | null): number | null {
  if (!value || !/^\d+$/.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

const checkingPhotoQuality = "Checking photo quality…";
const pdfQualityUnavailable =
  "Local photo quality check is unavailable for PDF pages. You can still upload it.";
const qualityCheckUnavailable =
  "We could not check this photo locally. You can still upload it.";

function qualityGuidance(
  preflight: Awaited<ReturnType<typeof preflightUploadImage>>,
) {
  if (preflight.mediaType === "application/pdf") return pdfQualityUnavailable;
  if (preflight.assessmentStatus === "ready") return null;

  return preflight.guidance.join(" ") || qualityCheckUnavailable;
}

/**
 * Runs the local-only preflight independently of the upload. A late result is
 * ignored after a page is removed or replaced, so it cannot label another file.
 */
function useUploadPages() {
  const [pages, setPages] = React.useState<UploadPage[]>([]);
  const [errors, setErrors] = React.useState<string[]>([]);
  const preflightRuns = React.useRef(new Map<string, number>());
  const nextPreflightRun = React.useRef(0);

  const checkQuality = React.useCallback((page: UploadPage) => {
    const run = ++nextPreflightRun.current;
    preflightRuns.current.set(page.id, run);

    void preflightUploadImage(page.file)
      .then((result) => {
        if (preflightRuns.current.get(page.id) !== run) return;

        const warning = qualityGuidance(result);
        setPages((current) =>
          current.map((item) =>
            item.id === page.id && item.file === page.file
              ? { ...item, warning }
              : item,
          ),
        );
      })
      .catch(() => {
        if (preflightRuns.current.get(page.id) !== run) return;

        setPages((current) =>
          current.map((item) =>
            item.id === page.id && item.file === page.file
              ? { ...item, warning: qualityCheckUnavailable }
              : item,
          ),
        );
      });
  }, []);

  const preparePages = React.useCallback((newPages: UploadPage[]) => {
    return newPages.map((page) => ({
      ...page,
      warning:
        page.file.type === "application/pdf"
          ? pdfQualityUnavailable
          : checkingPhotoQuality,
    }));
  }, []);

  const add = React.useCallback(
    (files: File[]) => {
      const result = validateUploadFiles(files, pages);
      const newPages = preparePages(result.pages);
      setPages((current) => [...current, ...newPages]);
      setErrors(result.errors);
      newPages.forEach(checkQuality);
    },
    [checkQuality, pages, preparePages],
  );

  const move = React.useCallback((id: string, direction: -1 | 1) => {
    setPages((current) => {
      const index = current.findIndex((page) => page.id === id);
      const next = index + direction;
      if (index < 0 || next < 0 || next >= current.length) return current;
      const copy = [...current];
      [copy[index], copy[next]] = [copy[next], copy[index]];
      return copy;
    });
  }, []);

  const remove = React.useCallback((id: string) => {
    preflightRuns.current.delete(id);
    setPages((current) => {
      const page = current.find((item) => item.id === id);
      if (page) releasePagePreview(page);
      return current.filter((item) => item.id !== id);
    });
  }, []);

  const replace = React.useCallback(
    (id: string, file: File) => {
      const old = pages.find((page) => page.id === id);
      if (!old) return;

      const result = validateUploadFiles(
        [file],
        pages.filter((page) => page.id !== id),
      );
      if (result.errors.length > 0) {
        setErrors(result.errors);
        return;
      }

      const replacement = preparePages(result.pages)[0];
      if (!replacement) return;

      preflightRuns.current.delete(id);
      releasePagePreview(old);
      setPages((current) =>
        current.map((page) => (page.id === id ? replacement : page)),
      );
      setErrors([]);
      checkQuality(replacement);
    },
    [checkQuality, pages, preparePages],
  );

  return { pages, setPages, errors, setErrors, add, move, remove, replace };
}

function TutorUploadWizard() {
  const router = useRouter();
  const params = useSearchParams();
  const requestedClassId = positiveId(params.get("classId"));
  const requestedStudentId = positiveId(params.get("studentId"));
  // `ws` remains only for previously shared local links; worksheetId is canonical.
  const requestedWorksheetId =
    positiveId(params.get("worksheetId")) ?? positiveId(params.get("ws"));
  const [step, setStep] = React.useState(1);
  const [classes, setClasses] = React.useState<TutorClass[]>([]);
  const [students, setStudents] = React.useState<TutorStudent[]>([]);
  const [worksheets, setWorksheets] = React.useState<TutorWorksheet[]>([]);
  const [selectedClassId, setSelectedClassId] = React.useState<number | null>(
    null,
  );
  const [selectedStudentId, setSelectedStudentId] = React.useState<
    number | null
  >(null);
  const [selectedWorksheetId, setSelectedWorksheetId] = React.useState<
    number | null
  >(null);
  const [loadingClasses, setLoadingClasses] = React.useState(true);
  const [loadingStudents, setLoadingStudents] = React.useState(false);
  const [loadingWorksheets, setLoadingWorksheets] = React.useState(false);
  const [selectionError, setSelectionError] = React.useState<string | null>(
    null,
  );
  const [retryCount, setRetryCount] = React.useState(0);
  const { pages, setPages, errors, setErrors, add, move, remove, replace } =
    useUploadPages();
  const [dragOver, setDragOver] = React.useState(false);
  const [uploading, setUploading] = React.useState(false);
  const [progress, setProgress] = React.useState(0);

  React.useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoadingClasses(true);
        setSelectionError(null);
      }
    });
    void (async () => {
      try {
        const loaded = await fetchTutorClasses();
        if (!active) return;
        setClasses(loaded);
        let targetClassId = requestedClassId;
        if (requestedWorksheetId !== null && targetClassId === null) {
          const worksheet = await fetchTutorWorksheet(requestedWorksheetId);
          targetClassId = worksheet.sourceClassId ?? null;
          if (targetClassId === null)
            throw new Error(
              "The worksheet has no class context. Select a class before uploading.",
            );
        }
        if (targetClassId !== null) {
          if (!loaded.some((item) => item.id === targetClassId))
            throw new Error("That class is not available for your account.");
          setSelectedClassId(targetClassId);
        }
      } catch (error) {
        if (active)
          setSelectionError(
            error instanceof Error
              ? error.message
              : "Classes could not be loaded. Please try again.",
          );
      } finally {
        if (active) setLoadingClasses(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [requestedClassId, requestedWorksheetId, retryCount]);

  React.useEffect(() => {
    if (selectedClassId === null) {
      queueMicrotask(() => {
        setStudents([]);
        setSelectedStudentId(null);
      });
      return;
    }
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoadingStudents(true);
        setStudents([]);
        setSelectedStudentId(null);
      }
    });
    void fetchTutorStudents(selectedClassId)
      .then((loaded) => {
        if (!active) return;
        setStudents(loaded);
        if (requestedStudentId !== null) {
          if (!loaded.some((item) => item.id === requestedStudentId))
            setSelectionError(
              "That student is not enrolled in the selected class.",
            );
          else setSelectedStudentId(requestedStudentId);
        }
      })
      .catch((error: unknown) => {
        if (active)
          setSelectionError(
            error instanceof Error
              ? error.message
              : "Students could not be loaded. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoadingStudents(false);
      });
    return () => {
      active = false;
    };
  }, [selectedClassId, requestedStudentId]);

  React.useEffect(() => {
    if (selectedClassId === null || selectedStudentId === null) {
      queueMicrotask(() => {
        setWorksheets([]);
        setSelectedWorksheetId(null);
      });
      return;
    }
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoadingWorksheets(true);
        setWorksheets([]);
        setSelectedWorksheetId(null);
      }
    });
    void fetchSubmissionWorksheets(selectedClassId, selectedStudentId)
      .then((loaded) => {
        if (!active) return;
        setWorksheets(loaded);
        if (requestedWorksheetId !== null) {
          if (!loaded.some((item) => item.id === requestedWorksheetId))
            setSelectionError(
              "That worksheet cannot be submitted for the selected student and class.",
            );
          else setSelectedWorksheetId(requestedWorksheetId);
        }
      })
      .catch((error: unknown) => {
        if (active)
          setSelectionError(
            error instanceof Error
              ? error.message
              : "Worksheets could not be loaded. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoadingWorksheets(false);
      });
    return () => {
      active = false;
    };
  }, [selectedClassId, selectedStudentId, requestedWorksheetId]);

  const selectedClass = classes.find((item) => item.id === selectedClassId);
  const selectedStudent = students.find(
    (item) => item.id === selectedStudentId,
  );
  const selectedWorksheet = worksheets.find(
    (item) => item.id === selectedWorksheetId,
  );
  const hasValidSelection =
    selectedClass !== undefined &&
    selectedStudent !== undefined &&
    selectedWorksheet !== undefined;
  const changeClass = (value: number) => {
    setSelectionError(null);
    setSelectedClassId(value);
    setSelectedStudentId(null);
    setSelectedWorksheetId(null);
  };
  const changeStudent = (value: number) => {
    setSelectionError(null);
    setSelectedStudentId(value);
    setSelectedWorksheetId(null);
  };
  const submit = async () => {
    if (
      !hasValidSelection ||
      selectedClassId === null ||
      selectedStudentId === null ||
      selectedWorksheetId === null ||
      pages.length === 0
    ) {
      setErrors([
        "Choose a class, student, worksheet, and at least one page before submitting.",
      ]);
      return;
    }
    setUploading(true);
    setErrors([]);
    setProgress(0);
    try {
      const result = await createOcrDocument({
        classId: selectedClassId,
        studentId: selectedStudentId,
        worksheetId: selectedWorksheetId,
        pages,
      });
      setProgress(100);
      router.push(`/ocr?submissionId=${result.id}`);
    } catch (error) {
      setErrors([
        error instanceof Error
          ? error.message
          : "Upload failed. Please try again.",
      ]);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 4 },
        py: 4,
      }}
    >
      <Box sx={{ maxWidth: 850, mx: "auto" }}>
        <Typography
          sx={{ fontSize: 12, letterSpacing: ".1em", color: "#6F675E", mb: 3 }}
        >
          STEP {step} OF 4
        </Typography>
        {step === 1 && (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
                mb: 1,
              }}
            >
              Choose a student worksheet
            </Typography>
            <Typography sx={{ color: "#6F675E", mb: 2 }}>
              Select the class, enrolled student, and approved worksheet before
              uploading their completed pages.
            </Typography>
            {loadingClasses && (
              <LinearProgress
                aria-label="Loading tutor classes"
                sx={{ mb: 2 }}
              />
            )}
            {selectionError && (
              <Box role="alert" sx={{ color: "#B4573F", mb: 2 }}>
                <Typography>{selectionError}</Typography>
              </Box>
            )}
            <Stack gap={2}>
              <TextField
                select
                label="Class"
                value={selectedClassId ?? ""}
                disabled={loadingClasses}
                onChange={(event) => changeClass(Number(event.target.value))}
                fullWidth
              >
                <MenuItem value="" disabled>
                  Select a class
                </MenuItem>
                {classes.map((item) => (
                  <MenuItem key={item.id} value={item.id}>
                    {item.className} · {item.level} {item.subject}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Student"
                value={selectedStudentId ?? ""}
                disabled={selectedClassId === null || loadingStudents}
                onChange={(event) => changeStudent(Number(event.target.value))}
                fullWidth
              >
                <MenuItem value="" disabled>
                  {selectedClassId === null
                    ? "Select a class first"
                    : loadingStudents
                      ? "Loading students…"
                      : "Select a student"}
                </MenuItem>
                {students.map((item) => (
                  <MenuItem key={item.id} value={item.id}>
                    {item.fullName}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Worksheet"
                value={selectedWorksheetId ?? ""}
                disabled={selectedStudentId === null || loadingWorksheets}
                onChange={(event) => {
                  setSelectionError(null);
                  setSelectedWorksheetId(Number(event.target.value));
                }}
                fullWidth
              >
                <MenuItem value="" disabled>
                  {selectedStudentId === null
                    ? "Select a student first"
                    : loadingWorksheets
                      ? "Loading worksheets…"
                      : "Select a worksheet"}
                </MenuItem>
                {worksheets.map((item) => (
                  <MenuItem key={item.id} value={item.id}>
                    {item.title}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            {!loadingWorksheets &&
            selectedStudentId !== null &&
            worksheets.length === 0 &&
            !selectionError ? (
              <Card
                variant="outlined"
                sx={{ p: 2, mt: 2, bgcolor: "#FFFDFA", borderColor: "#EBE4D9" }}
              >
                <Typography sx={{ fontWeight: 700 }}>
                  No worksheets available for this student/class.
                </Typography>
                <Typography sx={{ color: "#6F675E", mt: 0.5 }}>
                  Generate and approve a worksheet before uploading completed
                  work.
                </Typography>
                <Button
                  component={Link}
                  href={
                    selectedClassId
                      ? `/tutor/worksheets/new?classId=${selectedClassId}`
                      : "/tutor/worksheets/new"
                  }
                  sx={{ ...primary, mt: 1.5 }}
                >
                  Generate worksheet
                </Button>
              </Card>
            ) : null}
            <Stack
              direction={{ xs: "column", sm: "row" }}
              justifyContent="space-between"
              gap={1}
              sx={{ mt: 3 }}
            >
              <Button
                variant="outlined"
                sx={secondary}
                onClick={() => setRetryCount((count) => count + 1)}
              >
                Reload
              </Button>
              <Stack direction="row" gap={1}>
                <Button
                  onClick={() =>
                    hasValidSelection &&
                    router.push(
                      `/manual-answers?classId=${selectedClassId}&studentId=${selectedStudentId}&worksheetId=${selectedWorksheetId}`,
                    )
                  }
                  disabled={
                    !hasValidSelection ||
                    loadingClasses ||
                    loadingStudents ||
                    loadingWorksheets
                  }
                  variant="outlined"
                  sx={secondary}
                >
                  Enter answers manually
                </Button>
                <Button
                  onClick={() => setStep(2)}
                  disabled={
                    !hasValidSelection ||
                    loadingClasses ||
                    loadingStudents ||
                    loadingWorksheets
                  }
                  endIcon={<ArrowForwardIcon />}
                  sx={primary}
                >
                  Upload scan / photo
                </Button>
              </Stack>
            </Stack>
          </>
        )}
        {step === 2 && (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
              }}
            >
              Upload completed pages
            </Typography>
            <Typography sx={{ mb: 2, color: "#4A443D" }}>
              For {selectedClass?.className} → {selectedStudent?.fullName} →{" "}
              {selectedWorksheet?.title}. Add JPG, PNG, or PDF files.
            </Typography>
            <Box
              onDragOver={(event) => {
                event.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragOver(false);
                add(Array.from(event.dataTransfer.files));
              }}
              sx={{
                border: "2px dashed",
                borderColor: dragOver ? "#9E3A24" : "#DCCFBE",
                borderRadius: 2,
                p: 4,
                textAlign: "center",
                bgcolor: "#FFFDFA",
              }}
            >
              <UploadFileIcon />
              <Typography sx={{ fontWeight: 700 }}>
                Drag and drop files here
              </Typography>
              <Stack
                direction="row"
                justifyContent="center"
                gap={1}
                sx={{ mt: 2 }}
              >
                <Button component="label" variant="outlined" sx={secondary}>
                  Choose files
                  <input
                    hidden
                    type="file"
                    multiple
                    accept="image/jpeg,image/png,application/pdf"
                    onChange={(event) => {
                      add(Array.from(event.currentTarget.files || []));
                      event.currentTarget.value = "";
                    }}
                  />
                </Button>
                <Button
                  component="label"
                  variant="outlined"
                  startIcon={<CameraAltOutlinedIcon />}
                  sx={secondary}
                >
                  Take photo
                  <input
                    hidden
                    type="file"
                    accept="image/*"
                    capture="environment"
                    onChange={(event) => {
                      add(Array.from(event.currentTarget.files || []));
                      event.currentTarget.value = "";
                    }}
                  />
                </Button>
              </Stack>
              <Typography sx={{ fontSize: 12, color: "#6F675E", mt: 2 }}>
                Up to 20 MB per file
              </Typography>
            </Box>
            {errors.length > 0 && (
              <Box role="alert" sx={{ color: "#B4573F", mt: 2 }}>
                {errors.map((error) => (
                  <Typography key={error}>{error}</Typography>
                ))}
              </Box>
            )}
            <Typography sx={{ mt: 2, fontSize: 12, letterSpacing: ".1em" }}>
              {pages.length} PAGE{pages.length === 1 ? "" : "S"} DETECTED
            </Typography>
            {pages.length > 0 && (
              <PageReview
                pages={pages}
                onMove={move}
                onRotate={(id) =>
                  setPages((current) =>
                    current.map((page) =>
                      page.id === id
                        ? {
                            ...page,
                            rotation: ((page.rotation + 90) %
                              360) as UploadPage["rotation"],
                          }
                        : page,
                    ),
                  )
                }
                onRemove={remove}
                onReplace={replace}
              />
            )}
            <Stack
              direction="row"
              justifyContent="space-between"
              sx={{ mt: 3 }}
            >
              <Button
                onClick={() => setStep(1)}
                variant="outlined"
                sx={secondary}
              >
                Back
              </Button>
              <Button
                onClick={() =>
                  pages.length
                    ? setStep(3)
                    : setErrors([
                        "Add at least one page before reviewing your submission.",
                      ])
                }
                sx={primary}
              >
                Review submission
              </Button>
            </Stack>
          </>
        )}
        {step === 3 && (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
              }}
            >
              Ready to submit?
            </Typography>
            <Card variant="outlined" sx={{ p: 2, my: 2, bgcolor: "#FFFDFA" }}>
              <Typography sx={{ fontWeight: 700 }}>
                {selectedClass?.className} → {selectedStudent?.fullName}
              </Typography>
              <Typography>
                {selectedWorksheet?.title} · {pages.length} page
                {pages.length === 1 ? "" : "s"} ready for OCR review and AI
                marking
              </Typography>
            </Card>
            {errors.length > 0 && (
              <Typography role="alert" sx={{ color: "#B4573F" }}>
                {errors[0]}
              </Typography>
            )}
            {uploading && (
              <>
                <Typography sx={{ mt: 2 }}>
                  Uploading {Math.ceil((progress / 100) * pages.length) || 1} of{" "}
                  {pages.length} pages…
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={progress}
                  sx={{ mt: 1 }}
                />
              </>
            )}
            <Stack
              direction="row"
              justifyContent="space-between"
              sx={{ mt: 3 }}
            >
              <Button
                onClick={() => setStep(2)}
                disabled={uploading}
                variant="outlined"
                sx={secondary}
              >
                Back
              </Button>
              <Button
                onClick={() => void submit()}
                disabled={uploading || !hasValidSelection || pages.length === 0}
                sx={primary}
              >
                {uploading ? "Uploading…" : "Save and continue to OCR review"}
              </Button>
            </Stack>
          </>
        )}
      </Box>
    </Box>
  );
}

/**
 * Students may upload only work assigned to their authenticated profile.  This
 * deliberately has no class/student selector: those values are authoritative
 * server-side and a Student must never call the Tutor selection endpoints.
 */
function StudentUploadWizard() {
  const router = useRouter();
  const params = useSearchParams();
  const requestedWorksheetId =
    positiveId(params.get("worksheetId")) ?? positiveId(params.get("ws"));
  const [profile, setProfile] = React.useState<StudentSelfProfile | null>(null);
  const [worksheet, setWorksheet] = React.useState<StudentWorksheet | null>(
    null,
  );
  const [loading, setLoading] = React.useState(requestedWorksheetId !== null);
  const [selectionError, setSelectionError] = React.useState<string | null>(
    requestedWorksheetId === null
      ? "Choose an assigned worksheet from My Worksheets before uploading."
      : null,
  );
  const [retryCount, setRetryCount] = React.useState(0);
  const { pages, setPages, errors, setErrors, add, move, remove, replace } =
    useUploadPages();
  const [dragOver, setDragOver] = React.useState(false);
  const [uploading, setUploading] = React.useState(false);

  React.useEffect(() => {
    if (requestedWorksheetId === null) return;
    let active = true;
    queueMicrotask(() => {
      if (active) {
        setLoading(true);
        setSelectionError(null);
        setProfile(null);
        setWorksheet(null);
      }
    });
    void Promise.all([
      fetchStudentSelfProfile(),
      fetchStudentWorksheets({ status: "ASSIGNED" }),
    ])
      .then(([loadedProfile, assigned]) => {
        if (!active) return;
        const selected =
          assigned.find((item) => item.id === requestedWorksheetId) ?? null;
        if (selected === null) {
          setSelectionError(
            "This worksheet is not assigned to you or is no longer available for upload.",
          );
          return;
        }
        setProfile(loadedProfile);
        setWorksheet(selected);
      })
      .catch((error: unknown) => {
        if (active)
          setSelectionError(
            error instanceof Error
              ? error.message
              : "Your assigned worksheets could not be loaded. Please try again.",
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [requestedWorksheetId, retryCount]);

  const submit = async () => {
    if (profile === null || worksheet === null || pages.length === 0) {
      setErrors([
        "Add at least one page before submitting your assigned worksheet.",
      ]);
      return;
    }
    setUploading(true);
    setErrors([]);
    try {
      const saved = await createOcrDocument({
        studentId: profile.id,
        worksheetId: worksheet.id,
        pages,
      });
      router.push(`/ocr?submissionId=${saved.id}`);
    } catch (error) {
      setErrors([
        error instanceof Error
          ? error.message
          : "Upload failed. Please try again.",
      ]);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 4 },
        py: 4,
      }}
    >
      <Box sx={{ maxWidth: 850, mx: "auto" }}>
        <Typography
          sx={{ fontSize: 12, letterSpacing: ".1em", color: "#6F675E", mb: 3 }}
        >
          UPLOAD ASSIGNED WORKSHEET
        </Typography>
        {loading ? (
          <LinearProgress
            aria-label="Loading assigned worksheet"
            sx={{ mb: 2 }}
          />
        ) : null}
        {selectionError ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{ p: 3, borderColor: "#D79B63", bgcolor: "#FFFDFA" }}
          >
            <Typography sx={{ fontWeight: 700, color: "#9E3A24" }}>
              {selectionError}
            </Typography>
            <Stack direction="row" gap={1} sx={{ mt: 2 }}>
              {requestedWorksheetId !== null ? (
                <Button
                  onClick={() => setRetryCount((value) => value + 1)}
                  sx={secondary}
                >
                  Retry
                </Button>
              ) : null}
              <Button component={Link} href="/worksheets" sx={primary}>
                Return to My Worksheets
              </Button>
            </Stack>
          </Card>
        ) : null}
        {profile !== null && worksheet !== null ? (
          <>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 31,
                mb: 1,
              }}
            >
              Upload completed work
            </Typography>
            <Typography sx={{ color: "#6F675E", mb: 2 }}>
              Choose a scan/photo for OCR, or type answers directly. Both
              methods use the same Tutor review workflow.
            </Typography>
            <Card
              variant="outlined"
              sx={{ p: 2, mb: 2, bgcolor: "#FFFDFA", borderColor: "#EBE4D9" }}
            >
              <Typography sx={{ fontWeight: 700 }}>
                Student: {profile.fullName}
              </Typography>
              <Typography>Worksheet: {worksheet.title}</Typography>
              <Typography sx={{ fontSize: 13, color: "#6F675E", mt: 0.5 }}>
                {worksheet.subjects
                  .map((subject) => subject.name)
                  .join(" · ") || "Assigned worksheet"}
              </Typography>
            </Card>
            <Box
              onDragOver={(event) => {
                event.preventDefault();
                setDragOver(true);
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragOver(false);
                add(Array.from(event.dataTransfer.files));
              }}
              sx={{
                border: "2px dashed",
                borderColor: dragOver ? "#9E3A24" : "#DCCFBE",
                borderRadius: 2,
                p: 4,
                textAlign: "center",
                bgcolor: "#FFFDFA",
              }}
            >
              <UploadFileIcon />
              <Typography sx={{ fontWeight: 700 }}>
                Drag and drop completed pages here
              </Typography>
              <Stack
                direction="row"
                justifyContent="center"
                gap={1}
                sx={{ mt: 2 }}
              >
                <Button component="label" variant="outlined" sx={secondary}>
                  Choose files
                  <input
                    hidden
                    type="file"
                    multiple
                    accept="image/jpeg,image/png,application/pdf"
                    onChange={(event) => {
                      add(Array.from(event.currentTarget.files || []));
                      event.currentTarget.value = "";
                    }}
                  />
                </Button>
                <Button
                  component="label"
                  variant="outlined"
                  startIcon={<CameraAltOutlinedIcon />}
                  sx={secondary}
                >
                  Take photo
                  <input
                    hidden
                    type="file"
                    accept="image/jpeg,image/png"
                    capture="environment"
                    onChange={(event) => {
                      add(Array.from(event.currentTarget.files || []));
                      event.currentTarget.value = "";
                    }}
                  />
                </Button>
              </Stack>
              <Typography sx={{ fontSize: 12, color: "#6F675E", mt: 2 }}>
                JPG, PNG, or one PDF · up to 20 MB per file
              </Typography>
            </Box>
            {errors.length > 0 ? (
              <Box role="alert" sx={{ color: "#B4573F", mt: 2 }}>
                {errors.map((error) => (
                  <Typography key={error}>{error}</Typography>
                ))}
              </Box>
            ) : null}
            <Typography sx={{ mt: 2, fontSize: 12, letterSpacing: ".1em" }}>
              {pages.length} PAGE{pages.length === 1 ? "" : "S"} DETECTED
            </Typography>
            {pages.length > 0 ? (
              <PageReview
                pages={pages}
                onMove={move}
                onRotate={(id) =>
                  setPages((current) =>
                    current.map((page) =>
                      page.id === id
                        ? {
                            ...page,
                            rotation: ((page.rotation + 90) %
                              360) as UploadPage["rotation"],
                          }
                        : page,
                    ),
                  )
                }
                onRemove={remove}
                onReplace={replace}
              />
            ) : null}
            <Stack
              direction="row"
              justifyContent="space-between"
              sx={{ mt: 3 }}
            >
              <Button
                component={Link}
                href="/worksheets"
                variant="outlined"
                sx={secondary}
              >
                Cancel
              </Button>
              <Stack direction="row" gap={1}>
                <Button
                  component={Link}
                  href={`/manual-answers?worksheetId=${worksheet.id}`}
                  variant="outlined"
                  sx={secondary}
                >
                  Enter answers manually
                </Button>
                <Button
                  onClick={() => void submit()}
                  disabled={uploading || pages.length === 0}
                  sx={primary}
                >
                  {uploading ? "Uploading…" : "Save and continue to OCR review"}
                </Button>
              </Stack>
            </Stack>
          </>
        ) : null}
      </Box>
    </Box>
  );
}

function UploadPage() {
  return getBrowserSession()?.role === "STUDENT" ? (
    <StudentUploadWizard />
  ) : (
    <TutorUploadWizard />
  );
}

export default function Page() {
  return (
    <React.Suspense fallback={null}>
      <UploadPage />
    </React.Suspense>
  );
}
