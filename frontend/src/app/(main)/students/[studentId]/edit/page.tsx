"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import StudentForm from "@/components/students/StudentForm";
import {
  fetchTutorStudent,
  type TutorStudent,
  updateTutorStudent,
} from "@/services/students";

export default function EditStudentPage() {
  const { studentId } = useParams<{ studentId: string }>();
  const router = useRouter();
  const id = Number(studentId);
  const valid = Number.isSafeInteger(id) && id > 0;
  const [student, setStudent] = React.useState<TutorStudent | null>(null);
  const [error, setError] = React.useState<string | null>(
    valid ? null : "This student reference is invalid.",
  );
  React.useEffect(() => {
    if (!valid) return;
    void fetchTutorStudent(id)
      .then(setStudent)
      .catch((reason) =>
        setError(
          reason instanceof Error
            ? reason.message
            : "Student could not be loaded.",
        ),
      );
  }, [id, valid]);
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
            mb: 3,
          }}
        >
          Edit student
        </Typography>
        {error ? (
          <Card
            role="alert"
            variant="outlined"
            sx={{ p: 3, maxWidth: 620, borderLeft: "3px solid #B4573F" }}
          >
            <Typography sx={{ mb: 2 }}>{error}</Typography>
            <Button component={Link} href="/students">
              Back to students
            </Button>
          </Card>
        ) : !student ? (
          <Typography role="status">Loading student…</Typography>
        ) : (
          <StudentForm
            mode="edit"
            initialStudent={student}
            submitStudent={(request) => updateTutorStudent(student.id, request)}
            onComplete={() => router.push("/students")}
          />
        )}
      </Box>
    </Box>
  );
}
