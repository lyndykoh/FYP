import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createTheme } from "@mui/material/styles";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ViewportContext from "@/context/viewport-context";

import Topbar from "./topbar";

const testState = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  getBrowserSession: vi.fn(),
  clearAuthSession: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: testState.push, replace: testState.replace }),
}));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return {
    ...actual,
    getBrowserSession: testState.getBrowserSession,
    clearAuthSession: testState.clearAuthSession,
  };
});

function renderTopbar() {
  const handleToggleHeaderMenu = vi.fn();
  render(
    <ViewportContext.Provider
      value={{
        theme: createTheme(),
        isOverSmViewport: false,
        isOverMdViewport: false,
        isNavigationExpanded: false,
        setIsNavigationExpanded: vi.fn(),
        handleToggleHeaderMenu,
      }}
    >
      <Topbar />
    </ViewportContext.Provider>,
  );
  return { handleToggleHeaderMenu };
}

describe("Topbar", () => {
  beforeEach(() => {
    testState.push.mockReset();
    testState.replace.mockReset();
    testState.getBrowserSession.mockReset();
    testState.clearAuthSession.mockReset();
    testState.getBrowserSession.mockReturnValue({
      token: "token",
      email: "tutor@example.com",
      role: "TUTOR",
      expiresAt: Date.now() + 60_000,
    });
  });

  it("uses the light tutor shell without a color-mode control", () => {
    renderTopbar();

    expect(
      screen.getByRole("link", { name: "Skip to content" }),
    ).toHaveAttribute("href", "#main-content");
    expect(screen.queryByText("Color mode")).not.toBeInTheDocument();
  });

  it("opens the account menu by keyboard and displays the signed-in identity", async () => {
    const user = userEvent.setup();
    renderTopbar();
    await waitFor(() =>
      expect(testState.getBrowserSession).toHaveBeenCalledOnce(),
    );

    const accountButton = screen.getByRole("button", {
      name: "Open account menu",
    });
    accountButton.focus();
    await user.keyboard("{Enter}");

    expect(
      await screen.findByRole("menu", { name: "Account options" }),
    ).toBeVisible();
    expect(screen.getByText("tutor@example.com")).toBeVisible();
    expect(
      screen.queryByRole("menuitem", { name: "Profile" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Logout" })).toBeVisible();
  });

  it("clears the browser session and redirects when Logout is selected", async () => {
    const user = userEvent.setup();
    renderTopbar();
    await user.click(screen.getByRole("button", { name: "Open account menu" }));
    await user.click(await screen.findByRole("menuitem", { name: "Logout" }));

    expect(testState.clearAuthSession).toHaveBeenCalledOnce();
    expect(testState.replace).toHaveBeenCalledWith("/login");
  });
});
