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
- **Claude CLI** was used for the code implementation, based on the prompts defined in [specs](docs/specs)
  and [development](docs/development), and the project-wide guidelines in [CLAUDE.md](CLAUDE.md).

All the instructions given to **claude** are tracked in [CLAUDE.md](CLAUDE.md) and in
the [development](docs/development) folder, while the revisited project specification is available
at [PROJECT_SPECIFICATION.md](docs/specs/PROJECT_SPECIFICATION.md).