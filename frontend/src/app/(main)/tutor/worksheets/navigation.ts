import type { TutorWorksheet } from "@/services/worksheets";

function positiveId(value: number | null | undefined): value is number {
  return Number.isSafeInteger(value) && (value ?? 0) > 0;
}

export function worksheetClassId(
  worksheet: TutorWorksheet,
  fallbackClassId?: number,
): number | undefined {
  if (positiveId(worksheet.sourceClassId)) return worksheet.sourceClassId;

  const assignmentClassId = worksheet.assignments.find((assignment) =>
    positiveId(assignment.classId),
  )?.classId;

  if (positiveId(assignmentClassId)) return assignmentClassId;
  return positiveId(fallbackClassId) ? fallbackClassId : undefined;
}

export function tutorWorksheetsHref({
  classId,
  approved = false,
}: {
  classId?: number;
  approved?: boolean;
} = {}): string {
  const query = new URLSearchParams();
  if (positiveId(classId)) query.set("classId", String(classId));
  if (approved) query.set("approved", "1");

  const suffix = query.toString();
  return suffix ? `/tutor/worksheets?${suffix}` : "/tutor/worksheets";
}
