import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Login from "./login/login";
import Signup from "./signup/signup";

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
}));

function authToken(role: "TUTOR" | "STUDENT", email: string) {
  const encode = (value: object) =>
    btoa(JSON.stringify(value))
      .replace(/=/g, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
  return `${encode({ alg: "HS256" })}.${encode({
    sub: email,
    role,
    userId: 101,
    exp: Math.floor(Date.now() / 1000) + 3600,
  })}.signature`;
}

describe("authentication form integration", () => {
  beforeEach(() => {
    navigation.push.mockReset();
    navigation.replace.mockReset();
    document.cookie = "auth_token=; Max-Age=0; path=/";
  });

  it("submits login credentials through the API client and establishes the browser session", async () => {
    const token = authToken("TUTOR", "tutor@example.com");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          token,
          email: "tutor@example.com",
          fullName: "Test Tutor",
          role: "TUTOR",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<Login />);
    await user.type(screen.getByLabelText("Email"), "tutor@example.com");
    await user.type(screen.getByLabelText("Password"), "password-123");
    await user.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8081/api/auth/login",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          email: "tutor@example.com",
          password: "password-123",
        }),
      }),
    );
    expect(localStorage.getItem("jwt_token")).toBe(token);
    expect(document.cookie).toContain(`auth_token=${token}`);
    expect(navigation.replace).toHaveBeenCalledWith("/tutor/dashboard");
  });

  it("renders a backend login rejection without creating a session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "Invalid email or password" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    const user = userEvent.setup();

    render(<Login />);
    await user.type(screen.getByLabelText("Email"), "tutor@example.com");
    await user.type(screen.getByLabelText("Password"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "Sign In" }));

    expect(await screen.findByText("Invalid email or password")).toBeVisible();
    expect(localStorage.getItem("jwt_token")).toBeNull();
    expect(navigation.replace).not.toHaveBeenCalled();
  });

  it("renders a recoverable network error and prevents duplicate login requests", async () => {
    let rejectRequest!: (reason: Error) => void;
    const fetchMock = vi.fn().mockImplementation(
      () =>
        new Promise<Response>((_resolve, reject) => {
          rejectRequest = reject;
        }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<Login />);
    await user.type(screen.getByLabelText("Email"), "tutor@example.com");
    await user.type(screen.getByLabelText("Password"), "password-123");
    await user.click(screen.getByRole("button", { name: "Sign In" }));

    const pendingButton = screen.getByRole("button", { name: "Signing in…" });
    expect(pendingButton).toBeDisabled();
    fireEvent.click(pendingButton);
    expect(fetchMock).toHaveBeenCalledOnce();

    rejectRequest(new TypeError("Failed to fetch"));
    expect(
      await screen.findByText(
        "Unable to reach the authentication service. Please try again.",
      ),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "Sign In" })).toBeEnabled();
  });

  it("registers only the Student role and redirects to the Student home", async () => {
    const token = authToken("STUDENT", "student@example.com");
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          token,
          email: "student@example.com",
          fullName: "Test Student",
          role: "STUDENT",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<Signup />);
    await user.type(screen.getByLabelText("Full name"), "Test Student");
    await user.type(screen.getByLabelText("Email"), "student@example.com");
    await user.type(screen.getByLabelText("Password"), "StrongPassword1!");
    await user.click(screen.getByRole("button", { name: "Sign Up" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8081/api/auth/register",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          email: "student@example.com",
          password: "StrongPassword1!",
          fullName: "Test Student",
          role: "STUDENT",
        }),
      }),
    );
    expect(localStorage.getItem("jwt_token")).toBe(token);
    expect(navigation.replace).toHaveBeenCalledWith("/student/dashboard");
    expect(
      screen.queryByRole("button", { name: "Tutor" }),
    ).not.toBeInTheDocument();
  });

  it("blocks an empty registration form before it reaches the API", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    render(<Signup />);
    fireEvent.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(await screen.findByText("Please enter a valid name")).toBeVisible();
    expect(
      screen.getByText("Please enter a valid email address"),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Use at least 12 characters with uppercase, lowercase, a number and a symbol",
      ),
    ).toBeVisible();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("handles a registration network failure without an unhandled rejection", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("Failed to fetch")),
    );
    const user = userEvent.setup();

    render(<Signup />);
    await user.type(screen.getByLabelText("Full name"), "Test Student");
    await user.type(screen.getByLabelText("Email"), "student@example.com");
    await user.type(screen.getByLabelText("Password"), "StrongPassword1!");
    await user.click(screen.getByRole("button", { name: "Sign Up" }));

    expect(
      await screen.findByText(
        "Unable to reach the authentication service. Please try again.",
      ),
    ).toBeVisible();
  });
});
