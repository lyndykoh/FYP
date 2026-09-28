"use client";

import * as React from "react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import ClassForm from "@/components/classes/ClassForm";
import ClassStudentSelector from "@/components/classes/ClassStudentSelector";
import {
  fetchTutorClasses,
  type TutorClass,
  updateTutorClass,
} from "@/services/classes";

function MissingClass({ message }: { message: string }) {
  return (
    <Card
      component="section"
      role="alert"
      variant="outlined"
      sx={{
        maxWidth: 620,
        p: { xs: 2.5, sm: 3 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        borderLeft: "3px solid #B4573F",
        boxShadow: "none",
      }}
    >
      <Typography
        component="h2"
        sx={{
          fontFamily: "'Playfair Display', Georgia, serif",
          fontSize: 21,
          fontWeight: 500,
          mb: 0.75,
        }}
      >
        Class cannot be edited
      </Typography>
      <Typography
        sx={{ color: "#5A544C", fontSize: 13.5, lineHeight: 1.6, mb: 2 }}
      >
        {message}
      </Typography>
      <Button
        component={Link}
        href="/classes"
        variant="outlined"
        sx={{
          minHeight: 40,
          borderColor: "#E4DCD0",
          bgcolor: "#FFFDFA",
          color: "#2A2622",
          textTransform: "none",
          fontWeight: 500,
          borderRadius: "10px",
          px: 2,
          "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
        }}
      >
        Back to classes
      </Button>
    </Card>
  );
}

export default function EditClassPage() {
  const params = useParams<{ classId: string }>();
  const router = useRouter();
  const classId = Number(params.classId);
  const validClassId = Number.isSafeInteger(classId) && classId > 0;
  const [selectedClass, setSelectedClass] = React.useState<TutorClass | null>(
    null,
  );
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let current = true;
    if (!validClassId)
      return () => {
        current = false;
      };

    const loadOwnedClass = async () => {
      try {
        const ownedClasses = await fetchTutorClasses();
        const foundClass = ownedClasses.find((item) => item.id === classId);
        if (current) {
          if (foundClass) setSelectedClass(foundClass);
          else
            setError(
              "This class is not available in your account. It may have been removed or belongs to another tutor.",
            );
        }
      } catch (reason) {
        if (current)
          setError(
            reason instanceof Error
              ? reason.message
              : "Your classes could not be loaded. Please try again.",
          );
      }
    };

    void loadOwnedClass();
    return () => {
      current = false;
    };
  }, [classId, validClassId]);

  const loading = validClassId && !selectedClass && !error;
  const displayError = !validClassId
    ? "This class reference is invalid. Return to your classes and choose a class to edit."
    : error;

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
          component={Link}
          href="/classes"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 0.9,
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            textDecoration: "none",
            mb: 2.5,
            "&:hover": { color: "#B4573F" },
            "&:focus-visible": {
              outline: "3px solid #E08A72",
              outlineOffset: 3,
              borderRadius: 1,
            },
          }}
        >
          <ArrowBackIcon aria-hidden="true" sx={{ fontSize: 14 }} />
          ALL CLASSES
        </Box>
        <Typography
          sx={{
            color: "#A09488",
            fontSize: 10.5,
            fontWeight: 600,
            letterSpacing: ".13em",
            mb: 0.75,
          }}
        >
          TEACHING GROUPS
        </Typography>
        <Typography
          component="h1"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: { xs: 32, sm: 38 },
            fontWeight: 500,
            lineHeight: 1.1,
            letterSpacing: "-.02em",
            textWrap: "pretty",
            mb: 1,
          }}
        >
          Edit class
        </Typography>
        <Typography
          sx={{ color: "#6F675E", fontSize: 14, lineHeight: 1.6, mb: 3 }}
        >
          Update the details and regular teaching times, or add an existing
          Student account to this class.
        </Typography>
        {loading ? (
          <Card
            data-testid="edit-class-skeleton"
            variant="outlined"
            sx={{
              maxWidth: 880,
              p: 3,
              borderRadius: "14px",
              bgcolor: "#FFFDFA",
              borderColor: "#EBE4D9",
              boxShadow: "none",
            }}
          >
            <Skeleton variant="text" height={48} sx={{ bgcolor: "#F0EAE0" }} />
            <Skeleton
              variant="rounded"
              height={48}
              sx={{ bgcolor: "#F0EAE0", mt: 2 }}
            />
            <Skeleton
              variant="rounded"
              height={48}
              sx={{ bgcolor: "#F0EAE0", mt: 2 }}
            />
          </Card>
        ) : displayError || !selectedClass ? (
          <MissingClass
            message={
              displayError ?? "This class is not available in your account."
            }
          />
        ) : (
          <>
            <ClassForm
              mode="edit"
              initialClass={selectedClass}
              submitClass={(request) =>
                updateTutorClass(selectedClass.id, request)
              }
              onComplete={() => router.push(`/classes/${selectedClass.id}`)}
            />
            <ClassStudentSelector
              classId={selectedClass.id}
              onStudentAdded={() => router.push(`/classes/${selectedClass.id}`)}
            />
          </>
        )}
      </Box>
    </Box>
  );
}
