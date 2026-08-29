# Development Phase #3 - Analytics Features

## Project Design

### Assumptions

1. The Web Application doesn't manage transactions and users. These entities are read-only.
2. Only a simple analytics overview of transactions aggregated over a time period for reporting basic metrics grouped at
   difference granularities (day, week, month, year):
    - Count of transaction
    - Sum of transaction amounts by currency
3. Database is provided as part of the local environment and populated with test data for per purpose of the demo.

### Functional Requirements

| Functionality                   | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Customer Transactions Analytics | Two types of analytics are supported: transaction count distribution and transaction amount sum per currency distribution. These analytics can be scoped to a custom time period with a given granularity (day, week, month, year); default is 1 month by day. These analytics should be displayed as histograms or data points with trend lines in a graph. The time period range should constraint the choice of granularity: day granularity for 1 day to 1 month range; week granularity for 1 week to 30 weeks; month granularity for 1 month to 2 years; year granularity for 1 year to 5 years. |

### Non-Functional Requirements

| Requirement                   | Description                                                                                                                                                                                                                                                                                 |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Maintainability & Testability | Each module (backend REST layer, RAG/AI service, Angular frontend) should have a clear boundaries, follow clean architecture and clean code principles but prioritize simplifity and avoid unnecessary abstractions, and ensure testing of all features but not of details and boilerplate. |
| Usability                     | Pagination, filters, dropdown and any other frontend element the user interacts with must behave consistently across any component.                                                                                                                                                         |

### High-level APIs

The nature of this web application is suitable to RESTful APIs, for the communication between frontend and backed.

**Base Path:** `/api/v1`

| Method  | Endpoint Path                       | Description                                                                                   | Access Level | Request Query / Body                                                                        | Response Payload                   |
|---------|-------------------------------------|-----------------------------------------------------------------------------------------------|--------------|---------------------------------------------------------------------------------------------|------------------------------------|
| **GET** | `/customers/{customerId}/analytics` | Retrieves aggregate metrics (transaction counts and amount sums) bucketed by time granularity | Operator     | `?from=2026-01-01`<br>`&to=2026-08-29`<br>`&granularity=DAY` *(or `WEEK`, `MONTH`, `YEAR`)* | `200 OK`: `AnalyticsTimeSeriesDto` |

## Definition of Done

1. Implement the RESTful endpoint described above, with clean code principles in mind and using a fluent and idiomatic
   style. Ensure each feature is fully tested and the APIs provide correct metadata and error messages. Do not define
   custom exceptions if not required.
2. Define the PostgreSQL DB schema for the necessary tables, from the specifications, for this phase. Generate a script
   for provisioning data for local testing and demo.
3. Implement the Frontend using these APIs. The UI should be simple and easy to interact with. The aggregated metrics
   should be displayed in a graph that shows the distributions over the time-range selected. A date picker allows to
   select the range of dates for the time-range. A drop-down selection allows to switch between the types of
   aggregation: counts or amount sums. 
4. Define architectural rules, with ArchUnit as far as development goes. The rules should ensure that the packaging and
   modules are independent, coherent and keep a reasonable and simple balance between abstraction and concreteness. Do
   not over-engineer or complicate the structure. Ultimately, the project should be idiomatic and immediate. 
5. Ensure relevant logging, without affecting performance, and tracing is in place. 
6. The project should build and pass all the defined tests.