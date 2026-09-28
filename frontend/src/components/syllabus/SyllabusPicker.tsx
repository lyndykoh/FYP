"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";

import {
  fetchSyllabusTree,
  type SyllabusNode,
  type SyllabusTree,
} from "@/services/syllabus";

const NODE_TYPES = ["SUBJECT", "LEVEL", "THEME", "TOPIC", "SUBTOPIC"] as const;
const labels = [
  "Subject",
  "Level",
  "Theme",
  "Topic",
  "Subtopic (optional)",
] as const;
const EMPTY_SELECTION = [null, null, null, null, null] as Array<number | null>;

export interface SyllabusPickerProps {
  value: number | null | undefined;
  onChange: (topicId: number | null) => void;
  label?: string;
  required?: boolean;
  error?: string;
  helperText?: string;
  loadSyllabus?: () => Promise<SyllabusTree>;
}

function findPath(
  nodes: SyllabusNode[],
  targetId: number,
  path: SyllabusNode[] = [],
): SyllabusNode[] | null {
  for (const node of nodes) {
    const nextPath = [...path, node];
    if (node.id === targetId) return nextPath;
    const found = findPath(node.children, targetId, nextPath);
    if (found) return found;
  }
  return null;
}

function optionsAtDepth(
  tree: SyllabusTree,
  selected: Array<number | null>,
  depth: number,
): SyllabusNode[] {
  if (depth === 0) return tree.items;
  const parentId = selected[depth - 1];
  if (parentId === null) return [];
  const parentPath = findPath(tree.items, parentId);
  return parentPath?.at(-1)?.children ?? [];
}

function selectionFor(
  tree: SyllabusTree | null,
  value: number | null | undefined,
): Array<number | null> {
  if (!tree || !value) return [...EMPTY_SELECTION];
  const path = findPath(tree.items, value);
  if (!path || !isSelectableNode(path.at(-1))) return [...EMPTY_SELECTION];
  return NODE_TYPES.map((_, index) => path[index]?.id ?? null);
}

function isSelectableNode(
  node: SyllabusNode | null | undefined,
): node is SyllabusNode {
  return node?.nodeType === "TOPIC" || node?.nodeType === "SUBTOPIC";
}

const fieldSx = {
  ".MuiOutlinedInput-root": {
    bgcolor: "#FFFDFA",
    borderRadius: "9px",
    fontSize: 13,
    "& fieldset": { borderColor: "#E4DCD0" },
    "&.Mui-focused fieldset": { borderColor: "#E08A72" },
  },
  ".MuiInputLabel-root": { color: "#6F675E", fontSize: 12.5 },
  ".MuiFormHelperText-root": {
    color: "#B4573F",
    fontSize: 11.5,
    lineHeight: 1.4,
    mx: 0,
    mt: 0.7,
  },
} as const;

/** Cascading, read-only selection of a question-compatible topic or subtopic. */
export default function SyllabusPicker({
  value,
  onChange,
  label = "Syllabus",
  required = false,
  error,
  helperText = "Choose an existing syllabus topic or subtopic.",
  loadSyllabus = fetchSyllabusTree,
}: SyllabusPickerProps) {
  const [tree, setTree] = React.useState<SyllabusTree | null>(null);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [selection, setSelection] =
    React.useState<Array<number | null>>(EMPTY_SELECTION);

  const load = React.useCallback(async () => {
    setLoadError(null);
    try {
      setTree(await loadSyllabus());
    } catch (reason) {
      setLoadError(
        reason instanceof Error
          ? reason.message
          : "The syllabus could not be loaded. Please try again.",
      );
    }
  }, [loadSyllabus]);

  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the async load owns its loading state.
    void load();
  }, [load]);
  React.useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- selection is derived from the loaded tree and controlled value.
    setSelection(selectionFor(tree, value));
  }, [tree, value]);

  const change = (depth: number, rawValue: string) => {
    const id = rawValue ? Number(rawValue) : null;
    const next = selection.map((existing, index) =>
      index < depth ? existing : null,
    );
    next[depth] = Number.isSafeInteger(id) && id! > 0 ? id : null;
    setSelection(next);

    const selectedNode =
      next[depth] === null
        ? null
        : optionsAtDepth(tree!, next, depth).find(
            (node) => node.id === next[depth],
          );
    onChange(isSelectableNode(selectedNode) ? selectedNode.id : null);
  };

  if (loadError)
    return (
      <Box
        role="alert"
        sx={{
          borderLeft: "3px solid #B4573F",
          borderRadius: "0 10px 10px 0",
          bgcolor: "#F6EFE6",
          p: 1.5,
        }}
      >
        <Typography sx={{ color: "#5A544C", fontSize: 12.5, lineHeight: 1.55 }}>
          {loadError}
        </Typography>
        <Button
          onClick={() => void load()}
          sx={{
            minHeight: 34,
            px: 0,
            mt: 0.5,
            color: "#9E3A24",
            textTransform: "none",
            fontWeight: 600,
          }}
        >
          Retry syllabus
        </Button>
      </Box>
    );
  if (!tree)
    return (
      <Typography
        role="status"
        aria-live="polite"
        sx={{ color: "#8B837A", fontSize: 12.5 }}
      >
        Loading syllabus options…
      </Typography>
    );

  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
      <Typography
        component="legend"
        sx={{ color: "#6F675E", fontSize: 11.5, fontWeight: 600, mb: 0.8 }}
      >
        {label}
        {required ? " *" : ""}
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            sm: "repeat(2, minmax(0, 1fr))",
            lg: "repeat(5, minmax(0, 1fr))",
          },
          gap: 1,
        }}
      >
        {NODE_TYPES.map((nodeType, depth) => {
          const options = optionsAtDepth(tree, selection, depth);
          const disabled = depth > 0 && selection[depth - 1] === null;
          const hasSubtopics = depth === 4 && options.length > 0;
          if (depth === 4 && !hasSubtopics) return null;
          return (
            <TextField
              key={nodeType}
              select
              fullWidth
              label={labels[depth]}
              value={selection[depth] ?? ""}
              disabled={disabled}
              onChange={(event) => change(depth, event.target.value)}
              error={depth >= 3 && Boolean(error)}
              helperText={depth === 4 ? undefined : undefined}
              slotProps={{ select: { "aria-label": labels[depth] } }}
              sx={fieldSx}
            >
              <MenuItem value="">
                {depth === 4
                  ? "Keep selected topic"
                  : `Choose ${labels[depth].toLowerCase()}`}
              </MenuItem>
              {options.map((node) => (
                <MenuItem key={node.id} value={node.id}>
                  {node.name}
                </MenuItem>
              ))}
            </TextField>
          );
        })}
      </Box>
      {(error || helperText) && (
        <Typography
          role={error ? "alert" : undefined}
          sx={{
            color: error ? "#B4573F" : "#8B837A",
            fontSize: 11.5,
            lineHeight: 1.45,
            mt: 0.7,
          }}
        >
          {error || helperText}
        </Typography>
      )}
    </Box>
  );
}
