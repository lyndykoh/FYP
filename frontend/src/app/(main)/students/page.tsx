"use client";

import AddIcon from "@mui/icons-material/Add";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useSearchParams } from "next/navigation";

import StudentList from "@/components/students/StudentList";

export default function StudentsPage() {
  const params = useSearchParams();
  const parsedClassId = Number(params.get("classId"));
  const classId =
    Number.isSafeInteger(parsedClassId) && parsedClassId > 0
      ? parsedClassId
      : undefined;
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
              STUDENT MANAGEMENT
            </Typography>
            <Typography
              component="h1"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: { xs: 32, sm: 38 },
                fontWeight: 500,
                lineHeight: 1.1,
                letterSpacing: "-.02em",
              }}
            >
              Students
            </Typography>
            <Typography
              sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mt: 1 }}
            >
              Manage tutor-owned student profiles and their class memberships.
            </Typography>
          </Box>
          <Button
            component={Link}
            href="/students/new"
            startIcon={<AddIcon />}
            sx={{
              minHeight: 42,
              borderRadius: "10px",
              bgcolor: "#9E3A24",
              color: "#FBF9F5",
              textTransform: "none",
              fontWeight: 500,
              px: 2.25,
              "&:hover": { bgcolor: "#8A3120" },
            }}
          >
            Create student
          </Button>
        </Box>
        <StudentList classId={classId} />
      </Box>
    </Box>
  );
}
