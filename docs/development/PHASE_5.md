# Phase 5 — Cross-Cutting Concerns (Auth & Risk-Rule Administration)

**Status:** IMPLEMENTED
**Depends on:** Phases 2–4 — secures the existing endpoints and adds admin CRUD over the risk rules used in Phase 4.

## Objective

Add operator login (OAuth2/OIDC via Keycloak) and role-based access, and let admins manage the risk rules that drive
AI assessments.

## Scope

- **In:** OIDC login/logout, role-based authorization across all endpoints, risk-rule CRUD, the current-user endpoint,
  a frontend Administration section, Keycloak in the local environment.
- **Out:** new product analytics or AI features.
- **Assumptions:** transactions/customers are read-only and seeded; the Admin role manages risk-rule CRUD; Keycloak is
  provisioned locally with demo users `operator` (password `password`) and `admin` (password `admin`).
- Key decision: `docs/DECISIONS.md` D2 (OAuth2/OIDC via Keycloak).

## Requirements (refs into `PROJECT_SPECIFICATION.md`)

- Feature **8** (operators log in; each has an identity and access rights), plus CRUD administration of `risk_rules`.

## Functional Requirements

| Functionality  | Description                                                                                                                             |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Operator Login | An operator logs in via the OAuth2/OIDC Authorization Code + PKCE flow. The configured Keycloak demo identities are usable as defaults. |

## High-level APIs — Base Path `/api/v1`

| Method     | Endpoint Path          | Description                                                                        | Access Level | Request Query / Body                                     | Response Payload              |
|------------|------------------------|------------------------------------------------------------------------------------|--------------|----------------------------------------------------------|-------------------------------|
| **GET**    | `/risk-rules`          | Retrieves a paginated list of deterministic risk-scoring rules                     | Operator     | `?appliesTo=CARD`<br>`&page=0&size=20&sort=ruleName,asc` | `200 OK`: `Page<RiskRuleDto>` |
| **POST**   | `/risk-rules`          | Creates a new deterministic risk rule                                              | Admin        | **Body:** `CreateRiskRuleDto`                            | `201 Created`: `RiskRuleDto`  |
| **PUT**    | `/risk-rules/{ruleId}` | Updates an existing rule's threshold logic or score weight                         | Admin        | **Body:** `UpdateRiskRuleDto`                            | `200 OK`: `RiskRuleDto`       |
| **DELETE** | `/risk-rules/{ruleId}` | Deletes a risk rule                                                                | Admin        | `None`                                                   | `204 No Content`              |
| **GET**    | `/me`                  | Retrieves the current authenticated operator's profile and granted authority roles | Operator     | `None`                                                   | `200 OK`: `UserProfileDto`    |

## Acceptance Criteria

1. All endpoints protected by OIDC/OAuth2: read access requires an authenticated operator; the admin/editor role is
   required for the risk-rule write operations (POST/PUT/DELETE).
2. Keycloak is added to Docker Compose with `operator` and `admin` users, where `admin` holds the editor role.
3. Frontend: risk rules shown in a dedicated table under a separate "Administration" section (distinct from the
   "Customer Analytics" section); top-right shows the logged-in user's basic info and a logout button that performs a
   secure OAuth2/OIDC logout.

## Testing Scope

Backend: authorization matrix (operator vs admin across read vs write), token/role mapping, `/me` projection.
Frontend: authenticated route guards, admin-only visibility of the Administration section, login/logout flow.

## Risks / Open Questions

- Keycloak realm/client config reproducibility in Docker Compose for the demo.
- Consistent OIDC logout (front-channel) leaving no stale session.
