"use client";

import * as React from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CircularProgress from "@mui/material/CircularProgress";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  addStudentToTutorClass,
  fetchEligibleClassStudents,
  type EligibleClassStudent,
} from "@/services/classes";

export interface ClassStudentSelectorProps {
  classId: number;
  loadEligibleStudents?: (classId: number) => Promise<EligibleClassStudent[]>;
  addStudent?: (classId: number, loginUserId: number) => Promise<void>;
  onStudentAdded?: (student: EligibleClassStudent) => void | Promise<void>;
}

const card = {
  borderRadius: "14px",
  bgcolor: "#FFFDFA",
  borderColor: "#EBE4D9",
  boxShadow: "none",
} as const;

function studentLabel(student: EligibleClassStudent) {
  return `${student.fullName} — ${student.email}${student.level ? ` · ${student.level}` : ""}`;
}

/**
 * Selects an existing auth-service Student account. The API is the authority
 * for eligibility, so a stale browser list cannot create a duplicate or enrol
 * a Tutor account.
 */
export default function ClassStudentSelector({
  classId,
  loadEligibleStudents = fetchEligibleClassStudents,
  addStudent = addStudentToTutorClass,
  onStudentAdded,
}: ClassStudentSelectorProps) {
  const [students, setStudents] = React.useState<EligibleClassStudent[]>([]);
  const [selectedId, setSelectedId] = React.useState("");
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [success, setSuccess] = React.useState<string | null>(null);
  const requestId = React.useRef(0);

  const load = React.useCallback(async () => {
    const currentRequest = ++requestId.current;
    // Keep the mount effect asynchronous; this also avoids an immediate render
    // cascade while the account directory request is being scheduled.
    await Promise.resolve();
    if (requestId.current !== currentRequest) return;
    setLoading(true);
    setError(null);
    setSuccess(null);
    try {
      const loaded = await loadEligibleStudents(classId);
      if (requestId.current === currentRequest) {
        setStudents(loaded);
        setSelectedId((current) =>
          loaded.some((student) => String(student.loginUserId) === current)
            ? current
            : "",
        );
      }
    } catch (reason) {
      if (requestId.current === currentRequest)
        setError(
          reason instanceof Error
            ? reason.message
            : "Existing Student accounts could not be loaded. Please try again.",
        );
    } finally {
      if (requestId.current === currentRequest) setLoading(false);
    }
  }, [classId, loadEligibleStudents]);

  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      void load();
    }, 0);
    return () => {
      window.clearTimeout(timer);
      requestId.current += 1;
    };
  }, [load]);

  const selectedStudent =
    students.find((student) => String(student.loginUserId) === selectedId) ??
    null;

  const addSelectedStudent = async () => {
    if (!selectedStudent || saving) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await addStudent(classId, selectedStudent.loginUserId);
      setStudents((current) =>
        current.filter(
          (student) => student.loginUserId !== selectedStudent.loginUserId,
        ),
      );
      setSelectedId("");
      setSuccess(`${selectedStudent.fullName} has been added to this class.`);
      await onStudentAdded?.(selectedStudent);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "The Student could not be added to this class. Please try again.",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      component="section"
      aria-labelledby="add-existing-student-heading"
      variant="outlined"
      sx={{ ...card, maxWidth: 880, mt: 2.5, p: { xs: 2.25, sm: 2.75 } }}
    >
      <Typography
        id="add-existing-student-heading"
        component="h2"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: 22,
          fontWeight: 500,
          mb: 0.6,
        }}
      >
        Add an existing Student
      </Typography>
      <Typography
        sx={{ color: "#6F675E", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
      >
        Choose a Student account/login to enrol it in this class. Student
        accounts already in this class are not shown.
      </Typography>
      {loading ? (
        <Box
          data-testid="eligible-students-loading"
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1.25,
            minHeight: 48,
          }}
        >
          <CircularProgress
            size={20}
            aria-label="Loading existing Student accounts"
          />
          <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
            Loading existing Student accounts…
          </Typography>
        </Box>
      ) : error ? (
        <Alert
          severity="error"
          role="alert"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() => void load()}
              sx={{ textTransform: "none", fontWeight: 600 }}
            >
              Retry
            </Button>
          }
          sx={{ alignItems: "center" }}
        >
          {error}
        </Alert>
      ) : students.length === 0 ? (
        <Box
          sx={{
            border: "1px dashed #DCCFBE",
            borderRadius: "12px",
            p: 2,
            textAlign: "center",
            bgcolor: "#FFFDFA",
          }}
        >
          <Typography sx={{ fontSize: 13.5, fontWeight: 600, mb: 0.5 }}>
            No eligible Students available
          </Typography>
          <Typography
            sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}
          >
            Every existing Student is already enrolled, or there are no Student
            accounts to add.
          </Typography>
        </Box>
      ) : (
        <>
          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              alignItems: "flex-end",
              gap: 1.25,
            }}
          >
            <FormControl fullWidth sx={{ flex: "1 1 360px", minWidth: 0 }}>
              <InputLabel id="existing-student-label">
                Existing Student account
              </InputLabel>
              <Select
                labelId="existing-student-label"
                id="existing-student"
                value={selectedId}
                label="Existing Student account"
                onChange={(event) => setSelectedId(event.target.value)}
              >
                {students.map((student) => (
                  <MenuItem
                    key={student.loginUserId}
                    value={String(student.loginUserId)}
                  >
                    {studentLabel(student)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              type="button"
              variant="contained"
              onClick={() => void addSelectedStudent()}
              disabled={!selectedStudent || saving}
              sx={{
                minHeight: 48,
                bgcolor: "#E08A72",
                color: "#1B1917",
                textTransform: "none",
                fontWeight: 600,
                borderRadius: "10px",
                px: 2,
                "&:hover": { bgcolor: "#D2795F" },
                "&.Mui-disabled": { bgcolor: "#E6DED4", color: "#8B837A" },
              }}
            >
              {saving ? "Adding Student…" : "Add to class"}
            </Button>
          </Box>
          {success && (
            <Alert
              severity="success"
              sx={{ mt: 1.5 }}
              action={
                <Button
                  component={Link}
                  href={`/classes/${classId}`}
                  color="inherit"
                  size="small"
                  sx={{ textTransform: "none", fontWeight: 600 }}
                >
                  View roster
                </Button>
              }
            >
              {success}
            </Alert>
          )}
        </>
      )}
    </Card>
  );
}
