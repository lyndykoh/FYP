"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import { useParams } from "next/navigation";

import { getBrowserSession, type AuthRole } from "@/lib/auth";
import ProgressReport from "@/components/reports/ProgressReport";

export default function ProgressReportPage() {
  const params = useParams<{ reportId: string }>();
  const reportId = Number(params.reportId);
  const [role, setRole] = React.useState<AuthRole | null>(null);

  React.useEffect(() => {
    // Deliberately read browser storage only after mount, preventing an SSR/client mismatch.
    const timer = window.setTimeout(
      () => setRole(getBrowserSession()?.role ?? null),
      0,
    );
    return () => window.clearTimeout(timer);
  }, []);

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
      <Box
        sx={{ maxWidth: 1120, mx: "auto", animation: "fadeUp .35s ease both" }}
      >
        {role ? (
          <ProgressReport reportId={reportId} viewerRole={role} />
        ) : (
          <Box
            aria-label="Checking report access"
            sx={{ display: "grid", gap: 1.25, maxWidth: 620 }}
          >
            <Typography
              sx={{
                color: "#A09488",
                fontSize: 10.5,
                fontWeight: 700,
                letterSpacing: ".13em",
              }}
            >
              PROGRESS REPORT
            </Typography>
            <Skeleton height={54} width="56%" sx={{ bgcolor: "#F0EAE0" }} />
            <Skeleton height={130} sx={{ bgcolor: "#F0EAE0" }} />
          </Box>
        )}
      </Box>
    </Box>
  );
}
