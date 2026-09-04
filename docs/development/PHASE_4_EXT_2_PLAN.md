# Phase 4 EXT 2 — Implementation Plan

**Status:** COMPLETE
**Phase definition:** `docs/development/PHASE_4_EXT_2.md`

## Current State (verified)

`PHASE_4_EXT` (`COMPLETE`, frozen) built the wrong navigation shape for risk-assessment history. Confirmed against
`docs/specs/PROJECT_SPECIFICATION.md` line 37 — the PDF-derived requirement is: *"The operator should be able to
visualize the history of all AI Risk Assessments **per transaction**."* `PHASE_4_EXT`'s customer-wide tab/route
directly contradicts this higher-precedence source; this phase restores compliance, it isn't a stylistic
preference.

Exact current file contents (read fresh, not from memory):

- `app.routes.ts` has a `{ path: 'risk-assessments', component: RiskAssessmentHistoryPageComponent }` child route.
- `transactions-page.component.html` has a third `<a mat-tab-link routerLink="risk-assessments">Risk
  Assessments</a>` alongside Transactions/Analytics.
- `transaction-detail.component.html` renders one `<mat-card>` (Transaction Details) with a `<mat-card-actions>`
  holding `<app-risk-assessment-trigger>` and a `routerLink`-based "View Risk Assessments History" button —
  everything in one card, actions row below the content.
- `risk-assessment-trigger.component.html` wraps its `running`/`complete`/`failed` states each in their own
  `<mat-card class="progress-card|result-card|error-card">` — designed for a context where it was the *only* card
  in the section; now that it will always sit inside another card's `mat-card-content`, this nests card-in-card.
- `risk-assessment-history-table.component.ts`: `@Input() transactionId?: string` (optional), `HISTORY_COLUMNS`
  includes a `transactionId` column, `ai-risk-assessment.service.ts#findHistory`'s `transactionId` param is
  `string | undefined`, conditionally added to `HttpParams`.
- `risk-assessment-history-page/` — a 4-file thin wrapper component, routed, no longer wanted.
- No `MatDialog` usage exists anywhere in the frontend today — `@angular/material` is already a dependency
  (`^22.1.4`), so `MatDialogModule`/`MatDialogRef`/`MAT_DIALOG_DATA` need only be imported, no new package.
- Backend: `AiRiskAssessmentController`/`AiRiskAssessmentHistoryService` already accept `transactionId` as an
  **optional** query param — this predates `PHASE_4_EXT` (confirmed in `PHASE_4_EXT_PLAN.md`'s own text). No
  backend change is needed or planned; the frontend will simply always supply it again.

## Frontend Design

### 1. Remove the tab/route

- `app.routes.ts`: delete the `RiskAssessmentHistoryPageComponent` import and the `risk-assessments` child route
  entry, restoring exactly the pre-`PHASE_4_EXT` two-route array (`transactions`, `analytics`).
- `transactions-page.component.html`: delete the third `<a mat-tab-link routerLink="risk-assessments">` block.
- Delete the directory `frontend/src/app/features/risk-assessment/risk-assessment-history-page/` (all 4 files:
  `.ts/.html/.scss/.spec.ts`) — dead code once unrouted.

### 2. `TransactionDetailComponent` — two-card layout

`transaction-detail.component.html` restructures from one card to two, wrapped in a flex row:

```html
@if (transaction; as txn) {
  <div class="detail-cards">
    <mat-card class="transaction-card">
      <mat-card-header>...</mat-card-header>          <!-- unchanged content -->
      <mat-card-content>...</mat-card-content>          <!-- unchanged content -->
    </mat-card>

    <mat-card class="risk-assessment-card">
      <mat-card-header>
        <mat-card-title>Risk Assessment</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <app-risk-assessment-trigger [customerId]="customerId" [transactionId]="txn.transactionId" />
      </mat-card-content>
      <mat-card-actions>
        <button type="button" mat-button (click)="openHistory(txn.transactionId)">
          <fa-icon [icon]="faClockRotateLeft" /> View Risk Assessments History
        </button>
      </mat-card-actions>
    </mat-card>
  </div>
} @else {
  <p class="empty-state">Select a transaction to see its details.</p>
}
```

`transaction-detail.component.scss`: replace the current `mat-card-actions` flex rule with:

```scss
.detail-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: flex-start;
}

.transaction-card,
.risk-assessment-card {
  flex: 1 1 20rem;
  min-width: 0;
}
```

(`flex: 1 1 20rem` gives both cards equal growth from a sensible minimum, wrapping to stacked below ~40rem
container width — no new breakpoint/media-query machinery needed for a demo-scale app.)

`transaction-detail.component.ts`:
- Remove `RouterLink`/`routerLink` usage (the button now calls a method, not a route).
- Add `private readonly dialog = inject(MatDialog);` and:
  ```ts
  openHistory(transactionId: string): void {
    this.dialog.open(RiskAssessmentHistoryDialogComponent, {
      data: { customerId: this.customerId, transactionId },
      width: '900px',
      maxWidth: '95vw',
    });
  }
  ```
- Imports: add `MatDialogModule`, `RiskAssessmentHistoryDialogComponent`; drop `RouterLink`.

### 3. `RiskAssessmentTriggerComponent` — drop the nested-card look

`risk-assessment-trigger.component.html`: replace each `<mat-card class="...-card">...</mat-card>` wrapper with a
plain `<div class="...-panel">...</div>` (keeping `mat-card-header`/`mat-card-content`/`mat-card-actions` — which
are just CSS classes, not structural — removed too, replaced by plain markup with the same inner content) so it
renders as unelevated content inside the parent's `mat-card-content`. Concretely: `running` → `<div
class="progress-panel">` (was `mat-card`) with the `<ul class="stage-list">` moved directly inside (drop the inner
`mat-card-content` wrapper); `complete` → `<div class="result-panel">` with a plain header row (chip + score) then
the findings/recommendations paragraphs then a `<div class="result-actions">` holding "Run again" (was
`mat-card-actions`); `failed` → `<div class="error-panel">` with the message paragraph then a "Retry" button. The
`idle` case's plain `<button mat-raised-button>` is unchanged. No `MatCardModule` import is needed by this
component anymore (drop it); `MatChipsModule`/`MatProgressSpinnerModule`/`MatButtonModule`/`FaIconComponent` stay.

`risk-assessment-trigger.component.scss`: rename the `.progress-card`/`.result-card`/`.error-card` selectors to
`.progress-panel`/`.result-panel`/`.error-panel` (same rules — `max-width`, `.error-panel`'s left border accent);
everything else (`.stage-list`, `.risk-chip-*`, `.score-label`) is unchanged.

**Behavior is unchanged** — `risk-assessment-trigger.component.ts`'s state machine (`viewState`, `seenStages`,
`result`, `errorMessage`, `trigger()`, `isStageDone`/`isStageCurrent`, event handling) is not touched. The existing
`risk-assessment-trigger.component.spec.ts` queries `button`/`.stage-list` only (verified — no `mat-card`-specific
selectors), so it is expected to keep passing unmodified; re-run it as part of the test plan to confirm.

### 4. New `RiskAssessmentHistoryDialogComponent` (popup)

New directory `frontend/src/app/features/risk-assessment/risk-assessment-history-dialog/`:

- `risk-assessment-history-dialog.component.ts`:
  ```ts
  export interface RiskAssessmentHistoryDialogData {
    customerId: string;
    transactionId: string;
  }

  @Component({
    selector: 'app-risk-assessment-history-dialog',
    standalone: true,
    imports: [MatDialogModule, MatButtonModule, FaIconComponent, RiskAssessmentHistoryTableComponent],
    templateUrl: './risk-assessment-history-dialog.component.html',
    styleUrl: './risk-assessment-history-dialog.component.scss',
  })
  export class RiskAssessmentHistoryDialogComponent {
    readonly data = inject<RiskAssessmentHistoryDialogData>(MAT_DIALOG_DATA);
    private readonly dialogRef = inject(MatDialogRef<RiskAssessmentHistoryDialogComponent>);

    close(): void {
      this.dialogRef.close();
    }
  }
  ```
- `risk-assessment-history-dialog.component.html`:
  ```html
  <h2 mat-dialog-title>Risk Assessment History</h2>
  <mat-dialog-content>
    <app-risk-assessment-history-table [customerId]="data.customerId" [transactionId]="data.transactionId" />
  </mat-dialog-content>
  <mat-dialog-actions align="end">
    <button type="button" mat-button (click)="close()">
      <fa-icon [icon]="faXmark" /> Close
    </button>
  </mat-dialog-actions>
  ```
  (`faXmark` is already used elsewhere in this feature area — `risk-assessment-history-table.component.ts` — so
  no new icon dependency.)
- `risk-assessment-history-dialog.component.scss`: minimal — e.g. `mat-dialog-content { max-height: 70vh; }` so a
  long table scrolls within the popup rather than growing the viewport.
- `risk-assessment-history-dialog.component.spec.ts`: mounts the component directly with `MAT_DIALOG_DATA`/
  `MatDialogRef` test doubles (`{ provide: MAT_DIALOG_DATA, useValue: {...} }`, `{ provide: MatDialogRef, useValue:
  { close: jasmine.createSpy() } }`), asserts the nested history table receives the right `customerId`/
  `transactionId` inputs (query `By.directive(RiskAssessmentHistoryTableComponent)` and read `componentInstance`
  or assert the rendered table's request params — mirroring the existing pattern in
  `risk-assessment-history-table.component.spec.ts`'s own `historyUrl`/`httpMock` helpers, since the dialog needs
  `provideHttpClient()`/`provideHttpClientTesting()` too for the nested table to load), and that clicking Close
  invokes `MatDialogRef.close()`.

### 5. Revert transaction-scoping

- `ai-risk-assessment.service.ts#findHistory`: `transactionId: string | undefined` → `transactionId: string`;
  drop the `if (transactionId) { ... }` guard, restore the unconditional `.set('transactionId', transactionId)`
  on the base `HttpParams` chain (mirroring the pre-`PHASE_4_EXT` shape exactly).
- `ai-risk-assessment.service.spec.ts`: remove the `'omits transactionId from the query params when not
  provided'` test (added by `PHASE_4_EXT`, now testing a capability that no longer exists).
- `risk-assessment-history-table.component.ts`: `@Input() transactionId?: string` → `@Input({ required: true })
  transactionId!: string`.
- `risk-assessment-history-table.columns.ts`: remove the `{ key: 'transactionId', label: 'Transaction', filterType:
  'none' }` entry from `HISTORY_COLUMNS` (redundant — every row in the dialog already belongs to the transaction
  named in the dialog's own title/context).
- `risk-assessment-history-table.component.spec.ts`: remove the `'omits transactionId from the request when unset
  (customer-wide history)'` test; remove the `'renders a Transaction column identifying each row'` test (the
  column no longer exists) — restore the original `'expects transactionId'` assertions on every `historyUrl`
  request (already present in the remaining tests, just re-verify none relied on the optional path).

## File inventory

**Frontend — new:** `risk-assessment-history-dialog/risk-assessment-history-dialog.component.{ts,html,scss,
spec.ts}` (4 files).

**Frontend — deleted:** `risk-assessment-history-page/` (entire directory, 4 files).

**Frontend — modified:** `app.routes.ts`; `transactions-page.component.html` + `.spec.ts` (drop third-tab
assertions and the `risk-assessments` route-activation test added by `PHASE_4_EXT`); `transaction-detail.
component.{ts,html,scss,spec.ts}`; `risk-assessment-trigger.component.{html,scss}` (`.ts`/`.spec.ts` untouched);
`risk-assessment-history-table.component.ts` + `.columns.ts` + `.spec.ts`; `ai-risk-assessment.service.ts` +
`.spec.ts`.

**Backend:** none.

**Docs:** `docs/development/PHASE_4_EXT_2.md` (`Status` → `IMPLEMENTED` at `/implement` time), this plan file.
No `docs/DECISIONS.md` entry — this is a corrections-only phase with no new durable architectural decision (the
decision "history is per-transaction" is already the PDF's own requirement, not a new choice being recorded).

## Test plan → Acceptance-criteria mapping

| `PHASE_4_EXT_2.md` AC | Coverage |
|---|---|
| AC1 — no tab/route | `transactions-page.component.spec.ts` updated: the existing "renders tab links" test asserts exactly 2 links (`transactions`, `analytics`); the `PHASE_4_EXT`-added risk-assessments route-activation test is deleted; a new assertion (or reuse of the existing link-count check) confirms no `risk-assessments` link exists |
| AC2 — two side-by-side cards | `transaction-detail.component.spec.ts`: query two `mat-card` elements, assert their titles/roles ("Transaction {id}" vs "Risk Assessment"), assert the risk-assessment card contains the trigger's rendered content directly (no nested `mat-card` inside it — query `.progress-card`/`.result-card`/`.error-card` return nothing, `.progress-panel` etc. do once triggered) |
| AC3 — popup history | `transaction-detail.component.spec.ts`: spy/mock `MatDialog.open`, click the history button, assert it was called once with `RiskAssessmentHistoryDialogComponent` and `{ data: { customerId, transactionId } }`; `risk-assessment-history-dialog.component.spec.ts` covers the popup's own rendering/close behavior end-to-end |
| AC4 — transaction-scoped, required, no column | `risk-assessment-history-table.component.spec.ts` (required input, no transactionId column in rendered headers); `ai-risk-assessment.service.spec.ts` (transactionId always in params) |
| AC5 — full check green | `./gradlew check` + `npm test` run at `/implement` time before marking `IMPLEMENTED` |

## Risks / Open Questions

- None — confirmed no backend change needed (optional `transactionId` already existed pre-`PHASE_4_EXT`); no new
  npm dependency (`MatDialogModule` ships with the already-installed `@angular/material`); `provideAnimationsAsync()`
  is already registered app-wide in `app.config.ts`, so `MatDialog` works without further provider wiring.
