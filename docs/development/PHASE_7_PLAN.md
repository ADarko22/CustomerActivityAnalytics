# Phase 7 Implementation Plan — WireMock Stub Matching Fix & Orange Table Restyle

**Status:** PLANNED
**Phase definition:** `docs/development/PHASE_7.md`

Blueprint for (1) making the offline Anthropic WireMock demo actually work by default, and (2) pushing the three
Angular Material data tables further into the app's already-adopted orange palette. Read alongside `CLAUDE.md`
(Coding Standard #3 — avoid unnecessary abstraction), `docs/DECISIONS.md` D4/D18/D19/D26 (WireMock record-and-replay,
provider selection, and the new D26 supersession of D19's default-preservation rationale), and `PHASE_7.md`'s own
Scope/Functional Requirements.

## Current State (verified)

**WireMock — significant manual progress already made outside this plan, with two concrete bugs to fix, not the
"generalize 15 exact-body matchers" work `PHASE_7.md` originally framed as the open question:**

- `backend/src/main/resources/application.yml:56` already reads `provider: ${AI_PROVIDER:anthropic}` — the default
  is already flipped. No code change needed here; the design-time gap was that this contradicted
  `docs/DECISIONS.md` D19's explicit "preserve the long-standing default" rationale for OpenAI without any
  recorded resolution — now resolved by the new **D26** entry (`docs/DECISIONS.md`), which supersedes that part
  of D19 and records why the default changes to `anthropic` in this phase. `OpenAiRiskAssessmentAiClient`'s
  `@ConditionalOnProperty(..., matchIfMissing = true)` is left as-is per D26's Consequence — it only matters if
  `app.ai.provider` is removed from configuration entirely, which this phase does not do.
- The 15 stubs from the `f4285ac` recording session have already been renamed from `mapping-v1-messages-*.json` /
  `body-v1-messages-*.json` to `anthropic-messages-<suffix>.json` in both `mappings/` and `__files/` (confirmed via
  `git status`, shown as `R` renames). Their `request.bodyPatterns[0].equalToJson` content is **unchanged** — each
  still matches only the exact transaction/history JSON captured at recording time (confirmed identical `system`
  prompt + `model` across samples, differing only in the dynamic `messages[0].content`). **Bug:** each mapping's
  `response.bodyFileName` still points at the **old**, now-nonexistent name (e.g.
  `mappings/anthropic-messages-3AWDA.json` → `response.bodyFileName: "body-v1-messages-3AWDA.json"`, but the actual
  file on disk is now `__files/anthropic-messages-3AWDA.json`). Any request that *does* happen to match one of
  these 15 stubs currently gets a WireMock file-not-found error, not its recorded response — this is broken today,
  independent of the 404-on-new-requests problem.
- A catch-all fallback mapping has also already been re-added at `mappings/anthropic-messages.json` (broad match:
  `POST /v1/messages`, no body constraint) — matching the pre-4_EXT_2 design. **Bug:** its
  `response.bodyFileName: "anthropic-messages-response.json"` points at a file that doesn't exist; the actual
  fallback body was added as `__files/anthropic-messages.json` (confirmed present, well-formed: a generic
  `ModelAssessmentResult`-shaped JSON payload wrapped in the same Anthropic Messages envelope
  `AiRiskAssessmentWireMockReplayTest.java`'s `WhenProviderIsAnthropic.stubChatCompletion` builds
  `{id, type, role, model, content:[{type:text,text:...}], stop_reason, usage}`). Its `model` field is
  `claude-sonnet-4-5`, inconsistent with the now-default `claude-haiku-4-5` — cosmetic, not functional.
- `local-environment/wiremock/README.md` has already been partly updated (bold provider labels; Anthropic entry's
  link **text** now correctly says `anthropic-messages-response.json`) but its link **target**
  (`__files/anthropic-messages.json`) doesn't match that text, and the "Both mappings are always present" framing
  is now stale (Anthropic has 16 mappings — 15 specific + 1 catch-all — not 1).
- No `priority` field is set on the catch-all mapping — at default priority (WireMock's implicit tie-break is not
  something to rely on for 16 co-registered mappings), it should be explicit rather than assumed.
- **Why the 15 specific stubs are not being generalized/pruned (resolving `PHASE_7.md`'s open "Key decision"):**
  WireMock's `equalToJson` has no partial/field-masking mode, so the only way to make an individual stub tolerant
  of a different `transactionId`/history is to replace it with `matchesJsonPath` constraints on just the stable
  fields (`$.system`, `$.model`). But since **all 15 stubs share the identical stable `system` prompt and
  `model`**, doing that to all of them would make them indistinguishable — only one could ever be reached, per
  WireMock's own equal-priority tie-break, making the other 14 dead weight. The already-restored catch-all
  (once its bug is fixed and given a lower `priority`) achieves the actual goal — no request ever 404s — without
  discarding the 15 recorded fixtures or fighting WireMock's matcher model. This plan fixes the catch-all and the
  15 stubs' broken `bodyFileName`s in place; it does not touch any `bodyPatterns`.
- `docs/development/PHASE_6_PLAN.md`'s WireMock scope was unrelated (CI/Sonar only) — no conflict.
- **Security finding from code review, now fixed:** all 15 mapping files' `response.headers` carried the real
  `anthropic-organization-id` and `anthropic-workspace-id` values captured verbatim during the real recording
  session — account-identifying values that shouldn't be checked into the repo (or, as a first review round
  caught, restated verbatim in this plan document either). Redacted to `org_offline-demo`/`wrkspc_offline-demo`
  in all 15 files (mechanical, same transformation per file); no other field touched. The commit that first
  introduced these files (`f4285ac`) was not yet pushed to `origin/main` at the time this was caught, so no
  already-public history contains the real values.

**Frontend — verified current styling:**

- `frontend/src/styles.scss` already themes via Angular Material M3's `mat.$orange-palette` and defines one
  shared global class, `th.mat-mdc-header-cell.table-header-cell`, applied identically by all three table
  components — background `var(--mat-sys-surface-container, #fdfaf7)` (near-white) with only a 2px
  `var(--mat-sys-primary, #8a4a00)` bottom border. No alternating row treatment or table-container rounding
  exists anywhere.
- Three components, no shared table wrapper/component — each defines its own `.scss`:
  - `frontend/src/app/features/transactions/transaction-table/` — `<table mat-table class="transaction-table"
    multiTemplateDataRows>`; two `<tr>` per data row (`tr.transaction-row` + hidden `tr.detail-row` for
    expand-to-detail).
  - `frontend/src/app/features/risk-assessment/risk-assessment-history-table/` — same two-row-per-item shape
    (`tr.assessment-row` + `tr.detail-row`).
  - `frontend/src/app/features/administration/risk-rules-table/` — single `<tr mat-row>` per data row, no
    expand/detail.
- Because two of the three tables render a hidden (`display:none`) detail `<tr>` after every data row, CSS
  `:nth-child`/`:nth-of-type` alternation would count those hidden rows and stripe incorrectly — the fix must key
  off the `matRowDef` template's own `index`, not DOM position.
- Each table component already has a `.component.spec.ts` (Jasmine/Karma) — used for the new class-presence
  assertions in the test plan below.

## Design

### 1. Fix the 15 specific stubs' `bodyFileName` (mechanical, no `bodyPatterns` change)

In each of the 15 `local-environment/wiremock/mappings/anthropic-messages-<suffix>.json` files, change:

```diff
-  "bodyFileName" : "body-v1-messages-<suffix>.json",
+  "bodyFileName" : "anthropic-messages-<suffix>.json",
```

(one line per file, same transformation, `<suffix>` matching the file's own name — e.g. `3AWDA`, `3OQsW`, ...
`zJr9w`). No other field changes. This is what makes replaying one of the 15 original demo scenarios actually
return its recorded content instead of a WireMock file-not-found error.

### 2. Fix the catch-all mapping + body file name mismatch, and set explicit priority

- Rename `local-environment/wiremock/__files/anthropic-messages.json` →
  `local-environment/wiremock/__files/anthropic-messages-response.json` (matches what
  `mappings/anthropic-messages.json`'s `response.bodyFileName` already expects — no need to touch the mapping's
  `bodyFileName` field, only the body file's name on disk).
- Add an explicit `"priority": 10` to `local-environment/wiremock/mappings/anthropic-messages.json`, so it only
  ever serves when none of the 15 specific stubs (implicit default priority) match — deterministic fallback
  behavior instead of relying on an unspecified tie-break among 16 co-registered mappings.
- Optionally align the fallback body's `"model"` field from `claude-sonnet-4-5` to `claude-haiku-4-5` for
  consistency with the new default model — cosmetic, does not affect parsing (the app does not validate the
  response's echoed `model` field).

### 3. Fix `local-environment/wiremock/README.md`

- Update the Anthropic bullet's link target from `__files/anthropic-messages.json` to
  `__files/anthropic-messages-response.json` (matching the link text, and the rename in Design §2) — this also
  resolves the IDE's "Cannot resolve file" warnings.
- Replace "Both mappings are always present, so switching `app.ai.provider`... never requires touching WireMock
  config" with an accurate description: Anthropic now has 16 mappings (15 specific recorded scenarios + 1
  generic fallback, `priority: 10`); OpenAI still has exactly 1. Note that the fallback is what guarantees any
  Anthropic request gets a 200, while the 15 specific stubs let the three original demo scenarios replay their
  exact original findings/recommendations.

### 4. Shared table styling — `frontend/src/styles.scss`

Add two new global rules alongside the existing `.table-header-cell` block (same rationale comment style — three
separately-encapsulated components need identical treatment):

```scss
// Stronger orange header — supersedes the existing subtle-surface + border-only look for the same
// three tables `.table-header-cell` targets.
th.mat-mdc-header-cell.table-header-cell {
  background-color: var(--mat-sys-primary-container, #ffdcc2);
  color: var(--mat-sys-on-primary-container, #2a1800);
  font-weight: 600;
  border-bottom: 2px solid var(--mat-sys-primary, #8a4a00);
}

// Alternating row background — keyed off the *data* index bound by each table's own `matRowDef`
// (`app-alt-row`), not DOM position: two of the three tables render a hidden `display:none` detail
// <tr> after every data row for expand-to-detail, which would throw off :nth-child/:nth-of-type.
tr.mat-mdc-row.app-alt-row {
  background-color: var(--mat-sys-surface-container-low, #fff3e0);
}

// Rounded, slightly elevated table container — applied to the same wrapping element as the
// existing per-table width class (`.transaction-table` / `.history-table` / `.risk-rules-table`).
table.mat-mdc-table {
  border-radius: 12px;
  overflow: hidden;
}
```

The exact literal fallback hex values above are starting points; verify actual contrast against the live
M3-generated `--mat-sys-primary-container`/`--mat-sys-on-primary-container`/`--mat-sys-surface-container-low`
values via `getComputedStyle` during implementation (same verification approach the existing
`.table-header-cell` comment documents was used originally) and adjust the literal fallbacks if the generated
tokens differ meaningfully.

### 5. Wire the new classes into each table template

`table.mat-mdc-table` already matches Angular Material's own generated class on every `<table mat-table>`, so no
template change is needed for the header/rounding rules. The alternating-row class needs an index binding on
each table's *primary* data row (not the hidden detail row):

- `transaction-table.component.html:157-162` and `risk-assessment-history-table.component.html:127-132` — add
  `let i = dataIndex` (not `index`) to the existing `*matRowDef`, plus `[class.app-alt-row]="i % 2 === 1"` on
  `tr.transaction-row`/`tr.assessment-row`. **`dataIndex`, not `index`, is required here**: both tables use
  `multiTemplateDataRows` (one `matRowDef` for the primary row, a second for the hidden `expandedDetail` row),
  and Angular CDK's `index` context variable counts every *rendered* row across *all* row templates — so with
  two `<tr>` per data item it increments by 2 per item (0, 2, 4, ...), never landing on an odd value and
  silently disabling the stripe on every row past the first. `dataIndex` is CDK's dedicated data-array index,
  unaffected by how many templates render per item (confirmed via `@angular/cdk/types/table.d.ts`'s
  `RowContext<T>` — `dataIndex` is a distinct field from `index`/`renderIndex`). Caught by the new Karma
  assertions below, which is exactly why they were worth adding as automated coverage rather than relying on a
  visual check alone.
- `risk-rules-table.component.html:133` — single `<tr mat-row *matRowDef="let row; columns: displayedColumns">`,
  no `multiTemplateDataRows` — plain `let i = index` and `[class.app-alt-row]="i % 2 === 1"` is correct here
  (`index` and `dataIndex` coincide when there's exactly one template per data row).

The hidden `tr.detail-row` in the first two tables is left unstyled by `app-alt-row` — it's `display:none` except
when expanded, and when expanded it should read as a continuation of its parent row, not get its own stripe.

### 6. New automated test against the real `local-environment/wiremock/` fixtures (AC1/AC2)

`AiRiskAssessmentWireMockReplayTest.java` cannot cover this: both its nested scenarios boot an isolated,
dynamic-port `WireMockExtension` with one hand-built always-matching stub, and never point at
`local-environment/wiremock/`'s actual `mappings/`/`__files/` directories — it's the wrong tool for regression-
testing *this* fixture set. Add a new, plain JUnit 5 test (no Spring context needed — this is pure HTTP against a
WireMock instance loaded from disk), e.g.
`backend/src/test/java/io/github/adarko22/customeractivityanalytics/risk/engine/AiRiskAssessmentLocalWireMockFixturesTest.java`:

```java
class AiRiskAssessmentLocalWireMockFixturesTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort()
              .usingFilesUnderDirectory("../local-environment/wiremock"))
          .build();

  @Test
  void replayingAnOriginalRecordedScenarioReturnsItsOwnRecordedContent() {
    // Read one recorded mapping's request.bodyPatterns[0].equalToJson verbatim (e.g.
    // mappings/anthropic-messages-3AWDA.json) and POST it to wireMock.baseUrl() + "/v1/messages".
    // Assert HTTP 200 and that the response body contains that scenario's own recorded findings text
    // (proves the bodyFileName fix in Design §1 — without it, this currently 404/500s).
  }

  @Test
  void aNovelTransactionStillGetsTheGenericFallbackInsteadOf404() {
    // Build a request body with the same stable "system"/"model" fields as the recorded scenarios but a
    // different transactionId/history (i.e. one that matches none of the 15 bodyPatterns). POST it and
    // assert HTTP 200, with content distinct from any of the 15 specific recorded findings (proves the
    // catch-all + priority fix in Design §2, not a relaxed per-stub matcher, is what prevents the 404).
  }
}
```

Relative path `../local-environment/wiremock` resolves correctly from the `backend/` module's working directory
during test execution (verified: `local-environment/wiremock` exists one level up from `backend/`). This directly
exercises the exact files `./gradlew dev` loads, closing the gap `PHASE_7.md`'s original Testing Scope wording
assumed `AiRiskAssessmentWireMockReplayTest` could cover.

## File inventory

| File | Change |
|---|---|
| `local-environment/wiremock/mappings/anthropic-messages-*.json` (15 files) | Fix `response.bodyFileName` to match the already-renamed `__files/anthropic-messages-*.json` |
| `local-environment/wiremock/mappings/anthropic-messages.json` | Add `"priority": 10` |
| `local-environment/wiremock/__files/anthropic-messages.json` → `anthropic-messages-response.json` | Rename to match the mapping's existing `bodyFileName` reference; optionally align `model` field |
| `local-environment/wiremock/README.md` | Fix the broken link target; correct the "always present" / mapping-count description |
| `frontend/src/styles.scss` | Restyle `.table-header-cell`; add `.app-alt-row` and `table.mat-mdc-table` rounding rules |
| `frontend/src/app/features/transactions/transaction-table/transaction-table.component.html` | Add row index + `app-alt-row` binding |
| `frontend/src/app/features/risk-assessment/risk-assessment-history-table/risk-assessment-history-table.component.html` | Add row index + `app-alt-row` binding |
| `frontend/src/app/features/administration/risk-rules-table/risk-rules-table.component.html` | Add row index + `app-alt-row` binding |
| `backend/src/test/java/.../risk/engine/AiRiskAssessmentLocalWireMockFixturesTest.java` (new) | Automated AC1/AC2 coverage against the real `local-environment/wiremock/` fixtures |
| `docs/DECISIONS.md` | New D26 entry (already added — supersedes D19's default-preservation rationale) |

`backend/src/main/resources/application.yml` needs **no change** — the `anthropic` default is already in place.

## Test plan → Acceptance-criteria mapping

| `PHASE_7.md` AC | Verified by |
|---|---|
| AC1 (no env vars → no 404 for a novel transaction) | New `AiRiskAssessmentLocalWireMockFixturesTest.aNovelTransactionStillGetsTheGenericFallbackInsteadOf404` (Design §6) — automated; plus a manual `./gradlew dev` smoke check with no env vars set. |
| AC2 (original 15 scenarios still replay their own content) | New `AiRiskAssessmentLocalWireMockFixturesTest.replayingAnOriginalRecordedScenarioReturnsItsOwnRecordedContent` (Design §6) — automated, asserts the specific recorded `findings`/`recommendations` are returned. |
| AC3 (catch-all files exist, README resolves) | File existence check post-implementation; open `README.md` in the IDE and confirm no "Cannot resolve file" warnings. |
| AC4 (`app.ai.provider` defaults to `anthropic`) | Already true — `grep 'provider:' backend/src/main/resources/application.yml`. |
| AC5 (stronger orange header, alternating rows, rounded corners on all 3 tables) | `frontend/src/app/features/**/*.component.spec.ts` — new assertions that a rendered header `<th>` carries `.table-header-cell` and a rendered odd-indexed row carries `.app-alt-row`; manual visual check (`npm start`) for the rounding/color itself (no visual-regression tooling in this stack). |
| AC6 (shared SCSS, not duplicated) | Code review: the three `.component.scss` files gain **no** new header/row/corner rules — only `styles.scss` changes (plus the template `index`/class bindings). |
| AC7 (no regression in sort/filter/expand/pagination) | Existing Karma specs for all three table components must stay green unmodified beyond the new class-presence assertions. |
| AC8 (`./gradlew check` and `npm test` pass) | CI / local run, Global Definition of Done. |

## Risks / Open Questions

- **Literal fallback hex values in Design §4 are placeholders** pending a live `getComputedStyle` check against
  the actual M3-generated `--mat-sys-primary-container`/`--mat-sys-on-primary-container`/
  `--mat-sys-surface-container-low` tokens — implementation must verify contrast (header text vs. header
  background) meets basic readability, not just "more orange."
- **No automated visual-regression coverage** for AC5's actual color/roundedness — acceptance is manual, per
  `PHASE_7.md`'s own Testing Scope. A human must look at all three tables before this phase can be marked
  `COMPLETE`.
- **`AiRiskAssessmentLocalWireMockFixturesTest` (Design §6) is new test infrastructure**, not an extension of an
  existing suite — it's the smallest addition that can actually exercise `local-environment/wiremock/`'s real
  files (no existing test does), and is scoped to exactly the two assertions AC1/AC2 need.
