import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import StudentWorksheetsPage from "./page";
import {
  fetchStudentWorksheets,
  type StudentWorksheet,
} from "@/services/worksheets";

vi.mock("@/services/worksheets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/worksheets")>();
  return { ...actual, fetchStudentWorksheets: vi.fn() };
});

const worksheets: StudentWorksheet[] = [
  {
    id: 11,
    code: "SCI-11",
    title: "Plant transport review",
    subjects: [{ id: 1, name: "Science" }],
    topics: [{ id: 10, name: "Plant transport" }],
    assignedAt: "2026-08-08T09:00:00",
    dueAt: "2026-08-20T23:59:00",
    status: "ASSIGNED",
    submittedAt: null,
    reviewedAt: null,
    score: null,
  },
  {
    id: 12,
    code: "MTH-12",
    title: "Fractions reasoning",
    subjects: [{ id: 2, name: "Mathematics" }],
    topics: [{ id: 20, name: "Fractions" }],
    assignedAt: "2026-08-14T09:00:00",
    dueAt: null,
    status: "SUBMITTED",
    submittedAt: "2026-08-15T12:00:00",
    reviewedAt: null,
    score: null,
  },
  {
    id: 13,
    code: "SCI-13",
    title: "Plant vocabulary check",
    subjects: [{ id: 1, name: "Science" }],
    topics: [
      { id: 10, name: "Plant transport" },
      { id: 11, name: "Photosynthesis" },
    ],
    assignedAt: "2026-08-21T09:00:00",
    dueAt: "2026-08-30T23:59:00",
    status: "MARKED",
    submittedAt: "2026-08-22T12:00:00",
    reviewedAt: "2026-08-23T10:00:00",
    score: { earned: 8, available: 10, percent: 80 },
  },
];

async function selectOption(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(screen.getByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}

describe("Student worksheet library page", () => {
  beforeEach(() => {
    vi.mocked(fetchStudentWorksheets).mockReset();
  });

  it("renders canonical assignment dates, score, and context-sensitive actions", async () => {
    vi.mocked(fetchStudentWorksheets).mockResolvedValue(worksheets);
    render(<StudentWorksheetsPage />);

    expect(
      await screen.findByRole("heading", { name: "My Worksheets" }),
    ).toBeVisible();
    expect(screen.getByText("Plant transport review")).toBeVisible();
    expect(screen.getByText("Science · Plant transport")).toBeVisible();
    expect(screen.getByText("80%", { exact: true })).toBeVisible();
    expect(screen.getByText("8/10 marks")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Plant transport review" }),
    ).toHaveAttribute("href", "/worksheets/11");
    expect(screen.getByRole("link", { name: "Upload work" })).toHaveAttribute(
      "href",
      "/upload?ws=11",
    );
    expect(
      screen.getAllByRole("link", { name: "Open worksheet" })[0],
    ).toHaveAttribute("href", "/worksheets/11");
    expect(screen.getByRole("link", { name: "View results" })).toHaveAttribute(
      "href",
      "/worksheets/13/results",
    );
    expect(screen.getByRole("status", { name: "" })).toHaveTextContent(
      "Awaiting tutor review",
    );
  });

  it("combines search, subject, topic, status, and assigned-date filters", async () => {
    const user = userEvent.setup();
    vi.mocked(fetchStudentWorksheets).mockResolvedValue(worksheets);
    render(<StudentWorksheetsPage />);
    await screen.findByText("Plant transport review");

    await user.click(screen.getByRole("button", { name: "Marked" }));
    expect(
      screen.queryByText("Plant transport review"),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Plant vocabulary check")).toBeVisible();
    await selectOption(user, "Subject", "Science");
    await selectOption(user, "Topic", "Photosynthesis");
    expect(screen.getByText("Plant vocabulary check")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Assigned from"), {
      target: { value: "2026-08-22" },
    });
    expect(
      screen.getByRole("heading", {
        name: "No worksheets match those filters",
      }),
    ).toBeVisible();
    fireEvent.change(screen.getByLabelText("Assigned from"), {
      target: { value: "2026-08-21" },
    });
    await user.clear(screen.getByLabelText("Search worksheets"));
    await user.type(screen.getByLabelText("Search worksheets"), "vocabulary");
    expect(screen.getByText("Plant vocabulary check")).toBeVisible();
    await user.clear(screen.getByLabelText("Search worksheets"));
    await user.type(screen.getByLabelText("Search worksheets"), "fractions");
    expect(
      screen.getByRole("heading", {
        name: "No worksheets match those filters",
      }),
    ).toBeVisible();
  });

  it("renders loading, empty, invalid-response error, and retries a recoverable failure", async () => {
    const user = userEvent.setup();
    let resolve: ((value: StudentWorksheet[]) => void) | undefined;
    vi.mocked(fetchStudentWorksheets).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { rerender } = render(<StudentWorksheetsPage />);
    expect(screen.getByTestId("student-worksheet-skeleton")).toBeVisible();
    await waitFor(() =>
      expect(fetchStudentWorksheets).toHaveBeenCalledTimes(1),
    );
    resolve?.([]);
    expect(
      await screen.findByRole("heading", {
        name: "No worksheets have been assigned yet",
      }),
    ).toBeVisible();

    vi.mocked(fetchStudentWorksheets)
      .mockRejectedValueOnce(
        new Error(
          "The learning service returned an invalid student worksheet list. Please try again.",
        ),
      )
      .mockResolvedValueOnce(worksheets);
    rerender(<StudentWorksheetsPage key="invalid" />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "invalid student worksheet list",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading worksheets" }),
    );
    await waitFor(() =>
      expect(screen.getByText("Plant transport review")).toBeVisible(),
    );
  });

  it("keeps controls and cards in an auto-flowing mobile layout", async () => {
    const previousWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: 320,
    });
    vi.mocked(fetchStudentWorksheets).mockResolvedValue(worksheets);
    render(<StudentWorksheetsPage />);

    expect(await screen.findByTestId("student-worksheet-list")).toBeVisible();
    expect(screen.getByLabelText("Search worksheets")).toBeVisible();
    expect(screen.getByLabelText("Assigned from")).toBeVisible();
    expect(screen.getByRole("link", { name: "Upload work" })).toBeVisible();
    const styles = [...document.querySelectorAll("style")]
      .map((style) => style.textContent ?? "")
      .join("\n");
    expect(styles).toMatch(/grid-template-columns:1fr/);
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      value: previousWidth,
    });
  });
});
