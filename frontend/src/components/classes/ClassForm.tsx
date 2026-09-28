"use client";

import * as React from "react";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import MenuItem from "@mui/material/MenuItem";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Link from "next/link";

import {
  ClassApiError,
  type ClassMutationRequest,
  type ClassSchedule,
  type ClassStatus,
  type TutorClass,
} from "@/services/classes";

const DAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

type FormValues = {
  className: string;
  subject: string;
  level: string;
  status: ClassStatus;
  schedules: ClassSchedule[];
};

type FieldErrors = Record<string, string>;

export interface ClassFormProps {
  mode: "create" | "edit";
  initialClass?: TutorClass;
  submitClass: (request: ClassMutationRequest) => Promise<TutorClass>;
  onComplete: (savedClass: TutorClass) => void;
  cancelHref?: string;
}

function emptyValues(): FormValues {
  return {
    className: "",
    subject: "",
    level: "",
    status: "ACTIVE",
    schedules: [],
  };
}

function valuesFor(initialClass?: TutorClass): FormValues {
  if (!initialClass) return emptyValues();
  return {
    className: initialClass.className,
    subject: initialClass.subject,
    level: initialClass.level,
    status: initialClass.status,
    schedules: initialClass.schedules.map((schedule) => ({ ...schedule })),
  };
}

function maxLengthError(label: string, value: string, maxLength: number) {
  return value.trim().length > maxLength
    ? `${label} must be ${maxLength} characters or fewer.`
    : undefined;
}

export function validateClassForm(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  const fields: Array<
    [keyof Pick<FormValues, "className" | "subject" | "level">, string, number]
  > = [
    ["className", "Class name", 120],
    ["subject", "Subject", 80],
    ["level", "Level", 40],
  ];

  for (const [field, label, maxLength] of fields) {
    const value = values[field];
    if (!value.trim()) {
      errors[field] = `${label} is required.`;
    } else {
      const error = maxLengthError(label, value, maxLength);
      if (error) errors[field] = error;
    }
  }

  if (values.schedules.length > 7) {
    errors.schedules = "A class can have up to 7 schedule times.";
  }

  const seen = new Set<string>();
  values.schedules.forEach((schedule, index) => {
    const prefix = `schedules.${index}`;
    if (!schedule.dayOfWeek) errors[`${prefix}.dayOfWeek`] = "Choose a day.";
    if (!schedule.startTime)
      errors[`${prefix}.startTime`] = "Enter a start time.";
    if (!schedule.endTime) errors[`${prefix}.endTime`] = "Enter an end time.";
    if (
      schedule.startTime &&
      schedule.endTime &&
      schedule.startTime >= schedule.endTime
    ) {
      errors[`${prefix}.endTime`] = "End time must be after the start time.";
    }

    const scheduleKey = `${schedule.dayOfWeek}|${schedule.startTime}|${schedule.endTime}`;
    if (schedule.dayOfWeek && schedule.startTime && schedule.endTime) {
      if (seen.has(scheduleKey)) {
        errors[`${prefix}.dayOfWeek`] = "This schedule time is already listed.";
      }
      seen.add(scheduleKey);
    }
  });

  return errors;
}

function requestFor(values: FormValues): ClassMutationRequest {
  return {
    className: values.className.trim(),
    subject: values.subject.trim(),
    level: values.level.trim(),
    status: values.status,
    schedules: values.schedules.map((schedule) => ({ ...schedule })),
  };
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

function humanDay(day: string) {
  return `${day[0]}${day.slice(1).toLowerCase()}`;
}

export default function ClassForm({
  mode,
  initialClass,
  submitClass,
  onComplete,
  cancelHref = "/classes",
}: ClassFormProps) {
  const [values, setValues] = React.useState<FormValues>(() =>
    valuesFor(initialClass),
  );
  const [errors, setErrors] = React.useState<FieldErrors>({});
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const submissionInFlight = React.useRef(false);

  const updateValue = <Field extends keyof Omit<FormValues, "schedules">>(
    field: Field,
    value: FormValues[Field],
  ) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const updateSchedule = (
    index: number,
    field: keyof ClassSchedule,
    value: string,
  ) => {
    setValues((current) => ({
      ...current,
      schedules: current.schedules.map((schedule, scheduleIndex) =>
        scheduleIndex === index ? { ...schedule, [field]: value } : schedule,
      ),
    }));
    setErrors((current) => {
      const next = { ...current };
      delete next[`schedules.${index}.${field}`];
      delete next[`schedules.${index}.dayOfWeek`];
      return next;
    });
  };

  const addSchedule = () => {
    setValues((current) =>
      current.schedules.length >= 7
        ? current
        : {
            ...current,
            schedules: [
              ...current.schedules,
              { dayOfWeek: "MONDAY", startTime: "", endTime: "" },
            ],
          },
    );
    setErrors((current) => ({ ...current, schedules: "" }));
  };

  const removeSchedule = (index: number) => {
    setValues((current) => ({
      ...current,
      schedules: current.schedules.filter(
        (_, scheduleIndex) => scheduleIndex !== index,
      ),
    }));
    setErrors({});
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionInFlight.current) return;

    const clientErrors = validateClassForm(values);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      setSubmitError(null);
      return;
    }

    submissionInFlight.current = true;
    setIsSubmitting(true);
    setErrors({});
    setSubmitError(null);
    try {
      const savedClass = await submitClass(requestFor(values));
      onComplete(savedClass);
    } catch (reason) {
      if (reason instanceof ClassApiError) {
        setErrors(reason.fields);
        setSubmitError(reason.message);
      } else {
        setSubmitError(
          reason instanceof Error
            ? reason.message
            : "Your class could not be saved. Please try again.",
        );
      }
    } finally {
      submissionInFlight.current = false;
      setIsSubmitting(false);
    }
  };

  const submitLabel = mode === "create" ? "Create class" : "Save changes";
  const pendingLabel =
    mode === "create" ? "Creating class…" : "Saving changes…";

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
        <TextField
          required
          fullWidth
          label="Class name"
          value={values.className}
          onChange={(event) => updateValue("className", event.target.value)}
          slotProps={{
            htmlInput: { maxLength: 120, "aria-label": "Class name" },
          }}
          error={Boolean(errors.className)}
          helperText={errors.className || "Use the name families recognise."}
          sx={{ ...fieldSx, gridColumn: { sm: "1 / -1" } }}
        />
        <TextField
          required
          fullWidth
          label="Subject"
          value={values.subject}
          onChange={(event) => updateValue("subject", event.target.value)}
          slotProps={{ htmlInput: { maxLength: 80, "aria-label": "Subject" } }}
          error={Boolean(errors.subject)}
          helperText={errors.subject}
          sx={fieldSx}
        />
        <TextField
          required
          fullWidth
          label="Level"
          value={values.level}
          onChange={(event) => updateValue("level", event.target.value)}
          slotProps={{ htmlInput: { maxLength: 40, "aria-label": "Level" } }}
          error={Boolean(errors.level)}
          helperText={errors.level}
          sx={fieldSx}
        />
        <TextField
          select
          fullWidth
          label="Class status"
          value={values.status}
          onChange={(event) =>
            updateValue("status", event.target.value as ClassStatus)
          }
          sx={fieldSx}
        >
          <MenuItem value="ACTIVE">Active</MenuItem>
          <MenuItem value="INACTIVE">Inactive</MenuItem>
        </TextField>
      </Box>

      <Box
        component="section"
        aria-labelledby="class-schedule-title"
        sx={{ mt: 3, pt: 2.5, borderTop: "1px solid #F0EAE0" }}
      >
        <Box
          sx={{
            display: "flex",
            flexWrap: "wrap",
            alignItems: "flex-start",
            justifyContent: "space-between",
            gap: 1.5,
            mb: 1.5,
          }}
        >
          <Box>
            <Typography
              id="class-schedule-title"
              sx={{
                fontFamily: "'Playfair Display', Georgia, serif",
                fontSize: 21,
                fontWeight: 500,
                lineHeight: 1.2,
              }}
            >
              Class schedule
            </Typography>
            <Typography
              sx={{
                color: "#8B837A",
                fontSize: 12.5,
                lineHeight: 1.55,
                mt: 0.5,
              }}
            >
              Add the regular teaching times, or leave this empty while the
              schedule is being confirmed.
            </Typography>
          </Box>
          <Button
            type="button"
            onClick={addSchedule}
            disabled={values.schedules.length >= 7}
            startIcon={<AddIcon />}
            sx={{
              minHeight: 40,
              border: "1px solid #E4DCD0",
              borderRadius: "10px",
              bgcolor: "#FFFDFA",
              color: "#2A2622",
              textTransform: "none",
              fontWeight: 500,
              px: 1.75,
              "&:hover": { bgcolor: "#F4EFE6", borderColor: "#DCCFBE" },
              "&.Mui-disabled": { bgcolor: "#EDE6DB", color: "#B5AA9C" },
            }}
          >
            Add schedule time
          </Button>
        </Box>
        {errors.schedules && (
          <Typography
            role="alert"
            sx={{ color: "#B4573F", fontSize: 11.5, mb: 1 }}
          >
            {errors.schedules}
          </Typography>
        )}
        {values.schedules.length === 0 ? (
          <Box
            sx={{
              p: 2,
              border: "1px dashed #DCCFBE",
              borderRadius: "12px",
              bgcolor: "#FBF9F5",
            }}
          >
            <Typography sx={{ color: "#6F675E", fontSize: 13 }}>
              No regular schedule has been added.
            </Typography>
          </Box>
        ) : (
          <Box sx={{ display: "grid", gap: 1.25 }}>
            {values.schedules.map((schedule, index) => (
              <Box
                key={index}
                sx={{
                  display: "grid",
                  gridTemplateColumns: {
                    xs: "minmax(0, 1fr) minmax(0, 1fr)",
                    sm: "minmax(150px, 1fr) minmax(130px, .8fr) minmax(130px, .8fr) auto",
                  },
                  gap: 1.25,
                  alignItems: "start",
                  p: 1.5,
                  border: "1px solid #F0EAE0",
                  borderRadius: "12px",
                  bgcolor: "#FBF9F5",
                }}
              >
                <TextField
                  select
                  fullWidth
                  label={`Schedule ${index + 1} day`}
                  value={schedule.dayOfWeek}
                  onChange={(event) =>
                    updateSchedule(index, "dayOfWeek", event.target.value)
                  }
                  error={Boolean(errors[`schedules.${index}.dayOfWeek`])}
                  helperText={errors[`schedules.${index}.dayOfWeek`]}
                  sx={fieldSx}
                >
                  {DAYS.map((day) => (
                    <MenuItem key={day} value={day}>
                      {humanDay(day)}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  required
                  fullWidth
                  label={`Schedule ${index + 1} start time`}
                  type="time"
                  value={schedule.startTime}
                  onChange={(event) =>
                    updateSchedule(index, "startTime", event.target.value)
                  }
                  error={Boolean(errors[`schedules.${index}.startTime`])}
                  helperText={errors[`schedules.${index}.startTime`]}
                  slotProps={{
                    inputLabel: { shrink: true },
                    htmlInput: {
                      "aria-label": `Schedule ${index + 1} start time`,
                    },
                  }}
                  sx={fieldSx}
                />
                <TextField
                  required
                  fullWidth
                  label={`Schedule ${index + 1} end time`}
                  type="time"
                  value={schedule.endTime}
                  onChange={(event) =>
                    updateSchedule(index, "endTime", event.target.value)
                  }
                  error={Boolean(errors[`schedules.${index}.endTime`])}
                  helperText={errors[`schedules.${index}.endTime`]}
                  slotProps={{
                    inputLabel: { shrink: true },
                    htmlInput: {
                      "aria-label": `Schedule ${index + 1} end time`,
                    },
                  }}
                  sx={fieldSx}
                />
                <Button
                  type="button"
                  onClick={() => removeSchedule(index)}
                  aria-label={`Remove schedule ${index + 1}`}
                  sx={{
                    minWidth: 40,
                    minHeight: 40,
                    mt: { xs: 0, sm: 0.5 },
                    border: "1px solid #EBE4D9",
                    borderRadius: "9px",
                    color: "#B4573F",
                    bgcolor: "#FFFDFA",
                    "&:hover": { bgcolor: "#F7E3DC", borderColor: "#E0B9AC" },
                  }}
                >
                  <DeleteOutlineIcon aria-hidden="true" sx={{ fontSize: 18 }} />
                </Button>
              </Box>
            ))}
          </Box>
        )}
      </Box>

      {submitError && (
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
      )}

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
