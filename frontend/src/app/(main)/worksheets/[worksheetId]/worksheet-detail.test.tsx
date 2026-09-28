import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Page from "./page";
import {
  downloadStudentWorksheetPdf,
  fetchStudentWorksheet,
  type StudentWorksheetDetail,
} from "@/services/worksheets";

const navigation = vi.hoisted(() => ({ worksheetId: "12" }));
vi.mock("next/navigation", () => ({ useParams: () => navigation }));
vi.mock("@/services/worksheets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/worksheets")>();
  return {
    ...actual,
    fetchStudentWorksheet: vi.fn(),
    downloadStudentWorksheetPdf: vi.fn(),
  };
});

const worksheet: StudentWorksheetDetail = {
  id: 12,
  code: "SCI-12",
  title: "Water cycle practice",
  instructions: "Answer every question in complete sentences.",
  subject: "Science",
  assignedAt: "2026-08-20T09:00:00",
  dueAt: "2026-08-30T17:00:00",
  questions: [
    {
      id: 10,
      code: "SCI-WATER-1",
      prompt: "Explain evaporation.",
      questionType: "OPEN_ENDED",
      totalMarks: 2,
      topicId: 8,
      topicName: "Water cycle",
    },
  ],
};

describe("Student worksheet detail page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    navigation.worksheetId = "12";
    vi.mocked(fetchStudentWorksheet).mockResolvedValue(worksheet);
    vi.mocked(downloadStudentWorksheetPdf).mockResolvedValue(
      new Blob(["pdf"], { type: "application/pdf" }),
    );
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:worksheet"),
      revokeObjectURL: vi.fn(),
    });
  });

  it("loads only the selected Student worksheet and renders its safe question content", async () => {
    render(<Page />);
    expect(
      await screen.findByRole("heading", { name: "Water cycle practice" }),
    ).toBeVisible();
    expect(fetchStudentWorksheet).toHaveBeenCalledWith(12);
    expect(screen.getByText("Explain evaporation.")).toBeVisible();
    expect(
      screen.getByText(/SCI-WATER-1.*Water cycle.*Open Ended.*2.0 marks/),
    ).toBeVisible();
    expect(
      screen.getByText("Answer every question in complete sentences."),
    ).toBeVisible();
  });

  it("downloads the selected worksheet PDF with a recoverable error state", async () => {
    const user = userEvent.setup();
    render(<Page />);
    await screen.findByRole("heading", { name: "Water cycle practice" });
    await user.click(screen.getByRole("button", { name: "Download PDF" }));
    await waitFor(() =>
      expect(downloadStudentWorksheetPdf).toHaveBeenCalledWith(12),
    );
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:worksheet");

    vi.mocked(downloadStudentWorksheetPdf).mockRejectedValueOnce(
      new Error("Worksheet PDF could not be downloaded."),
    );
    await user.click(screen.getByRole("button", { name: "Download PDF" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Worksheet PDF could not be downloaded.",
    );
  });

  it("shows loading, invalid-id, and not-found states without fallback content", async () => {
    let resolve: ((detail: StudentWorksheetDetail) => void) | undefined;
    vi.mocked(fetchStudentWorksheet).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { rerender } = render(<Page />);
    expect(
      screen.getByTestId("student-worksheet-detail-loading"),
    ).toBeVisible();
    await waitFor(() => expect(fetchStudentWorksheet).toHaveBeenCalledWith(12));
    resolve?.(worksheet);
    expect(
      await screen.findByRole("heading", { name: "Water cycle practice" }),
    ).toBeVisible();

    vi.mocked(fetchStudentWorksheet).mockRejectedValueOnce(
      new Error("Worksheet not found."),
    );
    navigation.worksheetId = "404";
    rerender(<Page key="missing" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Worksheet not found.",
    );

    navigation.worksheetId = "invalid";
    rerender(<Page key="invalid" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "worksheet reference is invalid",
    );
  });
});
