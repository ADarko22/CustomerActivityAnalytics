# Development Phase #1 - Technology Decisions nad Local Environment Setup

## Technology Stack

The Web Application should be implemented as a Gradle Multimodule project, with a backend module, a frontend module, and
local environment setup. Dependencies are managed centrally in a Gradle toml catalog. The stack uses technologies I am
most familiar with, in order for me to supervise and review the AI-driven implementation.

### Backend

- Java 25 and Spring Boot 4.1, and a frontend module using Angular 22 and Node.js 22.
- Spring Boot starter dependencies for JPA/Hibernate, Postgres and Flyway, Web, AI, Actuator, OpenAPI and Oauth2.
- Database schema creation is managed with Flyway. A specific profile is used to initialized data for local development.
- ArchUnit

### Frontend

- Angular 22 and Node.js 22.
- Fontawesome icons and Angular-Oauth2 library.

### Local Environment

- Docker Compose.
- Running Postgres, Keycloak and wiremock. Having a folder with all the local configuration for these services (i.e.
  access credentials, stubs, demo identities and roles).
- Gradle scripts and tasks.

### CI/CD

- GitHub Actions.
- Gradle scripts and tasks.

## Definition of Done

1. Verify the provided project skeleton and manage new dependencies, following the initial structure via the Gradle toml
   catalog.
2. Define a Gradle task via a custom script, if necessary, for starting the docker-compose infrastructure + java
   backend + the angular frontend, within a single terminal with all the outputs piped in it, by highlighting different
   sources with a prefix, i.e. `[backend]`, `[frontend]`, or `[docker]`, and different colors.
3. Define Gradle build task with:
    1. Applying code linting, via Checkstyle with `google-java-format` and eslint with `eslint-config-google`.
    2. Running tests and generate reports, with JUnit and Jest; use JaCoCo and Instanbul for coverage report.
4. Define GitHub Actions workflow:
    1. Rely on Gradle for the linting, build and test phases.
    2. Integrate with a Sonar Project on SonarCloud (I will provision the SONAR_TOKEN and the identifying configuration)
5. Define a docker-compose that provisions a PostgreSQL DB, and setup a local-environment folder with a postgresql
   folder where to place future initialization scripts.
