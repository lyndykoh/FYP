import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const route = vi.hoisted(() => ({ classId: "20" }));

vi.mock("next/navigation", () => ({
  useParams: () => route,
}));

vi.mock("@/components/classes/ClassDetail", () => ({
  default: ({ classId }: { classId: number }) => (
    <output data-testid="class-detail-route">{classId}</output>
  ),
}));

import ClassDetailPage from "./page";

describe("ClassDetailPage", () => {
  it("passes the class ID from the route to the real Class Detail loader", () => {
    render(<ClassDetailPage />);
    expect(screen.getByTestId("class-detail-route")).toHaveTextContent("20");
  });
});
