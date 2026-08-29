# Development Phase #2 - Transactions Features

## Project Design

### Assumptions

1. The Web Application doesn't manage transactions and users. These entities are read-only.
2. Database is provided as part of the local environment and populated with test data for per purpose of the demo.

### Functional Requirements

| Functionality                  | Description                                                                                                                                                          |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Customer Search                | Simple search with drop-down selection limited to 5 (to facilitate the demo); use autocomplete based on the input (when empty return just first results)             |
| Customer Transactions Overview | When a customer is selected a paginated table shows the transactions sorted by the most recent. The table provides filters and sorting on the column headers.        |
| Customer Transaction Detail    | When selecting a transaction the whole details are displayed in a top level card. The details are specific to the activity type, which must be handled consistently. |

### Non-Functional Requirements

| Requirement                   | Description                                                                                                                                                                                                                                                                                 |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| Maintainability & Testability | Each module (backend REST layer, RAG/AI service, Angular frontend) should have a clear boundaries, follow clean architecture and clean code principles but prioritize simplifity and avoid unnecessary abstractions, and ensure testing of all features but not of details and boilerplate. |
| Usability                     | Pagination, filters, dropdown and any other frontend element the user interacts with must behave consistently across any component.                                                                                                                                                         |

### High-level APIs

The nature of this web application is suitable to RESTful APIs, for the communication between frontend and backed.
**Base Path:** `/api/v1`

| Method  | Endpoint Path                                          | Description                                                                                                         | Access Level | Request Query / Body                                                                                      | Response Payload                                                                                     |
|---------|--------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| **GET** | `/customers`                                           | Searches and retrieves a paginated list of customers (for search bar autocomplete)                                  | Operator     | `?query=abc`<br>`&page=0&size=10`                                                                         | `200 OK`: `Page<CustomerDto>`                                                                        |
| **GET** | `/customers/{customerId}/transactions`                 | Retrieves paginated transaction history across all activity types (`CARD`, `PAYMENT`, `CRYPTO`)                     | Operator     | `?activityType=...`<br>`&status=...`<br>`&from=...`<br>`&to=...`<br>`&page=0&size=20&sort=createdAt,desc` | `200 OK`: `Page<TransactionDto>`                                                                     |
| **GET** | `/customers/{customerId}/transactions/{transactionId}` | Retrieves full polymorphic details for a specific transaction (Card merchant info, Payment IBANs, or Crypto hashes) | Operator     | `None`                                                                                                    | `200 OK`: `TransactionDetailDto`<br/>Polymorphism: different Json fields based on the activity type. |

## Definition of Done

1. Implement the RESTful endpoint described above, with clean code principles in mind and using a fluent and idiomatic
   style. Ensure each feature is fully tested and the APIs provide correct metadata and error messages. Do not define
   custom exceptions if not required.
2. Define the PostgreSQL DB schema for the necessary tables, from the specifications, for this phase. Generate a script
   for provisioning data for local testing and demo.
3. Implement the Frontend using these APIs. The UI should be simple and easy to interact with. A search bar is expected
   for selecting a customer. A simple dropdown can suggest the top 5 best matches (initially the top 5 alphabetically).
   Once a customer is selected, a table shows all its transactions by page. Only the basic transactions info are
   displayed. When selecting a transaction a detailed view, integrated seamlessly in the UI, shows more info according
   to the transaction activity type. Each column of the transaction can be used for sorting and filtering.
4. A dropdown selection menu should also be available to apply a general filter by activity type. By default, it is set
   to `ALL` and defines the behavior describe previously. When a different value is displayed, one of `CARD`, `PAYMENT`,
   or `CRYPTO`, the table is extended with the fields specific to that activity type. This allows to apply further
   filtering and sorting on more specific properties.
5. Define architectural rules, with ArchUnit as far as development goes. The rules should ensure that the packaging and
   modules are independent, coherent and keep a reasonable and simple balance between abstraction and concreteness. Do
   not over-engineer or complicate the structure. Ultimately, the project should be idiomatic and immediate.
6. Ensure relevant logging, without affecting performance, and tracing is in place.
7. The project should build and pass all the defined tests.