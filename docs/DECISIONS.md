# Architectural & Scope Decisions

Durable, append-only decision log: choices **not mandated by the assignment PDF** but made deliberately, so
graders and future phases see intent rather than scope creep. Supersede (don't delete) when a decision
changes. This file extends the PDF but never overrides it — see `CLAUDE.md`.

Format per entry: **Decision · Context · Consequence**. Status: `Accepted` / `Superseded by #N`.

---

## D1 — Angular (not React) for the frontend · Accepted

- **Decision:** Build the frontend in Angular 22 instead of the PDF's suggested React.
- **Context:** The PDF names React but explicitly allows substituting supporting technologies. Angular is
  the stack the author can best supervise and review for AI-driven implementation.
- **Consequence:** Uses `angular-oauth2-oidc`, Jest, ESLint (`eslint-config-google`), Istanbul.

## D2 — OAuth2 / OIDC via Keycloak for operator login · Accepted

- **Decision:** Implement "login by different operators" as OAuth2/OIDC (Authorization Code + PKCE) backed
  by a local Keycloak instance with demo `operator` and `admin` users.
- **Context:** The PDF requires only "login possibility by different operators," no mechanism.
- **Consequence:** Adds Keycloak to Docker Compose and role-based access (read for all; admin/editor for
  risk-rule writes). Introduced in Phase 5.

## D3 — Server-Sent Events (SSE) for live AI progress · Accepted

- **Decision:** Stream AI risk-assessment progress to the UI over SSE (`text/event-stream`) with typed
  stage events.
- **Context:** The PDF asks for "live-updates of the assessment processing" but names no transport. SSE
  fits a one-way server→client progress stream simply.
- **Consequence:** A streaming endpoint plus resilience rules (timeout cleanup; assessment continues and
  persists if the operator disconnects). Introduced in Phase 4.

## D4 — WireMock-stubbed LLM for offline demo · Accepted

- **Decision:** Integrate a configurable AI provider, with a feature-flagged record-and-replay mode where
  WireMock serves recorded LLM sessions.
- **Context:** The PDF explicitly permits stubbing LLM calls. Offline determinism makes the 10–15 min demo
  reliable.
- **Consequence:** Adds WireMock to Docker Compose and a dev record flag. Introduced in Phase 4.

## D5 — Quality gates: CI, ArchUnit, coverage · Accepted

- **Decision:** Add GitHub Actions CI (lint/build/test + SonarCloud), ArchUnit architecture tests, and
  coverage (JaCoCo/Istanbul) — none required by the PDF.
- **Context:** The methodology itself is graded; automated quality gates demonstrate engineering rigor.
- **Consequence:** Extra build config and a per-phase ArchUnit expectation in the global Definition of Done.

## D6 — Two-table risk-assessment model · Accepted

- **Decision:** Split persisted results into `risk_final_assessments` (aggregate: level, score, findings,
  recommendations, per transaction) and `risk_assessments` (line items: which rules fired, with
  `score_contribution`), keyed `(assessment_id, rule_id)`.
- **Context:** The PDF defines only a single `risk_assessments` signal table; the spec needs both a
  persisted final outcome and per-transaction history (Feature 7). The original spec had contradictory
  FK/PK definitions.
- **Consequence:** Reconciled data model in `PROJECT_SPECIFICATION.md`; `risk_level` is categorical
  (LOW/MEDIUM/HIGH) with a separate numeric `risk_score`.

## D7 — Karma/Jasmine over Jest for frontend tests · Accepted

- **Decision:** Use Angular's default Karma/Jasmine test runner instead of the Jest named in `CLAUDE.md`.
- **Context:** The scaffolded `package.json` ships `ng test` (Karma/Jasmine) with Istanbul coverage
  already wired up; `jest-preset-angular` lags new Angular majors for no clear benefit here.
- **Consequence:** `CLAUDE.md`'s Testing & Quality line updated; `frontend/build.gradle.kts`'s `test` task
  runs `npm run test:ci` (Karma, headless Chromium via Puppeteer, `--code-coverage`).

## D8 — `@angular-eslint` + Prettier over `eslint-config-google` · Accepted

- **Decision:** Lint the frontend with `@angular-eslint`'s flat config plus Prettier instead of
  `eslint-config-google`.
- **Context:** `eslint-config-google` predates ESLint flat config and modern Angular tooling;
  `@angular-eslint` is the idiomatic, actively maintained choice for Angular 22.
- **Consequence:** `frontend/eslint.config.js` (flat config) and `.prettierrc` added; `CLAUDE.md` updated.

## D9 — Spotless for `google-java-format` · Accepted

- **Decision:** Apply `google-java-format` via the Spotless Gradle plugin rather than Checkstyle.
- **Context:** Spotless is the canonical Gradle integration for `google-java-format`; `spotlessCheck`
  fails the build on violations, satisfying the original Checkstyle intent with less configuration.
- **Consequence:** `backend/build.gradle.kts` applies `com.diffplug.spotless`; `check` depends on
  `spotlessCheck`.

## D10 — Testcontainers for backend integration tests · Accepted

- **Decision:** Use Testcontainers (Postgres) with Spring Boot's `@ServiceConnection` for the backend
  context-load test, instead of a hand-managed local database.
- **Context:** Gives CI a real Postgres instance per test run without provisioning shared infrastructure.
- **Consequence:** `backend/build.gradle.kts` adds `spring-boot-testcontainers`,
  `testcontainers-junit-jupiter`, and `testcontainers-postgresql`; `ApplicationContextTest` boots a
  `PostgreSQLContainer`.

## D11 — Puppeteer-bundled Chromium for Karma tests · Accepted

- **Decision:** Run Karma against Puppeteer's bundled Chromium (via `frontend/scripts/karma-runner.js`)
  instead of a system-installed browser or a CI browser-setup action.
- **Context:** A system Chrome path differs across machines and CI runners; Puppeteer's bundled Chromium
  makes `npm test` hermetic everywhere with no CI-specific install step.
- **Consequence:** `frontend/karma.conf.js` launches a `ChromeHeadlessCI` custom launcher; `.github/
  workflows/ci.yml` needs no separate Chrome setup.

## D12 — Transaction-table row tooltip restores the spec's "hover" behavior · Superseded by D14

- **Decision:** `PROJECT_SPECIFICATION.md` Feature 2 shows activity details "when selecting ... or
  hovering," but `PHASE_2.md` only describes the detail card appearing "on selecting." Phase 2 reconciles
  both: the full `TransactionDetailDto` card stays select-only, and each row additionally gets a
  `matTooltip` with a one-line summary (status, amount, currency).
- **Context:** Identified in `PHASE_2_PLAN.md` Clarification #5 as a gap between the two precedence
  layers, not a hard contradiction.
- **Consequence:** `TransactionTableComponent`'s row template binds `[matTooltip]` to a `rowSummary()`
  helper; no new dependency (`MatTooltipModule` already ships with `@angular/material`).

## D13 — Temporary permit-all `SecurityFilterChain` until Phase 5 · Accepted

- **Decision:** Add a minimal `SecurityConfig` (`permitAll()` on every request) from Phase 2 onward, until
  Phase 5 replaces it with real OAuth2/OIDC login and role-based access.
- **Context:** Phase 1 added `spring-boot-starter-security-oauth2-resource-server` in preparation for
  D2/Phase 5. With that starter on the classpath and no security bean, Spring Security's default-deny
  posture would `401` every request, making Phases 2–4 unusable before Phase 5 exists — which
  `PHASE_2.md` implies is fine by scoping auth "Out" until Phase 5.
- **Consequence:** All endpoints are unauthenticated through Phase 2–4; `SecurityConfig` is expected to be
  replaced, not extended, once Phase 5 implements D2.

## D14 — Row expand-to-detail supersedes the D12 hover tooltip · Accepted

- **Decision:** Phase 2 EXT replaces the bottom-of-page transaction detail panel with an inline,
  click-to-expand table row (the full `TransactionDto` detail rendered beneath its owning row). D12's
  `matTooltip` row summary is removed.
- **Context:** D12's hover summary existed to restore the spec's "hovering" affordance without duplicating
  the full detail card, which at the time required a click plus a scroll. Once the full detail is one
  click away inline, the tooltip adds no benefit.
- **Consequence:** `TransactionTableComponent` no longer binds `matTooltip`/`rowSummary()`; clicking a row
  toggles an inline expanded detail row (Angular Material's `multiTemplateDataRows` pattern).
  `MatTooltipModule` is no longer used by this component.

## D15 — Chart.js + `ng2-charts` for the analytics graph · Accepted

- **Decision:** Render the Phase 3 analytics time series with Chart.js via `ng2-charts`, instead of
  `@swimlane/ngx-charts`.
- **Context:** `CLAUDE.md` names no charting library and Angular Material ships no chart component.
  `ngx-charts` is D3-based and heavier for a single bar/line chart; Chart.js is canvas-based and smaller,
  with `ng2-charts` providing a first-class standalone-component (`BaseChartDirective`) wrapper.
- **Consequence:** `frontend/package.json` adds `chart.js` and `ng2-charts`; `app.config.ts` calls
  `provideCharts(withDefaultRegisterables())`; `AnalyticsChartComponent` wraps `<canvas baseChart>`,
  rendering a bar chart for transaction counts and a multi-series line chart (one line per currency) for
  amount sums.

## D16 — Config-driven range↔granularity constraints, exposed via a dedicated endpoint · Accepted

- **Decision:** Move the analytics range↔granularity bounds (previously hardcoded in
  `Granularity.isRangeValid`) into a `@ConfigurationProperties(prefix = "app.analytics.range-constraints")`
  record (`AnalyticsRangeProperties`), fail-fast validated at startup. Expose the active bounds read-only
  via `GET /api/v1/analytics/range-constraints` (`AnalyticsConfigController`), and carry the same bound
  data as RFC 7807 `ProblemDetail` extension properties on the rejected-range `400`.
- **Context:** The user asked for configurable bounds and frontend pre-validation (disable invalid
  granularities, constrain the datepicker, show a human error) instead of only reacting to raw `400` text.
  A dedicated `RangeConstraintDto` (plain `String` unit names) was used instead of the internal
  `AnalyticsRangeProperties.Bound` type because `ChronoUnit`'s overridden `toString()` ("Days") is picked
  up by Jackson's JSR-310 module ahead of the generic enum serializer — inconsistent with every other
  enum in this API. Caught by `AnalyticsConfigControllerTest`, confirmed via `javap` against the resolved
  Jackson jars.
- **Consequence:** `Granularity` no longer owns validity logic; `AnalyticsService` reads bounds from
  `AnalyticsRangeProperties`. `application.yml` gains the project's first custom `app.*` namespace. The
  frontend adds `AnalyticsConfigService` and a pure `range-constraint.util.ts` (mirrors
  `LocalDate.plus(amount, ChronoUnit)` semantics) that `AnalyticsPanelComponent` uses to disable
  out-of-range granularities, bound the datepicker, and render structured inline errors.

## D17 — RAG over risk rules/history is structured DB filtering, not vector search · Accepted

- **Decision:** Implement "RAG" (`PROJECT_SPECIFICATION.md` Feature 9, `PHASE_4.md`) as structured,
  filtered database reads — `risk_rules` filtered by `applies_to IN (activityType, 'ALL')`, plus the
  transaction's own prior `risk_final_assessments` — injected verbatim into the prompt, rather than an
  embedding model + vector store.
- **Context:** `risk_rules` is a small, structured, operator-curated table (dozens of rows), not an
  unstructured document corpus; `PHASE_4.md` narrows this phase's RAG sources to "risk rules + prior
  assessments." A vector store would be an unnecessary abstraction for this data shape.
- **Consequence:** `risk/RiskRuleRetrievalService` and `risk/AssessmentHistoryRetrievalService` are plain
  repository queries; no embedding/vector-index dependency added. Introduced in Phase 4.

## D18 — Single OpenAI-shaped AI client behind a swappable interface · Fulfilled by D19

- **Decision:** Implement the "configurable AI Provider" of Feature 5 as one concrete
  `risk.ai.OpenAiRiskAssessmentAiClient` (Spring AI `ChatClient` over `spring-ai-starter-model-openai`)
  behind a `risk.ai.RiskAssessmentAiClient` interface, with model name and provider label externalized as
  config — not literal multi-provider wiring.
- **Context:** WireMock stubs the OpenAI-shaped HTTP contract regardless of the configured provider label
  (D4), so a second real provider SDK would add maintenance surface with no acceptance criterion
  exercising it. The interface seam makes a second provider a future addition, not a rewrite.
- **Consequence:** `risk/ai/RiskAssessmentAiClient` is the only extension point. Introduced in Phase 4.

## D19 — Multi-provider AI selection via `@ConditionalOnProperty` + concrete-`ChatModel` injection · Accepted

- **Decision:** `app.ai.provider` (`openai`/`anthropic`) now selects the active `RiskAssessmentAiClient`
  bean — `OpenAiRiskAssessmentAiClient` and the new `AnthropicRiskAssessmentAiClient` are each
  `@ConditionalOnProperty(prefix = "app.ai", name = "provider", ...)`, `openai` keeping
  `matchIfMissing = true` as the default. Each implementation injects its concrete `OpenAiChatModel`/
  `AnthropicChatModel` bean and reports its own model name; `AiRiskAssessmentOrchestrator` logs whichever
  model the active client reports instead of reading a provider-specific `@Value`.
- **Context:** Fulfills D18's seam — the user has a real Anthropic subscription and asked to try it,
  including WireMock record-mode support. With both OpenAI and Anthropic starters on the classpath, Spring
  AI no longer autoconfigures a single unqualified `ChatClient.Builder` (ambiguous between the two
  `ChatModel`s) — confirmed via `javap` — hence the concrete-type injection.
- **Consequence:** A third provider needs one more conditional client plus its own config block;
  `AiRiskAssessmentOrchestrator` and its callers are untouched. Details:
  `docs/development/PHASE_4_EXT_PLAN.md` Clarification #5/#6. Introduced in Phase 4 EXT.

## D20 — Risk assessment history is a per-transaction popup, not a customer-wide tab · Accepted

- **Decision:** "View Risk Assessments History" opens a `MatDialog` popup (the project's first use of
  Angular Material's dialog) showing a flat table of that transaction's past assessments. It is not a
  routed page or top-level tab, and is not scoped to the whole customer.
- **Context:** An earlier iteration built a customer-wide "Risk Assessments" tab, contradicting
  `PROJECT_SPECIFICATION.md`'s requirement for history **per transaction** and the user's explicit UX
  correction: trigger and result render as their own card beside Transaction Details, reached via a
  closable popup, not a nav destination.
- **Consequence:** `RiskAssessmentHistoryDialogComponent` wraps `RiskAssessmentHistoryTableComponent`
  (`transactionId` required, no `transactionId` column). The backend's optional `transactionId` query
  param on `GET /customers/{id}/ai-assessments` is unchanged. Future closable-popup UI should reuse
  `MatDialog`. Introduced in Phase 4 EXT 2, correcting Phase 4 EXT.

## D21 — Administration section visibility is frontend-admin-gated, independent of the backend's own (more permissive) read access · Accepted

- **Decision:** The Angular "Administration" section (nav link, `/administration` route, risk-rule table)
  is gated to the `ADMIN` realm role via `adminGuard` and `authService.isAdmin()`, even though the
  backend's `GET /api/v1/risk-rules` stays `Operator`-level (any authenticated user), per `PHASE_5.md`'s
  API table.
- **Context:** D2 covers the backend read/write role split but says nothing about frontend visibility.
  `PHASE_5.md` separately asks for "admin-only visibility of the Administration section" while listing
  `GET /risk-rules` as `Operator`-level — both are honored: the backend endpoint stays the more permissive
  of the two, while the frontend only surfaces the section to `ADMIN` users, since its sole purpose here
  (risk-rule management) is `ADMIN`-only anyway. `adminGuard` is a UX gate, not the security boundary.
- **Consequence:** `AdministrationPageComponent`/`RiskRulesTableComponent` render edit/delete controls only
  when `authService.isAdmin()` is true; a non-admin calling `/api/v1/risk-rules` directly still gets `200`,
  by design. Introduced in Phase 5.

## D22 — Flyway SQL migrations/seed excluded from SonarCloud analysis · Accepted

- **Decision:** Exclude `backend/src/main/resources/db/**` (Flyway `V*`/`R__` files) from SonarCloud via
  `sonar.exclusions`, rather than editing the SQL or triaging findings individually.
- **Context:** 30 of 39 open MAINTAINABILITY issues were `plsql:VarcharUsageCheck`/`plsql:S1192` findings
  against these Postgres files — SonarCloud has no Postgres analyzer, so `.sql` defaults to Oracle-oriented
  PL/SQL rules that flag idiomatic Postgres `VARCHAR` and routine repeated seed literals as smells.
- **Consequence:** These files are out of scope for static analysis going forward; no SQL content changed.
  Introduced in Phase 6.

## D23 — `risk_level` is computed on read, not persisted · Supersedes D6 (partially)

- **Decision:** Drop `risk_final_assessments.risk_level` as a stored column. Every read path (SSE
  completion, assessment-history DTO, RAG history-context prompt block, and the history endpoint's
  `riskLevel` filter) computes the categorical level on demand from the persisted `risk_score`, using the
  configurable `RiskAssessmentProperties.levelThresholds` (`app.risk.level-thresholds`).
- **Context:** D6 made `risk_level` a stored column when the score→level mapping was effectively fixed.
  Once thresholds became runtime-configurable, a persisted `risk_level` goes stale as soon as an operator
  changes `app.risk.level-thresholds` — historical rows would keep the level computed under whatever
  thresholds were active at insert time. `PHASE_5_EXT_2.md` asks for it to be dropped and computed for
  display only, which changes the data model and so requires this recorded decision.
- **Consequence:** `RiskFinalAssessment` and the Flyway schema lose `risk_level`; every consumer calls
  `RiskAssessmentProperties.levelFor(...)` instead of reading a column. Changing
  `app.risk.level-thresholds` now retroactively changes the displayed level for every existing assessment —
  intended, not a defect. `PROJECT_SPECIFICATION.md` marks `risk_level` as derived. Introduced in Phase 5
  EXT_2.

## D24 — PII guardrail is advisory (logs, never blocks) · Supersedes part of PHASE_5_EXT_2's original design

- **Decision:** `PiiGuardrailService`'s scan result never aborts an assessment. A pattern match logs a
  `WARN` naming the violated pattern (never the matched value); the pipeline proceeds to the model call and
  persistence as normal.
- **Context:** This guardrail originally shipped as a hard block (`FAILED`, no persistence/model call) on a
  match. Manual verification against seeded demo data found it blocked every assessment for
  `transactionId=c0000000-...-001`: the `CARD_PAN` regex's optional `-` separator let it bridge across the
  UUID's hyphen-delimited hex groups, matching a 16-digit span across a mostly-zero demo ID — a required,
  non-PII structural field. Building a precise, blocking-capable PAN/IBAN detector (Luhn validation,
  boundary-aware exclusions) was judged unnecessary complexity for a second line of defense behind an
  allow-list (`PromptContextMapper`, Phase 4) that already keeps real PII out of the common case — the
  user's direction was to keep it a prompt-quality guideline, not a hard gate.
- **Consequence:** `AiRiskAssessmentOrchestrator`'s `GUARDRAIL_CHECK` stage still runs and emits its SSE
  token, but a match's only effect is the `WARN` log — a live signal that `PromptContextMapper`'s allow-list
  may need review, not a runtime safety gate. Amended in Phase 5 EXT_2 post-completion.

## D25 — Residual frontend devDependency vulnerabilities accepted, not force-fixed · Accepted

- **Decision:** Leave the 10 `npm audit` findings against the frontend (7 moderate, 3 high — `image-size`,
  `qs`, `uuid`, several transitive levels under `@angular-devkit/build-angular`) in place rather than
  resolving via `npm audit fix --force`.
- **Context:** Phase 6 asked for the frontend to be "free of ... vulnerabilities." A non-forced `npm audit
  fix` makes no change — every fix path requires a breaking major bump to `@angular-devkit/build-angular`.
  All 10 findings sit exclusively in `devDependencies` used by the build/test toolchain — none ship in the
  production bundle. Forcing a breaking Angular CLI bump to chase transitive dev-tool CVEs is a worse
  trade than the residual risk.
- **Consequence:** These findings are expected to clear naturally on the next `@angular-devkit/
  build-angular` upgrade, not tracked as a recurring task. Introduced in Phase 6.

## D26 — Default `app.ai.provider` flips from `openai` to `anthropic` · Supersedes part of D19

- **Decision:** `application.yml`'s `app.ai.provider: ${AI_PROVIDER:...}` default changes from `openai` to
  `anthropic`, so `./gradlew dev` plays back recorded Anthropic WireMock sessions out of the box.
- **Context:** D19 kept `OpenAiRiskAssessmentAiClient`'s `matchIfMissing = true` as the original default
  because OpenAI was simply the first provider implemented, not a spec requirement (Feature 5 names no
  default). By Phase 7 the offline demo's Anthropic WireMock fixtures are far more built out (15 recorded
  scenarios + a fallback, vs. OpenAI's single stub), and ongoing development targets a real Anthropic
  subscription.
- **Consequence:** `OpenAiRiskAssessmentAiClient`'s `matchIfMissing = true` becomes practically unreachable
  since `app.ai.provider` is always resolved to a concrete value — left as-is (harmless) rather than
  deleted. Explicitly setting `AI_PROVIDER=openai` still selects the OpenAI client. Introduced in Phase 7.
