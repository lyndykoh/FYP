"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import FileDownloadOutlinedIcon from "@mui/icons-material/FileDownloadOutlined";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import type { AuthRole } from "@/lib/auth";
import {
  downloadProgressReportPdf,
  fetchProgressReport,
  type ProgressReport as ProgressReportData,
  type ProgressReportSnapshot,
} from "@/services/reports";

export interface ProgressReportProps {
  reportId: number;
  viewerRole: AuthRole;
  loadReport?: (
    reportId: number,
    role: AuthRole,
  ) => Promise<ProgressReportData>;
  downloadPdf?: (reportId: number, role: AuthRole) => Promise<Blob>;
}

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;
const focusOutline = {
  "&:focus-visible": { outline: "3px solid #E08A72", outlineOffset: 2 },
} as const;

function humaniseKey(value: string): string {
  return value
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/[_-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^./, (letter) => letter.toUpperCase());
}

function textValue(value: string | number | boolean | null): string {
  if (value === null) return "Not recorded";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function SnapshotValue({ value }: { value: unknown }) {
  if (
    value === null ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  ) {
    return (
      <Typography
        sx={{
          color: "#4A443D",
          fontSize: 13.5,
          lineHeight: 1.65,
          overflowWrap: "anywhere",
        }}
      >
        {textValue(value)}
      </Typography>
    );
  }
  if (Array.isArray(value)) {
    if (!value.length)
      return (
        <Typography sx={{ color: "#8B837A", fontSize: 13 }}>
          No evidence was recorded for this section.
        </Typography>
      );
    return (
      <Box component="ul" sx={{ m: 0, pl: 2.25, display: "grid", gap: 0.65 }}>
        {value.map((item, index) => (
          <Box component="li" key={index} sx={{ color: "#4A443D", pl: 0.25 }}>
            <SnapshotValue value={item} />
          </Box>
        ))}
      </Box>
    );
  }
  if (isRecord(value)) {
    const entries = Object.entries(value);
    if (!entries.length)
      return (
        <Typography sx={{ color: "#8B837A", fontSize: 13 }}>
          No evidence was recorded for this section.
        </Typography>
      );
    return (
      <Box
        component="dl"
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "minmax(112px, .42fr) 1fr" },
          gap: 1.1,
          m: 0,
        }}
      >
        {entries.map(([key, item]) => (
          <React.Fragment key={key}>
            <Typography
              component="dt"
              sx={{
                color: "#6F675E",
                fontSize: 11.5,
                fontWeight: 700,
                letterSpacing: ".02em",
                overflowWrap: "anywhere",
              }}
            >
              {humaniseKey(key)}
            </Typography>
            <Box component="dd" sx={{ m: 0, minWidth: 0 }}>
              <SnapshotValue value={item} />
            </Box>
          </React.Fragment>
        ))}
      </Box>
    );
  }
  return (
    <Typography sx={{ color: "#8B837A", fontSize: 13 }}>
      This evidence format is not supported.
    </Typography>
  );
}

function SnapshotSections({ snapshot }: { snapshot: ProgressReportSnapshot }) {
  const entries = Object.entries(snapshot);
  if (!entries.length) {
    return (
      <Card
        component="section"
        aria-labelledby="report-evidence-heading"
        variant="outlined"
        sx={{
          ...card,
          p: { xs: 2.25, sm: 3 },
          borderStyle: "dashed",
          borderColor: "#DCCFBE",
          textAlign: "center",
          minHeight: 212,
          display: "grid",
          placeItems: "center",
        }}
      >
        <Box>
          <Typography
            id="report-evidence-heading"
            component="h2"
            sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500 }}
          >
            No evidence has been added
          </Typography>
          <Typography
            sx={{ color: "#8B837A", fontSize: 13.5, lineHeight: 1.6, mt: 0.75 }}
          >
            This saved report has no recorded progress evidence yet.
          </Typography>
        </Box>
      </Card>
    );
  }
  return (
    <Box
      component="section"
      aria-labelledby="report-evidence-heading"
      sx={{ display: "grid", gap: 1.5 }}
    >
      <Box>
        <Typography
          id="report-evidence-heading"
          component="h2"
          sx={{ fontFamily: serif, fontSize: 24, fontWeight: 500 }}
        >
          Progress evidence
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 13, mt: 0.4 }}>
          These statements are the saved evidence for this reporting period.
        </Typography>
      </Box>
      <Box
        data-testid="report-evidence-grid"
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
          gap: 1.5,
        }}
      >
        {entries.map(([key, value]) => (
          <Card
            key={key}
            component="section"
            variant="outlined"
            sx={{ ...card, p: { xs: 1.75, sm: 2.25 }, minWidth: 0 }}
          >
            <Typography
              component="h3"
              sx={{ color: "#2A2622", fontSize: 15, fontWeight: 700, mb: 1.1 }}
            >
              {humaniseKey(key)}
            </Typography>
            <SnapshotValue value={value} />
          </Card>
        ))}
      </Box>
    </Box>
  );
}

function ReportSkeleton() {
  return (
    <Box
      aria-label="Loading progress report"
      data-testid="progress-report-skeleton"
      sx={{ display: "grid", gap: 2.5 }}
    >
      <Skeleton
        variant="text"
        height={56}
        width="42%"
        sx={{ bgcolor: "#F0EAE0" }}
      />
      <Card variant="outlined" sx={{ ...card, p: { xs: 2, sm: 2.5 } }}>
        <Skeleton height={30} width="36%" sx={{ bgcolor: "#F0EAE0" }} />
        <Skeleton height={80} sx={{ mt: 1, bgcolor: "#F0EAE0" }} />
      </Card>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))",
          gap: 1.5,
        }}
      >
        {[0, 1].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={142}
            sx={{ borderRadius: "14px", bgcolor: "#F0EAE0" }}
          />
        ))}
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
        sx={{ fontFamily: serif, fontSize: 24, fontWeight: 500 }}
      >
        Progress report could not be loaded
      </Typography>
      <Typography
        sx={{
          color: "#5A544C",
          fontSize: 13.5,
          lineHeight: 1.6,
          mt: 0.75,
          mb: 2,
        }}
      >
        {message}
      </Typography>
      <Button
        onClick={retry}
        variant="outlined"
        sx={{
          minHeight: 40,
          textTransform: "none",
          borderColor: "#E4DCD0",
          color: "#2A2622",
          ...focusOutline,
        }}
      >
        Try again
      </Button>
    </Card>
  );
}

function displayDate(value: string | null): string {
  return value
    ? value.replace("T", " · ").replace(/:\d{2}(?:\.\d+)?$/, "")
    : "Not finalised";
}

export default function ProgressReport({
  reportId,
  viewerRole,
  loadReport = fetchProgressReport,
  downloadPdf = downloadProgressReportPdf,
}: ProgressReportProps) {
  const [report, setReport] = React.useState<ProgressReportData | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [exportError, setExportError] = React.useState<string | null>(null);
  const [exporting, setExporting] = React.useState(false);
  const exportingRef = React.useRef(false);
  const [attempt, setAttempt] = React.useState(0);
  const validReportId = Number.isSafeInteger(reportId) && reportId > 0;

  React.useEffect(() => {
    if (!validReportId) return;
    let current = true;
    void loadReport(reportId, viewerRole).then(
      (loaded) => {
        if (current) {
          setReport(loaded);
          setError(null);
        }
      },
      (reason: unknown) => {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "This progress report could not be loaded.",
          );
      },
    );
    return () => {
      current = false;
    };
  }, [attempt, loadReport, reportId, validReportId, viewerRole]);

  if (!validReportId)
    return (
      <ErrorState
        message="This progress report reference is invalid."
        retry={() => setAttempt((value) => value + 1)}
      />
    );

  if (error)
    return (
      <ErrorState
        message={error}
        retry={() => setAttempt((value) => value + 1)}
      />
    );
  if (!report) return <ReportSkeleton />;

  const final = report.status === "FINAL";
  const statusStyle = final
    ? { bgcolor: "#E4EDE4", color: "#4A6B50", label: "FINAL · IMMUTABLE" }
    : { bgcolor: "#F0EAE0", color: "#6F675E", label: "DRAFT · READ ONLY" };
  const recipient =
    viewerRole === "TUTOR" ? "Tutor view" : "Student recipient view";

  const exportPdf = async () => {
    if (exportingRef.current) return;
    exportingRef.current = true;
    setExporting(true);
    setExportError(null);
    let objectUrl: string | null = null;
    let temporaryAnchor: HTMLAnchorElement | null = null;
    try {
      const blob = await downloadPdf(report.id, viewerRole);
      objectUrl = URL.createObjectURL(blob);
      temporaryAnchor = document.createElement("a");
      temporaryAnchor.href = objectUrl;
      temporaryAnchor.download = `${report.reportCode}.pdf`;
      temporaryAnchor.style.display = "none";
      document.body.appendChild(temporaryAnchor);
      temporaryAnchor.click();
    } catch (reason: unknown) {
      setExportError(
        reason instanceof Error
          ? reason.message
          : "This progress report PDF could not be downloaded. Please try again.",
      );
    } finally {
      temporaryAnchor?.remove();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      exportingRef.current = false;
      setExporting(false);
    }
  };

  return (
    <Box sx={{ display: "grid", gap: 2.5, color: "#2A2622" }}>
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "flex-start",
          justifyContent: "space-between",
          gap: 2,
        }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography
            sx={{
              color: "#A09488",
              fontSize: 10.5,
              fontWeight: 700,
              letterSpacing: ".13em",
              mb: 0.75,
            }}
          >
            SAVED PROGRESS REPORT
          </Typography>
          <Typography
            component="h1"
            sx={{
              fontFamily: serif,
              fontSize: { xs: 32, sm: 39 },
              fontWeight: 500,
              lineHeight: 1.1,
              letterSpacing: "-.02em",
            }}
          >
            {report.studentName}&apos;s progress report
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 13.5, mt: 0.75 }}>
            {report.reportCode} · {report.periodStart} to {report.periodEnd}
          </Typography>
        </Box>
        <Chip
          aria-label={`Report status: ${final ? "Final, immutable" : "Draft, read only"}`}
          label={statusStyle.label}
          sx={{
            height: 26,
            bgcolor: statusStyle.bgcolor,
            color: statusStyle.color,
            fontSize: 9.5,
            fontWeight: 700,
            letterSpacing: ".05em",
            flex: "0 0 auto",
          }}
        />
      </Box>

      <Card
        component="section"
        aria-labelledby="report-snapshot-heading"
        variant="outlined"
        sx={{ ...card, p: { xs: 2, sm: 2.5 } }}
      >
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 2,
            alignItems: "flex-start",
            justifyContent: "space-between",
          }}
        >
          <Box sx={{ minWidth: 0, flex: "1 1 260px" }}>
            <Typography
              id="report-snapshot-heading"
              component="h2"
              sx={{ fontFamily: serif, fontSize: 23, fontWeight: 500 }}
            >
              {final ? "Finalised snapshot" : "Draft snapshot"}
            </Typography>
            <Typography
              sx={{
                color: "#5A544C",
                fontSize: 13.5,
                lineHeight: 1.6,
                mt: 0.6,
              }}
            >
              {final
                ? "This final report is a saved, immutable record. Later learning activity does not change the evidence shown here."
                : "This draft is shown read-only in this report view. It has not been finalised for the student recipient."}
            </Typography>
          </Box>
          <Typography
            aria-label={`Report viewer: ${recipient}`}
            sx={{ color: "#8B837A", fontSize: 12, flex: "0 0 auto" }}
          >
            {recipient}
          </Typography>
        </Box>
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
            gap: 1.25,
            borderTop: "1px solid #F0EAE0",
            mt: 2,
            pt: 1.75,
          }}
        >
          <Box>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".06em",
              }}
            >
              SAVED
            </Typography>
            <Typography sx={{ color: "#4A443D", fontSize: 13, mt: 0.35 }}>
              {displayDate(report.generatedAt)}
            </Typography>
          </Box>
          <Box>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".06em",
              }}
            >
              FINALISED
            </Typography>
            <Typography sx={{ color: "#4A443D", fontSize: 13, mt: 0.35 }}>
              {displayDate(report.finalizedAt)}
            </Typography>
          </Box>
          <Box>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".06em",
              }}
            >
              ACCESS
            </Typography>
            <Typography sx={{ color: "#4A443D", fontSize: 13, mt: 0.35 }}>
              {recipient}
            </Typography>
          </Box>
        </Box>
      </Card>

      <SnapshotSections snapshot={report.snapshot} />

      <Box
        sx={{ display: "flex", flexWrap: "wrap", gap: 1, alignItems: "center" }}
      >
        <Button
          onClick={() => void exportPdf()}
          disabled={exporting}
          aria-busy={exporting || undefined}
          startIcon={<FileDownloadOutlinedIcon />}
          variant="outlined"
          sx={{
            minHeight: 40,
            textTransform: "none",
            borderColor: "#E4DCD0",
            color: "#2A2622",
            "&:hover": { borderColor: "#DCCFBE", bgcolor: "#F4EFE6" },
            ...focusOutline,
          }}
        >
          {exporting ? "Preparing PDF…" : "Export PDF"}
        </Button>
        {viewerRole === "TUTOR" ? (
          <Button
            component={Link}
            href={`/students/${report.studentId}`}
            startIcon={<OpenInNewIcon />}
            variant="outlined"
            sx={{
              minHeight: 40,
              textTransform: "none",
              borderColor: "#E4DCD0",
              color: "#2A2622",
              ...focusOutline,
            }}
          >
            Open student profile
          </Button>
        ) : (
          <Button
            component={Link}
            href="/progress"
            startIcon={<ArrowBackIcon />}
            variant="outlined"
            sx={{
              minHeight: 40,
              textTransform: "none",
              borderColor: "#E4DCD0",
              color: "#2A2622",
              ...focusOutline,
            }}
          >
            Back to my progress
          </Button>
        )}
      </Box>
      {exportError && (
        <Card
          component="section"
          role="alert"
          aria-live="assertive"
          variant="outlined"
          sx={{ ...card, p: 2, borderLeft: "3px solid #B4573F" }}
        >
          <Typography
            sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6 }}
          >
            {exportError}
          </Typography>
          <Button
            onClick={() => void exportPdf()}
            disabled={exporting}
            variant="outlined"
            sx={{
              minHeight: 38,
              mt: 1.25,
              textTransform: "none",
              borderColor: "#E4DCD0",
              color: "#2A2622",
              ...focusOutline,
            }}
          >
            Try export again
          </Button>
        </Card>
      )}
    </Box>
  );
}
