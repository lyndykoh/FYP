import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchTutorAlerts,
  parseTutorAlerts,
  resolveTutorAlert,
} from "./alerts";
const sample = {
  id: 1,
  studentId: 2,
  studentName: "Bella Tan",
  type: "WEAK_TOPIC",
  severity: "WARNING",
  status: "OPEN",
  title: "Needs practice",
  message: "Below threshold.",
  createdAt: "2026-08-28T12:00:00",
};
describe("alerts client", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });
  it("validates responses and sends owner-scoped action requests", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify([sample]), { status: 200 }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...sample, status: "RESOLVED" }), {
          status: 200,
        }),
      );
    await expect(fetchTutorAlerts()).resolves.toEqual([sample]);
    await expect(resolveTutorAlert(1)).resolves.toMatchObject({
      status: "RESOLVED",
    });
    expect(fetch).toHaveBeenLastCalledWith(
      expect.stringContaining("/alerts/1/resolve"),
      expect.objectContaining({ method: "POST" }),
    );
  });
  it("rejects invalid payloads and invalid IDs", async () => {
    expect(() => parseTutorAlerts([{ id: 1 }])).toThrow(/invalid/i);
    await expect(resolveTutorAlert(0)).rejects.toThrow(/invalid/i);
  });
});
