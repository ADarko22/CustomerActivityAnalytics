You are acting strictly as the RELEASE GATE. Run this only after `/review $1 code` returned `APPROVED`.

Phase id: `$1` (e.g. `PHASE_2`). Read `docs/development/$1.md` (its acceptance criteria) and confirm the latest code
review was `APPROVED`.

Task:

1. Verify the phase is genuinely done: every acceptance criterion in `docs/development/$1.md` is met, and
   `./gradlew check` and `npm test` pass on a clean tree.
2. Freeze the plan: set the `Status:` field in `docs/development/$1_PLAN.md` to `COMPLETE`. It becomes historical
   record — later phases must not read it as authority.
3. Set the `Status:` field in `docs/development/$1.md` to `COMPLETE`.
4. Promote only durable knowledge:
   - Record any new architectural or beyond-PDF decision made this phase in `docs/DECISIONS.md`.
   - Update `README.md` (How to Run / Architecture summary / Assumptions) with what is now true for users/graders.
   Do NOT copy transient implementation detail into durable docs.
5. Leave the working tree clean and ready for the next phase.

Output: a short confirmation that Phase `$1` meets its acceptance criteria, what durable docs were updated, and that
the next phase can begin.
