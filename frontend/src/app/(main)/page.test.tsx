import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import MainHomePage from "./page";

const state = vi.hoisted(() => ({
  replace: vi.fn(),
  getBrowserSession: vi.fn(),
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: state.replace }),
}));
vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, getBrowserSession: state.getBrowserSession };
});

describe("Main home redirect", () => {
  beforeEach(() => {
    state.replace.mockReset();
    state.getBrowserSession.mockReset();
  });

  it("redirects each signed-in role to its own dashboard", async () => {
    state.getBrowserSession.mockReturnValue({ role: "STUDENT" });
    const { rerender } = render(<MainHomePage />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "OPENING YOUR WORKSPACE",
    );
    await waitFor(() =>
      expect(state.replace).toHaveBeenCalledWith("/student/dashboard"),
    );
    state.replace.mockReset();
    state.getBrowserSession.mockReturnValue({ role: "TUTOR" });
    rerender(<MainHomePage />);
    await waitFor(() =>
      expect(state.replace).toHaveBeenCalledWith("/tutor/dashboard"),
    );
  });

  it("sends a missing browser session to sign in", async () => {
    state.getBrowserSession.mockReturnValue(null);
    render(<MainHomePage />);
    await waitFor(() => expect(state.replace).toHaveBeenCalledWith("/login"));
  });
});
