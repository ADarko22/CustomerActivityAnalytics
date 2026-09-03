# Phase 3 Implementation Plan — Analytics Features

**Status:** COMPLETE

Blueprint for `PHASE_3.md`. Adds a read-only aggregation endpoint (transaction count + amount-sum-by-currency,
bucketed by day/week/month/year) over the Phase 2 data model, plus an Angular graph view. Read alongside `CLAUDE.md`
(conventions), `docs/specs/PROJECT_SPECIFICATION.md` (Feature 3 / data model), and `docs/DECISIONS.md` (D1, D5–D11,
D13 — all still apply; this plan proposes a new D15 for the charting library).

## Current State (verified)

- Backend: `customer` and `transaction` packages fully implemented (Phase 2 / 2 EXT). `TransactionController` already
  exposes a rich filter set (`activityType`, `status`, `from`, `to`, `minAmount`, `maxAmount`, `currency`, plus one
  optional param per activity-specific column) via `TransactionSpecifications` (common predicates) composed with
  `CardActivitySpecifications` / `PaymentActivitySpecifications` / `CryptoActivitySpecifications` (type predicates).
  `Transaction` uses `JOINED` inheritance; `CardActivity`/`PaymentActivity`/`CryptoActivity` expose the parent's
  `createdAt`/`amount`/`currency` on the same Java type. All four repositories extend `JpaSpecificationExecutor`,
  which already provides an **unpaged** `findAll(Specification)` list overload — no new repository methods are
  needed for aggregation.
- Frontend: `TransactionsPageComponent` renders only `TransactionTableComponent` for a selected customer; no tabs, no
  date-picker, no chart library, no `analytics` feature folder yet. `app.config.ts` provides `HttpClient` and async
  Material animations but no date adapter.
- No new tables are introduced by `PHASE_3.md` (Scope explicitly reuses `transactions`/activity tables), so — unlike
  Phase 2 — this plan adds **no new Flyway migration**, only a seed-data extension (see § Local environment).

## Design clarifications (flagging for `/review PHASE_3 plan`, not silent contradictions)

1. **Filter gap between `PHASE_3.md`'s API table and its own Requirements line / `PROJECT_SPECIFICATION.md` Feature
   3.** The API table only shows `from`/`to`/`granularity`, but `PHASE_3.md`'s own "Requirements" line reads "Feature
   3 (aggregated statistics over custom time ranges, **with common-property filters, and activity-specific filters
   when a single type is selected**)", and the spec's Feature 3.1–3.3 mandate exactly that. This mirrors
   `PHASE_2_PLAN.md` Clarification #3 (type-specific params omitted from the phase doc's API table but required by
   its AC). Resolution: the analytics endpoint accepts the **same full filter surface** as
   `GET /transactions` — `activityType`, `status`, `minAmount`, `maxAmount`, `currency`, and the per-type optional
   filters — reusing the existing `TransactionSpecifications`/`*ActivitySpecifications` predicates verbatim. Not a
   contradiction, just completing an acknowledged gap the same way Phase 2 did.
2. **No charting library exists in the tech stack.** `CLAUDE.md`'s frontend stack names Angular/FontAwesome/
   `angular-oauth2-oidc` but no chart package, and Angular Material ships no chart component. This plan selects
   **Chart.js + `ng2-charts`** (canvas-based, small footprint, first-class Angular standalone-component wrapper) over
   `@swimlane/ngx-charts` (heavier — pulls in D3 as a transitive dependency for a single bar/line chart). Recorded as
   a new durable decision (`docs/DECISIONS.md` D15) — assigned as an implementation-time doc edit, following the
   precedent set by `PHASE_2_PLAN.md` Clarification #5 / § File inventory (doc reconciliation happens in the same
   commit as the code, not during `/plan`).
3. **In-memory Java bucketing instead of DB-side `GROUP BY`/`date_trunc`.** `PHASE_3.md`'s own Scope explicitly
   states: *"Initial low-load, so no query optimization, such as indexes or materialized views are required."* and
   AC3 asks only for **documentation** of the future optimization, not its implementation (a reading
   `PHASE_2_PLAN.md`'s Risks section already anticipates: *"consistent with how PHASE_3.md handles its own
   analytics-scaling question"*). Given that, this plan fetches the already-filtered rows via the existing
   `findAll(Specification)` (unpaged) and buckets/aggregates them in plain Java rather than writing a
   Postgres-specific `date_trunc` Criteria/native query. This is simpler (no DB-specific `CriteriaBuilder.function`
   calls, no timestamp-type friction between Hibernate's `Instant` mapping and Postgres `date_trunc`'s `timestamp`
   return type), and — importantly for the Testing Scope's "boundary cases on the range ↔ granularity constraints" —
   the bucketing logic becomes a pure, DB-free unit test (`GranularityTest`) instead of a Testcontainers round-trip
   for every edge case. The DB-side alternative (indexes, `GROUP BY date_trunc(...)`, materialized views,
   TimescaleDB continuous aggregates) is exactly what AC3's required write-up documents as the future path — see §
   Backend Design → Scaling notes.
4. **UTC-based bucket boundaries.** `PHASE_3.md`'s own Risk line flags "time-bucket aggregation across time zones."
   `created_at` is stored as a timezone-naive Postgres `TIMESTAMP` and mapped to `Instant` (Phase 2 convention,
   unchanged). This plan buckets by truncating to a `LocalDate` in **UTC** (day boundary = UTC midnight; week
   boundary = ISO Monday; month/year = calendar month/year start). This is a deliberate, documented simplification —
   not solved per-operator-timezone — appropriate for a single-timezone demo dataset; flagged here rather than left
   implicit.
5. **UI integration point: a new "Analytics" tab, not a new route.** `PROJECT_SPECIFICATION.md` frames Features 2 and
   3 as two views of one "dashboard," not two destinations reached via customer search. This plan adds
   `MatTabsModule` to `TransactionsPageComponent` (tabs: "Transactions" / "Analytics") rather than a new
   `customers/:customerId/analytics` route, keeping the existing route/search flow (`app.routes.ts`,
   `customer-search`) untouched and avoiding a second point of navigation for the same customer context.
6. **Analytics filter state is independent of the Transactions tab's filter state.** The two tabs are not required to
   stay in sync (`PHASE_3.md` AC2 only asks for the analytics tab's own date-picker/aggregation-toggle/graph); wiring
   the table's per-column filter signals into the analytics query would add cross-component coupling with no
   acceptance criterion requiring it. The analytics panel owns its own `activityType`/common/type-specific filter
   state, built with the same `TransactionFilter`-shaped model for consistency, not shared state.
7. **One endpoint call returns both metrics; the "counts vs amount sums" dropdown (AC2) is a client-side view
   toggle.** Each bucket in the response carries both `transactionCount` and `amountByCurrency`, so switching the
   aggregation-type dropdown re-renders the already-fetched series instead of re-querying the backend. Multi-currency
   sums are never combined (`Map<currency, sum>` per bucket, per the Risk note "multi-currency sums must stay
   separated").
8. **Range↔granularity bounds are calendar-based (`java.time.Period`/`LocalDate.plusX`), not fixed-day
   approximations**, and inclusive at both ends: e.g. for `WEEK`, `to` must fall in `[from + 1 week, from + 30
   weeks]`. This avoids the ambiguity of approximating "1 month" as a fixed day count (28–31 days) and matches how an
   operator would reason about the constraint.
9. **Defaulting.** `PHASE_3.md`'s functional requirement states a default of "1 month by day." If `from`/`to` are
   both omitted, the effective range is `[now − 1 calendar month, now]` (UTC) and `granularity` defaults to `DAY` (a
   Spring `@RequestParam(defaultValue = "DAY")`). Supplying only one of `from`/`to` is treated as if the range is
   fully specified (both required together) — a `400` is *not* raised for a single missing bound; the missing bound
   is filled from the same default, keeping the endpoint forgiving for manual/demo use.

## Backend Design

### `Granularity` enum (`analytics/Granularity.java`)

A rich enum owning its own range-validation and bucketing behavior (strategy-per-constant), avoiding a separate
validator/bucketer class:

```java
public enum Granularity {
  DAY {
    public boolean isRangeValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plusDays(1)) && !to.isAfter(from.plusMonths(1));
    }
    public LocalDate bucketStart(LocalDate date) { return date; }
    public LocalDate next(LocalDate bucketStart) { return bucketStart.plusDays(1); }
  },
  WEEK { /* from.plusWeeks(1)..from.plusWeeks(30); bucketStart = Monday-of-week; next = +1 week */ },
  MONTH { /* from.plusMonths(1)..from.plusYears(2); bucketStart = first-of-month; next = +1 month */ },
  YEAR { /* from.plusYears(1)..from.plusYears(5); bucketStart = first-of-year; next = +1 year */ };

  public abstract boolean isRangeValid(LocalDate from, LocalDate to);
  public abstract LocalDate bucketStart(LocalDate date);
  public abstract LocalDate next(LocalDate bucketStart);
}
```

### `AnalyticsService` (`analytics/AnalyticsService.java`)

```java
findTimeSeries(customerId, activityType, status, from, to, minAmount, maxAmount, currency,
               typeFilters, granularity) -> AnalyticsTimeSeriesDto
```

1. `customerService.requireExists(customerId)` (same 404 pattern as `TransactionService`).
2. Apply the defaulting from Clarification #9, then convert `from`/`to` to UTC `LocalDate`s and call
   `granularity.isRangeValid(fromDate, toDate)`; `400` via `ResponseStatusException` (no custom exception type, per
   `CLAUDE.md` coding standard #3) if it fails, with a message stating the allowed span for that granularity.
3. Fetch rows: `activityType == null` → `transactionRepository.findAll(TransactionSpecifications.common(...))`;
   otherwise the matching `CardActivityRepository`/`PaymentActivityRepository`/`CryptoActivityRepository` with its
   `*Specifications.filter(...)` — the exact dispatch `switch` already used by `TransactionService.findOverview`,
   minus the `Pageable` (unpaged `List<T>` overload).
4. Group rows by `granularity.bucketStart(LocalDate.ofInstant(row.getCreatedAt(), UTC))`.
5. Walk the bucket sequence from `granularity.bucketStart(fromDate)` to `granularity.bucketStart(toDate)` via
   `granularity.next(...)`, emitting one `AnalyticsBucketDto` per step — **including empty buckets** (`count = 0`,
   `amountByCurrency = {}`) so the frontend series has no gaps (Clarification #7 / a continuous trend-line). Every
   possible range/granularity combination is bounded (≤ ~31 buckets for `DAY`, ≤ 30 for `WEEK`, ≤ 24 for `MONTH`, ≤ 5
   for `YEAR`), so this loop is always small.
6. Per bucket: `transactionCount = rows.size()`; `amountByCurrency` = rows grouped by `currency` (case-sensitive, as
   stored), amounts summed with `BigDecimal::add` (no floating-point rounding).

Reuses `TransactionTypeFilters` (promoted from `TransactionService.TypeFilters`, see § Backend — modified) so
`analytics` and `transaction` share one filter-parameter record instead of two near-identical 12-field types.

### `AnalyticsController` (`analytics/AnalyticsController.java`)

`GET /api/v1/customers/{customerId}/analytics` — same query parameter list as `TransactionController.findOverview`
(`activityType`, `status`, `from`, `to`, `minAmount`, `maxAmount`, `currency`, the twelve per-type filters) plus
`granularity` (`@RequestParam(defaultValue = "DAY")`). No `Pageable` — the response is the full bucket series, not a
page.

### DTOs (`analytics/dto/`)

```java
public record AnalyticsBucketDto(
    Instant bucketStart, long transactionCount, Map<String, BigDecimal> amountByCurrency) {}

public record AnalyticsTimeSeriesDto(
    ActivityType activityType, // null = ALL
    Granularity granularity,
    Instant from,
    Instant to,
    List<AnalyticsBucketDto> buckets) {}
```

Plain records (not a sealed family) — Phase 3 has one response shape, not a polymorphic one; `CLAUDE.md`'s "sealed
interfaces" instruction targets the transaction DTOs' polymorphism, not every payload.

### Scaling notes (satisfies AC3 — a **documentation** deliverable, no code change)

New file `docs/development/PHASE_3_SCALING_NOTES.md`, linked from `AnalyticsService`'s class Javadoc, covering:

- Current approach and its ceiling (bounded in-memory bucketing over an unpaged filtered fetch — fine while a
  customer's per-range row count stays in the hundreds/low-thousands, per `PHASE_3.md`'s stated low-load
  assumption).
- Trigger for revisiting: aggregating over multi-year ranges or high-cardinality customers where the unpaged fetch
  itself becomes the bottleneck.
- Candidate optimizations, in likely adoption order: (1) an index on `(customer_id, created_at)` per activity table
  (already partially covered by Phase 2's `transactions(customer_id)`/`transactions(created_at)` indexes — child
  tables would need their own); (2) push the `GROUP BY date_trunc(:granularity, created_at), currency` aggregation
  into Postgres itself (native/JPQL query) instead of fetching rows; (3) a materialized view refreshed on a schedule
  for the common granularities; (4) TimescaleDB hypertables + continuous aggregates if analytics becomes a
  first-class, frequently-hit workload rather than an occasional operator lookup.

### Logging (Global DoD, following the Phase 2 convention)

`AnalyticsService` logs one `INFO` line per request (customer ID, `activityType`, `granularity`, bucket count in the
response) and one `DEBUG` line with the actual filter values — same PII posture as `TransactionService` (no customer
names/free-text at `INFO`). The `400` range/granularity-violation path logs at `WARN` with the requested granularity
and computed span, mirroring `TransactionService.validateSort`'s `WARN` pattern.

## Local environment / seed data (no new migration — see § Current State)

- `backend/src/main/resources/db/seed/R__seed_demo_data.sql` — **extend, don't replace**, the existing Angelo/Maria/
  John rows. Today's data spans only a few days across Jan–Feb 2026, which cannot demonstrate `WEEK`/`MONTH`/`YEAR`
  granularities meaningfully. Add one more `CARD` batch for Angelo spread across the **preceding 14 months** (e.g.
  `generate_series` stepping by ~11 days, `created_at` computed relative to the seed's fixed anchor date rather than
  `now()`, keeping the script deterministic) so all four granularities and their full constraint ranges (up to 5
  years for `YEAR`, up to 2 years for `MONTH`) have at least *some* real buckets to render, without breaking Phase 2
  repository tests that assert on the existing fixed rows/counts (verify `TransactionRepositoryTest` et al. don't
  hardcode a total row count for Angelo before touching this file — they seed their own fixtures per
  `PHASE_2_PLAN.md`'s stated convention, so this should be additive-safe).
- No `V3__*.sql` migration — Phase 3 introduces no new tables/columns.

## Frontend Design

- **New dependency:** `chart.js`, `ng2-charts` (Clarification #2 / D15).
- `app.config.ts` — add `provideNativeDateAdapter()` (Angular Material's datepicker needs a date adapter; nothing
  currently provides one since Phase 2 never used `MatDatepicker`).
- Models (`core/models/analytics.model.ts`): `Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'`;
  `AnalyticsBucket { bucketStart: string; transactionCount: number; amountByCurrency: Record<string, number> }`;
  `AnalyticsTimeSeries { activityType: ActivityType | null; granularity: Granularity; from: string; to: string;
  buckets: AnalyticsBucket[] }`.
- Service (`core/services/analytics.service.ts`): `findTimeSeries(customerId, filter: TransactionFilter, from, to,
  granularity)` — thin `HttpClient` wrapper building `HttpParams`, mirroring `transaction.service.ts`'s pattern
  exactly (reuses the existing `TransactionFilter` model for the shared filter fields, Clarification #6).
- Components (`features/analytics/`):
  - `analytics-panel/` — owns the panel's signal state: `activityType` (`MatSelect`, default `ALL`, extends with
    type-specific filter inputs when a type is picked — reusing `TYPE_COLUMNS`' field metadata from
    `transaction-table.columns.ts` where practical to avoid a second column-config source of truth), a `from`/`to`
    `MatDatepicker` range pair, a `granularity` `MatSelect`, and an aggregation-type `MatSelect` ("Transaction Count"
    / "Amount by Currency"). Debounced (300 ms) like the transaction table's filters. Surfaces the backend's `400`
    (invalid range/granularity) as an inline `MatError` next to the granularity control rather than a generic toast.
  - `analytics-chart/` — a `ng2-charts` `<canvas baseChart>` wrapper component, `@Input() series: AnalyticsTimeSeries`
    and `@Input() metric: 'count' | 'amount'`. `metric === 'count'` renders a single-series **bar chart** (histogram,
    per Functional Requirements wording) of `transactionCount` per bucket; `metric === 'amount'` renders a
    multi-series **line chart** (trend-line), one line per currency observed across the series' buckets (missing
    currency in a given bucket = `0`, not omitted, so lines stay continuous) — satisfies "shown as histograms or
    trend-line graphs" and the "multi-currency sums must stay separated" risk.
- `transactions-page/` — add `MatTabsModule`; tab 1 = existing `TransactionTableComponent` (unchanged), tab 2 =
  new `AnalyticsPanelComponent` (Clarification #5).
- `angular.json` budgets (currently `950kB`/`1.2MB`, already raised once in Phase 2) — verify they still hold after
  adding Chart.js; bump only if `ng build` actually warns/fails (same posture as `PHASE_2_PLAN.md`'s Risks entry).

## File inventory

**Backend — new:** `analytics/Granularity.java`, `analytics/AnalyticsService.java`, `analytics/AnalyticsController.java`,
`analytics/dto/{AnalyticsBucketDto,AnalyticsTimeSeriesDto}.java`, `transaction/TransactionTypeFilters.java` (promoted
from `TransactionService.TypeFilters`); test: `analytics/GranularityTest.java` (pure unit — bucket-sequence and
range-validity boundary cases per granularity, incl. UTC edge cases), `analytics/AnalyticsServiceTest.java` (Mockito,
mirrors `TransactionServiceTest` — dispatch-by-activityType, 404 on unknown customer, 400 on invalid range),
`analytics/AnalyticsServiceIntegrationTest.java` (Testcontainers/`@DataJpaTest`, mirrors `TransactionRepositoryTest` —
seeds fixtures spanning multiple buckets, asserts real count/amount-by-currency aggregation and empty-bucket
gap-filling end to end), `analytics/AnalyticsControllerTest.java` (MockMvc, mirrors `TransactionControllerTest` —
query-param wiring, `jsonPath` on the bucket series shape, 400 response body on constraint violation).

**Backend — modified:** `transaction/TransactionService.java` (remove the nested `TypeFilters` record, use the
promoted `TransactionTypeFilters`), `transaction/TransactionController.java` (same rename), any test referencing
`TransactionService.TypeFilters`; `db/seed/R__seed_demo_data.sql` (§ Local environment).

**Frontend — new:** `core/models/analytics.model.ts`; `core/services/analytics.service.ts` (+ `.spec.ts`);
`features/analytics/{analytics-panel,analytics-chart}/*` (each with `.ts/.html/.scss/.spec.ts`).

**Frontend — modified:** `package.json` (`chart.js`, `ng2-charts`), `app.config.ts` (`provideNativeDateAdapter`),
`features/transactions/transactions-page/*` (tabs), `angular.json` (budgets, only if needed).

**Documentation — new:** `docs/development/PHASE_3_SCALING_NOTES.md` (AC3 deliverable).

**Documentation reconciliation (assigned as an `/implement`-time task, mirroring `PHASE_2_PLAN.md`'s precedent):**
`docs/DECISIONS.md` gains `D15` (Chart.js + `ng2-charts` over `ngx-charts`, Clarification #2), added in the same
commit as the dependency.

## Test plan → Acceptance-criteria mapping

| `PHASE_3.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — endpoint returns both metrics bucketed correctly, enforces range↔granularity constraints | `GranularityTest` (boundary cases per granularity, both sides of each bound), `AnalyticsServiceTest` (400 on violation, dispatch by `activityType`, 404 on unknown customer), `AnalyticsServiceIntegrationTest` (real bucketing/currency-grouping/gap-filling against seeded Postgres rows), `AnalyticsControllerTest` (query-param wiring, response shape, 400 body) | `analytics.service.spec.ts` (correct `HttpParams` incl. filters/granularity, response parsed into `AnalyticsTimeSeries`) |
| AC2 — graph, date picker, aggregation-type dropdown | — | `analytics-panel.component.spec.ts` (date range → correct `from`/`to` params, granularity/aggregation-type selects, inline error on backend 400), `analytics-chart.component.spec.ts` (bar dataset for `count`, one line-series per currency for `amount`, zero-filled gaps) |
| AC3 — documentation of future optimization | — (doc-only, no test) | — |

Backend testing also covers the Global DoD: `ArchitectureTest`'s existing rules apply unchanged to the new
`analytics` package (controller/repository/persistence-API isolation) with no new rule needed; Testcontainers Postgres
(D10) backs `AnalyticsServiceIntegrationTest`; Spotless formatting unaffected (no new tooling).

## Risks / Open Questions (carried from `PHASE_3.md`, resolved or narrowed where possible)

- **Time-bucket aggregation across time zones** — resolved by Clarification #4 (fixed UTC bucketing); not
  per-operator-timezone-aware, documented as a deliberate demo-scope simplification rather than solved.
- **Partial buckets at range edges** — resolved by walking the bucket sequence from `granularity.bucketStart(from)`
  to `granularity.bucketStart(to)`, so the first/last bucket may represent a partial calendar unit (e.g. `from` mid-
  week for `WEEK`) but is still fully counted, not dropped or double-counted.
- **Multi-currency sums must stay separated** — resolved by `Map<String, BigDecimal>` per bucket end-to-end (backend
  DTO → frontend model → one chart series per currency); never summed across currencies.
- **New:** promoting `TransactionService.TypeFilters` to a standalone `TransactionTypeFilters` record touches
  Phase 2 code under this phase's plan. Scoped tightly (rename + move, no behavior change) and covered by the
  existing `TransactionServiceTest`/`TransactionControllerTest` continuing to pass unmodified in behavior.
- **New:** `chart.js`/`ng2-charts` is a first-time frontend dependency addition since Phase 2's FontAwesome/Material
  additions — verify `ng2-charts`' current major targets Angular 22's standalone/signals APIs before implementation;
  fall back to a hand-rolled minimal Chart.js wrapper (no `ng2-charts`) if the wrapper package lags the Angular
  version, without changing the D15 choice of Chart.js itself as the rendering engine.
