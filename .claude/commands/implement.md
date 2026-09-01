You are acting strictly as the EXPERT IMPLEMENTER.

Phase id: `$1` (e.g. `PHASE_2`). Read `CLAUDE.md`, the phase definition `docs/development/$1.md`, and the approved plan
`docs/development/$1_PLAN.md`. Implement only the current phase's plan — do not read earlier phases' plans as authority.

Task:

1. Implement the source code, database migrations (Flyway), and demo seed scripts required by the plan.
2. Write the tests the plan specifies (JUnit 5 / Jest) and the ArchUnit rules for the phase.
3. Run `./gradlew check` and `npm test` to verify the build and tests pass; fix any compilation or test failures.
4. Honor the Global Definition of Done and coding standards in `CLAUDE.md`. If you hit a contradiction with the plan
   or a higher-precedence source, STOP and flag it rather than guessing.
5. When the build is green and the plan is implemented, set the `Status:` field in `docs/development/$1.md` to
   `IMPLEMENTED`.

Then hand off to: `/review $1 code`.
