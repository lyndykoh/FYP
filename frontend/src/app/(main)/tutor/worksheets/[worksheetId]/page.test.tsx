import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Page from "./page";
import {
  approveWorksheet,
  fetchTutorWorksheet,
  type TutorWorksheet,
} from "@/services/worksheets";

const navigation = vi.hoisted(() => ({ worksheetId: "7", replace: vi.fn() }));
vi.mock("next/navigation", () => ({
  useParams: () => navigation,
  useRouter: () => ({ replace: navigation.replace }),
}));
vi.mock("@/services/worksheets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/worksheets")>();
  return { ...actual, approveWorksheet: vi.fn(), fetchTutorWorksheet: vi.fn() };
});

const worksheet: TutorWorksheet = {
  id: 7,
  code: "WS-7",
  title: "Water practice",
  instructions: null,
  subject: "Science",
  worksheetType: "STANDARD",
  targetMode: "CLASS",
  status: "DRAFT",
  generationRequestId: 4,
  dueAt: null,
  assignments: [],
  questions: [
    {
      id: 8,
      code: "Q-8",
      prompt: "Explain condensation.",
      questionType: "OPEN_ENDED",
      totalMarks: 2,
      topicId: 10,
      topicName: "Water",
    },
  ],
};

describe("Tutor worksheet detail page", () => {
  beforeEach(() => {
    navigation.worksheetId = "7";
    navigation.replace.mockReset();
    vi.mocked(fetchTutorWorksheet).mockReset();
    vi.mocked(approveWorksheet).mockReset();
  });
  it("renders loading then the owner-scoped worksheet", async () => {
    let resolve: ((value: TutorWorksheet) => void) | undefined;
    vi.mocked(fetchTutorWorksheet).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    render(<Page />);
    expect(screen.getByTestId("tutor-worksheet-detail-skeleton")).toBeVisible();
    resolve?.(worksheet);
    expect(
      await screen.findByRole("heading", { name: "Water practice" }),
    ).toBeVisible();
  });
  it("renders missing/foreign access as a recoverable error and retries", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchTutorWorksheet)
      .mockRejectedValueOnce(new Error("Worksheet resource was not found."))
      .mockResolvedValueOnce(worksheet);
    render(<Page />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Worksheet resource was not found.",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading worksheet" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "Water practice" }),
      ).toBeVisible(),
    );
    expect(fetchTutorWorksheet).toHaveBeenCalledTimes(2);
  });
  it("rejects an invalid worksheet id before requesting the service", () => {
    navigation.worksheetId = "not-a-number";
    render(<Page />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "worksheet reference is invalid",
    );
    expect(fetchTutorWorksheet).not.toHaveBeenCalled();
  });
  it("replaces the detail route after a successful approval and keeps its class filter", async () => {
    vi.mocked(fetchTutorWorksheet).mockResolvedValue({
      ...worksheet,
      sourceClassId: 12,
    });
    vi.mocked(approveWorksheet).mockResolvedValue({
      ...worksheet,
      sourceClassId: 12,
      status: "APPROVED",
      assignments: [
        {
          id: 1,
          assignmentType: "CLASS",
          classId: 12,
          studentProfileId: null,
          assignedAt: "2026-09-06T10:00:00",
          dueAt: null,
        },
      ],
    });
    render(<Page />);

    await userEvent
      .setup()
      .click(
        await screen.findByRole("button", {
          name: "Approve & assign worksheet",
        }),
      );

    expect(navigation.replace).toHaveBeenCalledWith(
      "/tutor/worksheets?classId=12&approved=1",
    );
  });
});
