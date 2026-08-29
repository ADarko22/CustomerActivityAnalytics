# Project Implementation Guidelines

You are an expert full-stack engineer building the Customer Activity Analytics Web Application[cite: 1].

## Tech Stack Guidelines

- **Java & Spring Boot:** Java 25, Spring Boot 4.1.x, Spring Data JPA, Spring Security OAuth2[cite: 6].
- **Frontend:** Angular 22, Node.js 22, FontAwesome icons, `angular-oauth2-oidc`[cite: 6].
- **Database & Migration:** PostgreSQL, Flyway migrations (under `backend/src/main/resources/db/migration`), Docker
  Compose[cite: 6].
- **Testing & Quality:** JUnit 5, Jest, JaCoCo, Istanbul, ArchUnit, Checkstyle (`google-java-format`), ESLint (
  `eslint-config-google`)[cite: 6].
- **Local Environment:** Docker Compose provisioning Postgres, Keycloak, and WireMock stubs[cite: 6].

## Coding Standards

1. **API Protocol:** Adhere strictly to RESTful resource paths defined in phase specs (e.g.,
   `/api/v1/customers/{customerId}/transactions`)[cite: 2]. Use SSE (`text/event-stream`) for AI streaming[cite: 4].
2. **Polymorphic Transactions:** `card_activity`, `payment_activity`, and `crypto_activity` extend base
   `transactions`[cite: 1]. Model DTOs with sealed interfaces and Jackson type discriminators.
3. **Simplicity:** Prioritize clean code and simple architecture over unnecessary abstractions. Avoid custom exceptions
   unless explicitly needed.

## Execution Rules

Output all bash actions using `cat << 'EOF' > filename` blocks. Always run build checks (`./gradlew check` or
`npm test`) to ensure code compiles and passes tests before finishing.