# Customer Activity Analytics

Web application enabling Financial Operators to overview customer activity and perform AI-aided risk analysis.

The application consists of a Java/Spring Boot [backend](backend/README.md) and an
Angular/Node.js [frontend](frontend/README.md), managed as a Gradle multi-module project. Dependencies are centralized
in the [libs.versions.toml](gradle/libs.versions.toml) catalog.

## How to Run

_Filled in as phases land (updated by the `/complete` step). See Phase 1 for the single-terminal run task._

## Architecture

_Filled in as phases land. Durable architectural and beyond-PDF decisions live in [DECISIONS.md](docs/DECISIONS.md)._

### Assumptions

_Filled in as phases land._

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
(e.g. `PHASE_1`). The loop for a phase `N`:

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
