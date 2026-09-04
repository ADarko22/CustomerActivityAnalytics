# Phase 5 EXT — Administration Table Filtering/Sorting, Header Styling, Customer-Search Fixes

**Status:** IMPLEMENTED
**Depends on:** `PHASE_5.md` (`COMPLETE`, frozen — not reopened). This phase extends/fixes the Administration
risk-rules table and the header's customer-search box introduced in Phase 5; the OAuth2/OIDC login flow,
role-based authorization, and risk-rule CRUD endpoints from Phase 5 are correct and unchanged.

## Objective

Phase 5's risk-rules table only filters by "Applies To" and has no user-driven column sorting, unlike the
transaction table and risk-assessment history table which both already support per-column filtering and
sorting. Separately, the header's customer-search box has three usability gaps: it shows on the Administration
section where it's meaningless, it doesn't populate with the customer's name when a customer URL is opened
directly (deep link), and its suggestion dropdown doesn't disambiguate same-named customers. This phase brings
the risk-rules table up to parity with the app's other tables, introduces a consistent header style across all
three tables, and fixes the three customer-search gaps — plus a light, targeted code-cleaning pass.

## Scope

- **In:**
  - Risk-rules table: per-column filtering (rule name, applies-to, threshold logic, weight) and per-column
    sorting, mirroring the transaction table's and risk-assessment history table's existing pattern.
  - A shared, visually distinct header style (bold text, tinted background, accent underline) applied to all
    three sortable/filterable tables in the app (transaction table, risk-assessment history table, risk-rules
    table) — not just the Administration one.
  - Customer-search box hidden on the Administration route; visible everywhere else.
  - Fix: a direct/bookmarked/refreshed navigation to `/customers/{id}/transactions` (or `/analytics`) populates
    the header's search box with that customer's name, not just selections made through the box itself.
  - Customer-search suggestion dropdown shows the customer ID in brackets alongside the name.
  - Light code-cleaning pass: fix the one known Prettier formatting violation; give the risk-rules table the
    same `*.columns.ts` structural convention the other two sortable tables already use.
  - Customer-search field sized comfortably wider so typical full names don't feel cramped.
  - Fix: the AI risk-assessment SSE stream (`GET .../ai-assessments/stream`) authenticates correctly — found
    during this phase's own manual browser verification pass, where triggering "Run AI Risk Assessment" produced
    a generic "Connection lost while assessing this transaction." error with **zero backend log output**.
  - Backend logging: every request rejected by Spring Security (missing/invalid token, insufficient role) is
    logged at WARN — the gap that made the SSE bug above silent in the first place.
- **Out:** No change to risk-rule CRUD write behavior (create/edit/delete dialog, validation, role gating), no
  change to the OAuth2/OIDC login flow itself (Keycloak config, roles, token issuance), no extraction of a shared
  pagination/filter utility across the three tables (each keeps its own independent component logic), no changes
  to the transaction table or risk-assessment history table beyond adding the one shared header CSS class.

## Functional Requirements

| Functionality | Description |
|---|---|
| Risk-rules per-column filter/sort | Each column (rule name, applies to, threshold logic, weight) has its own filter control behind a header icon, and clicking a column header sorts by that column — same interaction pattern as the transaction table. |
| Distinct table headers | Header cells read visibly different from cell values (weight/style/background) across all three tables in the app. |
| Scoped customer search | The header's customer-search box renders only on Customer Analytics routes (`/`, `/customers/:id/**`), not on `/administration`. |
| Deep-link customer name | Loading `/customers/{id}/transactions` or `/customers/{id}/analytics` directly (no prior in-app search) shows that customer's name in the search box. |
| Disambiguated suggestions | Each suggestion in the search dropdown shows `First Last (customerId)`. |
| Wider customer search field | The header's customer-search input is sized to comfortably fit typical full names. |
| Authenticated AI-assessment stream | Triggering an AI risk assessment streams progress over the same authenticated request path as every other API call — a missing/invalid token is never both rejected and unlogged. |
| Auth-rejection logging | Every request rejected for missing/invalid credentials or insufficient role is logged at WARN with method, URI, and reason. |

## API Additions (base path `/api/v1`)

| Method | Endpoint Path | Description | Access Level | Request | Response |
|--------|---------------|--------------|--------------|---------|----------|
| **GET** | `/customers/{customerId}` | Retrieves a single customer by ID | Operator | `None` | `200 OK`: `CustomerDto`, `404` if not found |
| **GET** | `/risk-rules` | *(extended)* Adds `ruleName` (contains), `thresholdLogic` (contains), `minWeight`/`maxWeight` (range) query params alongside the existing `appliesTo` | Operator | `?ruleName=&thresholdLogic=&minWeight=&maxWeight=&appliesTo=&page=&size=&sort=` | `200 OK`: `Page<RiskRuleDto>` (unchanged shape) |

## Acceptance Criteria

1. `GET /api/v1/risk-rules` accepts filters on all four data columns (`ruleName`, `appliesTo`, `thresholdLogic`,
   `weight` via `minWeight`/`maxWeight`) combinable with each other, and `sort` works for any of the four
   columns; the risk-rules table UI exposes all of this via per-column header controls (filter icon + menu,
   `mat-sort-header` on the label) — no standalone "Applies To" toolbar control remains once the per-column
   filter replaces it.
2. All three sortable tables (transaction table, risk-assessment history table, risk-rules table) render header
   cells with a shared, visually distinct style from their own cell values.
3. `<app-customer-search>` is absent from the rendered DOM on `/administration` and present on every other
   route.
4. Navigating directly to a `/customers/{id}/transactions` or `/customers/{id}/analytics` URL (simulating a
   fresh load/bookmark, not an in-app search selection) results in the header's search box displaying that
   customer's first and last name.
5. The search dropdown's suggestion list shows each customer as `First Last (customerId)`.
6. `./gradlew check` and `npm test` pass, including new/updated coverage for all of the above.
7. Clicking "Run AI Risk Assessment" against a valid transaction streams through to `COMPLETE`/`FAILED` without a
   spurious "Connection lost" error; the browser's network request for the stream endpoint carries a valid
   `Authorization` header.
8. An unauthenticated or role-forbidden request against any `/api/v1/**` endpoint produces a WARN-level backend
   log line identifying the method, URI, and rejection reason.
9. The customer-search field is visibly wider than the phase's original `320px` and still fits the header layout
   at standard viewport widths.

## Testing Scope

Backend: `RiskRuleControllerTest`/`RiskRuleServiceTest` cover the new filter params (individually and combined)
and confirm sorting by each column; a new `CustomerControllerTest` (or extension of an existing one) covers
`GET /customers/{id}` for found/not-found/unauthenticated cases, matching the authorization-matrix convention
established in Phase 5. Every existing 401/403 controller test (`RiskRuleControllerTest`, `CustomerControllerTest`,
`AiRiskAssessmentControllerTest`, `TransactionControllerTest`, `UserProfileControllerTest`,
`AnalyticsConfigControllerTest`, `AnalyticsControllerTest`) continues to assert the same status codes unchanged,
confirming the new `AuthenticationEntryPoint`/`AccessDeniedHandler` preserve response semantics while adding
logging.

Frontend: `risk-rules-table.component.spec.ts` covers per-column filter (debounced), sort, and the removed
toolbar control; `customer-search.component.spec.ts` covers the new `customerId` input (fetch-and-populate,
clear-on-unset, graceful-on-404) and the bracketed-ID suggestion rendering; `app.component.spec.ts` covers
route-based search-box visibility. `ai-risk-assessment.service.spec.ts` was rewritten around
`HttpTestingController` to cover the `HttpClient`-based stream (progressive chunk parsing, an event split across
a chunk boundary, `COMPLETE`/`FAILED` completion with request cancellation, and a propagated connection error),
replacing the old native-`EventSource` fake.

## Risks / Open Questions

- **AI-assessment SSE authentication bug (resolved).** Native `EventSource` (used by
  `AiRiskAssessmentService.streamAssessment`) cannot attach the Bearer JWT that Phase 5 made mandatory on every
  `/api/v1/**` route, so the stream request was rejected with a silent `401` before ever reaching the controller
  — the controller's own `log.info` never ran, and Spring Security's default rejection logging is DEBUG-only
  (silent at this project's INFO root level), so the failure produced **zero** backend log output. This was
  caught only by live-browser reproduction (network tab showing the `401`), not by any automated test, since the
  existing controller test suite already exercised the 401 path directly via MockMvc without going through a
  real `EventSource`. Fixed by switching the frontend to an `HttpClient`-based stream (routed through the
  existing `DefaultOAuthInterceptor`, same as every other authenticated call) instead of introducing a
  token-in-URL workaround, and by adding WARN-level logging on every Security rejection so this class of failure
  is never silent again.
- **Multi-angle code review (resolved).** An 8-angle automated review of the full working-tree diff (line-by-line,
  removed-behavior, cross-file, reuse, simplification, efficiency, altitude, CLAUDE.md conventions — each
  independently verified) surfaced 9 confirmed issues, all fixed:
  - `customer-search.component.ts`: the `customerId` input's lookup had no cancellation of a stale in-flight
    request (race condition on rapid navigation between customers) and no guard against re-fetching a customer
    already selected from the dropdown (redundant `GET` on every selection). Fixed with a `switchMap`-driven
    `customerIdChange$` pipeline (cancels stale lookups) plus tracking the already-loaded customer id
    (`onCustomerSelected` now also sets it directly, skipping the round-trip). The `searchControl.setValue(customer
    as unknown as string, ...)` type-erasing cast was also removed by typing the control `string | Customer` and
    filtering `valueChanges` to strings before it reaches the suggestions search.
  - `risk-rules-table.component.html`: the "Applies To" filter's `mat-select` had no `[value]` binding, so
    reopening the filter menu never showed the currently active selection (unlike every other filter). Fixed via a
    new `filterValue(key)` helper bound on both the select and the text filters; `clearFilter` was also wired to
    the template's text filters (it existed but was dead code, unlike the pattern it was meant to mirror in
    `transaction-table.component.ts`).
  - `RiskRuleSpecifications.java`: the new `ruleName`/`thresholdLogic` `LIKE` filters didn't escape `%`/`_`, so a
    filter value containing a literal `_` would match unrelated rows via SQL wildcard semantics. Fixed with a
    3-arg `cb.like(..., escapeChar)` and a local escaping helper.
  - `SecurityConfig.java`: the custom `AuthenticationEntryPoint` fully replaced Spring Security's default handler,
    silently dropping the RFC 6750 `WWW-Authenticate` challenge header on every `401`. Fixed by having both new
    handler beans log at WARN and then delegate the actual response to Spring's own `BearerTokenAuthenticationEntryPoint`/
    `BearerTokenAccessDeniedHandler` — restoring the header and, as a side effect, collapsing the near-duplicate
    logic the review also flagged between the two beans into a single `logRejection` helper.
