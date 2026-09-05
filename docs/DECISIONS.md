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

## D16 — Config-driven range↔granularity constraints, exposed via a dedicated endpoint · Accepted
- **Decision:** PHASE_3_EXT round 2 moves the analytics range↔granularity bounds (previously hardcoded per constant
  in `Granularity.isRangeValid`) into a `@ConfigurationProperties(prefix = "app.analytics.range-constraints")` record
  (`AnalyticsRangeProperties`, bound via `@ConfigurationPropertiesScan`), with fail-fast `@PostConstruct` validation
  that every `Granularity` has a configured bound. The active bounds are exposed read-only via a new
  `GET /api/v1/analytics/range-constraints` endpoint (`AnalyticsConfigController`), returned as a
  `Map<Granularity, RangeConstraintDto>`. The rejected-range `400` is built with `ResponseStatusException` and
  carries the same bound data as RFC 7807 `ProblemDetail` extension properties (`granularity`, `minAmount`,
  `minUnit`, `maxAmount`, `maxUnit`, `requestedFrom`, `requestedTo`) alongside a human-readable `detail` string, so
  the frontend can render its own inline message from structured data instead of parsing backend prose.
- **Context:** the user asked for the bounds to become configurable and for the frontend to pre-validate (disable
  invalid granularities, constrain the datepicker, show a human error and a hover explainer) rather than only
  reacting to a raw `400` message. A dedicated `RangeConstraintDto` (plain `String` unit names, not `ChronoUnit`
  directly) was introduced instead of reusing `AnalyticsRangeProperties.Bound` as the wire type: `ChronoUnit`'s
  overridden `toString()` ("Days") is used by Jackson's JSR-310 module ahead of the generic enum serializer, so
  serializing `Bound` directly produced values inconsistent with every other enum in this API's JSON contract
  (upper-case `name()`, e.g. "DAYS"). This was caught by `AnalyticsConfigControllerTest` and confirmed via `javap`
  against the resolved `jackson-annotations`/`jackson-datatype-jsr310` jars before fixing.
- **Consequence:** `Granularity` no longer owns validity logic (`bucketStart`/`next` only); `AnalyticsService` reads
  bounds from `AnalyticsRangeProperties` and calls `Bound.isValid`. `application.yml` gains the project's first
  custom `app.*` namespace. The frontend adds `AnalyticsConfigService` (fetches the constraints once) and a pure
  `range-constraint.util.ts` (mirrors `LocalDate.plus(amount, ChronoUnit)` semantics, including month/year-end
  clamping) that `AnalyticsPanelComponent` uses to disable out-of-range granularity options, bound the `to`
  datepicker's `[min]`/`[max]`, render a tooltip summarizing all configured windows, and render any surviving `400`
  as an inline structured message instead of raw backend text.

## D17 — RAG over risk rules/history is structured DB filtering, not vector search · Accepted

- **Decision:** The Phase 4 AI risk assessment implements "RAG" (`PROJECT_SPECIFICATION.md` Feature 9,
  `PHASE_4.md` Requirements) as structured, filtered database reads — `risk_rules` filtered by
  `applies_to IN (activityType, 'ALL')`, and the transaction's own prior `risk_final_assessments` — injected
  verbatim into the prompt, rather than an embedding model + vector store.
- **Context:** `risk_rules` is a small, structured, operator-curated table (dozens, not thousands, of rows), not
  an unstructured document corpus; `PHASE_4.md`'s own Scope narrows this phase's RAG sources to "risk rules +
  prior assessments" (Feature 9's broader "policies and regulations" sources are deferred, not overridden). A
  vector store would be a genuinely unnecessary abstraction (`CLAUDE.md` Coding Standard #3) for this data shape.
- **Consequence:** `risk/RiskRuleRetrievalService` and `risk/AssessmentHistoryRetrievalService` perform plain
  repository queries; no embedding model, vector index, or similarity-search dependency was added. Introduced in
  Phase 4 (`docs/development/PHASE_4_PLAN.md` Clarification #1).

## D18 — Single OpenAI-shaped AI client behind a swappable interface · Fulfilled by D19

- **Decision:** The "configurable AI Provider" of `PROJECT_SPECIFICATION.md` Feature 5 is implemented as one
  concrete `risk.ai.OpenAiRiskAssessmentAiClient` (Spring AI `ChatClient` over `spring-ai-starter-model-openai`)
  behind a `risk.ai.RiskAssessmentAiClient` interface, with the model name and a provider label externalized as
  configuration (`spring.ai.openai.chat.options.model`, `app.ai.provider`) — not literal multi-provider wiring
  (e.g. a second real provider SDK).
- **Context:** WireMock stubs the OpenAI-shaped HTTP contract regardless of which "provider" label is configured
  (D4), so a second real provider SDK would add real dependency/maintenance surface with no acceptance criterion
  exercising it. The interface seam means a second provider is a future implementation, not a rewrite.
- **Consequence:** `risk/ai/RiskAssessmentAiClient` is the only extension point; swapping providers later means
  adding an implementation and a config switch, not touching `AiRiskAssessmentOrchestrator` or its callers.
  Introduced in Phase 4 (`docs/development/PHASE_4_PLAN.md` Clarification #4).

## D19 — Multi-provider AI selection via `@ConditionalOnProperty` + concrete-`ChatModel` injection · Accepted

- **Decision:** `app.ai.provider` (`openai` or `anthropic`) now genuinely selects which `RiskAssessmentAiClient`
  bean is active — `OpenAiRiskAssessmentAiClient` and the new `AnthropicRiskAssessmentAiClient` are each
  `@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = ...)`, with `openai` also
  `matchIfMissing = true` to preserve the long-standing default. Each implementation injects the concrete
  `OpenAiChatModel`/`AnthropicChatModel` bean rather than the generic `ChatClient.Builder`, and reports its own
  active model name via `RiskAssessmentAiClient.modelName()`. `AiRiskAssessmentOrchestrator` no longer injects any
  provider-specific `@Value` (previously `spring.ai.openai.chat.options.model`) — it logs whichever model the
  active client reports.
- **Context:** Fulfills D18's stated seam — the user has a real Anthropic subscription and asked to try it,
  including WireMock record-mode support (documented in `local-environment/wiremock/README.md`). Verified
  empirically (`javap` against resolved JARs) that with both `spring-ai-starter-model-openai` and
  `spring-ai-starter-model-anthropic` on the classpath, Spring AI no longer autoconfigures a single unqualified
  `ChatClient.Builder` bean (ambiguous between the two `ChatModel`s), which is why each client now injects its
  provider's concrete `ChatModel` type directly instead. `spring.ai.model.chat` (both auto-configurations'
  `matchIfMissing = true` condition) is left unset, so both `OpenAiChatModel` and `AnthropicChatModel` beans are
  always constructed regardless of `app.ai.provider` — harmless, since neither performs a network call at
  construction; only the app's own `RiskAssessmentAiClient` selection is gated by `app.ai.provider`.
- **Consequence:** Adding a third provider means one more `@ConditionalOnProperty`-gated `RiskAssessmentAiClient`
  implementation and its own `spring.ai.<provider>.*` config block — `AiRiskAssessmentOrchestrator` and its
  callers are untouched. `docs/development/PHASE_4_EXT_PLAN.md` Design Clarification #5/#6 has the full analysis.
  Introduced in Phase 4 EXT.

## D20 — Risk assessment history is a per-transaction popup, not a customer-wide tab · Accepted

- **Decision:** "View Risk Assessments History" opens a `MatDialog` popup — the project's first use of Angular
  Material's dialog component — showing a flat table of *that transaction's* past assessments, closable without
  navigating away. It is not a routed page or top-level tab, and it is not scoped to the whole customer.
- **Context:** An earlier iteration briefly built a customer-wide "Risk Assessments" tab/route, which contradicted
  `docs/specs/PROJECT_SPECIFICATION.md`'s own requirement ("the operator should be able to visualize the history
  of all AI Risk Assessments **per transaction**") and the user's explicit UX correction: the trigger and its
  result render as their own card beside the Transaction Details card (not a full-width actions row), and history
  is reached from that card via a closable popup, not a nav destination.
- **Consequence:** `RiskAssessmentHistoryDialogComponent` wraps the existing `RiskAssessmentHistoryTableComponent`
  (`transactionId` required again, no `transactionId` column — every row already belongs to the transaction named
  in the dialog's context). The backend's `transactionId` query param on `GET /customers/{id}/ai-assessments`
  stays optional (unchanged, predates this phase) even though the frontend now always supplies it — no backend
  change needed. Future closable-popup UI in this app should reuse `MatDialog` for consistency.
  Introduced in Phase 4 EXT 2 (`docs/development/PHASE_4_EXT_2_PLAN.md`), correcting Phase 4 EXT.

## D21 — Administration section visibility is frontend-admin-gated, independent of the backend's own (more permissive) read access level · Accepted

- **Decision:** The Angular "Administration" section (nav link, `/administration` route, and its risk-rule table)
  is gated to the `ADMIN` realm role only — via `adminGuard` on the route and an `authService.isAdmin()` check on
  the nav link itself — even though the backend's `GET /api/v1/risk-rules` endpoint remains `Operator`-level (any
  authenticated user), per `docs/development/PHASE_5.md`'s own API table.
- **Context:** D2 already covers the *core* mechanism ("role-based access: read for all; admin/editor for
  risk-rule writes" at the backend), but does not say anything about which *frontend* users should ever see the
  Administration section itself. `PHASE_5.md`'s Testing Scope separately asks to verify "admin-only visibility of
  the Administration section," while its API table lists `GET /risk-rules`'s access level as `Operator`. Rather
  than silently picking one interpretation, both are honored simultaneously: the backend endpoint stays the more
  permissive of the two (any operator could still call it directly — e.g. a future admin tool, or `curl` with a
  valid token), while the frontend's own UX choice is to only surface the section to `ADMIN` users, since its sole
  purpose in this phase (risk-rule management) is something only `ADMIN` can act on anyway. The `adminGuard` is a
  UX gate, not the security boundary — the real boundary is the backend's `hasRole("ADMIN")` check on the three
  write verbs.
- **Consequence:** `AdministrationPageComponent`/`RiskRulesTableComponent` render their edit/delete controls only
  when `authService.isAdmin()` is true; a non-admin operator who reaches `/api/v1/risk-rules` directly (bypassing
  the UI) still gets a `200` read response, by design. Introduced in Phase 5
  (`docs/development/PHASE_5_PLAN.md` Clarification #7).

## D22 — Flyway SQL migrations/seed excluded from SonarCloud analysis · Accepted

- **Decision:** `backend/src/main/resources/db/**` (Flyway `V*`/`R__` migration and seed files) is excluded from
  SonarCloud analysis via `sonar.exclusions`, rather than editing the SQL to satisfy the findings or triaging them
  one by one in the SonarCloud UI.
- **Context:** D5 already put SonarCloud in CI as a quality gate. Once analysis actually ran, 30 of the project's
  39 open MAINTAINABILITY issues turned out to be `plsql:VarcharUsageCheck`/`plsql:S1192` findings against these
  Postgres migration/seed files — SonarCloud has no dedicated PostgreSQL analyzer, so `.sql` files default to its
  Oracle-oriented PL/SQL rules, which flag idiomatic Postgres `VARCHAR` as "should be `VARCHAR2`" (a type that
  doesn't exist in Postgres) and treat routine repeated literals in seed `INSERT`s as duplication smells.
- **Consequence:** These files are simply out of scope for static analysis going forward — not resolved
  issue-by-issue, since the underlying rule set doesn't fit the dialect and would keep re-flagging every future
  migration the same way. No SQL content was changed to accommodate this. Introduced in Phase 6
  (`docs/development/PHASE_6.md`).

## D23 — `risk_level` is computed on read, not persisted · Supersedes D6 (partially)

- **Decision:** `risk_final_assessments.risk_level` is dropped as a stored column. Every read path (the SSE
  completion payload, the assessment-history list DTO, the RAG history-context block injected into future
  prompts, and the history endpoint's `riskLevel` filter) computes the categorical level on demand from the
  persisted `risk_score`, using the already-configurable `RiskAssessmentProperties.levelThresholds`
  (`app.risk.level-thresholds`, introduced in Phase 4/4 EXT).
- **Context:** D6 originally reconciled `risk_level` into `PROJECT_SPECIFICATION.md`'s data model as a stored,
  categorical column alongside the numeric `risk_score`. At the time, the score→level mapping was effectively
  fixed. Once the mapping's thresholds became runtime-configurable, a persisted `risk_level` silently goes
  stale the moment an operator changes `app.risk.level-thresholds` — historical rows would keep showing the
  level computed under whatever thresholds were active when each row was inserted, diverging from the level
  the same `risk_score` would earn under the current configuration. `PHASE_5_EXT_2.md` asks for `risk_level`
  to be dropped and "compute[d] just for UI displaying," which changes the data model
  `PROJECT_SPECIFICATION.md` records — per `CLAUDE.md`'s precedence rules this requires this explicit,
  recorded decision rather than a silent implementation change.
- **Consequence:** `RiskFinalAssessment` and the Flyway schema lose the `risk_level` column; every consumer
  (SSE orchestrator, `AiRiskAssessmentHistoryService`, the RAG history-context prompt renderer,
  `RiskFinalAssessmentSpecifications`'s `riskLevel` filter) calls `RiskAssessmentProperties.levelFor(...)` (or
  an equivalent `risk_score` range translation for the filter) instead of reading a column. Changing
  `app.risk.level-thresholds` now retroactively changes the displayed level for every existing assessment on
  next read — the intended behavior, not a defect. `docs/specs/PROJECT_SPECIFICATION.md`'s
  `risk_final_assessments` table is updated to mark `risk_level` as derived, not stored. Introduced in Phase 5
  EXT_2 (`docs/development/PHASE_5_EXT_2.md`).

## D24 — PII guardrail is advisory (logs, never blocks) · Supersedes part of PHASE_5_EXT_2's original design

- **Decision:** `PiiGuardrailService`'s scan result never aborts an AI risk assessment. A pattern match logs a
  `WARN` naming the violated pattern (never the matched value) and the pipeline proceeds to the model call and
  persistence exactly as it would on a clean prompt.
- **Context:** `PHASE_5_EXT_2.md` originally shipped this guardrail as a hard block (`FAILED`, no persistence, no
  model call) on a match. In manual verification against the seeded demo data, this blocked every assessment for
  `transactionId=c0000000-0000-0000-0000-000000000001`: the `CARD_PAN` regex's optional `-` separator let it
  bridge across the UUID's hyphen-delimited hex groups, matching a 16-digit span across three groups of this
  mostly-zero demo ID — a required, non-PII structural field `PromptContextMapper` always includes. The phase's
  own "verified no false positive" analysis had only checked a single UUID hex group in isolation, missing that
  the regex can span across group boundaries. Building a precise, blocking-capable PAN/IBAN detector (Luhn
  validation, boundary-aware exclusions, etc.) was judged unnecessary complexity for a second line of defense
  behind an allow-list (`PromptContextMapper`, Phase 4) that already keeps real PII out of the prompt in the
  common case — the user's explicit direction was to keep the guardrail simple and treat it as a prompt-quality
  guideline, not a hard gate, deferring precise blocking to a future improvement if a real free-text PII surface
  is ever added.
- **Consequence:** `AiRiskAssessmentOrchestrator`'s `GUARDRAIL_CHECK` stage still runs and still emits its SSE
  progress token, but a match's only effect is the `WARN` log line — useful as a live signal that
  `PromptContextMapper`'s allow-list may need review, not as a runtime safety gate. Amended in Phase 5 EXT_2
  post-completion (`docs/development/PHASE_5_EXT_2.md`, Risks/Open Questions).

## D25 — Residual frontend devDependency vulnerabilities accepted, not force-fixed · Accepted

- **Decision:** The 10 `npm audit` findings against the frontend (7 moderate, 3 high — `image-size`, `qs`,
  `uuid`, each several transitive levels deep under `@angular-devkit/build-angular`) are left in place rather
  than resolved via `npm audit fix --force`.
- **Context:** Phase 6 asked for the frontend to be "free of ... vulnerabilities" (AC2). A non-forced `npm audit
  fix` makes no change — every fix path requires a breaking major bump to `@angular-devkit/build-angular` (or
  the Angular CLI toolchain it pulls in), which `npm audit fix --force` confirms is the only route. All 10
  findings sit exclusively in `devDependencies` used by the build/test toolchain (`less`'s image handling,
  `karma`'s and `webpack-dev-server`'s bundled `body-parser`/`express`, `sockjs`) — none of this code ships in
  the production Angular bundle. Forcing a breaking Angular CLI bump to chase transitive dev-tool CVEs risks
  destabilizing the build for a vulnerability surface that is never deployed, which is a worse trade than the
  residual risk itself.
- **Consequence:** These 10 findings remain open in `npm audit` and are expected to clear naturally the next
  time `@angular-devkit/build-angular` is upgraded to a release line with patched transitive dependencies —
  not tracked as a recurring manual task. If a future Angular upgrade is done for other reasons, re-running
  `npm audit fix` at that point is the natural moment to re-check. Introduced in Phase 6
  (`docs/development/PHASE_6.md`).

## D26 — Default `app.ai.provider` flips from `openai` to `anthropic` · Supersedes part of D19

- **Decision:** `backend/src/main/resources/application.yml`'s `app.ai.provider: ${AI_PROVIDER:...}` default
  changes from `openai` to `anthropic`, so `./gradlew dev` plays back the recorded Anthropic WireMock sessions
  out of the box with no environment variables set.
- **Context:** D19 introduced genuine multi-provider selection and explicitly kept `OpenAiRiskAssessmentAiClient`'s
  `@ConditionalOnProperty(..., matchIfMissing = true)` "to preserve the long-standing default" — at the time,
  OpenAI was simply the first (and, until Phase 4 EXT, only) provider implemented, not a requirement from
  `docs/specs/PROJECT_SPECIFICATION.md` (Feature 5 asks only for "a configurable AI Provider," naming no default).
  By Phase 7, the offline demo's WireMock fixture set (`local-environment/wiremock/mappings/anthropic-messages*
  .json`) is substantially built out for Anthropic (15 recorded scenarios + a generic fallback, vs. OpenAI's
  single stub), and the developer's ongoing work targets a real Anthropic subscription — defaulting to
  `anthropic` matches where both the recorded fixtures and active development already point.
- **Consequence:** `OpenAiRiskAssessmentAiClient`'s `matchIfMissing = true` becomes effectively unreachable in
  normal operation, since `app.ai.provider` is now always resolved to a concrete value (`anthropic` by default,
  or an explicit `AI_PROVIDER` override) and is never literally absent from the Spring `Environment` — the
  annotation is left as-is (harmless, and still correctly restores the old default if the property line is ever
  removed from `application.yml` entirely) rather than deleted, to avoid an unrelated behavior change. Explicitly
  setting `AI_PROVIDER=openai` continues to select the OpenAI client exactly as before. Introduced in Phase 7
  (`docs/development/PHASE_7.md`).
