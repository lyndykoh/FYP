import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import PageReview from "./PageReview";
const pages = [
  {
    id: "one",
    file: new File(["a"], "one.jpg", { type: "image/jpeg" }),
    previewUrl: "blob:one",
    rotation: 0 as const,
    warning: null,
  },
  {
    id: "two",
    file: new File(["b"], "two.jpg", { type: "image/jpeg" }),
    previewUrl: "blob:two",
    rotation: 0 as const,
    warning: "This image may be hard to read.",
  },
];
describe("PageReview", () => {
  it("offers reorder, rotate, remove and replacement controls", async () => {
    const move = vi.fn(),
      rotate = vi.fn(),
      remove = vi.fn(),
      replace = vi.fn();
    render(
      <PageReview
        pages={pages}
        onMove={move}
        onRotate={rotate}
        onRemove={remove}
        onReplace={replace}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "Move down" })[0]);
    await user.click(screen.getAllByRole("button", { name: "Rotate" })[0]);
    await user.click(screen.getAllByRole("button", { name: "Remove" })[0]);
    expect(move).toHaveBeenCalledWith("one", 1);
    expect(rotate).toHaveBeenCalledWith("one");
    expect(remove).toHaveBeenCalledWith("one");
    expect(screen.getByText(/hard to read/i)).toBeVisible();
  });
});
