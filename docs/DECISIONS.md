# Architectural & Scope Decisions

Durable decision log. Each entry records a choice that is **not mandated by the assignment PDF** but adopted
deliberately, so graders and future phases see intent rather than scope creep. Entries are append-only; supersede
(don't delete) when a decision changes. Precedence: this file extends the PDF but never overrides it — see
`CLAUDE.md`.

Format per entry: **Decision · Context · Consequence**. Status one of `Accepted` / `Superseded by #N`.

---

## D1 — Angular (not React) for the frontend · Accepted

- **Decision:** Build the frontend in Angular 22 instead of the PDF's suggested React.
- **Context:** The PDF lists preferred technologies ("React") but explicitly allows the candidate to choose supporting
  technologies. Angular is the stack the author can best supervise and review for AI-driven implementation.
- **Consequence:** Uses `angular-oauth2-oidc`, Jest, ESLint (`eslint-config-google`), Istanbul. Diverges from the
  literal preference; justified as an allowed substitution.

## D2 — OAuth2 / OIDC via Keycloak for operator login · Accepted

- **Decision:** Implement "login by different operators" as OAuth2/OIDC (Authorization Code + PKCE) backed by a local
  Keycloak instance with demo `operator` and `admin` users.
- **Context:** The PDF requires only "login possibility by different operators" without prescribing a mechanism.
- **Consequence:** Adds Keycloak to Docker Compose and role-based access (read for all; admin/editor for risk-rule
  writes). Introduced in Phase 5.

## D3 — Server-Sent Events (SSE) for live AI progress · Accepted

- **Decision:** Stream AI risk-assessment progress to the UI over SSE (`text/event-stream`) with typed stage events.
- **Context:** The PDF asks that the operator "receive live-updates of the assessment processing" but names no
  transport. SSE fits one-way server→client progress streams simply.
- **Consequence:** A streaming endpoint plus resilience rules (timeout cleanup; assessment continues and persists if
  the operator disconnects). Introduced in Phase 4.

## D4 — WireMock-stubbed LLM for offline demo · Accepted

- **Decision:** Integrate a configurable AI provider but allow a feature-flagged record-and-replay mode where WireMock
  serves recorded LLM sessions.
- **Context:** The PDF explicitly permits stubbing LLM calls. Offline determinism makes the 10–15 min demo reliable.
- **Consequence:** Adds WireMock to Docker Compose and a dev record flag. Introduced in Phase 4.

## D5 — Quality gates: CI, ArchUnit, coverage · Accepted

- **Decision:** Add GitHub Actions CI (lint/build/test + SonarCloud), ArchUnit architecture tests, and coverage
  (JaCoCo/Istanbul), none required by the PDF.
- **Context:** The methodology is itself graded; visible, automated quality gates demonstrate engineering rigor.
- **Consequence:** Extra build config and a per-phase ArchUnit expectation captured in the global Definition of Done.

## D6 — Two-table risk-assessment model · Accepted

- **Decision:** Split persisted results into `risk_final_assessments` (aggregate: level, score, findings,
  recommendations, per transaction) and `risk_assessments` (line items: which rules fired, with `score_contribution`),
  keyed `(assessment_id, rule_id)`.
- **Context:** The PDF defines only a single `risk_assessments` signal table; the spec needs a persisted final outcome
  plus per-transaction history (Feature 7). The original spec had contradictory FK/PK definitions.
- **Consequence:** Reconciled data model in `PROJECT_SPECIFICATION.md`; `risk_level` is categorical (LOW/MEDIUM/HIGH)
  with a separate numeric `risk_score`.

## D7 — Karma/Jasmine over Jest for frontend tests · Accepted
- **Decision:** Use Angular's default Karma/Jasmine test runner instead of the Jest originally named in `CLAUDE.md`.
- **Context:** The scaffolded `package.json` ships Angular's default `ng test` (Karma/Jasmine), which already emits
  Istanbul coverage. `jest-preset-angular` can lag new Angular majors and adds config surface for no clear benefit
  here.
- **Consequence:** `CLAUDE.md`'s Testing & Quality line updated; `frontend/build.gradle.kts`'s `test` task runs
  `npm run test:ci` (Karma, headless Chromium via Puppeteer, `--code-coverage`).

## D8 — `@angular-eslint` + Prettier over `eslint-config-google` · Accepted
- **Decision:** Lint the frontend with `@angular-eslint`'s flat config plus Prettier instead of `eslint-config-google`.
- **Context:** `eslint-config-google` predates ESLint flat config and modern Angular tooling; `@angular-eslint` is the
  idiomatic, actively maintained choice for Angular 22.
- **Consequence:** `frontend/eslint.config.js` (flat config) and `.prettierrc` added; `CLAUDE.md` updated.

## D9 — Spotless for `google-java-format` · Accepted
- **Decision:** Apply `google-java-format` via the Spotless Gradle plugin rather than Checkstyle.
- **Context:** Spotless is the canonical Gradle integration for `google-java-format`; `spotlessCheck` fails the build
  on violations, satisfying the original Checkstyle intent with less configuration.
- **Consequence:** `backend/build.gradle.kts` applies `com.diffplug.spotless`; `check` depends on `spotlessCheck`.

## D10 — Testcontainers for backend integration tests · Accepted
- **Decision:** Use Testcontainers (Postgres) with Spring Boot's `@ServiceConnection` for the backend context-load
  test, instead of a hand-managed local database.
- **Context:** Gives CI a real Postgres instance per test run without provisioning shared infrastructure.
- **Consequence:** `backend/build.gradle.kts` adds `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, and
  `testcontainers-postgresql`; `ApplicationContextTest` boots a `PostgreSQLContainer`.

## D11 — Puppeteer-bundled Chromium for Karma tests · Accepted
- **Decision:** Run Karma against Puppeteer's bundled Chromium (via a small `frontend/scripts/karma-runner.js`
  wrapper) instead of a system-installed browser or a CI browser-setup action.
- **Context:** A system Chrome path differs across local machines and CI runners; Puppeteer downloads a matching
  Chromium as a devDependency, making `npm test` hermetic everywhere without a CI-specific browser-install step.
- **Consequence:** `frontend/karma.conf.js` launches a `ChromeHeadlessCI` custom launcher; CI (`.github/workflows/
  ci.yml`) needs no separate Chrome setup step.

## D12 — Transaction-table row tooltip restores the spec's "hover" behavior · Superseded by D14
- **Decision:** `PROJECT_SPECIFICATION.md` Feature 2 shows activity-specific details "when selecting the activity or
  hovering on it," but `PHASE_2.md`'s functional requirement only describes the full detail card appearing "on
  selecting." Phase 2 reconciles both: the full `TransactionDetailDto` card stays select-only (matching the phase
  doc), and each transaction-table row additionally carries a `matTooltip` with a one-line summary (status, amount,
  currency), restoring the spec's hover behavior without duplicating the detail view.
- **Context:** Identified during Phase 2 planning (`PHASE_2_PLAN.md` Clarification #5) as a gap between the two
  precedence layers rather than a hard contradiction.
- **Consequence:** `TransactionTableComponent`'s row template binds `[matTooltip]` to a `rowSummary()` helper; no new
  dependency (`MatTooltipModule` ships with `@angular/material`, already a project dependency).

## D13 — Temporary permit-all `SecurityFilterChain` until Phase 5 · Accepted
- **Decision:** Add a minimal `SecurityConfig` (`permitAll()` on every request) in `backend/.../config/
  SecurityConfig.java`, active from Phase 2 onward until Phase 5 replaces it with real OAuth2/OIDC login and
  role-based access.
- **Context:** Phase 1 already added `spring-boot-starter-security-oauth2-resource-server` to the build (per the
  Phase 1 tech stack, in preparation for D2/Phase 5). With that starter on the classpath and no security
  configuration bean, Spring Security's default-deny posture rejects every request with `401`, which would make
  Phase 2's (and Phase 3-4's) endpoints unusable before Phase 5 exists. `PHASE_2.md` explicitly scopes auth "Out"
  to Phase 5, implying every earlier phase's endpoints should be open.
- **Consequence:** All endpoints are unauthenticated through Phase 2-4; `SecurityConfig` is expected to be replaced
  (not merely extended) when Phase 5 implements D2's Keycloak-backed OAuth2/PKCE flow and role-based access.

## D14 — Row expand-to-detail supersedes the D12 hover tooltip · Accepted
- **Decision:** Phase 2 EXT replaces the bottom-of-page transaction detail panel with an inline, click-to-expand
  table row (the full `TransactionDto` detail rendered directly beneath its owning row). The `matTooltip` row summary
  introduced by D12 is removed.
- **Context:** D12's one-line hover summary (status, amount, currency) existed to restore `PROJECT_SPECIFICATION.md`
  Feature 2's "hovering" affordance without duplicating the full detail card, which at the time was a bottom-of-page
  panel requiring a click plus a scroll. Once the full detail is one click away, inline, directly under the row, the
  tooltip delivers strictly less information no faster — a redundant second affordance for no remaining benefit.
- **Consequence:** `TransactionTableComponent` no longer binds `matTooltip`/`rowSummary()`; clicking a row toggles an
  inline expanded detail row (Angular Material's `multiTemplateDataRows` pattern) instead. `MatTooltipModule` is no
  longer used by this component.

## D15 — Chart.js + `ng2-charts` for the analytics graph · Accepted
- **Decision:** Render the Phase 3 analytics time series with Chart.js via the `ng2-charts` Angular wrapper, instead
  of `@swimlane/ngx-charts`.
- **Context:** `CLAUDE.md`'s frontend stack names no charting library, and Angular Material ships no chart component,
  so Phase 3 needs to pick one. `ngx-charts` is D3-based, pulling in a heavier transitive dependency tree for a
  single bar/line chart; Chart.js is canvas-based, smaller, and `ng2-charts` provides a first-class Angular
  standalone-component (`BaseChartDirective`) wrapper around it.
- **Consequence:** `frontend/package.json` adds `chart.js` and `ng2-charts`; `app.config.ts` calls
  `provideCharts(withDefaultRegisterables())`; `AnalyticsChartComponent` (`features/analytics/analytics-chart/`)
  wraps `<canvas baseChart>`, rendering a bar chart for transaction counts and a multi-series line chart (one line
  per currency) for amount sums.
