# Phase 5 EXT_2 — Refining AI Features

**Status:** NOT_STARTED
**Depends on:** `PHASE_5_EXT.md` (`COMPLETE`, frozen — not reopened). Enforce input guardrail on AI usage to protect PII
and out of scope querying. Display the risk_assessments entries in the assessment history table as a detailed view of a
risk_final_assessments entity.

## Objective

Make sure that the PII are not disclosed to the LLM and that the queries sent are consistent with the application intent
and scope. Enhance the risk assessment history to visualize as details of a final assessment the list of matched rules
and their contributing score to the final score. Make the Risk Level Mapping (LOW, MEDIUM, HIGH) configurable with
thresholds set on the backend. Drop the risk_level from risk_final_assessments, and compute it just for UI displaying
and highlight with proper coloring (light yellow, light orange, light red).

## Scope

- **In:**
  - A runtime PII guardrail: a config-driven pattern-matching safety net that scans the fully-assembled LLM prompt
    (after RAG injection of risk rules and history, immediately before the model call) for PII-shaped content (card
    PAN, IBAN, email address, crypto wallet address), as a second line of defense behind the existing
    `PromptContextMapper` build-time allow-list (Phase 4). On a match: abort before any LLM call, emit a `FAILED` SSE
    event with a generic message, log the violated pattern *names* at `WARN` (never the matched value itself), and
    persist no assessment row. Patterns are externalized as configuration (`@ConfigurationProperties`, fail-fast
    validated at startup, mirroring the existing `AnalyticsRangeProperties`/`RiskAssessmentProperties` idiom) so a new
    pattern is a config change, not a code change.
  - A new `GUARDRAIL_CHECK` SSE stage, emitted between `HISTORY_RETRIEVAL` and `MODEL_CALL`, so the operator sees the
    check happening live, same as every other stage.
  - Drop the persisted `risk_level` column from `risk_final_assessments` (Flyway migration) and recompute it on every
    read path from the persisted `risk_score` via the existing configurable thresholds
    (`RiskAssessmentProperties.levelThresholds`, already introduced in Phase 4/4 EXT) instead of trusting a value
    frozen at insert time. This covers: the SSE completion payload, the assessment-history list DTO, the RAG
    history-context block injected into future prompts, and the history endpoint's `riskLevel` filter (translated to
    a `risk_score` range predicate at query time using the same thresholds, since the column filtered on no longer
    exists).
  - Frontend: an expand-to-detail row on the risk-assessment-history table (reusing the transaction table's existing
    `multiTemplateDataRows` inline-expand pattern, D14) revealing, per assessment, the list of fired risk rules and
    each one's `scoreContribution`, sorted by contribution descending. The backend already returns this data
    end-to-end (`AiRiskAssessmentDto.ruleContributions`) — it is simply never rendered today.
  - Frontend: the same rule-contributions breakdown rendered in the live "Run AI Risk Assessment" trigger result
    panel, via one shared presentational component used in both places, so a freshly-completed assessment and a
    historical one look consistent.
  - Frontend: extract the currently-duplicated risk-level chip styling (today hardcoded and duplicated verbatim in
    `risk-assessment-history-table.component.scss` and `risk-assessment-trigger.component.scss`, using green for
    LOW) into one shared risk-level badge component, and change the palette to light yellow (LOW), light orange
    (MEDIUM, close to today's), light red (HIGH, close to today's).
  - `docs/DECISIONS.md` D23 (new) recording the `risk_level` persistence change, and a corresponding data-model note
    in `docs/specs/PROJECT_SPECIFICATION.md` marking `risk_level` as derived/not persisted — required because the
    current spec and D6 record it as a stored column; per `CLAUDE.md`'s precedence rules this can only be changed via
    an explicit, recorded decision, not silently.
- **Out:**
  - A scope/intent classifier for "out of scope querying." There is currently zero free-text user input anywhere near
    the AI risk-assessment flow — triggering an assessment is a pure button click on a `transactionId`, and the user
    prompt template's only variables are system-derived (`transactionContext`, `rules`, `history`). Building a
    generic scope-guardrail now would defend a surface that doesn't exist yet. Recorded as an open question below,
    not silently dropped from the original ask.
  - Any change to the RAG retrieval mechanism itself (D17 stands: structured DB filtering, not vector search).
  - Any change to how `risk_score` itself is computed (fired-rule weight × match strength, D6) — only the persistence
    and recomputation of its derived LOW/MEDIUM/HIGH categorization changes.
  - Any new REST endpoint or change to an existing endpoint's response shape — `AiRiskAssessmentDto.riskLevel` stays
    exactly where it is in the contract; it is simply computed per-response instead of column-sourced.
  - Any change to risk-rule CRUD, OAuth2/OIDC login, or the Administration section (Phase 5 / Phase 5 EXT territory,
    unaffected here).

## Functional Requirements

| Functionality                       | Description                                                                                                                                                        |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AI input guardrail                  | Before every model call, the fully-assembled prompt is scanned against a config-driven set of PII patterns (card PAN, IBAN, email, crypto wallet address). A match aborts the assessment (`FAILED`, generic message, `WARN` log of the pattern name only) before any LLM call is made or any row is persisted. Adding/editing a pattern is a configuration change. |
| Guardrail progress visibility        | The SSE stream emits a `GUARDRAIL_CHECK` stage between `HISTORY_RETRIEVAL` and `MODEL_CALL`, visible in the live progress UI like every other stage.               |
| Dynamic risk-level computation       | `risk_level` is never persisted; every read path (SSE completion, history list, RAG history-context, history filter) derives it from the persisted `risk_score` and the currently-configured thresholds, so a thresholds change is reflected retroactively across all history. |
| Assessment history drill-down        | Each row in the assessment-history table expands (click-to-expand, mirroring the transaction table's inline-detail pattern) to reveal the fired rules and each one's `scoreContribution`, sorted descending. |
| Consistent live-result drill-down    | The live "Run AI Risk Assessment" result panel renders the same rule-contributions breakdown as the history table, via a shared component.                          |
| Re-colored risk-level badge          | LOW/MEDIUM/HIGH render via one shared badge component with a light-yellow/light-orange/light-red palette, replacing today's duplicated, green-for-LOW styling.      |

## API Additions (base path `/api/v1`)

None. This phase changes internal computation and persistence only — every existing endpoint's request/response
shape (`GET .../ai-assessments/stream`, `GET .../ai-assessments`) is unchanged; `riskLevel` remains a field on
`AiRiskAssessmentDto`, now computed per-response instead of column-sourced. The `riskLevel` query filter on
`GET .../ai-assessments` keeps its existing name and semantics; only its internal translation to a SQL predicate
changes (range-on-`risk_score` instead of equality-on-`risk_level`).

## Acceptance Criteria

1. A prompt containing a seeded PII-shaped value (card PAN, IBAN, email, or crypto wallet address pattern) is blocked
   before the AI client is ever invoked; the SSE stream emits `FAILED` with a generic message; the backend log
   contains a `WARN` line naming the violated pattern, never the matched value; no `risk_final_assessments` row is
   written for the blocked run.
2. A clean, real-seed-data prompt is unaffected by the guardrail (no false positive) — verified against the existing
   seeded rules/transactions/history fixtures.
3. The SSE stream for a successful run includes a `GUARDRAIL_CHECK` stage between `HISTORY_RETRIEVAL` and
   `MODEL_CALL`.
4. `risk_final_assessments` has no `risk_level` column after the new migration runs; the entity, repository queries,
   and every consumer compile and pass without it.
5. Changing `app.risk.level-thresholds` and re-reading an already-persisted `risk_score` (via the history endpoint or
   the RAG history-context renderer) reflects the new thresholds immediately — proving the level is computed live,
   not frozen at insert time.
6. `GET .../ai-assessments?riskLevel=HIGH` (and `LOW`/`MEDIUM`) still returns exactly the rows whose current
   `risk_score` falls in that level's configured range, combinable with the endpoint's other existing filters.
7. The assessment-history table's expanded row and the live trigger result panel both render the fired-rules list
   with `ruleName` and `scoreContribution`, sorted descending by contribution.
8. LOW/MEDIUM/HIGH render with the light-yellow/light-orange/light-red palette consistently in both the history table
   and the trigger result panel, via the same shared component (no duplicated chip CSS remains in either
   `.scss` file).
9. `./gradlew check` and `npm test` pass, including new/updated coverage for all of the above.

## Testing Scope

Backend: a guardrail service unit test covering pattern hits (one per PII category) and misses; a config-properties
test asserting fail-fast startup validation (invalid/empty pattern configuration); an orchestrator test covering the
block-before-model-call path (asserts the AI client is never invoked and no row is persisted); a regression test
proving that two reads of the same persisted `risk_score` under different `app.risk.level-thresholds` values produce
different computed `riskLevel` results; updated specification/history-service tests covering the `riskLevel` filter's
translation to a `risk_score` range predicate, including combination with other existing filters.

Frontend: `risk-assessment-history-table.component.spec.ts` covers expand/collapse and fired-rules rendering; a new
spec for the shared rule-contributions component; `risk-assessment-trigger.component.spec.ts` covers the same
breakdown rendering on `COMPLETE`; a new spec for the shared risk-level badge component's colour-per-level mapping.

## Risks / Open Questions

- **"Out of scope querying" has no current attack surface.** The original ask names both PII disclosure and
  out-of-scope querying, but research confirmed there is no free-text user input anywhere near the AI risk-assessment
  flow today — assessments are triggered by a button click on a `transactionId`, and the prompt template's variables
  are all system-derived. Building a scope/intent classifier now would defend against a surface that doesn't exist,
  which is why this phase scopes that half out (see Scope → Out) rather than building it speculatively. Revisit this
  if/when a free-text AI-facing feature (e.g. an "ask a question about this customer" affordance) is ever added.
- **Dropping `risk_level` changes historical display retroactively when thresholds change — by design.** Once
  `risk_level` is no longer persisted, an operator who tightens or loosens `app.risk.level-thresholds` will see every
  past assessment's displayed level shift accordingly on next read, not just newly-computed ones. This is the
  intended behavior (a single, current source of truth for the score→level mapping, per the objective), not a defect
  — but it is a real, user-visible consequence worth being explicit about, and is the reason this required a new
  recorded decision (`docs/DECISIONS.md` D23) rather than a silent implementation change, since it revises what
  `docs/specs/PROJECT_SPECIFICATION.md` and D6 currently describe as a stored column.
