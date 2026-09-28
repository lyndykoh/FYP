import { describe, expect, it } from "vitest";

import {
  isValidEmail,
  isValidFullName,
  isValidLoginPassword,
  isValidRegistrationPassword,
  MAX_EMAIL_LENGTH,
  MAX_PASSWORD_LENGTH,
} from "./validation";

describe("frontend validation", () => {
  it("accepts valid authentication inputs", () => {
    expect(isValidEmail("student@example.test")).toBe(true);
    expect(isValidFullName("Aisha Tan")).toBe(true);
    expect(isValidLoginPassword("any-existing-password")).toBe(true);
    expect(isValidRegistrationPassword("Safe-password-2026!")).toBe(true);
  });

  it("rejects injection-like, malformed, and oversized text", () => {
    expect(
      isValidEmail("student@example.test\r\nBcc: victim@example.test"),
    ).toBe(false);
    expect(isValidEmail("<script>alert(1)</script>@example.test")).toBe(false);
    expect(isValidFullName("<img src=x onerror=alert(1)>")).toBe(false);
    expect(isValidEmail(`${"a".repeat(MAX_EMAIL_LENGTH - 9)}@test.test`)).toBe(
      false,
    );
    expect(isValidLoginPassword("p".repeat(MAX_PASSWORD_LENGTH + 1))).toBe(
      false,
    );
  });

  it("keeps registration password requirements separate from login", () => {
    expect(isValidLoginPassword("short")).toBe(true);
    expect(isValidRegistrationPassword("short")).toBe(false);
    expect(isValidRegistrationPassword("alllowercasepassword1!")).toBe(false);
  });
});
