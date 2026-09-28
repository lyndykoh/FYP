"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";

import MasteryMap from "@/components/mastery/MasteryMap";
import { fetchMasteryMap, type MasteryMapData } from "@/services/mastery";

export default function TopicsPage() {
  const [data, setData] = React.useState<MasteryMapData | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const load = React.useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      setData(await fetchMasteryMap());
    } catch (reason) {
      setData(null);
      setError(
        reason instanceof Error
          ? reason.message
          : "Topics could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, []);
  React.useEffect(() => {
    let current = true;
    void fetchMasteryMap().then(
      (loaded) => {
        if (current) {
          setData(loaded);
          setLoading(false);
        }
      },
      (reason: unknown) => {
        if (current) {
          setData(null);
          setError(
            reason instanceof Error
              ? reason.message
              : "Topics could not be loaded.",
          );
          setLoading(false);
        }
      },
    );
    return () => {
      current = false;
    };
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
      <Box sx={{ maxWidth: 1420, mx: "auto" }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          SYLLABUS TOPICS
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 40 },
            fontWeight: 500,
            mb: 0.65,
          }}
        >
          Learning journey
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 14, mb: 2.5 }}>
          Open a topic to review the approved evidence behind its current
          status.
        </Typography>
        {loading ? (
          <Card
            aria-label="Loading topic map"
            variant="outlined"
            sx={{ borderColor: "#EBE4D9", bgcolor: "#FFFDFA", p: 2.5 }}
          >
            <Skeleton height={34} width="38%" />
            <Skeleton height={92} sx={{ mt: 1 }} />
          </Card>
        ) : null}
        {error ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{ borderColor: "#F0DCD4", bgcolor: "#FDF6F3", p: 2.5 }}
          >
            <Typography
              component="h2"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 22,
                mb: 0.75,
              }}
            >
              Topics could not be loaded
            </Typography>
            <Typography sx={{ color: "#6F675E", fontSize: 13.5, mb: 1.5 }}>
              {error}
            </Typography>
            <Button
              onClick={() => void load()}
              variant="outlined"
              sx={{
                borderColor: "#E4DCD0",
                color: "#2A2622",
                textTransform: "none",
              }}
            >
              Try again
            </Button>
          </Card>
        ) : null}
        {data ? (
          <MasteryMap data={data} heading="Your syllabus topics" showFilters />
        ) : null}
      </Box>
    </Box>
  );
}
