# Phase 3 EXT Implementation Plan — Analytics Data & Navigation Fixes

**Status:** COMPLETE

Blueprint for `PHASE_3_EXT.md`. **Round 1** (AC1–3: empty default chart, blank deep link, tab↔URL sync), **Round 2**
(AC4–9: configurable range↔granularity constraints, frontend-driven UX from that config, and a compact analytics
toolbar), and **Round 3** (AC10–11: fixing a real range-defaulting bug discovered after retuning the Round 2 config,
and three toolbar UI refinements) are already implemented and reviewed — see their sections below, carried forward
unchanged as ground truth, not redone. This revision adds the **Round 4** blueprint (AC12–15: syncing the date
pickers with the server-confirmed range, never-future bidirectional picker bounds, a clear-to-default affordance,
horizontal chart scroll, and two further toolbar relocations). Read alongside `CLAUDE.md`, `docs/DECISIONS.md` (D1,
D5–D11, D13, D15–D16 — all still apply), and `docs/development/PHASE_3_EXT.md`.

## Current State (verified)

- `Granularity` (`backend/.../analytics/Granularity.java`) is a rich enum with per-constant `isRangeValid(from, to)`
  (hardcoded bounds), `bucketStart(date)`, `next(bucketStart)`.
- `AnalyticsService.findTimeSeries` calls `granularity.isRangeValid(fromDate, toDate)` and throws a plain
  `ResponseStatusException(BAD_REQUEST, "Range [" + fromDate + ", " + toDate + "] is not valid for granularity " +
  granularity)` on failure — no structured data, just a string.
- No custom `app.*` namespace exists in `application.yml` yet; `CustomerActivityAnalyticsApplication` has no
  `@ConfigurationPropertiesScan`; no `spring-boot-starter-validation` dependency is present.
- `AnalyticsPanelComponent`/`.html` (Round 1, unchanged by this plan's predecessor) renders one long `flex-wrap` row:
  activity type, status, currency, min/max amount, up to 4 type-specific fields, From/To datepickers, a granularity
  `<mat-select>` with all 4 options always enabled, and a `mat-button-toggle-group` for the metric switch. Errors
  render as `<div class="analytics-error">{{ errorMessage() }}</div>`, sourced from `HttpErrorResponse.error.detail`.
- `TransactionTableComponent`/`.html` already has a proven, reusable **pattern** (not a shared component) for
  collapsing a filter behind an icon: `mat-icon-button` + `[matMenuTriggerFor]` + `<mat-menu>`, with a documented
  `(click)="$event.stopPropagation()"` workaround for `mat-menu`'s auto-close-on-inner-click behavior.
- `LocalDate.plus(long, TemporalUnit)` accepts `java.time.temporal.ChronoUnit` (`DAYS`/`WEEKS`/`MONTHS`/`YEARS`) and
  is a verified drop-in equivalent for today's hardcoded `.plusDays()`/`.plusWeeks()`/`.plusMonths()`/`.plusYears()`
  calls — this is what makes the bounds genuinely config-drivable with one generic check instead of four
  per-constant methods.

## Design clarifications (flagging for `/review PHASE_3_EXT plan`, resolving this plan's own open questions)

1. **Bootstrapping interaction order (resolves `PHASE_3_EXT.md`'s open question).** `granularity` keeps its existing
   default (`DAY`, matching the controller's `@RequestParam(defaultValue = "DAY")`). Rule set, applied reactively:
   - If neither `from` nor `to` is picked: no artificial restriction: all 4 granularity options stay enabled, no
     datepicker `min`/`max` — identical to today's default-request behavior (Round 1 AC1 stays satisfied).
   - Once `from` is picked (with or without `to`): the `To` picker's `[min]`/`[max]` are computed from `from` + the
     **currently selected granularity's** bound. `From` itself is never constrained.
   - Once both `from` and `to` are set: each granularity `<mat-option>` is `[disabled]` unless
     `isWithinConstraint(from, to, boundFor(thatGranularity))`.
   - Changing `granularity` to a value whose bound no longer fits the current `from`/`to` clears `to` (not
     `granularity` — a granularity is never auto-changed out from under the operator), forcing a fresh pick within
     the new bound.
2. **Config shape: `(amount, ChronoUnit)` pairs, independently for min and max** (resolves the "config shape" open
   question) — confirmed workable because today's mixed-unit bounds (e.g. DAY: min **1 day**, max **1 month**) need
   independent units per side, and `LocalDate.plus(amount, ChronoUnit)` is a verified equivalent of the current
   per-constant `.plusX()` calls, so behavior is provably unchanged for the shipped defaults.
3. **Error mechanism: Spring `ProblemDetail` extension properties, not just a nicer string** (resolves the "error
   mechanism" open question). `ResponseStatusException.getBody()` returns a mutable `ProblemDetail`;
   `spring.mvc.problemdetails.enabled: true` is already on, so `setProperty(...)` calls serialize as RFC 7807
   extension members with zero new dependencies. The frontend gets structured data (`granularity`, `minSpan`,
   `maxSpan`, `requestedFrom`, `requestedTo`) to render its own message from the *same* constraint data it already
   fetched for AC5, instead of parsing backend prose — the human-readable `detail` string is still populated (states
   granularity + bounds) as a plain-text fallback for any non-UI consumer.
4. **Toolbar primary/secondary split (resolves the "toolbar split" open question):** primary/always-visible = From,
   To, Granularity (with the info tooltip), Aggregation-type dropdown. Secondary/collapsed-into-one-menu = activity
   type, status, currency, min/max amount, and the type-specific fields — all of Round 1's existing secondary
   filters, unchanged in *content*, just relocated behind one filter icon.
5. **Reuse the *pattern*, not a shared component.** `TransactionTableComponent`'s icon-button + `mat-menu` technique
   is reused as-is (same directive combination, same `stopPropagation` workaround). It is **not** extracted into a
   shared `<app-filter-menu>` component — the table's version is one-menu-per-column driven by its own column
   config, while the Analytics panel needs exactly one menu holding several unrelated fields; two structurally
   different usages of the same small pattern don't yet justify a shared abstraction (`CLAUDE.md` Simplicity).
6. **The config endpoint's response type is the config-binding type itself** (`AnalyticsRangeProperties.Bound`), not
   a parallel DTO — it's already a plain immutable record of primitives/`ChronoUnit`, with no JPA/internal coupling
   to hide, so a second near-identical class would be needless per `CLAUDE.md` coding standard #3. Flagged
   explicitly as a deliberate simplicity choice, not an oversight, in case the reviewer disagrees.
7. **Missing-bound fail-fast, no new dependency.** The properties class validates at startup (a `@PostConstruct`
   check that every `Granularity` has a configured bound, throwing `IllegalStateException` if not) rather than
   adding `spring-boot-starter-validation` for one invariant — consistent with keeping the dependency footprint
   minimal.

## Backend Design

### `AnalyticsRangeProperties` (new, `analytics/AnalyticsRangeProperties.java`)

```java
@ConfigurationProperties(prefix = "app.analytics.range-constraints")
public record AnalyticsRangeProperties(Map<Granularity, Bound> bounds) {

  public record Bound(long minAmount, ChronoUnit minUnit, long maxAmount, ChronoUnit maxUnit) {
    public boolean isValid(LocalDate from, LocalDate to) {
      return !to.isBefore(from.plus(minAmount, minUnit)) && !to.isAfter(from.plus(maxAmount, maxUnit));
    }
  }

  @PostConstruct
  void validateAllGranularitiesConfigured() {
    for (Granularity granularity : Granularity.values()) {
      if (!bounds.containsKey(granularity)) {
        throw new IllegalStateException("Missing range constraint for granularity " + granularity);
      }
    }
  }

  public Bound boundsFor(Granularity granularity) {
    return bounds.get(granularity);
  }
}
```

`CustomerActivityAnalyticsApplication` gains `@ConfigurationPropertiesScan` (first properties class in the project;
scans for and registers all `@ConfigurationProperties` classes automatically going forward).

### `Granularity` (modified)

Remove the abstract `isRangeValid` method and its 4 per-constant implementations — range validation is no longer the
enum's responsibility (values are config-driven, and the check itself is now generic, living on `Bound`). Keep
`bucketStart`/`next` exactly as-is (bucketing behavior is explicitly out of scope for this round).

### `AnalyticsService` (modified)

Constructor gains an `AnalyticsRangeProperties` dependency. Replace the validation block:

```java
AnalyticsRangeProperties.Bound bound = rangeProperties.boundsFor(granularity);
if (!bound.isValid(fromDate, toDate)) {
  log.warn(/* unchanged fields */);
  throw invalidRangeException(granularity, bound, fromDate, toDate);
}
```

`invalidRangeException` builds a `ResponseStatusException` with a `detail` stating the granularity and human-readable
bounds (e.g. "Range [2015-12-31, 2026-09-02] is not valid for granularity YEAR — YEAR requires a range between 1
year(s) and 5 year(s)."), then sets extension properties on `.getBody()`: `granularity`, `minAmount`/`minUnit`,
`maxAmount`/`maxUnit`, `requestedFrom`, `requestedTo` (all values already at hand — no extra computation).

### `AnalyticsConfigController` (new, `analytics/AnalyticsConfigController.java`)

```java
@RestController
public class AnalyticsConfigController {
  @GetMapping("/api/v1/analytics/range-constraints")
  public Map<Granularity, AnalyticsRangeProperties.Bound> rangeConstraints() {
    return rangeProperties.bounds();
  }
}
```

Not customer-scoped (global configuration, not per-customer data) — a new top-level `/api/v1/analytics/...` path,
distinct from `/api/v1/customers/{id}/analytics`. No auth beyond the existing permit-all `SecurityConfig` (D13).

### `application.yml` (modified)

```yaml
app:
  analytics:
    range-constraints:
      bounds:
        DAY: { min-amount: 1, min-unit: DAYS, max-amount: 1, max-unit: MONTHS }
        WEEK: { min-amount: 1, min-unit: WEEKS, max-amount: 30, max-unit: WEEKS }
        MONTH: { min-amount: 1, min-unit: MONTHS, max-amount: 2, max-unit: YEARS }
        YEAR: { min-amount: 1, min-unit: YEARS, max-amount: 5, max-unit: YEARS }
```

Values are copied verbatim from today's hardcoded bounds — default behavior for an operator who never touches
configuration is unchanged (per `PHASE_3_EXT.md`'s Assumptions).

## Frontend Design

### `core/models/analytics.model.ts` (modified)

Add `ChronoUnit = 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS'`, `RangeConstraint { minAmount; minUnit; maxAmount; maxUnit }`,
`RangeConstraints = Record<Granularity, RangeConstraint>`.

### `core/services/analytics-config.service.ts` (new)

Thin `HttpClient` wrapper: `getRangeConstraints(): Observable<RangeConstraints>` → `GET
/api/v1/analytics/range-constraints`. A separate service from `AnalyticsService` (different backend resource, not
customer-scoped, fetched once rather than per-query).

### `core/utils/range-constraint.util.ts` (new)

Pure functions, no new date-library dependency (native `Date` arithmetic is sufficient for whole-unit day/week/month/
year math, consistent with how `AnalyticsPanelComponent` already works with native `Date`):
- `addUnit(date: Date, amount: number, unit: ChronoUnit): Date`
- `isWithinConstraint(from: Date, to: Date, constraint: RangeConstraint): boolean`
- `minSelectableTo(from: Date, constraint: RangeConstraint): Date`
- `maxSelectableTo(from: Date, constraint: RangeConstraint): Date`

### `AnalyticsPanelComponent` (modified)

New state: `rangeConstraints = signal<RangeConstraints | null>(null)` (fetched once via `AnalyticsConfigService` in
the constructor); `errorDetail = signal<{ granularity, minAmount, minUnit, maxAmount, maxUnit, requestedFrom,
requestedTo } | null>(null)` replacing the current plain `errorMessage` string signal (falls back to
`error.error?.detail` if extension properties are absent, e.g. for a non-range 4xx).

New computed: `availableGranularities` — per Clarification #1's rules, using `range-constraint.util.ts`'s
`isWithinConstraint`. New `toDatepickerBounds` computed — `{ min, max }` from `fromDate()` + `granularity()`'s bound,
via `minSelectableTo`/`maxSelectableTo`, bound to the `To` `matDatepicker` input's `[min]`/`[max]`.

`onGranularityChange` gains the "clear `to` if it no longer fits" step from Clarification #1.

### `AnalyticsPanelComponent` template (rewritten)

Compact toolbar per Clarification #4: From/To/Granularity/Aggregation-type directly visible; an info icon
(`matTooltip`, text built from `rangeConstraints()`, e.g. "DAY: 1 day–1 month · WEEK: 1–30 weeks · MONTH: 1 month–2
years · YEAR: 1–5 years") next to Granularity; the remaining fields (activity type, status, currency, min/max amount,
type-specific) move into one `mat-icon-button` + `mat-menu` (Clarification #5's reused pattern). The metric switch
becomes a `<mat-select>` (two `mat-option`s) replacing `mat-button-toggle-group`, same `onMetricChange` handler. The
error rendering becomes an inline `mat-error`-styled element near the Granularity control, built from `errorDetail()`.

## File inventory

*(Round 2 scope below. Round 3's and Round 4's file changes are listed inline within their own `## Round 3` / `##
Round 4` sections rather than duplicated here.)*

**Backend — new:** `analytics/AnalyticsRangeProperties.java`, `analytics/AnalyticsConfigController.java`; test:
`analytics/AnalyticsRangePropertiesTest.java` (Spring `ApplicationContextRunner` — binds defaults, verifies an
override changes a bound, verifies the fail-fast on a missing entry), `analytics/AnalyticsConfigControllerTest.java`
(MockMvc — asserts the endpoint returns the configured bounds).

**Backend — modified:** `analytics/Granularity.java` (remove `isRangeValid`), `analytics/AnalyticsService.java`
(inject `AnalyticsRangeProperties`, richer error), `CustomerActivityAnalyticsApplication.java`
(`@ConfigurationPropertiesScan`), `application.yml` (new `app.analytics.range-constraints` section).

**Backend — tests requiring updates (breaking constructor/API change, not just additions):**
`analytics/GranularityTest.java` (remove the `isRangeValid`-boundary test cases; keep the `bucketStart`/`next`
cases), `analytics/AnalyticsServiceTest.java` and `analytics/AnalyticsServiceIntegrationTest.java`
(`AnalyticsService`'s constructor gains `AnalyticsRangeProperties` — both test classes need a fixture instance with
the same default bounds as `application.yml`), plus a new test in one of them asserting the `400` response carries
the extension properties.

**Frontend — new:** `core/models` additions inline in `analytics.model.ts` (no new file);
`core/services/analytics-config.service.ts` (+ `.spec.ts`); `core/utils/range-constraint.util.ts` (+ `.spec.ts`).

**Frontend — modified:** `features/analytics/analytics-panel/analytics-panel.component.{ts,html,spec.ts}` (scss
adjusted only as needed for the new icon/menu/dropdown layout).

**Documentation reconciliation (assigned as an `/implement`-time task, per the `D12`/`D15` precedent):**
`docs/DECISIONS.md` gains `D16` — config-driven range↔granularity constraints via `@ConfigurationProperties`
(Clarifications #1–#2), exposed via a new endpoint, with `ProblemDetail` extension properties for structured `400`s
(Clarification #3) — beyond-PDF architectural choices, added in the same commit as the code.

## Round 3 — range-defaulting bug fix and toolbar polish

**Root cause (verified against the actual code, not assumed):** `AnalyticsService.findTimeSeries`'s old defaulting
block —

```java
Instant effectiveTo = to != null ? to : referenceInstant(customerId);
Instant effectiveFrom = from != null ? from : effectiveTo.atZone(UTC).minusMonths(1).toInstant();
```

— defaults an omitted `to` to `referenceInstant(customerId)` (the customer's latest transaction, Round 1's fix) with
no regard for a *provided* `from`. If `from` is set to a date after that customer's latest activity (entirely
plausible once bounds are widened, e.g. `DAY: 1d–3mo`), `effectiveTo` ends up *before* `effectiveFrom` — an inverted
span that always fails `Bound.isValid`'s min check, surfaced as a `400` that reads as if the *max* bound isn't being
honored (it never got the chance to be checked). Symmetrically, an omitted `from` defaulted to a hardcoded
`minusMonths(1)`, independent of the selected granularity's own minimum — already broken for anything coarser than
`MONTH` even before this bug was reported.

**Fix — `AnalyticsService.findTimeSeries`:** replace the two-line block with a four-branch, granularity-bound-aware
version (full code in `AnalyticsService.java`, implemented):
- **Both given:** unchanged, still validated by `Bound.isValid` below.
- **`from` only:** `to` defaults to `from + bound.maxAmount()/maxUnit()` — the largest window the granularity
  allows, starting from `from`. Always passes `Bound.isValid` by construction.
- **`to` only:** symmetric — `from` defaults to `to - bound.maxAmount()/maxUnit()`.
- **Neither given** (user-directed, not the originally-planned "max span" default): `to` stays anchored to
  `referenceInstant(customerId)` (Round 1's fix, unchanged), `from` becomes the 1st of the calendar month containing
  that date, clamped to at least `to - bound.minAmount()/minUnit()` so the default is never itself invalid.

New private helpers `plusSpan`/`minusSpan`/`startOfMonthDefault` do the `LocalDate`-based arithmetic (required —
`Instant.plus(amount, ChronoUnit.MONTHS)` throws `UnsupportedTemporalTypeException`; `LocalDate.plus` is what already
makes `Bound.isValid` correct today). `Bound.isValid` itself is unchanged — it remains the safety net for the "both
given" path.

**`AnalyticsRangeProperties`:** the existing `@PostConstruct validateAllGranularitiesConfigured` check now also
asserts, per configured `Bound`, that the max span (applied to a fixed reference date) doesn't resolve *before* the
min span — a cheap fail-fast guard against a future config typo (`max-amount` smaller than `min-amount`), consistent
with the existing pattern.

**Explicitly accepted residual gap:** switching `granularity` to something coarser before ever picking a date still
hits the "neither given" branch with that coarser granularity, and "month-to-date" can violate a much larger
configured minimum. Narrow edge case (frontend granularity always starts at `DAY`), not part of what was reported —
documented in `PHASE_3_EXT.md`'s Risks section rather than silently left unaddressed.

**Frontend — `analytics-panel.component.{ts,html,scss}` (three independent UI fixes, no defaulting-logic changes on
this side since the frontend already omits unset `from`/`to` from the request, which is exactly what the backend fix
now handles correctly):**
1. **Dropdown truncation:** `.metric-field`/`.granularity-field` widened in the SCSS so "Amount by Currency" no
   longer clips — pure CSS.
2. **Legend-style tooltip:** `constraintsTooltip()` now joins one padded, aligned line per granularity with `'\n'`
   instead of `' · '`; a global `.legend-tooltip { white-space: pre-line; }` rule (in `frontend/src/styles.scss`,
   not component-scoped SCSS — `matTooltip` panels render in the CDK overlay container outside any component's view,
   confirmed by grep: no `::ng-deep` precedent exists in this codebase, so a global rule is the correct, standard
   approach rather than introducing a new pattern) makes the `\n`s render as line breaks. Interaction stays
   hover-triggered, per user confirmation — satisfies the original AC7 wording verbatim.
3. **Visible active-filter indicator:** a new `hasActiveSecondaryFilters` computed signal (true if `activityType()`
   isn't `'ALL'`, or any value in `filters()` is set) drives `[class.filter-active]` on the funnel `mat-icon-button`,
   reusing `TransactionTableComponent`'s exact `.filter-trigger`/`.filter-active` CSS rule (color toggle,
   `var(--mat-sys-primary, #e65100)` when active) — same established pattern, aggregated across the whole secondary
   set instead of per-column, per user confirmation over introducing a new chip/badge pattern.

## Round 4 — picker sync/bounds, chart scroll, toolbar relocations

**Root causes (verified against the actual code before designing the fix):**
- `analytics-panel.component.ts`'s `load()` only ever called `this.series.set(series)` on success — it never wrote
  `series.from`/`series.to` back into the `fromDate`/`toDate` signals, so a server-computed default (Round 3) stayed
  invisible in the pickers even while the chart rendered real data for that range.
- Round 3's `from`-only branch computes `to = from + bound.maxAmount()/maxUnit()` with no upper bound — combined
  with the fix above finally letting a `from`-only pick reach the chart, a `From` picked close to today could push
  the computed `to` into the future, rendering empty future-dated buckets.
- Only `toDatepickerMin`/`toDatepickerMax` existed; `From` had no reciprocal bound, and neither picker was capped
  against today.

**Backend — `AnalyticsService.findTimeSeries`:** the `from != null` branch's `to` now clamps at `todayStart()` (new
private helper, `LocalDate.now(UTC).atStartOfDay(UTC).toInstant()`) via a new `minInstant(a, b)` helper:
```java
effectiveTo = to != null ? to : minInstant(plusSpan(from, bound.maxAmount(), bound.maxUnit()), todayStart());
```
The `to`-only and neither-given branches are unchanged (never produce a future date by construction). This can't
spuriously shorten a range below the granularity's minimum once the frontend fix below ships, because the new
`fromDatepickerMax` always leaves at least one min-span of room before today when `To` isn't set yet.

**Frontend — `analytics-panel.component.{ts,html,scss}`:**
1. **Picker sync (two parts — both required):** (a) `load()`'s success handler now also calls
   `this.fromDate.set(new Date(series.from))` / `this.toDate.set(new Date(series.to))` — direct signal writes, not
   routed through `onFromDateChange`/`onToDateChange`, so no reload loop. (b) On its own, (a) is not sufficient:
   `MatDatepickerInput`'s displayed text is driven by the `<input>`'s own `[value]` binding, not implicitly by an
   external signal write, so `analytics-panel.component.html`'s `From`/`To` inputs also gain
   `[value]="fromDate()"` / `[value]="toDate()"`. Without (b), the signals update correctly but the picker fields
   stay visibly blank — this was caught as a separate, real bug during manual browser verification (the fields
   showed only their new clear icon, no date text, despite the chart rendering the correct range) and is the actual
   mechanism that satisfies AC12, not (a) alone.
2. **Bidirectional bounds:** `range-constraint.util.ts` gained `subtractUnit`/`minSelectableFrom`/`maxSelectableFrom`
   (mirroring the existing forward `addUnit`/`minSelectableTo`/`maxSelectableTo`; `addUnit`'s day/month arithmetic
   already handles negative amounts correctly via native `Date` rollover, verified, no change needed there). New
   `fromDatepickerMin`/`fromDatepickerMax` computed signals mirror the existing `To` ones, and `toDatepickerMax` (now
   non-nullable) gains a `minDate(..., today)` cap; `fromDatepickerMax` gains the same cap plus a `today − minSpan`
   fallback when `To` isn't set yet, which is what makes the backend clamp above always land inside the valid range.
3. **Clear affordance:** a small `faCircleXmark` `matSuffix` button per date field, shown when that field has a
   value, calling `onFromDateChange(null)`/`onToDateChange(null)` — the next successful `load()` response
   repopulates the field via fix 1, reusing the backend's own defaulting logic rather than duplicating it
   client-side.
4. **Chart scroll (`analytics-chart.component.*`):** canvas wrapped in an `overflow-x: auto` scroll container with
   an inner div whose `min-width` scales with bucket count (`28px` × bucket count) — `responsive: true` (kept)
   resizes the canvas to that wide inner width since Chart.js's `ResizeObserver` tracks the canvas's *immediate*
   parent, confirmed via exploration.
5. **Info legend relocation:** the standalone info-icon button is removed; `[matTooltip]`/`matTooltipClass` move
   onto the Granularity field's `<mat-label>`.
6. **Filter relocation:** the filter `mat-icon-button` + `<mat-menu>` move out of `.analytics-toolbar` into a new
   `.chart-overlay-wrapper` (`position: relative`) that also contains `<app-analytics-chart>`, with
   `.chart-filter-trigger { position: absolute; top/right: 0.5rem; z-index: 1; }` plus a small background/shadow so
   the icon stays legible over chart content — same menu/active-color behavior as Round 3, purely repositioned. As a
   consequence, the filter control (like the chart) only renders in the non-error branch of the template — accepted
   as a natural, minor side effect of "float over the chart," not separately worked around.

**Test-fixture note:** `analytics-panel.component.spec.ts`'s existing `emptySeries` fixture used `from: '', to: ''`,
which is fine for the old code but produces an `Invalid Date` once responses are synced into the pickers (surfaced
as real `RangeError`s from `toISOString()` on subsequent reloads once discovered while running the full suite). Two
fixes: (a) the fixture's `from`/`to` are now valid ISO strings; (b) a new `flushAnalyticsRequest()` test helper
echoes back whatever `from`/`to` params were actually sent (mirroring real backend behavior for an explicitly-given
side) instead of always returning the fixed fixture values, so a test that picks a date doesn't have that pick
silently overwritten by an unrelated fixture value once the response round-trips into the signals.

## Test plan → Acceptance-criteria mapping

| `PHASE_3_EXT.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1–3 *(Round 1, implemented)* | Already covered — see the original implementation's test suite (`AnalyticsServiceTest`, `AnalyticsServiceIntegrationTest`, `TransactionRepositoryTest`, `transactions-page.component.spec.ts`'s `RouterTestingHarness` tests) — unaffected by this round except for the constructor-signature update noted above. | Same. |
| AC4 — configurable bounds, defaults unchanged | `AnalyticsRangePropertiesTest` (defaults bind correctly; an overridden property changes `Bound.isValid`'s outcome; missing entry fails fast); updated `AnalyticsServiceTest`/`AnalyticsServiceIntegrationTest` continue passing unmodified in *behavior* with the default-bound fixture. | — |
| AC5 — constraints exposed, frontend drives UX from them | `AnalyticsConfigControllerTest` (endpoint returns the configured bounds). | `analytics-config.service.spec.ts`; `range-constraint.util.spec.ts` (pure-function correctness incl. mixed-unit bounds like DAY's 1-day/1-month); `analytics-panel.component.spec.ts` (granularity options disable/enable per Clarification #1's rules for a given from/to; `To` datepicker `min`/`max` reflect the selected granularity; changing granularity clears an now-invalid `to`). |
| AC6 — seamless inline error | `AnalyticsServiceTest`/`AnalyticsControllerTest` (400 response body carries `granularity`/`minAmount`/`minUnit`/`maxAmount`/`maxUnit`/`requestedFrom`/`requestedTo` extension properties, plus a human-readable `detail`). | `analytics-panel.component.spec.ts` (a rejected response renders the inline `mat-error`-styled message with the human-readable bounds, not raw JSON/prose). |
| AC7 — info affordance | — (pure frontend rendering of already-fetched data) | `analytics-panel.component.spec.ts` (tooltip text is built from `rangeConstraints()`, covers all 4 granularities). |
| AC8 — compact toolbar | — | `analytics-panel.component.spec.ts` (primary controls render directly; secondary fields are reachable only via the filter menu, mirroring `transaction-table.component.spec.ts`'s existing menu-open/apply/clear test pattern). |
| AC9 — metric dropdown | — | `analytics-panel.component.spec.ts` (dropdown renders both options; selecting each updates `metric()`/the chart's `metricType` input, reusing the existing `onMetricChange` assertions). |
| AC10 — correct partial-range defaulting *(Round 3)* | `AnalyticsServiceTest`: `fromOnlyDefaultsToUsingGranularitysMaxSpan`, `toOnlyDefaultsFromUsingGranularitysMaxSpan` (the reported bug, now fixed), `defaultsFromToStartOfCurrentMonthWhenRangeOmitted`, `clampsStartOfMonthDefaultWhenTooCloseToMinimumSpan` (the safety-clamp edge case); `AnalyticsRangePropertiesTest`: `failsFastWhenABoundsMaxSpanIsSmallerThanItsMinSpan`. | — (no frontend defaulting-logic change; the frontend already omits unset sides, which the backend fix now handles correctly) |
| AC11 — toolbar UI polish *(Round 3)* | — | `analytics-panel.component.spec.ts`: tooltip-legend-format test, active-filter-indicator tests (activity type, secondary filter set/cleared); dropdown-width fix verified manually (not meaningfully assertable under jsdom/Karma layout). |
| AC12 — pickers reflect the effective range *(Round 4)* | — | `analytics-panel.component.spec.ts`: picking only `From` and receiving a from-only response populates `toDate()` from `series.to`. |
| AC13 — never-future, bidirectional bounds *(Round 4)* | `AnalyticsServiceTest`: `fromOnlyClampsDefaultToTodayWhenMaxSpanWouldBeInTheFuture`. | `analytics-panel.component.spec.ts`: `fromDatepickerMin`/`fromDatepickerMax` computed from a picked `To`; `toDatepickerMax` bracket-tested against real wall-clock time to never exceed today even when `From` is today; `fromDatepickerMax` leaves min-span room before today when neither side is picked. |
| AC14 — clear-to-default affordance *(Round 4)* | — | `analytics-panel.component.spec.ts`: clicking a field's `.clear-date-button` drops that field's request param; the field re-populates once the reload resolves (covered by AC12's test). |
| AC15 — chart scroll + toolbar relocations *(Round 4)* | — | `analytics-chart.component.spec.ts`: `minWidthPx` scales with bucket count. `analytics-panel.component.spec.ts`: `MatTooltip` is bound on the Granularity label and no `.info-trigger` element remains. Horizontal-scroll rendering and the filter icon's floated position verified manually (not meaningfully assertable under jsdom/Karma's layout engine). |

Backend testing also covers the Global DoD: `ArchitectureTest`'s existing rules apply unchanged to
`AnalyticsConfigController` (controller/repository/persistence-API isolation, no new rule needed); Spotless/ESLint/
Prettier formatting unaffected (no new tooling, no new dependencies on either side).

## Risks / Open Questions

*(This section is Round 2-scoped, kept as historical record. Round 3's residual gap — switching `granularity` to
something coarser before ever picking a date can still 400 against "month-to-date" — is tracked in
`PHASE_3_EXT.md`'s Risks section, not repeated here. Round 4 raised no new open questions; its one accepted design
trade-off — the floated filter icon only rendering in the chart's non-error branch — is noted inline in the Round 4
section above, not listed separately here.)*

All four open questions carried from `PHASE_3_EXT.md` are resolved by Clarifications #1–#4 above. Remaining, smaller
items for `/implement` to watch:

- **`LocalDate.plus(amount, ChronoUnit)` equivalence** is asserted, not yet test-proven — `AnalyticsRangePropertiesTest`
  and the updated `AnalyticsServiceTest` boundary cases (mirroring the removed `GranularityTest` cases) must
  positively confirm identical pass/fail behavior at the exact boundaries (e.g. DAY: exactly 1 month valid, 1 month +
  1 day invalid) to fully retire the risk, not just assert it in this plan.
- **Datepicker `[min]`/`[max]` vs. a `[matDatepickerFilter]` function** — `[min]`/`[max]` are sufficient here since
  the valid window is always one contiguous range (not a sparse allowed-dates set), simpler than a filter function;
  `/implement` should confirm Angular Material 22's `MatDatepickerInput` still exposes both as documented (no API
  drift expected, but not yet verified against the installed version).
- **`errorDetail`'s fallback path** (when a `400` has no extension properties, e.g. a future unrelated validation
  error reusing the same endpoint) must still render *something* reasonable via the existing `detail` string, not a
  blank/broken UI — `/implement` should keep today's plain-string fallback alongside the new structured rendering.
