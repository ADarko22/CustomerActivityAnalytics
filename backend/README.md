# Customer Activity Analytics - Backend

Spring Boot 4.1 REST API (Java 25): customer/transaction data, analytics, and the AI risk-assessment feature. See
the [root README](../README.md) for the full architecture, design decisions, and how to run the whole stack.

## Run standalone

```
../gradlew :backend:bootRun
```

Requires PostgreSQL (`../local-environment/docker-compose.yml` provisions it) and the `local` Spring profile for
seed data — `../gradlew dev` sets both up automatically from the repo root.

## API docs

Swagger UI: `http://localhost:8080/swagger-ui.html`. Health check: `http://localhost:8080/actuator/health`.

## Tests

```
../gradlew :backend:check
```

Runs Spotless (formatting), JUnit 5 (unit + Testcontainers-backed integration tests), ArchUnit (package
boundaries), and JaCoCo coverage.
