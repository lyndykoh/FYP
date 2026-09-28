"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { WorksheetBuilder } from "@/components/worksheets/WorksheetBuilder";
import type { TutorWorksheet } from "@/services/worksheets";
import { tutorWorksheetsHref, worksheetClassId } from "../navigation";

function positiveQueryId(value: string | null): number | undefined {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/** The profile action supplies both identifiers; the builder verifies membership before it sends a request. */
export default function Page() {
  const params = useSearchParams();
  const router = useRouter();
  const classId = positiveQueryId(params.get("classId"));
  const onApproved = React.useCallback(
    (worksheet: TutorWorksheet) => {
      router.replace(
        tutorWorksheetsHref({
          classId: worksheetClassId(worksheet, classId),
          approved: true,
        }),
      );
    },
    [classId, router],
  );

  return (
    <WorksheetBuilder
      classId={classId ?? 0}
      initialStudentId={positiveQueryId(params.get("studentId"))}
      onApproved={onApproved}
    />
  );
}
