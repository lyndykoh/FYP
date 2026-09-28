import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TutorClass } from "@/services/classes";
import {
  StudentApiError,
  type AvailableStudentAccount,
  type TutorStudent,
} from "@/services/students";
import StudentForm from "./StudentForm";

const classes: TutorClass[] = [
  {
    id: 12,
    tutorId: 7,
    className: "Primary 5 Science",
    subject: "Science",
    level: "Primary 5",
    status: "ACTIVE",
    schedules: [],
  },
];
const accounts: AvailableStudentAccount[] = [
  {
    id: 71,
    fullName: "Bella Tan",
    email: "bella.tan@email.com",
    level: "Primary 6",
  },
  {
    id: 72,
    fullName: "Jayden Lim",
    email: "jayden.lim@email.com",
    level: null,
  },
];
const student: TutorStudent = {
  id: 1,
  tutorId: 7,
  fullName: "Bella Tan",
  loginUserId: 71,
  classes: [],
  createdAt: "2026-01-01T00:00:00",
  updatedAt: "2026-01-01T00:00:00",
};

function matchingAccounts(search: string) {
  const query = search.toLowerCase();
  return Promise.resolve(
    accounts.filter(
      (account) =>
        !query ||
        account.fullName.toLowerCase().includes(query) ||
        account.email.toLowerCase().includes(query),
    ),
  );
}

describe("StudentForm", () => {
  beforeEach(() => vi.clearAllMocks());

  it("uses a searchable existing-Student selector and submits its internal ID", async () => {
    const user = userEvent.setup();
    const submitStudent = vi.fn().mockResolvedValue(student);
    const loadStudentAccounts = vi.fn(matchingAccounts);
    render(
      <StudentForm
        mode="create"
        submitStudent={submitStudent}
        onComplete={vi.fn()}
        loadClasses={async () => classes}
        loadStudentAccounts={loadStudentAccounts}
      />,
    );

    expect(screen.queryByLabelText("Student login ID")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Student name")).not.toBeInTheDocument();
    const selector = await screen.findByRole("combobox", { name: /student/i });
    await user.click(selector);
    await user.type(selector, "Bell");
    await waitFor(() =>
      expect(loadStudentAccounts).toHaveBeenCalledWith("Bell"),
    );
    expect(
      await screen.findByText("bella.tan@email.com", { exact: false }),
    ).toBeVisible();
    await user.click(screen.getByText("Bella Tan", { exact: true }));
    await user.click(screen.getByRole("button", { name: /Primary 5 Science/ }));
    await user.click(screen.getByRole("button", { name: "Create student" }));

    await waitFor(() =>
      expect(submitStudent).toHaveBeenCalledWith({
        fullName: "Bella Tan",
        loginUserId: 71,
        classIds: [12],
      }),
    );
  });

  it("matches an email fragment and prevents submission until a Student is selected", async () => {
    const user = userEvent.setup();
    const submitStudent = vi.fn().mockResolvedValue(student);
    render(
      <StudentForm
        mode="create"
        submitStudent={submitStudent}
        onComplete={vi.fn()}
        loadClasses={async () => []}
        loadStudentAccounts={matchingAccounts}
      />,
    );
    const selector = await screen.findByRole("combobox", { name: /student/i });
    await user.click(screen.getByRole("button", { name: "Create student" }));
    expect(
      await screen.findByText("Select an existing Student account."),
    ).toBeVisible();
    expect(submitStudent).not.toHaveBeenCalled();
    await user.type(selector, "jayden.lim@");
    expect(
      await screen.findByText("jayden.lim@email.com", { exact: false }),
    ).toBeVisible();
    await user.click(screen.getByText("Jayden Lim", { exact: true }));
    await user.click(screen.getByRole("button", { name: "Create student" }));
    await waitFor(() =>
      expect(submitStudent).toHaveBeenCalledWith({
        fullName: "Jayden Lim",
        loginUserId: 72,
        classIds: [],
      }),
    );
  });

  it("shows loading, empty, and retryable account-directory errors without losing the form", async () => {
    const user = userEvent.setup();
    let resolve!: (value: AvailableStudentAccount[]) => void;
    const delayed = vi.fn(
      () =>
        new Promise<AvailableStudentAccount[]>((complete) => {
          resolve = complete;
        }),
    );
    const { rerender } = render(
      <StudentForm
        mode="create"
        submitStudent={vi.fn()}
        onComplete={vi.fn()}
        loadClasses={async () => classes}
        loadStudentAccounts={delayed}
      />,
    );
    expect(
      await screen.findByLabelText("Loading Student accounts"),
    ).toBeVisible();
    await waitFor(() => expect(delayed).toHaveBeenCalled());
    resolve([]);
    expect(
      await screen.findByText("No available students found."),
    ).toBeVisible();

    const unavailable = vi
      .fn()
      .mockRejectedValueOnce(new Error("Directory unavailable"))
      .mockResolvedValueOnce(accounts);
    rerender(
      <StudentForm
        mode="create"
        submitStudent={vi.fn()}
        onComplete={vi.fn()}
        loadClasses={async () => classes}
        loadStudentAccounts={unavailable}
      />,
    );
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Directory unavailable",
      ),
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));
    await waitFor(() => expect(unavailable).toHaveBeenCalledTimes(2));
    await user.click(screen.getByRole("combobox", { name: /student/i }));
    expect(await screen.findByText("Bella Tan", { exact: true })).toBeVisible();
    expect(screen.getByText("Primary 5 Science")).toBeVisible();
  });

  it("keeps the latest directory search when an older response arrives afterwards", async () => {
    const user = userEvent.setup();
    const requests = new Map<
      string,
      (value: AvailableStudentAccount[]) => void
    >();
    const loadStudentAccounts = vi.fn(
      (search: string) =>
        new Promise<AvailableStudentAccount[]>((resolve) =>
          requests.set(search, resolve),
        ),
    );
    render(
      <StudentForm
        mode="create"
        submitStudent={vi.fn()}
        onComplete={vi.fn()}
        loadClasses={async () => []}
        loadStudentAccounts={loadStudentAccounts}
      />,
    );

    const selector = await screen.findByRole("combobox", { name: /student/i });
    await waitFor(() => expect(loadStudentAccounts).toHaveBeenCalledWith(""));
    await user.type(selector, "Jay");
    await waitFor(() =>
      expect(loadStudentAccounts).toHaveBeenCalledWith("Jay"),
    );
    requests.get("Jay")?.([accounts[1]]);
    expect(
      await screen.findByText("Jayden Lim", { exact: true }),
    ).toBeVisible();
    requests.get("")?.([]);
    await waitFor(() =>
      expect(screen.getByText("Jayden Lim", { exact: true })).toBeVisible(),
    );
  });

  it("maps server-side invalid or duplicate account selection errors to the selector", async () => {
    const user = userEvent.setup();
    render(
      <StudentForm
        mode="create"
        submitStudent={vi
          .fn()
          .mockRejectedValue(
            new StudentApiError("Student account is already linked", 409, {
              loginUserId: "Choose another Student account.",
            }),
          )}
        onComplete={vi.fn()}
        loadClasses={async () => []}
        loadStudentAccounts={matchingAccounts}
      />,
    );
    const selector = await screen.findByRole("combobox", { name: /student/i });
    await user.click(selector);
    await user.click(await screen.findByText("Bella Tan", { exact: true }));
    await user.click(screen.getByRole("button", { name: "Create student" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already linked",
    );
    expect(screen.getByText("Choose another Student account.")).toBeVisible();
  });
});
