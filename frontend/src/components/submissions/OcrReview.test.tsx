import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import OcrReview from "./OcrReview";
describe("OcrReview", () => {
  it("marks low confidence text and saves a correction", async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    render(
      <OcrReview
        pages={[
          {
            pageId: 1,
            extractionId: 2,
            text: "dark",
            confidence: 0.4,
            status: "REQUIRES_REVIEW",
          },
        ]}
        onCorrect={save}
      />,
    );
    const u = userEvent.setup();
    await u.clear(screen.getByLabelText("Page 1 text"));
    await u.type(screen.getByLabelText("Page 1 text"), "corrected");
    await u.click(screen.getByRole("button", { name: "Save correction" }));
    expect(save).toHaveBeenCalledWith(2, "corrected");
    expect(screen.getByRole("status")).toBeVisible();
  });
  it("requires a confirmed question mapping before submitting durable OCR answers", async () => {
    const submit = vi.fn().mockResolvedValue(undefined);
    render(
      <OcrReview
        pages={[
          {
            pageId: 1,
            extractionId: 2,
            text: "answer",
            confidence: 0.95,
            status: "READY",
          },
        ]}
        questions={[{ id: 8, prompt: "Explain conduction." }]}
        onCorrect={vi.fn().mockResolvedValue(undefined)}
        onSubmitForReview={submit}
      />,
    );
    const u = userEvent.setup();
    const button = screen.getByRole("button", {
      name: "Submit for Tutor Review",
    });
    expect(button).toBeEnabled();
    await u.click(button);
    expect(submit).toHaveBeenCalledWith([
      { extractionId: 2, questionBankId: 8 },
    ]);
  });
});
