import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { TutorClass } from "@/services/classes";

import ClassList from "./ClassList";

const classes: TutorClass[] = [
  {
    id: 12,
    tutorId: 7,
    className: "Primary 5 Science",
    subject: "Science",
    level: "Primary 5",
    status: "ACTIVE",
    schedules: [{ dayOfWeek: "MONDAY", startTime: "16:00", endTime: "17:30" }],
  },
  {
    id: 20,
    tutorId: 7,
    className: "Primary 6 Maths",
    subject: "Maths",
    level: "Primary 6",
    status: "INACTIVE",
    schedules: [],
  },
];

describe("ClassList", () => {
  it("shows a loading skeleton before rendering class cards", async () => {
    let resolve!: (value: TutorClass[]) => void;
    const loadClasses = vi.fn(
      () =>
        new Promise<TutorClass[]>((complete) => {
          resolve = complete;
        }),
    );

    render(<ClassList loadClasses={loadClasses} />);

    expect(screen.getByTestId("class-list-skeleton")).toBeVisible();
    resolve(classes);
    expect(
      await screen.findByRole("link", { name: "Open Primary 5 Science" }),
    ).toHaveAttribute("href", "/classes/12");
    expect(
      screen.getByRole("link", { name: "Open Primary 6 Maths" }),
    ).toHaveAttribute("href", "/classes/20");
  });

  it("renders supplied classes, schedules, and a responsive card grid", async () => {
    render(<ClassList loadClasses={async () => classes} />);

    expect(await screen.findByText("Primary 5 Science")).toBeVisible();
    expect(screen.getByText("Monday 16:00–17:30")).toBeVisible();
    expect(screen.getByText("Schedule to be confirmed")).toBeVisible();
    expect(screen.getByTestId("class-grid")).toHaveStyle({
      gridTemplateColumns: "repeat(auto-fill, minmax(310px, 1fr))",
    });
  });

  it("filters by status and searches class name, subject, or level", async () => {
    const user = userEvent.setup();
    render(<ClassList loadClasses={async () => classes} />);

    await screen.findByText("Primary 5 Science");
    await user.click(screen.getByRole("button", { name: "Active" }));
    expect(screen.getByText("Primary 5 Science")).toBeVisible();
    expect(screen.queryByText("Primary 6 Maths")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "All classes" }));
    await user.type(screen.getByLabelText("Search classes"), "maths");
    expect(screen.queryByText("Primary 5 Science")).not.toBeInTheDocument();
    expect(screen.getByText("Primary 6 Maths")).toBeVisible();
  });

  it("gives a retryable error for a failed or invalid class response", async () => {
    const loadClasses = vi
      .fn()
      .mockRejectedValueOnce(
        new Error(
          "The learning service returned an invalid class list. Please try again.",
        ),
      )
      .mockResolvedValueOnce(classes);
    const user = userEvent.setup();
    render(<ClassList loadClasses={loadClasses} />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "invalid class list",
    );
    await user.click(
      screen.getByRole("button", { name: "Retry loading classes" }),
    );
    expect(await screen.findByText("Primary 5 Science")).toBeVisible();
    expect(loadClasses).toHaveBeenCalledTimes(2);
  });

  it("uses an actionable empty state and clears empty filters", async () => {
    const user = userEvent.setup();
    const refresh = vi.fn().mockResolvedValue([]);
    const { rerender } = render(<ClassList loadClasses={refresh} />);

    expect(
      await screen.findByRole("heading", { name: "No classes yet" }),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Refresh classes" }));
    expect(refresh).toHaveBeenCalledTimes(2);

    rerender(<ClassList loadClasses={async () => classes} />);
    await screen.findByText("Primary 5 Science");
    await user.type(screen.getByLabelText("Search classes"), "history");
    expect(
      await screen.findByRole("heading", {
        name: "No classes match these filters",
      }),
    ).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(await screen.findByText("Primary 5 Science")).toBeVisible();
  });

  it("keeps each card link available to keyboard and assistive technology", async () => {
    render(<ClassList loadClasses={async () => classes} />);

    const card = await screen.findByRole("link", {
      name: "Open Primary 5 Science",
    });
    expect(card).not.toHaveAttribute("tabindex", "-1");
    expect(within(card).getByText("Open class summary")).toBeVisible();
  });
});
