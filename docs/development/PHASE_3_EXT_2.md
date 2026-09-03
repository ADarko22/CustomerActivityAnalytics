# Phase 3 EXT 2 — Analytics Picker UX, Customer-Switch Navigation, Transaction Date Filter

**Status:** COMPLETE
**Depends on:** `PHASE_3_EXT.md` (`COMPLETE`) — refines the Analytics panel's date-picker behavior introduced there
(Round 4, AC12–15) and fixes a navigation bug in the customer search that predates it; adds a new filter to the
Transactions tab built in Phase 2. `PHASE_3_EXT.md` is frozen and not reopened by this phase.

## Objective

Three issues found using the app after `PHASE_3_EXT` shipped:

1. The Analytics date pickers are too eager: clearing one side to set up a new range gets it silently
   auto-recomputed by the very next reload, before the operator can finish picking a granularity or a replacement
   value — defeating the point of clearing it.
2. Switching customers via the header search bar while viewing Analytics always drops back to the Transactions tab
   instead of staying on Analytics.
3. The Transactions tab has no way to filter by transaction date, even though the backend already supports it.

## Scope

- **In:**
  - Once a date field (`From` or `To`) has been explicitly set or cleared by the operator, it is never again
    silently overwritten by an auto-computed value on a subsequent reload — it stays under the operator's control
    (blank or set) until they change it again or switch to a different customer. This does not change the
    *default* first-load behavior (`PHASE_3_EXT.md` AC12 — an untouched field still shows the server-computed
    default immediately).
  - When a blank date field's calendar is opened, it opens positioned at the boundary date that would maximize the
    window for the current granularity and the other, set side — the same boundary the backend would compute as
    that side's default — instead of opening on today's month regardless of context.
  - Switching customers via the header search preserves whichever tab (Transactions or Analytics) is currently
    active, instead of always navigating to Transactions.
  - Switching customers resets the Analytics date pickers (and their "touched" state) and the Transactions date
    filter, so each customer's view starts from that customer's own default rather than inheriting the previous
    customer's picks.
  - The Transactions tab's "Date" column gains a filter (mirroring the existing per-column icon + popover pattern
    already used by every other filterable column) accepting a `From`/`To` range over `createdAt`.
- **Out:** No backend changes — `GET /api/v1/customers/{id}/transactions` already accepts `from`/`to` and applies
  them (`TransactionController`, `TransactionSpecifications`), and the frontend's `TransactionFilter` model and
  `TransactionService` already carry `from`/`to` through generically; this phase only adds the missing UI. No
  changes to the Analytics range↔granularity validation logic, the chart, or the toolbar layout (`PHASE_3_EXT.md`
  Rounds 2–4 stand as implemented). No "Apply"/explicit-confirm button introduced — the panel stays fully reactive,
  as today.
- **Assumptions:** The existing per-field clear-icon affordance on the Analytics pickers (`PHASE_3_EXT.md` AC14)
  and the existing bidirectional/never-future calendar bounds (`PHASE_3_EXT.md` AC13) are correct and unchanged —
  this phase only changes *when* the auto-computed value is written back into the field, and *where* a blank
  field's calendar opens to, not the bounds or defaulting math itself.

## Requirements (refs into prior phases / bug reports)

- User-reported UX gap: the Analytics date pickers recompute a cleared field too quickly, before the operator can
  finish adjusting granularity or picking a new value — the operator asked for one side of the range to be able to
  "stay undefined" while they choose the granularity, and for the picker, once opened, to point at the extremity
  that defines the currently-shown graph, rather than an arbitrary "pause" or longer timeout.
- User-reported navigation bug: switching customers via the header search while on the Analytics tab always lands
  on the Transactions tab.
- User-requested feature: a transaction-date range filter on the Transactions tab, matching how every other
  filterable column already works there.

## Functional Requirements

| Functionality | Description |
|---|---|
| Date field stays under operator control once touched | Explicitly setting or clearing `From`/`To` stops that field from being overwritten by an auto-computed value on later reloads, until the operator changes it again or switches customers. |
| Blank-field calendar opens at the relevant boundary | Opening `From`'s calendar when blank shows the month of the earliest allowed date given `To` and the granularity; opening `To`'s calendar when blank shows the month of the latest allowed date (capped at today) given `From` and the granularity. |
| Customer switch preserves the active tab | Selecting a different customer from the header search keeps the operator on whichever tab (Transactions/Analytics) they were already viewing. |
| Customer switch resets per-customer view state | The Analytics date pickers/touched-state and the Transactions date filter reset to that new customer's own defaults on switch, rather than carrying over the previous customer's picks. |
| Transaction date-range filter | The Transactions tab's Date column has a `From`/`To` filter, consistent with the existing per-column filter-menu pattern, narrowing the list to transactions whose `createdAt` falls within the range. |

## Acceptance Criteria

1. On the Analytics tab, clearing `To` (via its clear icon) and then changing the Granularity selection one or more
   times leaves `To` visibly blank throughout — it is not silently repopulated by any of the resulting reloads. The
   chart may still update live as granularity changes; only the picker's own displayed value must stay under the
   operator's control.
2. With `To` blank and `From` set, opening `To`'s calendar shows the month containing `From` plus the selected
   granularity's configured max span, capped at today — the same value the backend would use as `To`'s default if
   left blank. With `From` blank and `To` set, opening `From`'s calendar shows the month containing `To` minus that
   max span.
3. From a seeded customer's `.../analytics` route, using the header search to select a different customer navigates
   to that customer's `.../analytics`, not `.../transactions`. From `.../transactions`, switching customers stays
   on `.../transactions` (existing behavior, unaffected).
4. After switching customers from the Analytics tab, the `From`/`To` pickers show the new customer's own default
   range (per `PHASE_3_EXT.md` AC12), not a range explicitly picked for the previous customer. After switching
   customers from the Transactions tab, an active date-range filter from the previous customer is cleared, not
   carried over.
5. The Transactions tab's Date column header has a filter icon (matching every other filterable column) opening a
   popover with `From`/`To` date fields and a "Clear" action; setting a range narrows the table to transactions
   whose date falls within it; clearing removes the filter and restores the full list.

## Testing Scope

- Frontend: `analytics-panel.component.spec.ts` — a test that clearing `To` and then flushing one or more
  subsequent reloads leaves `toDate()` null; a test that the calendars' `startAt` values reflect the correct
  boundary for a blank field given the other side and the selected granularity; a test that switching `customerId`
  resets both date signals and their touched-state, still producing that customer's own default on the next load.
- Frontend: `customer-search.component.spec.ts` — a test that selecting a customer while the current URL ends in
  `/analytics` navigates to the new customer's `/analytics`, and to `/transactions` otherwise.
- Frontend: `transaction-table.component.spec.ts` — a test that the Date column's filter menu applies `from`/`to`
  request params and that "Clear" removes them; a test that switching `customerId` resets `filters()` and
  `activityType()`.
- No backend test changes expected — the transaction list endpoint's `from`/`to` support is pre-existing and
  already covered by its own test suite.

## Risks / Open Questions

Both open questions below are resolved in `docs/development/PHASE_3_EXT_2_PLAN.md`'s "Design clarifications"
section; kept here as historical record of what was flagged, not repeated in full.

- **"Touched" state is in-memory only**, scoped to the component instance's lifetime — it does not persist across
  a hard refresh or a direct deep-link to a different customer (those already get a fresh component instance, so
  this is consistent with "switching customers resets it," not a gap).
- **Interaction with `clearToIfNowInvalid()`** (existing, `PHASE_3_EXT.md` Round 2) — resolved: the forced clear it
  performs never touches the touched-flag either way, so the field simply stays in whatever mode (auto/touched) it
  was already in, which was the intended behavior.
- **Scope boundary for "reset on customer switch"** — resolved: intentionally narrow (Analytics date pickers +
  touched-state, Transactions filters/activity-type). Granularity, metric, and Analytics' secondary filters
  persist as operator "sticky preferences" across a switch, by design, not reset along with the rest.
