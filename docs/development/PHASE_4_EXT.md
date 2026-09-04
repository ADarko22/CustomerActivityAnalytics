# Phase 4 EXT — Risk Assessment UX, Multi-Provider AI, and Backend Cleanup

**Status:** COMPLETE
**Depends on:** Phase 4 (AI Risk Assessment) — refines its UX and AI-provider configurability; no changes to the
scoring algorithm, the two-table persisted model, or the SSE stage contract.

## Objective

Four rounds of follow-up work on the completed Phase 4 feature: (1) reorganize the transaction-detail UI so the AI
Risk Assessment trigger sits directly with the transaction card and a new dedicated view replaces the nested
per-transaction history table; (2) make the AI provider, API key, and model genuinely configurable — fixing an
abstraction leak where orchestration code reached into an OpenAI-specific config key — and add real Anthropic
support, documented end-to-end including WireMock record-mode; (3) give the offline WireMock demo a realistic
response delay so the SSE progress UI is actually visible; (4) sub-package the `risk` backend package, which has
grown to 19 top-level classes. Clean, idiomatic code is a hard requirement throughout, not just for new code —
where fixing an item means correcting an existing shortcut (the OpenAI-specific `@Value` leak), fix it rather than
building around it.

## Scope

- **In:**
  - **UI reorganization:** the "Run AI Risk Assessment" button moves to sit directly with (not below, in a
    separately-headed section under) the transaction detail card, in the same expanded row (D14). A second button,
    "View Risk Assessments History", sits alongside it. The per-transaction inline history table currently embedded
    in the expanded row is removed. Clicking "View Risk Assessments History" navigates to a new, dedicated view
    listing that *customer's* AI risk assessments across all of their transactions (not scoped to one transaction),
    using the existing, already-optional `transactionId` support on `GET /customers/{id}/ai-assessments` (backend
    unchanged) — with a Transaction ID column added so each row's origin is identifiable. The history table's
    current per-row expand-to-show-rule-contributions interaction is removed entirely (a flat row — risk level,
    score, findings, recommendations, triggered-at — is sufficient; no drill-down is required). The existing
    "Run AI Risk Assessment" live-progress → final-result behavior (SSE stage list → result/error card) is
    unchanged, only its placement changes.
  - **Configurable AI provider/API key/model, plus Anthropic:** `app.ai.provider` becomes a real selector (not just
    a log label) choosing which `RiskAssessmentAiClient` bean is active. A second implementation is added for
    Anthropic (Spring AI's Anthropic starter, managed by the already-imported `spring-ai-bom`), configurable via
    its own `spring.ai.anthropic.api-key`/`chat.options.model`/`base-url`, mirroring the existing OpenAI
    configuration shape exactly. `AiRiskAssessmentOrchestrator`'s direct
    `@Value("${spring.ai.openai.chat.options.model}")` injection is removed — the active model name becomes
    something each `RiskAssessmentAiClient` implementation reports about itself, so orchestration code no longer
    names any specific provider's config key.
  - **Anthropic offline-demo + record-mode support, documented:** the WireMock offline stub setup and the
    record-mode workflow (`local-environment/wiremock/README.md`) are extended to cover Anthropic — including that
    Anthropic's Messages API uses a different request path than OpenAI's chat-completions endpoint, so the stub
    mapping(s) must match whichever provider is currently selected. Document, step by step, how to point
    `app.ai.provider=anthropic` at a real `ANTHROPIC_API_KEY` and WireMock's record mode to capture a real
    Anthropic response as a new offline stub.
  - **WireMock response delay:** the offline demo's stubbed AI response gets an artificial delay
    (`fixedDelayMilliseconds`, a native WireMock 3.9.1 mapping field — confirmed present in the current stub
    structure) long enough that the `MODEL_CALL` SSE stage is visibly "in progress" in the UI rather than resolving
    instantly, comfortably within the existing `app.risk.assessment-timeout` (45s) / `sse-timeout` (50s) headroom.
    Confirmed this does not affect `AiRiskAssessmentWireMockReplayTest` (it stubs its own in-process WireMock
    instance, independent of the Docker-mounted mapping file). No artificial delay is added to backend
    orchestration code itself (e.g. no `Thread.sleep()` between RAG stages) — that would fake realism in
    production code for a demo-only concern, which the Clean Code requirement above rules out; only the
    already-external WireMock stub gets the delay.
  - **`risk` package sub-packaging:** the 19 top-level classes in
    `backend/.../risk/` split into cohesive sub-packages (recommended grouping below; `/plan-phase` may adjust
    naming) — a mechanical move with no behavior change:
    - `risk.persistence` — JPA entities/repositories/specifications/projections/enums: `RiskRule`,
      `RiskRuleRepository`, `RiskFinalAssessment`, `RiskFinalAssessmentRepository`,
      `RiskFinalAssessmentSpecifications`, `RiskAssessmentLineItem`, `RiskAssessmentLineItemId`,
      `RiskAssessmentLineItemRepository`, `RuleContributionRow`, `RiskLevel`, `RuleScope`,
      `RiskAssessmentPersistenceService`.
    - `risk.engine` — the orchestration/RAG/scoring layer: `AiRiskAssessmentOrchestrator`,
      `RiskRuleRetrievalService`, `AssessmentHistoryRetrievalService`, `PromptContextMapper`,
      `RiskScoringService`, `RiskAssessmentProperties`.
    - `risk.api` — `AiRiskAssessmentController`, `AiRiskAssessmentHistoryService`.
    - `risk.ai` and `risk.dto` — already exist as sub-packages today; unchanged in scope, `risk.ai` gains the new
      Anthropic client.
- **Out:** the scoring algorithm (`weight × relevance`), the two-table persisted model, the SSE stage names/order,
  and the `AiRiskAssessmentDto`/`AiRiskAssessmentEventDto` response shapes are all unchanged. No change to
  `TransactionTableComponent`'s own row-expand mechanism (D14) — only what renders inside the expanded row's AI
  section. No new backend query/filter capability is required for the new history view beyond what
  `GET /customers/{id}/ai-assessments` already supports (optional `transactionId`). No auth (Phase 5). No
  risk-rule CRUD (Phase 5). No third AI provider beyond OpenAI/Anthropic.
- **Assumptions:** Spring AI's `spring-ai-bom` (already imported at the pinned version) manages a
  `spring-ai-starter-model-anthropic` artifact compatible with the existing `spring-ai-starter-model-openai` setup
  — `/plan-phase` must confirm this resolves before committing to the dependency addition, falling back to
  documenting the gap as a risk if it doesn't. Anthropic's Spring AI client supports a `base-url` override
  analogous to OpenAI's (needed for WireMock offline/record-mode reuse) — `/plan-phase` must confirm.

## Requirements (refs into Phase 4 / user follow-up)

- UX follow-up on Phase 4's Functional Requirements (`PHASE_4.md`: "AI Risk Assessment" triggered "by a button next
  to a transaction" and "AI Risk Assessment History" "made available... to the operator") — reinterpreting "next
  to a transaction" and "made available to the operator" with a more deliberate, less cluttered layout than the
  first implementation.
- Directly closes a gap against Phase 4's own NFR ("Configurability: AI provider, model name, and prompt templates
  are externalized as configuration/code artifacts, never hardcoded") and `docs/DECISIONS.md` D18 ("a second
  provider would implement" the `RiskAssessmentAiClient` interface) — this EXT is the first phase to actually
  deliver a second, selectable provider, and to fix the orchestrator's OpenAI-specific `@Value` leak that D18's own
  interface seam didn't quite achieve in practice.
- User-requested infra realism (WireMock delay) and code-quality maintenance (package structure) — beyond-spec,
  beyond-PDF requests in the same spirit as prior `_EXT` phases.

## Functional Requirements

| Functionality | Description |
|---|---|
| Integrated trigger button | The "Run AI Risk Assessment" button renders directly with the transaction detail card in the expanded row, not in a separately-headed section below it. Its existing live-progress → result/error behavior is unchanged. |
| History-view navigation button | A second button, "View Risk Assessments History", sits alongside the trigger button and navigates to a new, dedicated view. |
| Customer-wide, flat history view | The new view lists all of the current customer's AI risk assessments (across every transaction, using the backend's existing optional `transactionId` support), one flat row per assessment (risk level, score, findings, recommendations, triggered-at, and a Transaction ID column) — no per-row drill-down/expansion. |
| No more nested history table in the detail row | The transaction-detail expanded row no longer embeds a per-transaction history table. |
| Selectable AI provider | `app.ai.provider` (`openai` or `anthropic`) determines which `RiskAssessmentAiClient` bean is active; each provider is configured via its own standard Spring AI keys (`spring.ai.<provider>.api-key`/`chat.options.model`/`base-url`). |
| No orchestration-layer provider coupling | `AiRiskAssessmentOrchestrator` no longer reads any provider-specific configuration key directly; the active model name is reported by whichever `RiskAssessmentAiClient` is active. |
| Anthropic offline demo + record mode, documented | WireMock offline replay and the record-mode workflow work for `app.ai.provider=anthropic` the same way they do for OpenAI today, with the request-path difference (Anthropic Messages API vs. OpenAI chat-completions) accounted for in the stub mapping(s); documented step by step in `local-environment/wiremock/README.md` (or equivalent). |
| Realistic offline demo pacing | The WireMock-stubbed AI response carries a fixed delay long enough for the `MODEL_CALL` SSE stage to be visibly in progress in the UI, without approaching the configured assessment/SSE timeouts. |
| `risk` package sub-packaging | The `risk` package's ~19 top-level classes are grouped into cohesive sub-packages (persistence / engine / api, alongside the existing `ai`/`dto`); package-private visibility and existing ArchUnit rules continue to hold; no behavior change. |

## Acceptance Criteria

1. In a transaction's expanded detail row, "Run AI Risk Assessment" and "View Risk Assessments History" render as
   two clearly associated buttons directly with the transaction detail card — no separate "AI Risk Assessment"
   section heading, no inline history table in this row.
2. Clicking "Run AI Risk Assessment" still streams live SSE stage progress and ends in a final result or error
   card, unchanged in behavior from today, just relocated.
3. Clicking "View Risk Assessments History" navigates to a new view showing a flat, non-expandable table of every
   AI risk assessment for the current customer (across all of their transactions), including which transaction
   each row belongs to.
4. Setting `app.ai.provider=anthropic` (plus a valid Anthropic API key and model) causes the backend to call
   Anthropic instead of OpenAI for the model-call stage, with no other behavior change (same SSE stages, same
   persisted two-table result, same scoring). Setting `app.ai.provider=openai` (or leaving it unset) preserves
   today's OpenAI behavior exactly.
5. `AiRiskAssessmentOrchestrator`'s source contains no reference to any provider-specific Spring AI configuration
   key; the active model name traceable in its log line is supplied by the active `RiskAssessmentAiClient`.
6. `local-environment/wiremock/README.md` (or an equivalent doc) documents, with concrete steps, how to run the
   offline demo and the record-mode workflow against Anthropic, not just OpenAI.
7. Running the offline demo, the `MODEL_CALL` SSE stage is visibly active (spinner shown) for a perceptible,
   consistent duration before `COMPLETE` — not instantaneous — while the overall assessment still completes well
   within the configured timeouts.
8. The `risk` backend package's top-level directory contains no more top-level classes than the newly-proposed
   sub-package scheme's own grouping classes (i.e. persistence/engine/api concerns are no longer flatly mixed at
   the top level); `./gradlew check` (ArchUnit + all existing tests) passes unchanged in outcome after the move.

## Testing Scope

Frontend: a test that the transaction-detail expanded row renders both buttons without the old section
heading/inline table; the new history-view component/route renders a flat (non-expandable) table scoped to
`customerId` only, including a Transaction ID column, mirroring the existing
`risk-assessment-history-table.component.spec.ts` filter/pagination test patterns; a routing test that the new
button navigates to the new view. Backend: a test that `app.ai.provider=anthropic` selects the Anthropic
`RiskAssessmentAiClient` bean (and `openai`/unset selects the OpenAI one); a test/assertion that
`AiRiskAssessmentOrchestrator`'s log line reflects whichever provider/model is active; existing
`AiRiskAssessmentWireMockReplayTest`-style coverage extended or duplicated for the Anthropic path if a real
Anthropic starter is added; all existing `risk` package tests continue to pass after the package-move (import/path
updates only, no behavior change expected).

## Risks / Open Questions

- **Anthropic starter/BOM availability** — `/plan-phase` must confirm `spring-ai-starter-model-anthropic` (or
  equivalent) actually resolves at the pinned `spring-ai-bom` version before committing to it; if it doesn't, this
  phase's Anthropic scope narrows to documenting the mechanism with OpenAI as the only proven implementation, and
  should say so explicitly rather than shipping something unverified.
- **Anthropic `base-url` override support** — needed for WireMock offline/record-mode reuse; unconfirmed as of this
  writing (OpenAI's SDK-level `base-url` semantics, including the `/v1` suffix quirk documented in
  `application-local.yml`, may not transfer identically to Anthropic's client) — `/plan-phase` should verify and
  document any difference.
- **Two providers, one WireMock instance:** whether to ship two always-present stub mappings (one per provider's
  request path) so switching `app.ai.provider` never requires touching WireMock config, or one active mapping the
  docs say to swap — `/plan-phase` should decide, favoring the always-both option if it's not meaningfully more
  complex (keeps the offline demo provider-switch truly zero-friction).
- **New view's placement:** a third `mat-tab-nav-bar` tab ("Risk Assessments", alongside today's
  Transactions/Analytics) is the most consistent option with the existing UX pattern and is recommended, but
  `/plan-phase`/UX judgment should confirm or choose a different navigation surface (e.g. a plain routed page
  reached only via the button, not a persistent tab).
- **Provider-selection mechanism:** `@ConditionalOnProperty`-style bean selection vs. a small factory/strategy
  bean choosing among injected `List<RiskAssessmentAiClient>` — left to `/plan-phase`, either is consistent with
  D18's existing interface seam.
- Whether this phase's provider-selection mechanism and Anthropic addition warrant a new `docs/DECISIONS.md` entry
  (superseding/extending D18) — very likely yes, left for `/plan-phase` to draft per the established
  documentation-reconciliation precedent (e.g. `PHASE_3_PLAN.md`'s D15/D16 additions).
