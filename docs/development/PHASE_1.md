# Phase 1 — Technology Decisions and Local Environment Setup

**Status:** NOT_STARTED
**Depends on:** — (foundational)

## Objective

Establish the Gradle multi-module skeleton, centralized dependency catalog, local Docker environment, and CI pipeline
so every later phase can build, test, and run against a consistent baseline.

## Scope

- **In:** project scaffolding, `gradle/libs.versions.toml` catalog, single-terminal run task, Docker Compose (Postgres
  now; Keycloak + WireMock folders reserved for Phases 4–5), build/lint/test/coverage wiring, GitHub Actions.
- **Out:** any product feature or REST endpoint (those start in Phase 2).
- Stack choices and their rationale live in `docs/DECISIONS.md` (D1 Angular, D5 CI/ArchUnit/coverage).

## Technology Stack

Gradle multi-module project: a backend module, a frontend module, and local-environment setup. Dependencies managed
centrally in a Gradle TOML catalog. The stack favors technologies the author can supervise while reviewing AI output.

- **Backend:** Java 25, Spring Boot 4.1; starters for JPA/Hibernate, Postgres + Flyway, Web, AI, Actuator, OpenAPI,
  OAuth2; ArchUnit. Flyway manages schema; a dedicated profile seeds data for local development.
- **Frontend:** Angular 22, Node.js 22; FontAwesome icons; `angular-oauth2-oidc`.
- **Local Environment:** Docker Compose running Postgres (and, from later phases, Keycloak and WireMock), with a config
  folder for credentials, stubs, and demo identities/roles; Gradle scripts and tasks.
- **CI/CD:** GitHub Actions driving the Gradle lint/build/test phases.

## Acceptance Criteria

1. Project skeleton verified; all dependencies flow through the `gradle/libs.versions.toml` catalog.
2. A single Gradle task starts Docker Compose + backend + frontend in one terminal, multiplexing output with colored
   `[backend]` / `[frontend]` / `[docker]` prefixes.
3. The Gradle build applies linting (Checkstyle + `google-java-format`; ESLint + `eslint-config-google`), runs tests
   (JUnit + Jest), and produces coverage reports (JaCoCo + Istanbul).
4. A GitHub Actions workflow runs lint/build/test via Gradle and integrates with SonarCloud (author provisions
   `SONAR_TOKEN` and the Sonar project config).
5. Docker Compose provisions PostgreSQL and a `local-environment/postgresql` folder is reserved for future init
   scripts.

## Testing Scope

Smoke-level: build succeeds, both module test suites run (even if empty), and the run task boots the stack locally.

## Risks / Open Questions

- Java 25 + Spring Boot 4.1 are bleeding-edge; verify plugin/starter compatibility early.
- Confirm the single-terminal multiplexed run task works cross-platform (macOS dev host).
