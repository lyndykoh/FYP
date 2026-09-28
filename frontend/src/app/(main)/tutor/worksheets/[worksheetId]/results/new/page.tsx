"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import ManualResultForm, {
  type ManualResultStudent,
} from "@/components/marking/ManualResultForm";
import { fetchManualResults, type MarkingReview } from "@/services/submissions";
import { fetchTutorStudents } from "@/services/students";
import {
  fetchTutorWorksheet,
  type TutorWorksheet,
} from "@/services/worksheets";

function eligibleStudents(
  worksheet: TutorWorksheet,
  students: Awaited<ReturnType<typeof fetchTutorStudents>>,
): ManualResultStudent[] {
  const directIds = new Set(
    worksheet.assignments
      .filter((assignment) => assignment.assignmentType === "STUDENT")
      .map((assignment) => assignment.studentProfileId)
      .filter((id): id is number => id !== null),
  );
  const classIds = new Set(
    worksheet.assignments
      .filter((assignment) => assignment.assignmentType === "CLASS")
      .map((assignment) => assignment.classId)
      .filter((id): id is number => id !== null),
  );
  return students
    .filter(
      (student) =>
        directIds.has(student.id) ||
        student.classes.some((classroom) => classIds.has(classroom.id)),
    )
    .map((student) => ({ id: student.id, fullName: student.fullName }));
}

export default function Page() {
  const { worksheetId } = useParams<{ worksheetId: string }>();
  const router = useRouter();
  const id = Number(worksheetId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const [worksheet, setWorksheet] = React.useState<TutorWorksheet | null>(null);
  const [students, setStudents] = React.useState<ManualResultStudent[]>([]);
  const [existingResults, setExistingResults] = React.useState<MarkingReview[]>(
    [],
  );
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!validId) return;
    let active = true;
    Promise.all([
      fetchTutorWorksheet(id),
      fetchTutorStudents(),
      fetchManualResults(id),
    ])
      .then(([loadedWorksheet, loadedStudents, manualResults]) => {
        if (!active) return;
        setWorksheet(loadedWorksheet);
        setStudents(eligibleStudents(loadedWorksheet, loadedStudents));
        setExistingResults(
          manualResults.students.flatMap((student) => student.results),
        );
      })
      .catch((caught: unknown) => {
        if (active)
          setError(
            caught instanceof Error
              ? caught.message
              : "Manual result entry could not be opened.",
          );
      });
    return () => {
      active = false;
    };
  }, [id, validId]);

  if (!validId || error)
    return (
      <Box sx={{ p: 3 }}>
        <Typography role="alert">
          {error ?? "Worksheet could not be opened."}
        </Typography>
      </Box>
    );
  if (!worksheet)
    return (
      <Box sx={{ p: 3 }}>
        <Typography>Loading manual result entry…</Typography>
      </Box>
    );
  return (
    <Box sx={{ py: { xs: 2, sm: 3 }, px: { xs: 1, sm: 2 } }}>
      <Button
        component={Link}
        href={`/tutor/worksheets/${worksheet.id}`}
        sx={{ textTransform: "none", color: "#6F675E", mb: 1 }}
      >
        Back to worksheet
      </Button>
      <ManualResultForm
        worksheet={worksheet}
        students={students}
        existingResults={existingResults}
        onCreated={() =>
          router.push(`/tutor/worksheets/${worksheet.id}/results`)
        }
      />
    </Box>
  );
}
