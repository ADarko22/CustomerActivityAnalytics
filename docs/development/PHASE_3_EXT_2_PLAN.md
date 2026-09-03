# Phase 3 EXT 2 Implementation Plan — Analytics Picker UX, Customer-Switch Navigation, Transaction Date Filter

**Status:** COMPLETE

Blueprint for `PHASE_3_EXT_2.md`. Frontend-only — the backend already fully supports everything this phase needs
(verified below). Read alongside `CLAUDE.md`, `docs/DECISIONS.md` (D15–D16 apply, unaffected by this phase; no new
decision expected — see "Design clarifications"), and `docs/development/PHASE_3_EXT_2.md`.

## Current State (verified this session, by reading each file in full)

- `analytics-panel.component.ts`: `load()`'s success handler unconditionally writes
  `this.fromDate.set(new Date(series.from))` / `this.toDate.set(new Date(series.to))` on every successful response
  (`PHASE_3_EXT.md` Round 4, AC12) — no distinction between "never touched" and "just explicitly cleared."
  `fromDatepickerMin`/`fromDatepickerMax`/`toDatepickerMin`/`toDatepickerMax` (Round 4, AC13) are already computed
  and already represent the correct boundary values for `startAt` (see Design below — no new computation needed).
  `ngOnChanges` only calls `load()` on `customerId` change; it does not reset `fromDate`/`toDate` or any touched
  state. `clearToIfNowInvalid()` sets `toDate` to `null` via a direct `.set(null)` call, not through
  `onToDateChange`.
- `customer-search.component.ts`: `onCustomerSelected` hardcodes `this.router.navigate(['/customers',
  customer.customerId, 'transactions'])`. `Router` is already injected; no `ActivatedRoute` is injected or needed —
  `Router.url` (a getter reflecting the current navigated URL) is sufficient to detect the active tab.
- `app.routes.ts`: `customers/:customerId` hosts `TransactionsPageComponent` with two children,
  `transactions`/`analytics`, matching `AnalyticsPanelComponent`/`TransactionTableComponent` respectively — the
  literal path segments `'transactions'`/`'analytics'` are exactly what the URL ends with and what
  `router.navigate` needs as the third array element.
- `transaction-table.columns.ts`: `ColumnDef.filterType` is `'text' | 'select' | 'boolean' | 'amount' | 'none'`;
  `createdAt` is `{ key: 'createdAt', label: 'Date', filterType: 'none' }`.
- `transaction-table.component.html`: one `@switch (column.filterType)` per column inside a shared per-column
  `<mat-menu>`, each case rendering its own fields inside `.filter-menu-content`; the `'amount'` case
  (lines 87-113) is the closest existing shape to mirror — two fields + one combined `mat-button` "Clear" — as
  opposed to Analytics' per-field clear-icon pattern (a different component, no need to match it).
- `transaction-table.component.ts`: no `MatDatepickerModule` imported yet.
  `provideNativeDateAdapter()` is already provided app-wide in `app.config.ts:15` (confirmed) — no new provider
  needed anywhere. `onFilterChange(key, value)`/`filterChange$` (debounced 300ms) is the generic path every filter
  already goes through; `isFilterActive(column)` special-cases `'amount'` today, needs a parallel `'date'` case.
  `ngOnChanges` resets only `pageIndex` on `customerId` change — `filters`/`activityType` are not reset today.
- `transaction.model.ts`: `TransactionFilter.from?: string` / `.to?: string` already exist.
- `transaction.service.ts`: `findOverview` forwards every defined `TransactionFilter` key generically as a query
  param — `from`/`to` need no special handling on the service side.
- Backend: `TransactionController` already declares `@RequestParam(required = false) Instant from`/`to` on the
  transactions-overview endpoint; `TransactionSpecifications.common(...)` already builds `createdAt >= from` /
  `createdAt <= to` predicates. **No backend changes in this phase.**

## Design clarifications (resolving `PHASE_3_EXT_2.md`'s two open Risk questions)

1. **`clearToIfNowInvalid()` × touched state.** Resolved as: unchanged behavior, no special-casing needed.
   `clearToIfNowInvalid()` sets `toDate` directly (`this.toDate.set(null)`), never through `onToDateChange`, so it
   never touches `toTouched` either way — if `to` was already operator-touched, it stays touched (blank, no
   auto-refill, exactly respecting "the operator last left it blank"); if `to` was still in "auto" mode, it stays
   "auto" and the next load naturally recomputes it for the new granularity, identical to today's behavior. No new
   code needed beyond the touched-flag mechanism itself — this interaction falls out correctly by construction.
2. **Reset-on-customer-switch scope.** Confirmed narrow, as originally scoped: `AnalyticsPanelComponent` resets
   `fromDate`/`toDate`/`fromTouched`/`toTouched`; `TransactionTableComponent` resets `filters`/`activityType`
   (which includes any date-range filter, since it lives in `filters`). Granularity, metric, and Analytics'
   secondary filters (status/currency/amount/type-specific) are intentionally left as operator "sticky preferences"
   that persist across a customer switch — not part of what was reported broken, and resetting them wasn't
   requested. No new `DECISIONS.md` entry — this is a bug-fix/UX-refinement scope decision, not a beyond-PDF
   architectural choice.

## Frontend Design

### 1. `analytics-panel.component.ts` — touched-state gating + reset

```ts
private fromTouched = false;
private toTouched = false;

onFromDateChange(date: Date | null): void {
  this.fromTouched = true;
  this.fromDate.set(date);
  this.clearToIfNowInvalid();
  this.change$.next();
}

onToDateChange(date: Date | null): void {
  this.toTouched = true;
  this.toDate.set(date);
  this.change$.next();
}

ngOnChanges(changes: SimpleChanges): void {
  if (changes['customerId']) {
    this.fromTouched = false;
    this.toTouched = false;
    this.fromDate.set(null);
    this.toDate.set(null);
    this.load();
  }
}
```

In `load()`'s success handler, gate the two existing sync writes:

```ts
next: (series) => {
  this.series.set(series);
  if (!this.fromTouched) this.fromDate.set(new Date(series.from));
  if (!this.toTouched) this.toDate.set(new Date(series.to));
},
```

### 2. `analytics-panel.component.html` — `startAt` on both calendars

```html
<mat-datepicker #fromPicker [startAt]="fromDatepickerMin()" />
...
<mat-datepicker #toPicker [startAt]="toDatepickerMax()" />
```

`fromDatepickerMin()` already equals "`to` minus the granularity's max span" (the earliest allowed `From`, i.e. the
value that maximizes the window); `toDatepickerMax()` already equals "`from` plus the granularity's max span,
capped at today" (the latest allowed `To`). Both already exist and are already correct for this purpose — this is
a pure template wiring change, no new TS logic.

### 3. `customer-search.component.ts` — preserve the active tab

```ts
onCustomerSelected(event: MatAutocompleteSelectedEvent): void {
  const customer = event.option.value as Customer;
  const tab = this.router.url.endsWith('/analytics') ? 'analytics' : 'transactions';
  this.router.navigate(['/customers', customer.customerId, tab]);
}
```

### 4. Transaction date-range filter

`transaction-table.columns.ts`: widen the union and change `createdAt`'s entry:

```ts
filterType: 'text' | 'select' | 'boolean' | 'amount' | 'date' | 'none';
...
{ key: 'createdAt', label: 'Date', filterType: 'date' },
```

`transaction-table.component.ts`: add `MatDatepickerModule` to `imports`; add two signals and handlers, and gate
`isFilterActive`:

```ts
readonly fromDateFilter = signal<Date | null>(null);
readonly toDateFilter = signal<Date | null>(null);

onFromDateFilterChange(date: Date | null): void {
  this.fromDateFilter.set(date);
  this.onFilterChange('from', date?.toISOString());
}
onToDateFilterChange(date: Date | null): void {
  this.toDateFilter.set(date);
  this.onFilterChange('to', date?.toISOString());
}
clearDateFilter(): void {
  this.fromDateFilter.set(null);
  this.toDateFilter.set(null);
  this.filters.update((current) => ({ ...current, from: undefined, to: undefined }));
  this.pageIndex.set(0);
  this.filterChange$.next();
}
```

Signal-bound (`[value]="fromDateFilter()"` / `[value]="toDateFilter()"` on the inputs), not the uncontrolled
`HTMLInputElement`-ref style `clearAmountFilter` uses — deliberately, to avoid the exact defect `PHASE_3_EXT.md`
Round 4 already found and fixed for the Analytics pickers (an uncontrolled `matDatepicker` input's displayed text
does not follow a signal write without an explicit `[value]` binding).

`isFilterActive`:
```ts
if (column.filterType === 'date') {
  return this.filters().from !== undefined || this.filters().to !== undefined;
}
```

`ngOnChanges`, add to the existing `customerId`-change branch:
```ts
this.filters.set({});
this.activityType.set('ALL');
this.fromDateFilter.set(null);
this.toDateFilter.set(null);
```

`transaction-table.component.html`, new case in the filter-menu `@switch` (mirrors the `'amount'` case's shape:
two fields, one combined "Clear" button; no min/max cross-constraints — there is no granularity/bucketing concept
on a plain list filter, so Analytics' bounding logic does not apply here):

```html
@case ('date') {
  <mat-form-field appearance="outline">
    <mat-label>From</mat-label>
    <input matInput [matDatepicker]="fromDatePicker" [value]="fromDateFilter()"
      (dateChange)="onFromDateFilterChange($event.value)" />
    <mat-datepicker-toggle matSuffix [for]="fromDatePicker" />
    <mat-datepicker #fromDatePicker />
  </mat-form-field>
  <mat-form-field appearance="outline">
    <mat-label>To</mat-label>
    <input matInput [matDatepicker]="toDatePicker" [value]="toDateFilter()"
      (dateChange)="onToDateFilterChange($event.value)" />
    <mat-datepicker-toggle matSuffix [for]="toDatePicker" />
    <mat-datepicker #toDatePicker />
  </mat-form-field>
  <button type="button" mat-button (click)="clearDateFilter()">
    <fa-icon [icon]="faXmark" size="xs" /> Clear
  </button>
}
```

## File inventory

**Frontend — modified only, no new files:**
- `features/analytics/analytics-panel/analytics-panel.component.{ts,html,spec.ts}`
- `features/customer-search/customer-search.component.{ts,spec.ts}`
- `features/transactions/transaction-table/transaction-table.columns.ts`
- `features/transactions/transaction-table/transaction-table.component.{ts,html,spec.ts}`

**Backend:** none.

## Test plan → Acceptance-criteria mapping

| `PHASE_3_EXT_2.md` AC | Coverage |
|---|---|
| AC1 — cleared field stays blank across reloads | `analytics-panel.component.spec.ts`: clear `To` (via `onToDateChange(null)`), flush a reload, then trigger a second reload (e.g. a granularity change) and flush it too — assert `toDate()` is still `null` after both, proving the touched-gate holds across multiple subsequent loads, not just one. |
| AC2 — blank-field calendar `startAt` | `analytics-panel.component.spec.ts`: with `From` set, query the `To` `MatDatepicker` directive instance (`By.directive(MatDatepicker)`) and assert `.startAt` equals `toDatepickerMax()`; symmetric test for `From`'s `startAt` equaling `fromDatepickerMin()` with `To` set. |
| AC3 — customer switch preserves tab | `customer-search.component.spec.ts`: existing `'navigates to the selected customer transactions page'` test covers the default (non-analytics) case unchanged; new test stubs `router.url` (`spyOnProperty(router, 'url', 'get')`) to end in `/analytics` and asserts `router.navigate` is called with `[..., 'analytics']` instead. |
| AC4 — customer switch resets per-customer state | `analytics-panel.component.spec.ts`: after picking an explicit `From`, change `customerId` via `fixture.componentRef.setInput` + `detectChanges`, flush the resulting reload, and assert the picked value is gone (`fromDate()` reflects the new customer's own synced default, not the old pick). `transaction-table.component.spec.ts`: after setting `activityType`/a filter, change `customerId` and assert `filters()` is `{}` and `activityType()` is `'ALL'` on the next request. |
| AC5 — transaction date-range filter | `transaction-table.component.spec.ts`: `openFilterMenu('Filter Date')` (reusing the existing helper), set a value via `onFromDateFilterChange`/`onToDateFilterChange`, assert the next request carries `from`/`to` params; call `clearDateFilter()` and assert both params are absent from the next request — mirrors the existing text/select-filter test shapes in the same file. |

Backend testing: none required — `TransactionController`/`TransactionSpecifications`' `from`/`to` support is
pre-existing and already covered by that suite; this phase does not touch backend code.

## Risks / Open Questions

None outstanding — both risks carried from `PHASE_3_EXT_2.md` are resolved above under "Design clarifications."
