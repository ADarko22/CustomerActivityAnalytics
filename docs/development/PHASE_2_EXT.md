# Phase 2 EXT — Transaction Table & Detail UX Refinement

**Status:** COMPLETE
**Depends on:** Phase 2 — refines its transaction table and detail UI; no new backend endpoints or data.

## Objective

Refine the Phase 2 transaction browsing experience: move per-column filters off a standalone row and into compact,
per-header controls; replace the fixed bottom-of-page detail panel with an inline expandable row; and apply a
deliberate, cohesive visual design (clean typography, pastel orange/white palette) across the app.

## Scope

- **In:** `TransactionTableComponent` header UX (a sort icon and a filter icon per column, the filter icon opening a
  small popover with that column's filter control), row-expansion detail view (replacing the standalone
  `TransactionDetailComponent` placement), and an app-wide Material theme/typography restyle.
- **Out:** no new backend endpoints, DTOs, or query parameters (Phase 2's REST contract, filters, and sort fields are
  reused as-is); no changes to customer search; no changes to what is filterable/sortable — only how it's presented;
  no auth (Phase 5).
- **Assumptions:** Phase 2's existing filter/sort query-param contract and `TransactionService`/`CustomerService`
  frontend services are unchanged; this is a presentation-layer-only phase.

## Requirements (refs into prior phases)

- Refines Phase 2's Feature 2 UX (activity overview + detail-on-select) per this explicit UX request — a beyond-spec
  UI decision (not sourced from `PROJECT_SPECIFICATION.md`), similar in nature to `docs/DECISIONS.md` D12.

## Functional Requirements

| Functionality           | Description                                                                                                                                                                                                                                                                                            |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Column header controls  | Each sortable/filterable column header shows two small icons: a sort icon (toggles asc/desc/none — same behavior as today's `mat-sort-header`) and a filter icon that opens a small popover anchored to the header, containing that column's filter control (text/select/boolean/amount-range, matching today's filter types). The standalone filter row is removed. |
| Row expand-to-detail    | Clicking/selecting a transaction row expands it in place to reveal the full polymorphic detail (today's `TransactionDetailComponent` content) instead of a separate panel at the bottom of the page. Expanding a row collapses any previously expanded row.                                          |
| Visual design system    | An app-wide styling pass: a legible type scale, generous whitespace, and a pastel palette with orange as primary and white/near-white as the dominant background — applied consistently across the search bar, table, filter popovers, and expanded detail content, replacing the current default violet Material theme. |

## Acceptance Criteria

1. Every filterable column header exposes a filter icon opening a popover with that column's filter control; applying
   or clearing a filter behaves identically (same debounce, same query params) to today's standalone filter row,
   which is removed from the UI.
2. Each sortable column header keeps a sort icon that toggles single-column sort direction, unchanged in behavior
   from Phase 2.
3. Selecting a transaction row expands it inline to show the full polymorphic detail; the standalone bottom-of-page
   detail panel is removed; at most one row is expanded at a time.
4. The app uses a consistent pastel orange/white visual theme (readable contrast, no default Material violet
   remaining) across the search bar, table, filter popovers, and expanded-row detail.
5. No regression to Phase 2's underlying data behavior — the same query params, filters, sorts, and activity-type
   column extension continue to work exactly as before; only their presentation changes.

## Testing Scope

Frontend only: filter-icon popover opens/applies/clears the correct query param per column type (text/select/
boolean/amount-range); sort icon still toggles direction correctly; row-expand shows the correct polymorphic detail
and collapses any previously expanded row. No backend changes, so no new backend tests are required — existing Phase
2 backend tests continue to pass unchanged.

## Risks / Open Questions

- Whether the existing hover-tooltip row summary (`docs/DECISIONS.md` D12) stays alongside the new expand-to-detail
  interaction or is superseded by it (redundant once expand is one click away) — left for `/plan-phase` to decide and
  document, superseding D12 if removed.
- Angular Material's `MatTable` row-expansion pattern (`multiTemplateDataRows`) combined with the existing per-column
  `ng-container`/`matColumnDef` definitions and `matSort` header icons — `/plan-phase` should confirm there's no
  structural conflict.
- Whether a built-in Material M3 palette gets close enough to the desired pastel orange, or a custom palette
  (`mat.define-theme` with hand-picked hues) is needed — left for `/plan-phase` to resolve.
