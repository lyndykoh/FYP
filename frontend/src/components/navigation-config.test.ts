import { describe, expect, it } from "vitest";

import {
  getNavigationItems,
  getWorkspaceLabel,
  isNavigationItemSelected,
} from "./navigation-config";

describe("role-aware navigation configuration", () => {
  it("returns only Tutor destinations for a Tutor", () => {
    const items = getNavigationItems("TUTOR");

    expect(items.map(({ title, href }) => ({ title, href }))).toEqual([
      { title: "Dashboard", href: "/tutor/dashboard" },
      { title: "Classes", href: "/classes" },
      { title: "Students", href: "/students" },
      { title: "Worksheets", href: "/tutor/worksheets" },
      { title: "Reviews", href: "/tutor/reviews" },
      { title: "Question Bank", href: "/questions" },
      { title: "Alerts", href: "/tutor/alerts" },
      { title: "Upload", href: "/upload" },
    ]);
    expect(items.some((item) => item.href === "/progress")).toBe(false);
    expect(getWorkspaceLabel("TUTOR")).toBe("Tutor Workspace");
  });

  it("returns only Student destinations for a Student", () => {
    const items = getNavigationItems("STUDENT");

    expect(items.map((item) => item.href)).toEqual([
      "/student/dashboard",
      "/worksheets",
      "/upload",
      "/mistakes",
      "/progress",
      "/topics",
      "/subject-profile",
      "/profile",
    ]);
    expect(items.some((item) => item.href === "/classes")).toBe(false);
    expect(getWorkspaceLabel("STUDENT")).toBe("Student Workspace");
  });

  it("selects exact and nested destinations without selecting Home for every path", () => {
    const home = getNavigationItems("STUDENT")[0];
    const worksheets = getNavigationItems("STUDENT")[1];

    expect(isNavigationItemSelected(home, "/student/dashboard")).toBe(true);
    expect(isNavigationItemSelected(home, "/worksheets")).toBe(false);
    expect(isNavigationItemSelected(worksheets, "/worksheets")).toBe(true);
    expect(isNavigationItemSelected(worksheets, "/worksheets/42")).toBe(true);
    expect(isNavigationItemSelected(worksheets, "/progress")).toBe(false);
  });

  it("keeps the Tutor dashboard active for its nested routes", () => {
    const dashboard = getNavigationItems("TUTOR")[0];

    expect(isNavigationItemSelected(dashboard, "/tutor/dashboard")).toBe(true);
    expect(isNavigationItemSelected(dashboard, "/tutor/dashboard/today")).toBe(
      true,
    );
    expect(isNavigationItemSelected(dashboard, "/classes")).toBe(false);
  });
});
