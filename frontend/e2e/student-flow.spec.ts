import { expect, test } from "@playwright/test";
import { login, seededScenario } from "./support/stack";

test("student sees the seeded assigned worksheet", async ({ page }) => {
  const scenario = await seededScenario();
  await login(
    page,
    scenario.student.email,
    scenario.student.password,
    /\/student\/dashboard/,
  );
  await page.goto("/worksheets");
  await expect(
    page.getByRole("heading", { name: /my worksheets/i }),
  ).toBeVisible();
  await expect(page.getByText("E2E Evaporation Worksheet")).toBeVisible();
});
