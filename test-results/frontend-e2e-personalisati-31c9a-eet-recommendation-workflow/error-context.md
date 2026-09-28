# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: frontend/e2e/personalisation-flow.spec.ts >> tutor can open the real diagnostic worksheet recommendation workflow
- Location: frontend/e2e/personalisation-flow.spec.ts:4:5

# Error details

```
Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
Call log:
  - navigating to "/login", waiting until "load"

```

# Test source

```ts
  1  | import { expect, type Page } from "@playwright/test";
  2  | 
  3  | /**
  4  |  * Full MVP flows intentionally require a clean, seeded Compose profile.  The
  5  |  * application currently has no supported reset/seed API, so pretending that
  6  |  * arbitrary local data represents those workflows would hide regressions.
  7  |  */
  8  | export type E2eScenario = {
  9  |   tutor: { email: string; password: string };
  10 |   student: { email: string; password: string };
  11 |   classId: number;
  12 |   studentId: number;
  13 |   topicId: number;
  14 |   questionId: number;
  15 |   worksheetId: number;
  16 | };
  17 | 
  18 | let scenario: Promise<E2eScenario> | undefined;
  19 | 
  20 | export function seededScenario(): Promise<E2eScenario> {
  21 |   scenario ??= fetch(process.env.E2E_SEED_URL ?? "http://127.0.0.1:8090/context").then(async (response) => {
  22 |     if (!response.ok) throw new Error("The disposable E2E seed is unavailable. Start `docker compose --env-file compose.e2e.env -f compose.yaml -f compose.e2e.yaml --profile e2e up --build --wait`.");
  23 |     return response.json() as Promise<E2eScenario>;
  24 |   });
  25 |   return scenario;
  26 | }
  27 | 
  28 | export async function login(page: Page, email: string, password: string, home: RegExp): Promise<void> {
> 29 |   await page.goto("/login");
     |              ^ Error: page.goto: Protocol error (Page.navigate): Cannot navigate to invalid URL
  30 |   await page.getByLabel(/email/i).fill(email);
  31 |   await page.getByLabel(/password/i).fill(password);
  32 |   // The login page also offers provider buttons (for example, "Sign in with
  33 |   // Google").  Match the credential form's explicit submit label so this
  34 |   // helper never selects a provider flow.
  35 |   await page.getByRole("button", { name: "Sign In", exact: true }).click();
  36 |   await expect(page).toHaveURL(home);
  37 | }
  38 | 
```