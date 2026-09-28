"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import { useParams, useRouter } from "next/navigation";
import TutorWorksheetDetail from "@/components/worksheets/TutorWorksheetDetail";
import {
  fetchTutorWorksheet,
  type TutorWorksheet,
} from "@/services/worksheets";
import { tutorWorksheetsHref, worksheetClassId } from "../navigation";

function DetailSkeleton() {
  return (
    <Box
      data-testid="tutor-worksheet-detail-skeleton"
      sx={{ maxWidth: 1420, mx: "auto", p: { xs: 2, sm: 3 } }}
    >
      <Skeleton width={150} />
      <Skeleton height={55} width="48%" sx={{ mt: 1 }} />
      <Skeleton width="35%" />
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2.5, mt: 3 }}>
        <Card
          variant="outlined"
          sx={{
            flex: "1 1 460px",
            p: 3,
            borderColor: "#EBE4D9",
            borderRadius: "14px",
          }}
        >
          <Skeleton height={32} width="28%" />
          <Skeleton height={86} />
          <Skeleton height={86} />
        </Card>
        <Card
          variant="outlined"
          sx={{
            flex: "0 1 320px",
            p: 3,
            borderColor: "#EBE4D9",
            borderRadius: "14px",
          }}
        >
          <Skeleton height={34} />
          <Skeleton height={92} />
        </Card>
      </Box>
    </Box>
  );
}

export default function Page() {
  const { worksheetId } = useParams<{ worksheetId: string }>();
  const router = useRouter();
  const id = Number(worksheetId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const [data, setData] = React.useState<TutorWorksheet | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [retry, setRetry] = React.useState(0);
  React.useEffect(() => {
    if (!validId) return;
    let active = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reset route-specific state before the next request.
    setData(null);
    setError(null);
    fetchTutorWorksheet(id)
      .then((loaded) => {
        if (active) setData(loaded);
      })
      .catch((caught: unknown) => {
        if (active)
          setError(
            caught instanceof Error
              ? caught.message
              : "Worksheet could not be opened.",
          );
      });
    return () => {
      active = false;
    };
  }, [id, validId, retry]);
  if (!validId)
    return (
      <Box sx={{ maxWidth: 760, mx: "auto", p: 3 }}>
        <Card
          component="section"
          role="alert"
          variant="outlined"
          sx={{ p: 3, borderColor: "#F0DCD4", borderRadius: "14px" }}
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
          <Typography sx={{ color: "#6F675E", mt: 1 }}>
            The worksheet reference is invalid.
          </Typography>
        </Card>
      </Box>
    );
  if (error)
    return (
      <Box sx={{ maxWidth: 760, mx: "auto", p: 3 }}>
        <Card
          component="section"
          role="alert"
          variant="outlined"
          sx={{ p: 3, borderColor: "#F0DCD4", borderRadius: "14px" }}
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
          <Typography sx={{ color: "#6F675E", mt: 1, mb: 2 }}>
            {error}
          </Typography>
          <Button
            onClick={() => setRetry((value) => value + 1)}
            variant="outlined"
            sx={{
              borderColor: "#E4DCD0",
              color: "#2A2622",
              textTransform: "none",
            }}
          >
            Retry loading worksheet
          </Button>
        </Card>
      </Box>
    );
  if (!data) return <DetailSkeleton />;
  return (
    <TutorWorksheetDetail
      key={worksheetId}
      worksheet={data}
      onApproved={(worksheet) =>
        router.replace(
          tutorWorksheetsHref({
            classId: worksheetClassId(worksheet),
            approved: true,
          }),
        )
      }
    />
  );
}
