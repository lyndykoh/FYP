# Build and Container Dependency Inventory

This file records the dependencies required to build, test, and run the repository. It is derived from the checked-in Dockerfiles, Compose file, npm lockfiles, Maven POMs, and Maven wrapper configuration.

Do not install Java or npm transitive libraries individually in an image. Maven resolves Java dependencies from the Spring Boot BOM and the POM files; `npm ci` resolves the exact JavaScript dependency graph from the lockfiles.

## 1. Host requirements

Only the following host software is required when the complete project is built through Docker:

| Dependency | Required version | Purpose |
| --- | --- | --- |
| Docker Engine or Docker Desktop | Current version with BuildKit | Builds and runs all images. |
| Docker Compose | Compose v2 (`docker compose`) | Starts the application stack. |
| Git | Any current version | Checks out the repository. Not required inside runtime images. |

The build host must be able to reach:

- Docker Hub for base images.
- Maven Central for Java dependencies.
- The npm registry for frontend dependencies.
- The configured AI provider at runtime for grading and OCR.

## 2. Local non-container development requirements

These are required only when commands such as `npm test` are run directly on the host:

| Dependency | Required version | Notes |
| --- | --- | --- |
| Node.js | 20.x | Matches `frontend/Dockerfile`. |
| npm | Version bundled with Node 20 | Must support lockfile version 3 and `npm ci`. |
| JDK | 17 | A full JDK containing `javac` is required; a JRE is insufficient. |
| POSIX shell | `/bin/sh` compatible | Required by both Maven wrapper scripts. |
| `curl` or `wget` | Any current version | Used by Maven Wrapper if Maven is not cached. |

Maven does not need to be installed globally. The repository wrappers download these versions:

- Auth service: Apache Maven 3.9.15.
- Grading service: Apache Maven 3.9.16.

Install JavaScript dependencies with:

```bash
npm ci
npm --prefix frontend ci
```

## 3. Container base images

### Frontend

| Stage | Required image | Included tooling |
| --- | --- | --- |
| Build | `node:20-alpine` | Node.js 20, npm, Alpine Linux/musl. |
| Runtime | `node:20-alpine` | Node.js 20 and the libraries required by Next.js standalone output. |

The frontend lockfile includes Linux musl binaries for Next.js SWC and Sharp, including x64 and ARM64 variants. No additional Alpine build packages are currently required by the locked dependency graph.

The build stage must run `npm ci`, not `npm ci --only=production`, because `next build` requires TypeScript and type packages stored under `devDependencies`. The standalone runtime stage does not need `node_modules` copied from the builder.

### Auth service

| Stage | Required image | Included tooling |
| --- | --- | --- |
| Build | `maven:3.9-eclipse-temurin-17` | Maven 3.9 and a complete Temurin JDK 17. |
| Runtime | `eclipse-temurin:17-jre-jammy` | Temurin Java 17 runtime plus the Ubuntu package source used to install the health-check client. |

### Grading service

The grading service requires the same image types as the auth service:

- Build: Maven 3.9 with Temurin JDK 17.
- Runtime: Temurin Java 17 JRE.

The grading and learning services use the same Maven/JDK 17 build image and Temurin JRE 17 runtime image as auth-service. All backend runtime images include `curl` for Compose health checks and run as an unprivileged `app` user.

### Infrastructure

| Service | Current image | Requirement |
| --- | --- | --- |
| PostgreSQL | `postgres:18-alpine` | Runtime database and `pg_isready` health check; the volume mounts the PostgreSQL 18 parent data directory. |
| Adminer | `adminer` | Optional database administration UI. Pin a tested image version before deployment. |
| Nginx edge | `nginx:1.27-alpine` | Production-only TLS/reverse-proxy edge. Its health check uses Alpine’s built-in BusyBox `wget`, so it does not rely on an uninstalled `curl` binary. |
| E2E AI/OCR mock and seed | `node:22-alpine` | Disposable offline fixture services. They use only Node’s built-in HTTP/fetch APIs and have no npm package manifest. |

No standalone OCR executable is currently required. OCR and grading are HTTP calls to the configured external AI provider.

### Browser-test host dependency

Playwright runs from the host/CI runner rather than inside Compose. Install its
Chrome channel after frontend dependencies with `make e2e-chrome` on macOS or
Windows, or use `make e2e-chrome-linux` on Linux. The latter installs the
native system libraries required by Playwright and is the command used in CI.

## 4. Frontend direct dependencies

Exact versions below come from `frontend/package-lock.json`.

### Production and build runtime

| Package | Locked version | Purpose |
| --- | ---: | --- |
| `next` | 16.3.3 | Web framework and standalone server build. |
| `react` | 19.2.4 | UI runtime. |
| `react-dom` | 19.2.4 | DOM renderer. |
| `@mui/material` | 9.1.1 | Component and styling framework. |
| `@mui/icons-material` | 9.1.1 | UI icon set. |
| `@mui/material-nextjs` | 9.1.1 | MUI/Next.js integration. |
| `@emotion/cache` | 11.14.0 | CSS-in-JS cache. |
| `@emotion/react` | 11.14.0 | Emotion React runtime. |
| `@emotion/styled` | 11.14.1 | Styled component API. |

### Build, type-check, lint, and test

| Package | Locked version | Purpose |
| --- | ---: | --- |
| `typescript` | 5.9.3 | Type checking and Next.js TypeScript build support. |
| `@types/node` | 20.19.41 | Node.js types. |
| `@types/react` | 19.2.14 | React types. |
| `@types/react-dom` | 19.2.3 | React DOM types. |
| `eslint` | 9.39.4 | Lint runner. |
| `eslint-config-next` | 16.3.3 | Next.js lint rules, pinned to the same release as Next.js. |
| `vitest` | 4.1.11 | Unit and integration test runner. |
| `@vitest/coverage-v8` | 4.1.11 | Test coverage provider. |
| `@vitejs/plugin-react` | 5.2.0 | React transform for Vitest/Vite. |
| `jsdom` | 29.1.1 | Browser-like test environment. |
| `@testing-library/react` | 16.3.2 | React component testing. |
| `@testing-library/jest-dom` | 6.9.1 | DOM assertions. |
| `@testing-library/user-event` | 14.6.6 | User interaction simulation. |
| `@playwright/test` | 1.62.1 | Chrome end-to-end test runner for the disposable Compose fixture stack. |

The repository root also installs `husky` 9.1.7 for local Git hooks. Husky is not required in production images.

### Evaluator-facing frontend library rationale

These are framework, styling, development, and testing primitives. None
implements Tutor/Student workflows, grading, OCR decisions, worksheet
generation, authentication policy, or learning logic for this project.

| Package | Purpose and repository evidence | Why it is allowed | What to tell an evaluator |
| --- | --- | --- | --- |
| `next` | Routing, layouts, server rendering, and builds in `frontend/src/app`, `src/proxy.ts`, and `next.config.ts`. | Web framework only. | “Next.js provides routing and rendering. We implement pages, access rules, API calls, and application logic.” |
| `react`, `react-dom` | Components, hooks, contexts, and browser rendering throughout `.tsx` files. `react-dom` is mounted by Next.js. | UI runtime primitives only. | “React renders components; React DOM connects them to the browser. Our screens and interactions are our own code.” |
| `@mui/material`, `@mui/icons-material` | General buttons, forms, cards, dialogs, layout, theming, and static icons. | Presentation primitives; no education or workflow behaviour. | “MUI supplies visual building blocks and icons. We compose them and implement validation, navigation, and business rules.” |
| `@mui/material-nextjs`, `@emotion/cache`, `@emotion/react`, `@emotion/styled` | MUI/Next style integration and CSS generation, including the project’s styled components. | Styling infrastructure and required peer/runtime support. | “These packages make our own theme and component CSS render consistently; they implement no product feature.” |
| `husky` | Runs the repository’s own Git hook through the root `prepare` script. | Local development tooling only; absent from runtime images. | “Husky runs our commit-message check and is not application functionality.” |
| `typescript`, `@types/node`, `@types/react`, `@types/react-dom` | Source type checking and type declarations. | Compiler/static-analysis tooling only. | “These packages check code and provide type information; they add no runtime feature.” |
| `eslint`, `eslint-config-next` | Linting through `npm run lint` and `frontend/eslint.config.mjs`. | Code-quality tooling and rules only. | “ESLint checks our code for consistency and mistakes.” |
| `vitest`, `@vitejs/plugin-react`, `@vitest/coverage-v8`, `jsdom` | Unit/integration test execution, React transformation, coverage, and simulated DOM. | Test infrastructure only. | “They execute and measure tests we wrote; they do not implement the application.” |
| `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event` | Component rendering, assertions, typing, clicks, and keyboard tests. | Test helpers only. | “They simulate users against behaviour implemented in our code.” |
| `@playwright/test` | Chrome browser automation for `frontend/e2e` against the disposable Compose fixture stack. | Test runner only; not bundled into the deployed application. | “Playwright verifies browser workflows against our application; it does not provide the workflows.” |

#### Potential evaluator questions

| Item | Risk | Resolution |
| --- | --- | --- |
| Official 42 subject/specification is not stored in this repository. | Exact version-specific library restrictions cannot be checked here. | Compare this inventory against the exact evaluation subject before submission. This is an audit limitation, not evidence that a package is forbidden. |
| `@emotion/*` and `react-dom` have few or no direct imports. | They may appear unused. | Keep them: they are direct peer/runtime requirements of MUI, `@mui/material-nextjs`, and Next.js, making the Docker build reproducible. |
| `next` and `@mui/material` are large packages. | An evaluator may ask whether they provide assessed features. | Point to `frontend/src/app`, `src/lib/auth.ts`, `src/proxy.ts`, navigation components, and forms: they contain the project’s access, data, and workflow code. |

**Conclusion:** the direct npm dependencies are limited to frontend rendering,
styling, type checking, linting, tests, browser tests, and Git hooks. No direct
npm dependency provides a complete authentication system, 2FA, chat,
tournament, matchmaking, Pong/game engine, grading workflow, worksheet flow,
or education feature. This is a feature-library compliance explanation, not a
live CVE audit; `make security-audit` performs the separate dependency audit.

## 5. Auth-service Java dependencies

The service uses Java 17 and Spring Boot parent 4.0.6.

### Application dependencies

- `spring-boot-starter-actuator`
- `spring-boot-starter-webmvc`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-boot-starter-flyway`
- `flyway-database-postgresql`
- PostgreSQL JDBC driver
- `jjwt-api` 0.11.5
- `jjwt-impl` 0.11.5 at runtime
- `jjwt-jackson` 0.11.5 at runtime
- Lombok at compile time

### Test dependencies

- Spring Boot actuator test support
- Spring Boot Data JPA test support
- Spring Boot validation test support
- Spring Boot Web MVC test support
- H2 in-memory database
- Testcontainers JUnit Jupiter and PostgreSQL modules (real-PostgreSQL migration compatibility)

## 6. Grading-service Java dependencies

The service uses Java 17 and Spring Boot parent 4.1.0.

### Application dependencies

- `spring-boot-starter-webmvc`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-flyway`
- `flyway-database-postgresql`
- PostgreSQL JDBC driver
- Jackson Databind
- `jjwt-api` 0.11.5
- `jjwt-impl` 0.11.5 at runtime
- `jjwt-jackson` 0.11.5 at runtime

### Test dependencies

- Spring Boot Data JPA test support
- Spring Boot Web MVC test support
- Spring Security test support
- H2 in-memory database
- Testcontainers JUnit Jupiter and PostgreSQL modules (real-PostgreSQL migration compatibility)

## 7. Learning-service Java dependencies

The service uses Java 17 and Spring Boot parent 4.0.6. Its application dependencies are Spring Boot Data JPA, validation, Web MVC, Security, Actuator, Flyway, PostgreSQL JDBC, and JJWT 0.11.5. Its test dependencies provide Data JPA, validation, Web MVC and Security testing, H2, plus Testcontainers JUnit Jupiter and PostgreSQL modules.

## 8. Required configuration

The following values must be supplied through Compose or another secrets/configuration provider. Use `.env.example` as the template; do not bake real secrets into images.

### PostgreSQL

- `POSTGRES_USER`
- `POSTGRES_PW`
- `POSTGRES_DB`

### Auth service

- `AUTH_DB_URL`
- `AUTH_DB_USERNAME`
- `AUTH_DB_PASSWORD`
- `AUTH_DB_SCHEMA`
- `JWT_SECRET` containing at least 32 random bytes
- `JWT_EXPIRATION_MS`
- Optional first-account provisioning: `BOOTSTRAP_TUTOR_EMAIL`, `BOOTSTRAP_TUTOR_PASSWORD`, and `BOOTSTRAP_TUTOR_FULL_NAME` must be supplied together or all left blank.

### Grading service

- `GRADING_DB_URL`
- `GRADING_DB_USERNAME`
- `GRADING_DB_PASSWORD`
- `GRADING_DB_SCHEMA`
- `JWT_SECRET`, identical to the auth-service value
- `AI_ENGINE_URL`
- `AI_ENGINE_MODEL`
- `AI_ENGINE_API_KEY`
- `AI_VISION_MODEL`

### Learning service

- `LEARNING_DB_URL`
- `LEARNING_DB_USERNAME`
- `LEARNING_DB_PASSWORD`
- `LEARNING_DB_SCHEMA`
- `JWT_SECRET`, identical to the auth-service value

### Frontend

- `NEXT_PUBLIC_API_URL`

`NEXT_PUBLIC_API_URL` is compiled into the browser bundle and must be available in the frontend build stage. It must be a URL reachable by the user's browser; `http://auth-service:8081` is only resolvable inside the Compose network and is not a valid browser-facing production value.

## 9. Network, ports, and persistent storage

| Component | Default port | Dependencies |
| --- | ---: | --- |
| Frontend | 3000 | Browser-accessible auth/API URL. |
| Adminer | 8080 | PostgreSQL. |
| Auth service | 8081 | PostgreSQL and shared JWT secret. |
| Grading service | 8082 | PostgreSQL, shared JWT secret, and external AI provider. |
| Learning service | 8083 | PostgreSQL and shared JWT secret. |
| PostgreSQL | 5432 | Persistent `postgres_data` volume. |

Services must share a private Compose network. Only browser-facing services should need published host ports in production.

## 10. Future container hardening

The Issue 01–07 container foundation is complete. Later deployment work should still:

1. Pin Adminer and all build/runtime base images to tested digests for release reproducibility.
2. Run the complete unit and integration verification commands in CI before publishing images.
3. Put TLS and a browser-facing reverse proxy in front of the services for deployment.
4. Replace `.env` secrets with the target platform's secret manager.

## 11. Verification commands

```bash
make deps
make frontend-lint
make frontend-typecheck
make frontend-test
make frontend-build
make backend-test
make ci-compose
make e2e-config
```

The learning service deliberately reuses the checked-in auth-service Maven
Wrapper (`backend/auth-service/mvnw -f backend/learning-service/pom.xml`)
because its POM is the source of dependency resolution. This is documented in
the Makefile and CI; Maven itself is present in every backend build image.

For tests and commands that require Docker daemon access, public registries,
external providers, a VM, or GitHub Actions, see
[SANDBOX-VALIDATION-RUNBOOK.md](docs/SANDBOX-VALIDATION-RUNBOOK.md).
