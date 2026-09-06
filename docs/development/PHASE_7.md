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
   app 404s. **Correction after a first fix attempt (see Key Decision):** a first pass left the 15 stubs' matchers
   untouched and relied solely on a generic fallback, which technically stopped 404s but meant the specific
   stubs *still* never matched — not even on a second assessment of the *same* transaction, since the prompt's
   "Prior assessments" section grows a new timestamped entry every re-assessment. The actual fix scopes each
   stub to its transaction's stable `transactionId` (embedded in the prompt, and confirmed stable across every
   `./gradlew dev` run — it's generated deterministically by the Flyway demo seed), not the full body.
2. Push the frontend's three data tables further into the app's already-adopted orange Material palette — stronger
   orange table headers, alternating row backgrounds, and more rounded corners — applied consistently, not
   per-component copy-paste.

## Scope

- **In:**
    - **WireMock stub matching (backend / local-environment):** ensure a request for any of the seeded demo
      transactions always replays that transaction's own recorded findings — regardless of how much "Prior
      assessments" history has accumulated since it was recorded — and any other transaction still gets a generic
      200 via a low-priority catch-all, never a 404. Resolution (see `Key decision` below): of the 15 originally
      recorded stubs, 6 were redundant re-recordings of the same 4 transactions at different points in their
      history — deleted, keeping one canonical stub per unique transaction (9 remain). Each kept stub's
      `bodyPatterns` is rewritten from a full-body `equalToJson` to a `transactionId`-scoped regex
      (`{"matches": "(?s).*transactionId: <uuid>.*"}`), so it matches its transaction regardless of history growth
      without matching any other transaction. The generic catch-all fallback
      (`local-environment/wiremock/mappings/anthropic-messages.json` + a correctly-named
      `local-environment/wiremock/__files/anthropic-messages-response.json`, both previously misreferenced and
      still cited by `local-environment/wiremock/README.md`, causing broken-link warnings) is fixed with an
      explicit lower `priority` so it only serves requests none of the 9 specific stubs match.
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
- **Key decision (revised after the first fix attempt proved insufficient — see `PHASE_7_PLAN.md` Current
  State):** WireMock's `equalToJson` body matcher has no built-in way to "ignore these specific dynamic fields,
  match everything else exactly." An earlier pass considered rewriting each mapping's `bodyPatterns` to a
  narrower `matchesJsonPath`-based matcher targeting only the stable `system`/`model` fields — rejected, since
  all 15 original stubs share an *identical* `system` prompt and `model`, making that rewrite indistinguishable
  across stubs. That earlier pass then left the stubs' matchers untouched entirely and relied only on the
  catch-all — which stopped 404s but meant the specific stubs *still* never replayed their own findings, since
  even re-running the same transaction produces a different full body (fresh "Prior assessments" timestamps each
  time). **Corrected resolution:** `system`/`model` were the wrong candidate key — `transactionId`, embedded in
  the prompt text, *is* stable per-transaction (confirmed generated deterministically by the Flyway demo seed,
  identical across every `./gradlew dev` run) and unique per transaction. Each stub now matches via a regex
  scoped to its own `transactionId`, tolerant of any amount of history/timestamp drift while remaining fully
  transaction-specific. This also exposed that only 9 of the 15 original recordings were distinct transactions —
  the other 6 were redundant re-recordings, now deleted.

## Functional Requirements

| Functionality                    | Description                                                                                                                                                                                               |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Offline Anthropic replay default | With no env vars set, `./gradlew dev` uses `app.ai.provider=anthropic` and every AI risk-assessment request against WireMock returns a valid 200, never a 404.                                            |
| Transaction-scoped stub matching | Each of the 9 kept recorded stubs (deduplicated from the original 15) matches its own transaction via a `transactionId`-scoped regex, tolerant of any amount of prior-assessment history/timestamp drift. |
| Catch-all fallback restored      | A generic, always-matching Anthropic stub exists again as the lowest-priority fallback, and `wiremock/README.md`'s file references resolve.                                                               |
| Orange table header              | All three data tables render their header row with a visibly stronger orange background, not just an accent border.                                                                                       |
| Alternating table rows           | All three data tables render alternating light-orange/white row backgrounds.                                                                                                                              |
| Rounded table containers         | All three data tables render with rounded container corners consistent with Material's M3 shape tokens.                                                                                                   |
| Shared styling, not duplication  | The new header/row/corner treatment is defined once (shared SCSS) and consumed by all three table components, not copy-pasted three times.                                                                |

## Acceptance Criteria

1. `./gradlew dev` with no `AI_PROVIDER`/`ANTHROPIC_*`/`WIREMOCK_*` env vars set successfully completes an AI risk
   assessment end-to-end against WireMock (no `NotFoundException`/404) for a transaction that is **not** one of
   the 9 seeded demo transactions with a specific stub.
2. Assessing one of the 9 seeded demo transactions returns a 200 with its own recorded `findings`/
   `recommendations` content, **including on a second (or later) assessment of that same transaction** — i.e.
   the match survives a "Prior assessments" section that has grown beyond what was originally recorded.
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
  it can't exercise the real fixture set this phase fixes. Instead, a new, self-contained backend test
  (`AiRiskAssessmentLocalWireMockFixturesTest`) points a `WireMockExtension` directly at `local-environment/
  wiremock` (`usingFilesUnderDirectory`) and asserts over raw HTTP: (a) POSTing a request for a known seeded
  transaction's `transactionId`, built with a "Prior assessments" section that **differs** from whatever was
  originally recorded, still returns 200 with that transaction's own recorded `findings`/`recommendations` —
  and (b) POSTing a request for a `transactionId` not among the 9 kept stubs still returns 200 via the generic
  catch-all, with none of the specific findings text. See `PHASE_7_PLAN.md` Design for the exact test class and
  location.
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
