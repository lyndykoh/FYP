# Local and CI Validation Runbook

This log records commands that the repository can run but this Codex sandbox
could not fully execute. Run them from the repository root, copy the output
under the relevant result entry, and replace `Not run in sandbox` with the
date, machine, and outcome. These commands use the local Docker daemon,
GitHub Actions, or public package registries required by the project.

## Prerequisites

```bash
docker info
docker compose version
make deps
```

Expected result: Docker reports a running server, Compose reports v2, and both
root and frontend `npm ci` commands finish with exit code `0`.

## Sandbox-excluded checks

| Check | Command | Expected success output | Sandbox status and result field |
| --- | --- | --- | --- |
| Dependency audit | `make security-audit` | npm exits `0` and reports no high/critical production dependency advisories. A finding correctly exits non-zero and must be remediated or explicitly risk-accepted. | **Passed after remediation:** Next.js and its matching ESLint config were pinned to 16.3.3, then npm’s non-forced lockfile remediation was applied. Both `npm audit` and `npm audit --omit=dev --audit-level=high` report `found 0 vulnerabilities`. |
| Four application image builds | `make ci-compose` | Compose validates `.env.example`, then reports `Built` for auth, grading, learning, and frontend images; exit code `0`. | **Passed on the host:** all four application images built successfully. |
| Browser installation on macOS/Windows | `make e2e-chrome` | Playwright finishes installing the Chrome channel with exit code `0`. | Not run in sandbox: browser download is external. Result: `________________`. |
| Browser installation on Linux | `make e2e-chrome-linux` | Playwright installs Chrome and native browser libraries with exit code `0`. This is CI-equivalent and may require administrator permission. | Not run in sandbox: browser/system-package download is external. Result: `________________`. |
| Disposable offline browser suite | `make e2e` | Compose reports healthy `postgres`, three APIs, frontend, `ai-ocr-mock`, and `e2e-seed`; Playwright runs the current 10 browser assertions and exits `0`; cleanup removes only E2E containers and `e2e_postgres_data`. | **Passed:** the deterministic fixture stack became healthy and all 10 Playwright assertions passed. The cleanup completed, leaving no E2E containers or volumes running. |
| Real PostgreSQL Testcontainers paths | `make backend-test` | Maven verification succeeds for all three services. On a Docker-capable machine the PostgreSQL Testcontainers migration tests execute rather than appearing as skipped. | Maven passed in sandbox, but Docker-backed Testcontainers tests were skipped because the sandbox cannot connect to Docker. Result: `________________`. |
| VM-only TLS/Compose deployment | `make production-secrets && make vm-tls && make production-config && make production-up && make production-ps` | Production-shaped stack shows healthy Nginx, frontend, and three APIs. `curl --resolve lumina.sg:443:<VM_IP> --cacert ../tls/fullchain.pem -fsS https://lumina.sg/healthz` succeeds; port 80 redirects to HTTPS. | Not run in sandbox: it reads/writes `../secrets.txt` and `../tls`, needs ports 80/443 and a trusted VM-only certificate. Result: `________________`. |
| Real OpenAI/DeepSeek smoke | Run a representative OCR/marking flow using a configured provider key. | Provider returns an OCR/marking response, or a meaningful provider error. Do not print the secret. | Not run in sandbox: requires a real provider key and network. Offline E2E intentionally uses the local mock instead. Result: `________________`. |
| GitHub Actions PR tier | Push a branch and open a pull request. | Required checks appear as `Frontend checks`, `Backend checks (auth-service)`, `Backend checks (grading-service)`, `Backend checks (learning-service)`, and `Compose configuration and images`; each is green. | Cannot create or inspect a GitHub pull request from this sandbox. Result: `________________`. |
| Scheduled/manual E2E tier | In GitHub Actions, run **CI** with **Run workflow**, or merge to `main`. | `Offline E2E` starts the fixture stack, uploads `offline-e2e-artifacts` on success/failure, and deletes the fixture volume. | Cannot dispatch a GitHub Action from this sandbox. Result: `________________`. |

## Commands already verified in the sandbox

Run these locally too if you want an independent record; each should exit `0`.

```bash
make help
make e2e-config
npm run compose:config
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
make backend-test
git diff --check
```

Recorded sandbox results:

- Frontend lint and type check: passed.
- Frontend unit suite: 64 files and 283 tests passed.
- Frontend production build: passed.
- Auth, grading, and learning Maven `verify`: passed; Docker-only Testcontainers cases skipped.
- Normal and E2E Compose configuration: passed.
- Makefile help/dry-run and GitHub Actions YAML parsing: passed.

Recorded host results supplied after this runbook was created:

- `make ci-compose`: passed; auth, grading, learning, and frontend images built.
- E2E fixture stack: passed readiness for PostgreSQL, all three APIs, frontend,
  AI/OCR mock, seed service, and Adminer.
- Initial `make e2e`: 6 of 10 assertions passed. The first four failures were
  caused by an ambiguous “Sign In” locator. The next rerun exposed the fixture
  CORS mismatch (`localhost:3000` versus Playwright's `127.0.0.1:3000`) and a
  real learning database-schema configuration defect. Those were corrected,
  along with the OCR document/page persistence ordering issue. The final
  `make e2e` run passed all 10 browser assertions and removed its disposable
  stack and data volume.

## Updating this record

Record the command, date, host/VM, exit code, and final summary line. Do not
paste API keys, JWT secrets, `.env` values, or complete container environments
into this document.
