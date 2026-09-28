import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type * as React from "react";
import { describe, expect, it, vi } from "vitest";

import { ClassApiError, type TutorClass } from "@/services/classes";

import ClassForm from "./ClassForm";

const savedClass: TutorClass = {
  id: 12,
  tutorId: 7,
  className: "Primary 5 Science",
  subject: "Science",
  level: "Primary 5",
  status: "ACTIVE",
  schedules: [{ dayOfWeek: "MONDAY", startTime: "16:00", endTime: "17:30" }],
};

function renderForm(
  overrides: Partial<React.ComponentProps<typeof ClassForm>> = {},
) {
  const submitClass = vi.fn().mockResolvedValue(savedClass);
  const onComplete = vi.fn();
  render(
    <ClassForm
      mode="create"
      submitClass={submitClass}
      onComplete={onComplete}
      {...overrides}
    />,
  );
  return { submitClass, onComplete };
}

async function completeRequiredFields(
  user: ReturnType<typeof userEvent.setup>,
) {
  await user.type(screen.getByLabelText("Class name"), " Primary 5 Science ");
  await user.type(screen.getByLabelText("Subject"), " Science ");
  await user.type(screen.getByLabelText("Level"), " Primary 5 ");
}

describe("ClassForm", () => {
  it("submits a valid trimmed class request and completes the create flow", async () => {
    const user = userEvent.setup();
    const { submitClass, onComplete } = renderForm();

    await completeRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Create class" }));

    await waitFor(() =>
      expect(submitClass).toHaveBeenCalledWith({
        className: "Primary 5 Science",
        subject: "Science",
        level: "Primary 5",
        status: "ACTIVE",
        schedules: [],
      }),
    );
    expect(onComplete).toHaveBeenCalledWith(savedClass);
  });

  it("validates required values before calling the mutation client", async () => {
    const user = userEvent.setup();
    const { submitClass } = renderForm();

    await user.click(screen.getByRole("button", { name: "Create class" }));

    expect(await screen.findByText("Class name is required.")).toBeVisible();
    expect(screen.getByText("Subject is required.")).toBeVisible();
    expect(screen.getByText("Level is required.")).toBeVisible();
    expect(submitClass).not.toHaveBeenCalled();
  });

  it("rejects an invalid or duplicate schedule before submission", async () => {
    const user = userEvent.setup();
    const { submitClass } = renderForm();

    await completeRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Add schedule time" }));
    fireEvent.change(screen.getByLabelText("Schedule 1 start time"), {
      target: { value: "17:30" },
    });
    fireEvent.change(screen.getByLabelText("Schedule 1 end time"), {
      target: { value: "16:00" },
    });
    await user.click(screen.getByRole("button", { name: "Create class" }));
    expect(
      await screen.findByText("End time must be after the start time."),
    ).toBeVisible();
    expect(submitClass).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Schedule 1 start time"), {
      target: { value: "16:00" },
    });
    fireEvent.change(screen.getByLabelText("Schedule 1 end time"), {
      target: { value: "17:30" },
    });
    await user.click(screen.getByRole("button", { name: "Add schedule time" }));
    fireEvent.change(screen.getByLabelText("Schedule 2 start time"), {
      target: { value: "16:00" },
    });
    fireEvent.change(screen.getByLabelText("Schedule 2 end time"), {
      target: { value: "17:30" },
    });
    await user.click(screen.getByRole("button", { name: "Create class" }));

    expect(
      await screen.findByText("This schedule time is already listed."),
    ).toBeVisible();
    expect(submitClass).not.toHaveBeenCalled();
  });

  it("shows field and request errors returned by the server", async () => {
    const user = userEvent.setup();
    const submitClass = vi
      .fn()
      .mockRejectedValue(
        new ClassApiError(
          "A class named 'Primary 5 Science' already exists for this tutor",
          409,
          { className: "Choose a different class name." },
        ),
      );
    renderForm({ submitClass });

    await completeRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Create class" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "already exists",
    );
    expect(screen.getByText("Choose a different class name.")).toBeVisible();
  });

  it("keeps only one mutation in flight and gives the submit button a loading state", async () => {
    const user = userEvent.setup();
    let resolve!: (value: TutorClass) => void;
    const submitClass = vi.fn(
      () =>
        new Promise<TutorClass>((complete) => {
          resolve = complete;
        }),
    );
    const { onComplete } = renderForm({ submitClass });

    await completeRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Create class" }));
    const pendingButton = screen.getByRole("button", {
      name: "Creating class…",
    });
    expect(pendingButton).toBeDisabled();
    fireEvent.click(pendingButton);
    expect(submitClass).toHaveBeenCalledOnce();

    resolve(savedClass);
    await waitFor(() => expect(onComplete).toHaveBeenCalledWith(savedClass));
  });

  it("shows a recoverable wrong-owner error returned while editing", async () => {
    const user = userEvent.setup();
    const submitClass = vi
      .fn()
      .mockRejectedValue(
        new ClassApiError("Class 12 was not found for this tutor", 404),
      );
    renderForm({ mode: "edit", initialClass: savedClass, submitClass });

    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "not found for this tutor",
    );
    expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled();
  });
});
