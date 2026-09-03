# Phase 2 EXT Implementation Plan — Transaction Table & Detail UX Refinement

**Status:** COMPLETE

Blueprint for `PHASE_2_EXT.md`. Pure Angular presentation-layer refinement of Phase 2's transaction browsing UX — no
backend, schema, or API changes. Read alongside `CLAUDE.md` (conventions) and `docs/DECISIONS.md` D12 (the hover
tooltip this phase reconsiders).

## Current State (verified)

- `TransactionTableComponent` renders a `.filter-row` of standalone `mat-form-field`s (one per filterable column,
  built from `transaction-table.columns.ts`'s `ColumnDef[]`) above the table, and sorts via `mat-sort-header` on the
  whole `<th>`. Row click emits `transactionSelected`.
- `TransactionsPageComponent` owns a `selectedTransaction` signal and renders `<app-transaction-detail>` (a `mat-card`
  with per-activity-type fields) **below** the table, wired to that output.
- `frontend/src/styles.scss` sets `mat.theme({ color: mat.$violet-palette, typography: Roboto, density: 0 })` — the
  only place a color palette is configured; no component overrides raw colors, so this single file drives nearly all
  of the app's current look.
- `@fortawesome/angular-fontawesome`, `@fortawesome/fontawesome-svg-core`, `@fortawesome/free-solid-svg-icons` were
  added to `package.json` in Phase 2 but are **not used anywhere yet** (verified: no `fa-icon`/`FontAwesome` reference
  in `frontend/src`). `MatMenuModule`, `MatButtonModule`, `MatIconModule` are likewise unused so far but already
  available (part of the already-installed `@angular/material` package — no new npm install needed for this phase).
- Verified via `node_modules/@angular/material/core/theming/_palettes.scss`: Angular Material ships a built-in
  `mat.$orange-palette` M3 system palette — resolves Clarification/Risk #3 from `PHASE_2_EXT.md` without a custom
  palette definition.

## Design clarifications (resolving `PHASE_2_EXT.md`'s open questions)

1. **D12's hover tooltip is superseded, not kept.** `PHASE_2_EXT.md` explicitly left this for the plan to decide.
   Once a row expands in place to show the *full* detail on a single click, the tooltip's one-line summary (status,
   amount, currency) is strictly less information delivered no faster — it adds a second, redundant hover affordance
   for no remaining benefit. This plan removes `TransactionTableComponent`'s `matTooltip`/`rowSummary()` entirely and
   records a new `docs/DECISIONS.md` entry marking D12 `Superseded by D14` (assigned as an `/implement`-time doc task,
   mirroring how Phase 2's plan deferred its own doc reconciliation — see File inventory).
2. **No structural conflict between `multiTemplateDataRows` and the existing per-column `matColumnDef`s.** Angular
   Material's standard "expandable rows" pattern adds one extra, hidden row template (a distinct `matColumnDef` id,
   e.g. `expandedDetail`, spanning full width via `[attr.colspan]`) selected by a `when` predicate on `matRowDef` —
   it doesn't touch the existing per-column defs or `matSort` at all. The existing `displayedColumns()` computed stays
   the header/normal-row column list, completely unchanged; the detail row's `matRowDef` binds its own `columns` to
   the literal single-element array `['expandedDetail']` — not `displayedColumns()` plus it (see Row expand-to-detail
   below for why mixing the two would duplicate normal cells inside the detail row).
3. **Built-in `mat.$orange-palette`, not a custom palette.** Confirmed available (see Current State) — simplest path,
   no new SCSS palette authoring.
4. **No dedicated "expand" chevron column.** Material's official example also adds a visible chevron-icon column
   (clicked to expand) reused in every row. Since the user's ask is "the row" folding out and the whole row is
   already clickable today (`(click)="onRowClick(row)"`), this plan skips the extra chevron column — one less visual
   element, consistent with the "clean dashboard" styling goal — and keeps click-anywhere-on-the-row-to-expand.

## Frontend Design

### Column header: sort icon (kept) + filter icon (new) + popover

- **Sort icon is the existing `mat-sort-header` directive, relocated, not reimplemented.** Today `mat-sort-header` is
  on the whole `<th>`; it moves to wrap only the header **label** (e.g. a `<span mat-sort-header>{{ column.label }}
  </span>` inside the `<th>`), so clicking the new filter icon button beside it doesn't also toggle sort. Behavior
  (asc → desc → none cycle, `onSortChange`) is byte-for-byte the same directive as Phase 2 — satisfies AC2 with zero
  logic change. Material's sort arrow is tuned via SCSS to stay visible (not hover-only) so it reads as a deliberate
  small icon, matching the "two small icons" framing.
- **Filter icon is new**, rendered only for columns where `column.filterType !== 'none'`: a small `<button
  mat-icon-button [matMenuTriggerFor]="filterMenu">` containing a FontAwesome `faFilter` icon (`<fa-icon
  [icon]="faFilter" />`, imported as the standalone `FaIconComponent` — this is the first real use of the
  already-installed FontAwesome packages, matching `CLAUDE.md`'s icon-library choice instead of introducing
  `MatIconModule`/ligature icons). The icon is visually highlighted (a filled/colored state) when that column
  currently has an active filter value, so operators can see what's filtered without opening the menu.
- **The popover is a `<mat-menu>`**, one per column, declared inside the same `@for` loop as the column definitions
  (each iteration gets its own scoped `#filterMenu="matMenu"` template reference — a standard, supported Angular
  pattern, no id collisions). Its content is exactly today's `.filter-row` control for that column (text input /
  `mat-select` / boolean `mat-select` / min+max amount inputs), unchanged in behavior (same `onFilterChange` calls,
  same 300 ms debounce). Because the controls inside `<mat-menu>` are not `mat-menu-item`s, typing/selecting inside
  them does **not** auto-close the menu — live, debounced filtering while the popover stays open works exactly as it
  does today in the standalone row.
- **Clearing a filter reuses each column's existing "unset" affordance — no separate, redundant "Clear" button is
  added.** `select`/`boolean` columns (`status`, `cardPresent`, etc.) already have a built-in unset path: the
  existing `<mat-option [value]="undefined">Any</mat-option>` entry, unchanged. `text`/`amount` columns have no such
  built-in control today (clearing means deleting the typed text), so **only these two filter types** gain a small
  inline "Clear" icon/button inside their popover (next to the input, calling `onFilterChange(key, undefined)` /
  resetting both min and max for `amount`) — resolving the ambiguity between the two mechanisms by scoping Clear to
  exactly the filter types that lack one today.
- The standalone `.filter-row` block and its container `<div class="filter-row">` are deleted entirely.

### Row expand-to-detail (replaces the bottom-of-page panel)

- `TransactionTableComponent` gains an `expandedTransactionId = signal<string | null>(null)` and a
  `toggleExpand(row: Transaction)` method (sets it to `row.transactionId`, or `null` if that row is already
  expanded) — replaces the current `onRowClick`/`transactionSelected` output, which is deleted (no other consumer
  after this change).
- `<table mat-table ... multiTemplateDataRows>` (the `multiTemplateDataRows` attribute is required whenever more than
  one `matRowDef` can match the same data item — a common, easy-to-miss requirement, called out explicitly here).
- A new `matColumnDef="expandedDetail"` renders a single `<td [attr.colspan]="displayedColumns().length">` containing
  `<app-transaction-detail [transaction]="row" />` (the existing component, reused as-is, now rendered per expanded
  row instead of page-level). Its `matRowDef` binds `columns: ['expandedDetail']` — **only** that one pseudo-column
  id, not `displayedColumns()` plus it — so the detail row renders exactly one full-width spanning cell instead of
  re-rendering every normal column's `<td>` alongside it (`MatTable` renders one `<td>` per id listed in a row def's
  `columns` array, so including `displayedColumns()` there would duplicate the normal cells in the detail row and
  break the colspan layout). It also uses `when: (_, row) => row.transactionId === expandedTransactionId()` and is
  declared **after** the normal data `matRowDef` (Material renders detail rows immediately following their owning
  data row). No `displayedColumnsWithDetail` computed is needed — `displayedColumns()` (unchanged) drives the header
  and normal data row exactly as before; the detail row's `columns` binding is the standalone literal
  `['expandedDetail']`.
- `TransactionDetailComponent` itself is unchanged — same inputs, same per-activity-type template — only its caller
  changes.
- `TransactionsPageComponent` is simplified: drop `selectedTransaction` signal, `onTransactionSelected`, the
  `TransactionDetailComponent` import/usage, and the now-unused `Transaction` model import. Its template becomes just
  `<app-transaction-table [customerId]="customerId()" />`.

### Visual design system (pastel orange/white)

- `frontend/src/styles.scss`: swap `color: mat.$violet-palette` → `color: mat.$orange-palette` in the existing
  `mat.theme(...)` call. Since no component currently hardcodes a color (verified in Current State), this one change
  cascades through every Material component (search field, table, menus, cards) automatically — the bulk of AC4 is
  satisfied here, not via per-component overrides.
- If the M3-generated default surface/background tone (typically a very light neutral, not pure white) doesn't read
  as "white" enough against the orange primary, override the relevant M3 system color CSS custom properties (e.g.
  `--mat-sys-surface`, `--mat-sys-background`) under `:root` in the same file — verify visually during `/implement`
  and tune rather than guessing exact hex values now.
- Light readability/whitespace tuning in `styles.scss` (base `font-size`/`line-height` on `body`, slightly more
  generous `main` padding in `app.component.scss`) — a small, deliberate pass, not a new design-system layer.
- New-element-only styling (no broad rewrites needed elsewhere): the active-filter icon's highlighted state, and the
  expanded-detail row's background tint (e.g. a subtle warm neutral to visually nest it under its owning row), both
  in `transaction-table.component.scss`. The now-unused `.filter-row` SCSS rule is removed.

## File inventory

**Frontend — modified:**
- `transaction-table.component.ts` — add `expandedTransactionId` signal + `toggleExpand`; remove `transactionSelected`
  output; `displayedColumns()` stays unchanged (no new computed needed — the detail row's `columns` binding is the
  literal `['expandedDetail']`, not derived from `displayedColumns()`); import `FaIconComponent`, `MatMenuModule`,
  `MatButtonModule`, `TransactionDetailComponent`; remove `rowSummary()`/tooltip wiring.
- `transaction-table.component.html` — restructure each header cell (label+sort span, filter icon+menu for filterable
  columns); delete the `.filter-row` block; add `multiTemplateDataRows` and the `expandedDetail` row/column defs.
- `transaction-table.component.scss` — header cell inline layout, active-filter icon state, expanded-row styling;
  remove now-dead `.filter-row` rule.
- `transaction-table.component.spec.ts` — update per Test plan below.
- `transactions-page.component.ts` — remove `selectedTransaction`, `onTransactionSelected`, unused imports.
- `transactions-page.component.html` — remove `<app-transaction-detail>`.
- `transactions-page.component.spec.ts` — remove the now-invalid "shows transaction detail once a row is selected"
  test; keep the route-`customerId` test.
- `styles.scss` — palette swap + light readability/whitespace tuning.
- `app.component.scss` — minor padding/spacing tune only, if needed for visual consistency with the new palette.

**Frontend — unchanged (reused as-is):** `transaction-detail.component.ts/html/scss` (+ its spec), `customer-search.*`,
`page.model.ts`, `customer.model.ts`, `transaction.model.ts`, `customer.service.ts`, `transaction.service.ts` — no
data/query-param contract changes.

**Backend:** none.

**Documentation reconciliation (assigned as an `/implement`-time task):** `docs/DECISIONS.md` gains `D14` recording
the hover-tooltip removal and marks `D12`'s Status as `Superseded by D14`, per Design Clarification #1.

## Test plan → Acceptance-criteria mapping

| `PHASE_2_EXT.md` AC | Frontend coverage |
|---|---|
| AC1 — filter icon opens a popover; apply/clear behaves identically (same debounce, same query params); standalone row removed | `transaction-table.component.spec.ts`: open the menu (`trigger.openMenu()`), interact with the control inside it, assert the same debounced query param appears on the resulting HTTP request as today's test already asserts; a new test asserts selecting "Any" clears a `select`/`boolean` column's param, and another asserts the new inline "Clear" control removes a `text`/`amount` column's param(s). |
| AC2 — sort icon keeps toggling single-column sort direction, unchanged | Existing `onSortChange` test is retained as-is (directive unchanged, only its DOM position moved) — additionally assert the header now renders both the sort-labeled span and the filter button. |
| AC3 — row expands inline to full detail; bottom panel removed; only one row expanded at a time | New tests: clicking a row reveals its `expandedDetail` row with the correct polymorphic content; clicking a second row collapses the first (only one expanded at once); clicking an already-expanded row collapses it. `transactions-page.component.spec.ts` no longer references transaction selection/detail. |
| AC4 — consistent pastel orange/white theme, no violet remaining | Manual/visual verification (no automated visual-regression tooling in this project) — confirm via `ng serve` that the search bar, table, filter popovers, and expanded detail all render the orange/white theme; grep confirms no remaining `violet` reference in `styles.scss`. |
| AC5 — no regression to Phase 2's data behavior | Reuses the same `TransactionService.findOverview` call sites and query-param construction untouched; existing `customer-search`/`transaction.service` specs are unaffected and continue to pass. |

`ng lint` and `npm test` (Karma) must stay green; no backend changes, so `./gradlew check`'s backend leg is
unaffected — only the frontend leg is re-verified.

## Risks / Open Questions

- **`mat-sort-header` on a `<span>` instead of the whole `<th>`:** confirm during `/implement` that Material still
  renders the sort arrow and applies the correct ARIA/click behavior when the directive host is narrowed from `<th>`
  to an inner `<span>` — if Material requires the host to be the actual header-cell element, fall back to keeping
  `mat-sort-header` on the `<th>` and instead stop the filter button's click from bubbling
  (`(click)="$event.stopPropagation()"`) so it doesn't also trigger sort.
- **M3 default surface tone vs. "white"** — verify visually; may need the CSS-custom-property override called out
  above rather than the seed palette alone.
- **`multiTemplateDataRows` interaction with the paginator/sort** — confirm expanding a row doesn't miscount page
  size or break `matSort`'s column-header targeting (Material's official pattern already accounts for this, but
  verify against this project's existing `mat-paginator`/`matSort` wiring during `/implement`).
