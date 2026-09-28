import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import AuthGuard from "./auth-guard";

const testState = vi.hoisted(() => ({
  pathname: "/progress",
  replace: vi.fn(),
  getBrowserSession: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => testState.pathname,
  useRouter: () => ({ replace: testState.replace }),
}));

vi.mock("@/lib/auth", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/auth")>();
  return { ...actual, getBrowserSession: testState.getBrowserSession };
});

function studentSession() {
  return {
    token: "token",
    email: "student@example.com",
    role: "STUDENT" as const,
    expiresAt: Date.now() + 60_000,
  };
}

function tutorSession() {
  return {
    token: "token",
    email: "tutor@example.com",
    role: "TUTOR" as const,
    expiresAt: Date.now() + 60_000,
  };
}

describe("AuthGuard", () => {
  beforeEach(() => {
    testState.pathname = "/progress";
    testState.replace.mockReset();
    testState.getBrowserSession.mockReset();
  });

  it("shows a protected loading state before authorizing an allowed route", async () => {
    testState.getBrowserSession.mockReturnValue(studentSession());

    render(
      <AuthGuard>
        <div>Protected progress</div>
      </AuthGuard>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "Checking your access",
    );
    expect(await screen.findByText("Protected progress")).toBeVisible();
    expect(testState.replace).not.toHaveBeenCalled();
  });

  it("shows an unauthenticated state and redirects a missing session to login", async () => {
    testState.getBrowserSession.mockReturnValue(null);

    render(
      <AuthGuard>
        <div>Protected progress</div>
      </AuthGuard>,
    );

    expect(
      await screen.findByText(/session is missing or has expired/i),
    ).toBeVisible();
    await waitFor(() =>
      expect(testState.replace).toHaveBeenCalledWith("/login"),
    );
    expect(screen.queryByText("Protected progress")).not.toBeInTheDocument();
  });

  it("authorizes the Tutor dashboard for a Tutor", async () => {
    testState.pathname = "/tutor/dashboard";
    testState.getBrowserSession.mockReturnValue(tutorSession());

    render(
      <AuthGuard>
        <div>Tutor dashboard</div>
      </AuthGuard>,
    );

    expect(await screen.findByText("Tutor dashboard")).toBeVisible();
    expect(testState.replace).not.toHaveBeenCalled();
  });

  it("shows an unauthorized state and redirects cross-role navigation", async () => {
    testState.pathname = "/classes";
    testState.getBrowserSession.mockReturnValue(studentSession());

    render(
      <AuthGuard>
        <div>Tutor classes</div>
      </AuthGuard>,
    );

    expect(await screen.findByText(/do not have permission/i)).toBeVisible();
    await waitFor(() =>
      expect(testState.replace).toHaveBeenCalledWith("/student/dashboard"),
    );
    expect(screen.queryByText("Tutor classes")).not.toBeInTheDocument();
  });

  it("returns to loading instead of exposing new route content while a route change is checked", async () => {
    testState.getBrowserSession.mockReturnValue(studentSession());
    const { rerender } = render(
      <AuthGuard>
        <div>Protected progress</div>
      </AuthGuard>,
    );
    expect(await screen.findByText("Protected progress")).toBeVisible();

    testState.pathname = "/classes";
    rerender(
      <AuthGuard>
        <div>Tutor classes</div>
      </AuthGuard>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "Checking your access",
    );
    expect(screen.queryByText("Tutor classes")).not.toBeInTheDocument();
    expect(await screen.findByText(/do not have permission/i)).toBeVisible();
  });
});
