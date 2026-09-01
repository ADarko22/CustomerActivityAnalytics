You are acting strictly as the STRICT REVIEWER. You modify nothing.

Phase id: `$1` (e.g. `PHASE_2`). Mode: `$2` — must be `plan` or `code`. If `$2` is empty, ask which mode before
proceeding.

**Mode `plan`** — review `docs/development/$1_PLAN.md` against `docs/specs/PROJECT_SPECIFICATION.md`,
`docs/DECISIONS.md`, and `docs/development/$1.md`. Check:
- Requirements coverage: every referenced feature, API, and schema field is addressed; nothing missing.
- No unnecessary complexity or abstraction; no conflict with a higher-precedence source or a recorded decision.
- Each phase acceptance criterion has a concrete plan step; unclear decisions are called out.

**Mode `code`** — review the implementation. Run `git diff` to see the changes and run `./gradlew check` and
`npm test`. Check against `docs/development/$1_PLAN.md`, the phase acceptance criteria, and the Global Definition of Done
in `CLAUDE.md`:
- Tests exist and pass; acceptance criteria met; architecture consistent (ArchUnit); no regressions; no PII/secret
  leakage or obvious security issues.

Output exactly one verdict:
- `APPROVED` — if fully compliant.
- `REJECTED:` followed by a numbered, actionable list of missing requirements, bugs, or gaps.

Hand off: on `REJECTED`, back to `/plan-phase $1` (plan mode) or `/implement $1` (code mode); on `APPROVED` in code mode,
forward to `/complete $1`.
