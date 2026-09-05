# Phase 7 — WireMock Recorded-Stub Matching Fix & Orange Table Restyle

**Status:** IMPLEMENTED
**Depends on:** Phases 4 / 4_EXT / 4_EXT_2 (configurable AI provider, D4/D18/D19, WireMock record-and-replay) — fixes
a regression in the offline demo path introduced by 4_EXT_2's fully-specific recorded stubs, and supersedes part of
D19 via the new `docs/DECISIONS.md` D26 (default provider flips to `anthropic`). Also touches the transaction table
(Phase 2/2_EXT), the risk-assessment history table (Phase 5-line work), and the administration risk-rules table
(D21) purely for visual restyling — no functional change to any of them.

## Objective

Two independent hardening/polish items bundled into one phase:

1. Restore a working **offline** AI risk-assessment demo when `app.ai.provider` defaults to `anthropic` — currently
   broken because the 15 WireMock stubs recorded in 4_EXT_2 each match on the *exact* request body of the one
   real call that produced them (including per-request unique fields), so no other request ever matches and the
   app 404s.
2. Push the frontend's three data tables further into the app's already-adopted orange Material palette — stronger
   orange table headers, alternating row backgrounds, and more rounded corners — applied consistently, not
   per-component copy-paste.

## Scope

- **In:**
  - **WireMock stub matching (backend / local-environment):** ensure no Anthropic request against
    `local-environment/wiremock/` ever 404s, without discarding the 15 recorded scenarios recorded in 4_EXT_2.
    Resolution (see `Key decision` below): fix the 15 renamed stubs' `bodyFileName` references (broken by an
    in-progress manual rename, pointing at now-nonexistent files) so replaying one of the exact original
    transactions returns its own recorded content, and restore/fix a low-priority catch-all fallback stub
    (`local-environment/wiremock/mappings/anthropic-messages.json` + a correctly-named
    `local-environment/wiremock/__files/anthropic-messages-response.json`, both currently present but
    misreferenced, and still cited by `local-environment/wiremock/README.md`, causing broken-link warnings) with
    an explicit lower `priority` so it only serves requests none of the 15 specific stubs match. The 15 stubs'
    exact-body `bodyPatterns` matchers are *not* rewritten to be more permissive — see Key Decision for why.
  - **Default AI provider (backend):** set `app.ai.provider: ${AI_PROVIDER:anthropic}` in
    `backend/src/main/resources/application.yml` (currently defaults to `openai`) so the app plays back the
    recorded Anthropic sessions out of the box. Recorded as `docs/DECISIONS.md` D26, which supersedes the
    default-preservation rationale in D19's `matchIfMissing = true` framing.
  - **Frontend table restyle:** apply alternating row backgrounds (light orange / white), a stronger orange
    header background (today the header only gets a 2px orange *border*, per the shared `.table-header-cell`
    class in `frontend/src/styles.scss`), and rounded container corners to all three Angular Material tables:
    `transaction-table`, `risk-assessment-history-table`, and `risk-rules-table`. Introduce this via a shared,
    reusable styling mechanism (e.g. new global SCSS classes in `styles.scss` alongside the existing
    `.table-header-cell`, applied by all three components) rather than duplicating rules across each
    component's own `.scss` file, consistent with the CLAUDE.md usability NFR that shared UI elements behave
    consistently.
- **Out:** no change to any table's *columns*/data/behavior (sorting, filtering, expand-to-detail); no change to
  the AI risk-assessment domain logic, prompt construction, `AiRiskAssessmentOrchestrator`, or the structured
  output schema; no new CSS framework or design-system tooling (stays within the already-adopted Angular
  Material M3 + SCSS stack); no restyle of non-table surfaces (buttons, dialogs, forms, nav) beyond what's needed
  for visual consistency with the new table look; no change to which real Anthropic API key/workspace is needed
  to *record* new stubs — only to what's needed to *replay* them offline.
- **Assumptions:** the app already themes via Angular Material's M3 `mat.$orange-palette`
  (`frontend/src/styles.scss`) — this phase pushes the *table-specific* treatment further into orange, it does
  not introduce orange as a new brand color. "Rounded and pleasant" means table-container `border-radius` using
  Material's existing M3 shape tokens, not a new spacing/shadow/elevation system.
- **Key decision (resolved during planning — see `PHASE_7_PLAN.md` Current State):** WireMock's `equalToJson`
  body matcher has no built-in way to "ignore these specific dynamic fields, match everything else exactly."
  Rewriting each recorded mapping's `bodyPatterns` to a narrower `matchesJsonPath`-based matcher (targeting only
  the stable `system`/`model` fields) was considered but rejected: all 15 stubs share an *identical* stable
  `system` prompt and `model`, so that rewrite would make them indistinguishable from one another — only one
  could ever be reached under WireMock's own equal-priority tie-break, turning the other 14 into dead weight.
  Resolution: leave the 15 stubs' `bodyPatterns` untouched (each still replays its own exact original scenario
  when byte-for-byte re-run) and rely on a `priority`-ordered catch-all fallback for everything else — achieving
  "no request ever 404s" without discarding the recorded fixtures or fighting WireMock's matcher model.

## Functional Requirements

| Functionality                    | Description                                                                                                                                                            |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Offline Anthropic replay default | With no env vars set, `./gradlew dev` uses `app.ai.provider=anthropic` and every AI risk-assessment request against WireMock returns a valid 200, never a 404.        |
| Generalized stub matching         | The 15 recorded stubs (or their replacement matching strategy) tolerate a different `transactionId`/prior-assessment history than the one they were recorded against. |
| Catch-all fallback restored       | A generic, always-matching Anthropic stub exists again as the lowest-priority fallback, and `wiremock/README.md`'s file references resolve.                          |
| Orange table header               | All three data tables render their header row with a visibly stronger orange background, not just an accent border.                                                  |
| Alternating table rows            | All three data tables render alternating light-orange/white row backgrounds.                                                                                          |
| Rounded table containers          | All three data tables render with rounded container corners consistent with Material's M3 shape tokens.                                                             |
| Shared styling, not duplication   | The new header/row/corner treatment is defined once (shared SCSS) and consumed by all three table components, not copy-pasted three times.                          |

## Acceptance Criteria

1. `./gradlew dev` with no `AI_PROVIDER`/`ANTHROPIC_*`/`WIREMOCK_*` env vars set successfully completes an AI risk
   assessment end-to-end against WireMock (no `NotFoundException`/404) for a transaction that does **not** match
   any of the 15 originally-recorded scenarios verbatim.
2. Re-running one of the 15 originally-recorded scenarios (same transaction/customer/history as when it was
   captured) still returns a 200 with its own recorded `findings`/`recommendations` content, via its own
   (unchanged) specific stub.
3. `local-environment/wiremock/mappings/anthropic-messages.json` and
   `local-environment/wiremock/__files/anthropic-messages-response.json` exist again, and
   `local-environment/wiremock/README.md`'s references to them resolve (no more "Cannot resolve file" IDE
   warnings).
4. `backend/src/main/resources/application.yml`'s `app.ai.provider` default is `anthropic`.
5. The transaction table, risk-assessment history table, and risk-rules table all show: a visibly stronger orange
   header background, alternating light-orange/white row backgrounds, and rounded table-container corners.
6. The new table styling is implemented once in shared SCSS and referenced by all three components — no
   per-component duplication of the new rules.
7. No existing table functionality (sorting, filtering, row expansion, pagination) regresses.
8. `./gradlew check` and `npm test` both pass (Global Definition of Done, `CLAUDE.md`).

## Testing Scope

- **Backend:** the existing `backend/src/test/java/.../risk/engine/AiRiskAssessmentWireMockReplayTest.java`
  cannot be extended for this — it boots its own isolated, dynamic-port WireMock instance with a single
  always-matching stub, and never loads `local-environment/wiremock/`'s actual mapping/`__files` directories, so
  it can't exercise the real fixture set this phase fixes. Instead, add a new, self-contained backend test that
  points a `WireMockServer`/`WireMockExtension` directly at `local-environment/wiremock` (`usingFilesUnderDirectory`)
  and asserts over raw HTTP: (a) POSTing a body byte-identical to one of the 15 recorded scenarios to `/v1/messages`
  returns 200 with that scenario's own recorded `findings`/`recommendations`, and (b) POSTing a body with a
  different `transactionId`/history (same stable `system`/`model`) also returns 200, with the generic catch-all's
  content — proving the fallback, not a relaxed per-stub matcher, is what prevents the 404. See
  `PHASE_7_PLAN.md` Design for the exact test class and location.
- **Frontend:** this stack has no visual-regression tooling — Karma/Jasmine coverage is limited to asserting the
  new CSS classes (header/alternating-row/rounded-container) are present on rendered `DebugElement`s in each of
  the three table components' existing specs. Actual color/roundedness correctness is verified visually
  (`npm start` + manual check across all three tables), not asserted in an automated test.

## Risks / Open Questions

- **Real-key dependency for future re-recording is unaffected** — this phase does not change how new stubs get
  recorded (`WIREMOCK_RECORD_MODE`/`WIREMOCK_PROXY_TARGET`, per `local-environment/wiremock/README.md`), only how
  existing/offline replay behaves.
- **No existing visual-regression safety net** for the UI restyle — acceptance of the color/spacing/roundedness
  criteria is manual, not automated; a reviewer must actually look at the three tables before calling this phase
  done.
