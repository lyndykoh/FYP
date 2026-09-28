import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import AlertList from "./AlertList";
import type { TutorAlert } from "@/services/alerts";

const alert: TutorAlert = {
  id: 3,
  studentId: 7,
  studentName: "Bella Tan",
  type: "WEAK_TOPIC",
  severity: "WARNING",
  status: "OPEN",
  title: "Adaptation needs practice",
  message:
    "62% mastery after 2 approved attempts; the alert threshold is below 70%.",
  createdAt: "2026-08-28T10:00:00",
};
describe("AlertList", () => {
  it("renders a responsive factual alert and removes it after resolve", async () => {
    const resolve = vi.fn().mockResolvedValue({ ...alert, status: "RESOLVED" });
    render(<AlertList loadAlerts={async () => [alert]} resolve={resolve} />);
    expect(
      await screen.findByRole("heading", { name: "Adaptation needs practice" }),
    ).toBeVisible();
    expect(screen.getByText("Bella Tan")).toHaveAttribute(
      "href",
      "/students/7",
    );
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Resolve alert" }));
    expect(resolve).toHaveBeenCalledWith(3);
    expect(
      await screen.findByRole("heading", { name: "No active alerts" }),
    ).toBeVisible();
  });
  it("renders loading, empty, and retryable error states", async () => {
    let resolve!: (alerts: TutorAlert[]) => void;
    const { rerender } = render(
      <AlertList
        loadAlerts={() =>
          new Promise<TutorAlert[]>((done) => {
            resolve = done;
          })
        }
      />,
    );
    expect(screen.getByLabelText("Loading alerts")).toBeVisible();
    resolve([]);
    expect(
      await screen.findByRole("heading", { name: "No active alerts" }),
    ).toBeVisible();
    rerender(
      <AlertList
        loadAlerts={vi
          .fn()
          .mockRejectedValue(new Error("Alert service unavailable"))}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Alert service unavailable",
    );
  });
  it("keeps a recoverable failure visible when dismissal is rejected", async () => {
    render(
      <AlertList
        loadAlerts={async () => [alert]}
        dismiss={async () => {
          throw new Error("Alert cannot be dismissed");
        }}
      />,
    );
    await screen.findByText("Adaptation needs practice");
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Dismiss alert" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Alert cannot be dismissed",
    );
  });
  it("removes an alert after the owning Tutor dismisses it", async () => {
    const dismiss = vi
      .fn()
      .mockResolvedValue({ ...alert, status: "DISMISSED" });
    render(<AlertList loadAlerts={async () => [alert]} dismiss={dismiss} />);
    await screen.findByText("Adaptation needs practice");
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Dismiss alert" }));
    expect(dismiss).toHaveBeenCalledWith(3);
    expect(
      await screen.findByRole("heading", { name: "No active alerts" }),
    ).toBeVisible();
  });
});
