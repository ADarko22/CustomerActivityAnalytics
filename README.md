# Customer Activity Analytics

Web Application enabling Financial Operators to overview Customer Activity and perform Risk Analysis with the aid of AI.

The Web Application consists of a Java/SpringBoot [backend](backend/README.md) and an
Angular/Node.js [frontend.md](frontend/README.md), managed as a Gradle multi-module project.

Dependencies are centralized in the [libs.versions.toml](gradle/libs.versions.toml) catalog.

## How to Run

## Architecture

### Assumptions

### Design Decisions

## Implementation Journey

This project has been implemented with the aid of AI tools and Agents:

- **Gemini Chatbot** was used for research, brainstorming, and refinement of prompts driving the vibe-coding.
- **Claude CLI** was used for the code implementation, based on the prompts defined
  in [specs](docs/specs) and [development](docs/development), and the project-wide guidelines
  in [.claude/commands](.claude/commands) and [CLAUDE.md](CLAUDE.md).

All the instructions given to **Claude** are tracked in [CLAUDE.md](CLAUDE.md) and in
the [development](docs/development) folder, while the revisited project specification is available
at [PROJECT_SPECIFICATION.md](docs/specs/PROJECT_SPECIFICATION.md).

### CLI Interactive Loop

**Claude CLI** is used to invoke directly the commands sequentially, as follows:

1. Step 1: Run the Planner
   Reads PROJECT_SPECIFICATION.md and PHASE_1.md, creates docs/development/PHASE_1_PLAN.md, and stops.

  ```
  claude /plan PHASE_1.md
  ```

2. Step 2: Run the Reviewer on the Plan

   ```
   claude /review PHASE_1.md
   ```
   Audits PHASE_1_PLAN.md. If it outputs REJECTED: <reason>, you simply run:

   ```
   claude "Fix the plan in docs/development/PHASE_1_PLAN.md based on this feedback: <reason>"
   ```
   Once it outputs APPROVED, move to implementation.

3. Step 3: Run the Implementer
   ```
   claude /implement PHASE_1.md
   ```
   The CLI reads PHASE_1_PLAN.md, writes Java/TypeScript files, updates SQL schema scripts, and automatically runs
   ./gradlew test in the terminal to verify its work.

4. Step 4: Run the Reviewer on the Diff
   ```
   claude /review PHASE_1.md
   ```
   The CLI inspects git diff and recent build outputs. If REJECTED: <reason>, you simply run:
   ```
   claude /implement PHASE_1.md based on this feedback: <reason>
   ```

5. Step 5: Finalize & Commit
   ```
   git add .
   claude "Generate a conventional git commit message for the changes and commit."
   ```
