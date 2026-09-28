"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";

import {
  fetchTutorWorksheets,
  type TutorWorksheet,
} from "@/services/worksheets";
import { tutorWorksheetsHref } from "./navigation";

function statusLabel(status: TutorWorksheet["status"]) {
  return status === "DRAFT"
    ? "Draft"
    : status === "APPROVED"
      ? "Assigned"
      : "Archived";
}

export default function TutorWorksheetsPage() {
  const params = useSearchParams();
  const router = useRouter();
  const classId = Number(params.get("classId"));
  const validClassId = Number.isSafeInteger(classId) && classId > 0;
  const showApprovalConfirmation = params.get("approved") === "1";
  const [approvalConfirmation, setApprovalConfirmation] = React.useState(
    showApprovalConfirmation,
  );
  const [worksheets, setWorksheets] = React.useState<TutorWorksheet[] | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);
  const load = React.useCallback(async () => {
    setWorksheets(null);
    setError(null);
    try {
      setWorksheets(
        await fetchTutorWorksheets(validClassId ? classId : undefined),
      );
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Worksheets could not be loaded. Please try again.",
      );
    }
  }, [classId, validClassId]);
  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the async load owns its loading state.
    void load();
  }, [load]);
  React.useEffect(() => {
    if (!showApprovalConfirmation) return;
    router.replace(
      tutorWorksheetsHref({ classId: validClassId ? classId : undefined }),
    );
  }, [classId, router, showApprovalConfirmation, validClassId]);
  React.useEffect(() => {
    if (!approvalConfirmation) return;
    const dismissConfirmation = window.setTimeout(
      () => setApprovalConfirmation(false),
      3600,
    );
    return () => window.clearTimeout(dismissConfirmation);
  }, [approvalConfirmation]);
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
      <Box sx={{ maxWidth: 1120, mx: "auto" }}>
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "flex-end",
            justifyContent: "space-between",
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
              WORKSHEET MANAGEMENT
            </Typography>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: { xs: 32, sm: 38 },
                fontWeight: 500,
              }}
            >
              Worksheets
            </Typography>
            <Typography sx={{ color: "#6F675E", fontSize: 14, mt: 0.75 }}>
              {validClassId
                ? `Showing worksheets assigned to class #${classId}.`
                : "Review and manage your generated worksheets."}
            </Typography>
          </Box>
          <Button
            component={Link}
            href={
              validClassId
                ? `/tutor/worksheets/new?classId=${classId}`
                : "/tutor/worksheets/new"
            }
            sx={{
              minHeight: 42,
              borderRadius: "10px",
              bgcolor: "#E08A72",
              color: "#1B1917",
              textTransform: "none",
              fontWeight: 600,
              px: 2.25,
            }}
          >
            Generate Worksheet
          </Button>
        </Box>
        {approvalConfirmation && (
          <Box
            role="status"
            aria-live="polite"
            aria-atomic="true"
            sx={{
              position: "fixed",
              bottom: 26,
              left: "50%",
              transform: "translateX(-50%)",
              zIndex: 90,
              maxWidth: "92vw",
              bgcolor: "#1B1917",
              color: "#F4EFE6",
              borderRadius: "11px",
              px: 2.75,
              py: 1.75,
              boxShadow: "0 12px 34px rgba(42,38,34,.28)",
              fontSize: 13.5,
            }}
          >
            Worksheet Sent to Students
          </Box>
        )}
        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{
              p: 3,
              borderLeft: "3px solid #B4573F",
              borderColor: "#EBE4D9",
              bgcolor: "#FFFDFA",
            }}
          >
            <Typography sx={{ mb: 1 }}>{error}</Typography>
            <Button
              onClick={() => void load()}
              sx={{ color: "#9E3A24", textTransform: "none" }}
            >
              Retry loading worksheets
            </Button>
          </Card>
        ) : worksheets === null ? (
          <Typography role="status" sx={{ color: "#8B837A" }}>
            Loading worksheets…
          </Typography>
        ) : worksheets.length === 0 ? (
          <Card
            variant="outlined"
            sx={{
              p: 3,
              border: "1px dashed #DCCFBE",
              bgcolor: "#FFFDFA",
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
              No worksheets here yet
            </Typography>
            <Typography sx={{ color: "#8B837A", fontSize: 13, mt: 0.75 }}>
              Generate a tutor-reviewed worksheet for this class.
            </Typography>
          </Card>
        ) : (
          <Box sx={{ display: "grid", gap: 1.25 }}>
            {worksheets.map((worksheet) => (
              <Card
                key={worksheet.id}
                variant="outlined"
                sx={{
                  p: { xs: 2, sm: 2.25 },
                  borderRadius: "12px",
                  borderColor: "#EBE4D9",
                  bgcolor: "#FFFDFA",
                  boxShadow: "none",
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "center",
                  gap: 1.25,
                }}
              >
                <Box sx={{ flex: "1 1 260px", minWidth: 0 }}>
                  <Typography sx={{ fontSize: 14, fontWeight: 600 }}>
                    {worksheet.title}
                  </Typography>
                  <Typography
                    sx={{ color: "#8B837A", fontSize: 11.5, mt: 0.45 }}
                  >
                    {worksheet.questions.length} questions ·{" "}
                    {worksheet.questions
                      .reduce(
                        (total, question) => total + question.totalMarks,
                        0,
                      )
                      .toFixed(1)}{" "}
                    marks
                  </Typography>
                </Box>
                <Chip
                  label={statusLabel(worksheet.status).toUpperCase()}
                  size="small"
                  sx={{
                    bgcolor:
                      worksheet.status === "APPROVED" ? "#E9EEE8" : "#F0EAE0",
                    color:
                      worksheet.status === "APPROVED" ? "#4A6B50" : "#6F675E",
                    fontSize: 9.5,
                    fontWeight: 700,
                  }}
                />
                <Button
                  component={Link}
                  href={`/tutor/worksheets/${worksheet.id}`}
                  sx={{
                    minHeight: 34,
                    color: "#9E3A24",
                    textTransform: "none",
                    fontWeight: 600,
                  }}
                >
                  Manage worksheet
                </Button>
              </Card>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
}
