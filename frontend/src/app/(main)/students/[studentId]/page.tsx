"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import { useParams } from "next/navigation";

import MasteryMap from "@/components/mastery/MasteryMap";
import LearningInsightsPanel from "@/components/students/LearningInsightsPanel";
import StudentProfile from "@/components/students/StudentProfile";
import { fetchMasteryMap, type MasteryMapData } from "@/services/mastery";

function isValidStudentId(studentId: number) {
  return Number.isSafeInteger(studentId) && studentId > 0;
}

function masteryMapErrorMessage(reason: unknown) {
  return reason instanceof Error
    ? reason.message
    : "Mastery map could not be loaded.";
}

function MasteryMapSkeleton() {
  return (
    <Card
      aria-label="Loading student mastery map"
      variant="outlined"
      sx={{
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
        borderRadius: "14px",
        p: 2.5,
        mt: 2.5,
      }}
    >
      <Skeleton height={32} width="36%" />
      <Skeleton height={88} sx={{ mt: 1 }} />
    </Card>
  );
}

function MasteryMapLoadError({
  error,
  retry,
}: {
  error: string;
  retry: () => void;
}) {
  return (
    <Card
      component="section"
      role="alert"
      variant="outlined"
      sx={{
        borderColor: "#F0DCD4",
        bgcolor: "#FDF6F3",
        borderRadius: "14px",
        p: 2.5,
        mt: 2.5,
      }}
    >
      <Typography
        component="h2"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: 21,
          mb: 0.75,
        }}
      >
        Mastery map could not be loaded
      </Typography>
      <Typography sx={{ color: "#6F675E", fontSize: 13, mb: 1.25 }}>
        {error}
      </Typography>
      <Button
        onClick={retry}
        variant="outlined"
        sx={{ borderColor: "#E4DCD0", color: "#2A2622", textTransform: "none" }}
      >
        Try again
      </Button>
    </Card>
  );
}

function StudentMasteryMap({ studentId }: { studentId: number }) {
  const [data, setData] = React.useState<MasteryMapData | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const validStudentId = isValidStudentId(studentId);

  const load = React.useCallback(async () => {
    if (!validStudentId) {
      return;
    }

    setError(null);

    try {
      setData(await fetchMasteryMap(studentId));
    } catch (reason) {
      setError(masteryMapErrorMessage(reason));
    }
  }, [studentId, validStudentId]);

  React.useEffect(() => {
    if (!validStudentId) {
      return;
    }

    let isCurrentRequest = true;

    const loadInitialMasteryMap = async () => {
      try {
        const loadedMap = await fetchMasteryMap(studentId);
        if (isCurrentRequest) {
          setData(loadedMap);
        }
      } catch (reason) {
        if (isCurrentRequest) {
          setError(masteryMapErrorMessage(reason));
        }
      }
    };

    void loadInitialMasteryMap();

    return () => {
      isCurrentRequest = false;
    };
  }, [studentId, validStudentId]);

  if (!validStudentId) {
    return null;
  }

  if (!data && !error) {
    return <MasteryMapSkeleton />;
  }

  if (error) {
    return <MasteryMapLoadError error={error} retry={() => void load()} />;
  }

  if (!data) {
    return null;
  }

  return (
    <Box sx={{ mt: 2.5 }}>
      <MasteryMap
        data={data}
        studentId={studentId}
        heading="Canonical mastery map"
      />
    </Box>
  );
}

export default function StudentProfilePage() {
  const params = useParams<{ studentId: string }>();
  const studentId = Number(params.studentId);

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
        sx={{ maxWidth: 1420, mx: "auto", animation: "fadeUp .35s ease both" }}
      >
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          STUDENT MANAGEMENT
        </Typography>
        <StudentProfile studentId={studentId} />
        <Box sx={{ mt: 2.5 }}>
          <LearningInsightsPanel studentId={studentId} />
        </Box>
        <StudentMasteryMap studentId={studentId} />
      </Box>
    </Box>
  );
}
