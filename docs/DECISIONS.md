# Architectural & Scope Decisions

Durable decision log. Each entry records a choice that is **not mandated by the assignment PDF** but adopted
deliberately, so graders and future phases see intent rather than scope creep. Entries are append-only; supersede
(don't delete) when a decision changes. Precedence: this file extends the PDF but never overrides it — see
`CLAUDE.md`.

Format per entry: **Decision · Context · Consequence**. Status one of `Accepted` / `Superseded by #N`.

---

## D1 — Angular (not React) for the frontend · Accepted

- **Decision:** Build the frontend in Angular 22 instead of the PDF's suggested React.
- **Context:** The PDF lists preferred technologies ("React") but explicitly allows the candidate to choose supporting
  technologies. Angular is the stack the author can best supervise and review for AI-driven implementation.
- **Consequence:** Uses `angular-oauth2-oidc`, Jest, ESLint (`eslint-config-google`), Istanbul. Diverges from the
  literal preference; justified as an allowed substitution.

## D2 — OAuth2 / OIDC via Keycloak for operator login · Accepted

- **Decision:** Implement "login by different operators" as OAuth2/OIDC (Authorization Code + PKCE) backed by a local
  Keycloak instance with demo `operator` and `admin` users.
- **Context:** The PDF requires only "login possibility by different operators" without prescribing a mechanism.
- **Consequence:** Adds Keycloak to Docker Compose and role-based access (read for all; admin/editor for risk-rule
  writes). Introduced in Phase 5.

## D3 — Server-Sent Events (SSE) for live AI progress · Accepted

- **Decision:** Stream AI risk-assessment progress to the UI over SSE (`text/event-stream`) with typed stage events.
- **Context:** The PDF asks that the operator "receive live-updates of the assessment processing" but names no
  transport. SSE fits one-way server→client progress streams simply.
- **Consequence:** A streaming endpoint plus resilience rules (timeout cleanup; assessment continues and persists if
  the operator disconnects). Introduced in Phase 4.

## D4 — WireMock-stubbed LLM for offline demo · Accepted

- **Decision:** Integrate a configurable AI provider but allow a feature-flagged record-and-replay mode where WireMock
  serves recorded LLM sessions.
- **Context:** The PDF explicitly permits stubbing LLM calls. Offline determinism makes the 10–15 min demo reliable.
- **Consequence:** Adds WireMock to Docker Compose and a dev record flag. Introduced in Phase 4.

## D5 — Quality gates: CI, ArchUnit, coverage · Accepted

- **Decision:** Add GitHub Actions CI (lint/build/test + SonarCloud), ArchUnit architecture tests, and coverage
  (JaCoCo/Istanbul), none required by the PDF.
- **Context:** The methodology is itself graded; visible, automated quality gates demonstrate engineering rigor.
- **Consequence:** Extra build config and a per-phase ArchUnit expectation captured in the global Definition of Done.

## D6 — Two-table risk-assessment model · Accepted

- **Decision:** Split persisted results into `risk_final_assessments` (aggregate: level, score, findings,
  recommendations, per transaction) and `risk_assessments` (line items: which rules fired, with `score_contribution`),
  keyed `(assessment_id, rule_id)`.
- **Context:** The PDF defines only a single `risk_assessments` signal table; the spec needs a persisted final outcome
  plus per-transaction history (Feature 7). The original spec had contradictory FK/PK definitions.
- **Consequence:** Reconciled data model in `PROJECT_SPECIFICATION.md`; `risk_level` is categorical (LOW/MEDIUM/HIGH)
  with a separate numeric `risk_score`.
