# Phase 4 EXT 2 — Fix the Risk-Assessment UI Layout (Cards, Popup History)

**Status:** COMPLETE
**Depends on:** `PHASE_4_EXT.md` (`COMPLETE`, frozen — not reopened). This phase corrects only that phase's
frontend navigation/layout decisions (the "Risk Assessments" tab/route and the below-the-card trigger placement);
the backend multi-provider AI selection, WireMock realism, and `risk` package sub-structuring from `PHASE_4_EXT`
are correct and unchanged.

## Objective

`PHASE_4_EXT` misread the UX brief: it built a customer-wide "Risk Assessments" tab/route instead of a
transaction-scoped popup, and left the assessment trigger in a full-width actions row below the transaction card
instead of a clearly separate card beside it. This phase corrects both, restoring the original, more explicit
intent: assessment history is *per transaction*, reached via a closable popup from the transaction detail (not a
nav tab); the trigger/result lives in its own visually distinct card, side by side with the Transaction Details
card.

## Scope

- **In:**
  - Remove the "Risk Assessments" `mat-tab-nav-bar` tab and the `risk-assessments` child route entirely.
    `RiskAssessmentHistoryPageComponent` (and its route) is deleted — dead code, no longer reachable.
  - `TransactionDetailComponent`'s template becomes a two-card row: the existing Transaction Details `mat-card`
    (unchanged content) on the left, and a new "Risk Assessment" `mat-card` on the right, laid out with CSS
    flex/grid so they sit side by side (wrapping to stacked on narrow viewports). The right card has its own
    `mat-card-header` titled "Risk Assessment", `mat-card-content` hosting `<app-risk-assessment-trigger>`, and
    `mat-card-actions` hosting the "View Risk Assessments History" button.
  - `RiskAssessmentTriggerComponent`'s own template drops its internal `mat-card` wrapping for the
    running/complete/failed states (`progress-card`/`result-card`/`error-card` become plain content), since it
    now always renders inside the outer "Risk Assessment" card — avoids a nested card-in-card look. The
    component's state machine (`viewState`, `seenStages`, `result`, `errorMessage`, `trigger()`, event handling)
    is unchanged; only its own template's outer wrapper elements change.
  - "View Risk Assessments History" opens a **popup** (`MatDialog`, not yet used anywhere in this project but
    already available via the existing `@angular/material` dependency) containing the existing
    `RiskAssessmentHistoryTableComponent`, scoped to the current transaction, with a title and a close control.
    A new small wrapper, `RiskAssessmentHistoryDialogComponent`
    (`features/risk-assessment/risk-assessment-history-dialog/`), supplies the dialog chrome (title + close
    button) around the existing table component — the table itself is unchanged in its filtering/sorting/
    pagination behavior, only its hosting context changes from "routed page" to "dialog".
  - Revert `RiskAssessmentHistoryTableComponent`'s `transactionId` input from optional back to
    `@Input({required: true})` — `PHASE_4_EXT`'s optional-transactionId change existed only to support the
    now-removed customer-wide view. Revert `AiRiskAssessmentService.findHistory`'s `transactionId` parameter the
    same way. Remove the `transactionId` column from `HISTORY_COLUMNS` — redundant when every row in the dialog
    already belongs to the same transaction shown directly above it.
  - `AiRiskAssessmentController`/`AiRiskAssessmentHistoryService` (backend) are **unchanged** — `transactionId`
    was already optional at the API layer before `PHASE_4_EXT` (confirmed in `PHASE_4_EXT_PLAN.md`'s own text:
    "the existing, already-optional `transactionId` support ... backend unchanged"), so no backend edit is needed
    to revert the frontend to always supplying it.
- **Out:** No change to the SSE stage contract, the scoring algorithm, the two-table persisted model, the
  multi-provider AI selection (`app.ai.provider`), the WireMock delay/Anthropic stub, or the `risk` backend
  package's `persistence`/`engine`/`api`/`ai`/`dto` sub-structure — all of that `PHASE_4_EXT` work stands as-is.

## Functional Requirements

| Functionality | Description |
|---|---|
| Two-card transaction detail | Transaction Details and Risk Assessment render as two visually distinct `mat-card`s side by side within the expanded transaction row. |
| No Risk Assessments tab/route | The third tab and `risk-assessments` route are removed; `TransactionsPageComponent` has exactly the two tabs it had before `PHASE_4_EXT` (Transactions, Analytics). |
| Popup assessment history | "View Risk Assessments History" opens a closable `MatDialog` popup showing that transaction's assessment history table, without navigating away from the transaction table view. |
| Transaction-scoped history | The history table (dialog and service) is scoped to a single `transactionId` again — required, not optional; no `transactionId` column (redundant in a single-transaction popup). |
| Integrated, explicit risk assessment | The trigger/progress/result/error states render as the content of the "Risk Assessment" card itself, not as a separately-elevated nested card. |

## Acceptance Criteria

1. `TransactionsPageComponent` shows exactly two tabs (Transactions, Analytics); no "Risk Assessments" tab or
   `/customers/:id/risk-assessments` route exists anywhere in `app.routes.ts`.
2. Expanding a transaction row shows two side-by-side cards: "Transaction {id}" (existing detail content) and
   "Risk Assessment" (trigger button, then live SSE progress, then result/error — all inside this card, not a
   nested card-in-card).
3. Clicking "View Risk Assessments History" opens a popup dialog showing a table of that transaction's past
   assessments (not all of the customer's), with a visible close control; closing it returns to the transaction
   table unchanged, no navigation occurred.
4. `RiskAssessmentHistoryTableComponent.transactionId` and `AiRiskAssessmentService.findHistory`'s `transactionId`
   parameter are both required again; the history table has no `transactionId` column.
5. `./gradlew check` and `npm test` (including new/updated specs for the two-card layout, the dialog, and the
   reverted optional-transactionId inputs) pass.

## Testing Scope

Frontend: `transaction-detail.component.spec.ts` asserts both cards render with correct titles/content and that
clicking the history button opens `MatDialog` with the expected `transactionId`/`customerId` data (spy on
`MatDialog.open`); a new `risk-assessment-history-dialog.component.spec.ts` covers the dialog wrapper (renders
the table, close button calls `MatDialogRef.close()`); `risk-assessment-history-table.component.spec.ts` reverts
its optional-transactionId/column tests; `ai-risk-assessment.service.spec.ts` reverts its optional-transactionId
test; `transactions-page.component.spec.ts` drops the third-tab/route tests; `risk-assessment-trigger.component.
spec.ts` re-verified against the simplified template (existing `button`/`.stage-list` selectors should be
unaffected). No backend test changes expected (API unchanged).

## Risks / Open Questions

- None expected — this is a frontend-only layout correction with a precisely-specified target state; the backend
  API shape this depends on (optional `transactionId`) already exists and is already tested.
