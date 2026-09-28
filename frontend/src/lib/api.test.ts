import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiRequest, getErrorMessage, register } from "./api";

describe("apiRequest", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  it("adds the bearer token to protected requests", async () => {
    localStorage.setItem("jwt_token", "test-token");
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await apiRequest("/api/students", { method: "GET" });

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8081/api/students",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
          "Content-Type": "application/json",
        }),
      }),
    );
  });

  it("does not send a token to authentication routes", async () => {
    localStorage.setItem("jwt_token", "test-token");
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await apiRequest("/api/auth/login", { method: "POST" });

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8081/api/auth/login",
      expect.objectContaining({
        headers: expect.not.objectContaining({
          Authorization: expect.anything(),
        }),
      }),
    );
  });

  it("preserves caller-supplied headers", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await apiRequest("/api/students", {
      headers: { "X-Request-ID": "request-1" },
    });

    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8081/api/students",
      expect.objectContaining({
        headers: expect.objectContaining({ "X-Request-ID": "request-1" }),
      }),
    );
  });
});

describe("getErrorMessage", () => {
  it("returns a JSON message when supplied", async () => {
    const response = new Response(
      JSON.stringify({ message: "Invalid request" }),
      {
        status: 400,
        headers: { "content-type": "application/json" },
      },
    );

    await expect(getErrorMessage(response)).resolves.toBe("Invalid request");
  });

  it("returns a JSON error when a message is absent", async () => {
    const response = new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: { "content-type": "application/json" },
    });

    await expect(getErrorMessage(response)).resolves.toBe("Unauthorized");
  });

  it("falls back to plain text", async () => {
    const response = new Response("Service unavailable", { status: 503 });

    await expect(getErrorMessage(response)).resolves.toBe(
      "Service unavailable",
    );
  });

  it("falls back to the HTTP status for an empty body", async () => {
    const response = new Response("", { status: 500 });

    await expect(getErrorMessage(response)).resolves.toBe(
      "Request failed with status 500",
    );
  });

  it("returns malformed JSON as text without consuming the response twice", async () => {
    const response = new Response("not-json", {
      status: 502,
      headers: { "content-type": "application/json" },
    });

    await expect(getErrorMessage(response)).resolves.toBe("not-json");
  });
});

describe("register", () => {
  it("stores the token returned by the registration endpoint", async () => {
    const expires = Math.floor(Date.now() / 1000) + 3600;
    const token = `${btoa(JSON.stringify({ alg: "HS256" }))}.${btoa(
      JSON.stringify({
        sub: "student@example.com",
        role: "STUDENT",
        exp: expires,
      }),
    )}.signature`;
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            token,
            email: "student@example.com",
            role: "STUDENT",
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
      ),
    );

    const result = await register(
      "student@example.com",
      "StrongPassword1!",
      "Test Student",
    );

    expect(result.token).toBe(token);
    expect(localStorage.getItem("jwt_token")).toBe(token);
    expect(fetch).toHaveBeenCalledWith(
      "http://localhost:8081/api/auth/register",
      expect.objectContaining({
        body: JSON.stringify({
          email: "student@example.com",
          password: "StrongPassword1!",
          fullName: "Test Student",
          role: "STUDENT",
        }),
      }),
    );
  });
});
