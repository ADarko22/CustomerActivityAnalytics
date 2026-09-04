# Phase 4 EXT Implementation Plan — Risk Assessment UX, Multi-Provider AI, and Backend Cleanup

**Status:** COMPLETE

Blueprint for `PHASE_4_EXT.md`. Read alongside `CLAUDE.md` (conventions), `docs/DECISIONS.md` (D4, D6, D18 all
apply; this plan proposes a new D19), and `docs/development/PHASE_4_EXT.md` (the five open questions it leaves for
this plan are resolved below, all empirically verified — not guessed — against the actual `spring-ai-bom:2.0.1`
artifacts and Anthropic's public API docs).

## Current State (verified)

- **`risk` package: exactly 20 top-level `.java` files** in `backend/.../risk/` (confirmed via `find -maxdepth 1`,
  correcting `PHASE_4_EXT.md`'s approximate "~19"), plus the existing `risk/ai/` (5 files) and `risk/dto/` (4 files)
  sub-packages, which stay as-is. **Zero files outside `risk/`** import anything from `risk.*` (confirmed via
  repo-wide grep) — the package-move is fully self-contained; no ripple edits anywhere else in the codebase.
- **9 test files** under `backend/src/test/.../risk/`, one-to-one with the classes they test, moving alongside
  their subjects. `AiRiskAssessmentWireMockReplayTest` is the one cross-cutting exception (exercises persistence +
  engine + ai together end-to-end) — placed in `risk.engine` alongside `AiRiskAssessmentOrchestratorTest`, which it
  most directly complements.
- **`AiRiskAssessmentOrchestrator`'s abstraction leak, confirmed exactly:** its constructor takes
  `@Value("${spring.ai.openai.chat.options.model}") String model`, and its one `log.info(...)` call is the only
  place `model`/`aiProviderProperties.provider()` are used. `AiProviderProperties` (`app.ai.provider`,
  `app.ai.record-mode`) is bound but `provider` is never read by anything that selects a bean — there is exactly one
  `RiskAssessmentAiClient` (`OpenAiRiskAssessmentAiClient`), unconditionally `@Component`-registered.
- **`OpenAiRiskAssessmentAiClient` currently injects the generic `ChatClient.Builder`** (auto-configured
  unconditionally today because only one model starter is on the classpath). **This does not survive adding a second
  provider starter** — see Design Clarification 5 below; this plan changes the injection shape as part of the fix,
  not as an afterthought.
- **Dependency verification (empirical, not assumed):**
  - `org.springframework.ai:spring-ai-starter-model-anthropic:2.0.1` resolves on Maven Central — confirmed via a
    direct `curl` HTTP 200 on its POM (`repo1.maven.org/.../spring-ai-starter-model-anthropic/2.0.1/....pom`) and via
    its `maven-metadata.xml`, which lists `2.0.1` as the latest version. It is managed by the already-imported
    `spring-ai-bom:2.0.1` (also confirmed resolvable), so no new BOM/version pin is needed — only a new
    `[libraries]` entry.
  - Downloaded and `javap`-inspected the actual `spring-ai-autoconfigure-model-anthropic:2.0.1` classes (mirroring
    `docs/DECISIONS.md` D16's precedent of verifying against resolved jars, not documentation prose, which can lag
    or generalize):
    - `AnthropicConnectionProperties.CONFIG_PREFIX = "spring.ai.anthropic"` — fields `baseUrl`, `apiKey`, `timeout`,
      `maxRetries`, `customHeaders`. So `spring.ai.anthropic.base-url` and `spring.ai.anthropic.api-key` exist and
      mirror OpenAI's shape exactly.
    - `AnthropicChatProperties.CONFIG_PREFIX = "spring.ai.anthropic.chat"` — has **both** a flat `model` field
      (`spring.ai.anthropic.chat.model`) **and** a nested `Options.model` field
      (`spring.ai.anthropic.chat.options.model`). This plan uses `chat.options.model`, matching the existing
      `spring.ai.openai.chat.options.model` shape already used in `application.yml`, for cross-provider consistency.
    - `AnthropicChatAutoConfiguration.anthropicChatModel(...)` produces a bean of concrete type
      `org.springframework.ai.anthropic.AnthropicChatModel` — distinct from `OpenAiChatAutoConfiguration`'s
      `org.springframework.ai.openai.OpenAiChatModel`. **Neither auto-configuration is conditioned on anything this
      app controls**; both `ChatModel` beans are always present once both starters are dependencies, regardless of
      `app.ai.provider`. This is the concrete reason `ChatClient.Builder` (generic, unqualified) cannot be injected
      once a second starter is added — see Design Clarification 5.
  - Anthropic Messages API response shape (`docs.anthropic.com` → redirects to `platform.claude.com/docs/en/api/
    messages`) confirmed: top-level `id`, `type: "message"`, `role: "assistant"`, `content: [{type: "text", text:
    "..."}]`, `model`, `stop_reason`, `usage`. Materially different envelope from OpenAI's
    `choices[].message.content` — the WireMock stub for Anthropic needs its own response body shape (§ Local
    environment).
- **Frontend data flow (confirmed unchanged since Phase 4):** `customerId` is router-bound into
  `TransactionTableComponent` (component-input-binding is active — both it and `AnalyticsPanelComponent` already
  rely on it), passed down to `TransactionDetailComponent`, which independently passes `(customerId, transactionId)`
  to `RiskAssessmentTriggerComponent` and `RiskAssessmentHistoryTableComponent` as sibling, uncoupled inputs. The
  only cross-component wiring is `RiskAssessmentTriggerComponent`'s `assessmentAdded` output →
  `#historyTable.reload()` template-ref call. `app.routes.ts` is a flat two-level tree
  (`customers/:customerId` → `transactions` | `analytics` children); `TransactionsPageComponent` is a pure
  `mat-tab-nav-bar` shell with no logic. `AiRiskAssessmentService.findHistory()` and
  `RiskAssessmentHistoryTableComponent`'s `transactionId` input are both currently hard-required; the backend
  endpoint (`AiRiskAssessmentController.findHistory`) already declares `@RequestParam(required = false) UUID
  transactionId` — only the Angular side needs to change to support an all-of-customer view.

## Design clarifications (resolving `PHASE_4_EXT.md`'s open questions)

1. **Anthropic starter/BOM availability — CONFIRMED, not a risk.** See Current State. Proceed with the dependency
   addition unconditionally.
2. **Anthropic `base-url` override — CONFIRMED.** `spring.ai.anthropic.base-url` exists, default
   `api.anthropic.com`, same shape as OpenAI's override.
3. **Two providers, one WireMock instance: ship both stub mappings, always present.** Not meaningfully more complex
   (one more mapping + response file, § Local environment) and keeps `app.ai.provider` switching zero-friction, per
   `PHASE_4_EXT.md`'s own stated preference.
4. **New view's placement: a third `mat-tab-nav-bar` tab, "Risk Assessments"**, alongside today's Transactions/
   Analytics — confirmed as the recommended option; adopted, since it is a one-line addition to an existing,
   already-consistent pattern (`transactions-page.component.html`) rather than inventing a new navigation surface.
5. **Provider-selection mechanism: `@ConditionalOnProperty` per implementation, injecting the provider-specific
   concrete `ChatModel` type — not the generic `ChatClient.Builder`.** This is a **new finding**, not anticipated by
   `PHASE_4_EXT.md`'s own risk list: once `spring-ai-starter-model-anthropic` is added, Spring AI's default
   `ChatClient.Builder` autoconfiguration (`@ConditionalOnSingleCandidate(ChatModel.class)`-style, backing off when
   more than one `ChatModel` bean exists) will no longer produce a usable unqualified `ChatClient.Builder` bean,
   because **both** `OpenAiChatModel` and `AnthropicChatModel` beans are always constructed (neither provider
   starter's autoconfiguration is gated by `app.ai.provider`). The fix, verified against the actual bean-producing
   method signatures (`OpenAiChatAutoConfiguration.openAiChatModel(...)` → `OpenAiChatModel`;
   `AnthropicChatAutoConfiguration.anthropicChatModel(...)` → `AnthropicChatModel`, both javap-confirmed): each
   `RiskAssessmentAiClient` implementation constructor-injects its own **concrete, distinct** `ChatModel` subtype
   (`OpenAiChatModel` / `AnthropicChatModel`) — type-based autowiring disambiguates cleanly since the two types
   never collide — then builds its own `ChatClient` via `ChatClient.builder(thatChatModel).build()` (a stable, core
   Spring AI static factory, unrelated to the per-starter autoconfiguration this plan works around).
   `OpenAiRiskAssessmentAiClient`'s existing `ChatClient.Builder chatClientBuilder` constructor parameter must
   therefore change to `OpenAiChatModel openAiChatModel` as part of this same fix — not left as-is, since leaving it
   unqualified would break at context-startup once the Anthropic starter is added (an ambiguous-bean failure this
   plan avoids by construction, not by qualifier gymnastics). `/implement` must confirm this boots cleanly with a
   context test exercising both `@ConditionalOnProperty` branches (§ Test plan).
6. **`docs/DECISIONS.md` gains D19** (multi-provider AI selection + the `ChatModel`-not-`ChatClient.Builder`
   injection fix), recorded as fulfilling — not superseding — D18's own stated extension point ("swapping providers
   later means adding an implementation and a config switch, not touching `AiRiskAssessmentOrchestrator` or its
   callers"). Added at implement time, per the established `PHASE_2_PLAN.md`/`PHASE_3_PLAN.md` precedent.
7. **`app.ai.provider` gains an environment-variable override** (`${AI_PROVIDER:openai}`), which
   `PHASE_4_EXT.md` implicitly requires (AC4's "setting `app.ai.provider=anthropic`") but the current
   `application.yml` doesn't support today (`provider: openai` is a literal, not `${...}`-templated, unlike every
   other provider-facing property in this file). Needed so the user's "try it out" workflow is a pure environment
   change, no `application.yml` edit.
8. **`risk.persistence` / `risk.engine` / `risk.api` — exact file assignment**, grouping by concern (data-access
   layer vs. orchestration/RAG/scoring vs. web), not by Spring stereotype:
   - `risk.persistence` (12 files): `RiskRule`, `RiskRuleRepository`, `RiskFinalAssessment`,
     `RiskFinalAssessmentRepository`, `RiskFinalAssessmentSpecifications`, `RiskAssessmentLineItem`,
     `RiskAssessmentLineItemId`, `RiskAssessmentLineItemRepository`, `RuleContributionRow`, `RiskLevel`,
     `RuleScope`, `RiskAssessmentPersistenceService` (a `@Service`, but its sole job is the two-table write — data
     access, not orchestration).
   - `risk.engine` (6 files): `AiRiskAssessmentOrchestrator`, `RiskRuleRetrievalService`,
     `AssessmentHistoryRetrievalService`, `PromptContextMapper`, `RiskScoringService`, `RiskAssessmentProperties`
     (most of its fields — `maxTriggeredRules`, `assessmentTimeout`, `levelThresholds`, `historyContextSize` — are
     engine-consumed; `sseTimeout` is the one field the controller also reads, an acceptable cross-package
     reference, not a violation of anything).
   - `risk.api` (2 files): `AiRiskAssessmentController`, `AiRiskAssessmentHistoryService`.
   - 12 + 6 + 2 = 20, matching the verified top-level count exactly. `risk.ai` (existing, gains
     `AnthropicRiskAssessmentAiClient` + a small shared prompt-rendering helper, see § Backend Design) and `risk.dto`
     (existing, unchanged) are untouched in location.
   - This is a purely mechanical move (package declaration + import updates only); ArchUnit's
     `packagesShouldBeFreeOfCycles` rule slices only on the first segment after the base package (confirmed during
     Phase 4 planning), so `risk.*` remains one slice regardless of these sub-packages — no new cycle risk, no new
     ArchUnit rule needed.

## Backend Design

### Provider abstraction fix (`risk.ai`)

`RiskAssessmentAiClient` interface gains one method:

```java
public interface RiskAssessmentAiClient {
  String PROMPT_VERSION = "v1";
  ModelAssessmentResult assess(String transactionContext, List<RiskRule> candidateRules, List<RiskFinalAssessment> history);
  String modelName();
}
```

A new package-private `RiskPromptRenderer` (static helper, `risk.ai`) extracts `OpenAiRiskAssessmentAiClient`'s
existing `renderRules`/`renderHistory` private static methods verbatim, so `AnthropicRiskAssessmentAiClient` reuses
them instead of duplicating ~20 lines of identical rendering logic — the Clean Code requirement `PHASE_4_EXT.md`
calls out applies here directly (don't fix one leak by introducing a copy-paste one).

`OpenAiRiskAssessmentAiClient` changes:
- Constructor parameter `ChatClient.Builder chatClientBuilder` → `OpenAiChatModel openAiChatModel`
  (`org.springframework.ai.openai.OpenAiChatModel`); body becomes
  `this.chatClient = ChatClient.builder(openAiChatModel).build();`.
- New `@Value("${spring.ai.openai.chat.options.model}") String model` constructor parameter (moved **from**
  `AiRiskAssessmentOrchestrator`, not duplicated) + a `public String modelName() { return model; }` override.
- Class annotation adds `@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai",
  matchIfMissing = true)` — `matchIfMissing = true` preserves today's default (unset `app.ai.provider` = OpenAI,
  satisfying `PHASE_4_EXT.md` AC4's "leaving it unset preserves today's OpenAI behavior exactly").
- `renderRules`/`renderHistory` calls delegate to the new `RiskPromptRenderer`.

New `AnthropicRiskAssessmentAiClient` (`risk.ai`), structurally mirroring the above:
```java
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "anthropic")
public class AnthropicRiskAssessmentAiClient implements RiskAssessmentAiClient {
  public AnthropicRiskAssessmentAiClient(
      AnthropicChatModel anthropicChatModel,
      @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
      @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource,
      @Value("${spring.ai.anthropic.chat.options.model}") String model) { ... }
  // assess(...) — identical shape to OpenAiRiskAssessmentAiClient's, chatClient built from anthropicChatModel
  // modelName() — returns model
}
```
Same prompt template resources (confirmed provider-agnostic: plain `{placeholder}` `.st` syntax, no OpenAI-specific
content) — no new prompt files needed.

`AiRiskAssessmentOrchestrator` changes: drop the `@Value("${spring.ai.openai.chat.options.model}") String model`
constructor parameter and the `model` field entirely; its `log.info(...)` line's `model` argument becomes
`aiClient.modelName()`. This is the concrete fix for AC5 ("no reference to any provider-specific Spring AI
configuration key").

### `risk` package sub-packaging

Mechanical move per Design Clarification 8's file lists — `package` declarations and imports updated across all 20
main + 9 test files (plus `risk.ai`'s 2 new/changed files); no other behavior change. After the move, `risk/`'s
top-level directory contains zero `.java` files (only the `persistence/`, `engine/`, `api/`, `ai/`, `dto/`
subdirectories) — the concrete, testable form of `PHASE_4_EXT.md` AC8.

### Configuration

`gradle/libs.versions.toml` — new library entry:
```toml
spring-ai-anthropic = { module = "org.springframework.ai:spring-ai-starter-model-anthropic" }
```
`backend/build.gradle.kts` — `implementation(libs.spring.ai.anthropic)` alongside the existing
`implementation(libs.spring.ai.openai)` (both managed by the already-imported `spring-ai-bom`, no version needed).

`application.yml` — add the Anthropic mirror of the existing OpenAI block, and make `app.ai.provider`
env-overridable:
```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:placeholder-api-key}
      chat:
        options:
          model: ${ANTHROPIC_MODEL:claude-sonnet-4-5}
      # base-url intentionally unset (Spring AI defaults to the real Anthropic API) — the local
      # profile points it at WireMock's Anthropic-shaped stub instead, mirroring OpenAI's setup.
app:
  ai:
    provider: ${AI_PROVIDER:openai}
```

`application-local.yml` — add the Anthropic WireMock override alongside the existing OpenAI one (same host:port,
since one WireMock instance serves both stub mappings on different paths):
```yaml
spring:
  ai:
    anthropic:
      base-url: http://localhost:${WIREMOCK_PORT:8089}
```
(No `/v1` suffix quirk here — unlike the OpenAI `openai-java` SDK, confirmed via `AnthropicConnectionProperties`'s
plain `baseUrl` field with no evidence of a similar path-inclusion default; `/implement` must still verify the
actual outgoing request path empirically against the WireMock stub, since this is the one item in this plan not
independently confirmed by a jar/docs inspection — flagged in § Risks.)

## Local environment

- `local-environment/wiremock/mappings/openai-chat-completions.json` — add `"fixedDelayMilliseconds": 2500` as a
  sibling of `status`/`headers`/`bodyFileName` (confirmed valid WireMock 3.9.1 mapping field). No other change.
- `local-environment/wiremock/mappings/anthropic-messages.json` (new) — matches `POST /v1/messages`, same
  `fixedDelayMilliseconds: 2500`, `bodyFileName: anthropic-messages-response.json`.
- `local-environment/wiremock/__files/anthropic-messages-response.json` (new) — Anthropic Messages API envelope
  (confirmed shape: `id`, `type: "message"`, `role: "assistant"`, `content: [{type: "text", text: "<JSON-encoded
  ModelAssessmentResult>"}]`, `model`, `stop_reason: "end_turn"`, `usage`), with the same `content` payload (rule
  IDs `90000000-...-000000000001`/`...003`, findings/recommendations text) as the existing OpenAI stub, so both
  providers demo identically offline.
- `local-environment/wiremock/README.md` — new "Anthropic" section mirroring the existing OpenAI record-mode
  instructions: set `AI_PROVIDER=anthropic` + a real `ANTHROPIC_API_KEY`, start WireMock in record mode (proxying
  to `https://api.anthropic.com` instead of `https://api.openai.com`), trigger a live assessment, copy the recorded
  mapping/response into `mappings/`/`__files/`. Since `WIREMOCK_RECORD_MODE`'s `--proxy-all` target is currently
  hardcoded to `https://api.openai.com` in `docker-compose.yml`'s `command`, note that recording Anthropic requires
  temporarily editing that one proxy target (documented as a manual step, not a second always-on record mode — no
  acceptance criterion asks for recording both providers simultaneously).
- `AiRiskAssessmentWireMockReplayTest` is unaffected by the delay addition (confirmed during Phase 4: it stubs its
  own in-process WireMock instance, independent of these Docker-mounted mapping files) and unaffected by the new
  Anthropic mapping (different provider, not exercised by that test unless a dedicated Anthropic replay test is
  added — see § Test plan).

## Frontend Design

- **`TransactionDetailComponent`** (`transaction-detail.component.html`): the `<mat-card>` gains a `<mat-card-
  actions>` block (Material's idiomatic slot for card-level buttons) directly inside it, holding
  `<app-risk-assessment-trigger>` and a new "View Risk Assessments History" button
  (`[routerLink]="['/customers', customerId, 'risk-assessments']"`). The entire
  `<section class="ai-risk-assessment-section"><h3>AI Risk Assessment</h3>...</section>` block — including the
  `<app-risk-assessment-history-table>` mount and the `#historyTable`/`assessmentAdded` wiring — is deleted; the
  trigger's live-progress/result card still renders below the actions row (inside the same `mat-card`, e.g. in
  `mat-card-content`), unchanged in its own internal behavior.
- **`AiRiskAssessmentService.findHistory`**: `transactionId: string` → `transactionId?: string`; the
  `HttpParams.set('transactionId', transactionId)` call becomes conditional (`if (transactionId) { params =
  params.set('transactionId', transactionId); }`), mirroring the existing conditional-set pattern already used for
  the `filter` object's optional fields in the same method.
- **`RiskAssessmentHistoryTableComponent`**: `@Input({required: true}) transactionId!: string` →
  `@Input() transactionId?: string`; passed through as-is to the now-optional service parameter. `ngOnChanges`'s
  existing `if (changes['transactionId'] || changes['customerId'])` guard needs no change — `customerId` stays
  `@Input({required: true})`, so it always registers a change on init regardless of whether `transactionId` is ever
  bound, guaranteeing the initial `load()` still fires for the new customer-only usage.
  - **Remove the nested expand-row entirely**: drop `multiTemplateDataRows` from the `<table>`, the
    `expandedDetail` `matColumnDef`/second `matRowDef`, `toggleExpand()`, `expandedAssessmentId` signal, the
    `(click)` handler and `cursor: pointer` styling on `.assessment-row`, and the `.detail-row`/`.detail-row-open`
    SCSS. The table becomes a plain, non-interactive `mat-table` row-wise (column filtering/sorting/pagination are
    unaffected — orthogonal to the expand mechanism). `RuleContribution`/`ruleContributions` stay in the
    `AiRiskAssessment` model and the API response (backend unchanged, still valid, just no longer rendered) — not
    removed, since Phase 4's history endpoint contract is out of scope here.
  - **Add a `transactionId` column** to `HISTORY_COLUMNS` (`risk-assessment-history-table.columns.ts`):
    `{ key: 'transactionId', label: 'Transaction', filterType: 'none' }`, positioned first (or after `triggeredAt`)
    so every row's origin is identifiable in the new all-customer view — harmless, always-present context in the
    still-supported single-transaction view too.
- **New route + page component**: `frontend/src/app/features/risk-assessment/risk-assessment-history-page/`
  (`risk-assessment-history-page.component.ts/.html/.scss/.spec.ts`) — a thin wrapper, `@Input({required: true})
  customerId!: string` (router-bound, matching `TransactionTableComponent`/`AnalyticsPanelComponent`'s existing
  pattern), template is essentially `<app-risk-assessment-history-table [customerId]="customerId" />` (no
  `transactionId` passed → all-of-customer mode) plus a page heading for orientation.
- **`app.routes.ts`**: new child route `{ path: 'risk-assessments', component: RiskAssessmentHistoryPageComponent }`
  alongside `transactions`/`analytics`, under the existing `customers/:customerId` parent (no new resolver needed,
  same `customerId` router-param binding already relied on).
- **`transactions-page.component.html`**: a third `<a mat-tab-link routerLink="risk-assessments" ...>Risk
  Assessments</a>`, matching the existing two tabs' exact markup/`routerLinkActive` pattern.

## File inventory

**Backend — new:** `risk/ai/AnthropicRiskAssessmentAiClient.java`, `risk/ai/RiskPromptRenderer.java`.

**Backend — moved (package rename, content otherwise unchanged) + modified in place:**
- → `risk/persistence/`: `RiskRule.java`, `RiskRuleRepository.java`, `RiskFinalAssessment.java`,
  `RiskFinalAssessmentRepository.java`, `RiskFinalAssessmentSpecifications.java`, `RiskAssessmentLineItem.java`,
  `RiskAssessmentLineItemId.java`, `RiskAssessmentLineItemRepository.java`, `RuleContributionRow.java`,
  `RiskLevel.java`, `RuleScope.java`, `RiskAssessmentPersistenceService.java` (+ test:
  `AiRiskAssessmentRepositoryTest.java`, `RiskFinalAssessmentSpecificationsTest.java`).
- → `risk/engine/`: `AiRiskAssessmentOrchestrator.java` (modified — drops the `model`/`@Value`, calls
  `aiClient.modelName()`), `RiskRuleRetrievalService.java`, `AssessmentHistoryRetrievalService.java`,
  `PromptContextMapper.java`, `RiskScoringService.java`, `RiskAssessmentProperties.java` (+ test:
  `AiRiskAssessmentOrchestratorTest.java` [modified — new `modelName()` mock stubbing],
  `AiRiskAssessmentWireMockReplayTest.java`, `PromptContextMapperTest.java`, `RiskAssessmentPropertiesTest.java`,
  `RiskRuleRetrievalServiceTest.java`, `RiskScoringServiceTest.java`).
- → `risk/api/`: `AiRiskAssessmentController.java`, `AiRiskAssessmentHistoryService.java` (+ test:
  `AiRiskAssessmentControllerTest.java`).
- `risk/ai/OpenAiRiskAssessmentAiClient.java` (modified — `ChatClient.Builder` → `OpenAiChatModel`,
  `@ConditionalOnProperty`, new `model`/`modelName()`), `risk/ai/RiskAssessmentAiClient.java` (modified — new
  `modelName()` method).

**Backend — modified:** `build.gradle.kts`, `gradle/libs.versions.toml` (Anthropic starter),
`application.yml` (`spring.ai.anthropic.*`, `app.ai.provider` env override), `application-local.yml`
(`spring.ai.anthropic.base-url`).

**Backend — new test:** `risk/ai/AnthropicRiskAssessmentAiClientTest.java` (or a parameterized extension of the
existing WireMock replay test covering both providers — `/plan-phase` recommends parameterizing
`AiRiskAssessmentWireMockReplayTest` over `app.ai.provider` rather than duplicating the whole test class, since the
pipeline logic under test is provider-agnostic by design), `risk/ai/RiskProviderSelectionTest.java` (a focused
`ApplicationContextRunner`-based test — not a full `@SpringBootTest` — asserting `app.ai.provider=openai`/unset
resolves `OpenAiRiskAssessmentAiClient` and `app.ai.provider=anthropic` resolves `AnthropicRiskAssessmentAiClient`,
exactly one bean either way). Placed in `risk/ai/` — mirroring where `OpenAiRiskAssessmentAiClient`/
`AnthropicRiskAssessmentAiClient` themselves live — not the unrelated top-level `config` package reserved for
`@Configuration` classes like `SecurityConfig`/`RiskAssessmentAsyncConfig`.

**Local environment — new:** `local-environment/wiremock/mappings/anthropic-messages.json`,
`local-environment/wiremock/__files/anthropic-messages-response.json`.

**Local environment — modified:** `local-environment/wiremock/mappings/openai-chat-completions.json` (delay),
`local-environment/wiremock/README.md` (Anthropic section).

**Frontend — new:** `features/risk-assessment/risk-assessment-history-page/*`
(`.ts/.html/.scss/.spec.ts`).

**Frontend — modified:** `transaction-detail.component.{ts,html,scss,spec.ts}`,
`ai-risk-assessment.service.{ts,spec.ts}`,
`risk-assessment-history-table/{risk-assessment-history-table.component.ts,.html,.scss,.spec.ts,
risk-assessment-history-table.columns.ts}`, `app.routes.ts`, `transactions-page.component.html`,
`transaction-table.component.spec.ts` — a real regression, not optional cleanup: this spec's two row-expansion
tests (`'expands a row to show its detail...'`, `'expanding a second row collapses the first'`) currently call a
`flushAiAssessmentHistory()` helper / `httpMock.match(...)` that expects an `ai-assessments` HTTP request to fire
merely from a transaction row expanding — a side effect of `TransactionDetailComponent`'s nested
`RiskAssessmentHistoryTableComponent` eagerly mounting under `multiTemplateDataRows` (Phase 4's design). Once that
nested component is removed here (§ Frontend Design), that request never fires; `flushAiAssessmentHistory()`'s
`httpMock.expectOne(...)` would throw "found none" — both tests must drop those now-obsolete expectations (no
`ai-assessments` request is expected on row-expand at all anymore), or they fail immediately post-move.

**Frontend — unchanged (reused as-is):** `risk-assessment-trigger/*` (internal behavior untouched, only its parent
template placement changes), `TransactionTableComponent`'s own row-expand mechanism (D14, unrelated).

**Documentation reconciliation (assigned as an `/implement`-time task, per established precedent):**
`docs/DECISIONS.md` gains `D19` (multi-provider AI selection via `@ConditionalOnProperty` + concrete-`ChatModel`
injection, Design Clarification 5/6).

## Test plan → Acceptance-criteria mapping

| `PHASE_4_EXT.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — integrated buttons, no separate section/inline table | — | `transaction-detail.component.spec.ts` (both buttons render inside/with the card; no `.ai-risk-assessment-section`/history-table element present); `transaction-table.component.spec.ts`'s two row-expansion tests updated to drop their now-obsolete `ai-assessments` request expectations (§ File inventory) — the regression check that AC1's removal didn't leave a stray HTTP-mock assumption behind |
| AC2 — trigger behavior unchanged | — (no orchestrator/SSE contract change) | `risk-assessment-trigger.component.spec.ts` unchanged (already covers the state machine); a `transaction-detail.component.spec.ts` case confirming the trigger still renders/functions from its new location |
| AC3 — new customer-wide flat history view | `AiRiskAssessmentControllerTest`/`AiRiskAssessmentHistoryService`-level coverage already exercises the optional-`transactionId` path (Phase 4, unchanged) | `risk-assessment-history-page.component.spec.ts` (renders the table scoped to `customerId` only); `risk-assessment-history-table.component.spec.ts` updated — a case with `transactionId` unset asserting the request omits the param and a Transaction column renders; expand-row tests removed (behavior deleted) |
| AC4 — `app.ai.provider` selects the correct client, defaults preserved | `RiskProviderSelectionTest` (both branches + default), `AiRiskAssessmentWireMockReplayTest` parameterized over both providers | — |
| AC5 — no provider-specific key in orchestrator | `AiRiskAssessmentOrchestratorTest` (asserts `log`/behavior sources `modelName()` from a mocked `RiskAssessmentAiClient`, not a `@Value`); a source-level check (no `spring.ai.` string literal in `AiRiskAssessmentOrchestrator.java`) is straightforward to eyeball at `/review` time, no dedicated test needed | — |
| AC6 — Anthropic offline/record-mode documented | — (doc-only) | — |
| AC7 — visible `MODEL_CALL` delay | Manual/e2e verification (`docker compose up wiremock` + a live `/stream` call, timing observed) — not meaningfully assertable as a fast unit/integration test without adding real wall-clock waits; `AiRiskAssessmentWireMockReplayTest`'s own in-process stub is intentionally undelayed (keeps that test fast) | — |
| AC8 — package sub-packaging, `./gradlew check` green | `ArchitectureTest`'s existing rules apply unchanged (no new rule needed, per Design Clarification 8); full `./gradlew check` must pass post-move with identical outcome | — |

Also covers Testing Scope items not tied to a single AC: all 9 existing `risk` test classes continue passing after
the package move (import/path updates only); `RiskPromptRenderer` extraction is covered transitively by both AI
clients' existing/new tests (no separate unit test needed for two five-line private-static-turned-shared methods —
avoiding a test for its own sake per `CLAUDE.md`'s Simplicity standard).

## Risks / Open Questions

- **Anthropic's actual outgoing request path/base-url composition** (whether `spring.ai.anthropic.base-url` needs a
  path suffix analogous to OpenAI's `/v1` quirk, documented in `application-local.yml`) is the one config detail
  this plan could not independently verify via jar/docs inspection (no evidence found of an SDK-level default-path
  behavior for Anthropic's client, unlike OpenAI's `openai-java` SDK) — `/implement` must confirm empirically
  (a real request against the local WireMock Anthropic stub) before trusting the `application-local.yml` value
  written above; adjust the stub's `urlPath` or the `base-url` value together if the first attempt 404s, exactly as
  happened for OpenAI during Phase 4.
- **`ChatClient.builder(ChatModel)` static factory** — used with high confidence (stable, core Spring AI API,
  unrelated to the per-starter autoconfiguration classes this plan worked around) but not itself jar-inspected;
  trivial to confirm at first compile.
- **Two always-live provider starters means both `spring.ai.openai.api-key` and `spring.ai.anthropic.api-key` must
  each have a working placeholder default** (confirmed both `ChatModel` beans construct unconditionally) — already
  designed for above (§ Configuration), flagged here so `/implement`/`/review` don't mistake one placeholder as
  optional.
- **Recording Anthropic responses requires a manual `docker-compose.yml` proxy-target edit** (§ Local environment)
  since `WIREMOCK_RECORD_MODE`'s `--proxy-all` target is a single hardcoded value — acceptable for a documented,
  occasional dev workflow per `PHASE_4_EXT.md`'s own scope (no requirement to record both providers at once), but
  worth `/review` double-checking the documented steps are actually followable end-to-end.
- **`RiskAssessmentPersistenceService` in `risk.persistence` despite being a `@Service`, not a repository** — a
  concern-based grouping choice (Design Clarification 8), not a stereotype-based one; flagged in case `/review`
  prefers a different split (e.g. a fourth `risk.persistence.service`), though this plan judges the extra
  sub-sub-package unnecessary for one class.
