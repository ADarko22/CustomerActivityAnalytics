# Development Phase #5 - Cross-Cutting Concerns

## Project Design

### Assumptions

1. The Web Application doesn't manage transactions and users. These entities are read-only.
2. The Admin role is assumed for creating, updating and deleting risk rules.
3. Security is managed via a Keycloak instance, setup with two users: operator (password) and admin (admin). It is part
   of the local
   environment.
4. Database is provided as part of the local environment and populated with test data for per purpose of the demo.

### Functional Requirements

| Functionality  | Description                                                                                                                                     |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Operator Login | An operator logs in with the OAuth/OIDC PKCE login flow. The configured identifies from Keycloak local environment can be used as default demo. |

### Non-Functional Requirements

| Requirement                   | Description                                                                                                                                                                                                                                                                                 |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Maintainability & Testability | Each module (backend REST layer, RAG/AI service, Angular frontend) should have a clear boundaries, follow clean architecture and clean code principles but prioritize simplifity and avoid unnecessary abstractions, and ensure testing of all features but not of details and boilerplate. |
| Usability                     | Pagination, filters, dropdown and any other frontend element the user interacts with must behave consistently across any component.                                                                                                                                                         |

### High-level APIs

The nature of this web application is suitable to RESTful APIs, for the communication between frontend and backed.

**Base Path:** `/api/v1`

| Method     | Endpoint Path          | Description                                                                        | Access Level | Request Query / Body                                     | Response Payload              |
|------------|------------------------|------------------------------------------------------------------------------------|--------------|----------------------------------------------------------|-------------------------------|
| **GET**    | `/risk-rules`          | Retrieves a paginated list of deterministic risk scoring rules                     | Operator     | `?appliesTo=CARD`<br>`&page=0&size=20&sort=ruleName,asc` | `200 OK`: `Page<RiskRuleDto>` |
| **POST**   | `/risk-rules`          | Creates a new deterministic risk rule in the rule engine                           | Admin        | **Body:** `CreateRiskRuleDto`                            | `201 Created`: `RiskRuleDto`  |
| **PUT**    | `/risk-rules/{ruleId}` | Updates an existing risk rule's threshold logic or score weight                    | Admin        | **Body:** `UpdateRiskRuleDto`                            | `200 OK`: `RiskRuleDto`       |
| **DELETE** | `/risk-rules/{ruleId}` | Deletes a risk rule from the engine                                                | Admin        | `None`                                                   | `204 No Content`              |
| **GET**    | `/me`                  | Retrieves the current authenticated operator's profile and granted authority roles | Operator     | `None`                                                   | `200 OK`: `UserProfileDto`    |

## Definition of Done

1. Implement the RESTful endpoint described above, with clean code principles in mind and using a fluent and idiomatic
   style. Ensure each feature is fully tested and the APIs provide correct metadata and error messages. Do not define
   custom exceptions if not required. Use OIDC/OAuth2 for user authentication and authorization. The authenticated user
   will have access to any APIs based on their roles; read access is required for all APIs; while the admin/editor role
   is only required for GET/POST/PUT/DELETE operations on the risk-rules table.
2. Define the PostgreSQL DB schema for the necessary tables, from the specifications, for this phase. Generate a script
   for provisioning data for local testing and demo.
3. Add Keycloak to the Docker compose for the local environment setup. Configured two users: operator and admin. Where
   the admin has the editor roles allowing to manage risk rules.
4. Implement the Frontend using these APIs. The UI should be simple and easy to interact with. Risk rules are displayed
   in a dedicated table, in a separate "Administration" section, separate from the "Customer Analytics" section
   described and implemented so far. The UI should display in the top right the login user, basic info and a logout
   button. Logout should take care of starting a secure OAuth2/OIDC logout.
5. Define architectural rules, with ArchUnit as far as development goes. The rules should ensure that the packaging and
   modules are independent, coherent and keep a reasonable and simple balance between abstraction and concreteness. Do
   not over-engineer or complicate the structure. Ultimately, the project should be idiomatic and immediate.
6. Ensure relevant logging, without affecting performance, and tracing is in place.
7. The project should build and pass all the defined tests.
