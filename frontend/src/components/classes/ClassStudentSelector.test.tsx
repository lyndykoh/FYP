import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";

import type { EligibleClassStudent } from "@/services/classes";

import ClassStudentSelector from "./ClassStudentSelector";

const students: EligibleClassStudent[] = [
  {
    loginUserId: 81,
    fullName: "Ada Learner",
    email: "ada@example.com",
    level: "Primary 5",
  },
  {
    loginUserId: 82,
    fullName: "Bella Tan",
    email: "bella@example.com",
    level: null,
  },
];

function renderSelector(
  overrides: Partial<ComponentProps<typeof ClassStudentSelector>> = {},
) {
  const loadEligibleStudents = vi.fn().mockResolvedValue(students);
  const addStudent = vi.fn().mockResolvedValue(undefined);
  const onStudentAdded = vi.fn();
  render(
    <ClassStudentSelector
      classId={12}
      loadEligibleStudents={loadEligibleStudents}
      addStudent={addStudent}
      onStudentAdded={onStudentAdded}
      {...overrides}
    />,
  );
  return { loadEligibleStudents, addStudent, onStudentAdded };
}

async function selectStudent(
  user: ReturnType<typeof userEvent.setup>,
  name: RegExp,
) {
  await user.click(
    screen.getByRole("combobox", { name: "Existing Student account" }),
  );
  await user.click(await screen.findByRole("option", { name }));
}

describe("ClassStudentSelector", () => {
  it("shows a loading state while existing Student accounts are retrieved", () => {
    renderSelector({
      loadEligibleStudents: () => new Promise<EligibleClassStudent[]>(() => {}),
    });
    expect(screen.getByTestId("eligible-students-loading")).toBeVisible();
    expect(
      screen.getByLabelText("Loading existing Student accounts"),
    ).toBeVisible();
  });

  it("lists real eligible Student accounts with their name, email, and available level", async () => {
    const user = userEvent.setup();
    const { loadEligibleStudents } = renderSelector();

    expect(
      await screen.findByRole("combobox", { name: "Existing Student account" }),
    ).toBeVisible();
    await selectStudent(user, /Ada Learner — ada@example\.com · Primary 5/);

    expect(loadEligibleStudents).toHaveBeenCalledWith(12);
    expect(
      screen.getByRole("combobox", { name: "Existing Student account" }),
    ).toHaveTextContent("Ada Learner — ada@example.com · Primary 5");
  });

  it("shows an informative empty state when no Student account can be added", async () => {
    renderSelector({ loadEligibleStudents: vi.fn().mockResolvedValue([]) });
    expect(
      await screen.findByText("No eligible Students available"),
    ).toBeVisible();
    expect(screen.getByText(/already enrolled/i)).toBeVisible();
  });

  it("shows an API error and can retry loading accounts", async () => {
    const user = userEvent.setup();
    const loadEligibleStudents = vi
      .fn()
      .mockRejectedValueOnce(new Error("Student directory is unavailable"))
      .mockResolvedValueOnce(students);
    renderSelector({ loadEligibleStudents });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Student directory is unavailable",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(
      await screen.findByRole("combobox", { name: "Existing Student account" }),
    ).toBeVisible();
    expect(loadEligibleStudents).toHaveBeenCalledTimes(2);
  });

  it("adds the selected existing Student, removes that account from the offered list, and reports success", async () => {
    const user = userEvent.setup();
    const { addStudent, onStudentAdded } = renderSelector();
    await screen.findByRole("combobox", { name: "Existing Student account" });
    await selectStudent(user, /Ada Learner/);
    await user.click(screen.getByRole("button", { name: "Add to class" }));

    await waitFor(() => expect(addStudent).toHaveBeenCalledWith(12, 81));
    expect(onStudentAdded).toHaveBeenCalledWith(students[0]);
    expect(
      await screen.findByText("Ada Learner has been added to this class."),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "View roster" })).toHaveAttribute(
      "href",
      "/classes/12",
    );

    await user.click(
      screen.getByRole("combobox", { name: "Existing Student account" }),
    );
    expect(
      screen.queryByRole("option", { name: /Ada Learner/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /Bella Tan/ })).toBeVisible();
  });
});
