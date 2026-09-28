# Module catalogue blocker log

## Status

**Blocked as of 2026-08-31.**  The repository does not contain the exact 42
subject PDF or authoritative module catalogue that governs this evaluation.
`DEPENDENCIES.md` records the same limitation.  Public copies of different
subject versions are not suitable substitutes because the available module
names, prerequisites, and point arithmetic differ.

## Why no 14-point claim is published

The application has testable work in authentication, databases, responsive UI,
accessibility, AI-assisted OCR/marking with Tutor review, Compose deployment,
and CI.  Those facts do not establish that any particular item earns a module
point in the evaluator's subject version.  Claiming a catalogue ID, point value,
or total without the governing document would be misleading.

## Required input to unblock

Add the exact official catalogue to this repository (for example,
`docs/official-module-catalogue.pdf`) or provide its stable official URL and
revision.  Then update the `Module evidence` table in [README.md](../README.md)
with:

1. the official catalogue ID and title;
2. its exact point value and prerequisites;
3. implementation links; and
4. an automated test link for each claimed module.

Run `npm run verify:modules` afterwards.  It deliberately fails until the
declared, evidence-backed total is at least 14.

## Available evidence to map once supplied

| Delivered capability | Implementation evidence | Automated evidence |
| --- | --- | --- |
| Role-aware account access | [auth-service](../backend/auth-service) | [AuthControllerIntegrationTest](../backend/auth-service/src/test/java/com/fttranscendence/authservice/controller/AuthControllerIntegrationTest.java) |
| Tutor-owned classes and existing Student membership | [learning classroom package](../backend/learning-service/src/main/java/com/fttranscendence/learning/classroom) | [ClassStudentMembershipIntegrationTest](../backend/learning-service/src/test/java/com/fttranscendence/learning/classroom/ClassStudentMembershipIntegrationTest.java) |
| Taxonomy-backed questions and worksheets | [question package](../backend/learning-service/src/main/java/com/fttranscendence/learning/question) | [P6ScienceQuestionBankSeedIntegrationTest](../backend/learning-service/src/test/java/com/fttranscendence/learning/question/P6ScienceQuestionBankSeedIntegrationTest.java) |
| OCR, correction, and Tutor review | [submission controller](../backend/grading-service/src/main/java/com/fttranscendence/grading/controller/SubmissionDocumentController.java) | [OcrSubmissionFinalizationIntegrationTest](../backend/grading-service/src/test/java/com/fttranscendence/grading/controller/OcrSubmissionFinalizationIntegrationTest.java) |
| Compose and browser workflows | [Compose configuration](../compose.yaml) | [CI workflow](../.github/workflows/ci.yml) |
