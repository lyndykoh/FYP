"use client";

import * as React from "react";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";

import {
  createTutorNote,
  deleteTutorNote,
  fetchTutorNotes,
  updateTutorNote,
  type TutorNote,
} from "@/services/students";

export interface TutorNotesProps {
  studentId: number;
  loadNotes?: (studentId: number) => Promise<TutorNote[]>;
  createNote?: (
    studentId: number,
    request: { content: string },
  ) => Promise<TutorNote>;
  updateNote?: (
    studentId: number,
    noteId: number,
    request: { content: string },
  ) => Promise<TutorNote>;
  removeNote?: (studentId: number, noteId: number) => Promise<void>;
}

const serif = "'Playfair Display', Georgia, serif";
const inputSx = {
  "& .MuiOutlinedInput-root": {
    bgcolor: "#FFFDFA",
    borderRadius: "10px",
    "& fieldset": { borderColor: "#DED4C7" },
    "&:hover fieldset": { borderColor: "#C8B59D" },
  },
  "& .MuiInputBase-input": { fontSize: 13, lineHeight: 1.55 },
};

function timestamp(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Recently updated"
    : new Intl.DateTimeFormat("en", {
        month: "short",
        day: "numeric",
        year: "numeric",
      }).format(date);
}

function sortNotes(notes: TutorNote[]) {
  return [...notes].sort(
    (left, right) =>
      right.updatedAt.localeCompare(left.updatedAt) || right.id - left.id,
  );
}

export default function TutorNotes({
  studentId,
  loadNotes = fetchTutorNotes,
  createNote = createTutorNote,
  updateNote = updateTutorNote,
  removeNote = deleteTutorNote,
}: TutorNotesProps) {
  const [notes, setNotes] = React.useState<TutorNote[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [draft, setDraft] = React.useState("");
  const [editing, setEditing] = React.useState<TutorNote | null>(null);
  const [saving, setSaving] = React.useState(false);
  const [deletingId, setDeletingId] = React.useState<number | null>(null);

  const reload = React.useCallback(async () => {
    setError(null);
    try {
      setNotes(sortNotes(await loadNotes(studentId)));
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Tutor notes could not be loaded. Please try again.",
      );
    }
  }, [loadNotes, studentId]);

  React.useEffect(() => {
    queueMicrotask(() => {
      void reload();
    });
  }, [reload]);

  const save = async () => {
    const content = draft.trim();
    if (!content || saving) return;
    setSaving(true);
    setError(null);
    try {
      const saved = editing
        ? await updateNote(studentId, editing.id, { content })
        : await createNote(studentId, { content });
      setNotes((current) =>
        sortNotes([
          ...(current ?? []).filter((note) => note.id !== saved.id),
          saved,
        ]),
      );
      setDraft("");
      setEditing(null);
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Tutor note could not be saved. Please try again.",
      );
    } finally {
      setSaving(false);
    }
  };

  const beginEdit = (note: TutorNote) => {
    if (saving || deletingId !== null) return;
    setEditing(note);
    setDraft(note.content);
    setError(null);
  };

  const remove = async (note: TutorNote) => {
    if (saving || deletingId !== null) return;
    setDeletingId(note.id);
    setError(null);
    try {
      await removeNote(studentId, note.id);
      setNotes((current) =>
        (current ?? []).filter((item) => item.id !== note.id),
      );
      if (editing?.id === note.id) {
        setEditing(null);
        setDraft("");
      }
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Tutor note could not be deleted. Please try again.",
      );
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <Card
      component="section"
      aria-labelledby="tutor-notes-heading"
      variant="outlined"
      sx={{
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
        borderRadius: "14px",
        boxShadow: "none",
        p: { xs: 2, sm: 2.5 },
      }}
    >
      <Typography
        id="tutor-notes-heading"
        component="h2"
        sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500, mb: 0.35 }}
      >
        Private tutor notes
      </Typography>
      <Typography
        sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.55, mb: 1.5 }}
      >
        Only you can view and manage these notes. They are never shown to the
        student.
      </Typography>

      <Box
        component="form"
        onSubmit={(event) => {
          event.preventDefault();
          void save();
        }}
        sx={{ display: "grid", gap: 1, mb: 1.5 }}
      >
        <TextField
          label={editing ? "Edit private note" : "Add a private note"}
          multiline
          minRows={3}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          slotProps={{ htmlInput: { maxLength: 4000 } }}
          helperText={`${draft.length}/4000`}
          disabled={saving || deletingId !== null}
          sx={inputSx}
        />
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            gap: 1,
            justifyContent: "flex-end",
          }}
        >
          {editing ? (
            <Button
              type="button"
              onClick={() => {
                setEditing(null);
                setDraft("");
              }}
              disabled={saving}
              sx={{ minHeight: 38, color: "#6F675E", textTransform: "none" }}
            >
              Cancel
            </Button>
          ) : null}
          <Button
            type="submit"
            disabled={!draft.trim() || saving || deletingId !== null}
            sx={{
              minHeight: 38,
              borderRadius: "9px",
              bgcolor: "#2F5D50",
              color: "#FFFDF8",
              textTransform: "none",
              fontWeight: 600,
              px: 1.8,
              "&:hover": { bgcolor: "#24493F" },
              "&.Mui-disabled": { bgcolor: "#DFE7E1", color: "#77847B" },
            }}
          >
            {saving ? "Saving…" : editing ? "Save note" : "Add note"}
          </Button>
        </Box>
      </Box>

      {error ? (
        <Box
          role="alert"
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            gap: 1,
            p: 1.2,
            mb: 1.25,
            borderLeft: "3px solid #B4573F",
            bgcolor: "#FDF6F3",
          }}
        >
          <Typography
            sx={{ flex: "1 1 220px", color: "#7E301F", fontSize: 12.5 }}
          >
            {error}
          </Typography>
          {notes === null ? (
            <Button
              onClick={() => void reload()}
              size="small"
              sx={{ color: "#9E3A24", textTransform: "none" }}
            >
              Retry
            </Button>
          ) : null}
        </Box>
      ) : null}

      {notes === null ? (
        <Typography
          aria-label="Loading private tutor notes"
          sx={{ color: "#8B837A", fontSize: 12.5 }}
        >
          Loading notes…
        </Typography>
      ) : notes.length === 0 ? (
        <Box
          sx={{
            p: 1.6,
            border: "1px dashed #DCCFBE",
            borderRadius: "10px",
            bgcolor: "#FBF9F5",
          }}
        >
          <Typography sx={{ fontSize: 13, fontWeight: 500 }}>
            No private notes yet
          </Typography>
          <Typography sx={{ color: "#8B837A", fontSize: 12, mt: 0.35 }}>
            Record a concise observation, follow-up, or feedback point for
            yourself.
          </Typography>
        </Box>
      ) : (
        <Box
          component="ul"
          aria-label="Private tutor notes"
          sx={{ listStyle: "none", display: "grid", gap: 1, p: 0, m: 0 }}
        >
          {notes.map((note) => (
            <Box
              component="li"
              key={note.id}
              sx={{
                p: 1.35,
                border: "1px solid #EFE8DE",
                borderRadius: "10px",
                bgcolor: "#FBF9F5",
              }}
            >
              <Typography
                sx={{
                  whiteSpace: "pre-wrap",
                  overflowWrap: "anywhere",
                  fontSize: 13,
                  lineHeight: 1.6,
                }}
              >
                {note.content}
              </Typography>
              <Box
                sx={{
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 0.75,
                  mt: 1,
                }}
              >
                <Typography sx={{ color: "#A09488", fontSize: 10.5 }}>
                  Updated {timestamp(note.updatedAt)}
                </Typography>
                <Box sx={{ display: "flex", gap: 0.5 }}>
                  <Button
                    onClick={() => beginEdit(note)}
                    disabled={saving || deletingId !== null}
                    size="small"
                    startIcon={
                      <EditOutlinedIcon
                        aria-hidden="true"
                        sx={{ fontSize: 15 }}
                      />
                    }
                    sx={{
                      minHeight: 30,
                      color: "#5D6F62",
                      textTransform: "none",
                      fontSize: 11.5,
                    }}
                  >
                    Edit
                  </Button>
                  <Button
                    onClick={() => void remove(note)}
                    disabled={saving || deletingId !== null}
                    size="small"
                    startIcon={
                      <DeleteOutlineIcon
                        aria-hidden="true"
                        sx={{ fontSize: 15 }}
                      />
                    }
                    sx={{
                      minHeight: 30,
                      color: "#9E3A24",
                      textTransform: "none",
                      fontSize: 11.5,
                    }}
                  >
                    {deletingId === note.id ? "Deleting…" : "Delete"}
                  </Button>
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
      )}
    </Card>
  );
}
