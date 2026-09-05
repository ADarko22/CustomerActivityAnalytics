# Phase 5 EXT_2 — Implementation Plan

**Status:** COMPLETE
**Phase definition:** `docs/development/PHASE_5_EXT_2.md`

## Current State (verified)

- `docs/DECISIONS.md` D23 ("`risk_level` is computed on read, not persisted") and the corresponding
  `docs/specs/PROJECT_SPECIFICATION.md` `risk_final_assessments` table note (line 143, `risk_level` marked
  *derived, not stored*) were **already recorded** in the same commit that introduced `PHASE_5_EXT_2.md`
  (`a0ed3ac`). The phase doc's scope item asking for these to be written is therefore already satisfied —
  nothing further to write there; this plan only implements the code/schema consequences D23 describes.
- `RiskAssessmentProperties.levelFor(BigDecimal)` (`risk/engine/RiskAssessmentProperties.java:35`) already
  exists (Phase 4/4 EXT) and is exactly the score→level function every read path needs to call instead of
  reading a stored column. No new threshold logic — just new call sites.
- `risk_final_assessments.risk_level` is currently a stored, non-null `VARCHAR(10)` column (`V3__risk_assessment_
  schema.sql:15`), mapped by `RiskFinalAssessment` (`@Enumerated(EnumType.STRING)` field + constructor arg +
  getter). No seed script (`db/seed/R__seed_demo_data.sql`) inserts into `risk_final_assessments` — assessment
  rows only ever come from a live orchestrator run — so no seed-data migration is needed alongside the column
  drop.
- Every current write path to `RiskFinalAssessment.riskLevel` and every read of `.getRiskLevel()` (grep-verified,
  production code only):
  - Written: `RiskAssessmentPersistenceService.save(...)` (passes `scored.level()` into the constructor).
  - Read: `AiRiskAssessmentOrchestrator.toDto(...)` (SSE completion payload), `AiRiskAssessmentHistoryService.
    findHistory(...)` (history list DTO), `RiskFinalAssessmentSpecifications.filter(...)` (`riskLevel` query-param
    equality predicate), `RiskPromptRenderer.renderHistory(...)` (RAG history-context block). These are exactly
    the four consumers D23 and the phase doc's Functional Requirements table name — confirmed complete, no fifth
    place found.
  - Test-only construction of `new RiskFinalAssessment(..., RiskLevel.X, ...)`: `AiRiskAssessmentOrchestratorTest`,
    `AiRiskAssessmentRepositoryTest` (×2), `RiskFinalAssessmentSpecificationsTest` (helper, ×3 call sites),
    `AiRiskAssessmentWireMockReplayTest` (asserts `.getRiskLevel()` once).
- **The guardrail cannot be inserted as a simple pre-model-call check without a small refactor first.** Today,
  prompt *rendering* (loading `risk-assessment-{system,user}.st` as `PromptTemplate`s and calling `.render(Map.of(
  "transactionContext", ..., "rules", ..., "history", ...))`) happens *inside* `OpenAiRiskAssessmentAiClient.
  assess(...)` / `AnthropicRiskAssessmentAiClient.assess(...)` — duplicated verbatim in both — in the same method
  call that dispatches to the model. There is no single point today holding "the fully-assembled prompt" as a
  string the orchestrator could scan before calling `assess(...)`. See Backend Design §1 for the fix — extracting
  a single, shared prompt-assembly step is a prerequisite for the guardrail, not optional scope creep, and it
  also deletes the duplication between the two provider clients as a side effect.
- `RiskPromptRenderer` (`risk/ai/RiskPromptRenderer.java`) is a package-private static utility (`renderRules`,
  `renderHistory`) called by both provider clients; no dedicated unit test exists for it today (covered only
  indirectly via `AiRiskAssessmentWireMockReplayTest`).
- Frontend: `AssessmentStage` progress stages, `RiskLevel`, `RuleContribution`, chip styling, and the
  transaction-table's `multiTemplateDataRows` expand pattern (`transaction-table.component.{html,scss,ts}`) are
  exactly as the phase doc describes (verified by reading all of them). No `frontend/src/app/shared/` directory
  exists yet — this is the project's first cross-component-reuse case *within* one feature area, so the two new
  shared components are added under `features/risk-assessment/` itself (siblings of the components that consume
  them), not a new top-level shared module for two components.

## Backend Design

### 1. Single prompt-assembly step (prerequisite refactor)

Move prompt rendering out of both provider clients into one new engine-side component, so there is exactly one
place that produces "the fully-assembled prompt" — which both the guardrail and the AI client then consume.

- **New `risk/ai/AssembledPrompt.java`**: `public record AssembledPrompt(String system, String user) {}`. Lives in
  `risk.ai` (not `risk.engine`) because it's the input type of the `risk.ai` port (`RiskAssessmentAiClient`), and
  `risk.engine` already depends on `risk.ai` today (`AiRiskAssessmentOrchestrator` imports `RiskAssessmentAiClient`,
  `ModelAssessmentResult`, `AiProviderProperties`) — this keeps the same dependency direction rather than adding a
  new one.
- **New `risk/engine/RiskAssessmentPromptAssembler.java`** (`@Component`), replacing `risk/ai/RiskPromptRenderer.java`
  (delete it — its two static methods become private helpers here):
  ```java
  @Component
  public class RiskAssessmentPromptAssembler {
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final RiskAssessmentProperties riskProperties;

    public RiskAssessmentPromptAssembler(
        @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
        @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource,
        RiskAssessmentProperties riskProperties) {
      this.systemPromptTemplate = new PromptTemplate(systemPromptResource);
      this.userPromptTemplate = new PromptTemplate(userPromptResource);
      this.riskProperties = riskProperties;
    }

    public AssembledPrompt assemble(
        String transactionContext, List<RiskRule> rules, List<RiskFinalAssessment> history) {
      String user = userPromptTemplate.render(Map.of(
          "transactionContext", transactionContext,
          "rules", renderRules(rules),
          "history", renderHistory(history)));
      return new AssembledPrompt(systemPromptTemplate.render(), user);
    }

    private static String renderRules(List<RiskRule> rules) { /* moved verbatim from RiskPromptRenderer */ }

    private String renderHistory(List<RiskFinalAssessment> history) {
      // same shape as today, except: "  riskLevel: " + riskProperties.levelFor(assessment.getRiskScore())
      // instead of assessment.getRiskLevel() — forced by the column drop, and itself satisfies the phase's
      // "RAG history-context block" derived-riskLevel requirement.
    }
  }
  ```
- **`risk/ai/RiskAssessmentAiClient.java`**: `assess(...)` signature becomes `ModelAssessmentResult assess(AssembledPrompt prompt)` (was `assess(String, List<RiskRule>, List<RiskFinalAssessment>)`).
- **`OpenAiRiskAssessmentAiClient` / `AnthropicRiskAssessmentAiClient`**: drop the `Resource systemPromptResource`/
  `Resource userPromptResource` constructor params and the `PromptTemplate` fields entirely; `assess` becomes:
  ```java
  @Override
  public ModelAssessmentResult assess(AssembledPrompt prompt) {
    return chatClient.prompt().system(prompt.system()).user(prompt.user()).call().entity(ModelAssessmentResult.class);
  }
  ```

### 2. PII guardrail — config + service

- **New `risk/engine/PiiGuardrailProperties.java`** (`@ConfigurationProperties(prefix = "app.risk.guardrail")`,
  mirroring `RiskAssessmentProperties`/`AnalyticsRangeProperties`'s fail-fast `@PostConstruct` idiom):
  ```java
  @ConfigurationProperties(prefix = "app.risk.guardrail")
  public record PiiGuardrailProperties(List<PatternRule> patterns) {

    @PostConstruct
    void validate() {
      if (patterns == null || patterns.isEmpty()) {
        throw new IllegalStateException("app.risk.guardrail.patterns must configure at least one PII pattern");
      }
      for (PatternRule rule : patterns) {
        if (rule.name() == null || rule.name().isBlank()) {
          throw new IllegalStateException("app.risk.guardrail.patterns[].name must not be blank");
        }
        if (rule.regex() == null || rule.regex().isBlank()) {
          throw new IllegalStateException(
              "app.risk.guardrail.patterns[].regex must not be blank for pattern=" + rule.name());
        }
        try {
          Pattern.compile(rule.regex());
        } catch (PatternSyntaxException e) {
          throw new IllegalStateException(
              "app.risk.guardrail.patterns[].regex is invalid for pattern=" + rule.name(), e);
        }
      }
    }

    public record PatternRule(String name, String regex) {}
  }
  ```
- **New `risk/engine/PiiGuardrailService.java`** (`@Component`), precompiles once at construction (not per-scan —
  Global NFR "without harming performance"):
  ```java
  @Component
  public class PiiGuardrailService {
    private final List<CompiledPattern> compiledPatterns;

    public PiiGuardrailService(PiiGuardrailProperties properties) {
      this.compiledPatterns = properties.patterns().stream()
          .map(p -> new CompiledPattern(p.name(), Pattern.compile(p.regex())))
          .toList();
    }

    /** Returns the violated pattern's name on a match, empty on a clean prompt. */
    public Optional<String> scan(String prompt) {
      return compiledPatterns.stream()
          .filter(cp -> cp.pattern().matcher(prompt).find())
          .map(CompiledPattern::name)
          .findFirst();
    }

    private record CompiledPattern(String name, Pattern pattern) {}
  }
  ```
- **Default patterns** (`application.yml`), one per category the phase names:
  ```yaml
  app:
    risk:
      guardrail:
        patterns:
          - name: CARD_PAN
            regex: '\b(?:\d[ -]?){13,19}\b'
          - name: IBAN
            regex: '\b[A-Z]{2}\d{2}[A-Z0-9]{10,30}\b'
          - name: EMAIL
            regex: '\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b'
          - name: CRYPTO_WALLET
            regex: '\b0x[a-fA-F0-9]{40}\b|\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\b'
  ```
  **Verified no false positive against every numeric field the prompt renders** (Design Clarification below):
  amounts (`6000.00`), MCC codes (4 digits), rule weights/score contributions (`25.00`), and `Instant.toString()`
  timestamps (`-`/`T`/`:`/`.`-separated, longest contiguous digit run is the 6-9-digit fractional-seconds part)
  all stay under 13 consecutive digits. The one field worth flagging: `transactionId`/`ruleId` are UUIDs
  (8-4-4-4-12 hex groups) — the 12-char final group is the longest possible all-digit run *if* every hex
  character in it happens to be `0`-`9`, which is exactly at the 13-digit threshold's boundary (12 < 13), so it
  cannot trip `CARD_PAN` by construction, not by luck of the currently-seeded UUIDs.

### 3. Guardrail wired into the orchestrator + new SSE stage

- `risk/dto/AssessmentStage.java`: insert `GUARDRAIL_CHECK` between `HISTORY_RETRIEVAL` and `MODEL_CALL`.
- `AiRiskAssessmentOrchestrator`: inject `RiskAssessmentPromptAssembler promptAssembler` and `PiiGuardrailService
  guardrailService` (two new constructor params). New flow:
  ```java
  emitSafely(emitter, progress(PROMPT_BUILDING));
  String context = promptContextMapper.map(transaction);

  emitSafely(emitter, progress(RULE_RETRIEVAL));
  List<RiskRule> rules = riskRuleRetrievalService.findApplicable(transaction.activityType());

  emitSafely(emitter, progress(HISTORY_RETRIEVAL));
  List<RiskFinalAssessment> history = assessmentHistoryRetrievalService.recentFor(...);

  AssembledPrompt prompt = promptAssembler.assemble(context, rules, history);

  emitSafely(emitter, progress(GUARDRAIL_CHECK));
  Optional<String> violation = guardrailService.scan(prompt.user());
  if (violation.isPresent()) {
    log.warn("AI risk assessment blocked by PII guardrail: transactionId={}, violatedPattern={}",
        transaction.transactionId(), violation.get());
    emitSafely(emitter, AiRiskAssessmentEventDto.failed(GENERIC_FAILURE_MESSAGE));
    emitter.complete();
    return;
  }

  emitSafely(emitter, progress(MODEL_CALL));
  try {
    ModelAssessmentResult result = callWithTimeout(prompt); // now takes AssembledPrompt, not (context, rules, history)
    ...
  ```
  `toDto(...)` replaces `persisted.getRiskLevel()` with `riskProperties.levelFor(persisted.getRiskScore())`
  (`riskProperties` is already an orchestrator field).
- **Design Clarification — scan the *user* prompt only, not system+user.** The system prompt
  (`risk-assessment-system.st`) has zero template variables — it renders to byte-identical static text on every
  request, never carrying transaction/rule/history content. Regex-scanning it on every call would be pure
  overhead with zero incremental protection (Global NFR: logging/tracing "without harming performance"). All
  RAG-injected content lands exclusively in the user prompt, which is what's scanned.

### 4. `risk_level` column drop

- **New `backend/src/main/resources/db/migration/V4__drop_risk_level.sql`**:
  ```sql
  -- Phase 5 EXT_2: risk_level is computed on read from risk_score (docs/DECISIONS.md D23), no longer stored.
  ALTER TABLE risk_final_assessments DROP COLUMN risk_level;
  ```
- `RiskFinalAssessment`: remove the `riskLevel` field, its constructor parameter, `@Enumerated`/`@Column`
  annotations, and `getRiskLevel()`.
- `RiskAssessmentPersistenceService.save(...)`: drop `scored.level()` from the `new RiskFinalAssessment(...)` call
  (`RiskScoringService.ScoredAssessment.level()` itself is untouched — still computed at scoring time for the
  orchestrator's own log line, just no longer persisted).
- `RiskFinalAssessmentSpecifications.filter(...)`: add a `RiskAssessmentProperties riskProperties` parameter;
  replace the `riskLevel` equality predicate with a score-range translation using the same boundary logic as
  `RiskAssessmentProperties.levelFor` (`<=lowMax` = LOW, `(lowMax, mediumMax]` = MEDIUM, `>mediumMax` = HIGH):
  ```java
  if (riskLevel != null) {
    RiskAssessmentProperties.LevelThresholds t = riskProperties.levelThresholds();
    switch (riskLevel) {
      case LOW -> predicates.add(cb.lessThanOrEqualTo(root.get("riskScore"), t.lowMax()));
      case MEDIUM -> {
        predicates.add(cb.greaterThan(root.get("riskScore"), t.lowMax()));
        predicates.add(cb.lessThanOrEqualTo(root.get("riskScore"), t.mediumMax()));
      }
      case HIGH -> predicates.add(cb.greaterThan(root.get("riskScore"), t.mediumMax()));
    }
  }
  ```
  **Design Clarification — this duplicates `levelFor`'s boundary logic rather than extracting a shared helper.**
  One runs as a scalar `BigDecimal.compareTo` in Java, the other builds a JPA `CriteriaBuilder` predicate — there
  is no natural shared abstraction between the two without over-engineering a single-use interface for three
  lines of code (CLAUDE.md Coding Standard #3). Both call sites are exercised by boundary-value tests
  (`RiskAssessmentPropertiesTest` and `RiskFinalAssessmentSpecificationsTest`, both already covering
  `lowMax`/`lowMax+1`/`mediumMax`/`mediumMax+1`), so any future drift between the two is caught immediately by
  either suite, not silently.
  `risk.persistence → risk.engine` (for `RiskAssessmentProperties`) is not a new dependency direction —
  `RiskAssessmentPersistenceService` (already in `risk.persistence`) already imports `risk.engine.
  RiskScoringService` today.
- `AiRiskAssessmentHistoryService`: inject `RiskAssessmentProperties riskProperties` (new constructor param,
  already how `AiRiskAssessmentController` depends on it, so no new package-dependency direction); replace
  `assessment.getRiskLevel()` with `riskProperties.levelFor(assessment.getRiskScore())` when building each DTO;
  pass `riskProperties` into `RiskFinalAssessmentSpecifications.filter(...)`.
- The controller's own `findHistory(...)` signature is **unchanged** — `riskLevel` stays a `@RequestParam(required
  = false) RiskLevel` exactly as today (per the phase's "No API Additions" scope); only the service-layer
  translation changes.

## Frontend Design

### 5. `GUARDRAIL_CHECK` stage in the trigger's live progress list

- `ai-risk-assessment.model.ts`: add `'GUARDRAIL_CHECK'` to the `AssessmentStage` union, between
  `'HISTORY_RETRIEVAL'` and `'MODEL_CALL'`.
- `risk-assessment-trigger.component.ts`: insert `'GUARDRAIL_CHECK'` into `PROGRESS_STAGES` at the same position;
  add `GUARDRAIL_CHECK: 'Running safety checks'` to `STAGE_LABELS`. No other logic changes — `isStageDone`/
  `isStageCurrent` are already stage-agnostic (index-based against `seenStages()`).

### 6. Shared `RiskLevelBadgeComponent` (new palette)

New `frontend/src/app/features/risk-assessment/risk-level-badge/risk-level-badge.component.{ts,html,scss,spec.ts}`:
```ts
@Component({
  selector: 'app-risk-level-badge',
  standalone: true,
  imports: [MatChipsModule],
  templateUrl: './risk-level-badge.component.html',
  styleUrl: './risk-level-badge.component.scss',
})
export class RiskLevelBadgeComponent {
  @Input({ required: true }) level!: RiskLevel;
}
```
```html
<mat-chip [class]="'risk-chip-' + level.toLowerCase()">{{ level }}</mat-chip>
```
```scss
.risk-chip-low {
  --mdc-chip-elevated-container-color: #fff9db; // light yellow (was light green)
  --mdc-chip-label-text-color: #8a6d00;
}
.risk-chip-medium {
  --mdc-chip-elevated-container-color: #fff4e5; // unchanged (already light orange)
  --mdc-chip-label-text-color: #b26a00;
}
.risk-chip-high {
  --mdc-chip-elevated-container-color: #fdecea; // unchanged (already light red)
  --mdc-chip-label-text-color: #c62828;
}
```
Then, in both consumers:
- `risk-assessment-history-table.component.html`: `@case ('riskLevel') { <app-risk-level-badge [level]="row.riskLevel" /> }`.
- `risk-assessment-trigger.component.html` (`complete` case): `<app-risk-level-badge [level]="r.riskLevel" />`
  replacing the inline `<mat-chip>`.
- Both `.component.ts` files: swap `MatChipsModule` import for `RiskLevelBadgeComponent` in their `imports` array
  (each file's only `mat-chip` usage is the one being replaced — verified, safe to drop the module import).
- Both `.component.scss` files: delete the `.risk-chip-low/-medium/-high` blocks entirely (AC8 requires zero
  duplicated chip CSS remaining in either file).

### 7. Shared `RuleContributionsListComponent`

New `frontend/src/app/features/risk-assessment/rule-contributions-list/rule-contributions-list.component.{ts,html,scss,spec.ts}`:
```ts
@Component({
  selector: 'app-rule-contributions-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rule-contributions-list.component.html',
  styleUrl: './rule-contributions-list.component.scss',
})
export class RuleContributionsListComponent {
  @Input({ required: true }) contributions!: RuleContribution[];

  get sortedContributions(): RuleContribution[] {
    return [...this.contributions].sort((a, b) => b.scoreContribution - a.scoreContribution);
  }
}
```
```html
@if (sortedContributions.length) {
  <ul class="rule-contributions-list">
    @for (c of sortedContributions; track c.ruleId) {
      <li><span class="rule-name">{{ c.ruleName }}</span><span class="rule-score">+{{ c.scoreContribution }}</span></li>
    }
  </ul>
} @else {
  <p class="empty-state">No rules fired for this assessment.</p>
}
```
Minimal flex-row `<li>` styling in the `.scss`.

### 8. History-table expand-to-detail row (mirrors `TransactionTableComponent`, D14)

- `risk-assessment-history-table.component.ts`:
  - add `readonly expandedAssessmentId = signal<string | null>(null);`
  - add `toggleExpand(row: AiRiskAssessment): void { this.expandedAssessmentId.set(this.expandedAssessmentId() === row.assessmentId ? null : row.assessmentId); }`
  - reset it to `null` inside the existing `ngOnChanges` block (alongside the other filter resets).
  - `imports`: add `RiskLevelBadgeComponent`, `RuleContributionsListComponent`.
- `risk-assessment-history-table.component.html`:
  - add `multiTemplateDataRows` to the `<table mat-table ...>` element.
  - new column def:
    ```html
    <ng-container matColumnDef="expandedDetail">
      <td mat-cell *matCellDef="let row" [attr.colspan]="displayedColumns.length">
        <app-rule-contributions-list [contributions]="row.ruleContributions" />
      </td>
    </ng-container>
    ```
  - data row gets `(click)="toggleExpand(row)"` and `class="assessment-row"`; add a second row def:
    ```html
    <tr mat-row *matRowDef="let row; columns: ['expandedDetail']" class="detail-row"
        [class.detail-row-open]="row.assessmentId === expandedAssessmentId()"></tr>
    ```
- `risk-assessment-history-table.component.scss`: add the same `tr.assessment-row { cursor: pointer; }` /
  `tr.detail-row { display: none; &.detail-row-open { display: table-row; td { padding: 0 1rem 1rem;
  border-bottom: none; } } }` rules verbatim from `transaction-table.component.scss` (the `display: none`
  approach is there specifically because `height:0`/`overflow:hidden` doesn't reliably collapse a populated
  `<tr>` across browsers — same reasoning applies here unchanged).

### 9. Live trigger result panel — same breakdown, always visible

`risk-assessment-trigger.component.html` (`complete` case), directly under the Recommendations paragraph:
```html
<app-rule-contributions-list [contributions]="r.ruleContributions" />
```
No expand/collapse here — unlike the history table's many rows, this panel shows exactly one (the
just-completed) result, so the breakdown renders unconditionally, consistent with how findings/recommendations
already render unconditionally in this panel.
`risk-assessment-trigger.component.ts` `imports`: add `RuleContributionsListComponent`.

## File inventory

**Backend — new:**
`risk/ai/AssembledPrompt.java`; `risk/engine/RiskAssessmentPromptAssembler.java`; `risk/engine/
PiiGuardrailProperties.java`; `risk/engine/PiiGuardrailService.java`; `backend/src/main/resources/db/migration/
V4__drop_risk_level.sql`; test: `risk/engine/RiskAssessmentPromptAssemblerTest.java`; `risk/engine/
PiiGuardrailServiceTest.java`; `risk/engine/PiiGuardrailPropertiesTest.java`; `risk/api/
AiRiskAssessmentHistoryServiceTest.java` (new — no such file exists today; unit test with mocked repositories,
covers the dynamic-`riskLevel`-recomputation regression for AC5's history-endpoint half, see AC-mapping table).

**Backend — deleted:** `risk/ai/RiskPromptRenderer.java`.

**Backend — modified:** `risk/ai/RiskAssessmentAiClient.java`; `risk/ai/OpenAiRiskAssessmentAiClient.java`;
`risk/ai/AnthropicRiskAssessmentAiClient.java`; `risk/dto/AssessmentStage.java`; `risk/engine/
AiRiskAssessmentOrchestrator.java` (+ `.spec` test); `risk/persistence/RiskFinalAssessment.java`; `risk/
persistence/RiskAssessmentPersistenceService.java`; `risk/persistence/RiskFinalAssessmentSpecifications.java` (+
test, including a genuinely new combined-filter test case — see AC6 in the AC-mapping table, existing tests only
ever exercise one filter at a time today); `risk/api/AiRiskAssessmentHistoryService.java`; `application.yml`;
test: `AiRiskAssessmentOrchestratorTest`; `AiRiskAssessmentRepositoryTest`; `RiskFinalAssessmentSpecificationsTest`;
`AiRiskAssessmentWireMockReplayTest`;
`AiRiskAssessmentControllerTest` (only if `RiskPropertiesTestConfig`'s bean wiring needs the new
`PiiGuardrailProperties`/`RiskAssessmentPromptAssembler` beans present in the `@WebMvcTest` slice — verify at
implement time; likely no change since the controller itself gains no new dependency).

**Frontend — new:** `risk-level-badge/risk-level-badge.component.{ts,html,scss,spec.ts}`; `rule-contributions-list/
rule-contributions-list.component.{ts,html,scss,spec.ts}`.

**Frontend — modified:** `ai-risk-assessment.model.ts`; `risk-assessment-trigger.component.{ts,html,scss,spec.ts}`;
`risk-assessment-history-table.component.{ts,html,scss,spec.ts}`.

**Docs:** `docs/development/PHASE_5_EXT_2.md` (`Status` → `IMPLEMENTED` at `/implement` time), this plan file.
No further `docs/DECISIONS.md`/`PROJECT_SPECIFICATION.md` changes — D23 and the spec note already exist (see
Current State).

## Test plan → Acceptance-criteria mapping

| `PHASE_5_EXT_2.md` AC | Coverage |
|---|---|
| AC1 — seeded PII blocks before any LLM call, generic `FAILED`, `WARN` log names the pattern only, no row persisted | `PiiGuardrailServiceTest`: one hit per category (CARD_PAN, IBAN, EMAIL, CRYPTO_WALLET) + a miss case. `AiRiskAssessmentOrchestratorTest`: new test stubbing `guardrailService.scan(...)` → `Optional.of("EMAIL")`, asserting `aiClient` and `persistenceService` are never invoked, stages are exactly `[PROMPT_BUILDING, RULE_RETRIEVAL, HISTORY_RETRIEVAL, GUARDRAIL_CHECK, FAILED]`, and the emitted message is the generic one (not the pattern name) |
| AC2 — no false positive against real seeded fixtures | `AiRiskAssessmentWireMockReplayTest` (both `WhenProviderIsOpenAi`/`WhenProviderIsAnthropic` nested classes) continues to pass unmodified against its existing realistic fixture (amount `6000.00`, card `****1234`, rule weight `25.00`, findings/recommendations prose) — this *is* the "clean, real-seed-data prompt" regression proof; no separate fixture needed |
| AC3 — `GUARDRAIL_CHECK` between `HISTORY_RETRIEVAL` and `MODEL_CALL` | `AiRiskAssessmentOrchestratorTest.emitsStagesInOrderThenCompletesWithPersistedResult` updated: asserts 6 events (was 5), stage list includes `GUARDRAIL_CHECK` at the correct position |
| AC4 — no `risk_level` column, everything compiles/passes without it | `V4__drop_risk_level.sql` runs against the Testcontainers Postgres in every integration test (D10); `RiskFinalAssessment`/repository/entity tests updated to construct without a level; a full `./gradlew check` is the final proof |
| AC5 — changing `app.risk.level-thresholds` retroactively changes an already-persisted score's computed level, via **both** named read paths | `RiskAssessmentPropertiesTest` already covers `levelFor` boundary behavior directly (the pure function both paths below call). **History-endpoint half:** new `risk/api/AiRiskAssessmentHistoryServiceTest.java` (unit test, repositories mocked) persists/stubs one assessment with a fixed `riskScore`, calls `findHistory(...)` twice — once per `RiskAssessmentProperties` instance built with a different `levelThresholds` — and asserts the returned `AiRiskAssessmentDto.riskLevel` differs between the two calls. **RAG-history-context half:** `RiskAssessmentPromptAssemblerTest.assembleRendersHistoryEntryRiskLevelFromCurrentThresholds` (new, part of the `RiskAssessmentPromptAssemblerTest` file already listed under File inventory) calls `assemble(...)` with one `RiskFinalAssessment` fixture under two `RiskAssessmentProperties` threshold instances and asserts the rendered `riskLevel:` line in `AssembledPrompt.user()` differs between the two |
| AC6 — `riskLevel=HIGH/LOW/MEDIUM` filter still returns the right rows, combinable with other filters | `RiskFinalAssessmentSpecificationsTest.filtersByRiskLevel` updated to pass a `RiskAssessmentProperties` instance and assert against `riskScore` (not a removed `getRiskLevel()`). **Combinability is not actually exercised by any existing test** — each current test (`filtersByTransactionId`, `filtersByTriggeredAtRange`, `filtersByScoreRange`) passes exactly one non-null filter argument at a time, `riskLevel` included; a genuinely new test `filtersByRiskLevelCombinedWithAnotherFilter` is added, calling `filter(...)` with both `riskLevel` **and** a second non-null filter (e.g. `riskLevel=RiskLevel.HIGH` plus `transactionId=transaction1`, against fixture rows where one HIGH-scored row belongs to `transaction1` and another HIGH-scored row belongs to a different transaction) and asserting only the row matching *both* predicates is returned |
| AC7 — expand row + trigger panel both render fired rules with `ruleName`/`scoreContribution`, sorted descending | `rule-contributions-list.component.spec.ts` (new, sort order); `risk-assessment-history-table.component.spec.ts` (new test: click a row, assert the rendered contributions list appears with correct content); `risk-assessment-trigger.component.spec.ts` (existing `COMPLETE` test extended to assert the breakdown renders) |
| AC8 — LOW/MEDIUM/HIGH light-yellow/orange/red via one shared component, no duplicated chip CSS | `risk-level-badge.component.spec.ts` (new, one test per level asserting the right CSS class); grep/verify both `.scss` files have zero `.risk-chip-*` rules remaining |
| AC9 — `./gradlew check` and `npm test` pass | Run both at `/implement` time before marking `IMPLEMENTED` |

## Risks / Open Questions

- **Carried forward from the phase doc, not resolved here (correctly out of scope):** no scope/intent classifier
  for "out of scope querying" — there is still no free-text user input anywhere near this flow, confirmed again
  during this planning pass. Revisit only if a free-text AI-facing feature is ever added.
- **`AiRiskAssessmentControllerTest`'s `@WebMvcTest` slice**: adding `PiiGuardrailProperties`/
  `RiskAssessmentPromptAssembler` as new orchestrator dependencies is invisible to this test today (it
  `@MockitoBean`-mocks `AiRiskAssessmentOrchestrator` wholesale, never constructing a real one) — flagged in the
  file inventory as "verify at implement time" rather than assumed, since a `@WebMvcTest` slice's exact bean set
  can be sensitive to unrelated `@ConfigurationProperties` scanning; expected outcome is **no change needed**.
- **Regex false-positive risk on `CARD_PAN`'s broad 13-19-digit-run pattern** is addressed under Backend Design
  §2 with a concrete field-by-field check against everything the user prompt currently renders — if a future
  field ever introduces a long uninterrupted digit run (e.g. a raw phone number were ever added to
  `PromptContextMapper`), it would need re-checking against this same pattern; not a risk today.
