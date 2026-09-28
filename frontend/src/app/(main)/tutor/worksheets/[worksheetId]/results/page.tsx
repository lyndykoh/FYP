"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  fetchManualResults,
  type ManualResultsResponse,
} from "@/services/submissions";
import { fetchTutorStudents } from "@/services/students";
import {
  fetchTutorWorksheet,
  type TutorWorksheet,
} from "@/services/worksheets";

export default function Page() {
  const { worksheetId } = useParams<{ worksheetId: string }>();
  const id = Number(worksheetId);
  const validId = Number.isSafeInteger(id) && id > 0;
  const [worksheet, setWorksheet] = React.useState<TutorWorksheet | null>(null);
  const [results, setResults] = React.useState<ManualResultsResponse | null>(
    null,
  );
  const [names, setNames] = React.useState(new Map<number, string>());
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!validId) return;
    let active = true;
    Promise.all([
      fetchTutorWorksheet(id),
      fetchManualResults(id),
      fetchTutorStudents(),
    ])
      .then(([loadedWorksheet, loadedResults, students]) => {
        if (!active) return;
        setWorksheet(loadedWorksheet);
        setResults(loadedResults);
        setNames(
          new Map(students.map((student) => [student.id, student.fullName])),
        );
      })
      .catch((caught: unknown) => {
        if (active)
          setError(
            caught instanceof Error
              ? caught.message
              : "Manual results could not be opened.",
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
  if (!worksheet || !results)
    return (
      <Box sx={{ p: 3 }}>
        <Typography aria-live="polite">Loading manual results…</Typography>
      </Box>
    );
  const total = results.students.reduce(
    (sum, student) => sum + student.completedQuestions,
    0,
  );
  return (
    <Box
      sx={{
        py: { xs: 2, sm: 3 },
        px: { xs: 1, sm: 2 },
        maxWidth: 1120,
        mx: "auto",
      }}
    >
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          gap: 1,
          flexWrap: "wrap",
          mb: 2,
        }}
      >
        <Box>
          <Typography
            component="h1"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: { xs: 28, sm: 36 },
            }}
          >
            Manual results
          </Typography>
          <Typography sx={{ color: "#6F675E" }}>
            {worksheet.title} · {total} approved question result
            {total === 1 ? "" : "s"}
          </Typography>
        </Box>
        <Button
          component={Link}
          href={`/tutor/worksheets/${worksheet.id}/results/new`}
          sx={{
            bgcolor: "#9E3A24",
            color: "#FFFDFA",
            textTransform: "none",
            "&:hover": { bgcolor: "#7E2E1D" },
          }}
        >
          Enter results
        </Button>
      </Stack>
      {results.students.length === 0 ? (
        <Card variant="outlined" sx={{ p: 3, borderColor: "#EBE4D9" }}>
          <Typography>No manual results have been entered yet.</Typography>
          <Typography sx={{ color: "#6F675E", mt: 0.5 }}>
            Choose an assigned student and record their approved marks.
          </Typography>
        </Card>
      ) : (
        <Stack spacing={2}>
          {results.students.map((student) => (
            <Card
              key={student.studentId}
              variant="outlined"
              sx={{ borderColor: "#EBE4D9", overflowX: "auto" }}
            >
              <Box
                sx={{
                  p: 2,
                  borderBottom: "1px solid #EBE4D9",
                  bgcolor: "#FBF9F5",
                }}
              >
                <Typography sx={{ fontWeight: 700 }}>
                  {names.get(student.studentId) ??
                    `Student ${student.studentId}`}
                </Typography>
                <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
                  {student.completedQuestions} approved question result
                  {student.completedQuestions === 1 ? "" : "s"}
                </Typography>
              </Box>
              <Table
                size="small"
                aria-label={`Manual results for ${names.get(student.studentId) ?? `student ${student.studentId}`}`}
              >
                <TableHead>
                  <TableRow>
                    <TableCell>Question</TableCell>
                    <TableCell>Score</TableCell>
                    <TableCell>Feedback</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {student.results.map((result) => (
                    <TableRow key={result.id}>
                      <TableCell>
                        {worksheet.questions.find(
                          (question) => question.id === result.questionBankId,
                        )?.code ?? `Question ${result.questionBankId}`}
                      </TableCell>
                      <TableCell>
                        {result.approvedMarks?.toFixed(2) ?? "—"} /{" "}
                        {result.maxMarks.toFixed(2)}
                      </TableCell>
                      <TableCell sx={{ maxWidth: 360 }}>
                        {result.approvedFeedback}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          component={Link}
                          href={`/tutor/reviews/${result.id}`}
                          size="small"
                          sx={{ color: "#9E3A24", textTransform: "none" }}
                        >
                          Review or edit
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Card>
          ))}
        </Stack>
      )}
    </Box>
  );
}
