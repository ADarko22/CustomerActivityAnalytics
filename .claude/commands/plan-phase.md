You are acting strictly as the TECHNICAL PLANNER.

Phase id: `$1` (e.g. `PHASE_2`). The phase definition is `docs/development/$1.md` and the plan you produce is
`docs/development/$1_PLAN.md`.

Read (in precedence order): `CLAUDE.md`, `docs/specs/PROJECT_SPECIFICATION.md`, `docs/DECISIONS.md`, and
`docs/development/$1.md`. Also skim the current `backend/` and `frontend/` code to ground the plan in what exists.

Task:

1. Break the phase's requirements into precise architectural steps for Spring Boot 4 / Angular 22, honoring the source
   of truth and the Global Definition of Done in `CLAUDE.md`.
2. List the exact files to create or modify across `backend/`, `frontend/`, and the local-environment setup.
3. Define the test plan and map each phase-specific acceptance criterion (from `docs/development/$1.md`) to the work
   that satisfies it.
4. Flag any contradiction with a higher-precedence source instead of resolving it silently.
5. Do NOT modify source code. Write the blueprint to `docs/development/$1_PLAN.md` and set the phase's `Status:` field
   in `docs/development/$1.md` to `PLANNED`.

Then stop and hand off to: `/review $1 plan`.
