const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const {
  REQUIRED_HEADINGS,
  validateReadme,
  validateRepository,
} = require("./verify-readme.cjs");

function fixture({ total = 14, status = "VERIFIED", row, extra = "" } = {}) {
  const headings = REQUIRED_HEADINGS.map((heading) => {
    const isQuickStartSection = heading === "Clean-checkout quick start";
    const commands = isQuickStartSection
      ? "\n\n```bash\nmake deps\nmake compose-config\nmake compose-up\n```"
      : "";

    return `## ${heading}${commands}`;
  }).join("\n\n");

  const defaultScorecardRow =
    "| WEB-01 | Web application | 14 | [implementation](src/app.ts) | " +
    "[test](test/app.test.js) | VERIFIED |";
  const scorecardRow = row || defaultScorecardRow;

  return (
    `# Lumina\n\n${headings}\n\n` +
    `**Module catalogue status:** ${status}\n\n` +
    "<!-- MODULE_SCORECARD_START -->\n" +
    "| Catalogue ID | Claim | Points | Implementation | Test | Status |\n" +
    "| --- | --- | ---: | --- | --- | --- |\n" +
    `${scorecardRow}\n` +
    "<!-- MODULE_SCORECARD_END -->\n\n" +
    `**Verified module total:** ${total} / 14\n${extra}`
  );
}

function withFixtureFiles(callback) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "readme-validator-"));
  fs.mkdirSync(path.join(root, "src"));
  fs.mkdirSync(path.join(root, "test"));
  fs.writeFileSync(path.join(root, "src", "app.ts"), "export {};\n");
  fs.writeFileSync(path.join(root, "test", "app.test.js"), "");
  try {
    callback(root);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
}

test("accepts a complete 14-point scorecard with real local evidence", () => {
  withFixtureFiles((root) => {
    const result = validateReadme(fixture(), root);
    assert.deepEqual(result.errors, []);
    assert.equal(result.verifiedTotal, 14);
    assert.equal(result.catalogueStatus, "VERIFIED");
  });
});

test("reports missing headings and placeholder prose", () => {
  withFixtureFiles((root) => {
    const result = validateReadme("# Lumina\n\nTODO add this", root);
    assert(result.errors.some((error) => error.includes("missing required heading")));
    assert(result.errors.some((error) => error.includes("placeholder text")));
  });
});

test("reports broken evidence links and incorrect arithmetic", () => {
  withFixtureFiles((root) => {
    const markdown = fixture({
      total: 13,
      row:
        "| WEB-01 | Web application | 14 | [implementation](missing.ts) | " +
        "[test](test/app.test.js) | VERIFIED |",
    });
    const result = validateReadme(markdown, root);

    assert(result.errors.some((error) => error.includes("broken local link")));
    assert(
      result.errors.some((error) => error.includes("does not equal evidence total")),
    );
  });
});

test("permits an explicit zero-point catalogue blocker without inventing points", () => {
  withFixtureFiles((root) => {
    const markdown = fixture({
      total: 0,
      status: "BLOCKED",
      row: "| N/A | Catalogue unavailable | 0 | [blocker](src/app.ts) | N/A | BLOCKED |",
    });
    const result = validateReadme(markdown, root);

    assert.deepEqual(result.errors, []);
    assert.equal(result.verifiedTotal, 0);
    assert.equal(result.catalogueStatus, "BLOCKED");
  });
});

test("repository validation checks local links in all required documentation", () => {
  withFixtureFiles((root) => {
    fs.writeFileSync(path.join(root, "README.md"), fixture());
    fs.mkdirSync(path.join(root, "docs"));
    fs.writeFileSync(
      path.join(root, "docs", "architecture.md"),
      "[source](../src/app.ts)\n",
    );
    fs.writeFileSync(
      path.join(root, "docs", "database-schema.md"),
      "[source](../src/app.ts)\n",
    );
    fs.writeFileSync(
      path.join(root, "docs", "module-catalogue-blocker.md"),
      "[missing](../nope.md)\n",
    );

    const result = validateRepository(root);

    assert(
      result.errors.some((error) => error.includes("module-catalogue-blocker.md")),
    );
  });
});
