"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  dismissTutorAlert,
  fetchTutorAlerts,
  resolveTutorAlert,
  type TutorAlert,
} from "@/services/alerts";

const severityPresentation = {
  INFO: { bg: "#F0EAE0", color: "#6F675E", label: "MONITOR" },
  WARNING: { bg: "#F7E3DC", color: "#9E3A24", label: "NEEDS PRACTICE" },
  CRITICAL: { bg: "#F7E3DC", color: "#9E3A24", label: "HIGH PRIORITY" },
} as const;

interface AlertListProps {
  loadAlerts?: () => Promise<TutorAlert[]>;
  resolve?: (id: number) => Promise<TutorAlert>;
  dismiss?: (id: number) => Promise<TutorAlert>;
}

function errorMessage(reason: unknown, fallback: string): string {
  return reason instanceof Error ? reason.message : fallback;
}

function LoadingAlerts() {
  return (
    <Box aria-label="Loading alerts" sx={{ display: "grid", gap: 1.25 }}>
      {[0, 1, 2].map((index) => (
        <Card
          key={index}
          variant="outlined"
          sx={{ p: 2, bgcolor: "#FFFDFA", borderColor: "#EBE4D9" }}
        >
          <Skeleton height={26} width="40%" />
          <Skeleton height={44} />
        </Card>
      ))}
    </Box>
  );
}

function EmptyAlerts() {
  return (
    <Card
      component="section"
      variant="outlined"
      sx={{
        textAlign: "center",
        p: 4,
        minHeight: 280,
        display: "grid",
        placeItems: "center",
        bgcolor: "#FFFDFA",
        border: "1px dashed #DCCFBE",
      }}
    >
      <Box>
        <Typography
          component="h2"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 23,
          }}
        >
          No active alerts
        </Typography>
        <Typography sx={{ color: "#8B837A", mt: 0.75 }}>
          New tutor actions will appear here when evidence needs attention.
        </Typography>
      </Box>
    </Card>
  );
}

export default function AlertList({
  loadAlerts = fetchTutorAlerts,
  resolve = resolveTutorAlert,
  dismiss = dismissTutorAlert,
}: AlertListProps) {
  const [alerts, setAlerts] = React.useState<TutorAlert[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busyAlertId, setBusyAlertId] = React.useState<number | null>(null);

  const load = React.useCallback(async () => {
    setError(null);
    try {
      setAlerts(await loadAlerts());
    } catch (reason) {
      setError(errorMessage(reason, "Alerts could not be loaded."));
    }
  }, [loadAlerts]);

  React.useEffect(() => {
    let isCurrent = true;

    void loadAlerts().then(
      (loadedAlerts) => {
        if (isCurrent) setAlerts(loadedAlerts);
      },
      (reason: unknown) => {
        if (isCurrent)
          setError(errorMessage(reason, "Alerts could not be loaded."));
      },
    );

    return () => {
      isCurrent = false;
    };
  }, [loadAlerts]);

  const updateAlert = async (
    alert: TutorAlert,
    action: (id: number) => Promise<TutorAlert>,
  ) => {
    setBusyAlertId(alert.id);
    setError(null);
    try {
      const updatedAlert = await action(alert.id);
      setAlerts((currentAlerts) =>
        (currentAlerts ?? []).filter(({ id }) => id !== updatedAlert.id),
      );
    } catch (reason) {
      setError(errorMessage(reason, "Alert could not be updated."));
    } finally {
      setBusyAlertId(null);
    }
  };

  if (!alerts && !error) return <LoadingAlerts />;
  if (error) {
    return (
      <Card
        component="section"
        role="alert"
        variant="outlined"
        sx={{
          p: 3,
          maxWidth: 560,
          bgcolor: "#FFFDFA",
          borderColor: "#EBE4D9",
          borderLeft: "3px solid #B4573F",
        }}
      >
        <Typography
          component="h2"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 21,
          }}
        >
          Alerts could not be loaded
        </Typography>
        <Typography sx={{ mt: 0.75, color: "#5A544C" }}>{error}</Typography>
        <Button
          onClick={() => void load()}
          variant="outlined"
          sx={{
            mt: 1.5,
            textTransform: "none",
            borderColor: "#E4DCD0",
            color: "#2A2622",
          }}
        >
          Try again
        </Button>
      </Card>
    );
  }
  if (!alerts?.length) return <EmptyAlerts />;

  return (
    <Box sx={{ display: "grid", gap: 1.25 }}>
      {alerts.map((alert) => {
        const presentation = severityPresentation[alert.severity];

        return (
          <Card
            key={alert.id}
            component="article"
            variant="outlined"
            sx={{
              p: { xs: 2, sm: 2.25 },
              bgcolor: "#FFFDFA",
              borderColor: "#EBE4D9",
              borderRadius: "12px",
              boxShadow: "none",
            }}
          >
            <Box
              sx={{
                display: "flex",
                flexWrap: "wrap",
                alignItems: "flex-start",
                justifyContent: "space-between",
                gap: 1,
              }}
            >
              <Box sx={{ minWidth: 0, flex: "1 1 280px" }}>
                <Typography
                  component="h2"
                  sx={{
                    fontFamily: "'Playfair Display', Georgia, serif",
                    fontSize: 21,
                  }}
                >
                  {alert.title}
                </Typography>
                <Typography
                  sx={{
                    color: "#6F675E",
                    fontSize: 13.5,
                    mt: 0.5,
                    lineHeight: 1.55,
                  }}
                >
                  {alert.message}
                </Typography>
                <Typography sx={{ color: "#8B837A", fontSize: 12, mt: 0.9 }}>
                  Student:{" "}
                  <Link
                    href={`/students/${alert.studentId}`}
                    style={{ color: "inherit" }}
                  >
                    {alert.studentName}
                  </Link>
                </Typography>
              </Box>
              <Chip
                label={presentation.label}
                size="small"
                sx={{
                  bgcolor: presentation.bg,
                  color: presentation.color,
                  fontWeight: 700,
                  fontSize: 10,
                  letterSpacing: ".04em",
                }}
              />
            </Box>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mt: 1.5 }}>
              <Button
                onClick={() => void updateAlert(alert, resolve)}
                disabled={busyAlertId !== null}
                sx={{
                  minHeight: 36,
                  textTransform: "none",
                  bgcolor: "#9E3A24",
                  color: "#FFFDFA",
                  "&:hover": { bgcolor: "#8A3120" },
                }}
              >
                Resolve alert
              </Button>
              <Button
                onClick={() => void updateAlert(alert, dismiss)}
                disabled={busyAlertId !== null}
                variant="outlined"
                sx={{
                  minHeight: 36,
                  textTransform: "none",
                  borderColor: "#E4DCD0",
                  color: "#5A544C",
                }}
              >
                Dismiss alert
              </Button>
            </Box>
          </Card>
        );
      })}
    </Box>
  );
}
