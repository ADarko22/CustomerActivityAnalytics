# Phase 4 Implementation Plan — Risk Assessment Features

**Status:** COMPLETE

Blueprint for `PHASE_4.md`. Adds the AI risk-assessment domain: a new `risk_rules` / `risk_final_assessments` /
`risk_assessments` schema, an SSE-streamed assessment pipeline (RAG over rules + history → LLM call → scoring →
persistence), a paginated per-transaction history endpoint, WireMock-based offline replay, and the corresponding
Angular UI. Read alongside `CLAUDE.md` (conventions), `docs/specs/PROJECT_SPECIFICATION.md` (Features 4–7, 9 / data
model), and `docs/DECISIONS.md` (D1, D3–D6, D9–D11, D13 all apply; this plan proposes new D17/D18 entries for the
RAG-retrieval interpretation and the single-AI-provider interpretation, respectively).

## Current State (verified)

- Backend has three domain packages (`customer`, `transaction`, `analytics`) following one consistent shape:
  `*Controller` (thin, `@RequestParam`/`@PathVariable` wiring only) → `*Service` (`customerService.requireExists(...)`
  guard, `ResponseStatusException` for 404/400, `slf4j` `INFO`/`DEBUG`/`WARN` logging) → Spring Data
  `JpaSpecificationExecutor` repositories + `*Specifications` predicate builders. No `risk`/`ai` package exists yet.
- `build.gradle.kts` already depends on `spring-ai-starter-model-openai` (BOM `springAi = 2.0.1` in
  `gradle/libs.versions.toml`) and `application.yml` already has a placeholder `spring.ai.openai.api-key` — added in
  Phase 1 specifically "in preparation" for this phase, per its own inline comment. No `spring.ai.openai.base-url` or
  `chat.options.model` is configured yet; both are added by this plan.
- `SecurityConfig` (D13) permits all requests; the new endpoints are unauthenticated through Phase 5, same as every
  existing endpoint.
- Flyway is at `V2__customer_transaction_schema.sql`; this plan adds `V3__risk_assessment_schema.sql`. The seed script
  (`db/seed/R__seed_demo_data.sql`, a repeatable migration) is extended, not replaced, with `risk_rules` demo rows.
- `local-environment/docker-compose.yml` provisions only `postgres`; `keycloak/` and `wiremock/` are empty
  placeholder folders (`.gitkeep` only) reserved by the project layout for Phases 4–5. This plan is the first to fill
  `wiremock/`.
- Frontend has one consistent component shape per feature folder (`*.component.ts/.html/.scss/.spec.ts`), a thin
  `core/services/*.service.ts` per backend resource (`HttpClient` + `HttpParams`, mirrors the resource's filter
  fields), and models under `core/models/*.model.ts`. `TransactionTableComponent`'s row expands inline to
  `TransactionDetailComponent` on click (D14) — the natural mounting point for a per-transaction "AI Risk Assessment"
  section. No SSE/`EventSource` usage exists yet; no chip/badge/progress-spinner Material modules are imported yet.
- `proxy.conf.json` proxies `/api` to `localhost:8080` for `ng serve`; relative-path `HttpClient`/`EventSource` calls
  already work for every existing feature and will work identically for the new SSE stream.

## Design clarifications (flagging for `/review PHASE_4 plan`, not silent contradictions)

1. **RAG retrieval is structured DB filtering, not vector/embedding search.** `PROJECT_SPECIFICATION.md` Feature 9 and
   `PHASE_4.md`'s Requirements line both call this "RAG," but `risk_rules` is a small, structured, operator-curated
   table (not a large unstructured corpus), and `PHASE_4.md`'s own Scope narrows the RAG sources for this phase to
   "risk rules + prior assessments" (Feature 9's broader "policies and regulations" sources are out of scope here —
   a phased narrowing of a spec feature, not an override of it). Given that, "RAG" is implemented as: (a) fetch
   candidate `risk_rules` filtered by `applies_to IN (activityType, 'ALL')`, (b) fetch the transaction's prior
   `risk_final_assessments` (+ line items), and (c) inject both, verbatim, into the user prompt as context — no
   embedding model, no vector store. This avoids a genuinely unnecessary abstraction (`CLAUDE.md` Coding Standard
   #3) for a rule set that will realistically hold dozens, not thousands, of rows. Recorded as a new durable decision
   (`docs/DECISIONS.md` D17), added at implement time per the `PHASE_2_PLAN.md`/`PHASE_3_PLAN.md` precedent.
2. **`threshold_logic` is natural-language, LLM-interpreted text, not executable code.** `PROJECT_SPECIFICATION.md`
   describes it only as "Rule condition" (`TEXT`). Since match strength is explicitly computed by the LLM ("The
   risk-level score weights match strength against the fired rule's weight" — `PHASE_4.md` Functional Requirements),
   `threshold_logic` is authored as a natural-language condition (e.g. *"Card-present transaction amount exceeds
   5,000 EUR equivalent at a merchant category code associated with high chargeback rates"*) that the system prompt
   instructs the model to evaluate against the provided (PII-scrubbed) transaction context — not a DSL parsed/executed
   by backend code. This keeps rule authoring in the DB (as the spec requires) while keeping the backend's job to
   arithmetic (`weight × relevance`) and persistence, not condition-evaluation logic.
3. **PII/sensitive-field scrubbing set, decided per activity type** (spec does not enumerate which fields count):
   - Always omitted from the LLM context: `customerId` (the assessment is transaction-scoped; the model never needs
     customer identity), `card_pan` (even though already masked at rest), `authorization_code`, `sender_account`,
     `receiver_account`, `wallet_address_from`, `wallet_address_to`, `tx_hash` (pseudonymous but attacker-usable /
     traceable to a real wallet — excluded under the NFR's "attacker-usable detail" clause, not just "PII").
   - Always included (transactional-pattern signals the assessment needs): `transactionId` (opaque UUID, needed for
     traceability/observability, not personally identifying), `activityType`, `amount`, `currency`, `status`,
     `createdAt`, and the remaining type-specific *categorical* fields — `cardType`, `merchantName`, `mccCode`,
     `cardPresent`, `declineReason` (CARD); `paymentMethod`, `receiverBankCountry` (PAYMENT); `blockchain`,
     `exchangeName` (CRYPTO).
   - A dedicated `PromptContextMapper` (not ad hoc string-building inside the orchestrator) owns this allow-list, unit
     tested per activity type to assert the excluded literals never appear in the rendered prompt string (Testing
     Scope: "PII-scrubbing of prompt context").
4. **Single AI-provider implementation behind a swappable interface, not literal multi-provider wiring.**
   `PROJECT_SPECIFICATION.md` Feature 5 asks for "a configurable AI Provider, using a configurable model." Adding a
   second real provider SDK (e.g. Anthropic) with no test coverage benefit (WireMock stubs the OpenAI-shaped HTTP
   contract regardless of which "provider" label is configured) would be scope beyond what any acceptance criterion
   exercises. This plan implements one concrete `OpenAiRiskAssessmentAiClient` (Spring AI `ChatClient` over the
   `spring-ai-starter-model-openai` already on the classpath) behind a `RiskAssessmentAiClient` interface, with
   `spring.ai.openai.chat.options.model` and a new `app.ai.provider` label externalized as configuration (never
   hardcoded, satisfying the phase's "Configurability" NFR and Feature 5's literal ask) — swapping in a second
   provider later is an interface implementation, not a rewrite. This is a choice adopted deliberately and not
   mandated by the PDF (which only says "configurable"), so — like D1's React→Angular substitution and D4's
   WireMock-stubbing choice — it is recorded as a new durable decision (`docs/DECISIONS.md` D18), added at implement
   time alongside D17.
5. **WireMock record-mode is a local-environment/docs deliverable, not new Java code.** `PHASE_4.md` AC3: "a dev
   feature flag records real interactions to build the replay data." Building a custom record/replay proxy in the
   backend would duplicate WireMock's own well-tested `--record-mappings --proxy-all=<target>` feature. This plan
   sets `spring.ai.openai.base-url` (env-overridable) to point at the `wiremock` Docker Compose service by default in
   the local profile; a `WIREMOCK_RECORD_MODE` Compose env var switches WireMock itself between "serve canned
   stubs" (offline default) and "proxy to the real OpenAI API + record the exchange as a new stub" (dev-only,
   requires a real `OPENAI_API_KEY`). The backend additionally exposes `app.ai.record-mode` (default `false`) purely
   as an observability/startup-log flag — logged once at boot — so the flag's state is visible from the app side too,
   satisfying "a dev feature flag" literally without re-implementing WireMock.
6. **History endpoint's per-column filters are backend query params**, mirroring `TransactionController`'s established
   convention, even though `PHASE_4.md`'s API table only lists `transactionId`/`page`/`size`/`sort`. AC5 asks for a
   "paginated, per-column-filterable table," and pagination + per-column filtering only compose correctly when
   filtering happens server-side (same gap-filling pattern as `PHASE_2_PLAN.md` Clarification #3 /
   `PHASE_3_PLAN.md` Clarification #1 — the phase doc's API table under-specifies params its own AC requires). Adds
   `riskLevel`, `from`/`to` (on `triggeredAt`), `minScore`/`maxScore` as optional query params, built via a
   dedicated `RiskFinalAssessmentSpecifications` predicate builder (§ Domain packages) rather than inline
   `Predicate` construction in the service — the same separation of concerns `TransactionSpecifications`/
   `CardActivitySpecifications`/etc. already establish for every other filterable resource.
7. **`transactionId` stays optional on the history endpoint** (per the phase doc's own `(optional transactionId)`
   annotation) even though the frontend's only consumer always supplies it (AC5 is phrased "per transaction"). The
   endpoint is intentionally more general than its one current UI consumer — no contradiction, just unused generality
   the phase doc itself asks for.
8. **Max-triggered-rules cap applies on the output side**, after the model returns its rule/relevance matches: sort by
   `relevance` descending, keep the configured top-N (default 5), recompute `riskScore` only from the retained
   rows. This matches AC2's wording ("bounds the maximum number of risk rules **activated**") — activation is an
   outcome of the model call, not a pre-filter on which rules are *offered* as RAG context (all applicable rules are
   still shown to the model so it can reason about the full rule set before ranking).
9. **SSE resilience: emitter failures never interrupt the pipeline.** Every `emitter.send(...)` call is wrapped so
   that an `IOException` (client disconnected) is caught and logged at `DEBUG`, and the orchestration coroutine keeps
   running — RAG retrieval, the model call, scoring, and the two-table persistence happen unconditionally once
   started. This directly satisfies the NFR ("assessment continues and persists ... even though the SSE ends") and is
   verified by a unit test that fails a fake emitter mid-stream and asserts the DB rows are still written.
10. **`FAILED` stage does not persist a partial result** — if the model call errors or times out, the pipeline emits
    `FAILED` (a generic, non-internal-detail message, per the Security NFR) and returns without writing to
    `risk_final_assessments`/`risk_assessments`. The operator can simply retrigger the assessment; there is no
    acceptance criterion asking for failed-attempt history.

## Backend Design

### Schema (`db/migration/V3__risk_assessment_schema.sql`)

```sql
CREATE TABLE risk_rules (
    rule_id         UUID PRIMARY KEY,
    rule_name       VARCHAR(255) NOT NULL,
    applies_to      VARCHAR(20) NOT NULL CHECK (applies_to IN ('CARD', 'PAYMENT', 'CRYPTO', 'ALL')),
    threshold_logic TEXT NOT NULL,
    weight          DECIMAL(5, 2) NOT NULL
);

CREATE TABLE risk_final_assessments (
    assessment_id   UUID PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES transactions (transaction_id),
    triggered_at    TIMESTAMP NOT NULL,
    risk_level      VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    risk_score      DECIMAL(10, 2) NOT NULL,
    findings        TEXT NOT NULL,
    recommendations TEXT NOT NULL
);

CREATE INDEX idx_risk_final_assessments_transaction_id ON risk_final_assessments (transaction_id);
CREATE INDEX idx_risk_final_assessments_triggered_at ON risk_final_assessments (triggered_at);

CREATE TABLE risk_assessments (
    assessment_id       UUID NOT NULL REFERENCES risk_final_assessments (assessment_id),
    rule_id             UUID NOT NULL REFERENCES risk_rules (rule_id),
    transaction_id      UUID NOT NULL REFERENCES transactions (transaction_id),
    triggered_at        TIMESTAMP NOT NULL,
    score_contribution  DECIMAL(5, 2) NOT NULL CHECK (score_contribution >= 0),
    PRIMARY KEY (assessment_id, rule_id)
);

CREATE INDEX idx_risk_assessments_transaction_id ON risk_assessments (transaction_id);
```

Seed (`db/seed/R__seed_demo_data.sql`, additive): 7–8 `risk_rules` rows spanning `ALL`/`CARD`/`PAYMENT`/`CRYPTO`
(e.g. "High-value transaction," "Card-not-present at high-chargeback MCC," "Cross-border payment to a
non-cooperative jurisdiction," "Rapid succession of crypto transfers to a new wallet"), each with a natural-language
`threshold_logic` and a `weight` between 10–40, so a triggered assessment has real rules to match against and the
history table has something to render before any live demo run. Since this file is a *repeatable* Flyway migration
that reruns whenever its checksum changes, the new `INSERT INTO risk_rules` block follows the file's existing
delete-then-insert idempotency convention — a `DELETE FROM risk_rules;` is added alongside the file's existing
`DELETE FROM card_activity; / ... / DELETE FROM customers;` preamble (ordered before the inserts, no FK-ordering
conflict since the seed script does not insert `risk_final_assessments`/`risk_assessments` rows).

### Domain packages (`risk/`)

```
risk/
  RuleScope.java                     enum CARD, PAYMENT, CRYPTO, ALL (+ matches(ActivityType))
  RiskLevel.java                     enum LOW, MEDIUM, HIGH
  RiskRule.java                      @Entity → risk_rules
  RiskRuleRepository.java            JpaRepository; findByAppliesToIn(Collection<RuleScope>)
  RiskFinalAssessment.java           @Entity → risk_final_assessments
  RiskFinalAssessmentRepository.java JpaRepository + JpaSpecificationExecutor (history filters)
  RiskFinalAssessmentSpecifications.java  predicate builder for riskLevel/from/to/minScore/maxScore
                                      (transactionId, customerId-via-join) — same shape as
                                      TransactionSpecifications, used by the history endpoint
  RiskAssessmentLineItem.java        @Entity → risk_assessments, @EmbeddedId
  RiskAssessmentLineItemId.java      @Embeddable (assessmentId, ruleId)
  RiskAssessmentLineItemRepository.java  JpaRepository; findByAssessmentIdInWithRuleName(...)
  RiskAssessmentProperties.java      @ConfigurationProperties("app.risk") record
  RiskRuleRetrievalService.java      RAG source #1 — applicable rules for an activityType
  AssessmentHistoryRetrievalService.java  RAG source #2 — prior final assessments for a transaction
  RiskScoringService.java            weight × relevance, max-rules cap, score → RiskLevel mapping
  PromptContextMapper.java           TransactionDto → PII-scrubbed prompt context (Clarification #3)
  AiRiskAssessmentOrchestrator.java  the SSE-driving pipeline (stages, persistence, resilience)
  AiRiskAssessmentController.java    GET .../stream, GET .../ai-assessments
  ai/
    RiskAssessmentAiClient.java        interface: assess(context, rules, history) -> ModelAssessmentResult
    OpenAiRiskAssessmentAiClient.java  Spring AI ChatClient implementation
    ModelAssessmentResult.java         record: List<RuleMatch>, findings, recommendations
    RuleMatch.java                     record: UUID ruleId, BigDecimal relevance
  dto/
    AssessmentStage.java               enum PROMPT_BUILDING, RULE_RETRIEVAL, HISTORY_RETRIEVAL,
                                        MODEL_CALL, COMPLETE, FAILED
    AiRiskAssessmentEventDto.java      record: stage, message, AiRiskAssessmentDto result (COMPLETE only)
    AiRiskAssessmentDto.java           record: assessmentId, transactionId, triggeredAt, riskLevel,
                                        riskScore, findings, recommendations, List<RuleContributionDto>
    RuleContributionDto.java           record: ruleId, ruleName, scoreContribution
```

`RuleScope` lives in `risk`, not `transaction`, because `ALL` has no meaning for an actual transaction's
`ActivityType` — keeping the two enums separate avoids polluting the transaction domain with a risk-only concept;
`RuleScope.matches(ActivityType)` is the only coupling point.

### Prompts (`resources/prompts/`)

`risk-assessment-system.st` and `risk-assessment-user.st` — Spring AI `PromptTemplate` resources, not inline Java
strings, so "prompt engineering managed as code" (Feature 5) is reviewable/diffable independently of orchestration
logic. A `PROMPT_VERSION = "v1"` constant (bumped on template changes) is attached to every log line and every
`ModelAssessmentResult` for traceability (Observability NFR). System prompt defines: output JSON shape (rule
matches with `ruleId`/`relevance`, `findings`, `recommendations`), the `[0.00, 1.00]` relevance bound, and an
explicit instruction to only cite rule IDs from the supplied rule list. User prompt template renders the
`PromptContextMapper` output, the candidate rules (id, name, `applies_to`, `threshold_logic`, `weight`), and up to
5 most-recent prior `risk_final_assessments` for the same transaction (risk level + score + findings, no PII — those
were already scrubbed when the original assessment was built, so nothing new to scrub here).

### `RiskAssessmentProperties` (`app.risk.*`, mirrors `AnalyticsRangeProperties`'s pattern)

```yaml
app:
  ai:
    provider: openai          # Clarification #4 — observability label
    record-mode: false        # Clarification #5 — logged at boot only
  risk:
    max-triggered-rules: 5
    assessment-timeout: 45s
    sse-timeout: 50s          # > assessment-timeout, so COMPLETE/FAILED always has time to land
    level-thresholds:
      low-max: 30             # score <= 30  -> LOW
      medium-max: 70          # 30 < score <= 70 -> MEDIUM; above -> HIGH
    history-context-size: 5   # prior assessments injected as RAG context
```

`@PostConstruct` validation (same style as `AnalyticsRangeProperties`) rejects `medium-max <= low-max` and a
`sse-timeout <= assessment-timeout` at startup — fail fast, not at first request (Clarification #9's "SSE and
assessment timeouts are kept consistent" NFR, enforced structurally).

### `RiskScoringService`

```java
score(List<RuleMatch> matches, Map<UUID, RiskRule> rulesById, RiskAssessmentProperties props)
    -> ScoredAssessment(RiskLevel level, BigDecimal totalScore, List<ScoredRule> retained)
```

1. Validate each `relevance` is within `[0.00, 1.00]` (clamp + `WARN`-log if the model returns out-of-range values —
   defensive against a misbehaving stub/model, not a thrown error, since a demo shouldn't hard-fail on this).
2. `scoreContribution = rule.weight() * relevance`, per matched rule.
3. Sort by `relevance` descending, keep top `max-triggered-rules` (Clarification #8).
4. `totalScore = Σ retained.scoreContribution`; map to `RiskLevel` via `level-thresholds`.

Pure, DB-free, exhaustively unit-testable (mirrors `GranularityTest`'s boundary-case style).

### `AiRiskAssessmentOrchestrator` — the SSE pipeline

Runs on a dedicated `@Async("riskAssessmentExecutor")` method (new `config/RiskAssessmentAsyncConfig.java`,
`@EnableAsync` + a bounded `ThreadPoolTaskExecutor`, isolated from Boot's default task executor so a burst of
assessment requests can't starve other `@Async` usage). Controller creates the `SseEmitter`, kicks off the async
method, and returns immediately — the method owns the emitter's lifecycle from there.

```
emitSafely(emitter, PROMPT_BUILDING)
context = promptContextMapper.map(transaction)                 // Clarification #3
emitSafely(emitter, RULE_RETRIEVAL)
rules = riskRuleRetrievalService.findApplicable(activityType)  // RAG source #1
emitSafely(emitter, HISTORY_RETRIEVAL)
history = assessmentHistoryRetrievalService.recentFor(transactionId, historyContextSize)  // RAG source #2
emitSafely(emitter, MODEL_CALL)
try {
  result = aiClient.assess(context, rules, history)  // bounded by assessment-timeout
  scored = riskScoringService.score(result.matches(), rulesById, props)
  persisted = persist(transactionId, scored, result.findings(), result.recommendations())  // @Transactional
  emitSafely(emitter, COMPLETE, toDto(persisted))
  emitter.complete()
} catch (Exception e) {
  log.warn("Assessment failed: transactionId={}, stage=MODEL_CALL", transactionId, e)  // full detail server-side only
  emitSafely(emitter, FAILED, "Assessment could not be completed. Please retry.")       // generic, per Security NFR
  emitter.complete()
}
```

`emitSafely` catches/logs `IOException` from `emitter.send(...)` at `DEBUG` and returns normally — the `try/catch`
never wraps the RAG/model-call/persistence steps, so a disconnect never short-circuits them (Clarification #9).
`emitter.onTimeout`/`onError` are registered only for cleanup logging, not cancellation.

### `AiRiskAssessmentController`

```java
GET /api/v1/customers/{customerId}/ai-assessments/stream?transactionId={uuid}
  -> validates customer + transaction (customerService.requireExists, transaction lookup filtered by
     customerId, same 404 pattern as TransactionService.findDetail) BEFORE creating the emitter (fail fast,
     no dangling SSE connection on a bad ID); returns SseEmitter(props.sseTimeout())

GET /api/v1/customers/{customerId}/ai-assessments?transactionId=&riskLevel=&from=&to=&minScore=&maxScore=
    &page=0&size=10&sort=triggeredAt,desc
  -> Page<AiRiskAssessmentDto>, same customerService.requireExists + optional-transactionId-ownership-check
     pattern as TransactionController
```

### Observability (Global DoD + phase NFR)

One `INFO` line per assessment run logging `transactionId`, `promptVersion`, `provider`, `model`, matched-rule
count, retained-rule count, RAG source sizes (rules considered, history rows considered), and final `riskLevel` —
enough to answer "why did this transaction get this level" without a debugger, satisfying the Observability NFR
verbatim. `DEBUG` carries the full rule ID list and raw model relevance values (still no transaction PII, since the
prompt itself never contained any).

## Local environment

- `local-environment/wiremock/mappings/openai-chat-completions.json` — a stub matching
  `POST /v1/chat/completions` (any body — regex/`urlPattern` match on the path only, since prompt content varies
  per transaction) returning a canned OpenAI-shaped JSON response whose `content` is a JSON string matching
  `ModelAssessmentResult`'s shape, so the full pipeline (parse → score → persist) exercises real deserialization
  code even offline.
- `local-environment/wiremock/__files/` — the response body fixture(s) referenced by the mapping.
- `local-environment/docker-compose.yml` — new `wiremock` service (`wiremock/wiremock` image), port
  `${WIREMOCK_PORT:-8089}`, volumes mounting `./wiremock/mappings` and `./wiremock/__files`; command switches
  between normal stub-serving and `--record-mappings --proxy-all=https://api.openai.com` based on
  `WIREMOCK_RECORD_MODE` (Clarification #5).
- `backend/src/main/resources/application-local.yml` — adds `spring.ai.openai.base-url:
  http://localhost:${WIREMOCK_PORT:8089}` so the local profile talks to WireMock by default; the non-local
  `application.yml` leaves `base-url` unset (Spring AI's own default, i.e. the real OpenAI API) for whichever
  environment supplies a real `OPENAI_API_KEY`.
- `local-environment/wiremock/README.md` (or a section in the repo's top-level `README.md`) documenting the
  record-mode toggle procedure: set `WIREMOCK_RECORD_MODE=true` + a real `OPENAI_API_KEY`, run a live assessment
  through the UI, copy the newly recorded mapping from WireMock's admin API into `mappings/`, flip the flag back.

## Frontend Design

- **New Angular Material modules:** `MatChipsModule` (risk-level badge), `MatProgressSpinnerModule` (live stage
  indicator), reusing existing `MatCardModule`/`MatTableModule`/`MatPaginatorModule`/`MatSelectModule`/
  `MatDatepickerModule`/`MatButtonModule` for the history table (mirrors `transaction-table.component.ts`'s shape
  exactly).
- **Models** (`core/models/ai-risk-assessment.model.ts`):
  ```ts
  export type AssessmentStage =
    | 'PROMPT_BUILDING' | 'RULE_RETRIEVAL' | 'HISTORY_RETRIEVAL' | 'MODEL_CALL' | 'COMPLETE' | 'FAILED';
  export interface RuleContribution { ruleId: string; ruleName: string; scoreContribution: number; }
  export interface AiRiskAssessment {
    assessmentId: string; transactionId: string; triggeredAt: string;
    riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'; riskScore: number;
    findings: string; recommendations: string; ruleContributions: RuleContribution[];
  }
  export interface AiRiskAssessmentEvent { stage: AssessmentStage; message?: string; result?: AiRiskAssessment; }
  export interface AiRiskAssessmentFilter {
    riskLevel?: string; from?: string; to?: string; minScore?: number; maxScore?: number;
  }
  ```
- **Service** (`core/services/ai-risk-assessment.service.ts`):
  - `streamAssessment(customerId, transactionId): Observable<AiRiskAssessmentEvent>` — wraps a native `EventSource`
    in an `Observable` (open on subscribe, `onmessage` → `next(JSON.parse(...))`, `onerror` → `error(...)`,
    teardown closes the `EventSource`). The `EventSource` constructor is injected via a small factory function
    parameter (default `() => new EventSource(url)`) so `ai-risk-assessment.service.spec.ts` can substitute a fake
    implementing `onmessage`/`onerror`/`close` — the standard pattern for unit-testing browser-global streaming APIs
    without a real HTTP round trip.
  - `findHistory(customerId, transactionId, filter, page, size, sort): Observable<Page<AiRiskAssessment>>` — thin
    `HttpClient` wrapper, identical shape to `TransactionService.findOverview`.
- **Components** (`features/risk-assessment/`):
  - `risk-assessment-trigger/` — a button ("Run AI Risk Assessment") plus a live-progress card. On click, subscribes
    to `streamAssessment(...)`; renders one row per stage seen so far (checkmark for past stages, spinner for the
    current one) using a fixed, ordered stage list so out-of-order/duplicate events can't corrupt the display. On a
    `COMPLETE` event, the stage list is replaced by the final result card (risk-level `MatChip` colored by level,
    findings, recommendations, rule-contribution breakdown) — AC5's "events disappear leaving only the final
    result." On `FAILED`, the stage list is replaced by a compact inline error state with a "Retry" button
    (Clarification #10 — no persisted result to show, so this is the reasonable non-literal reading of "final
    result").
  - `risk-assessment-history-table/` — paginated, per-column-filterable table (mirrors
    `transaction-table.component.ts`/`transaction-table.columns.ts`: a `ColumnDef[]` for `triggeredAt` (date
    range), `riskLevel` (select), `riskScore` (amount-style min/max), `findings`/`recommendations` (text, truncated
    with a `matTooltip` for the full text), debounced 300 ms filter changes, `MatPaginatorModule`). Row click
    expands rule contributions inline (same `multiTemplateDataRows` pattern as `TransactionTableComponent`, D14).
  - Both are mounted inside `TransactionDetailComponent`'s template, under a new "AI Risk Assessment" section —
    the trigger card refreshes the history table's first page on `COMPLETE` (simple signal-based `(assessmentAdded)`
    output → parent calls a `reload()` method on the history table via `@ViewChild`), so a newly triggered
    assessment appears without a manual refresh.

## File inventory

**Backend — new:** `risk/{RuleScope,RiskLevel,RiskRule,RiskRuleRepository,RiskFinalAssessment,
RiskFinalAssessmentRepository,RiskFinalAssessmentSpecifications,RiskAssessmentLineItem,RiskAssessmentLineItemId,
RiskAssessmentLineItemRepository,RiskAssessmentProperties,RiskRuleRetrievalService,
AssessmentHistoryRetrievalService,RiskScoringService,PromptContextMapper,AiRiskAssessmentOrchestrator,
AiRiskAssessmentController}.java`;
`risk/ai/{RiskAssessmentAiClient,OpenAiRiskAssessmentAiClient,ModelAssessmentResult,RuleMatch}.java`;
`risk/dto/{AssessmentStage,AiRiskAssessmentEventDto,AiRiskAssessmentDto,RuleContributionDto}.java`;
`config/RiskAssessmentAsyncConfig.java`; `resources/prompts/{risk-assessment-system,risk-assessment-user}.st`;
`db/migration/V3__risk_assessment_schema.sql`.
Test: `risk/RiskScoringServiceTest.java` (weight×relevance arithmetic, level-threshold boundaries, max-rules cap
ordering), `risk/PromptContextMapperTest.java` (parametrized CARD/PAYMENT/CRYPTO — asserts excluded literals absent),
`risk/RiskRuleRetrievalServiceTest.java` (ALL + type-specific matching), `risk/RiskFinalAssessmentSpecificationsTest.java`
(`@DataJpaTest` — each filter predicate in isolation and combined: `riskLevel`, `from`/`to`, `minScore`/`maxScore`,
`transactionId`), `risk/AiRiskAssessmentOrchestratorTest.java` (Mockito — stage-sequencing order,
persistence-survives-emitter-`IOException`, `FAILED` path persists nothing, model-call timeout triggers `FAILED`
without persisting), `risk/AiRiskAssessmentControllerTest.java` (MockMvc async — 404s before streaming starts,
`text/event-stream` content type, `SseEmitter` timeout triggers `asyncDispatch`/`onTimeout` cleanup without
persisting a second time, history pagination/filter/sort wiring), `risk/AiRiskAssessmentRepositoryTest.java`
(`@DataJpaTest`/Testcontainers — composite-PK persistence, cascade-free FK integrity),
`risk/AiRiskAssessmentWireMockReplayTest.java` (`WireMockExtension` from a new `testImplementation` dependency,
`spring.ai.openai.base-url` pointed at the extension's port — full pipeline against a canned stub).

**Backend — modified:** `application.yml` (`app.ai.*`, `app.risk.*`, `spring.ai.openai.chat.options.model`),
`application-local.yml` (`spring.ai.openai.base-url` → local WireMock), `db/seed/R__seed_demo_data.sql`
(`risk_rules` rows), `build.gradle.kts` + `gradle/libs.versions.toml` (WireMock test dependency),
`CustomerActivityAnalyticsApplication.java` (`@EnableAsync` if not placed on the new config class instead).

**Frontend — new:** `core/models/ai-risk-assessment.model.ts`; `core/services/ai-risk-assessment.service.ts` (+
`.spec.ts`); `features/risk-assessment/{risk-assessment-trigger,risk-assessment-history-table}/*` (each with
`.ts/.html/.scss/.spec.ts`).

**Frontend — modified:** `features/transactions/transaction-detail/*` (mounts the new section).

**Local environment — new:** `local-environment/wiremock/mappings/openai-chat-completions.json`,
`local-environment/wiremock/__files/*.json`.

**Local environment — modified:** `local-environment/docker-compose.yml` (`wiremock` service).

**Documentation reconciliation (assigned as an `/implement`-time task, mirroring `PHASE_2_PLAN.md`/`PHASE_3_PLAN.md`
precedent):** `docs/DECISIONS.md` gains `D17` (structured-DB-filter RAG over vector search, Clarification #1) and
`D18` (single OpenAI-shaped `RiskAssessmentAiClient` implementation behind a swappable interface, standing in for
Feature 5's "configurable AI Provider," Clarification #4).

## Test plan → Acceptance-criteria mapping

| `PHASE_4.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — SSE progress + two-table persistence (`score_contribution = weight × relevance ∈ [0,1]`) | `RiskScoringServiceTest`, `AiRiskAssessmentOrchestratorTest` (stage order + persistence), `AiRiskAssessmentRepositoryTest` (composite PK, both tables written) | `risk-assessment-trigger.component.spec.ts` (stage list renders in order, then final result) |
| AC2 — configurable max-triggered-rules cap, prioritized by relevance | `RiskScoringServiceTest` (top-N by relevance, cap boundary) | — |
| AC3 — fully configurable LLM integration, offline WireMock mode, dev record flag | `AiRiskAssessmentWireMockReplayTest` (offline path), config-property tests (`RiskAssessmentProperties` `@PostConstruct` validation) | — |
| AC4 — WireMock in Docker Compose with its own local-environment folder | — (infra; verified manually — `docker compose up wiremock` + a live `/stream` call against it) | — |
| AC5 — paginated per-column-filterable history table; live steps then final-result-only | `AiRiskAssessmentControllerTest` (filter/sort/pagination query wiring) | `risk-assessment-history-table.component.spec.ts` (filter/pagination call params, mirrors `transaction-table.component.spec.ts`), `risk-assessment-trigger.component.spec.ts` (stage list disappears on `COMPLETE`/`FAILED`) |

Also covers Testing Scope items not tied to a single AC: PII-scrubbing (`PromptContextMapperTest`), SSE
timeout/disconnect resilience — two distinct timeouts per Clarification #9/§ `RiskAssessmentProperties`:
the *model-call* timeout (`AiRiskAssessmentOrchestratorTest`'s timeout-triggers-`FAILED`-without-persisting case)
and the *SSE connection* timeout (`AiRiskAssessmentControllerTest`'s `SseEmitter.onTimeout` cleanup case), plus
disconnect resilience (`AiRiskAssessmentOrchestratorTest`'s `IOException`-mid-stream case).
`ArchitectureTest`'s existing rules apply unchanged to the
new `risk`/`risk.ai`/`risk.dto` packages (controller/repository/persistence-API isolation) — no new ArchUnit rule
needed since the existing `noClasses().that().haveSimpleNameEndingWith("Controller")...` rules are package-agnostic.

## Risks / Open Questions (carried from `PHASE_4.md`, resolved or narrowed where possible)

- **SSE lifecycle vs. background persistence on disconnect** — resolved by Clarification #9's `emitSafely` design;
  verified by a dedicated orchestrator test rather than a flaky real-network integration test.
- **No PII leaks into prompts** — resolved by Clarification #3's explicit allow-list + parametrized test; the
  allow-list is a single reviewable location (`PromptContextMapper`), not scattered string concatenation.
- **Recording/replay fidelity between real provider responses and WireMock stubs** — mitigated, not eliminated: the
  WireMock stub's `content` field is real JSON deserialized by the same `ModelAssessmentResult` parsing code the
  live path uses (Local environment § bullet 1), so a shape mismatch fails the replay test immediately rather than
  surfacing only against the real API. Genuine prompt-quality/model-behavior fidelity is inherently unverifiable
  offline — out of scope for automated testing, called out here rather than silently assumed.
- **New:** the WireMock test dependency (`testImplementation`, in-process `WireMockExtension`, not a Testcontainers
  image) is this phase's first backend dependency addition since Phase 1 — verify its current major version has no
  conflicting Jetty/Jackson transitive versions against Spring Boot 4.1's BOM before implementation; fall back to a
  raw `HttpServer`-based stub (more code, zero dependency risk) only if a real conflict surfaces.
- **New:** `@EmbeddedId` composite-key mapping for `RiskAssessmentLineItem` is this codebase's first composite-PK
  entity (every prior table uses a single `UUID` PK, including the `JOINED`-inheritance `Transaction` subtypes) —
  flagged as new Hibernate-mapping territory worth extra attention in `AiRiskAssessmentRepositoryTest`, not a design
  risk (it's the standard `@EmbeddedId`/`@MapsId` pattern for a two-FK composite key).
