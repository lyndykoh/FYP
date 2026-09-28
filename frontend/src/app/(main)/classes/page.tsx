"use client";

import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import ClassList from "@/components/classes/ClassList";

export default function ClassesPage() {
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
          TEACHING GROUPS
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 38 },
            fontWeight: 500,
            lineHeight: 1.1,
            letterSpacing: "-.02em",
            textWrap: "pretty",
            mb: 1,
          }}
        >
          My Classes
        </Typography>
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
          <Typography
            sx={{
              color: "#6F675E",
              fontSize: 14,
              lineHeight: 1.6,
              maxWidth: "52ch",
            }}
          >
            Find a teaching group, check its schedule, and open its summary.
          </Typography>
          <Button
            component={Link}
            href="/classes/new"
            sx={{
              minHeight: 42,
              borderRadius: "10px",
              bgcolor: "#9E3A24",
              color: "#FBF9F5",
              textTransform: "none",
              fontWeight: 500,
              px: 2.25,
              boxShadow: "0 1px 2px rgba(42,38,34,.12)",
              "&:hover": { bgcolor: "#8A3120" },
            }}
          >
            Create class
          </Button>
        </Box>
        <ClassList />
      </Box>
    </Box>
  );
}
