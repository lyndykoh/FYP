"use client";

import * as React from "react";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Skeleton from "@mui/material/Skeleton";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import { fetchTutorClasses, type TutorClass } from "@/services/classes";
import {
  StudentApiError,
  fetchAvailableStudentAccounts,
  type AvailableStudentAccount,
  type StudentMutationRequest,
  type TutorStudent,
} from "@/services/students";

type FormValues = { fullName: string; loginUserId: string; classIds: number[] };
type FieldErrors = Record<string, string>;

export interface StudentFormProps {
  mode: "create" | "edit";
  initialStudent?: TutorStudent;
  submitStudent: (request: StudentMutationRequest) => Promise<TutorStudent>;
  onComplete: (student: TutorStudent) => void;
  loadClasses?: () => Promise<TutorClass[]>;
  loadStudentAccounts?: (search: string) => Promise<AvailableStudentAccount[]>;
  cancelHref?: string;
}

const fieldSx = {
  ".MuiOutlinedInput-root": {
    bgcolor: "#FBF9F5",
    borderRadius: "9px",
    fontSize: 13.5,
    "& fieldset": { borderColor: "#E4DCD0" },
    "&.Mui-focused fieldset": { borderColor: "#E08A72" },
  },
  ".MuiInputLabel-root": { color: "#6F675E", fontSize: 13 },
  ".MuiFormHelperText-root": {
    color: "#B4573F",
    fontSize: 11.5,
    lineHeight: 1.4,
    mx: 0,
    mt: 0.7,
  },
} as const;

function valuesFor(student?: TutorStudent): FormValues {
  return {
    fullName: student?.fullName ?? "",
    loginUserId: student?.loginUserId?.toString() ?? "",
    classIds: student?.classes.map((item) => item.id) ?? [],
  };
}

export function validateStudentForm(
  values: FormValues,
  mode: StudentFormProps["mode"],
  selectedAccount: AvailableStudentAccount | null,
): FieldErrors {
  const errors: FieldErrors = {};
  const name = values.fullName.trim();
  if (mode === "create") {
    if (!selectedAccount)
      errors.studentAccount = "Select an existing Student account.";
  } else if (!name) {
    errors.fullName = "Student name is required.";
  } else if (name.length > 120) {
    errors.fullName = "Student name must be 120 characters or fewer.";
  }
  if (new Set(values.classIds).size !== values.classIds.length) {
    errors.classIds = "Each class can be selected only once.";
  }
  return errors;
}

function requestFor(
  values: FormValues,
  mode: StudentFormProps["mode"],
  selectedAccount: AvailableStudentAccount | null,
): StudentMutationRequest {
  const loginUserId = values.loginUserId.trim();
  return {
    fullName:
      mode === "create" ? selectedAccount!.fullName : values.fullName.trim(),
    loginUserId:
      mode === "create"
        ? selectedAccount!.id
        : loginUserId
          ? Number(loginUserId)
          : null,
    classIds: [...new Set(values.classIds)],
  };
}

function MembershipPicker({
  classes,
  selectedIds,
  onToggle,
}: {
  classes: TutorClass[];
  selectedIds: number[];
  onToggle: (classId: number) => void;
}) {
  if (classes.length === 0) {
    return (
      <Box
        sx={{
          p: 2,
          border: "1px dashed #DCCFBE",
          borderRadius: "12px",
          bgcolor: "#FBF9F5",
        }}
      >
        <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
          No classes are available yet. You can assign this student after
          creating a class.
        </Typography>
      </Box>
    );
  }
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(215px, 1fr))",
        gap: 1.25,
      }}
    >
      {classes.map((item) => {
        const selected = selectedIds.includes(item.id);
        return (
          <Button
            key={item.id}
            type="button"
            aria-pressed={selected}
            onClick={() => onToggle(item.id)}
            sx={{
              minHeight: 84,
              display: "block",
              textAlign: "left",
              borderRadius: "12px",
              p: 1.5,
              textTransform: "none",
              border: selected ? "1.5px solid #9E3A24" : "1.5px solid #EBE4D9",
              bgcolor: selected ? "#FDF6F3" : "#FFFDFA",
              color: "#2A2622",
              "&:hover": {
                bgcolor: selected ? "#FDF6F3" : "#F4EFE6",
                borderColor: selected ? "#9E3A24" : "#DCCFBE",
              },
              "&:focus-visible": {
                outline: "3px solid #E08A72",
                outlineOffset: 2,
              },
            }}
          >
            <Box
              component="span"
              sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.45 }}
            >
              <Box
                component="span"
                aria-hidden="true"
                sx={{
                  width: 14,
                  height: 14,
                  border: selected
                    ? "4px solid #9E3A24"
                    : "1.5px solid #A09488",
                  borderRadius: "50%",
                  boxSizing: "border-box",
                  flex: "0 0 auto",
                }}
              />
              <Typography
                component="span"
                sx={{
                  fontSize: 13.5,
                  fontWeight: 600,
                  minWidth: 0,
                  overflow: "hidden",
                  whiteSpace: "nowrap",
                  textOverflow: "ellipsis",
                }}
              >
                {item.className}
              </Typography>
            </Box>
            <Typography
              component="span"
              sx={{ display: "block", color: "#8B837A", fontSize: 11.5 }}
            >
              {item.level} · {item.subject}
            </Typography>
          </Button>
        );
      })}
    </Box>
  );
}

export default function StudentForm({
  mode,
  initialStudent,
  submitStudent,
  onComplete,
  loadClasses = fetchTutorClasses,
  loadStudentAccounts = fetchAvailableStudentAccounts,
  cancelHref = "/students",
}: StudentFormProps) {
  const [values, setValues] = React.useState<FormValues>(() =>
    valuesFor(initialStudent),
  );
  const [errors, setErrors] = React.useState<FieldErrors>({});
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [classes, setClasses] = React.useState<TutorClass[] | null>(null);
  const [classesError, setClassesError] = React.useState<string | null>(null);
  const [studentAccounts, setStudentAccounts] = React.useState<
    AvailableStudentAccount[]
  >([]);
  const [studentSearch, setStudentSearch] = React.useState("");
  const [selectedAccount, setSelectedAccount] =
    React.useState<AvailableStudentAccount | null>(null);
  const [accountsLoading, setAccountsLoading] = React.useState(
    mode === "create",
  );
  const [accountsError, setAccountsError] = React.useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const submissionInFlight = React.useRef(false);
  const accountRequestSequence = React.useRef(0);

  const loadMembershipClasses = React.useCallback(async () => {
    setClasses(null);
    setClassesError(null);
    try {
      setClasses(await loadClasses());
    } catch (reason) {
      setClassesError(
        reason instanceof Error
          ? reason.message
          : "Your classes could not be loaded. Please try again.",
      );
    }
  }, [loadClasses]);

  const loadAccounts = React.useCallback(
    async (search: string) => {
      const requestSequence = ++accountRequestSequence.current;
      setAccountsLoading(true);
      setAccountsError(null);
      try {
        const accounts = await loadStudentAccounts(search);
        if (requestSequence === accountRequestSequence.current) {
          setStudentAccounts(accounts);
        }
      } catch (reason) {
        if (requestSequence === accountRequestSequence.current) {
          setAccountsError(
            reason instanceof Error
              ? reason.message
              : "Student accounts could not be loaded. Please try again.",
          );
        }
      } finally {
        if (requestSequence === accountRequestSequence.current) {
          setAccountsLoading(false);
        }
      }
    },
    [loadStudentAccounts],
  );

  React.useEffect(() => {
    if (mode !== "create") return;
    const timer = window.setTimeout(() => {
      void loadAccounts(studentSearch);
    }, 180);
    return () => window.clearTimeout(timer);
  }, [loadAccounts, mode, studentSearch]);

  React.useEffect(() => {
    let current = true;
    const requestMembershipClasses = async () => {
      try {
        const loaded = await loadClasses();
        if (current) setClasses(loaded);
      } catch (reason) {
        if (current)
          setClassesError(
            reason instanceof Error
              ? reason.message
              : "Your classes could not be loaded. Please try again.",
          );
      }
    };
    void requestMembershipClasses();
    return () => {
      current = false;
    };
  }, [loadClasses]);

  const updateValue = (field: "fullName" | "loginUserId", value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
  };
  const toggleClass = (classId: number) => {
    setValues((current) => ({
      ...current,
      classIds: current.classIds.includes(classId)
        ? current.classIds.filter((id) => id !== classId)
        : [...current.classIds, classId],
    }));
    setErrors((current) => {
      const next = { ...current };
      delete next.classIds;
      return next;
    });
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionInFlight.current) return;
    const validation = validateStudentForm(values, mode, selectedAccount);
    if (Object.keys(validation).length) {
      setErrors(validation);
      setSubmitError(null);
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    setErrors({});
    setSubmitError(null);
    try {
      onComplete(
        await submitStudent(requestFor(values, mode, selectedAccount)),
      );
    } catch (reason) {
      if (reason instanceof StudentApiError) {
        const fields = { ...reason.fields };
        if (fields.loginUserId) {
          fields.studentAccount = fields.loginUserId;
          delete fields.loginUserId;
        }
        setErrors(fields);
        setSubmitError(reason.message);
      } else
        setSubmitError(
          reason instanceof Error
            ? reason.message
            : "Your student could not be saved. Please try again.",
        );
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  };

  const submitLabel = mode === "create" ? "Create student" : "Save changes";
  const pendingLabel =
    mode === "create" ? "Creating student…" : "Saving changes…";

  return (
    <Card
      component="form"
      noValidate
      onSubmit={handleSubmit}
      variant="outlined"
      sx={{
        maxWidth: 880,
        p: { xs: 2.25, sm: 3 },
        borderRadius: "14px",
        bgcolor: "#FFFDFA",
        borderColor: "#EBE4D9",
        boxShadow: "none",
      }}
    >
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            sm: "minmax(0, 1fr) minmax(0, 1fr)",
          },
          gap: 2,
        }}
      >
        {mode === "create" ? (
          <Box sx={{ gridColumn: { sm: "1 / -1" } }}>
            <Autocomplete
              fullWidth
              options={studentAccounts}
              value={selectedAccount}
              loading={accountsLoading}
              inputValue={studentSearch}
              onInputChange={(_, value) => {
                setStudentSearch(value);
                setSelectedAccount(null);
                setErrors((current) => {
                  const next = { ...current };
                  delete next.studentAccount;
                  return next;
                });
              }}
              onChange={(_, account) => {
                setSelectedAccount(account);
                setErrors((current) => {
                  const next = { ...current };
                  delete next.studentAccount;
                  return next;
                });
              }}
              getOptionLabel={(account) =>
                `${account.fullName} — ${account.email}`
              }
              isOptionEqualToValue={(left, right) => left.id === right.id}
              noOptionsText={
                accountsLoading
                  ? "Loading Student accounts…"
                  : "No available students found."
              }
              renderOption={(props, account) => (
                <Box
                  component="li"
                  {...props}
                  key={account.id}
                  sx={{ display: "block !important", py: "10px !important" }}
                >
                  <Typography sx={{ fontWeight: 600, fontSize: 14 }}>
                    {account.fullName}
                  </Typography>
                  <Typography sx={{ color: "#6F675E", fontSize: 12.5 }}>
                    {account.level ? `${account.level} · ` : ""}
                    {account.email}
                  </Typography>
                </Box>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  required
                  label="Student"
                  placeholder="Search students..."
                  error={Boolean(errors.studentAccount)}
                  helperText={
                    errors.studentAccount ||
                    "Search by student name or email/login."
                  }
                  sx={fieldSx}
                />
              )}
            />
            {accountsLoading ? (
              <Typography
                role="status"
                aria-label="Loading Student accounts"
                sx={{ mt: 1, color: "#6F675E", fontSize: 12.5 }}
              >
                Loading Student accounts…
              </Typography>
            ) : null}
            {accountsError ? (
              <Alert
                role="alert"
                severity="error"
                sx={{ mt: 1.25 }}
                action={
                  <Button
                    color="inherit"
                    size="small"
                    onClick={() => void loadAccounts(studentSearch)}
                  >
                    Retry
                  </Button>
                }
              >
                {accountsError}
              </Alert>
            ) : null}
            {!accountsLoading &&
            !accountsError &&
            studentAccounts.length === 0 ? (
              <Typography
                role="status"
                sx={{ mt: 1, color: "#6F675E", fontSize: 12.5 }}
              >
                No available students found.
              </Typography>
            ) : null}
          </Box>
        ) : (
          <TextField
            required
            fullWidth
            label="Student name"
            value={values.fullName}
            onChange={(event) => updateValue("fullName", event.target.value)}
            slotProps={{
              htmlInput: { maxLength: 120, "aria-label": "Student name" },
            }}
            error={Boolean(errors.fullName)}
            helperText={errors.fullName || "Use the name families recognise."}
            sx={{ ...fieldSx, gridColumn: { sm: "1 / -1" } }}
          />
        )}
      </Box>

      <Box
        component="section"
        aria-labelledby="student-class-memberships"
        sx={{ mt: 3, pt: 2.5, borderTop: "1px solid #F0EAE0" }}
      >
        <Typography
          id="student-class-memberships"
          sx={{
            fontFamily: "'Playfair Display', Georgia, serif",
            fontSize: 21,
            fontWeight: 500,
            lineHeight: 1.2,
          }}
        >
          Class memberships
        </Typography>
        <Typography
          sx={{
            color: "#8B837A",
            fontSize: 12.5,
            lineHeight: 1.55,
            mt: 0.5,
            mb: 1.5,
          }}
        >
          Select the classes this student attends. Selections are owner-scoped
          and saved together.
        </Typography>
        {classes === null && !classesError ? (
          <Box
            data-testid="student-class-picker-skeleton"
            sx={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(215px, 1fr))",
              gap: 1.25,
            }}
          >
            {[0, 1].map((item) => (
              <Skeleton
                key={item}
                variant="rounded"
                height={84}
                sx={{ bgcolor: "#F0EAE0", borderRadius: "12px" }}
              />
            ))}
          </Box>
        ) : null}
        {classesError ? (
          <Box
            role="alert"
            sx={{
              p: 1.75,
              borderLeft: "3px solid #B4573F",
              borderRadius: "0 10px 10px 0",
              bgcolor: "#F6EFE6",
            }}
          >
            <Typography
              sx={{ color: "#5A544C", fontSize: 13, lineHeight: 1.55, mb: 1 }}
            >
              {classesError}
            </Typography>
            <Button
              type="button"
              onClick={() => void loadMembershipClasses()}
              sx={{
                minHeight: 34,
                color: "#B4573F",
                textTransform: "none",
                fontWeight: 600,
                px: 0.5,
              }}
            >
              Retry loading classes
            </Button>
          </Box>
        ) : null}
        {classes ? (
          <MembershipPicker
            classes={classes}
            selectedIds={values.classIds}
            onToggle={toggleClass}
          />
        ) : null}
        {errors.classIds ? (
          <Typography
            role="alert"
            sx={{ color: "#B4573F", fontSize: 11.5, mt: 1 }}
          >
            {errors.classIds}
          </Typography>
        ) : null}
      </Box>

      {submitError ? (
        <Box
          role="alert"
          sx={{
            mt: 2.5,
            p: 1.75,
            borderLeft: "3px solid #B4573F",
            borderRadius: "0 10px 10px 0",
            bgcolor: "#F6EFE6",
          }}
        >
          <Typography sx={{ color: "#5A544C", fontSize: 13, lineHeight: 1.55 }}>
            {submitError}
          </Typography>
        </Box>
      ) : null}
      <Box
        sx={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 1.25,
          mt: 3,
        }}
      >
        <Button
          type="submit"
          disabled={isSubmitting}
          sx={{
            minHeight: 42,
            borderRadius: "10px",
            bgcolor: "#9E3A24",
            color: "#FBF9F5",
            textTransform: "none",
            fontWeight: 500,
            px: 2.25,
            boxShadow: "0 1px 2px rgba(42,38,34,.12)",
            "&:hover": { bgcolor: "#8A3120" },
            "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#B5AA9C" },
          }}
        >
          {isSubmitting ? pendingLabel : submitLabel}
        </Button>
        <Button
          component={Link}
          href={cancelHref}
          sx={{
            minHeight: 42,
            border: "1px solid #E4DCD0",
            borderRadius: "10px",
            bgcolor: "#FFFDFA",
            color: "#2A2622",
            textTransform: "none",
            fontWeight: 500,
            px: 2,
            "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
          }}
        >
          Cancel
        </Button>
      </Box>
    </Card>
  );
}
