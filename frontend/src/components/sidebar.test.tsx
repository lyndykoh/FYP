import { render, screen, waitFor, within } from "@testing-library/react";
import { createTheme } from "@mui/material/styles";
import { beforeEach, describe, expect, it, vi } from "vitest";

import SidebarContext from "@/context/sidebar-context";
import ViewportContext from "@/context/viewport-context";
import type { AuthRole } from "@/lib/auth";

import Sidebar from "./sidebar";

const testState = vi.hoisted(() => ({
  pathname: "/classes",
  getBrowserSession: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => testState.pathname,
}));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, getBrowserSession: testState.getBrowserSession };
});

function session(role: AuthRole) {
  return {
    token: "token",
    email: `${role.toLowerCase()}@example.com`,
    role,
    expiresAt: Date.now() + 60_000,
  };
}

function renderSidebar(role: AuthRole, expanded = true) {
  testState.getBrowserSession.mockReturnValue(session(role));
  const closeNavigation = vi.fn();
  const handlePageItemClick = vi.fn();
  const handleSetSidebarExpanded = vi.fn((nextExpanded: boolean) =>
    nextExpanded ? vi.fn() : closeNavigation,
  );

  render(
    <ViewportContext.Provider
      value={{
        theme: createTheme(),
        isOverSmViewport: false,
        isOverMdViewport: false,
        isNavigationExpanded: expanded,
        setIsNavigationExpanded: vi.fn(),
        handleToggleHeaderMenu: vi.fn(),
      }}
    >
      <SidebarContext.Provider
        value={{
          expandedItemIds: [],
          handleSetSidebarExpanded,
          handlePageItemClick,
          mini: false,
          isFullyExpanded: true,
          setIsFullyExpanded: vi.fn(),
          isFullyCollapsed: false,
          setIsFullyCollapsed: vi.fn(),
          hasDrawerTransitions: false,
        }}
      >
        <Sidebar />
      </SidebarContext.Provider>
    </ViewportContext.Provider>,
  );

  return { closeNavigation, handlePageItemClick };
}

describe("Sidebar", () => {
  beforeEach(() => {
    testState.pathname = "/classes";
    testState.getBrowserSession.mockReset();
  });

  it("renders Tutor navigation and excludes Student-only destinations", async () => {
    renderSidebar("TUTOR");
    const navigation = screen.getByRole("navigation", {
      name: "Desktop navigation",
      hidden: true,
    });

    await waitFor(() => {
      expect(
        within(navigation).getByRole("link", {
          name: "Dashboard",
          hidden: true,
        }),
      ).toHaveAttribute("href", "/tutor/dashboard");
      expect(
        within(navigation).getByRole("link", { name: "Classes", hidden: true }),
      ).toHaveAttribute("href", "/classes");
    });
    expect(
      within(navigation).getByRole("link", { name: "Students", hidden: true }),
    ).toBeInTheDocument();
    expect(
      within(navigation).getByRole("link", {
        name: "Question Bank",
        hidden: true,
      }),
    ).toHaveAttribute("href", "/questions");
    expect(
      within(navigation).queryByRole("link", {
        name: "Progress",
        hidden: true,
      }),
    ).not.toBeInTheDocument();
  });

  it("renders Student navigation and excludes Tutor-only destinations", async () => {
    testState.pathname = "/progress";
    renderSidebar("STUDENT");
    const navigation = screen.getByRole("navigation", {
      name: "Desktop navigation",
      hidden: true,
    });

    await waitFor(() => {
      expect(
        within(navigation).getByRole("link", {
          name: "Progress",
          hidden: true,
        }),
      ).toHaveAttribute("href", "/progress");
    });
    expect(
      within(navigation).getByRole("link", {
        name: "Worksheets",
        hidden: true,
      }),
    ).toBeInTheDocument();
    expect(
      within(navigation).queryByRole("link", { name: "Classes", hidden: true }),
    ).not.toBeInTheDocument();
  });

  it("marks the active destination and leaves navigation links keyboard-focusable", async () => {
    renderSidebar("TUTOR");
    const navigation = screen.getByRole("navigation", {
      name: "Desktop navigation",
      hidden: true,
    });
    const classesLink = await within(navigation).findByRole("link", {
      name: "Classes",
      hidden: true,
    });
    expect(classesLink).toHaveAttribute("aria-current", "page");
    expect(classesLink).not.toHaveAttribute("tabindex", "-1");
  });
});
