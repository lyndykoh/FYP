import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { TutorNote } from "@/services/students";
import TutorNotes from "./TutorNotes";

const older: TutorNote = {
  id: 2,
  studentId: 31,
  content: "Earlier observation",
  createdAt: "2026-09-01T10:00:00",
  updatedAt: "2026-09-01T10:00:00",
};
const newer: TutorNote = {
  id: 4,
  studentId: 31,
  content: "Latest observation",
  createdAt: "2026-09-02T10:00:00",
  updatedAt: "2026-09-02T10:00:00",
};

describe("TutorNotes", () => {
  it("shows loading and an actionable empty state", async () => {
    let resolve!: (notes: TutorNote[]) => void;
    render(
      <TutorNotes
        studentId={31}
        loadNotes={() =>
          new Promise<TutorNote[]>((complete) => {
            resolve = complete;
          })
        }
      />,
    );
    expect(screen.getByLabelText("Loading private tutor notes")).toBeVisible();
    await waitFor(() => expect(resolve).toBeTypeOf("function"));
    resolve([]);
    expect(await screen.findByText("No private notes yet")).toBeVisible();
  });

  it("orders notes by newest update and creates, edits, and deletes without duplicate submits", async () => {
    const user = userEvent.setup();
    const createNote = vi.fn(
      async (
        _studentId: number,
        request: { content: string },
      ): Promise<TutorNote> => ({
        ...newer,
        id: 7,
        content: request.content,
        updatedAt: "2026-09-03T10:00:00",
      }),
    );
    const updateNote = vi.fn(
      async (
        _studentId: number,
        noteId: number,
        request: { content: string },
      ): Promise<TutorNote> => ({
        ...newer,
        id: noteId,
        content: request.content,
        updatedAt: "2026-09-04T10:00:00",
      }),
    );
    const removeNote = vi.fn(async () => undefined);
    const { container } = render(
      <TutorNotes
        studentId={31}
        loadNotes={async () => [older, newer]}
        createNote={createNote}
        updateNote={updateNote}
        removeNote={removeNote}
      />,
    );

    await screen.findByText("Latest observation");
    const rows = container.querySelectorAll("li");
    expect(rows[0]).toHaveTextContent("Latest observation");
    expect(rows[1]).toHaveTextContent("Earlier observation");

    await user.type(
      screen.getByLabelText("Add a private note"),
      "Check home revision",
    );
    await user.click(screen.getByRole("button", { name: "Add note" }));
    expect(await screen.findByText("Check home revision")).toBeVisible();
    expect(createNote).toHaveBeenCalledTimes(1);

    await user.click(screen.getAllByRole("button", { name: "Edit" })[0]);
    const editInput = screen.getByLabelText("Edit private note");
    await user.clear(editInput);
    await user.type(editInput, "Updated observation");
    await user.click(screen.getByRole("button", { name: "Save note" }));
    expect(await screen.findByText("Updated observation")).toBeVisible();
    expect(updateNote).toHaveBeenCalledTimes(1);

    await user.click(screen.getAllByRole("button", { name: "Delete" })[0]);
    expect(removeNote).toHaveBeenCalledTimes(1);
  });

  it("renders hostile note text literally instead of executing or parsing markup", async () => {
    const payload =
      '<img src=x onerror="window.__noteXss = true"> <script>window.__noteXss = true</script>';
    const { container } = render(
      <TutorNotes
        studentId={31}
        loadNotes={async () => [{ ...newer, content: payload }]}
      />,
    );
    expect(await screen.findByText(payload)).toBeVisible();
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("script")).toBeNull();
    expect(
      (window as Window & { __noteXss?: boolean }).__noteXss,
    ).toBeUndefined();
  });

  it("gives a retryable error for server failures", async () => {
    const user = userEvent.setup();
    const loadNotes = vi
      .fn()
      .mockRejectedValueOnce(
        new Error("Tutor notes are temporarily unavailable"),
      )
      .mockResolvedValueOnce([newer]);
    render(<TutorNotes studentId={31} loadNotes={loadNotes} />);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "temporarily unavailable",
    );
    await user.click(screen.getByRole("button", { name: "Retry" }));
    expect(await screen.findByText("Latest observation")).toBeVisible();
    expect(loadNotes).toHaveBeenCalledTimes(2);
  });
});
