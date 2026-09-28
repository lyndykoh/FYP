import { defineConfig, devices } from "@playwright/test";

/**
 * The E2E suite targets a Compose-managed stack by default.  Set E2E_BASE_URL
 * when testing a different local deployment; this is deliberately never a
 * production URL.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 45_000,
  expect: { timeout: 10_000 },
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  // Use the locally installed stable Chrome channel, not Playwright's bundled
  // Chromium, because Chrome is the supported browser for this product.
  projects: [
    {
      name: "chrome",
      use: { ...devices["Desktop Chrome"], channel: "chrome" },
    },
  ],
});
