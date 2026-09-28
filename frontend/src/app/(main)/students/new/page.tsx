"use client";

import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useRouter } from "next/navigation";
import StudentForm from "@/components/students/StudentForm";
import { createTutorStudent } from "@/services/students";

export default function NewStudentPage() {
  const router = useRouter();
  const returnToStudents = () => router.push("/students");

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
          STUDENT MANAGEMENT
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 38 },
            fontWeight: 500,
            mb: 1,
          }}
        >
          Create a student
        </Typography>
        <Typography sx={{ color: "#6F675E", fontSize: 14, mb: 3 }}>
          Add a tutor-owned student profile and select their classes.
        </Typography>
        <StudentForm
          mode="create"
          submitStudent={createTutorStudent}
          onComplete={returnToStudents}
        />
      </Box>
    </Box>
  );
}
