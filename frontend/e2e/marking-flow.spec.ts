import { expect, test } from "@playwright/test";
import { login, seededScenario } from "./support/stack";

test("Student submission appears in Tutor View and opens its source before review", async ({
  page,
}) => {
  const scenario = await seededScenario();
  await login(
    page,
    scenario.student.email,
    scenario.student.password,
    /\/student\/dashboard/,
  );
  await page.goto(
    `/upload?ws=${scenario.worksheetId}&studentId=${scenario.studentId}`,
  );
  await page.getByLabel("Choose files").setInputFiles({
    name: "handwritten-answer.png",
    mimeType: "image/png",
    // A tiny valid PNG is sufficient because the offline OCR provider returns
    // a fixed transcription. No student document is stored in this fixture.
    buffer: Buffer.from(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLk7wAAAABJRU5ErkJggg==",
      "base64",
    ),
  });
  await page
    .getByRole("button", { name: "Save and continue to OCR review" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Review extracted text" }),
  ).toBeVisible();
  await expect(page.getByLabel("Page 1 text")).toHaveValue(
    /water gains energy and evaporates/,
  );

  await page.getByLabel("Page 1 answer belongs to").click();
  await page.getByRole("option", { name: /Question 1:/ }).click();
  await page.getByRole("button", { name: "Submit for Tutor Review" }).click();
  await expect(page).toHaveURL(/\/worksheets\/\d+\/results/);

  await login(
    page,
    scenario.tutor.email,
    scenario.tutor.password,
    /\/tutor\/dashboard/,
  );
  await page.goto("/tutor/reviews");
  await expect(
    page.getByRole("link", { name: "View submitted worksheet" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "View submitted worksheet" }).click();
  await expect(
    page.getByRole("heading", { name: "Submitted worksheet" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Continue to Review" }).click();
  await expect(
    page.getByRole("heading", { name: "Tutor marking review" }),
  ).toBeVisible();
});
