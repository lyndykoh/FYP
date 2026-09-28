import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import PrivacyPage from "@/app/privacy/page";
import TermsPage from "@/app/terms/page";

describe("public legal documents", () => {
  it("renders the privacy policy with substantive current-build claims and navigation", () => {
    render(<PrivacyPage />);
    expect(
      screen.getByRole("heading", { name: "Privacy Policy" }),
    ).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "OCR and AI processing" }),
    ).toBeVisible();
    expect(screen.getByText(/password hash/i)).toBeVisible();
    expect(
      screen.getByText(/no marketing or analytics tracker/i),
    ).toBeVisible();
    expect(screen.getByRole("link", { name: "Terms of Use" })).toHaveAttribute(
      "href",
      "/terms",
    );
    expect(
      screen.queryByText(/lorem ipsum|coming soon|tbd|placeholder/i),
    ).not.toBeInTheDocument();
  });

  it("renders terms with upload, AI and tutor-approval rules", () => {
    render(<TermsPage />);
    expect(screen.getByRole("heading", { name: "Terms of Use" })).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Uploads and learning records" }),
    ).toBeVisible();
    expect(screen.getByText(/A tutor must review and approve/i)).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Privacy Policy" }),
    ).toHaveAttribute("href", "/privacy");
    expect(
      screen.getByRole("link", { name: "Back to Lumina Academy" }),
    ).toHaveAttribute("href", "/");
  });
});
