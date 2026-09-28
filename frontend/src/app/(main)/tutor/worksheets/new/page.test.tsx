import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigation = vi.hoisted(() => ({
  classId: "12",
  studentId: "31",
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      key === "classId"
        ? navigation.classId
        : key === "studentId"
          ? navigation.studentId
          : null,
  }),
  useRouter: () => ({ replace: navigation.replace }),
}));

vi.mock("@/components/worksheets/WorksheetBuilder", () => ({
  WorksheetBuilder: ({
    classId,
    initialStudentId,
    onApproved,
  }: {
    classId: number;
    initialStudentId?: number;
    onApproved?: (worksheet: {
      id: number;
      sourceClassId?: number;
      assignments: [];
    }) => void;
  }) => (
    <>
      <output data-testid="worksheet-target-context">
        {classId}:{initialStudentId ?? "none"}
      </output>
      <button
        onClick={() =>
          onApproved?.({ id: 22, sourceClassId: 12, assignments: [] })
        }
      >
        Complete approval
      </button>
    </>
  ),
}));

import GenerateWorksheetPage from "./page";

describe("GenerateWorksheetPage", () => {
  beforeEach(() => {
    navigation.classId = "12";
    navigation.studentId = "31";
    navigation.replace.mockReset();
  });

  it("passes valid class and future student context into the in-generator selector", () => {
    render(<GenerateWorksheetPage />);
    expect(screen.getByTestId("worksheet-target-context")).toHaveTextContent(
      "12:31",
    );
  });

  it("does not pass malformed query values as trusted targets", () => {
    navigation.classId = "not-a-number";
    navigation.studentId = "0";
    render(<GenerateWorksheetPage />);
    expect(screen.getByTestId("worksheet-target-context")).toHaveTextContent(
      "0:none",
    );
  });

  it("replaces the generator route with the filtered worksheet index after approval", async () => {
    render(<GenerateWorksheetPage />);

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Complete approval" }));

    expect(navigation.replace).toHaveBeenCalledWith(
      "/tutor/worksheets?classId=12&approved=1",
    );
  });
});
