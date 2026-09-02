# Phase 1 Implementation Plan — Technology Decisions & Local Environment Setup

**Status:** COMPLETE

Blueprint for `PHASE_1.md`. Turns the near-empty Gradle skeleton into a runnable, testable, CI-checked baseline. No
product features — scaffolding, tooling, local env, and CI only. Read alongside `CLAUDE.md` (conventions) and
`docs/DECISIONS.md` (scope rationale).

Revision note: this revision addresses the `REJECTED` findings from the prior `/review PHASE_1 plan` pass — see
Section G (new), the `spring-boot-testcontainers` addition in Section A, and the resolved `dev`-task mechanism in
Section E.

## Current State (verified)

- Gradle 9.7.1 wrapper; `settings.gradle.kts` includes `backend`, `frontend`. Root `build.gradle.kts` = group/version
  only.
- `backend/build.gradle.kts`: Spring Boot 4.1.1, Java 25 toolchain, deps for web/OpenAPI, JPA/Flyway/Postgres, OAuth2
  resource-server, Spring AI (OpenAI), actuator, test starters. **No `src/`, no `@SpringBootApplication`, no
  `application.yml`, no Spotless/JaCoCo/ArchUnit.**
- `frontend/`: `package.json` with Angular 22 + Material/CDK + `angular-oauth2-oidc`; node-gradle plugin (Node
  22.12.0). **No Angular workspace (`angular.json`, `tsconfig*`, `src/`), no lint/test/coverage tasks wired to
  Gradle.** `ng test` = Karma/Jasmine default.
- **Missing entirely:** docker-compose, `local-environment/`, `.github/`, root run/aggregate tasks, all quality
  tooling.

## Decisions locked — implemented via Section G below

1. **Frontend tests = Karma/Jasmine** (Angular default; Istanbul coverage built in). Not Jest.
2. **Frontend lint = @angular-eslint (flat config) + Prettier** (Google-ish style). Not `eslint-config-google`.
3. **Java format/lint = Spotless with `googleJavaFormat()`** (`spotlessCheck` fails the build) — satisfies the
   CLAUDE.md "Checkstyle (google-java-format)" intent.
4. **Backend tests use Testcontainers (Postgres)** so `@SpringBootTest` context-load runs against real Postgres in CI.

These diverge from CLAUDE.md's and PHASE_1.md's literal wording, written before this substitution was decided.
Section G below turns the reconciliation into concrete, assigned file edits (not prose asides) so `/implement` can't
skip them.

## Implementation Plan

### A. Dependency catalog — `gradle/libs.versions.toml`
- versions: `spotless`, `sonarqube`, `archunit`, `testcontainers`.
- plugins: `spotless` (`com.diffplug.spotless`), `sonarqube` (`org.sonarqube`).
- libraries: `archunit-junit5`; `testcontainers-bom`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`,
  **`spring-boot-testcontainers`** (`org.springframework.boot:spring-boot-testcontainers` — provides
  `@ServiceConnection`, required by the backend test in Section B; omitted from the previous revision).

### B. Backend module
- `backend/src/main/java/io/github/adarko22/CustomerActivityAnalyticsApplication.java` — `@SpringBootApplication`.
- `backend/src/main/resources/application.yml` — datasource (`jdbc:postgresql://localhost:5432/...`, creds from env
  with local defaults), Flyway enabled, JPA `ddl-auto: none` (Flyway owns schema), actuator health/info exposure,
  springdoc path.
- `backend/src/main/resources/db/migration/V1__baseline.sql` — empty baseline so Flyway runs cleanly (feature tables
  arrive in Phase 2+).
- `backend/build.gradle.kts` — apply `jacoco` + `spotless` (`java { googleJavaFormat() }`); add archunit +
  testcontainers + `spring-boot-testcontainers` test deps; make `check` depend on `spotlessCheck` and
  `jacocoTestReport`.
- Tests: `backend/src/test/java/.../ApplicationContextTest.java` — context-load `@SpringBootTest` on a Testcontainers
  Postgres (`@ServiceConnection`, now resolvable via the Section A dependency). Optional baseline ArchUnit test (real
  rules land Phase 2).

### C. Frontend module (Angular 22 workspace)
- Workspace files: `angular.json`, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.spec.json`.
- App: `src/index.html`, `src/main.ts`, `src/styles.scss`, `src/app/{app.component.ts,html,scss,spec.ts}`,
  `src/app/{app.config.ts,app.routes.ts}` — one landing component + its `.spec.ts` so `ng test` has a real test.
- Lint/format: `eslint.config.js` (@angular-eslint flat config), `.prettierrc`; add devDeps `angular-eslint`,
  `prettier`, and `concurrently` (used by the `dev` task in Section E, installed here since the node plugin and
  `node_modules` live in this module).
- `frontend/build.gradle.kts` — node-gradle tasks: `lint` (`ng lint`), `test`
  (`ng test --watch=false --code-coverage --browsers=ChromeHeadless`), `buildFe` (`ng build`); wire a `check` task →
  `lint` + `test`, and `assemble` → build. Also register the `dev` task here (Section E) since the node plugin is
  applied in this module.

### D. Local environment — `local-environment/`
- `local-environment/docker-compose.yml` — `postgres:16` service (DB/user/password env, named volume, port 5432),
  mounting `local-environment/postgresql/init/` → `/docker-entrypoint-initdb.d`.
- `local-environment/postgresql/init/.gitkeep` — reserved for future init scripts.
- `local-environment/keycloak/.gitkeep`, `local-environment/wiremock/.gitkeep` — reserved placeholders for Phases 4–5.

### E. Single-terminal run task (resolves review finding #4)

Rather than applying the node-gradle plugin twice (root + frontend) or shelling out to a bare `npm`/`npx` that may
not exist on PATH, the multiplexed run task is owned by the module that already has the node plugin:

- In **`frontend/build.gradle.kts`**, register `dev` as an `NpxTask` (from the already-applied node-gradle plugin)
  invoking `concurrently` (installed as a frontend devDependency in Section C, resolved from
  `frontend/node_modules/.bin` since the task's working directory is the frontend module):
  ```
  concurrently -k --names docker,backend,frontend -c blue,green,magenta \
    "docker compose -f ../local-environment/docker-compose.yml up" \
    "cd .. && ./gradlew :backend:bootRun" \
    "npm start"
  ```
  (paths are relative to `frontend/`, the task's working directory).
- In the **root** `build.gradle.kts`, register a one-line alias: `tasks.register("dev") { dependsOn(":frontend:dev") }`
  so `./gradlew dev` (AC #2's "single Gradle task") works from the repo root without duplicating the node toolchain.

### F. CI — `.github/workflows/ci.yml`
- `actions/checkout`, `actions/setup-java` (Temurin 25), `gradle/actions/setup-gradle`, a headless-Chrome setup for
  Karma (`browser-actions/setup-chrome`), then `./gradlew check`.
- Conditional SonarCloud: run `./gradlew sonar` only when `secrets.SONAR_TOKEN` is present (author provisions token +
  project config).

### G. Documentation reconciliation (new — resolves review findings #1 and #2)

Concrete doc edits, assigned as implementation tasks (not left as prose), so the durable docs match the actual stack
once B/C land:

1. **`CLAUDE.md`** — in "Testing & Quality", replace:
   - `Jest` → `Karma/Jasmine (Angular default)`
   - `ESLint (`eslint-config-google`)` → `` `@angular-eslint` (flat config) + Prettier ``
   - `Checkstyle (`google-java-format`)` → `` Spotless (`google-java-format`) ``
2. **`docs/DECISIONS.md`** — append, in the existing D1–D6 ADR format:
   - **D7** — Karma/Jasmine over Jest (Angular's default test runner; avoids `jest-preset-angular` version lag on
     Angular 22; Istanbul coverage is built in).
   - **D8** — `@angular-eslint` + Prettier over `eslint-config-google` (the latter predates flat-config and modern
     Angular tooling).
   - **D9** — Spotless as the Gradle integration for `google-java-format` (canonical wiring; `spotlessCheck` gates
     `check`).
   - **D10** — Testcontainers (Postgres) for backend integration tests (real DB in CI without a hand-managed
     instance).
3. **`docs/development/PHASE_1.md`** — update Acceptance Criterion #3 wording from *"ESLint +
   `eslint-config-google`... JUnit + Jest"* to match D7–D9: *"Spotless (`google-java-format`) for backend; `@angular-
   eslint` + Prettier and JUnit + Karma/Jasmine for frontend/backend tests; coverage via JaCoCo + Istanbul."* Cross-
   reference D7–D9 inline so the phase doc doesn't silently drift from `DECISIONS.md`.

These three edits happen at `/implement` time, in the same commit as the tooling they document — not during `/plan`.

## Acceptance-criteria mapping (`PHASE_1.md`, post Section-G wording update)
1. Skeleton verified; deps via catalog → §A/§B/§C add all new deps to `libs.versions.toml`.
2. Single Gradle task, multiplexed, colored `[backend]/[frontend]/[docker]` prefixes → §E.
3. Build does lint (Spotless + @angular-eslint) + tests (JUnit + Karma/Jasmine) + coverage (JaCoCo + Istanbul) →
   §B/§C `check`, wording reconciled in §G.3.
4. GitHub Actions lint/build/test + SonarCloud gated on `SONAR_TOKEN` → §F.
5. docker-compose provisions Postgres + reserved `local-environment/postgresql` folder → §D.

## Risks / Open Questions
- **Java 25 on CI:** confirm Temurin 25 is available on GH runners (fallback: setup-java distribution/EA or GraalVM).
- **`typescript ~6.0.3`** in `package.json` looks off for Angular 22 (usually TS ~5.9) — verify/pin during implement.
- **Headless Chrome in CI** required for Karma — ensure the Chrome setup step works, else switch to a
  puppeteer-provided Chromium.
- Spring AI `2.0.1` + Spring Boot `4.1.1` compatibility — smoke-check context load.
- `NpxTask`'s working-directory semantics (relative paths in the `concurrently` command string) should be verified
  against the installed node-gradle plugin version during implementation; adjust to absolute paths if relative
  resolution proves unreliable.

## Verification (at `/implement` time)
- `./gradlew check` green: backend Spotless+JaCoCo+context test (Testcontainers, `@ServiceConnection` resolvable) and
  frontend eslint+Karma+coverage all run and pass.
- `./gradlew dev` (from repo root) boots Postgres + backend + frontend in one terminal with colored prefixes; backend
  `/actuator/health` = UP; Angular landing page served; Ctrl-C tears all down.
- CI workflow parses and runs `check`; Sonar step skipped without a token.
- `CLAUDE.md`, `DECISIONS.md` (D7–D10), and `PHASE_1.md` AC #3 all agree on the actual tooling used.
