# Project Implementation Guidelines

You are an expert full-stack engineer building the Customer Activity Analytics Web Application.

## Source of Truth (highest precedence wins)

```
docs/specs/sq_pe_assignment.pdf   (product requirements — immutable)
  → docs/specs/PROJECT_SPECIFICATION.md   (technical requirements of record)
    → docs/DECISIONS.md   (durable architectural & beyond-PDF decisions)
      → docs/development/PHASE_N.md   (phase definition + status)
        → docs/development/PHASE_N_PLAN.md   (implementation plan; frozen at completion)
          → code + tests
```

When a lower layer contradicts a higher one, **STOP and flag it** — never silently proceed.
The PDF may only be *extended* (never overridden) via a recorded entry in `docs/DECISIONS.md`.
Read only the *current* phase's plan; a completed phase's plan is historical record, not authority.

## Tech Stack Guidelines

- **Java & Spring Boot:** Java 25, Spring Boot 4.1.x, Spring Data JPA, Spring Security OAuth2.
- **Frontend:** Angular 22, Node.js 22, FontAwesome icons, `angular-oauth2-oidc`.
- **Database & Migration:** PostgreSQL, Flyway migrations (under `backend/src/main/resources/db/migration`), Docker
  Compose.
- **Testing & Quality:** JUnit 5, Karma/Jasmine (Angular default), JaCoCo, Istanbul, ArchUnit, Spotless (
  `google-java-format`), `@angular-eslint` (flat config) + Prettier. See `docs/DECISIONS.md` D7–D10.
- **Local Environment:** Docker Compose provisioning Postgres, Keycloak, and WireMock stubs.

## Coding Standards

1. **API Protocol:** Adhere strictly to RESTful resource paths defined in the phase specs (e.g.,
   `/api/v1/customers/{customerId}/transactions`). Use SSE (`text/event-stream`) for AI streaming.
2. **Polymorphic Transactions:** `card_activity`, `payment_activity`, and `crypto_activity` extend base
   `transactions`. Model DTOs with sealed interfaces and Jackson type discriminators.
3. **Simplicity:** Prioritize clean code and simple architecture over unnecessary abstractions. Avoid custom exceptions
   unless explicitly needed.

## Global Definition of Done (applies to every phase — do not restate in phase docs)

- Clean, fluent, idiomatic code; correct API metadata and error messages; no needless custom exceptions.
- ArchUnit rules keep packages/modules independent and coherent, without over-engineering.
- Relevant logging and tracing in place, without harming performance.
- A PostgreSQL schema (Flyway) plus a local demo/data-seed script for the phase's tables.
- `./gradlew check` and `npm test` pass.

## Global Non-Functional Requirements (apply to every phase)

- **Maintainability & Testability:** clear module boundaries (backend REST layer, RAG/AI service, Angular frontend);
  test features, not boilerplate; prioritize simplicity over abstraction.
- **Usability:** pagination, filters, dropdowns, and every interactive element behave consistently across components.

## Workflow

Development runs as a manual, per-phase loop driven by the commands in `.claude/commands/`:
`/plan-phase → /review …plan-phase → /implement → /review …code → /complete`. See `README.md` for the full loop.
