# Phase 6 — Hardening, Polishing and Security

**Status:** NOT_STARTED
**Depends on:** Phases 1–5 — hardens the codebase by fixing deprecations, wrong or unused imports, variable names, and
other code styling related enhancements. Adds a concise and helpful Javadoc on classes and non-intuitive methods.
Replace vulnerable dependencies.

## Objective

Polish the codebase, make it readable, robust and maintainable. Improve stability and security of the codebase.

## Scope

- **In:** hardening of all the code, comments as documentation on complex logic requiring clarification. Resolve the
  SonarCloud MAINTAINABILITY issues raised against application code (`java:S1192` string-literal duplication,
  `java:S107` long parameter lists), fix the CI workflow so SonarCloud analysis actually runs automatically (it
  currently doesn't — see Risks), wire backend JaCoCo and frontend LCOV coverage into that analysis, and add SonarCloud
  status badges to the README.
- **Out:** no changes of behavior; no edits to the SQL content of already-applied `V*` versioned Flyway migrations
  (only Sonar analysis configuration touches those files — see D22).
- **Assumptions:** SonarCloud (`ADarko22_CustomerActivityAnalytics`, D5) is the source of truth for which
  maintainability issues count as "in scope" for this phase — this phase closes out everything currently OPEN/
  CONFIRMED there with a real underlying fix, not every theoretically-possible lint/style nit.
- **Key decision:** Flyway SQL migrations/seed files are excluded from SonarCloud analysis rather than edited or
  triaged issue-by-issue — see `docs/DECISIONS.md` D22 for why (SonarCloud has no PostgreSQL analyzer and
  misapplies Oracle PL/SQL rules to them).

## Functional Requirements

| Functionality                | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SonarCloud issue remediation | Every OPEN/CONFIRMED issue against application code has a real code fix, not a suppression: `java:S1192` (2, string-literal duplication), `java:S107` (10, long parameter lists across methods and entity constructors), `java:S4502` (1, undocumented CSRF-disable justification), `java:S112`/`S1130` (2, generic/unnecessary exception on `SecurityConfig`), `java:S2629` (1, conditionally-invoked log argument), test-quality nits (`S6068`/`S5853`/`S5778`/`S2925`, 14 total), and Gradle Kotlin-DSL task metadata (`kotlin:S6626`/`S6629`, 6, missing `group`/`description` on custom tasks). |
| Automated quality gate       | SonarCloud analysis runs on every push/PR via CI without manual intervention.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| Coverage visibility          | Backend (JaCoCo XML) and frontend (LCOV) coverage both feed into the SonarCloud analysis and are visible on its dashboard.                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| README badges                | The README displays live Quality Gate, Maintainability, Coverage, Bugs, and Vulnerabilities badges.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

## Acceptance Criteria

1. Java code should follow high coding standards, free of deprecations and vulnerabilities
2. Typescript should follow high coding standards, free of deprecations and vulnerabilities
3. Project builds and passes all tests
4. CI is also hardened to perform code analysis
5. Documentation (README files) are simplified and filled in where empty; aim is ease of readability and understanding;
   avoid details where not requried
6. Provide documentation comments (i.e. JavaDoc) where needed and appropriates
7. SonarCloud shows zero OPEN/CONFIRMED issues against application code (see the Functional Requirements table for
   the full current rule breakdown — 34 issues as of this phase's Sonar/CI wiring being verified live).
8. The SonarCloud analysis step in CI runs automatically on every push/PR when `SONAR_TOKEN` is configured as a
   repository secret — not gated on a condition that can never be true.
9. Backend and frontend coverage both appear on the SonarCloud dashboard (JaCoCo XML + LCOV wired in).
10. The README displays live SonarCloud badges (Quality Gate, Maintainability, Coverage, Bugs, Vulnerabilities).
11. No `V*` versioned Flyway migration's SQL content is changed — only Sonar analysis configuration.

## Testing Scope

No behavior change means the existing backend and frontend test suites are the regression safety net: every
suite must stay green (`./gradlew check`) before and after each hardening change, including the parameter-object
refactors (AC1/AC7) — those touch widely-called methods (controllers, services, and their tests), so each one
needs its call sites and tests updated in the same change, not just the method signature. The SonarCloud-specific
acceptance criteria (7–10) aren't unit-testable — they're verified by running `./gradlew sonar` against a real
`SONAR_TOKEN` and inspecting the live dashboard/API, plus a visual check that the README badges render.

## Risks / Open Questions

- **CI's SonarCloud step never actually ran automatically (resolved).** The existing `if: ${{ env.SONAR_TOKEN !=
  '' }}` condition referenced an env var declared in that same step's own `env:` block, which GitHub Actions does
  not expose to that step's own `if:` (only job/workflow-level env is visible there) — so the condition was always
  false. Fixed by referencing `secrets.SONAR_TOKEN` directly in the condition, and verified: the issues visible on
  the dashboard *before* this fix were actually coming from SonarCloud's own **Automatic Analysis** (a separate,
  CI-independent GitHub-integration feature that was silently enabled on the project) — SonarCloud refuses to run
  a CI-triggered scan while Automatic Analysis is active, and Automatic Analysis can't import JaCoCo/LCOV coverage
  (it doesn't build/run tests), so it had to be turned off in the SonarCloud project's Analysis Method settings
  before `./gradlew sonar` would succeed and coverage would populate. Once disabled, a real CI-mode scan surfaced
  a **more accurate and larger** issue set than Automatic Analysis had (34 open issues total, including 3 more
  `java:S107` violations — entity constructors — plus categories Automatic Analysis hadn't been surfacing at all:
  a CSRF-disable justification (`S4502`), a generic/unnecessary exception on `SecurityConfig` (`S112`/`S1130`), a
  conditionally-invocable log call (`S2629`), 14 test-code quality nits, and 6 Gradle Kotlin-DSL task-metadata
  nits) — see the Functional Requirements table for the current full breakdown, which supersedes any issue counts
  recorded earlier while Automatic Analysis was still active.
- **The `db/**` exclusion (D22) is a permanent blind spot, not a one-time fix.** Any future SQL added under
  `backend/src/main/resources/db/` will also go unanalyzed. This is intentional (there's no suitable analyzer to
  turn back on), but worth remembering if SonarCloud's SQL/PL-SQL analyzer ever adds real PostgreSQL support.
- **Parameter-object refactors are behavior-preserving but wide-reaching.** `AiRiskAssessmentHistoryService`,
  `AnalyticsService` (×2), `TransactionService`, and the `Card`/`Crypto`/`PaymentActivitySpecifications` classes
  all have their long parameter lists called from controllers and exercised by tests that assert on the current
  positional-argument signatures — each refactor's implementation plan needs to account for updating every call
  site and test in the same change.

