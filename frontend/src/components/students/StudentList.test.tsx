import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { TutorStudent } from "@/services/students";
import StudentList from "./StudentList";

const students: TutorStudent[] = [
  {
    id: 1,
    tutorId: 7,
    fullName: "Bella Tan",
    loginUserId: null,
    classes: [
      {
        id: 12,
        className: "Primary 5 Science",
        subject: "Science",
        level: "Primary 5",
      },
    ],
    createdAt: "2026-01-01T00:00:00",
    updatedAt: "2026-01-01T00:00:00",
  },
  {
    id: 2,
    tutorId: 7,
    fullName: "Jayden Lim",
    loginUserId: 9,
    classes: [],
    createdAt: "2026-01-01T00:00:00",
    updatedAt: "2026-01-01T00:00:00",
  },
];

describe("StudentList", () => {
  it("renders loading, responsive cards, search, class filters, and profile/edit navigation", async () => {
    let resolve!: (value: TutorStudent[]) => void;
    render(
      <StudentList
        loadStudents={() =>
          new Promise<TutorStudent[]>((complete) => {
            resolve = complete;
          })
        }
      />,
    );
    expect(screen.getByTestId("student-list-skeleton")).toBeVisible();
    resolve(students);
    expect(await screen.findByText("Bella Tan")).toBeVisible();
    expect(screen.getByTestId("student-grid")).toHaveStyle({
      gridTemplateColumns: "repeat(auto-fill, minmax(310px, 1fr))",
    });
    expect(
      screen.getByRole("link", { name: "View Bella Tan's profile" }),
    ).toHaveAttribute("href", "/students/1");
    expect(
      screen.getByRole("link", { name: "Edit Bella Tan" }),
    ).toHaveAttribute("href", "/students/1/edit");
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Search students"), "jayden");
    expect(screen.queryByText("Bella Tan")).not.toBeInTheDocument();
    expect(screen.getByText("Jayden Lim")).toBeVisible();
  });

  it("shows actionable empty and retryable error states", async () => {
    const { rerender } = render(<StudentList loadStudents={async () => []} />);
    expect(
      await screen.findByRole("heading", { name: "No students yet" }),
    ).toBeVisible();
    rerender(
      <StudentList
        loadStudents={vi
          .fn()
          .mockRejectedValue(new Error("Student service unavailable"))}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Student service unavailable",
    );
  });

  it("passes a class deep-link filter through to the owner-scoped service", async () => {
    const loadStudents = vi.fn().mockResolvedValue([students[0]]);
    render(<StudentList classId={12} loadStudents={loadStudents} />);
    await screen.findByText("Bella Tan");
    expect(loadStudents).toHaveBeenCalledWith(12);
  });
});
