# Customer Activity Analytics

Web application enabling Financial Operators to overview customer activity and perform AI-aided risk analysis.

The application consists of a Java/Spring Boot [backend](backend/README.md) and an
Angular/Node.js [frontend](frontend/README.md), managed as a Gradle multi-module project. Dependencies are centralized
in the [libs.versions.toml](gradle/libs.versions.toml) catalog.

## How to Run

Prerequisites: JDK 25 (managed via the Gradle toolchain), Docker Desktop (or another Docker daemon) running. Node is
provisioned automatically by Gradle for the frontend.

- **Run everything locally, one terminal:** `./gradlew dev` — starts Postgres (Docker Compose), the backend
  (`:8080`), and the frontend (`:4200`), multiplexed with colored `[docker]` / `[backend]` / `[frontend]` prefixes.
  Stop with Ctrl-C.
- **Verify:** `./gradlew check` — lint, tests, and coverage for both modules.
- **Build:** `./gradlew build`.
- Backend health: `http://localhost:8080/actuator/health`. Frontend: `http://localhost:4200`.
- AI risk assessments run offline by default, against a WireMock-stubbed LLM (`local-environment/wiremock/`) — no
  API key needed. `app.ai.provider` (`AI_PROVIDER` env var) selects `openai` (default) or `anthropic`; to use a real
  provider instead, set the matching `OPENAI_API_KEY`/`OPENAI_MODEL` or `ANTHROPIC_API_KEY`/`ANTHROPIC_MODEL` and
  clear that provider's `local`-profile `base-url` override (see `local-environment/wiremock/README.md`).

## Architecture

Gradle multi-module project:

- `backend` — Java 25, Spring Boot 4.1, Spring Data JPA + Flyway + PostgreSQL, Spring AI, OAuth2 resource server.
  Domain model (Phase 2): `customers` and polymorphic `transactions` (`CARD`/`PAYMENT`/`CRYPTO`, JPA `JOINED`
  inheritance), exposed under `/api/v1` — customer search, a paginated/filterable/sortable transaction overview, and
  per-transaction polymorphic detail. Phase 3 adds `GET /customers/{id}/analytics`: transaction count and
  amount-sum-by-currency, bucketed by day/week/month/year over a filterable range. The day/week/month/year
  range↔granularity bounds are configurable (`app.analytics.range-constraints` in `application.yml`, see
  `DECISIONS.md` D16) and exposed read-only via `GET /api/v1/analytics/range-constraints` for the frontend to drive
  its own validation UX; a rejected range returns a `ProblemDetail` `400` carrying structured bound/requested-range
  extension properties alongside a human-readable message. An omitted `from` or `to` is derived from the other side
  using the selected granularity's configured max span (never from an anchor unrelated to the provided side), capped
  so it never resolves into the future; omitting both defaults to month-to-date relative to the customer's own
  latest activity. Phase 4 adds an AI risk-assessment feature: `GET /customers/{id}/ai-assessments/stream` opens an
  SSE stream of typed progress tokens (`PROMPT_BUILDING`/`RULE_RETRIEVAL`/`HISTORY_RETRIEVAL`/`MODEL_CALL`/
  `COMPLETE`/`FAILED`) for a single transaction, then persists the outcome across two tables —
  `risk_final_assessments` (aggregate level/score/findings) and `risk_assessments` (per-rule line items with a
  `score_contribution = weight × relevance`, see `DECISIONS.md` D6); `GET /customers/{id}/ai-assessments` returns
  the paginated, per-column-filterable history. Risk rules and the transaction's own prior assessments are
  retrieved via structured DB filtering as RAG context (no vector store — D17) and sent to a Spring AI `ChatClient`
  behind a swappable `RiskAssessmentAiClient` interface (D18); the prompt never receives PII, account numbers, or
  wallet/tx identifiers — only categorical transaction signals (`risk/PromptContextMapper`). The model call and the
  SSE connection have independently configured, mutually consistent timeouts (`app.risk.*`), and persistence is
  decoupled from the SSE connection — an assessment completes and is saved even if the operator disconnects. Phase 4
  EXT makes the AI provider genuinely selectable (`app.ai.provider` = `openai`/`anthropic`, each with its own
  `RiskAssessmentAiClient` bean and Spring AI config block, D19) and sub-packages the `risk` backend package into
  `persistence`/`engine`/`api`/`ai`/`dto`. Phase 5 replaces the temporary `permitAll` `SecurityConfig` with real
  OAuth2/OIDC: every `/api/v1/**` endpoint now requires a valid Keycloak-issued JWT (resource-server, `jwk-set-uri`
  based — lazy JWKS fetch, so `./gradlew check` never needs a live Keycloak, see D2), and a
  `KeycloakRealmRoleConverter` maps the token's `realm_access.roles` claim to Spring Security authorities. Every
  request Spring Security rejects (missing/invalid token, insufficient role) is logged at WARN via a custom
  `AuthenticationEntryPoint`/`AccessDeniedHandler` in `SecurityConfig` — added after Phase 5 EXT's manual
  verification pass hit an SSE auth bug (below) that produced zero backend log output, since Spring Security's own
  rejection logging is DEBUG-only. `GET
  /api/v1/me` (new `user` package) projects the caller's claims/roles for the frontend header. `risk_rules` gains
  full CRUD (`risk/api/RiskRuleController`/`RiskRuleService`) — reads require any authenticated operator, writes
  (`POST`/`PUT`/`DELETE`) require the `ADMIN` realm role. Phase 5 EXT extends `GET /risk-rules` with `ruleName`/
  `thresholdLogic` (case-insensitive contains) and `minWeight`/`maxWeight` (range) filters, backed by a new
  `RiskRuleSpecifications` (`JpaSpecificationExecutor`), alongside the existing `appliesTo` filter — sorting by
  any of the four columns already worked for free via `Pageable`. It also adds `GET /api/v1/customers/{id}` (a
  single-customer lookup, reusing `CustomerService`'s existing 404 pattern) to support the frontend's deep-link
  fix below.
- `frontend` — Angular 22 + Angular Material, FontAwesome icons. Customer search (autocomplete), a server-driven
  transaction table with an activity-type filter, per-column sort/filter (icon-triggered popovers on each header),
  and inline click-to-expand row detail (Phase 2 / Phase 2 EXT). A pastel orange/white Material theme is applied
  app-wide. A customer's Transactions/Analytics views are separate, URL-synced routes
  (`customers/:customerId/transactions` and `.../analytics`, `mat-tab-nav-bar`-driven — both deep-linkable and
  refresh-safe, and switching customers via the header search preserves whichever tab is active) — with a shared
  `From`/`To` transaction-date filter also available directly on the Transactions tab's Date column (icon-triggered
  popover, matching every other filterable column). The Analytics view renders a compact date-range + granularity +
  aggregation-type toolbar above the chart (Chart.js via `ng2-charts`, see `DECISIONS.md` D15); an untouched `From`/
  `To` picker reflects the actual range being queried (including any side the backend computed on the caller's
  behalf), but once the operator explicitly sets or clears a side it stays exactly as they left it — including
  blank — across further reloads (e.g. while trying different granularities) until they change it again or switch
  customers, rather than being silently recomputed every time. Opening a blank side's calendar positions it at the
  boundary that would maximize the window (the same value the backend would use as that side's default), bounded
  bidirectionally by the other side and capped at today using the backend's configured constraints (D16); each
  field also has a "clear to default" affordance. Granularity's allowed-window legend appears on hovering its
  label; the secondary filters (activity type, status, currency, amount range, type-specific fields) collapse
  behind an icon that floats over the chart's top-right corner (reusing the transaction table's menu-popover
  pattern) and changes color when any are active. The chart scrolls horizontally when a range/granularity produces
  more buckets than fit its width. Switching customers resets both the Analytics pickers/touched-state and the
  Transactions date filter to that customer's own defaults. `ng serve` proxies `/api/**` to the backend via
  `frontend/proxy.conf.json`. Phase 4 adds a "Risk Assessment" card beside each transaction's own detail card
  (side by side, expanded from the same row — D14): "Run AI Risk Assessment" shows live SSE stage progress that
  replaces itself, in place, with the final risk-level/findings/recommendations (or a retry-able error) on
  completion. Phase 4 EXT adds multi-provider AI selection (backend); Phase 4 EXT 2 adds "View Risk Assessments
  History" to that same card, opening a closable popup (`MatDialog`, D20) with a paginated, per-column-filterable,
  flat table of that transaction's own past assessments. Phase 5 adds operator login: `angular-oauth2-oidc` drives
  an Authorization Code + PKCE flow against Keycloak, gating the entire app behind a valid session before any route
  renders (an `APP_INITIALIZER`-style `provideAppInitializer` redirects to Keycloak's login page up front, then
  calls `setupAutomaticSilentRefresh()` so a session outlives the ~5 min access-token lifetime through a full demo).
  The header shows the logged-in operator's name (via `GET /api/v1/me`) and a logout button (Keycloak front-channel
  logout); a new admin-only "Administration" section (nav link + route both gated to the `ADMIN` role, D21) hosts a
  paginated risk-rules table with create/edit (`MatDialog` form, D20 precedent) and delete, backed by the new
  `risk_rules` CRUD endpoints. Phase 5 EXT brings that table to parity with the transaction table and
  risk-assessment history table: per-column filter (icon-triggered menu on rule name, applies-to, threshold
  logic, and weight range) and `mat-sort-header` sorting on every column, replacing the earlier standalone
  "Applies To" toolbar dropdown. All three of those tables now share a single distinct-header style
  (`.table-header-cell` in `styles.scss`, chained with Material's own `.mat-mdc-header-cell` class to win the
  cascade against Material's own higher-specificity compiled rule). The header's customer-search box now only
  renders on Customer Analytics routes (hidden on `/administration`, driven by `AppComponent`'s own
  `NavigationEnd` subscription), correctly populates with the customer's name on a direct/bookmarked
  `/customers/{id}/**` load (not just an in-app selection, via the new `GET /api/v1/customers/{id}`), and its
  suggestion dropdown shows each customer's ID in parentheses to disambiguate same-named customers (and is a bit
  wider than before, `420px`, so typical full names don't feel cramped). That same manual verification pass
  surfaced an unrelated Phase 5 regression: `AiRiskAssessmentService.streamAssessment`'s native `EventSource` has
  no header-injection hook, so it couldn't carry the Bearer JWT Phase 5 made mandatory, and every "Run AI Risk
  Assessment" click silently 401'd with a generic "Connection lost" frontend error. Fixed by switching that
  stream to `HttpClient` (`reportProgress`/`observe: 'events'`, parsing `DownloadProgress`'s cumulative
  `partialText` as SSE framing) so it flows through the same `DefaultOAuthInterceptor` as every other call, rather
  than a token-in-URL workaround.
- `local-environment` — Docker Compose: PostgreSQL, WireMock serving canned AI responses for the offline demo (see
  `local-environment/wiremock/README.md` for the record-mode toggle), and (since Phase 5) Keycloak, provisioned
  declaratively from `local-environment/keycloak/realm-export.json` (`--import-realm`) — see
  `local-environment/keycloak/README.md` for the demo logins and how to re-export the realm.

CI (GitHub Actions) runs `./gradlew check` on every push/PR, with an optional SonarCloud pass when `SONAR_TOKEN` is
configured.

Durable architectural decisions — including every choice that goes beyond the assignment PDF — are tracked in
[DECISIONS.md](docs/DECISIONS.md).

### Assumptions

- Local/demo use only: default database credentials in `local-environment/docker-compose.yml` and
  `backend/src/main/resources/application.yml` are placeholders, overridable via environment variables — not
  intended for production deployment.
- AI risk assessments run against a WireMock-stubbed LLM by default (offline, deterministic demo, no API key
  needed). A real provider requires a real API key for the selected `app.ai.provider` (`OPENAI_API_KEY` or
  `ANTHROPIC_API_KEY`) and clearing that provider's `local` profile `base-url` override — see
  `local-environment/wiremock/README.md` for the record-mode toggle that captures new stubs from real provider
  responses, for either provider.
- Every `/api/v1/**` endpoint requires a valid Keycloak-issued OAuth2/OIDC JWT (D2, superseding the temporary
  `permitAll` `SecurityConfig` from D13); risk-rule writes additionally require the `ADMIN` realm role. Demo logins
  (`operator`/`password`, `admin`/`admin`) are provisioned in `local-environment/keycloak/realm-export.json`.
- Customer/transaction data is read-only and seeded for the demo (no create/update/delete endpoints); the seed
  dataset only loads under the `local` Spring profile (`./gradlew dev` sets this automatically). Risk rules gained
  full CRUD in Phase 5, gated to the `ADMIN` role for writes (reads stay open to any authenticated operator).
- Analytics aggregation (Phase 3) is computed in memory over an unpaged, already-filtered row fetch — no DB-side
  `GROUP BY`/indexes/materialized views yet, appropriate at the assignment's low-load/demo scale. See
  [PHASE_3_SCALING_NOTES.md](docs/development/PHASE_3_SCALING_NOTES.md) for the scale-up path.

## Implementation Journey

This project is implemented with the aid of AI tools and agents (the methodology is part of the assignment):

- **Gemini/ChatGPT/Claude Chatbot** — research, brainstorming, and refinement of the prompts driving the implementation.
- **Claude CLI** — the code implementation, driven by the specs in [docs/specs](docs/specs), the phase docs in
  [docs/development](docs/development), the commands in [.claude/commands](.claude/commands), and the project-wide
  guidelines in [CLAUDE.md](CLAUDE.md).

### Source of Truth

Precedence (highest wins), also enforced in [CLAUDE.md](CLAUDE.md):

```
sq_pe_assignment.pdf → PROJECT_SPECIFICATION.md → DECISIONS.md → PHASE_N.md → PHASE_N_PLAN.md → code
```

- [PROJECT_SPECIFICATION.md](docs/specs/PROJECT_SPECIFICATION.md) — technical requirements of record.
- [DECISIONS.md](docs/DECISIONS.md) — durable architectural and beyond-PDF decisions.
- [docs/development](docs/development) — per-phase definition (`PHASE_N.md`) and frozen plan (`PHASE_N_PLAN.md`).

### CLI Interactive Loop

Each phase is driven manually through Claude CLI. Commands take the phase id **without** the `.md` extension
(e.g. `PHASE_1`; a follow-up refinement scoped to an already-completed phase, such as a UX-only pass, uses an `_EXT`
suffix, e.g. `PHASE_2_EXT`, and runs through the identical loop below). The loop for a phase `N`:

1. **Plan** — `claude /plan-phase PHASE_N`
   Reads the spec, decisions, and `PHASE_N.md`; writes `docs/development/PHASE_N_PLAN.md`; sets `Status: PLANNED`;
   stops. Touches no source.

2. **Review the plan** — `claude /review PHASE_N plan`
   Audits the plan against the spec/decisions/phase. On `REJECTED: <reasons>`, refine and re-run `/plan-phase PHASE_N`
   (or `claude "fix docs/development/PHASE_N_PLAN.md: <reasons>"`). Loop until `APPROVED`.

3. **Implement** — `claude /implement PHASE_N`
   Reads `PHASE_N_PLAN.md`; writes Java/TypeScript, Flyway migrations, and seed scripts; runs `./gradlew check` and
   `npm test`; sets `Status: IMPLEMENTED`.

4. **Review the code** — `claude /review PHASE_N code`
   Inspects `git diff` and build/test output. On `REJECTED: <reasons>`, re-run `/implement PHASE_N`. Loop until
   `APPROVED`.

5. **Complete** — `claude /complete PHASE_N`
   Verifies acceptance criteria, freezes the plan (`Status: COMPLETE`), promotes durable knowledge into this README and
   `DECISIONS.md`, and sets the phase `Status: COMPLETE`.

6. **Commit** — `git add .` then
   `claude "Generate a conventional git commit message for the changes and commit."`

## LLMs & Agent Instructions (assignment deliverable)

_Summary of the LLM provider/models used and the agent instructions given — maintained here for the assessment._
