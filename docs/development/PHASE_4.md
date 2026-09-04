# Phase 4 — Risk Assessment Features

**Status:** COMPLETE
**Depends on:** Phase 2 (transactions), Phase 3 (customer analytics context); Phase 1 for the WireMock/AI local stack.

## Objective

Let an operator trigger an AI risk assessment for a single transaction, watch live progress over SSE, and review a
persisted per-transaction assessment history — with the LLM stubbable for offline demo.

## Scope

- **In:** the SSE stream + history endpoints, the two-table persisted result model, RAG over risk rules + prior
  assessments, PII-safe prompt construction, configurable provider/model, WireMock record-and-replay.
- **Out:** authentication/authorization (Phase 5), risk-rule CRUD (Phase 5).
- **Assumptions:**
    1. Transactions and customers are read-only and seeded for the demo.
    2. Assessment runs on a single transaction, using Risk Rules (and prior assessments) as the RAG knowledge source.
    3. AI calls are simulated via WireMock replaying recorded sessions, so the demo runs offline.
- Key decisions: `docs/DECISIONS.md` D3 (SSE), D4 (WireMock stubbing), D6 (two-table model).

## Requirements (refs into `PROJECT_SPECIFICATION.md`)

- Features **4–7** and **9** (trigger assessment with live updates; backend AI with configurable provider/model,
  code-managed prompts, RAG over risk rules; risk level from rule weight × match strength; persisted results;
  per-transaction history; RAG augmentation).
- Data model: `risk_rules`, `risk_assessments` (line items), `risk_final_assessments` (aggregate).

## Functional Requirements

| Functionality                  | Description                                                                                                                                                                                                                                                                                                             |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AI Risk Assessment             | Triggered by a button next to a transaction; shows a top-level card with live progress from an SSE stream of stages (e.g. PROMPT_BUILDING, RULE_RETRIEVAL, HISTORY_RETRIEVAL, MODEL_CALL, COMPLETE, FAILED), then the final content: risk level, findings summary, and recommendations.                                 |
| AI Risk Assessment Computation | A system prompt defines guidelines and expected outputs. A user prompt injects assessment-relevant transaction context with all PII (Personally Identifiable Information) omitted; risk rules and assessment history serve as RAG sources. The risk-level score weights match strength against the fired rule's weight. |
| AI Risk Assessment History     | Assessments are persisted and made available both to the operator and to the assessment process as a RAG knowledge source.                                                                                                                                                                                              |
| AI Interactions Stubbing       | A development feature flag records the session so recorded data can generate WireMock stubs for offline demo.                                                                                                                                                                                                           |

## Phase-specific Non-Functional Requirements

(These are in addition to the global NFRs in `CLAUDE.md`.)

| Requirement                | Description                                                                                                                                                                                                              |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Security & Data Protection | No PII, sensitive data, configuration, credentials, or attacker-usable detail is disclosed to the AI.                                                                                                                    |
| Reliability & Resilience   | SSE is bounded and cleans up resources after a timeout. If the operator disconnects mid-assessment, the assessment continues and is persisted even though the SSE ends. SSE and assessment timeouts are kept consistent. |
| Observability              | Each run is traceable end-to-end (prompt version, model, provider, rules used, RAG sources retrieved) — for debugging and for regulation.                                                                                |
| Configurability            | AI provider, model name, and prompt templates are externalized as configuration/code artifacts, never hardcoded.                                                                                                         |

## High-level APIs — Base Path `/api/v1`

| Method  | Endpoint Path                                   | Description                                                                                                                    | Access Level | Request Query / Body                                                                    | Response Payload                                                           |
|---------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| **GET** | `/customers/{customerId}/ai-assessments/stream` | Opens an SSE stream returning typed `AiRiskAssessmentEventDto` JSON objects representing progress tokens and the final summary | Operator     | `?transactionId={uuid}`                                                                 | `200 OK`: `text/event-stream`<br/>Data payload: `AiRiskAssessmentEventDto` |
| **GET** | `/customers/{customerId}/ai-assessments`        | Retrieves paginated history of past persisted AI risk assessments for the customer                                             | Operator     | `?transactionId={uuid}&page=0&size=10&sort=createdAt,desc` *(optional `transactionId`)* | `200 OK`: `Page<AiRiskAssessmentDto>`                                      |

## Acceptance Criteria

1. Assessment streams progress over SSE and, on completion, persists to two tables — `risk_final_assessments`
   (aggregate: risk level, findings, recommendations) and `risk_assessments` (line items: which rules fired, each with
   a `score_contribution` = fired rule weight × relevance in `[0.00, 1.00]`).
2. A configuration bounds the maximum number of risk rules activated, prioritized by relevance/applicability.
3. The LLM integration is fully configurable and supports an offline mode where WireMock stubs the LLM; a dev
   feature flag records real interactions to build the replay data.
4. WireMock is added to Docker Compose with its own folder in the local-development setup, acting as the LLM for
   offline demo.
5. Frontend: a paginated, per-column-filterable table of past assessments per transaction; live processing shows the
   backend/LLM steps, then the events disappear leaving only the final result.

## Testing Scope

Backend: SSE stage sequencing and timeout/disconnect resilience (assessment persists after disconnect); PII-scrubbing
of prompt context; score computation (weight × match strength); max-rules cap; WireMock replay path. Frontend:
progress-then-final-result behavior and history table filtering.

## Risks / Open Questions

- SSE lifecycle vs. background assessment persistence on operator disconnect — verify the two are decoupled.
- Guaranteeing no PII leaks into prompts (assert via tests).
- Recording/replay fidelity between real provider responses and WireMock stubs.
