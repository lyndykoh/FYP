"use client";

import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useRouter } from "next/navigation";

import ClassForm from "@/components/classes/ClassForm";
import { createTutorClass } from "@/services/classes";

export default function NewClassPage() {
  const router = useRouter();
  const returnToClasses = () => router.push("/classes");

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
          Create a class
        </Typography>
        <Typography
          sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mb: 3 }}
        >
          Add the teaching group details you want to manage.
        </Typography>
        <ClassForm
          mode="create"
          submitClass={createTutorClass}
          onComplete={returnToClasses}
        />
      </Box>
    </Box>
  );
}
