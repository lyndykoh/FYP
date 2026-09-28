import { expect, test } from "@playwright/test";

test("public Chrome route has no application console errors", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("console", (message) => {
    const text = message.text();
    // The test server is Next development mode. Its stack-trace eval and HMR
    // socket are deliberately blocked by the production CSP, and are not page
    // runtime errors. Keep the exemption narrow so application errors still fail.
    const nextDevelopmentNoise =
      /eval\(\) is not supported.*React requires eval\(\) in development mode|WebSocket connection.*_next\/webpack-hmr.*failed/.test(
        text,
      );
    if (
      message.type() === "error" &&
      !nextDevelopmentNoise &&
      !/favicon|Failed to load resource:.*404/.test(text)
    )
      errors.push(text);
  });
  page.on("pageerror", (error) => errors.push(error.message));

  await page.goto("/login");
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  expect(errors).toEqual([]);
});
