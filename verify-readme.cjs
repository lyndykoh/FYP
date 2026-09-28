#!/usr/bin/env node
/*
 * Documentation guardrail for Issue 56.
 *
 * This intentionally validates evidence rather than awarding module points.
 * Only an official, versioned catalogue can make the latter decision.
 */
const fs = require("node:fs");
const path = require("node:path");

const REQUIRED_HEADINGS = [
  "Overview",
  "Features and evidence",
  "Architecture",
  "Database schema",
  "Prerequisites",
  "Clean-checkout quick start",
  "Configuration",
  "Test accounts",
  "Development commands",
  "Testing and validation",
  "Offline Compose browser tests",
  "Deployment",
  "Security and privacy",
  "Continuous integration",
  "Module evidence",
  "Known limitations",
  "Contributors",
  "Licence",
];

const PLACEHOLDERS = [
  /someone add description here please/i,
  /\bTODO\b/,
  /\bTBD\b/,
  /\bFIXME\b/,
  /\[insert [^\]]+\]/i,
];

function withoutFencedCode(markdown) {
  return markdown.replace(/^```[\s\S]*?^```\s*$/gm, "");
}

function localLinkTargets(markdown) {
  const targets = [];
  const expression = /!?\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)/g;

  for (const match of markdown.matchAll(expression)) {
    const target = match[1].replace(/^<|>$/g, "");
    const isFragmentLink = target.startsWith("#");
    const isExternalLink = /^(?:https?:|mailto:|tel:)/i.test(target);

    if (!target || isFragmentLink || isExternalLink) {
      continue;
    }

    targets.push(target);
  }

  return targets;
}

function targetPath(target) {
  return decodeURIComponent(target.split("#", 1)[0]);
}

function validateLocalLinks(markdown, sourceDirectory, errors, label = "") {
  for (const target of localLinkTargets(markdown)) {
    const relative = targetPath(target);

    if (!relative) {
      continue;
    }

    const resolved = path.resolve(sourceDirectory, relative);

    if (!fs.existsSync(resolved)) {
      errors.push(`broken local link${label}: ${target}`);
    }
  }
}

function findModuleRows(markdown, errors) {
  const start = markdown.indexOf("<!-- MODULE_SCORECARD_START -->");
  const end = markdown.indexOf("<!-- MODULE_SCORECARD_END -->");
  if (start < 0 || end < 0 || end <= start) {
    errors.push("missing MODULE_SCORECARD markers");
    return [];
  }

  const scorecardSection = markdown.slice(start, end);
  const tableLines = scorecardSection
    .split("\n")
    .filter((line) => line.trim().startsWith("|"));

  if (tableLines.length < 3) {
    errors.push("module scorecard needs a header, separator, and at least one row");
    return [];
  }

  return tableLines
    .slice(2)
    .map((line, index) => {
      const cells = line
        .trim()
        .split("|")
        .slice(1, -1)
        .map((cell) => cell.trim());

      if (cells.length !== 6) {
        errors.push(`module scorecard row ${index + 1} must contain six columns`);
        return null;
      }

      return {
        catalogueId: cells[0],
        title: cells[1],
        points: Number(cells[2]),
        implementation: cells[3],
        test: cells[4],
        status: cells[5].toUpperCase(),
      };
    })
    .filter(Boolean);
}

function verifyEvidenceCell(value, label, errors) {
  const targets = localLinkTargets(value);
  if (targets.length === 0) {
    errors.push(`${label} must contain a local evidence link`);
  }
}

function parseDeclaredTotal(markdown, errors) {
  const totalPattern = /\*\*Verified module total:\*\*\s*`?(\d+)\s*\/\s*14`?/i;
  const match = markdown.match(totalPattern);
  if (!match) {
    errors.push("missing 'Verified module total: N / 14' declaration");
    return null;
  }
  return Number(match[1]);
}

function validateReadme(markdown, rootDirectory) {
  const errors = [];
  const warnings = [];
  const prose = withoutFencedCode(markdown);

  for (const heading of REQUIRED_HEADINGS) {
    if (!new RegExp(`^##\\s+${heading.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\$&")}\\s*$`, "mi").test(markdown)) {
      errors.push(`missing required heading: ${heading}`);
    }
  }

  for (const pattern of PLACEHOLDERS) {
    if (pattern.test(prose)) {
      errors.push(`placeholder text found: ${pattern}`);
    }
  }

  validateLocalLinks(markdown, rootDirectory, errors);

  const rows = findModuleRows(markdown, errors);
  const statusPattern = /\*\*Module catalogue status:\*\*\s*`?(VERIFIED|BLOCKED)`?/i;
  const statusMatch = markdown.match(statusPattern);
  const catalogueStatus = statusMatch ? statusMatch[1] : undefined;

  if (!catalogueStatus) {
    errors.push("missing module catalogue status");
  }

  let verifiedTotal = 0;
  for (const row of rows) {
    const hasValidPointValue = Number.isInteger(row.points) && row.points >= 0;

    if (!hasValidPointValue) {
      errors.push(`invalid point value for module '${row.title}'`);
      continue;
    }
    if (!["VERIFIED", "BLOCKED"].includes(row.status)) {
      errors.push(`invalid module status for '${row.title}'`);
      continue;
    }
    if (row.status === "VERIFIED") {
      const hasCatalogueEvidence = row.points > 0 && row.catalogueId !== "N/A";

      if (!hasCatalogueEvidence) {
        errors.push(`verified module '${row.title}' needs a catalogue ID and positive points`);
      }
      verifyEvidenceCell(
        row.implementation,
        `implementation for '${row.title}'`,
        errors,
      );
      verifyEvidenceCell(row.test, `test for '${row.title}'`, errors);
      verifiedTotal += row.points;
    } else if (row.points !== 0) {
      errors.push(`blocked module '${row.title}' must claim zero points`);
    }
  }

  const declaredTotal = parseDeclaredTotal(markdown, errors);
  if (declaredTotal !== null && declaredTotal !== verifiedTotal) {
    errors.push(
      `declared module total ${declaredTotal} does not equal evidence total ${verifiedTotal}`,
    );
  }
  if (catalogueStatus === "BLOCKED") {
    if (verifiedTotal !== 0) {
      errors.push("blocked catalogue status cannot contain verified points");
    }
    warnings.push("module point validation is blocked until the official catalogue is added");
  }

  const quickStartHeading = /^##\s+Clean-checkout quick start[ \t]*$/mi.exec(markdown);
  let quickStart = "";

  if (quickStartHeading) {
    const following = markdown.slice(
      quickStartHeading.index + quickStartHeading[0].length,
    );
    const nextHeading = following.search(/^##\s+/m);

    quickStart = nextHeading < 0 ? following : following.slice(0, nextHeading);
  }

  for (const command of ["make deps", "make compose-config", "make compose-up"]) {
    if (!quickStart.includes(command)) {
      errors.push(`clean-checkout instructions omit '${command}'`);
    }
  }

  return { errors, warnings, verifiedTotal, catalogueStatus };
}

function validateRepository(rootDirectory) {
  const readme = fs.readFileSync(path.join(rootDirectory, "README.md"), "utf8");
  const result = validateReadme(readme, rootDirectory);

  for (const relativePath of [
    "docs/architecture.md",
    "docs/database-schema.md",
    "docs/module-catalogue-blocker.md",
  ]) {
    const absolutePath = path.join(rootDirectory, relativePath);
    if (!fs.existsSync(absolutePath)) {
      result.errors.push(`required documentation file is missing: ${relativePath}`);
      continue;
    }

    validateLocalLinks(
      fs.readFileSync(absolutePath, "utf8"),
      path.dirname(absolutePath),
      result.errors,
      ` in ${relativePath}`,
    );
  }
  return result;
}

function run() {
  const requireFourteen = process.argv.includes("--require-14");
  const rootDirectory = path.resolve(__dirname, "..");
  const result = validateRepository(rootDirectory);

  if (requireFourteen && result.verifiedTotal < 14) {
    result.errors.push(
      `at least 14 verified module points are required; found ${result.verifiedTotal}`,
    );
  }

  for (const warning of result.warnings) {
    console.warn(`warning: ${warning}`);
  }

  if (result.errors.length > 0) {
    for (const error of result.errors) {
      console.error(`error: ${error}`);
    }

    process.exitCode = 1;
    return;
  }

  console.log(
    "README evidence validated: " +
      `${result.verifiedTotal} verified module point(s); ` +
      `catalogue ${result.catalogueStatus}.`,
  );
}

if (require.main === module) {
  run();
}

module.exports = { REQUIRED_HEADINGS, validateReadme, validateRepository };
