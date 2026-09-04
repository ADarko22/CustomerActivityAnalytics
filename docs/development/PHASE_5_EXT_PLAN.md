# Phase 5 EXT Implementation Plan — Administration Table Filtering/Sorting, Header Styling, Customer-Search Fixes

**Status:** IMPLEMENTED

Blueprint for `PHASE_5_EXT.md`. Brings the risk-rules table up to parity with the transaction table and
risk-assessment history table (per-column filter + sort), introduces a shared distinct-header style across all
three, fixes three customer-search usability gaps (Administration visibility, deep-link name population, bracketed
suggestion IDs), and a light, targeted code-cleaning pass. Read alongside `CLAUDE.md` (conventions) and
`docs/DECISIONS.md` (D21 applies unchanged — the Administration section's frontend-only admin gating is untouched
by this phase).

## Current State (verified)

- `risk-rules-table.component.ts`/`.html` supported only a single `appliesTo` toolbar dropdown filter and a
  hardcoded, unchangeable sort — unlike `transaction-table` and `risk-assessment-history-table`, which both
  already have the established per-column filter-icon-and-menu + `mat-sort-header` pattern (debounced `Subject`,
  a `*.columns.ts` file externalizing a `ColumnDef[]`, a generic filter-object service method).
- `RiskRuleRepository` extended only `JpaRepository`, not `JpaSpecificationExecutor`; `RiskRuleService`/
  `RiskRuleController` supported only `appliesTo` filtering. Sorting by any mapped field already worked for free
  via `Pageable.getSort()` — confirmed by the pre-existing `@PageableDefault(sort = "ruleName")` — so only
  filtering needed new backend surface.
- No table anywhere in the app visually distinguished header cells from body cells beyond Material's own M3
  defaults (`transaction-table.component.scss`/`risk-assessment-history-table.component.scss` only had
  `white-space: nowrap` on headers).
- `<app-customer-search>` rendered unconditionally in `app.component.html`'s global header, on every route
  including `/administration`, where it has no meaning.
- `CustomerSearchComponent`'s `searchControl` was only ever populated via an active dropdown selection
  (`onCustomerSelected`) — nothing read the `:customerId` route param, so a direct/bookmarked navigation to
  `/customers/{id}/transactions` left the box blank. No `GET /api/v1/customers/{id}` endpoint existed to support
  fixing this.
- The suggestion dropdown rendered only `{{ firstName }} {{ lastName }}`, with no ID shown.
- `npm run format:check` found exactly one pre-existing Prettier violation
  (`transactions-page.component.spec.ts`); everything else (Spotless, ESLint, `tsc --strict`) was already clean.

## Design clarifications

1. **The standalone "Applies To" toolbar dropdown is removed**, replaced by the `appliesTo` column's own
   filter-menu — keeping both would duplicate the same filter in two places, and neither reference table
   (`transaction-table`, `risk-assessment-history-table`) has a toolbar filter alongside per-column ones.
2. **The bracketed customer ID appears only in the suggestion dropdown**, not the closed/selected field —
   `displayCustomer()` stays name-only. The ask is about the dropdown disambiguating *before* selection; once
   selected or deep-linked, the ID is no longer needed.
3. **`GET /api/v1/customers/{id}` is a new, dedicated endpoint**, not a client-side reuse of the existing paged
   search endpoint — trivial given `CustomerRepository.findById` is already available, and it mirrors
   `CustomerService.requireExists`'s existing 404 pattern (`ResponseStatusException(HttpStatus.NOT_FOUND, ...)`).
4. **The shared header style lives in `frontend/src/styles.scss`** as a global, unencapsulated rule, applied by
   adding a class to each of the three tables' header cells — the same mechanism already used for
   `.legend-tooltip` in that file. **Selector specificity correction found during manual verification:** a bare
   `.table-header-cell` (specificity 0,1,0) was silently overridden by Angular Material's own compiled
   `.mat-mdc-table .mat-mdc-header-cell` rule (0,2,0) — confirmed via `getComputedStyle` showing the style simply
   never took effect despite the class being present and the rule existing in the stylesheet. Fixed by chaining
   `th.mat-mdc-header-cell.table-header-cell` (0,2,1), which reliably wins without `!important`.
5. **`AppComponent` owns route-awareness for both the Administration-visibility fix and the deep-link fix.**
   `CustomerSearchComponent` is declared outside `<router-outlet>`, so it cannot inject a useful `ActivatedRoute`
   itself (it would resolve to the root route, never the routed `customerId` segment). `AppComponent` subscribes
   once to `NavigationEnd`, derives `isAdministrationRoute` and the deepest route's `customerId` param by walking
   `route.root.firstChild`, and passes the latter down via a plain `[customerId]` input — keeping
   `CustomerSearchComponent` router-context-free.

## Backend Design

### `risk/persistence/RiskRuleSpecifications.java` (new)

Mirrors `CardActivitySpecifications`'s case-insensitive `LIKE` pattern for text fields and
`TransactionSpecifications`'s `BigDecimal` min/max range pattern:

```java
public static Specification<RiskRule> filter(
    RuleScope appliesTo, String ruleName, String thresholdLogic,
    BigDecimal minWeight, BigDecimal maxWeight)
```

### `risk/persistence/RiskRuleRepository.java` (modified)

Added `JpaSpecificationExecutor<RiskRule>` to the `extends` clause; removed the now-superseded
`findByAppliesTo(RuleScope, Pageable)` derived query (confirmed via grep it had no callers outside
`RiskRuleService`/its own test before removal). `findByAppliesToIn` (used by `RiskRuleRetrievalService` for RAG)
is untouched.

### `risk/api/RiskRuleService.java` / `risk/api/RiskRuleController.java` (modified)

`findAll` gained `ruleName`, `thresholdLogic`, `minWeight`, `maxWeight` params; the service always builds a
`Specification` via `RiskRuleSpecifications.filter(...)` and calls `riskRuleRepository.findAll(spec, pageable)`
(replacing the old `appliesTo != null ? findByAppliesTo(...) : findAll(...)` branch). No `SecurityConfig` change —
falls under the existing `anyRequest().authenticated()` rule, consistent with D21.

### `customer/CustomerController.java` / `customer/CustomerService.java` (modified)

Added `GET /api/v1/customers/{customerId}` → `CustomerService.findById`, reusing the existing `toDto` helper and
`ResponseStatusException(HttpStatus.NOT_FOUND, ...)` 404 pattern. No repository change — `findById` was already
free via `JpaRepository`.

### Backend tests

- New `risk/persistence/RiskRuleSpecificationsTest.java` (`@DataJpaTest` + `AbstractPostgresIntegrationTest`,
  mirroring `RiskFinalAssessmentSpecificationsTest`'s shape) — one test per predicate plus a combined-AND case.
  **Isolation note:** unlike `risk_final_assessments` (naturally scoped by a random `customerId` in its own
  test), `risk_rules` has no such key, and this suite's shared static Testcontainers Postgres instance already
  has rows left behind by `AiRiskAssessmentWireMockReplayTest` (a full `@SpringBootTest`, not auto-rolled-back).
  Every filter call here therefore also scopes on a per-row random UUID embedded in `ruleName`, making
  assertions immune to that pre-existing cross-test leakage without touching the leaking test's own
  infrastructure (out of scope for this phase).
- `risk/api/RiskRuleServiceTest.java` — the two old branch-assertion tests (`findByAppliesTo` vs `findAll`)
  replaced with tests asserting the `Specification`-based `findAll(any(Specification.class), eq(pageable))` call
  and correct DTO mapping.
- `risk/api/RiskRuleControllerTest.java` — added a param-passthrough test asserting all five query params reach
  the service.
- `customer/CustomerControllerTest.java` — added `findByIdReturnsCustomer`/`findByIdReturns404WhenMissing`/
  `findByIdReturns401WhenUnauthenticated`, matching the existing `jwt()`-based authorization-test convention.

## Frontend Design

### Risk-rules table

- `risk-rule.model.ts` gained `RiskRuleFilter { appliesTo?; ruleName?; thresholdLogic?; minWeight?; maxWeight?; }`.
- `risk-rule.service.ts`'s `list()` switched from a single named `appliesTo` param to a generic `filter` object,
  iterated into `HttpParams` — matching `TransactionService.findOverview`'s established shape exactly.
- New `risk-rules-table.columns.ts`: `RiskRuleColumnDef { key; label; filterType: 'text'|'select'|'weight'|'none';
  selectOptions?; }` and `RISK_RULE_COLUMNS` (ruleName: text, appliesTo: select, thresholdLogic: text, weight:
  weight-range, actions: none).
- `risk-rules-table.component.ts`/`.html` rewritten to the established pattern: `filters`/`sort` signals, a
  debounced (300ms) `filterChange$` `Subject`, `onFilterChange`/`clearFilter`/`clearWeightFilter`,
  `isFilterActive`, `onSortChange(sort: Sort)`, `matSort` on the table, a filter-icon `mat-icon-button` +
  `mat-menu` per filterable column (with the documented `$event.stopPropagation()` gotcha reproduced). Each
  `<th>` also gets `class="table-header-cell"`.
- `AdministrationPageComponent` needed no changes — it only binds `(edit)`/`(deleteRequested)`, neither of which
  changed shape.

### Shared header styling

`frontend/src/styles.scss` gained `th.mat-mdc-header-cell.table-header-cell { background-color: var(--mat-sys-
surface-container, #fdfaf7); font-weight: 600; border-bottom: 2px solid var(--mat-sys-primary, #8a4a00); }`
(see Design Clarification #4 for the specificity fix). `transaction-table.component.html` and
`risk-assessment-history-table.component.html` each got the one-line `class="table-header-cell"` addition to
their existing `<th mat-header-cell *matHeaderCellDef>` — no other change to either file.

### Customer-search fixes

- `app.component.ts` now injects `Router`/`ActivatedRoute`; a `NavigationEnd` subscription (via
  `takeUntilDestroyed()`) sets `isAdministrationRoute` and `routeCustomerId` signals. `app.component.html` wraps
  `<app-customer-search>` in `@if (!isAdministrationRoute())` and passes `[customerId]="routeCustomerId()"`.
- `customer.service.ts` gained `getById(customerId)` → `GET /api/v1/customers/{customerId}`.
- `customer-search.component.ts` gained `@Input() customerId?: string` and `implements OnChanges`: on a defined
  id, fetches and `searchControl.setValue(customer, { emitEvent: false })` (the `emitEvent: false` stops the
  programmatic set from re-triggering the suggestions pipeline); on undefined/404, resets to `''`.
- `customer-search.component.html`'s suggestion `<mat-option>` now renders
  `{{ firstName }} {{ lastName }} ({{ customerId }})`.

### Frontend tests

- `risk-rules-table.component.spec.ts` rewritten: default-sort assertion, pagination, sort-change, debounced
  `appliesTo`/`ruleName` filters (`fakeAsync`/`tick(300)`), min/max weight filters + `clearWeightFilter`, and
  admin-action visibility scoped to `[aria-label="Edit/Delete <ruleName>"]` (not a raw `button[mat-icon-button]`
  count, since filter-trigger buttons are now also `mat-icon-button`s in every row's header).
- `risk-rule.service.spec.ts` updated for the new filter-object signature, plus a combined-filters test.
- `customer-search.component.spec.ts` extended: `customerId` input → fetch-and-populate without a duplicate
  suggestions request, clears on unset, resets gracefully on a 404.
- `app.component.spec.ts` extended with real registered routes (`provideRouter([{path: 'administration', ...},
  {path: 'customers/:customerId/transactions', ...}])`) and `Router.navigateByUrl(...)`-driven tests: search box
  absent on `/administration`, present and passed the deep-linked `customerId` elsewhere.

## Code-Cleaning Pass

`npm run format:write` fixed the one known pre-existing violation
(`transactions-page.component.spec.ts`) plus formatted all newly-written files to the project's Prettier config.
No shared pagination/filter utility was extracted; `transaction-table`/`risk-assessment-history-table` received
no changes beyond the one-line header class, per the confirmed "light pass" scope.

## Post-Implementation Fixes (found during this phase's manual verification)

Manually verifying AC3/AC4 in a live browser (deep-linking into `/customers/{id}/transactions` as the
Administration-hidden customer-search box) led straight into using the app end-to-end, which surfaced a bug
outside this phase's original scope but blocking its own sign-off: triggering "Run AI Risk Assessment" failed
immediately with "Connection lost while assessing this transaction." and produced no backend log output at all.
Reproduced live: the browser's network tab showed the `GET .../ai-assessments/stream` request returning `401`.

**Root cause.** `AiRiskAssessmentService.streamAssessment` (unchanged since Phase 4) opens the SSE connection
with the native `EventSource` API. `EventSource` has no header-injection hook, so it cannot carry the Bearer JWT
that Phase 5's `SecurityConfig` (`anyRequest().authenticated()`) made mandatory on every `/api/v1/**` route —
unlike every other call in the app, which gets the token for free via `DefaultOAuthInterceptor`
(`app.config.ts`). Spring Security rejects the request in the filter chain before `AiRiskAssessmentController
.stream()` — which does log at INFO — ever runs, and Spring Security's own rejection logging is DEBUG-only
(silent at this project's default INFO root level), so the failure was completely invisible server-side.

### `frontend/src/app/core/services/ai-risk-assessment.service.ts` (rewritten)

`streamAssessment` no longer opens a raw `EventSource`. It now issues `this.http.get(url, { reportProgress: true,
observe: 'events', responseType: 'text' })` — Angular's fetch-backed `HttpClient` (`withFetch()`, already enabled
in `app.config.ts`) reports incremental chunks as `HttpDownloadProgressEvent`s carrying a cumulative
`partialText`. The service tracks a `processedUpTo` offset into that cumulative text and repeatedly extracts
complete `\n\n`-delimited SSE frames (`data: {...}`) as they become available, leaving any incomplete trailing
frame buffered for the next chunk — avoiding a `JSON.parse` on a frame that hasn't fully arrived yet. Going
through `HttpClient` means the request flows through `DefaultOAuthInterceptor` exactly like every other
authenticated call, with no token-in-URL workaround needed (rejected as a design option — tokens in URLs leak via
server access logs, browser history, and `Referer` headers). The now-unused `EventSourceFactory` injection point
was removed. Added `console.debug`/`console.error` logging (stream open / finish / failure, tagged
`[AiRiskAssessmentService]`, with customer/transaction context) per the broader "improve logging and tracking"
ask.

### `backend/.../config/SecurityConfig.java` (modified)

Added a custom `AuthenticationEntryPoint` bean (wired via `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...)
.authenticationEntryPoint(...))`) and `AccessDeniedHandler` bean (wired via `.exceptionHandling(ex -> ex
.accessDeniedHandler(...))`), each logging a WARN with the request method, URI, and rejection reason before
delegating the actual response to Spring's own `BearerTokenAuthenticationEntryPoint`/`BearerTokenAccessDeniedHandler`
(a shared private `logRejection` helper avoids duplicating the log call across both beans — see Code-Review Fixes
below for why delegating, not a bare `response.sendError(...)`, is the final shape). This closes the exact
diagnostic gap the SSE bug above depended on, for every endpoint — not just the SSE one. Verified no behavioral
regression: every existing 401/403 controller test (`RiskRuleControllerTest`, `CustomerControllerTest`,
`AiRiskAssessmentControllerTest`, `TransactionControllerTest`, `UserProfileControllerTest`,
`AnalyticsConfigControllerTest`, `AnalyticsControllerTest`) only asserts the status code, never the response body.

### `frontend/.../customer-search/customer-search.component.scss` (modified)

`.search-field`'s `width` increased `320px` → `420px` — spotted as cramped for typical full names during the
same manual verification pass.

### Tests

`ai-risk-assessment.service.spec.ts` rewritten around `HttpTestingController`/`TestRequest.event(...)` (the old
`FakeEventSource` test double no longer applies): emits progress events without closing, buffers an event split
across two `DownloadProgress` chunks until the delimiter arrives, completes and cancels the request on
`COMPLETE`/`FAILED`, and propagates a `401` as a stream error. `findHistory`'s existing coverage is unchanged.

## Code-Review Fixes

An 8-angle automated review of the full working-tree diff (line-by-line scan, removed-behavior audit, cross-file
tracing, reuse/duplication, simplification, efficiency, altitude, CLAUDE.md conventions), each candidate finding
independently re-verified, surfaced 9 confirmed issues. All fixed here, before `/complete`:

- **`frontend/.../customer-search/customer-search.component.ts` — race condition, redundant fetch, unsafe cast.**
  The `customerId` `@Input()`'s `ngOnChanges` called `customerService.getById(id).subscribe(...)` directly, with
  no cancellation of a previous in-flight call (unlike the constructor's own suggestions pipeline, which already
  used `switchMap`). Rapid navigation between two customers could let an out-of-order response overwrite the box
  with a stale name. Separately, selecting a customer from the dropdown already has the full `Customer` object,
  but the resulting navigation's `customerId` change re-fetched it anyway. And `searchControl.setValue(customer as
  unknown as string, ...)` erased type safety on a control that can hold either a `string` query or a `Customer`.
  Fixed together: `customerId` changes now flow through a `customerIdChange$` `Subject` piped through `switchMap`
  (cancels stale lookups) that also skips the fetch when `id` matches a `loadedCustomerId` tracked field;
  `onCustomerSelected` sets that field and the control's value directly (no round-trip for data already in hand);
  `searchControl` is typed `FormControl<string | Customer>`, with the suggestions pipeline's `valueChanges` piped
  through a `filter((v): v is string => typeof v === 'string')` type guard instead of the cast.
- **`frontend/.../risk-rules-table/risk-rules-table.component.html` — filter menu state not reflected.** The
  "Applies To" `mat-select` had no `[value]` binding (unlike the toolbar dropdown it replaced), so reopening the
  filter menu never showed the active selection. `clearFilter(key)` existed on the component but was never wired
  into the template, unlike the identical pattern in `transaction-table.component.html`. Fixed via a new
  `filterValue(key)` component method bound as `[value]` on both the select and the text filter inputs, and a
  `clearFilter(key, ...inputs)` signature (matching `transaction-table.component.ts`'s) wired to a `matSuffix`
  clear button on each text filter.
- **`backend/.../risk/persistence/RiskRuleSpecifications.java` — unescaped `LIKE` wildcards.** The `ruleName`/
  `thresholdLogic` filters built a `LIKE '%...%'` pattern by direct concatenation, so a filter value containing a
  literal `%` or `_` was interpreted as a SQL wildcard instead of a literal character (e.g. filtering by a rule
  named `high_value` would also match `highXvalue`). Fixed with the 3-arg `cb.like(expr, pattern, escapeChar)`
  overload and a local `containsPattern` helper that escapes `\`, `%`, and `_` before wrapping in `%...%`.
- **`backend/.../config/SecurityConfig.java` — dropped `WWW-Authenticate` header, duplicated logic.** The custom
  `AuthenticationEntryPoint` fully replaced Spring Security's default `BearerTokenAuthenticationEntryPoint`,
  silently dropping the RFC 6750 `WWW-Authenticate` challenge header every `401` response is expected to carry —
  and the two new handler beans were otherwise near-identical (same log-then-`sendError` shape, differing only in
  status/message). Fixed by having both beans log via a shared `logRejection` helper and then delegate the actual
  response to Spring's own `BearerTokenAuthenticationEntryPoint`/`BearerTokenAccessDeniedHandler`, which restores
  the header for free and collapses the duplication into one delegate-construction line per bean.

### Additional tests

`customer-search.component.spec.ts`: a stale-lookup-cancelled-by-navigation test (asserts the first request's
`TestRequest.cancelled`), a no-redundant-fetch-after-selection test. `risk-rules-table.component.spec.ts`: a
`clearFilter` test (param removed, native input blanked), a `filterValue` test. `RiskRuleSpecificationsTest.java`:
a `ruleNameFilterEscapesUnderscoreWildcard` test with a decoy row that an unescaped `_` would incorrectly match.
`RiskRuleControllerTest.java`: both the 401 and 403 tests now also assert `WWW-Authenticate` is present.

## Test Plan → Acceptance-Criteria Mapping

| `PHASE_5_EXT.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — risk-rules per-column filter/sort, no standalone toolbar control | `RiskRuleSpecificationsTest`, `RiskRuleControllerTest`'s param-passthrough test | `risk-rules-table.component.spec.ts` (sort, per-filter, combined) |
| AC2 — shared distinct header style across all three tables | — | Verified manually via `getComputedStyle` in a live browser session (Karma/JSDOM doesn't rasterize CSS specificity issues the same way — this class of bug is a real-browser-only verification) |
| AC3 — search box absent on `/administration` | — | `app.component.spec.ts` |
| AC4 — deep-link populates the customer name | `CustomerControllerTest` (`findById`) | `customer-search.component.spec.ts`, `app.component.spec.ts` |
| AC5 — bracketed customer ID in suggestions | — | Verified manually in a live browser session (a one-line interpolation change; not given a dedicated fragile CDK-overlay DOM test, consistent with how this codebase treats comparably trivial template-only changes) |
| AC6 — `./gradlew check` / `npm test` pass | ✓ | ✓ |
| AC7 — AI-assessment stream authenticates, no "Connection lost" | Existing `AiRiskAssessmentControllerTest` 401 case now reachable in practice, not just via MockMvc | `ai-risk-assessment.service.spec.ts`; verified manually end-to-end (real WireMock-backed assessment completed) |
| AC8 — auth rejections logged at WARN | Existing 401/403 controller tests confirm status codes unchanged; WARN log content verified manually against a live backend | — |
| AC9 — wider customer search field | — | Verified manually in a live browser session (one-line SCSS change) |

## Risks / Open Questions (resolved during implementation)

- **Header-style specificity bug** (Design Clarification #4) — caught only by live-browser `getComputedStyle`
  inspection during manual verification, not by any automated test (Karma/JSDOM doesn't render Material's actual
  compiled CSS cascade the same way a real browser does). This is the concrete reason the manual browser pass in
  `PHASE_5_EXT.md`'s Testing Scope was load-bearing rather than a formality.
- **Cross-test data leakage into `risk_rules`** (Design Clarification, backend tests) — pre-existing
  characteristic of this suite's shared static Testcontainers Postgres instance, not introduced by this phase;
  worked around locally in the new test rather than fixed at the suite level, which would be a larger,
  out-of-scope change to established test infrastructure.
