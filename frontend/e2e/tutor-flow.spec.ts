import { expect, test } from "@playwright/test";
import { login, seededScenario } from "./support/stack";

test("Tutor can inspect the seeded class/student/approved worksheet and export its PDF API", async ({
  page,
}) => {
  const scenario = await seededScenario();
  await login(
    page,
    scenario.tutor.email,
    scenario.tutor.password,
    /\/tutor\/dashboard/,
  );
  await page.goto(`/tutor/worksheets/${scenario.worksheetId}`);
  await expect(page.getByText("E2E Evaporation Worksheet")).toBeVisible();
  const download = await page.request.get(
    `http://127.0.0.1:8083/api/learning/tutor/worksheets/${scenario.worksheetId}/pdf`,
    {
      headers: {
        Authorization: `Bearer ${await page.evaluate(() => localStorage.getItem("jwt_token"))}`,
      },
    },
  );
  expect(download.ok()).toBeTruthy();
  expect(download.headers()["content-type"]).toContain("application/pdf");
});
