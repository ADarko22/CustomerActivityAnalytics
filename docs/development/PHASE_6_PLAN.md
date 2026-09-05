# Phase 6 Implementation Plan — Hardening, Polishing and Security

**Status:** COMPLETE
**Phase definition:** `docs/development/PHASE_6.md`

Blueprint for closing out every OPEN/CONFIRMED SonarCloud maintainability issue against application code with a real
fix (not a suppression), closing the AC1/AC2 deprecation/vulnerability gaps, filling in empty documentation, and
adding class-level Javadoc where it's genuinely missing — with **zero behavior change** (`PHASE_6.md` Scope "Out").
Read alongside `CLAUDE.md` (Coding Standard #3 — avoid unnecessary abstractions), `docs/DECISIONS.md` D22 (Flyway
SQL excluded from analysis), and `PHASE_6.md`'s own Functional Requirements table (the authoritative rule/count
breakdown this plan works against).

## Current State (verified)

- **Four of `PHASE_6.md`'s Functional Requirements rows are already fully implemented**, verified by reading the
  current repo state directly rather than assumed from the phase doc's own narrative:
  - *Automated quality gate* (AC8): `.github/workflows/ci.yml`'s SonarCloud step already reads `if: ${{
    secrets.SONAR_TOKEN != '' }}` (not the broken same-step-`env`-var form the phase doc's Risks section
    describes as the original bug) — already fixed.
  - *Coverage visibility* (AC9): root `build.gradle.kts`'s `sonar { properties { ... } }` block already sets
    `sonar.coverage.jacoco.xmlReportPaths` and `sonar.javascript.lcov.reportPaths` — already wired.
  - *Sonar exclusion* (D22, AC11): `sonar.exclusions = "backend/src/main/resources/db/**"` is already present —
    already applied, and no `V*` migration content needs touching.
  - *README badges* (AC10): root `README.md`'s top 5 lines are already the 5 named SonarCloud badges
    (Quality Gate, Maintainability, Coverage, Bugs, Vulnerabilities), correctly pointing at
    `ADarko22_CustomerActivityAnalytics` — already present.
  - This plan therefore does **no CI/build-config work** for AC8–AC11 — only the remaining rows (SonarCloud issue
    remediation) and ACs 1/2/3/5/6/7 need actual changes below.
- **SonarCloud issue catalog, verified file-by-file against the current source (not assumed from the phase doc's
  counts) — see Design §1–§7 for the fix for each:**
  - `java:S1192` (2): `SecurityConfig.java` (`"ADMIN"` literal ×3, lines 38/39/41); `RiskFinalAssessmentSpecifications.java`
    (`"riskScore"` literal ×6).
  - `java:S107` (10 named + 2 more the same refactor naturally cascades into, see below): `TransactionService
    .findOverview` (10 params), `AnalyticsService.findTimeSeries` (10) and the private `fetchRows` (9),
    `AiRiskAssessmentHistoryService.findHistory` (8), `CardActivitySpecifications.filter` /
    `PaymentActivitySpecifications.filter` / `CryptoActivitySpecifications.filter` (11 each), and the
    `CardActivity` (13) / `PaymentActivity` (10) / `CryptoActivity` (11) entity constructors.
  - `java:S4502` (1): `SecurityConfig.java:32`, `http.csrf(csrf -> csrf.disable())` — no comment above it at all
    today (the class Javadoc above the class explains JWT/roles, not CSRF).
  - `java:S112`/`S1130` (1): `SecurityConfig.securityFilterChain(HttpSecurity http) throws Exception` — the
    `throws` isn't dead (`HttpSecurity.build()` genuinely declares `throws Exception`), so the fix is to catch and
    rethrow unchecked inside the method, not just delete the clause.
  - `java:S2629` (1): of the 8 `log.debug(...)` call sites in application code, 7 pass only plain
    variables/fields (not flaggable); exactly one — `AiRiskAssessmentOrchestrator.java` (rule-matches line, right
    after the `log.info(...)` completion line) — passes two method-call results (`transaction.transactionId()`,
    `result.ruleMatches()`) unconditionally.
  - Test-quality nits (`S6068`/`S5853`/`S5778`/`S2925`, 14 total): only **one** is confirmed by direct inspection —
    `AiRiskAssessmentOrchestratorTest.modelCallTimeoutEmitsFailedWithoutPersisting`'s `Thread.sleep(300)` (S2925).
    Grepping the entire `backend/src/test/java` tree for the other three rules' textbook trigger patterns
    (`org.junit.Test`/`Assert.*` JUnit4 remnants, `assertTrue(x.equals(y))`, `assertThrows` at all) returns **zero
    hits** — the suite uses AssertJ's `assertThat(...)` exclusively and never calls `assertThrows`. **This does not
    reconcile with "14 total"** — flagged as a genuine gap, not silently assumed complete; see Risks below.
  - Gradle Kotlin-DSL task metadata (`kotlin:S6626`/`S6629`, 6 total): exactly 5 custom `tasks.register(...)` calls
    exist repository-wide (no 6th file anywhere — `backend/build.gradle.kts` and `settings.gradle.kts` register no
    custom tasks): `frontend/build.gradle.kts` `lint`/`test`/`buildFe`/`dev`, and root `build.gradle.kts` `dev`. All
    five are missing both `group` and `description`. 5×2=10 possible findings vs. "6 total" doesn't reconcile
    exactly either — same treatment as above (fix all 5 regardless; see Risks).
- **AC1/AC2 (deprecations/vulnerabilities) — no backend deprecation debt found:** `grep -rn "@Deprecated"
  backend/src/main/java` returns zero matches; `./gradlew :backend:compileJava` runs clean. Neither `build.gradle.kts`
  wires `-Xlint:deprecation`, which is itself a blind spot worth closing (Design §8) even though nothing is
  currently flagged.
- **AC2 — frontend `npm audit`: 10 vulnerabilities (7 moderate, 3 high), all transitive `devDependencies` of
  `@angular-devkit/build-angular`** (build tooling only — `image-size`/`qs`/`uuid`, none reachable from the shipped
  production bundle). `npm run lint` is already clean (zero warnings). No deprecated Angular/RxJS API usage found
  (`HttpClientModule`, `.toPromise()`, `ComponentFactoryResolver` — none present); a few components use the older
  decorator-style `@Input()` (e.g. `customer-search.component.ts`, `analytics-chart.component.ts`) which is **not**
  deprecated in Angular 22 (the newer signal-based `input()` is an alternative idiom, not a replacement for a
  removed API) — out of scope per Coding Standard #3, not a real AC2 gap.
- **AC5 — README audit, now grounded directly against the PDF (`docs/specs/sq_pe_assignment.pdf`), the
  highest-precedence source — not only `CLAUDE.md`'s simplicity standard as previously scoped.** The PDF's "How to
  provide results" section is explicit and literal: *"Please provide the project as a Git repository together with
  a README describing how to run the application, the architecture, the main design decisions, and any
  assumptions."* Its "Extras" line adds: *"Provide summary of LLMs of choice and short summary of agent
  instructions given."* This is a **named deliverable**, not a style nice-to-have — the root `README.md` is the
  PDF's actual grading artifact alongside the demo, so Design §11 below is rewritten to make the README's structure
  literally traceable to this checklist, ordered as the PDF lists it, with the architecture and agent-workflow
  content specifically made "easy to digest, high level" per direct user instruction.
  `backend/README.md` and `frontend/README.md` are each literally 2 lines (an H1 title, nothing else) — genuinely
  empty placeholders, but **supplementary** to the PDF's ask (the PDF asks for "a README," singular, at the repo
  root — these two are this project's own beyond-PDF convention, not a spec-mandated deliverable). Root
  `README.md`'s "Architecture" bullets are dense, chronologically-narrated per-phase walls of text (30+ lines per
  module bullet) that duplicate `docs/DECISIONS.md`/the phase docs rather than summarizing — the opposite of "high
  level." The root README's closing "LLMs & Agent Instructions (assignment deliverable)" section — which is
  literally the PDF's "Extras" ask — is still just a header + one italic placeholder sentence, never filled in.
- **AC6 — Javadoc gap, verified class-by-class (not taken from any single unverified pass) via a repo-wide scan for
  a javadoc block immediately above every top-level `public class`/`interface`:** already documented — `AnalyticsService`,
  `AiRiskAssessmentHistoryService`, `RiskScoringService`, `AiRiskAssessmentOrchestrator`, `RiskRuleService`,
  `RiskRuleRetrievalService`, `AssessmentHistoryRetrievalService`, `RiskAssessmentPersistenceService`,
  `PromptContextMapper`, `SecurityConfig`, `PiiGuardrailService`, `KeycloakRealmRoleConverter`,
  `AnalyticsConfigController`, `TransactionSpecifications`, `RiskFinalAssessmentSpecifications`, and every
  `@ConfigurationProperties` record already have one. **Genuinely missing** on non-trivial classes:
  `TransactionService`, `CustomerService`, `TransactionController`, `AnalyticsController`, `AiRiskAssessmentController`,
  `RiskRuleController`, `CustomerController`, `CardActivitySpecifications`, `PaymentActivitySpecifications`,
  `CryptoActivitySpecifications`, `TransactionMapper`. Entities, repositories, and DTOs/records are undocumented
  throughout every prior phase by established convention (self-describing field names) — not touched here, per
  Coding Standard #3 (no unnecessary comments).
- `TransactionTypeFilters` (`transaction/TransactionTypeFilters.java`) already exists as a record bundling every
  per-activity-type filter field, reused by both `TransactionService.findOverview` and `AnalyticsService
  .findTimeSeries`/`fetchRows` — establishing the exact "bundle repeated filter tuples into a record" pattern this
  plan extends to the *common* (non-type-specific) filter fields, which is what actually causes the S107 breaches.
- No `SecurityConfigTest` exists today — the `throws Exception`/CSRF-comment changes are behavior-invisible and
  covered only indirectly, by every `@WebMvcTest` slice that already `@Import(SecurityConfig.class)`.
- The three entity constructors' full-arg form (`CardActivity`/`PaymentActivity`/`CryptoActivity`) is **only ever
  called from tests** — no production code constructs these directly (rows come from Postgres via JPA's no-arg
  constructor + field reflection, or from the Flyway seed script's raw SQL) — so the entity-constructor refactor's
  entire blast radius is test fixture code, not application logic.

## Design

### 1. Shared filter records — the S107 fix for `TransactionService`/`AnalyticsService`/`*ActivitySpecifications`

All four flagged classes take exactly the same 7-value tuple — `(UUID customerId, TransactionStatus status, Instant
from, Instant to, BigDecimal minAmount, BigDecimal maxAmount, String currency)` — verified identical at every one of
their current call sites. `customerId` stays a separate leading parameter everywhere (it's the path-scoping
identifier, not a filter value, and keeping it explicit at the top of each signature preserves the
`customerService.requireExists(customerId)` call's readability); the remaining 6 fields become a new record:

**New `transaction/TransactionCommonFilters.java`:**
```java
public record TransactionCommonFilters(
    TransactionStatus status,
    Instant from,
    Instant to,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String currency) {}
```

Signature changes (all in the `transaction`/`analytics` packages, which already depend on each other today —
`AnalyticsService` already imports `TransactionTypeFilters`/`TransactionStatus`/`TransactionSpecifications` from
`transaction`, so this is not a new dependency direction):

| Class.method | Before | After |
|---|---|---|
| `TransactionService.findOverview` | 10 params | `(UUID customerId, ActivityType activityType, TransactionCommonFilters filters, TransactionTypeFilters typeFilters, Pageable pageable)` — 5 |
| `AnalyticsService.findTimeSeries` | 10 params | `(UUID customerId, ActivityType activityType, TransactionCommonFilters filters, TransactionTypeFilters typeFilters, Granularity granularity)` — 5 |
| `AnalyticsService.fetchRows` (private) | 9 params | `(UUID customerId, ActivityType activityType, TransactionCommonFilters filters, TransactionTypeFilters typeFilters)` — 4 |
| `CardActivitySpecifications.filter` | 11 params | `(UUID customerId, TransactionCommonFilters filters, String cardType, String merchantName, String mccCode, Boolean cardPresent)` — 6 |
| `PaymentActivitySpecifications.filter` | 11 params | `(UUID customerId, TransactionCommonFilters filters, String paymentMethod, String senderAccount, String receiverAccount, String receiverBankCountry)` — 6 |
| `CryptoActivitySpecifications.filter` | 11 params | `(UUID customerId, TransactionCommonFilters filters, String blockchain, String walletAddressFrom, String walletAddressTo, String exchangeName)` — 6 |

`TransactionSpecifications.common(...)` (7 params today — at, not over, the threshold) is **left untouched**: each
caller above unpacks `filters.status()`, `filters.from()`, etc. at the call site, exactly as they unpack
`customerId`/`status`/... today, just reading from the record instead of from loose locals. This avoids touching a
class SonarCloud doesn't flag, per Coding Standard #3.

**Controllers are unchanged in their public contract** — `TransactionController.findOverview` and
`AnalyticsController.findTimeSeries` keep every individual `@RequestParam`; each controller method's body simply
also builds a `new TransactionCommonFilters(status, from, to, minAmount, maxAmount, currency)` alongside the
`TransactionTypeFilters` it already builds, then passes both records to the service. Zero REST API/behavior change
(`PHASE_6.md` Scope "Out").

`AnalyticsService.findTimeSeries`'s own internal derivation of `effectiveFrom`/`effectiveTo` is unaffected in
substance, only in where the raw values come from: the method receives one `TransactionCommonFilters filters`
parameter (built by `AnalyticsController`, same as today's individual `from`/`to`/... arguments); it reads
`filters.from()`/`filters.to()` to derive `effectiveFrom`/`effectiveTo` exactly as today, and validates using the
two derived `LocalDate` values directly (`bound.isValid(fromDate, toDate)`) — validation never needs a
`TransactionCommonFilters` at all, today or after this change, so no record is built for it. Exactly **one** new
`TransactionCommonFilters` is constructed, immediately before calling `fetchRows`: same `status`/`minAmount`/
`maxAmount`/`currency` copied from `filters`, with `effectiveFrom`/`effectiveTo` substituted for `filters.from()`/
`filters.to()`. This is a direct, zero-behavior-change translation of the existing "compute effective range, then
filter with it" flow — not two records, just the one `fetchRows` already needs.

### 2. Shared filter record — the S107 fix for `AiRiskAssessmentHistoryService`/`RiskFinalAssessmentSpecifications`

Same pattern, independent domain (risk-assessment history, not transactions) — both flagged classes take the same
6-value tuple beyond `customerId`: `(UUID transactionId, RiskLevel riskLevel, Instant from, Instant to, BigDecimal
minScore, BigDecimal maxScore)`.

**New `risk/persistence/RiskAssessmentHistoryFilters.java`** (placed in `risk.persistence`, alongside `RiskLevel` —
`risk.api` already imports several `risk.persistence` types today, e.g. `RiskFinalAssessment`, `RiskLevel`,
`RiskFinalAssessmentSpecifications`, so this is not a new dependency direction):
```java
public record RiskAssessmentHistoryFilters(
    UUID transactionId,
    RiskLevel riskLevel,
    Instant from,
    Instant to,
    BigDecimal minScore,
    BigDecimal maxScore) {}
```

| Class.method | Before | After |
|---|---|---|
| `AiRiskAssessmentHistoryService.findHistory` | 8 params | `(UUID customerId, RiskAssessmentHistoryFilters filters, Pageable pageable)` — 3 |
| `RiskFinalAssessmentSpecifications.filter` | 8 params (an un-named 11th S107 offender — `PHASE_6.md`'s table names only `AiRiskAssessmentHistoryService`, but this sibling method breaches the same threshold and is the one `findHistory` calls; fixed as a natural consequence, not extra scope) | `(UUID customerId, RiskAssessmentHistoryFilters filters, RiskAssessmentProperties riskProperties)` — 3 |

`AiRiskAssessmentController.findHistory` keeps every individual `@RequestParam` (identical to the transaction case
above) and builds `new RiskAssessmentHistoryFilters(transactionId, riskLevel, from, to, minScore, maxScore)` before
calling the service — zero REST API/behavior change.

### 3. Entity-constructor parameter objects — `CardActivity`/`PaymentActivity`/`CryptoActivity`

All three subtype constructors repeat the same 6 base `Transaction` fields verbatim before their own
type-specific fields. A shared record removes the duplication and, for `CardActivity` specifically, is required
(not optional) to clear the threshold — bundling only the 6 base fields still leaves `CardActivity` at 1 + 7 = 8
params, still over.

**New `transaction/TransactionCoreFields.java`:**
```java
public record TransactionCoreFields(
    UUID transactionId,
    UUID customerId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    Instant createdAt) {}
```

- `Transaction`'s own protected 6-param constructor is **left untouched** (not flagged — exactly at the threshold,
  and it's `protected`, called only by the three subclasses below via `super(...)`, never directly).
- `CardActivity(TransactionCoreFields core, CardActivityDetails details)` — 2 params. New
  `transaction/card/CardActivityDetails.java`:
  ```java
  public record CardActivityDetails(
      String cardPan,
      String cardType,
      String merchantName,
      String mccCode,
      boolean cardPresent,
      String authorizationCode,
      String declineReason) {}
  ```
  Constructor body: `super(core.transactionId(), core.customerId(), core.amount(), core.currency(),
  core.status(), core.createdAt()); this.cardPan = details.cardPan(); ...` (unpacks both records — `Transaction`'s
  constructor signature itself is untouched, per above).
- `PaymentActivity(TransactionCoreFields core, String paymentMethod, String senderAccount, String receiverAccount,
  String receiverBankCountry)` — 5 params; no separate details record needed (4 type-specific fields, already under
  threshold once bundled with `core` — introducing one more record for 4 fields would be an unnecessary
  abstraction per Coding Standard #3).
- `CryptoActivity(TransactionCoreFields core, String blockchain, String walletAddressFrom, String walletAddressTo,
  String txHash, String exchangeName)` — 6 params; same reasoning, no details record.

**Blast radius is test-only** (Current State) — every call site across `CardActivityRepositoryTest`,
`PaymentActivityRepositoryTest`, `CryptoActivityRepositoryTest`, `TransactionRepositoryTest`,
`TransactionServiceTest`, `RiskFinalAssessmentSpecificationsTest`, `AiRiskAssessmentRepositoryTest`,
`AiRiskAssessmentWireMockReplayTest`, `AnalyticsServiceIntegrationTest`, and `AnalyticsServiceTest` gets updated to
construct `new TransactionCoreFields(...)` (+ `new CardActivityDetails(...)` for card fixtures) instead of the flat
argument list — mechanical, one call-site pattern per file, no test assertion logic changes.

### 4. `java:S1192` — extract the two duplicated literals

- `SecurityConfig.java`: `private static final String ADMIN_ROLE = "ADMIN";` (declared alongside the existing
  `log` field); replace all three `.hasRole("ADMIN")` calls with `.hasRole(ADMIN_ROLE)`.
- `RiskFinalAssessmentSpecifications.java`: `private static final String RISK_SCORE = "riskScore";`; replace all
  six `root.get("riskScore")` occurrences (the `LOW`/`MEDIUM`/`HIGH` switch branches plus the `minScore`/`maxScore`
  predicates) with `root.get(RISK_SCORE)`.

Both are pure internal refactors — no signature or behavior change, no test updates needed.

### 5. `java:S4502` + `java:S112`/`S1130` — `SecurityConfig` hardening

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
  try {
    http.csrf(
            // Stateless, bearer-JWT-only resource server (SessionCreationPolicy.STATELESS, no
            // cookies/sessions) — CSRF protects against an ambient credential the browser attaches
            // automatically (a session cookie), which never applies here.
            AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ... rest of the chain, unchanged ...
    return http.build();
  } catch (Exception e) {
    throw new IllegalStateException("Failed to build the security filter chain", e);
  }
}
```
`http.build()` (`HttpSecurity`'s `SecurityBuilder<O>` contract) is the only genuinely checked-exception-throwing
call in the method — catching it here and rethrowing as an unchecked `IllegalStateException` removes the leaked
generic `throws Exception` from the public bean method's signature (the actual fix S112/S1130 wants, not a
suppression) without changing what happens when Spring builds the chain at startup (a build failure still fails
context refresh either way — before, as a checked `Exception` Spring's own bean-creation machinery would wrap
anyway; after, as this explicit unchecked wrapper). No test currently asserts on this method's `throws` clause or
catches anything from it, so no test changes.

### 6. `java:S2629` — guard the one eager-argument `log.debug` call

`AiRiskAssessmentOrchestrator.java`, immediately after the existing `log.info("AI risk assessment completed: ...")`
block:
```java
if (log.isDebugEnabled()) {
  log.debug(
      "AI risk assessment rule matches: transactionId={}, matches={}",
      transaction.transactionId(),
      result.ruleMatches());
}
```
No test asserts on this log line; no behavior change (DEBUG output is identical when the level is actually
enabled).

### 7. Test-quality nit — `S2925` (`Thread.sleep` in a test)

`AiRiskAssessmentOrchestratorTest.modelCallTimeoutEmitsFailedWithoutPersisting` needs the stubbed `aiClient.assess(...)`
call to genuinely still be running past the orchestrator's real 50ms timeout — an `Awaitility`-style poll doesn't
fit (there's nothing to poll for; the async thread must simply not have returned yet). Replace the sleep with an
indefinite block that achieves the same effect without a raw `Thread.sleep`:
```java
when(aiClient.assess(PROMPT))
    .thenAnswer(
        invocation -> {
          new CountDownLatch(1).await(); // blocks forever; the 50ms future.get(...) times out first
          return new ModelAssessmentResult(List.of(), "findings", "recommendations");
        });
```
`Mockito.Answer#answer` declares `throws Throwable`, so `CountDownLatch.await()`'s checked `InterruptedException`
needs no extra handling. Test assertions (`verify(persistenceService, never())...`, the 6-event capture, the
`FAILED` stage) are unchanged — same behavior, just no `Thread.sleep`.

**The other 13 test-quality nits (`S6068`/`S5853`/`S5778`) are not resolved by this plan** — see Risks below;
flagged for verification against the live SonarCloud dashboard at implement time rather than guessed at.

### 8. Gradle Kotlin-DSL task metadata (`S6626`/`S6629`)

Add `group` and `description` to all five custom task registrations (covers the "6" count regardless of exactly
how SonarCloud attributes it per-task — see Risks):

```kotlin
// frontend/build.gradle.kts
tasks.register<NpmTask>("lint") {
  group = "verification"
  description = "Runs ESLint over the Angular app (npm run lint)."
  ...
}
tasks.register<NpmTask>("test") {
  group = "verification"
  description = "Runs Karma/Jasmine unit tests with coverage (npm run test:ci)."
  ...
}
tasks.register<NpmTask>("buildFe") {
  group = "build"
  description = "Builds the production Angular bundle (npm run build)."
  ...
}
tasks.register<NpxTask>("dev") {
  group = "application"
  description = "Runs Postgres, the backend, and this frontend together for local development."
  ...
}
```
```kotlin
// build.gradle.kts (root)
tasks.register("dev") {
  group = "application"
  description = "Delegates to :frontend:dev to run the full local stack from one terminal."
  dependsOn(":frontend:dev")
}
```

### 9. `-Xlint:deprecation` — close the backend deprecation blind spot (AC1)

No deprecated API usage was found, but neither `build.gradle.kts` surfaces `-Xlint:deprecation`, so javac's default
deprecation-note suppression means a future deprecated call wouldn't visibly warn. Add to `backend/build.gradle.kts`:
```kotlin
tasks.withType<JavaCompile> {
  options.compilerArgs.add("-Xlint:deprecation")
}
```
Expected to produce zero new warnings today (verified by the current clean `compileJava` run); this is forward-looking
hardening for AC1's "free of deprecations" and AC4's "CI hardened to perform code analysis" spirit, not a fix for
an existing issue.

### 10. Frontend `npm audit` — devDependency-only findings (AC2)

All 10 findings are transitive `devDependencies` of `@angular-devkit/build-angular` (build tooling — `image-size`,
`qs`, `uuid` — never in the shipped production bundle). Run `npm audit fix` (no `--force`) at implement time and
re-audit:
- If it resolves cleanly within semver ranges, keep the result — no `package.json` version pins need bumping past
  what `audit fix` chooses, and `npm run lint`/`npm test`/`npm run build` must still pass afterward (regression
  check, not just an audit-count check).
- If a residual finding still needs `--force` (a breaking major bump to `@angular-devkit/build-angular` or the
  Angular CLI itself), **do not force it** — that risks breaking the toolchain for a dev-only, non-shipped
  dependency chain outside this phase's "no behavior change" scope. Instead, record it as an accepted residual risk
  (flagged to the reviewer, not silently resolved) — a candidate for a new `docs/DECISIONS.md` entry alongside D22's
  precedent of "flag and record, don't force a fix that isn't worth its own risk."

### 11. Documentation — root README restructured around the PDF's literal deliverable checklist (AC5)

The PDF names four required README components, in this order, plus one "Extras" component — Design §11 makes the
root `README.md`'s own top-level section order **literally match that list**, so a reviewer skimming for "did they
cover X" finds each item exactly where the assignment says to look for it, without hunting through prose.

1. **"How to Run"** — already exists, already concise; left as-is (not part of the AC5 gap — this section was
   already short/accurate, verified by reading it: prerequisites, `./gradlew dev`, `./gradlew check`, health-check
   URLs, AI-provider switching).
2. **"Architecture" — rewritten for "easy to digest, high level"**, replacing the current 30+-line-per-module
   phase-by-phase narrative entirely (not trimmed-in-place — the chronological "Phase 3 adds X, Phase 4 EXT does Y"
   structure itself is the problem, not just its length): a short paragraph per module (`backend`/`frontend`/
   `local-environment`) — 3-5 sentences each, current-state only ("what exists," never "how it got there") — plus
   **one small Mermaid component diagram** showing the actual moving pieces and their interactions at a glance:
   Angular SPA → Spring Boot REST API → PostgreSQL, with Keycloak (auth) and the AI provider (OpenAI/Anthropic via
   Spring AI, WireMock-stubbed locally) as the two other boxes the API talks to. GitHub renders Mermaid natively in
   Markdown, so this adds zero tooling/build surface — it's README content, not application code, so it doesn't
   touch `PHASE_6.md`'s "no changes of behavior" scope at all. Every fact currently *only* stated in the existing
   Architecture section (not duplicated in `docs/DECISIONS.md` or a phase doc) is preserved somewhere in the
   rewrite or in the per-module `backend/README.md`/`frontend/README.md` (§ below) — this is a restructure, not a
   content deletion.
3. **"Key Design Decisions" — new section** (the PDF asks for this as its own named item, distinct from
   "architecture"; today it's only a single passing link at the end of the Architecture section, not addressed on
   its own terms). A short, curated list — one line each, decision + why in a handful of words — headlining the
   decisions most material to a reviewer assessing the approach, **not** all 24 `docs/DECISIONS.md` entries:
   candidates are D1 (Angular over React), D2 (OAuth2/Keycloak login), D3 (SSE for live AI progress), D4
   (WireMock-stubbed LLM for the offline demo), D6 (two-table risk-assessment model), D17 (RAG as structured DB
   filtering, not vector search), D19 (multi-provider AI selection), D23 (`risk_level` computed on read). Ends with
   one line linking to the full `docs/DECISIONS.md` log for every other decision. (Exact final wording/selection is
   an implementation-time judgment call within this candidate set — the point is "headline the handful that matter
   to a reviewer," not reproduce the log.)
4. **"Assumptions"** — today this is `### Assumptions`, a level-3 subsection nested *under* `## Architecture`
   (verified: `README.md` line 156, directly inside the Architecture block, before `## Implementation Journey`).
   **Promote it to its own top-level `## Assumptions` section and move it to immediately after the new `##
   Key Design Decisions` section** (item 3 above) — not just "trim in place" — so the rendered document's actual
   heading order is `How to Run → Architecture → Key Design Decisions → Assumptions → Implementation Journey →
   LLMs & Agent Instructions`, matching both the PDF's literal order and this list's own numbering. Content is
   lightly retoned for consistency with the new concise Architecture section; no fact is removed (same "preserve
   every fact" rule as §2 above).
5. **"Extras" — the "LLMs & Agent Instructions" section, filled in** (today: header + one placeholder sentence,
   never written). Two short parts, per the PDF's own two-part ask:
   - **"Summary of LLMs of choice":** which providers/models this project actually integrates (OpenAI + Anthropic
     via Spring AI), that it runs offline against a WireMock-stubbed LLM by default so the demo needs no API key
     (D4), and how an operator switches to a real provider (`app.ai.provider`, D19). A few sentences, not a
     restatement of `local-environment/wiremock/README.md`'s full record-mode mechanics — link there for detail.
   - **"Short summary of agent instructions given"** — this is the direct target of the user's "AI Agentic workflow
     ... should be easily described" instruction. Keep it genuinely short and scannable (the PDF says "short
     summary"): name the tool (Claude Code CLI) and the governing loop in one compact list — `CLAUDE.md` (the
     source-of-truth precedence chain) drives a per-phase `Plan → Review Plan → Implement → Review Code → Complete`
     cycle via the custom slash commands in `.claude/commands/`, each phase recorded as `docs/development/PHASE_N.md`
     + `PHASE_N_PLAN.md`. This is a **short, high-level pointer**, not a re-explanation — the README's own
     pre-existing "CLI Interactive Loop" section (further up, under "Implementation Journey") already has the full
     six-step mechanical detail; this Extras section should read like an executive summary that links down to it,
     not duplicate it.

**`backend/README.md`/`frontend/README.md`** (still planned, unchanged from the prior draft of this section —
these remain genuinely empty 2-line placeholders regardless of the PDF re-grounding above, and fixing them is still
part of AC5's "filled in where empty"): a short, real README each — `backend/README.md` covers what it is (Spring
Boot 4.1 REST API + Spring Data JPA/Flyway/PostgreSQL + Spring AI), how to run it standalone (`../gradlew
:backend:bootRun` with the `local` profile), where the API docs live (`springdoc-openapi` at `/swagger-ui.html`);
`frontend/README.md` covers what it is (Angular 22 + Material), `npm start`/`npm test`/`npm run lint`/`npm run
build`, and the `proxy.conf.json` backend-proxy note. Both end with a one-line pointer back to the root README for
the full picture — these two are supplementary convenience docs, not where the PDF's checklist needs to be
satisfied (that's the root README, per the "these are supplementary" note in Current State above).

### 12. Documentation — class-level Javadoc where missing (AC6)

Add a concise (1–3 sentence) class Javadoc to each of the following, matching the existing house style (see
`PiiGuardrailService`/`RiskScoringService`/`RiskRuleRetrievalService` as the pattern to replicate — state the
class's role and any non-obvious constraint, not a restatement of its name):

- `transaction/TransactionService.java`
- `customer/CustomerService.java`
- `transaction/TransactionController.java`
- `analytics/AnalyticsController.java`
- `risk/api/AiRiskAssessmentController.java`
- `risk/api/RiskRuleController.java`
- `customer/CustomerController.java`
- `transaction/dto/TransactionMapper.java`
- `transaction/card/CardActivitySpecifications.java`, `transaction/payment/PaymentActivitySpecifications.java`,
  `transaction/crypto/CryptoActivitySpecifications.java` (added while these three files are already being edited
  for Design §1's signature change — natural, not extra scope)

Entities, repositories, and DTOs/records are **not** touched — self-describing by field name, consistent with
every prior phase's own convention (Coding Standard #3: avoid unnecessary comments).

## File inventory

**Backend — new:**
`transaction/TransactionCommonFilters.java`; `transaction/TransactionCoreFields.java`;
`transaction/card/CardActivityDetails.java`; `risk/persistence/RiskAssessmentHistoryFilters.java`.

**Backend — modified (production code):**
`transaction/TransactionService.java`; `transaction/TransactionController.java`;
`analytics/AnalyticsService.java`; `analytics/AnalyticsController.java`;
`transaction/card/CardActivitySpecifications.java`; `transaction/payment/PaymentActivitySpecifications.java`;
`transaction/crypto/CryptoActivitySpecifications.java`; `transaction/card/CardActivity.java`;
`transaction/payment/PaymentActivity.java`; `transaction/crypto/CryptoActivity.java`;
`risk/api/AiRiskAssessmentHistoryService.java`; `risk/api/AiRiskAssessmentController.java`;
`risk/api/RiskRuleController.java`; `risk/persistence/RiskFinalAssessmentSpecifications.java`;
`config/SecurityConfig.java`; `risk/engine/AiRiskAssessmentOrchestrator.java`; `customer/CustomerController.java`;
`customer/CustomerService.java`; `transaction/dto/TransactionMapper.java`; `backend/build.gradle.kts`.

**Backend — modified (tests, mechanical call-site updates only, no assertion-logic changes):**
`transaction/TransactionServiceTest.java`; `transaction/TransactionControllerTest.java`;
`analytics/AnalyticsServiceTest.java`; `analytics/AnalyticsServiceIntegrationTest.java`;
`analytics/AnalyticsControllerTest.java`; `transaction/card/CardActivityRepositoryTest.java`;
`transaction/payment/PaymentActivityRepositoryTest.java`; `transaction/crypto/CryptoActivityRepositoryTest.java`;
`transaction/TransactionRepositoryTest.java`; `risk/persistence/RiskFinalAssessmentSpecificationsTest.java`;
`risk/persistence/AiRiskAssessmentRepositoryTest.java`; `risk/engine/AiRiskAssessmentWireMockReplayTest.java`;
`risk/api/AiRiskAssessmentControllerTest.java`;
`risk/api/AiRiskAssessmentHistoryServiceTest.java`; `risk/engine/AiRiskAssessmentOrchestratorTest.java` (S2925 fix).

**Frontend:** no source changes planned beyond `npm audit fix` re-locking `package-lock.json` (Design §10) —
verify at implement time whether anything actually changes.

**Root/build config:** `build.gradle.kts` (root — dev task metadata); `frontend/build.gradle.kts` (4 tasks'
metadata); `README.md` (root — Architecture trim + LLMs section fill-in); `backend/README.md`; `frontend/README.md`.

**Docs:** `docs/development/PHASE_6.md` (`Status` → `IMPLEMENTED` at `/implement` time), this plan file.
Possible new `docs/DECISIONS.md` entry only if Design §10's `npm audit fix` leaves a residual finding that can't be
resolved without `--force` — not written speculatively here; added only if that situation is actually hit.

## Test plan → Acceptance-criteria mapping

| `PHASE_6.md` AC | Coverage |
|---|---|
| AC1 — Java free of deprecations/vulnerabilities | `-Xlint:deprecation` wired (§9), zero `@Deprecated` usage confirmed; `./gradlew check` (Spotless + tests) is the regression proof for every S107/S1192/S112/S2629 fix |
| AC2 — TypeScript free of deprecations/vulnerabilities | `npm audit fix` run and re-verified (§10); `npm run lint` stays clean; `npm test` stays green |
| AC3 — project builds and passes all tests | `./gradlew check` and `npm test` run at `/implement` time before marking `IMPLEMENTED`, per every parameter-object refactor's call-site updates landing in the same change |
| AC4 — CI hardened to perform code analysis | Already satisfied (Current State) — `-Xlint:deprecation` (§9) is incremental hardening on top |
| AC5 — README simplified/filled in | Manual read-through of the root README against the PDF's literal checklist (§11): "How to Run," "Architecture" (high-level + diagram), "Key Design Decisions," "Assumptions," and "LLMs & Agent Instructions" each present, in that order, with no empty sections and no stale claims; `backend/README.md`/`frontend/README.md` no longer bare titles |
| AC6 — Javadoc where needed | The 11 classes named in §12 each get a class-level Javadoc block; spot-checked against the existing house style for consistency |
| AC7 — zero OPEN/CONFIRMED SonarCloud issues | Verified live against the SonarCloud dashboard/API at `/complete` time (not unit-testable, per `PHASE_6.md` Testing Scope) — every rule in §1–§8 above is a real code fix, not a suppression, so a repeat scan should show each as resolved |
| AC8 — Sonar CI step runs automatically | Already satisfied (Current State) — no work |
| AC9 — backend+frontend coverage on SonarCloud | Already satisfied (Current State) — no work |
| AC10 — README SonarCloud badges | Already satisfied (Current State) — no work |
| AC11 — no `V*` migration SQL changed | No migration files are touched anywhere in this plan — confirmed by the file inventory above containing zero `db/migration` entries |

## Risks / Open Questions

- **AC5's README work is re-scoped, not re-invented, by reading the PDF directly.** The prior draft of this plan
  treated the README work as a `CLAUDE.md`-simplicity cleanup; re-reading `docs/specs/sq_pe_assignment.pdf`'s "How
  to provide results"/"Extras" sections (the PDF is the highest-precedence source, per `CLAUDE.md`) shows the root
  README is a **named assignment deliverable** with four required components plus an "Extras" pair, in a specific
  order. This isn't a contradiction to flag-and-stop on (nothing in the PDF conflicts with what was already
  planned) — it's a precision correction: Design §11 now structures the README to make each of the PDF's five
  required components literally locatable as its own section, rather than only satisfying them incidentally through
  general "simplification."
- **Test-quality nit count gap (14 claimed, 1 confirmed by static grep).** `S6068`/`S5853`/`S5778`'s textbook
  trigger patterns (JUnit4 remnants, `assertTrue(x.equals(y))`, multi-statement `assertThrows` lambdas) do not
  exist anywhere in `backend/src/test/java` — the suite is 100% AssertJ, and `assertThrows` isn't used at all. This
  either means the phase doc's "14" count is stale (e.g. carried over from before a prior phase's test cleanup), or
  these rules also fire on AssertJ-idiom equivalents this static search wouldn't catch by text pattern alone.
  **Action:** pull the literal file:line list from the live SonarCloud dashboard/API at implement time and fix
  whatever it actually shows, rather than inventing speculative AssertJ "equivalents" now that might not match what
  SonarCloud is really flagging.
- **Gradle task-metadata count gap (6 claimed, 5 tasks × 2 missing properties = up to 10 possible).** Fixing all 5
  tasks with both `group` and `description` covers every plausible interpretation of "6" (whether that's a Sonar
  dedup quirk, or a per-task single combined finding) — no separate action needed regardless of which
  reconciliation is correct, but flagged so the discrepancy isn't silently assumed away.
- **`RiskFinalAssessmentSpecifications.filter`'s own 8-param breach (§2) isn't named in `PHASE_6.md`'s table** —
  only `AiRiskAssessmentHistoryService` is. Since `PHASE_6.md` states SonarCloud itself is the scope authority, and
  this method is `findHistory`'s sole downstream caller, fixing it is an unavoidable consequence of fixing
  `findHistory`'s own signature (a parameter object has to be threaded through), not scope creep.
- **`npm audit fix`'s actual effect is unknown until run** — Design §10 pre-commits to *not* forcing a breaking
  fix if one is needed, since this phase's explicit "no behavior change" scope covers build tooling stability, not
  just runtime behavior. If a real residual finding survives a non-forced `audit fix`, it needs a `docs/DECISIONS.md`
  entry recording that as a deliberate, reviewed choice — flagged for `/review PHASE_6 plan` rather than resolved
  here, since accepting a known vulnerability (even a dev-only one) is exactly the kind of beyond-the-PDF call D22
  set precedent for recording explicitly.
- **Parameter-object refactors are wide-reaching but mechanical** — every call site is either (a) a controller
  building the new record from its unchanged individual `@RequestParam`s, or (b) a test constructing the new
  record instead of passing flat arguments. No test's *assertions* change, only its *call syntax* — the actual
  regression risk is a missed call site, not a logic error, which `./gradlew check` (Spotless + full test suite)
  catches directly (a missed call site is a compile error, not a silent behavior change).
