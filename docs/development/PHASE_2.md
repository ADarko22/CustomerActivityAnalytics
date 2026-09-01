# Phase 2 — Transactions Features

**Status:** NOT_STARTED
**Depends on:** Phase 1 — needs the module skeleton, Postgres, and build/CI baseline.

## Objective

Let an operator search a customer by ID and browse that customer's transactions — a paginated, sortable, filterable
overview plus polymorphic per-transaction detail.

## Scope

- **In:** customer search, transaction overview, transaction detail; the `customers`, `transactions`, and the three
  activity tables; the Phase-2 endpoints below.
- **Out:** analytics (Phase 3), AI risk assessment (Phase 4), auth (Phase 5).
- **Assumption:** transactions and customers are read-only, seeded by the local environment for the demo.

## Requirements (refs into `PROJECT_SPECIFICATION.md`)

- Features **1–2** (customer search; sorted/paginated overview; type filter dropdown; detail-on-select).
- Data model: `customers`, `transactions`, `card_activity`, `payment_activity`, `crypto_activity`.

## Functional Requirements

| Functionality                  | Description                                                                                                                         |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Customer Search                | Search with a drop-down limited to the top 5 matches (demo-friendly); autocomplete on input; empty input returns the first results. |
| Customer Transactions Overview | On selecting a customer, a paginated table shows transactions sorted most-recent-first, with per-column filters and sorting.        |
| Customer Transaction Detail    | On selecting a transaction, full details render in a top-level card, specific to the activity type and handled polymorphically.     |

## High-level APIs — Base Path `/api/v1`

| Method  | Endpoint Path                                          | Description                                                                                                         | Access Level | Request Query / Body                                                                                      | Response Payload                                                            |
|---------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| **GET** | `/customers`                                           | Searches and retrieves a paginated list of customers (for search-bar autocomplete)                                  | Operator     | `?query=abc`<br>`&page=0&size=10`                                                                         | `200 OK`: `Page<CustomerDto>`                                               |
| **GET** | `/customers/{customerId}/transactions`                 | Retrieves paginated transaction history across all activity types (`CARD`, `PAYMENT`, `CRYPTO`)                     | Operator     | `?activityType=...`<br>`&status=...`<br>`&from=...`<br>`&to=...`<br>`&page=0&size=20&sort=createdAt,desc` | `200 OK`: `Page<TransactionDto>`                                            |
| **GET** | `/customers/{customerId}/transactions/{transactionId}` | Retrieves full polymorphic details for a specific transaction (card merchant info, payment IBANs, or crypto hashes) | Operator     | `None`                                                                                                    | `200 OK`: `TransactionDetailDto` — different JSON fields per activity type. |

## Acceptance Criteria

1. All three endpoints implemented with the queries/responses above; polymorphic detail returns activity-specific
   fields.
2. Frontend: search bar with a top-5 suggestion dropdown (initially top-5 alphabetically); paginated transaction table
   with per-column sort and filter; seamless detail view keyed to activity type.
3. An activity-type filter dropdown defaults to `ALL`; selecting `CARD` / `PAYMENT` / `CRYPTO` extends the table with
   that type's specific columns and enables filtering/sorting on them.

## Testing Scope

Backend: repository/query tests for pagination, filtering, sorting, and polymorphic detail mapping. Frontend: component
tests for search autocomplete, table paging/filtering, and the type-filter column extension.

## Risks / Open Questions

- Polymorphic serialization of the three activity types (sealed interface + Jackson discriminator) — verify round-trip.
- Efficient server-side paging/sorting across the base + child tables.
