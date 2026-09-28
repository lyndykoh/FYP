"use client";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import AlertList from "@/components/alerts/AlertList";
export default function TutorAlertsPage() {
  return (
    <Box
      sx={{
        minHeight: "100vh",
        bgcolor: "#F7F4EF",
        px: { xs: 2, sm: 3.75 },
        py: 3.75,
        color: "#2A2622",
      }}
    >
      <Box sx={{ maxWidth: 1120, mx: "auto" }}>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 700,
            letterSpacing: ".13em",
          }}
        >
          TUTOR WORKFLOW
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 39 },
            fontWeight: 500,
            mt: 0.75,
          }}
        >
          Alerts
        </Typography>
        <Typography sx={{ color: "#6F675E", mt: 0.75, mb: 3 }}>
          Evidence-backed alerts needing your decision. Resolve or dismiss an
          alert once you have acted.
        </Typography>
        <AlertList />
      </Box>
    </Box>
  );
}
