import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigation = vi.hoisted(() => ({
  approved: null as string | null,
  classId: null as string | null,
  replace: vi.fn(),
}));
const worksheets = vi.hoisted(() => ({ fetchTutorWorksheets: vi.fn() }));

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: (key: string) =>
      key === "classId"
        ? navigation.classId
        : key === "approved"
          ? navigation.approved
          : null,
  }),
  useRouter: () => ({ replace: navigation.replace }),
}));
vi.mock("@/services/worksheets", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/worksheets")>();
  return { ...actual, fetchTutorWorksheets: worksheets.fetchTutorWorksheets };
});

import TutorWorksheetsPage from "./page";

describe("TutorWorksheetsPage", () => {
  beforeEach(() => {
    navigation.approved = null;
    navigation.classId = null;
    navigation.replace.mockReset();
    worksheets.fetchTutorWorksheets.mockResolvedValue([]);
  });

  it("opens the in-generator target selector instead of redirecting to My Classes", async () => {
    render(<TutorWorksheetsPage />);
    expect(
      screen.getByRole("link", { name: "Generate Worksheet" }),
    ).toHaveAttribute("href", "/tutor/worksheets/new");
  });

  it("loads a fresh approved worksheet list, shows its assigned state, then clears the one-time flash query", async () => {
    navigation.classId = "12";
    navigation.approved = "1";
    worksheets.fetchTutorWorksheets.mockResolvedValue([
      {
        id: 9,
        code: "WS-9",
        title: "Water review",
        instructions: null,
        targetMode: "CLASS",
        status: "APPROVED",
        generationRequestId: 1,
        sourceClassId: 12,
        dueAt: null,
        questions: [],
        assignments: [],
      },
    ]);
    render(<TutorWorksheetsPage />);

    expect(await screen.findByText("ASSIGNED")).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Worksheet Sent to Students",
    );
    expect(worksheets.fetchTutorWorksheets).toHaveBeenCalledWith(12);
    expect(navigation.replace).toHaveBeenCalledWith(
      "/tutor/worksheets?classId=12",
    );
  });
});
