"use client";

import * as React from "react";
import ArrowForwardIcon from "@mui/icons-material/ArrowForward";
import SchoolOutlinedIcon from "@mui/icons-material/SchoolOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Chip from "@mui/material/Chip";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  fetchStudentSelfProfile,
  type StudentProfileClass,
  type StudentSelfProfile,
} from "@/services/students";

const serif = "'Playfair Display', Georgia, serif";
const card = {
  borderColor: "#EBE4D9",
  bgcolor: "#FFFDFA",
  borderRadius: "14px",
  boxShadow: "none",
} as const;

function initials(fullName: string) {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function percentage(value: number | null) {
  return value === null ? "—" : `${Math.round(value)}%`;
}

function ClassRow({
  item,
  last,
}: {
  item: StudentProfileClass;
  last: boolean;
}) {
  return (
    <Button
      component={Link}
      href={`/classes/${item.id}`}
      aria-label={`Open ${item.className}`}
      endIcon={<ArrowForwardIcon aria-hidden="true" sx={{ fontSize: 18 }} />}
      sx={{
        width: "100%",
        minHeight: 70,
        justifyContent: "space-between",
        gap: 1.5,
        px: { xs: 2, sm: 2.75 },
        py: 1.4,
        color: "#2A2622",
        textAlign: "left",
        textTransform: "none",
        borderRadius: 0,
        borderBottom: last ? 0 : "1px solid #F0EAE0",
        "&:hover": { bgcolor: "#FBF7F1" },
        "&:focus-visible": { outline: "3px solid #E08A72", outlineOffset: -3 },
      }}
    >
      <Box
        aria-hidden="true"
        sx={{
          width: 36,
          height: 36,
          borderRadius: "9px",
          flex: "0 0 auto",
          bgcolor: "#EAEDE7",
          color: "#4A6B50",
          display: "grid",
          placeItems: "center",
        }}
      >
        <SchoolOutlinedIcon sx={{ fontSize: 18 }} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography
          sx={{
            fontSize: 14,
            fontWeight: 600,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {item.className}
        </Typography>
        <Typography sx={{ fontSize: 12.5, color: "#8B837A", mt: 0.25 }}>
          {item.level} · {item.subject}
        </Typography>
      </Box>
      <Chip
        label={item.status === "ACTIVE" ? "Active" : "Inactive"}
        size="small"
        sx={{
          height: 23,
          bgcolor: item.status === "ACTIVE" ? "#EAEDE7" : "#F4EFE6",
          color: item.status === "ACTIVE" ? "#4A6B50" : "#6F675E",
          fontSize: 10.5,
          fontWeight: 600,
        }}
      />
    </Button>
  );
}

function ProfileSkeleton() {
  return (
    <Box
      aria-label="Loading profile"
      data-testid="profile-skeleton"
      sx={{ display: "grid", gap: 2.5 }}
    >
      <Card variant="outlined" sx={{ ...card, p: 3 }}>
        <Skeleton
          variant="rounded"
          height={74}
          width="72%"
          sx={{ bgcolor: "#F0EAE0" }}
        />
      </Card>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
        }}
      >
        {[1, 2, 3].map((item) => (
          <Skeleton
            key={item}
            variant="rounded"
            height={118}
            sx={{ bgcolor: "#F0EAE0", borderRadius: "14px" }}
          />
        ))}
      </Box>
      <Skeleton
        variant="rounded"
        height={180}
        sx={{ bgcolor: "#F0EAE0", borderRadius: "14px" }}
      />
    </Box>
  );
}

function Metric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <Card variant="outlined" sx={{ ...card, p: 2.15 }}>
      <Typography
        sx={{
          color: "#A09488",
          fontSize: 10,
          fontWeight: 600,
          letterSpacing: ".11em",
          mb: 1,
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontFamily: serif,
          fontSize: 29,
          fontWeight: 500,
          lineHeight: 1,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        {value}
      </Typography>
      <Typography
        sx={{ color: "#8B837A", fontSize: 11.5, lineHeight: 1.45, mt: 1 }}
      >
        {detail}
      </Typography>
    </Card>
  );
}

function ProfileContent({ profile }: { profile: StudentSelfProfile }) {
  return (
    <>
      <Card
        component="section"
        aria-label="Profile summary"
        variant="outlined"
        sx={{
          ...card,
          p: { xs: 2.25, sm: 3 },
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 2,
          mb: 2.5,
        }}
      >
        <Box
          aria-hidden="true"
          sx={{
            width: 66,
            height: 66,
            borderRadius: "50%",
            flex: "0 0 auto",
            bgcolor: "#D8B384",
            display: "grid",
            placeItems: "center",
            fontFamily: serif,
            fontSize: 24,
            fontWeight: 600,
            color: "#3A332C",
          }}
        >
          {initials(profile.fullName)}
        </Box>
        <Box sx={{ flex: "1 1 240px", minWidth: 0 }}>
          <Typography
            component="h1"
            sx={{
              fontFamily: serif,
              fontSize: { xs: 32, sm: 38 },
              fontWeight: 500,
              lineHeight: 1.1,
              letterSpacing: "-.02em",
            }}
          >
            {profile.fullName}
          </Typography>
          <Typography sx={{ color: "#6F675E", fontSize: 13.5, mt: 0.7 }}>
            Your profile is built from your enrolled classes and tutor-approved
            learning evidence.
          </Typography>
        </Box>
      </Card>

      <Box
        component="section"
        aria-label="Learning snapshot"
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(168px, 1fr))",
          gap: 1.75,
          mb: 2.5,
        }}
      >
        <Metric
          label="OVERALL MASTERY"
          value={percentage(profile.metrics.averageMastery)}
          detail={
            profile.metrics.topicCount
              ? `Across ${profile.metrics.topicCount} tracked topics`
              : "No mastery records yet"
          }
        />
        <Metric
          label="LEARNING ATTEMPTS"
          value={String(profile.metrics.totalAttempts)}
          detail={
            profile.metrics.totalAttempts === 1
              ? "One approved attempt"
              : "Approved attempts"
          }
        />
        <Metric
          label="ASSIGNED WORKSHEETS"
          value={String(profile.worksheets.length)}
          detail={
            profile.worksheets.length
              ? "Available from your tutor"
              : "No worksheets assigned yet"
          }
        />
      </Box>

      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          gap: 2.5,
          alignItems: "flex-start",
        }}
      >
        <Card
          component="section"
          aria-labelledby="enrolled-classes"
          variant="outlined"
          sx={{ ...card, flex: "1 1 460px", overflow: "hidden" }}
        >
          <Typography
            id="enrolled-classes"
            component="h2"
            sx={{
              px: { xs: 2, sm: 2.75 },
              py: 1.8,
              borderBottom: "1px solid #EFE8DE",
              fontFamily: serif,
              fontSize: 22,
              fontWeight: 500,
            }}
          >
            My classes
          </Typography>
          {profile.classes.length ? (
            profile.classes.map((item, index) => (
              <ClassRow
                key={item.id}
                item={item}
                last={index === profile.classes.length - 1}
              />
            ))
          ) : (
            <Box sx={{ p: 2.5 }}>
              <Typography
                component="h3"
                sx={{
                  fontFamily: serif,
                  fontSize: 18,
                  fontWeight: 500,
                  mb: 0.55,
                }}
              >
                No classes yet
              </Typography>
              <Typography
                role="status"
                sx={{ color: "#8B837A", fontSize: 12.5, lineHeight: 1.6 }}
              >
                Your enrolled classes will appear here when your tutor adds you
                to one.
              </Typography>
            </Box>
          )}
        </Card>

        <Card
          component="section"
          aria-labelledby="profile-actions"
          variant="outlined"
          sx={{
            ...card,
            flex: "0 1 320px",
            minWidth: 0,
            p: { xs: 2, sm: 2.5 },
          }}
        >
          <Typography
            id="profile-actions"
            component="h2"
            sx={{ fontFamily: serif, fontSize: 22, fontWeight: 500, mb: 0.7 }}
          >
            Learning profile
          </Typography>
          <Typography
            sx={{ color: "#6F675E", fontSize: 13, lineHeight: 1.6, mb: 1.5 }}
          >
            See the strengths, focus areas, and tutor-approved evidence that
            guide your next steps.
          </Typography>
          <Button
            component={Link}
            href="/subject-profile"
            endIcon={<ArrowForwardIcon aria-hidden="true" />}
            variant="outlined"
            sx={{
              minHeight: 38,
              borderColor: "#E4DCD0",
              bgcolor: "#FFFDFA",
              color: "#2A2622",
              borderRadius: "10px",
              textTransform: "none",
              fontSize: 13,
              fontWeight: 500,
              px: 1.8,
              "&:hover": { bgcolor: "#F4EFE6" },
            }}
          >
            Open subject profile
          </Button>
        </Card>
      </Box>
    </>
  );
}

export default function Page() {
  const [profile, setProfile] = React.useState<StudentSelfProfile | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const load = React.useCallback(async () => {
    setError(null);
    try {
      setProfile(await fetchStudentSelfProfile());
    } catch (reason) {
      setError(
        reason instanceof Error
          ? reason.message
          : "Your profile could not be loaded.",
      );
    }
  }, []);

  React.useEffect(() => {
    let current = true;
    const request = async () => {
      try {
        const loaded = await fetchStudentSelfProfile();
        if (current) {
          setError(null);
          setProfile(loaded);
        }
      } catch (reason) {
        if (current) {
          setError(
            reason instanceof Error
              ? reason.message
              : "Your profile could not be loaded.",
          );
        }
      }
    };
    void request();
    return () => {
      current = false;
    };
  }, []);

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
        sx={{ maxWidth: 1120, mx: "auto", animation: "fadeUp .35s ease both" }}
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
          STUDENT PROFILE
        </Typography>
        {!profile && !error ? <ProfileSkeleton /> : null}
        {error ? (
          <Card
            component="section"
            role="alert"
            variant="outlined"
            sx={{ ...card, borderColor: "#F0DCD4", bgcolor: "#FDF6F3", p: 2.5 }}
          >
            <Typography
              component="h1"
              sx={{ fontFamily: serif, fontSize: 24, fontWeight: 500, mb: 0.7 }}
            >
              Your profile could not be loaded
            </Typography>
            <Typography
              sx={{ color: "#6F675E", fontSize: 13, lineHeight: 1.6, mb: 1.35 }}
            >
              {error}
            </Typography>
            <Button
              onClick={() => void load()}
              variant="outlined"
              sx={{
                minHeight: 38,
                borderColor: "#E4DCD0",
                color: "#2A2622",
                textTransform: "none",
                borderRadius: "10px",
              }}
            >
              Try again
            </Button>
          </Card>
        ) : null}
        {profile ? <ProfileContent profile={profile} /> : null}
      </Box>
    </Box>
  );
}
