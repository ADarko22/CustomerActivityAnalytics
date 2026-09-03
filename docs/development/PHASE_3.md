# Phase 3 — Analytics Features

**Status:** COMPLETE
**Depends on:** Phase 2 — reuses the transaction data model and customer selection.

## Objective

Give the operator aggregated insight over a customer's transactions: count distribution and amount-sum-by-currency,
over a custom time range and granularity, rendered as a graph.

## Scope

- **In:** the analytics endpoint and its frontend graph; time-range + granularity controls.
- **Out:** AI risk assessment (Phase 4), auth (Phase 5).
- **Assumptions:** transactions/customers are read-only and seeded; analytics are a simple overview (count of
  transactions; sum of amounts by currency) grouped by day/week/month/year. Initial low-load, so no query optimization,
  such as indexes or materialized views are required.

## Requirements (refs into `PROJECT_SPECIFICATION.md`)

- Feature **3** (aggregated statistics over custom time ranges, with common-property filters, and activity-specific
  filters when a single type is selected).

## Functional Requirements

| Functionality                   | Description                                                                                                                                                                                                                                                                                                                                                     |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Customer Transactions Analytics | Two analytics: transaction-count distribution and amount-sum-per-currency distribution. Scoped to a custom time period at a chosen granularity (day/week/month/year; default 1 month by day), shown as histograms or trend-line graphs. Granularity is constrained by range: day → 1 day–1 month; week → 1–30 weeks; month → 1 month–2 years; year → 1–5 years. |

## High-level APIs — Base Path `/api/v1`

| Method  | Endpoint Path                       | Description                                                                                   | Access Level | Request Query / Body                                                                        | Response Payload                   |
|---------|-------------------------------------|-----------------------------------------------------------------------------------------------|--------------|---------------------------------------------------------------------------------------------|------------------------------------|
| **GET** | `/customers/{customerId}/analytics` | Retrieves aggregate metrics (transaction counts and amount sums) bucketed by time granularity | Operator     | `?from=2026-01-01`<br>`&to=2026-08-29`<br>`&granularity=DAY` *(or `WEEK`, `MONTH`, `YEAR`)* | `200 OK`: `AnalyticsTimeSeriesDto` |

## Acceptance Criteria

1. The analytics endpoint returns both metrics bucketed by the requested granularity and enforces the
   range ↔ granularity constraints above.
2. Frontend: a graph of the distribution over the selected range, a date picker for the range, and a dropdown to switch
   aggregation type (counts vs amount sums).
3. Documentation of the optimization to implement when analytics computation become intensive; i.e. aggregate over
   multiple years, requiring indexing, materialized views or real-time aggregation with TimescaleDB.

## Testing Scope

Backend: aggregation/bucketing correctness per granularity, boundary cases on the range ↔ granularity constraints,
amount-sum-by-currency grouping. Frontend: date-picker + aggregation-toggle behavior and graph rendering.

## Risks / Open Questions

- Time-bucket aggregation across time zones and partial buckets at range edges.
- Multi-currency sums must stay separated (no cross-currency totals).
