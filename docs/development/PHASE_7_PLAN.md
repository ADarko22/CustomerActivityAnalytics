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
- **[SUPERSEDED — round 1 reasoning, kept for record] Why the 15 specific stubs were not being generalized/
  pruned:** WireMock's `equalToJson` has no partial/field-masking mode, so the only way to make an individual
  stub tolerant of a different `transactionId`/history is to replace it with `matchesJsonPath` constraints on
  just the stable fields (`$.system`, `$.model`). But since **all 15 stubs share the identical stable `system`
  prompt and `model`**, doing that to all of them would make them indistinguishable — only one could ever be
  reached, per WireMock's own equal-priority tie-break, making the other 14 dead weight. The already-restored
  catch-all (once its bug is fixed and given a lower `priority`) achieves the actual goal — no request ever
  404s — without discarding the 15 recorded fixtures or fighting WireMock's matcher model. Round 1 fixed the
  catch-all and the 15 stubs' broken `bodyFileName`s in place; it did not touch any `bodyPatterns`.
- **Round 2 correction — the user confirmed via a real `./gradlew dev` run that the 15 specific stubs still
  never matched, even after round 1.** The round-1 reasoning above only evaluated `system`/`model` as candidate
  match keys and correctly ruled them out — but never considered `transactionId`, which **is** a stable,
  per-transaction-unique field embedded in the prompt text (`messages[0].content`), confirmed generated
  deterministically by `backend/src/main/resources/db/seed/R__seed_demo_data.sql` (fixed UUID formula off
  `generate_series` — same IDs every run; cross-checked against 5 stubs' embedded amounts, which are themselves
  computed by the same generator and matched exactly). The real reason `equalToJson` never matches — even
  replaying the *same* transaction twice — is that its "Prior assessments" history section grows a fresh
  `triggeredAt` timestamp on every re-assessment, breaking full-body equality immediately. Separately, of the 15
  recorded stubs, only **9 represent genuinely distinct transactions**; 6 are redundant re-recordings of the
  same 4 transactions at different points in their growing history:

  | transactionId group | files (priorCount) | keep | drop |
  |---|---|---|---|
  | `b...-003` | 3AWDA(5), 6fuUu(3), lioza(4), zEbZh(5) | **3AWDA** | 6fuUu, lioza, zEbZh |
  | `b...-001` | 3OQsW(2), CoFk2(1) | **3OQsW** | CoFk2 |
  | `b...-002` | xF5qC(2), xs2r8(1) | **xF5qC** | xs2r8 |
  | `a...-025` | oL2bT(0), xMpOa(1) | **xMpOa** | oL2bT |
  | others (`c...-003`, `c...-002`, `f...-027`, `f...-026`, `a...-024`) | 1 file each | keep | — |

  **Corrected resolution:** delete the 6 redundant pairs (`mappings/` + `__files/`); rewrite each of the 9 kept
  mappings' `bodyPatterns` from full-body `equalToJson` to a `transactionId`-scoped regex —
  `{"matches": "(?s).*transactionId: <that-file's-own-uuid>.*"}` — tolerant of any amount of history/timestamp
  drift while remaining fully transaction-specific. The catch-all (now serving 9-vs-everything-else instead of
  15) is otherwise unchanged.
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

### 1. Fix the `bodyFileName`s, delete redundant stubs, and scope matching by `transactionId` (round 2)

Round 1 fixed each of the 15 stubs' `bodyFileName` (mechanical, `body-v1-messages-<suffix>.json` →
`anthropic-messages-<suffix>.json`), but left `bodyPatterns` untouched. Round 2 supersedes that with the
corrected resolution from Current State:

1. Delete the 6 redundant mapping + body file pairs: `6fuUu`, `lioza`, `zEbZh`, `CoFk2`, `xs2r8`, `oL2bT` (both
   `mappings/anthropic-messages-<suffix>.json` and `__files/anthropic-messages-<suffix>.json`).
2. For each of the 9 kept files, replace `request.bodyPatterns` entirely:
   ```diff
   -  "bodyPatterns": [ { "equalToJson": "<full captured body>", "ignoreArrayOrder": true, "ignoreExtraElements": true } ]
   +  "bodyPatterns": [ { "matches": "(?s).*transactionId: <that-file's-own-uuid>.*" } ]
   ```
   `(?s)` (DOTALL) is defensive in case the raw body ever contains a real newline; UUIDs need no regex escaping.
   The 9 UUIDs (extracted from each file's own originally-recorded content): `3AWDA`→
   `b0000000-0000-0000-0000-000000000003`, `3OQsW`→`b0000000-0000-0000-0000-000000000001`, `xF5qC`→
   `b0000000-0000-0000-0000-000000000002`, `7KI5Z`→`c0000000-0000-0000-0000-000000000003`, `zJr9w`→
   `c0000000-0000-0000-0000-000000000002`, `L2R2v`→`f0000000-0000-0000-0000-000000000027`, `vNXC8`→
   `f0000000-0000-0000-0000-000000000026`, `Ow3xp`→`a0000000-0000-0000-0000-000000000024`, `xMpOa`→
   `a0000000-0000-0000-0000-000000000025`. `response`/`priority` fields are untouched.

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

### 6. New automated test against the real `local-environment/wiremock/` fixtures (AC1/AC2) — round 2 rewrite

`AiRiskAssessmentWireMockReplayTest.java` cannot cover this: both its nested scenarios boot an isolated,
dynamic-port `WireMockExtension` with one hand-built always-matching stub, and never point at
`local-environment/wiremock/`'s actual `mappings/`/`__files/` directories. A plain JUnit 5 test (no Spring
context needed — pure HTTP against a WireMock instance loaded from disk),
`backend/src/test/java/io/github/adarko22/customeractivityanalytics/risk/engine/AiRiskAssessmentLocalWireMockFixturesTest.java`,
exercises it directly.

**Round 1's version of this test only proved the byte-identical-replay case** (POST the exact originally-recorded
body back) — which passed even under the broken round-1 matchers, since it never varied the "Prior assessments"
section, so it never actually caught the bug the user hit. **Round 2 rewrites it to test the real regression**:
since the mapping's matcher no longer inspects `system`/`model`/schema at all (only a `transactionId` substring),
the test body no longer needs to replicate the full real prompt — it only needs to contain a `transactionId:
<uuid>` line, which is exactly what makes the "vary the history" test meaningful and simple:

```java
class AiRiskAssessmentLocalWireMockFixturesTest {

  private static final String KNOWN_TRANSACTION_ID = "b0000000-0000-0000-0000-000000000003"; // the 3AWDA stub

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort()
              .usingFilesUnderDirectory("../local-environment/wiremock"))
          .build();

  @Test
  void aKnownTransactionMatchesItsStubRegardlessOfGrowingAssessmentHistory() {
    // Build two request bodies for KNOWN_TRANSACTION_ID: one with the originally-recorded single
    // history entry, one with a longer/different history (extra entries, different timestamps).
    // POST both to wireMock.baseUrl() + "/v1/messages" and assert both return HTTP 200 with the
    // 3AWDA stub's own recorded findings text — this is exactly the case round 1 got wrong.
  }

  @Test
  void aNovelTransactionStillGetsTheGenericFallbackInsteadOf404() {
    // Build a request body containing a transactionId not among the 9 kept stubs. Assert HTTP 200
    // with content distinct from any specific stub's findings (proves the catch-all + priority is
    // what prevents the 404, not a relaxed per-stub matcher).
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
| `local-environment/wiremock/mappings/anthropic-messages-{6fuUu,lioza,zEbZh,CoFk2,xs2r8,oL2bT}.json` + matching `__files/` entries (6 pairs) | **Deleted** — redundant re-recordings of already-represented transactions (round 2) |
| `local-environment/wiremock/mappings/anthropic-messages-{3AWDA,3OQsW,xF5qC,7KI5Z,zJr9w,L2R2v,vNXC8,Ow3xp,xMpOa}.json` (9 files) | Round 1: fix `response.bodyFileName`. Round 2: replace `request.bodyPatterns` with a `transactionId`-scoped `matches` regex |
| `local-environment/wiremock/mappings/anthropic-messages.json` | Add `"priority": 10` |
| `local-environment/wiremock/__files/anthropic-messages.json` → `anthropic-messages-response.json` | Rename to match the mapping's existing `bodyFileName` reference; optionally align `model` field |
| `local-environment/wiremock/README.md` | Fix the broken link target; correct mapping-count description (round 1: 16 → round 2: 10); document the manual `transactionId`-scoped `matches` edit as a required post-recording step |
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
| AC2 (each of the 9 kept transactions replays its own content, including on repeat assessment) | New `AiRiskAssessmentLocalWireMockFixturesTest.aKnownTransactionMatchesItsStubRegardlessOfGrowingAssessmentHistory` (Design §6, round 2 rewrite) — automated; asserts a match survives a *changed* "Prior assessments" section, the exact case round 1's test didn't cover. |
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
- **Recording new stubs now requires a manual matcher edit every time** (Design §1 / README update) — WireMock's
  CLI record flags and Admin API `requestBodyPattern` options have no field-scoped extraction mode, so this
  can't be automated away; a future contributor recording a new transaction must remember this step or the new
  stub will silently regress to the same full-body-match problem this phase fixes.
