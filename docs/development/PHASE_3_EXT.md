# Phase 3 EXT — Analytics Data & Navigation Fixes

**Status:** COMPLETE
**Depends on:** Phase 3 — fixes/refines its Analytics feature; no new backend endpoints beyond what's listed below,
no data-model changes.

## Objective

Round 1 (implemented): fix three defects discovered after Phase 3 shipped — the Analytics tab's chart showing no
data points by default, deep-linking/hard-refreshing on a customer's page rendering a blank screen, and switching
between the Transactions/Analytics tabs not updating the browser URL.

Round 2 (implemented): make the Analytics view's range↔granularity constraints configurable and frontend-aware
instead of a hardcoded backend-only check the operator only discovers via a raw error string, and compact the
Analytics filter toolbar into a menu-bar-style strip above the chart.

Round 3 (implemented): fix a real defaulting bug discovered after retuning the Round 2 config to wider bounds — a
`from`-only (or `to`-only) request could silently combine with an unrelated default for the other side and produce
an always-invalid, inverted range, surfaced as a confusing 400 that reads as if the configured max bound isn't being
honored. Also set a clearer, more useful no-filter default ("month-to-date relative to the customer's latest
activity" instead of a hardcoded "1 month back"), and polish three rough edges in the Round 2 toolbar UI (truncated
dropdown text, a hard-to-scan tooltip, and no visible indicator of active secondary filters).

Round 4 (this update): Round 3's server-computed defaults were never read back into the date pickers, so they stayed
visibly blank even while real data rendered; combined with Round 3 letting a `from`-only request succeed by
extending `to` out to `from + maxSpan`, picking a `From` close to today could push the computed `to` *into the
future*, rendering empty chart bars for dates that haven't happened. This round: syncs the pickers with the
server-confirmed range, bounds both pickers bidirectionally (not just `To` from `From`) and caps both at today, adds
a per-field "clear to auto default" affordance, adds horizontal chart scrolling for wide ranges that don't fit, and
relocates two toolbar elements (info legend onto the Granularity label's hover; the filter control floated over the
chart instead of sitting in the toolbar row).

## Scope

- **In (Round 1 — implemented, see `PHASE_3_EXT_PLAN.md`):**
  - The Analytics tab shows real data by default for the seeded demo customer, without the operator having to
    manually adjust the date range first.
  - Deep-link / hard-refresh support: a fresh browser navigation (typed URL, bookmark, refresh — not client-side
    routing from `/`) to `customers/:customerId/transactions` and `customers/:customerId/analytics` renders the app
    correctly.
  - Two URL-addressable routes, `customers/:customerId/transactions` and `customers/:customerId/analytics`, kept in
    sync with the selected tab in both directions (tab click updates the URL; loading either URL, or browser
    back/forward, selects the matching tab).
- **In (Round 2 — implemented, see `PHASE_3_EXT_PLAN.md`):**
  - The DAY/WEEK/MONTH/YEAR range↔granularity bounds (today hardcoded in `Granularity`'s per-constant
    `isRangeValid`: DAY 1 day–1 month, WEEK 1–30 weeks, MONTH 1 month–2 years, YEAR 1–5 years) become configurable
    via Spring configuration (`application.yml`/environment), not hardcoded in source.
  - Those configured bounds are exposed to the frontend via a new read-only endpoint, so the Analytics panel can
    drive its own UX from the real, active configuration instead of duplicating hardcoded numbers.
  - The granularity `<mat-select>` disables options that don't fit the currently selected `from`/`to`.
  - The `From`/`To` date pickers only allow selecting a window that is valid for the selected granularity (or, more
    generally, for at least one granularity) — not a pick-then-fail cycle.
  - If a `400` still occurs (e.g. a combination not caught by the frontend guard), it renders as an inline,
    Material-styled message tied to the relevant controls, stating the allowed window in human-readable terms — not
    the current raw string (`"Range [2015-12-31, 2026-09-02] is not valid for granularity YEAR"`) dumped into a
    plain `<div>`.
  - An info/hover affordance near the range/granularity controls explains the currently configured allowed windows
    per granularity, sourced from the same exposed configuration (not separately hardcoded copy).
  - The Analytics filter toolbar is compacted into a menu-bar-style strip above the chart: the primary controls
    (date range, granularity, aggregation-type) stay directly visible; the secondary filters (activity type, status,
    currency, min/max amount, and the type-specific fields) collapse behind a single filter control, reusing
    `TransactionTableComponent`'s existing icon-button + `mat-menu` popover pattern
    (`transaction-table.component.html`) rather than inventing a new one.
  - The aggregation-type switch (Transaction Count vs. Amount by Currency) becomes a `<mat-select>` dropdown,
    replacing the current `mat-button-toggle-group`.
- **In (Round 3 — implemented, see `PHASE_3_EXT_PLAN.md`):**
  - A request with only `from` (or only `to`) set never spuriously rejects when the requested span is compatible
    with the selected granularity's configured bounds — the missing side is derived using that granularity's own
    max span, not an anchor unrelated to the provided side.
  - The no-filter default (`from` and `to` both omitted) is "month-to-date relative to the customer's latest
    activity": `to` stays anchored to the customer's most recent transaction (Round 1's fix, unchanged), and `from`
    becomes the 1st of the calendar month containing that date, safely clamped so the default is never itself
    invalid for the selected granularity's minimum span.
  - The "Show" aggregation-type dropdown displays its full option text ("Amount by Currency") without truncation.
  - The info affordance's hover content is reformatted as one aligned line per granularity instead of one long
    dot-separated string.
  - The filter icon visibly indicates whether any secondary filter (activity type, status, currency, amount range,
    type-specific fields) is currently active, reusing `TransactionTableComponent`'s existing per-column
    active-filter icon-color pattern.
- **In (Round 4 — new):**
  - The `From`/`To` date pickers reflect the actual effective range being shown — including a server-computed
    default for whichever side wasn't explicitly picked — instead of staying blank while the chart renders real
    data for that computed range.
  - Neither picker allows selecting a date later than today (transactions cannot happen in the future).
  - Bidirectional bounding: picking `To` first constrains `From`'s selectable range using the selected
    granularity's configured bounds, symmetrically with how picking `From` already constrains `To`.
  - A server-computed `from`-only default is itself capped at today, so it can never produce a range that extends
    into the future (closing the loop with the picker constraint above for values the app computes on its own).
  - Each date field has a "clear" affordance that resets it back to the server-computed default for the current
    granularity and the other, still-set side of the range.
  - The chart scrolls horizontally when the selected range/granularity combination produces more buckets than fit
    the visible width, instead of cramming them all into a fixed-width canvas.
  - The info/legend hover moves onto the Granularity field's label (no separate icon button).
  - The filter control moves out of the toolbar row and floats as an overlay button in the chart's top-right
    corner, freeing toolbar space.
- **Out:** the aggregation/bucketing algorithm itself (count + amount-sum-by-currency per bucket) and the
  `AnalyticsTimeSeriesDto`/`AnalyticsBucketDto` response payload shape are unchanged — only the constraint *values*
  (now config-driven), a new constraints-exposing endpoint, richer `400` content, the range-defaulting logic, and
  now the date-picker/chart/toolbar UI are added/changed; no auth (Phase 5); no charting-library change
  (`docs/DECISIONS.md` D15 stands); the `AnalyticsRangeProperties`/`Bound.isValid` validation algorithm itself is
  unchanged since Round 3 — only what gets fed into it, and now also how the frontend constrains what a user can
  feed into it.
- **Assumptions:** Round 1's, Round 2's, and Round 3's fixes are correct as implemented and are not being redone
  except where explicitly listed above. The configured range↔granularity bound *values* are operator-tunable via
  `application.yml` (confirmed by both the Round 3 and Round 4 bug reports, which surfaced only after retuning
  them) — this round's picker/default fixes must hold for *any* valid configuration, not just the originally-shipped
  defaults.

## Requirements (refs into prior phases / bug reports)

- Round 1 (implemented): user-reported regressions against `PHASE_3.md` AC2 (empty default chart), baseline SPA
  correctness (blank deep link), and a user-requested UX consistency improvement (URL should reflect the active tab).
- Round 2 (new): user-reported UX gap — the range↔granularity constraint is enforced only server-side and surfaced
  as a raw, unhelpful error string; the operator has no way to know the allowed window ahead of time or be guided by
  the UI (disabled options, constrained date pickers) instead of trial-and-error. User-requested: those constraints
  must be configurable (not hardcoded) and exposed to the frontend so it can drive proactive UX from the same source
  of truth, plus an explanatory info affordance. User-requested UI compaction: the Analytics filter controls should
  read as a compact menu bar above the chart, and the aggregation-type switch should be a dropdown rather than a
  toggle-button group.
- Round 3 (implemented): user-reported bug — after retuning `application.yml`'s bounds, picking only a `From` date
  produced a `400` claiming the max bound wasn't satisfied even though the requested span was well within it; root
  cause was an unrelated default anchor for the omitted `To`. User-requested fix: derive an omitted side from the
  granularity's own configured max span, and default the no-filter view to "start of the month through the
  customer's current/latest activity" instead of a hardcoded one-month window. User-requested UI polish: fix
  truncated dropdown text, reformat the info tooltip as a per-granularity legend, and make active secondary filters
  visible on the filter icon.
- Round 4 (new): user-reported follow-on issues — no horizontal scroll when the selected window/granularity
  produces more buckets than fit the chart; the date pickers never show the effective default when only one side is
  picked; the diagram could display future dates "that makes no sense." User-requested fixes: sync the pickers with
  the effective range; hard-cap both pickers (and the server's own from-only default) at today; bound each picker
  by the *other* side's pick using the configured max-window logic, symmetrically in both directions; a per-field
  "clear to auto default" affordance; horizontal chart scrolling. User-requested UI relocations: the info legend
  onto the Granularity dropdown's label hover; the filter control floated over the chart instead of in the toolbar.

## Functional Requirements

| Functionality | Description |
|---|---|
| Analytics default view has data *(Round 1)* | Opening the Analytics tab for the seeded demo customer, with no manual filter/date changes, renders a populated chart — real bars/lines, not an empty grid. |
| Deep-link / hard-refresh support *(Round 1)* | Typing, bookmarking, or refreshing on either Analytics-related route loads and renders the app correctly — no blank page, no failed script/style requests. |
| Tab ↔ URL sync *(Round 1)* | Selecting a tab navigates to its own URL; loading either URL directly, including via browser back/forward, selects the matching tab without a full page reload. |
| Configurable range↔granularity constraints *(Round 2)* | The DAY/WEEK/MONTH/YEAR min/max span bounds are read from Spring configuration, not hardcoded in `Granularity`; defaults match today's values; overriding a bound in configuration changes backend enforcement without a code change. |
| Constraints exposed to the frontend *(Round 2)* | A new read-only endpoint returns the active configured bounds; the Analytics panel fetches and uses this — not hardcoded frontend numbers — as the single source of truth for its own UX. |
| Granularity options reflect the selected range *(Round 2)* | The granularity `<mat-select>` visibly disables (not silently rejects) any option whose configured window doesn't fit the currently selected `from`/`to`. |
| Date pickers constrained to the allowed window *(Round 2)* | The `From`/`To` pickers prevent selecting a combination that would violate the applicable granularity's configured bounds, rather than allowing an invalid pick that only fails on submit. |
| Seamless inline error messaging *(Round 2)* | Any `400` that still occurs renders as an inline, Material-styled message near the relevant controls, stating the allowed window in human-readable terms, not a raw backend string in an unstyled `<div>`. |
| Constraint info affordance *(Round 2)* | An info icon/hover tooltip near the range/granularity controls explains the currently configured allowed windows per granularity, sourced from the same exposed configuration. |
| Compact analytics toolbar *(Round 2)* | Date range, granularity, and aggregation-type stay directly visible in a compact strip above the chart; activity type, status, currency, amount range, and type-specific filters collapse behind one filter control, reusing `TransactionTableComponent`'s existing menu-popover pattern. |
| Aggregation-type dropdown *(Round 2)* | The Transaction Count / Amount by Currency switch is a `<mat-select>` dropdown, not a `mat-button-toggle-group`. |
| Correct partial-range defaulting *(Round 3)* | A `from`-only or `to`-only request derives the missing side from the selected granularity's own configured max span, never from an anchor unrelated to the side that was provided. |
| Sensible no-filter default *(Round 3)* | With neither `from` nor `to` given, the view defaults to the 1st of the month containing the customer's latest activity through that latest activity itself, safely clamped against the granularity's minimum span. |
| Dropdown text fits *(Round 3)* | The aggregation-type "Show" dropdown renders "Amount by Currency" without truncation. |
| Legend-style constraint tooltip *(Round 3)* | The info affordance's hover content shows one aligned line per granularity instead of one long dot-separated string. |
| Visible active-filter indicator *(Round 3)* | The filter icon changes color when any secondary filter is currently active, reusing `TransactionTableComponent`'s existing pattern. |
| Pickers reflect the effective range *(Round 4)* | The `From`/`To` inputs display the actual range being queried, including any side the server computed on the caller's behalf, not just what was manually picked. |
| Never-future, bidirectional picker bounds *(Round 4)* | Neither picker allows a date later than today; picking either side constrains the other's selectable range using the selected granularity's configured bounds, in both directions. |
| From-only default capped at today *(Round 4)* | A server-computed `from`-only default (`to = from + maxSpan`) never lands in the future — it's capped at today's date. |
| Clear-to-default affordance *(Round 4)* | Each date field has a control that resets it to the server-computed default for the current granularity and the other side's current value. |
| Horizontal chart scroll *(Round 4)* | When the selected range/granularity produces more buckets than fit the chart's width, the chart area scrolls horizontally instead of cramming every bucket into a fixed width. |
| Info legend on the Granularity label *(Round 4)* | The per-granularity allowed-window legend appears on hover over the Granularity field's label, not a separate icon button. |
| Filter control floats over the chart *(Round 4)* | The secondary-filters icon+menu renders as an overlay in the chart's top-right corner rather than occupying a toolbar slot. |

## Acceptance Criteria

1. *(Round 1, implemented)* With the local seed data loaded and no manual date/filter changes, opening a seeded
   customer's Analytics tab shows at least one non-zero data point on the chart, verifiable for both the
   "Transaction Count" and "Amount by Currency" views.
2. *(Round 1, implemented)* A fresh hard navigation (a new browser tab or a hard refresh, not client-side navigation
   from `/`) to `http://localhost:4200/customers/{customerId}/transactions` renders the header, search bar, and
   transaction table — not a blank page. The same holds for `http://localhost:4200/customers/{customerId}/analytics`.
3. *(Round 1, implemented)* Clicking the "Analytics" tab updates the address bar to `.../analytics`; clicking
   "Transactions" updates it to `.../transactions`. Loading either URL directly, or using the browser's
   back/forward buttons, selects the corresponding tab without a full page reload.
4. *(Round 2, implemented)* The DAY/WEEK/MONTH/YEAR range↔granularity bounds are read from Spring configuration (e.g. `application.yml`), not
   hardcoded in `Granularity`; default values are unchanged from today (day 1d–1mo, week 1–30w, month 1mo–2y, year
   1–5y); changing a bound in configuration changes the backend's accepted range for that granularity with no code
   change.
5. *(Round 2, implemented)* A new backend endpoint exposes the active range↔granularity constraints. The Analytics
   panel fetches it on load and uses it — not hardcoded frontend values — to (a) enable only the granularities whose
   configured window fits the currently selected `from`/`to`, and (b) restrict date-picker selection to a window
   consistent with the selected granularity.
6. *(Round 2, implemented)* If the backend still rejects a request, the resulting message renders inline near the
   relevant controls in the existing Material style (not a raw unstyled string) and states the allowed window in
   human-readable terms (e.g. the granularity name plus its min/max span) rather than only echoing raw ISO dates and
   the enum name.
7. *(Round 2, implemented)* An info affordance (icon with hover tooltip) near the granularity/range controls
   explains the currently configured allowed windows per granularity, sourced from the same exposed configuration
   used for AC5.
8. *(Round 2, implemented)* The Analytics filter toolbar is visually compact: date range, granularity, and the
   aggregation-type control remain directly visible above the chart; activity type, status, currency, amount range,
   and type-specific filters are reachable via a single filter control (icon + menu/popover) instead of each
   occupying its own always-visible field.
9. *(Round 2, implemented)* The aggregation-type control (Transaction Count vs. Amount by Currency) is a
   `<mat-select>` dropdown, not a toggle-button group.
10. *(Round 3, implemented)* A `from`-only or `to`-only analytics request whose provided side, combined with the
    selected granularity's configured max span, describes a valid window never returns a `400` — the omitted side is
    derived from that max span, not from an anchor unrelated to the provided side. Separately, a request with
    neither `from` nor `to` defaults to the 1st of the month containing the customer's latest activity through that
    latest activity itself, and this default is never itself rejected regardless of the selected granularity's
    configured minimum span.
11. *(Round 3, implemented)* The "Show" aggregation-type dropdown displays "Amount by Currency" without truncating
    it; hovering the info icon shows one aligned line per granularity (not one long dot-separated string); the
    filter icon visibly changes color/state when at least one secondary filter (activity type, status, currency,
    amount range, type-specific field) is active, and reverts when none are.
12. *(Round 4, implemented)* After any successful load, the `From`/`To` picker inputs show the actual `from`/`to`
    the chart is rendering — including a side the caller left unpicked and the server computed — not blank fields.
13. *(Round 4, implemented)* Neither picker's calendar allows selecting a date after today. Once one side is picked,
    the other side's selectable calendar range is bounded to `[anchor − maxSpan, anchor − minSpan]` or `[anchor +
    minSpan, anchor + maxSpan]` (whichever direction applies) for the selected granularity, capped at today. A
    `from`-only request whose naive `from + maxSpan` would exceed today instead resolves to today, not a future
    date.
14. *(Round 4, implemented)* Each date field exposes a control that, when activated, clears that field and — once
    the resulting reload completes — repopulates it with the server-computed default for the current granularity
    and the other field's value (AC12's sync mechanism, triggered on demand).
15. *(Round 4, implemented)* When the active range/granularity combination has enough buckets to overflow the
    chart's normal width, a horizontal scrollbar appears under the chart and reveals the remaining buckets on
    scroll, without distorting the vertical axis. Hovering the Granularity field's label shows the same
    per-granularity legend AC11 describes; no separate info icon remains. The secondary-filters icon (same
    menu/active-color behavior as AC11) renders as a floating overlay in the chart's top-right corner instead of in
    the toolbar row.

## Testing Scope

**Round 1 (implemented):** frontend component/router test asserting tab-click ↔ URL sync in both directions; a
manual/e2e check that a hard refresh on both deep-link routes renders the app; a repository/integration-test
assertion that the seeded customer's default-range analytics response is non-empty.

**Round 2 (implemented):**
- Backend: a test that the configuration properties bind correctly with the documented defaults; a test that
  overriding a bound in configuration changes the validation outcome for that granularity; a test for the new
  constraints-exposing endpoint (returns the active configured values); a test that the `400` error response's
  content includes the human-readable allowed-window information (not just the old raw string).
- Frontend: a service test for fetching/parsing the constraints endpoint; a component test asserting the granularity
  select disables/enables options correctly for a given `from`/`to`; a test that the date pickers' selectable range
  reflects the active granularity's bounds; a test that the aggregation-type dropdown renders both options and
  switches the chart's metric; a test for the compact filter menu opening/applying/clearing, mirroring
  `TransactionTableComponent`'s existing `transaction-table.component.spec.ts` pattern; a test that an inline error
  renders correctly from a rejected response.

**Round 3 (implemented):**
- Backend: tests reproducing a `from`-only and a `to`-only request whose provided side plus the granularity's max
  span describes a valid window, asserting the call now succeeds and the derived side matches that max span; a test
  for the new "month-to-date relative to latest activity" no-filter default; a test for the safety clamp when the
  latest activity falls on the 1st of its own month; a test that a misconfigured bound (max span smaller than min
  span) fails fast at startup.
- Frontend: a test that the constraints tooltip renders one line per granularity; tests that the filter icon's
  active-indicator class tracks `hasActiveSecondaryFilters()` as activity type/secondary filters are set and
  cleared; the dropdown-width fix is verified manually (not meaningfully assertable under jsdom/Karma's layout).

**Round 4 (new):**
- Backend: a test that a `from`-only request whose naive `from + maxSpan` would exceed "now" instead resolves `to`
  to today's start, not the naive value.
- Frontend: a test that picking only `From` and receiving a from-only response populates `To` from that response; a
  test for `fromDatepickerMin`/`fromDatepickerMax` computed from a picked `To`; a test that `toDatepickerMax` never
  exceeds today (bracketed against real wall-clock time to avoid a flaky exact-instant comparison) even when `From`
  is picked as today itself; a test that `fromDatepickerMax` leaves room before today when neither side is picked;
  a test that each date field's clear control removes that field's request param and the field visibly re-populates
  after the reload; a test that the constraints tooltip is bound via `MatTooltip` on the Granularity label and no
  `.info-trigger` element remains; a chart-component test that the scrollable inner width scales with bucket count.
  Horizontal-scroll rendering itself and the filter icon's floated position are verified manually (not meaningfully
  assertable under jsdom/Karma's layout engine).

## Risks / Open Questions

Round 1's, Round 2's, and Round 3's original risks are resolved — see `docs/development/PHASE_3_EXT_PLAN.md` for the
implemented solutions; they are not repeated here.

Round 3's residual gap still stands, unaffected by Round 4 (flagged, not silently ignored): if a caller switches
`granularity` to something coarser (e.g. `YEAR`) before ever picking a date, the no-filter default still fires with
that coarser granularity, and "month-to-date" (at most ~31 days) can violate a much larger configured minimum (e.g.
`YEAR`'s 1 year), producing a `400`. This is a narrow edge case — the frontend's granularity always starts at `DAY`,
and this only triggers if it's changed before either date field is touched — outside what was reported.

Carried forward from Round 2 (historical; already resolved by the implemented design, see `PHASE_3_EXT_PLAN.md`):

- **Bootstrapping interaction order.** When neither `from`/`to` nor granularity has been touched yet, which
  constrains which first? A coherent two-way design is needed (e.g. granularity defaults to `DAY`; the `To` picker's
  bounds derive from `From` + the current granularity; changing granularity re-validates/re-derives those bounds;
  picking dates outside the current granularity's window should disable that granularity and prompt picking a valid
  one) — not fully prescribed here.
- **Configuration shape.** This is the first custom `app.*` namespace in `application.yml`. Today's min/max aren't
  always the same unit within one granularity (DAY: min 1 *day*, max 1 *month*) — `/plan-phase` should confirm
  whether bounds are best expressed as `(amount, ChronoUnit)` pairs per bound (min and max independently), and pick
  a binding mechanism (`@ConfigurationProperties`, not scattered `@Value`s, per idiomatic Spring practice).
- **Error-response mechanism.** Whether the richer error uses Spring's `ProblemDetail` extension properties
  (structured, machine-readable — available for free since `spring.mvc.problemdetails.enabled` is already on) versus
  only improving the free-text `detail` string. Extension properties let the frontend build its own message from
  structured data (reusing the same constraints it already fetched for AC5) rather than parsing prose; `/plan-phase`
  should confirm this doesn't conflict with the RFC 7807 shape already relied on elsewhere in the app.
- **Toolbar primary/secondary split.** This doc proposes date range + granularity + aggregation-type as
  always-visible, everything else collapsed into the filter menu — `/plan-phase`/UX judgment should confirm or
  adjust that split.
- **Reuse, not reinvention, of the filter-menu pattern.** `TransactionTableComponent`'s icon-button + `mat-menu`
  popover (including its documented workaround for `mat-menu` auto-closing on inner-control clicks) should be reused
  as-is for the Analytics toolbar's collapsed filters, for visual and behavioral consistency.
