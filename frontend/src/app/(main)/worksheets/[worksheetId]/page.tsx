"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import DownloadOutlinedIcon from "@mui/icons-material/DownloadOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams } from "next/navigation";

import {
  downloadStudentWorksheetPdf,
  fetchWorksheetImageUrl,
  fetchStudentWorksheet,
  type StudentWorksheetDetail,
} from "@/services/worksheets";
import SecureImageGallery from "@/components/questions/SecureImageGallery";

const INK = "#2A2622";
const MUTED = "#6F675E";
const BORDER = "#EBE4D9";
const CARD_BG = "#FFFDFA";

function validId(value: string): boolean {
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
}

function questionTypeLabel(value: string): string {
  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function DetailSkeleton() {
  return (
    <Stack
      spacing={1.5}
      sx={{ py: 10, alignItems: "center" }}
      data-testid="student-worksheet-detail-loading"
    >
      <CircularProgress size={28} aria-label="Loading worksheet" />
      <Typography sx={{ color: MUTED }}>Loading worksheet…</Typography>
    </Stack>
  );
}

function Detail({
  worksheet,
  onExport,
  exporting,
}: {
  worksheet: StudentWorksheetDetail;
  onExport: () => void;
  exporting: boolean;
}) {
  const totalMarks = worksheet.questions.reduce(
    (total, question) => total + question.totalMarks,
    0,
  );
  return (
    <>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{
          alignItems: { sm: "flex-start" },
          justifyContent: "space-between",
          mb: 2.5,
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Stack
            direction="row"
            spacing={1}
            useFlexGap
            sx={{ mb: 0.9, flexWrap: "wrap" }}
          >
            <Chip
              label="ASSIGNED WORKSHEET"
              size="small"
              sx={{
                bgcolor: "#F3EBDD",
                color: "#7A6238",
                fontSize: 10,
                fontWeight: 700,
                letterSpacing: ".05em",
              }}
            />
            {worksheet.subject ? (
              <Typography sx={{ color: "#8B837A", fontSize: 12, pt: 0.3 }}>
                {worksheet.subject}
              </Typography>
            ) : null}
          </Stack>
          <Typography
            component="h1"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: { xs: 30, sm: 38 },
              lineHeight: 1.15,
            }}
          >
            {worksheet.title}
          </Typography>
          <Typography sx={{ color: MUTED, mt: 0.75, fontSize: 13 }}>
            {worksheet.code} · {worksheet.questions.length} question
            {worksheet.questions.length === 1 ? "" : "s"} ·{" "}
            {totalMarks.toFixed(1)} marks
          </Typography>
        </Box>
        <Button
          onClick={onExport}
          disabled={exporting}
          startIcon={<DownloadOutlinedIcon />}
          sx={{
            minHeight: 40,
            flex: "0 0 auto",
            bgcolor: "#9E3A24",
            color: "#FFFDFA",
            textTransform: "none",
            fontWeight: 600,
            "&:hover": { bgcolor: "#7F2D1D" },
          }}
        >
          {exporting ? "Preparing PDF…" : "Download PDF"}
        </Button>
      </Stack>

      {worksheet.instructions ? (
        <Card
          component="section"
          variant="outlined"
          sx={{
            borderColor: BORDER,
            borderRadius: "12px",
            bgcolor: "#F9F4EC",
            p: { xs: 2, sm: 2.5 },
            mb: 2.5,
          }}
        >
          <Typography
            component="h2"
            sx={{
              fontSize: 13,
              fontWeight: 700,
              letterSpacing: ".06em",
              color: "#6F675E",
              textTransform: "uppercase",
              mb: 0.75,
            }}
          >
            Instructions
          </Typography>
          <Typography
            sx={{
              color: INK,
              fontSize: 14,
              lineHeight: 1.6,
              whiteSpace: "pre-wrap",
            }}
          >
            {worksheet.instructions}
          </Typography>
        </Card>
      ) : null}

      <Card
        component="section"
        variant="outlined"
        sx={{
          borderColor: BORDER,
          borderRadius: "12px",
          bgcolor: CARD_BG,
          p: { xs: 2, sm: 3 },
        }}
      >
        <Typography
          component="h2"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 25,
            mb: 0.5,
          }}
        >
          Questions
        </Typography>
        <Typography sx={{ color: MUTED, fontSize: 13, mb: 2.25 }}>
          Read every question before completing your work. Your tutor will
          review your submitted answers.
        </Typography>
        {worksheet.questions.length === 0 ? (
          <Box
            role="status"
            sx={{
              border: "1px dashed #DCCFBE",
              borderRadius: "10px",
              p: 2,
              color: MUTED,
              fontSize: 13,
            }}
          >
            This approved worksheet has no questions available. Please contact
            your tutor.
          </Box>
        ) : (
          <Box component="ol" sx={{ m: 0, pl: { xs: 2.75, sm: 3.5 } }}>
            {worksheet.questions.map((question, index) => (
              <Box
                component="li"
                key={question.id}
                sx={{
                  py: 2,
                  borderBottom:
                    index === worksheet.questions.length - 1
                      ? "none"
                      : "1px solid #F0EAE0",
                }}
              >
                <Typography
                  sx={{
                    color: INK,
                    fontWeight: 600,
                    fontSize: 14.5,
                    lineHeight: 1.55,
                    whiteSpace: "pre-wrap",
                  }}
                >
                  {question.prompt}
                </Typography>
                <SecureImageGallery
                  images={question.images ?? []}
                  loadImage={(imageId) =>
                    fetchWorksheetImageUrl("student", worksheet.id, imageId)
                  }
                />
                <Typography sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.75 }}>
                  {question.code} · {question.topicName} ·{" "}
                  {questionTypeLabel(question.questionType)} ·{" "}
                  {question.totalMarks.toFixed(1)} marks
                </Typography>
              </Box>
            ))}
          </Box>
        )}
      </Card>
    </>
  );
}

export default function StudentWorksheetDetailPage() {
  const { worksheetId } = useParams<{ worksheetId: string }>();
  const id = Number(worksheetId);
  const isValid = validId(worksheetId);
  const [worksheet, setWorksheet] =
    React.useState<StudentWorksheetDetail | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(isValid);
  const [retry, setRetry] = React.useState(0);
  const [exporting, setExporting] = React.useState(false);
  const [exportError, setExportError] = React.useState<string | null>(null);

  const load = React.useCallback(async () => {
    if (!isValid) return;
    setLoading(true);
    setError(null);
    setWorksheet(null);
    try {
      const loaded = await fetchStudentWorksheet(id);
      if (loaded.id !== id)
        throw new Error("The loaded worksheet does not match this link.");
      setWorksheet(loaded);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "This worksheet could not be loaded. Please try again.",
      );
    } finally {
      setLoading(false);
    }
  }, [id, isValid]);

  React.useEffect(() => {
    void Promise.resolve().then(load);
  }, [load, retry]);

  const exportPdf = async () => {
    setExporting(true);
    setExportError(null);
    try {
      const blob = await downloadStudentWorksheetPdf(id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${worksheet?.code || "worksheet"}.pdf`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (reason) {
      setExportError(
        reason instanceof Error
          ? reason.message
          : "Worksheet PDF could not be downloaded. Please try again.",
      );
    } finally {
      setExporting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 4 },
        py: { xs: 3, sm: 4 },
        color: INK,
      }}
    >
      <Box sx={{ maxWidth: 940, mx: "auto" }}>
        <Button
          component={Link}
          href="/worksheets"
          startIcon={<ArrowBackIcon sx={{ fontSize: 16 }} />}
          sx={{
            color: "#5A544C",
            textTransform: "none",
            fontSize: 14,
            mb: 2.5,
            p: 0,
            minWidth: 0,
            "&:hover": { bgcolor: "transparent", color: INK },
          }}
        >
          Back to Worksheets
        </Button>
        {!isValid ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              p: 3,
              borderColor: "#F0DCD4",
              borderRadius: "12px",
              bgcolor: CARD_BG,
            }}
          >
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 25,
              }}
            >
              Worksheet could not be opened
            </Typography>
            <Typography sx={{ color: MUTED, mt: 0.75 }}>
              The worksheet reference is invalid.
            </Typography>
          </Card>
        ) : null}
        {loading ? <DetailSkeleton /> : null}
        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              p: 3,
              borderColor: "#F0DCD4",
              borderRadius: "12px",
              bgcolor: CARD_BG,
            }}
          >
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 25,
              }}
            >
              Worksheet could not be opened
            </Typography>
            <Typography sx={{ color: "#9E3A24", mt: 0.75 }}>{error}</Typography>
            <Button
              onClick={() => setRetry((value) => value + 1)}
              sx={{ mt: 1.5, textTransform: "none" }}
            >
              Retry loading worksheet
            </Button>
          </Card>
        ) : null}
        {exportError ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              p: 2,
              mb: 2,
              borderColor: "#F0DCD4",
              borderRadius: "12px",
              bgcolor: "#FDF8F7",
            }}
          >
            <Typography sx={{ color: "#9E3A24" }}>{exportError}</Typography>
          </Card>
        ) : null}
        {worksheet ? (
          <Detail
            worksheet={worksheet}
            onExport={() => void exportPdf()}
            exporting={exporting}
          />
        ) : null}
      </Box>
    </Box>
  );
}
