"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import LinearProgress from "@mui/material/LinearProgress";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import type {
  MasteryMapData,
  MasteryNode,
  MasteryStatus,
} from "@/services/mastery";

const statusLabel: Record<MasteryStatus, string> = {
  NOT_STARTED: "Not started",
  LEARNING: "Learning",
  PRACTISING: "Practising",
  IMPROVING: "Improving",
  MASTERED: "Mastered",
  NEEDS_REVISION: "Needs revision",
};

function masteryColour(score: number): string {
  return score < 55 ? "#B4573F" : score < 72 ? "#D8B384" : "#93A896";
}

function statusStyle(status: MasteryStatus) {
  if (status === "MASTERED" || status === "IMPROVING")
    return { bgcolor: "#E4EDE4", color: "#4A6B50" };
  if (status === "NEEDS_REVISION" || status === "LEARNING")
    return { bgcolor: "#F7E3DC", color: "#9E3A24" };
  return { bgcolor: "#F0EAE0", color: "#6F675E" };
}

interface MasteryBranch {
  node: MasteryNode;
  children: MasteryBranch[];
}

function masteryForest(nodes: MasteryNode[]): MasteryBranch[] {
  const branches = new Map(
    nodes.map((node) => [
      node.topicId,
      { node, children: [] as MasteryBranch[] },
    ]),
  );
  const roots: MasteryBranch[] = [];
  for (const node of nodes) {
    const branch = branches.get(node.topicId)!;
    const parent =
      node.parentTopicId === null
        ? undefined
        : branches.get(node.parentTopicId);
    if (parent) parent.children.push(branch);
    else roots.push(branch);
  }
  return roots;
}

function isLearningNode(node: MasteryNode): boolean {
  return node.nodeType === "TOPIC" || node.nodeType === "SUBTOPIC";
}

function topicDetailHref(topicId: number, studentId?: number): string {
  return studentId
    ? `/topics/${topicId}?studentId=${studentId}`
    : `/topics/${topicId}`;
}

export interface MasteryMapFilters {
  status?: MasteryStatus;
  subjectId?: number;
  query?: string;
}

function subjectIdFor(
  node: MasteryNode,
  nodesById: Map<number, MasteryNode>,
): number | undefined {
  let current: MasteryNode | undefined = node;
  const visited = new Set<number>();
  while (current && !visited.has(current.topicId)) {
    visited.add(current.topicId);
    if (current.nodeType === "SUBJECT") return current.topicId;
    current =
      current.parentTopicId === null
        ? undefined
        : nodesById.get(current.parentTopicId);
  }
  return undefined;
}

/** Keeps matching learnable topics and their canonical ancestors visible. */
export function filterMasteryNodes(
  nodes: MasteryNode[],
  filters: MasteryMapFilters,
): MasteryNode[] {
  const nodesById = new Map(nodes.map((node) => [node.topicId, node]));
  const query = filters.query?.trim().toLocaleLowerCase();
  const included = new Set<number>();
  for (const node of nodes) {
    if (!isLearningNode(node)) continue;
    if (filters.status && node.status !== filters.status) continue;
    if (
      filters.subjectId &&
      subjectIdFor(node, nodesById) !== filters.subjectId
    )
      continue;
    if (
      query &&
      !`${node.topicCode} ${node.topicName}`.toLocaleLowerCase().includes(query)
    )
      continue;
    let current: MasteryNode | undefined = node;
    const visited = new Set<number>();
    while (current && !visited.has(current.topicId)) {
      visited.add(current.topicId);
      included.add(current.topicId);
      current =
        current.parentTopicId === null
          ? undefined
          : nodesById.get(current.parentTopicId);
    }
  }
  return nodes.filter((node) => included.has(node.topicId));
}

export interface MasteryMapProps {
  data: MasteryMapData;
  /** A tutor link carries the viewed student ID; a student link remains self-scoped. */
  studentId?: number;
  heading?: string;
  /** Topic browsing can filter the locally returned canonical hierarchy. */
  showFilters?: boolean;
}

export default function MasteryMap({
  data,
  studentId,
  heading = "Topic mastery",
  showFilters = false,
}: MasteryMapProps) {
  const [status, setStatus] = React.useState<MasteryStatus | undefined>();
  const [subjectId, setSubjectId] = React.useState<number | undefined>();
  const [query, setQuery] = React.useState("");
  const filters = { status, subjectId, query };
  const filteredNodes = showFilters
    ? filterMasteryNodes(data.nodes, filters)
    : data.nodes;
  const forest = masteryForest(filteredNodes);
  const overall =
    data.overallScore === null
      ? "Not calculated"
      : `${Math.round(data.overallScore)}%`;
  const subjects = data.nodes.filter((node) => node.nodeType === "SUBJECT");
  const hasTopics = data.nodes.some(isLearningNode);

  return (
    <Card
      component="section"
      aria-labelledby="mastery-map-heading"
      variant="outlined"
      sx={{
        borderColor: "#EBE4D9",
        bgcolor: "#FFFDFA",
        borderRadius: "14px",
        p: { xs: 2, sm: 2.5 },
        boxShadow: "none",
      }}
    >
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "baseline",
          justifyContent: "space-between",
          gap: 1,
          mb: 1.75,
        }}
      >
        <Box>
          <Typography
            id="mastery-map-heading"
            component="h2"
            sx={{
              fontFamily: "'Playfair Display', Georgia, serif",
              fontSize: 22,
              fontWeight: 500,
            }}
          >
            {heading}
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 12.5, mt: 0.4 }}>
            Approved learning evidence is shown for every active syllabus topic.
          </Typography>
        </Box>
        <Typography
          aria-label={`Overall mastery: ${overall}`}
          sx={{
            color: "#4A443D",
            fontSize: 13,
            fontWeight: 700,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          Overall {overall}
        </Typography>
      </Box>
      {showFilters ? (
        <Box
          component="section"
          aria-label="Topic filters"
          sx={{
            display: "grid",
            gridTemplateColumns: { xs: "1fr", sm: "repeat(3, minmax(0, 1fr))" },
            gap: 1,
            mb: 1.75,
          }}
        >
          <FilterSelect
            label="Subject"
            value={subjectId?.toString() ?? ""}
            onChange={(value) =>
              setSubjectId(value ? Number(value) : undefined)
            }
          >
            <option value="">All subjects</option>
            {subjects.map((subject) => (
              <option value={subject.topicId} key={subject.topicId}>
                {subject.topicName}
              </option>
            ))}
          </FilterSelect>
          <FilterSelect
            label="Status"
            value={status ?? ""}
            onChange={(value) =>
              setStatus(value ? (value as MasteryStatus) : undefined)
            }
          >
            <option value="">All statuses</option>
            {(Object.entries(statusLabel) as [MasteryStatus, string][]).map(
              ([value, label]) => (
                <option value={value} key={value}>
                  {label}
                </option>
              ),
            )}
          </FilterSelect>
          <Box
            component="label"
            sx={{
              display: "grid",
              gap: 0.45,
              color: "#6F675E",
              fontSize: 11.5,
              fontWeight: 700,
            }}
          >
            Topic contains
            <Box
              component="input"
              type="search"
              aria-label="Topic contains"
              value={query}
              onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                setQuery(event.target.value)
              }
              placeholder="Search topics"
              sx={{
                width: "100%",
                minWidth: 0,
                boxSizing: "border-box",
                border: "1px solid #DCCFBE",
                borderRadius: "7px",
                bgcolor: "#FFFDFA",
                color: "#2A2622",
                px: 1,
                py: 0.8,
                font: "inherit",
              }}
            />
          </Box>
        </Box>
      ) : null}
      {!forest.length ? (
        <Box
          role="status"
          sx={{
            p: 2,
            border: "1px dashed #DCCFBE",
            borderRadius: "10px",
            bgcolor: "#FBF9F5",
          }}
        >
          <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
            {hasTopics
              ? "No syllabus topics match these filters."
              : "No active syllabus topics are available yet."}
          </Typography>
        </Box>
      ) : (
        <Box sx={{ display: "grid", gap: 1.5 }}>
          {forest.map((branch) => (
            <MasteryBranchView
              branch={branch}
              key={branch.node.topicId}
              studentId={studentId}
            />
          ))}
        </Box>
      )}
    </Card>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  children,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  children: React.ReactNode;
}) {
  return (
    <Box
      component="label"
      sx={{
        display: "grid",
        gap: 0.45,
        color: "#6F675E",
        fontSize: 11.5,
        fontWeight: 700,
      }}
    >
      {label}
      <Box
        component="select"
        aria-label={label}
        value={value}
        onChange={(event: React.ChangeEvent<HTMLSelectElement>) =>
          onChange(event.target.value)
        }
        sx={{
          width: "100%",
          minWidth: 0,
          border: "1px solid #DCCFBE",
          borderRadius: "7px",
          bgcolor: "#FFFDFA",
          color: "#2A2622",
          px: 0.8,
          py: 0.8,
          font: "inherit",
        }}
      >
        {children}
      </Box>
    </Box>
  );
}

function MasteryBranchView({
  branch,
  studentId,
}: {
  branch: MasteryBranch;
  studentId?: number;
}) {
  const { node, children } = branch;
  if (!isLearningNode(node)) {
    return (
      <Box
        component="section"
        aria-label={`${node.nodeType.toLowerCase()} ${node.topicName}`}
        sx={{ display: "grid", gap: 1, pl: Math.min(node.depth, 2) * 1.25 }}
      >
        <Typography
          component={node.depth === 0 ? "h3" : "h4"}
          sx={{
            color: node.depth === 0 ? "#2A2622" : "#5A544C",
            fontSize: node.depth === 0 ? 16 : 13,
            fontWeight: node.depth === 0 ? 700 : 600,
            letterSpacing: node.depth === 0 ? ".02em" : 0,
          }}
        >
          {node.topicName}
        </Typography>
        <Box sx={{ display: "grid", gap: 1 }}>
          {children.map((child) => (
            <MasteryBranchView
              branch={child}
              key={child.node.topicId}
              studentId={studentId}
            />
          ))}
        </Box>
      </Box>
    );
  }
  const focus = node.score < 55 && node.status !== "NOT_STARTED";
  const href = topicDetailHref(node.topicId, studentId);
  const attempts =
    node.attemptCount === 1
      ? "1 approved attempt"
      : `${node.attemptCount} approved attempts`;
  return (
    <Box
      sx={{ display: "grid", gap: 1, pl: Math.max(0, node.depth - 2) * 1.25 }}
    >
      <Card
        component={Link}
        href={href}
        variant="outlined"
        aria-label={`${node.topicName}: ${statusLabel[node.status]}, ${Math.round(node.score)}% mastery. Open topic details.`}
        sx={{
          display: "flex",
          alignItems: "center",
          gap: { xs: 1.25, sm: 2 },
          p: { xs: 1.35, sm: "13px 16px" },
          textDecoration: "none",
          color: "inherit",
          borderColor: focus ? "#F0DCD4" : "#EFE8DE",
          bgcolor: focus ? "#FDF6F3" : "#FFFDFA",
          borderRadius: "10px",
          "&:focus-visible": { outline: "3px solid #9E3A24", outlineOffset: 2 },
          "&:hover": { bgcolor: focus ? "#FBEDE8" : "#FBF7F1" },
        }}
      >
        <Typography
          sx={{
            width: 44,
            flex: "0 0 auto",
            color: focus ? "#9E3A24" : "#4A443D",
            fontSize: 14,
            fontWeight: 600,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {Math.round(node.score)}%
        </Typography>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Box
            sx={{
              display: "flex",
              flexWrap: "wrap",
              alignItems: "center",
              gap: 0.75,
              mb: 0.65,
            }}
          >
            <Typography sx={{ minWidth: 0, fontSize: 13, fontWeight: 600 }}>
              {node.topicName}
            </Typography>
            {focus ? (
              <Chip
                label="FOCUS AREA"
                size="small"
                sx={{
                  height: 21,
                  bgcolor: "#F1D9D1",
                  color: "#9E3A24",
                  fontSize: 9.5,
                  fontWeight: 700,
                  letterSpacing: ".05em",
                }}
              />
            ) : null}
          </Box>
          <LinearProgress
            aria-label={`${node.topicName} mastery ${Math.round(node.score)}%`}
            variant="determinate"
            value={node.score}
            sx={{
              height: 5,
              borderRadius: 20,
              bgcolor: "#F0EAE0",
              ".MuiLinearProgress-bar": {
                bgcolor: masteryColour(node.score),
                borderRadius: 20,
              },
            }}
          />
        </Box>
        <Box
          sx={{
            display: "grid",
            justifyItems: "end",
            gap: 0.45,
            flex: "0 0 auto",
          }}
        >
          <Chip
            label={statusLabel[node.status]}
            size="small"
            sx={{
              height: 22,
              fontSize: 9.5,
              fontWeight: 700,
              letterSpacing: ".04em",
              ...statusStyle(node.status),
            }}
          />
          <Typography sx={{ color: "#8B837A", fontSize: 10.5 }}>
            {attempts}
          </Typography>
        </Box>
      </Card>
      {children.map((child) => (
        <MasteryBranchView
          branch={child}
          key={child.node.topicId}
          studentId={studentId}
        />
      ))}
    </Box>
  );
}
