# Customer Activity Analytics

Web application enabling Financial Operators to overview customer activity and perform AI-aided risk analysis.

The application consists of a Java/Spring Boot [backend](backend/README.md) and an
Angular/Node.js [frontend](frontend/README.md), managed as a Gradle multi-module project. Dependencies are centralized
in the [libs.versions.toml](gradle/libs.versions.toml) catalog.

## How to Run

Prerequisites: JDK 25 (managed via the Gradle toolchain), Docker Desktop (or another Docker daemon) running. Node is
provisioned automatically by Gradle for the frontend.

- **Run everything locally, one terminal:** `./gradlew dev` — starts Postgres (Docker Compose), the backend
  (`:8080`), and the frontend (`:4200`), multiplexed with colored `[docker]` / `[backend]` / `[frontend]` prefixes.
  Stop with Ctrl-C.
- **Verify:** `./gradlew check` — lint, tests, and coverage for both modules.
- **Build:** `./gradlew build`.
- Backend health: `http://localhost:8080/actuator/health`. Frontend: `http://localhost:4200`.

## Architecture

Gradle multi-module project:

- `backend` — Java 25, Spring Boot 4.1, Spring Data JPA + Flyway + PostgreSQL, Spring AI, OAuth2 resource server.
  Domain model (Phase 2): `customers` and polymorphic `transactions` (`CARD`/`PAYMENT`/`CRYPTO`, JPA `JOINED`
  inheritance), exposed under `/api/v1` — customer search, a paginated/filterable/sortable transaction overview, and
  per-transaction polymorphic detail.
- `frontend` — Angular 22 + Angular Material, FontAwesome icons. Customer search (autocomplete), a server-driven
  transaction table with an activity-type filter, per-column sort/filter (icon-triggered popovers on each header),
  and inline click-to-expand row detail (Phase 2 / Phase 2 EXT). A pastel orange/white Material theme is applied
  app-wide. `ng serve` proxies `/api/**` to the backend via `frontend/proxy.conf.json`.
- `local-environment` — Docker Compose (PostgreSQL now; Keycloak and WireMock folders reserved for later phases).

CI (GitHub Actions) runs `./gradlew check` on every push/PR, with an optional SonarCloud pass when `SONAR_TOKEN` is
configured.

Durable architectural decisions — including every choice that goes beyond the assignment PDF — are tracked in
[DECISIONS.md](docs/DECISIONS.md).

### Assumptions

- Local/demo use only: default database credentials in `local-environment/docker-compose.yml` and
  `backend/src/main/resources/application.yml` are placeholders, overridable via environment variables — not
  intended for production deployment.
- The AI provider integration is scaffolded (dependency + placeholder config) but inert until Phase 4 wires up real
  usage.
- No authentication is enforced yet: every `/api/v1/**` endpoint is open (a temporary `permitAll` `SecurityConfig`)
  until Phase 5 wires up real OAuth2/OIDC login and role-based access — see [DECISIONS.md](docs/DECISIONS.md) D13.
- Customer/transaction data is read-only and seeded for the demo (no create/update/delete endpoints); the seed
  dataset only loads under the `local` Spring profile (`./gradlew dev` sets this automatically).

## Implementation Journey

This project is implemented with the aid of AI tools and agents (the methodology is part of the assignment):

- **Gemini/ChatGPT/Claude Chatbot** — research, brainstorming, and refinement of the prompts driving the implementation.
- **Claude CLI** — the code implementation, driven by the specs in [docs/specs](docs/specs), the phase docs in
  [docs/development](docs/development), the commands in [.claude/commands](.claude/commands), and the project-wide
  guidelines in [CLAUDE.md](CLAUDE.md).

### Source of Truth

Precedence (highest wins), also enforced in [CLAUDE.md](CLAUDE.md):

```
sq_pe_assignment.pdf → PROJECT_SPECIFICATION.md → DECISIONS.md → PHASE_N.md → PHASE_N_PLAN.md → code
```

- [PROJECT_SPECIFICATION.md](docs/specs/PROJECT_SPECIFICATION.md) — technical requirements of record.
- [DECISIONS.md](docs/DECISIONS.md) — durable architectural and beyond-PDF decisions.
- [docs/development](docs/development) — per-phase definition (`PHASE_N.md`) and frozen plan (`PHASE_N_PLAN.md`).

### CLI Interactive Loop

Each phase is driven manually through Claude CLI. Commands take the phase id **without** the `.md` extension
(e.g. `PHASE_1`; a follow-up refinement scoped to an already-completed phase, such as a UX-only pass, uses an `_EXT`
suffix, e.g. `PHASE_2_EXT`, and runs through the identical loop below). The loop for a phase `N`:

1. **Plan** — `claude /plan-phase PHASE_N`
   Reads the spec, decisions, and `PHASE_N.md`; writes `docs/development/PHASE_N_PLAN.md`; sets `Status: PLANNED`;
   stops. Touches no source.

2. **Review the plan** — `claude /review PHASE_N plan`
   Audits the plan against the spec/decisions/phase. On `REJECTED: <reasons>`, refine and re-run `/plan-phase PHASE_N`
   (or `claude "fix docs/development/PHASE_N_PLAN.md: <reasons>"`). Loop until `APPROVED`.

3. **Implement** — `claude /implement PHASE_N`
   Reads `PHASE_N_PLAN.md`; writes Java/TypeScript, Flyway migrations, and seed scripts; runs `./gradlew check` and
   `npm test`; sets `Status: IMPLEMENTED`.

4. **Review the code** — `claude /review PHASE_N code`
   Inspects `git diff` and build/test output. On `REJECTED: <reasons>`, re-run `/implement PHASE_N`. Loop until
   `APPROVED`.

5. **Complete** — `claude /complete PHASE_N`
   Verifies acceptance criteria, freezes the plan (`Status: COMPLETE`), promotes durable knowledge into this README and
   `DECISIONS.md`, and sets the phase `Status: COMPLETE`.

6. **Commit** — `git add .` then
   `claude "Generate a conventional git commit message for the changes and commit."`

## LLMs & Agent Instructions (assignment deliverable)

_Summary of the LLM provider/models used and the agent instructions given — maintained here for the assessment._
