# Phase 3 — Analytics Scaling Notes

Satisfies `PHASE_3.md` AC3: "Documentation of the optimization to implement when analytics computation become
intensive." This is a documentation deliverable only — no code change is implied for the current phase (see
`PHASE_3_PLAN.md` Clarification #3).

## Current approach and its ceiling

`AnalyticsService.findTimeSeries` fetches the already-filtered rows for the requested customer/range/type via the
existing `Specification`-based repositories (unpaged `findAll(Specification)`), then buckets and aggregates them
(count + amount-sum-by-currency) in plain Java. No Postgres-side `GROUP BY`/`date_trunc`, no new indexes, no
materialized views — deliberately, per `PHASE_3.md`'s own stated assumption: *"Initial low-load, so no query
optimization... [is] required."*

This is cheap while a customer's per-range row count stays in the hundreds/low-thousands: the unpaged fetch is a
single indexed query (`transactions(customer_id)` / `transactions(created_at)`, added in Phase 2), and the number of
output buckets is always small and bounded by the granularity's own range constraint (≤ ~31 for `DAY`, ≤ 30 for
`WEEK`, ≤ 24 for `MONTH`, ≤ 5 for `YEAR`).

## When to revisit

- A single customer's transaction volume per requested range grows into the tens of thousands of rows — the unpaged
  fetch itself (not the bucketing) becomes the bottleneck, since every matching row is pulled into application memory
  before being discarded into a handful of buckets.
- Analytics becomes a frequently-hit, latency-sensitive path (e.g. a dashboard auto-refreshing every few seconds)
  rather than an occasional operator lookup.
- Multi-year (`YEAR` granularity, up to 5 years) or cross-customer aggregate views are introduced — today's
  per-customer, per-request computation doesn't amortize across requests.

## Candidate optimizations, in likely adoption order

1. **Indexes on the child activity tables.** Phase 2 added `transactions(customer_id)` and `transactions(created_at)`
   on the base table; `card_activity`/`payment_activity`/`crypto_activity` only have their PK/FK index. A composite
   `(transaction_id)` lookup is already covered by the join, but if type-specific *filter* columns (e.g.
   `card_activity.card_type`) become common analytics filters at scale, a composite index per hot filter column would
   help.
2. **Push the aggregation into Postgres.** Replace the in-memory grouping with a `GROUP BY date_trunc(:granularity,
   created_at), currency` query (native or JPQL) per activity table/specification, returning only the aggregated rows
   instead of every matching transaction. This is the direct, low-risk next step — same data model, no new
   infrastructure — once the unpaged fetch is measurably the bottleneck.
3. **A materialized view** (e.g. `daily_transaction_summary(customer_id, activity_type, currency, day, tx_count,
   amount_sum)`), refreshed on a schedule (`REFRESH MATERIALIZED VIEW CONCURRENTLY`), for the most common
   granularity (`DAY`). `WEEK`/`MONTH`/`YEAR` buckets can be derived from the daily view by further grouping, still
   far cheaper than scanning raw transactions.
4. **TimescaleDB hypertables + continuous aggregates** if analytics becomes a first-class, high-frequency workload
   (e.g. near-real-time dashboards, cross-customer trend views) rather than an occasional per-customer operator
   lookup — the natural next step given `transactions` is already a time-series-shaped table, but a heavier
   infrastructure commitment than 1–3 above and not justified by the current low-load assumption.
