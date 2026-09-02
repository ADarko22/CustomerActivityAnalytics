# Phase 2 Implementation Plan — Transactions Features

**Status:** PLANNED

Blueprint for `PHASE_2.md`. Adds the first product data model and REST surface (customer search, transaction
overview, polymorphic transaction detail) on top of the Phase 1 scaffolding, plus the matching Angular UI. Read
alongside `CLAUDE.md` (conventions), `docs/specs/PROJECT_SPECIFICATION.md` (data model / API contract), and
`docs/DECISIONS.md` (D1, D5, D6, D7–D11 — all still apply).

## Current State (verified)

- Backend: only `CustomerActivityAnalyticsApplication`, `application.yml`, an empty `V1__baseline.sql`, and
  `ApplicationContextTest` (Testcontainers Postgres context-load). No domain packages, no controllers, no ArchUnit
  test yet (Phase 1 explicitly deferred "real rules" to Phase 2).
- Frontend: bare Angular 22 standalone workspace — `AppComponent` with just a `RouterOutlet`, empty `routes`,
  `app.config.ts` provides only `provideZoneChangeDetection` + `provideRouter`. **No `provideHttpClient`, no Material
  theme/animations provider, no FontAwesome package**, even though `@angular/material`/`@angular/cdk` are already
  dependencies and FontAwesome is named in `CLAUDE.md`'s tech stack — Phase 2 is the first phase that needs all of
  these.
- No `local-environment` seed mechanism exists yet beyond the reserved `postgresql/init/` folder (Phase 1 left it
  empty).

## Design clarifications (flagging for `/review PHASE_2 plan`, not silent contradictions)

1. **Postgres column types for `activity_type` / `status`.** `PROJECT_SPECIFICATION.md` labels these `ENUM` in the
   data-model tables. This plan uses `VARCHAR` + a `CHECK` constraint (not a native Postgres `ENUM` type), mapped to
   a Java `enum` via `@Enumerated(STRING)` / a discriminator column. Native Postgres enums have known friction with
   Hibernate's discriminator-column mapping; a checked `VARCHAR` enforces the same closed set of values without that
   friction. Precedent: `docs/DECISIONS.md` D6 already reconciles a data-model ambiguity this way. Not treated as a
   PDF/spec contradiction (the spec doesn't mandate the physical column type), but flagged per the "flag, don't
   silently proceed" instruction since it deviates from the literal table wording.
2. **One `TransactionDto` sealed family for both the list and detail endpoints.** `PROJECT_SPECIFICATION.md`'s API
   table names two payloads — `Page<TransactionDto>` for the overview and `TransactionDetailDto` for the single-
   transaction endpoint — implying richer detail fields. Phase 2's data model has no fields that exist *only* for
   detail (card's `authorization_code`/`decline_reason` are the closest candidates, but PHASE_2 AC3 requires the
   *overview table itself* to expose type-specific columns once a type filter is selected, which only works cleanly
   if the list payload already carries them). This plan therefore implements a single sealed `TransactionDto` family
   (one Java type per activity type, full field set) reused for both endpoints — the list returns a `Page` of it, the
   detail endpoint returns one instance directly. This keeps one mapping path and avoids a near-duplicate
   `TransactionDetailDto` hierarchy carrying the same fields. Flagged for reviewer sign-off since it merges two
   spec-named payload shapes into one.
3. **Type-specific query parameters are not enumerated in `PHASE_2.md`'s API table** (only `activityType`, `status`,
   `from`, `to`, `page`, `size`, `sort` are listed), but AC3 requires filtering/sorting on the extended columns once a
   type is selected. This plan adds one optional query parameter per activity-specific column (e.g. `cardType`,
   `merchantName`, `mccCode`, `cardPresent` for `CARD`) as the natural, minimal completion of AC3 — not a
   contradiction, just filling an acknowledged gap. The same gap applies to the two *common* overview columns
   `amount`/`currency` (AC2's "per-column filters"), so this plan also adds `minAmount`/`maxAmount` and `currency` as
   common query parameters (§ Filtering & sorting) alongside `status`/`from`/`to`.
4. **Customer search must cover both name and Customer ID.** `PROJECT_SPECIFICATION.md` Feature 1 requires searching
   "by Customer ID"; `PHASE_2.md`'s functional-requirement wording only describes autocomplete/alphabetical behavior,
   which reads as name-oriented and doesn't repeat "by ID." Rather than silently picking one, this plan implements
   both in the same `query` parameter (§ Repositories): a single search matches `firstName`/`lastName` substrings
   **or** a prefix match against the customer ID's string form, so pasting a full or partial UUID and typing a name
   both work through the one `GET /customers?query=` contract already defined in `PHASE_2.md`'s API table. No API
   shape change needed, so this is a resolution, not an open question — flagged here for reviewer visibility since it
   reconciles a spec/phase-doc gap rather than being spelled out in either source.
5. **"...or hovering on it" (`PROJECT_SPECIFICATION.md` Feature 2) vs. "on selecting" (`PHASE_2.md`'s functional
   requirement).** The phase doc's narrower wording is adopted for the primary, full detail card (`transactions/
   transaction-detail`, unchanged), but a lightweight `matTooltip` on each table row (§ Frontend Design) restores the
   spec's hover behavior with a one-line summary (status, amount, currency) — no new dependency, `MatTooltipModule` is
   part of the already-referenced `@angular/material`. This reconciles both wordings instead of dropping the spec's
   requirement silently. Recording this as a durable decision (new `docs/DECISIONS.md` entry, e.g. `D12`) is assigned
   as a Phase 2 **implementation** task (§ File inventory), following the precedent set by `PHASE_1_PLAN.md` §G of
   deferring doc-reconciliation edits to `/implement` time rather than making them during `/plan`.

## Backend Design

### Data model / persistence

- JPA `JOINED` inheritance maps directly onto the spec's base+child table shape (each activity table's PK is also an
  FK to `transactions`):
  - `Transaction` — abstract `@Entity`, `@Inheritance(strategy = InheritanceType.JOINED)`,
    `@DiscriminatorColumn(name = "activity_type")`, `@Table(name = "transactions")`. Fields: `transactionId` (`@Id`),
    `customerId` (or a `@ManyToOne Customer`; use a plain `UUID customerId` column + no bidirectional association —
    Phase 2 never needs `Customer → Transaction` navigation, so skip the relationship mapping per the simplicity
    guideline), `amount`, `currency`, `status` (`TransactionStatus` enum), `createdAt`.
  - `CardActivity extends Transaction` `@DiscriminatorValue("CARD")` `@Table(name = "card_activity")` — adds
    `cardPan`, `cardType`, `merchantName`, `mccCode`, `cardPresent`, `authorizationCode`, `declineReason`.
  - `PaymentActivity extends Transaction` `@DiscriminatorValue("PAYMENT")` `@Table(name = "payment_activity")` — adds
    `paymentMethod`, `senderAccount`, `receiverAccount`, `receiverBankCountry`.
  - `CryptoActivity extends Transaction` `@DiscriminatorValue("CRYPTO")` `@Table(name = "crypto_activity")` — adds
    `blockchain`, `walletAddressFrom`, `walletAddressTo`, `txHash`, `exchangeName`.
  - **Entities are plain (non-sealed) classes.** CLAUDE.md's "model with sealed interfaces" instruction targets DTOs;
    sealing the JPA hierarchy risks Hibernate/ByteBuddy proxy-generation friction with `permits`-restricted
    subclasses. Polymorphic-to-DTO mapping instead uses an `instanceof`/pattern-matching `switch` (Java 25) in the
    mapper, which *does* get exhaustiveness-checked against the sealed `TransactionDto` output.
  - `Customer` — plain `@Entity`: `customerId` (`@Id`), `firstName`, `lastName`.
- **Known perf tradeoff (documented per Global DoD, not solved now):** `JOINED` inheritance means a base-type query
  (`activityType=ALL`) executes an outer join across all three child tables to resolve each row's concrete type. Fine
  at demo scale (PHASE_2.md's own stated assumption); flagged in Risks below as a future optimization if row counts
  grow (candidate fixes: `SINGLE_TABLE` inheritance, or a dedicated read-projection/view).

### Repositories

- `CustomerRepository extends JpaRepository<Customer, UUID>` with one `@Query` search method matching, per
  Clarification #4: `lower(firstName)`/`lower(lastName)` case-insensitive substring **or** `str(customerId)` (HQL's
  string-cast function) prefix match against the raw query text; empty/blank query matches everything (`:query = ''
  or ...`). No `ORDER BY` embedded in the query — alphabetical default sort and the top-5 cap (Clarification/finding
  on AC2's "top 5 matches") are both supplied by the controller's `@PageableDefault(size = 5, sort = {"lastName",
  "firstName"})`, so the top-5 limit holds even if a caller omits `size`.
- `TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction>` — used only
  for `activityType=ALL` and for the detail endpoint's initial by-id lookup (Hibernate resolves the concrete subclass
  transparently on `findById`).
- `CardActivityRepository extends JpaRepository<CardActivity, UUID>, JpaSpecificationExecutor<CardActivity>` (and the
  `Payment`/`Crypto` equivalents) — used when `activityType` narrows to one type, so type-specific columns (inherited
  attributes included, since `JOINED` subclasses expose parent fields on the same Java type) are filterable/sortable
  through the same `Specification`+`Pageable` mechanism.

### Filtering & sorting

- `org.springframework.data.jpa.domain.Specification` (already available via `spring-boot-starter-data-jpa`, no new
  dependency) — one small generic helper, `TransactionSpecifications`, building the customer-id/status/date-range
  **and `minAmount`/`maxAmount`/`currency`** predicates (all common overview columns, per Clarification #3) against
  any `Root<? extends Transaction>`; one specification class per activity type (`CardActivitySpecifications`, etc.)
  adding that type's own predicates and composing with the shared ones via `Specification.allOf(...)`.
- **Sort-field allowlisting.** Spring Data binds the `sort=field,dir` query param straight into a `Sort` object with
  no validation — passing it through unchecked would let a caller probe arbitrary entity properties. `TransactionService`
  validates the requested sort property names against a small per-type allowlist (common fields always allowed; type
  fields allowed only when that type is selected) and returns `400` (`ResponseStatusException`, no custom exception
  class) on an unknown property.
- `application.yml` gets `spring.data.web.pageable.max-page-size` (e.g. `100`) to bound `size`.

### Service & controller layer

- `CustomerService` — `search(query, pageable) -> Page<CustomerDto>`; also exposes `requireExists(customerId)` (used
  by `TransactionService` to 404 early on an unknown customer — `ResponseStatusException(NOT_FOUND)`).
- `TransactionService`:
  - `findOverview(customerId, activityType, status, from, to, typeFilters, pageable) -> Page<TransactionDto>` —
    dispatches to `TransactionRepository` (ALL) or the matching `XActivityRepository` (specific type), applies
    specifications, maps entities to the sealed `TransactionDto` via `TransactionMapper`.
  - `findDetail(customerId, transactionId) -> TransactionDto` — `TransactionRepository.findById`, verify
    `customerId` matches (else 404, don't leak cross-customer existence), map via the same `TransactionMapper`.
- `CustomerController` — `GET /api/v1/customers` (`query`, `page`, `size`; `@PageableDefault(size = 5)` so the
  autocomplete's top-5 cap holds by default per Clarification #4).
- `TransactionController` — `GET /api/v1/customers/{customerId}/transactions` (`activityType`, `status`, `from`,
  `to`, `minAmount`, `maxAmount`, `currency`, the per-type optional filters from Clarification #3,
  `page`/`size`/`sort` via `Pageable`); `GET /api/v1/customers/{customerId}/transactions/{transactionId}`.
- `application.yml` gets `spring.mvc.problemdetails.enabled: true` so the built-in `ResponseStatusException` 404/400s
  serialize as RFC 7807 `application/problem+json` — no custom exception types needed, satisfies the Global DoD's
  "correct API metadata and error messages" without new abstractions.

### Logging & tracing (Global DoD — first phase where it applies)

Phase 1 shipped no business logic, so this Global DoD line was moot; Phase 2 is the first phase with real
services/controllers, so it's addressed concretely here rather than left implicit:

- Standard SLF4J (`org.slf4j.Logger`, already transitively provided by Spring Boot) in `CustomerService` and
  `TransactionService` — one `INFO` line per inbound request logging the *non-PII* shape of the call (customer ID,
  activity-type filter, page/size, applied-filter *count*), and one `DEBUG` line including the actual filter values.
  This keeps default-level logs free of customer names / search text (PII) while still giving an operator visibility
  into request volume and shape at `INFO`.
- No new tracing dependency (e.g. Micrometer Tracing/Zipkin) is introduced in Phase 2 — a single Spring Boot service
  with no downstream service calls yet has nothing to distributedly trace. Spring Boot's default Logback pattern
  already includes thread + timestamp for correlating log lines within one request; revisit real distributed tracing
  when Phase 4 introduces the AI provider call chain (SSE streaming, RAG retrieval) where multi-step correlation
  actually matters.
- `ResponseStatusException` 404/400 paths (unknown customer/transaction, invalid sort field) log at `WARN` with the
  offending identifier/field name (not request bodies), so operator-facing errors are diagnosable without a debugger.
- No performance impact: all logging is at `INFO`/`DEBUG`/`WARN` via the existing SLF4J/Logback stack already on the
  classpath — no new appenders, no synchronous remote log shipping.

### DTOs (sealed + Jackson discriminator — CLAUDE.md coding standard #2)

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "activityType", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CardTransactionDto.class, name = "CARD"),
    @JsonSubTypes.Type(value = PaymentTransactionDto.class, name = "PAYMENT"),
    @JsonSubTypes.Type(value = CryptoTransactionDto.class, name = "CRYPTO"),
})
public sealed interface TransactionDto permits CardTransactionDto, PaymentTransactionDto, CryptoTransactionDto {
  UUID transactionId(); UUID customerId(); ActivityType activityType();
  BigDecimal amount(); String currency(); TransactionStatus status(); Instant createdAt();
}
```
- `CardTransactionDto`, `PaymentTransactionDto`, `CryptoTransactionDto` — records implementing the interface, common
  fields repeated (records can't inherit state) plus their own type-specific fields.
- `CustomerDto(UUID customerId, String firstName, String lastName)`.
- `TransactionMapper` — static methods, one `switch` over the entity's runtime type (Java 25 pattern-matching switch,
  exhaustive against the entity hierarchy) producing the matching sealed DTO variant.

## Local environment / migrations

- `backend/src/main/resources/db/migration/V2__customer_transaction_schema.sql` — creates `customers`, `transactions`
  (with the `activity_type` `CHECK` constraint per Clarification #1, and — per `PROJECT_SPECIFICATION.md`'s data
  model, `customer_id UUID (FK → customers)` — an explicit `transactions.customer_id REFERENCES
  customers(customer_id)` foreign key), `card_activity`, `payment_activity`, `crypto_activity`, each child table's PK
  also an FK to `transactions(transaction_id)` (matches the `JOINED` mapping). Indexes: `transactions(customer_id)`,
  `transactions(created_at)` at minimum (needed for the default sort/pagination).
- `backend/src/main/resources/db/seed/R__seed_demo_data.sql` (new `db/seed` folder) — a repeatable Flyway migration
  with a handful of demo customers and a spread of `CARD`/`PAYMENT`/`CRYPTO` transactions across statuses and dates,
  enough to exercise pagination, filtering, and sorting in the demo/UI by hand.
- `backend/src/main/resources/application-local.yml` (new) — `spring.flyway.locations:
  classpath:db/migration,classpath:db/seed`, so seed data only loads under the `local` profile; the default
  (non-local) profile — including CI's Testcontainers-backed tests — never runs it, keeping repository tests in
  control of their own fixtures.
- Backend tests that need seeded rows insert their own fixtures per test (via the repository under test), independent
  of the `local` seed script.
- **Activating the `local` profile.** Nothing in the repo currently sets `spring.profiles.active`/
  `SPRING_PROFILES_ACTIVE` anywhere (verified: no reference exists in any `.yml`/`.kts` file), so `application-
  local.yml` alone would never be picked up by the `./gradlew dev` flow from `PHASE_1_PLAN.md` §E, and the seed data
  would silently never load — a concrete gap in the prior plan revision. Fix: `frontend/build.gradle.kts`'s `dev`
  `NpxTask` command (§E of `PHASE_1_PLAN.md`) is modified to prefix the backend leg with the env var, i.e.
  `"SPRING_PROFILES_ACTIVE=local ./gradlew :backend:bootRun"` instead of the current `"./gradlew :backend:bootRun"`.
  This only affects local `bootRun`; CI/Testcontainers-backed tests never set this profile and are unaffected.

## Frontend Design

- New dependencies: `@fortawesome/angular-fontawesome`, `@fortawesome/fontawesome-svg-core`,
  `@fortawesome/free-solid-svg-icons` (CLAUDE.md names FontAwesome; Phase 1 added the Angular/Material packages but
  never an icon set). Angular Material (`@angular/material`, `@angular/cdk`) is already a dependency but unused so
  far — Phase 2 is first to consume `MatTable`, `MatPaginator`, `MatSort`, `MatAutocomplete`, `MatSelect`,
  `MatFormField`, `MatCard`.
- `app.config.ts` — add `provideHttpClient(withFetch())` (nothing calls the backend yet) and
  `provideAnimationsAsync()` (Material requires it).
- `styles.scss` — add a Material theme (`@use '@angular/material' as mat;` + `mat.theme(...)` or a prebuilt theme),
  currently entirely missing.
- Models (`frontend/src/app/core/models/`):
  - `page.model.ts` — generic `Page<T>` interface matching Spring Data's JSON `Page` shape (`content`,
    `totalElements`, `totalPages`, `number`, `size`).
  - `customer.model.ts` — `Customer { customerId; firstName; lastName }`.
  - `transaction.model.ts` — `ActivityType`, `TransactionStatus` string unions, a `BaseTransaction` field set, and a
    discriminated union `Transaction = CardTransaction | PaymentTransaction | CryptoTransaction` keyed on
    `activityType`, mirroring the backend sealed DTOs field-for-field.
- Services (`frontend/src/app/core/services/`): `customer.service.ts` (`search(query, page, size)`),
  `transaction.service.ts` (`findOverview(customerId, filters, pageable)`, `findDetail(customerId, transactionId)`) —
  thin `HttpClient` wrappers building `HttpParams` from the filter/pagination state.
- Components (`frontend/src/app/features/`):
  - `customer-search/` — `MatAutocomplete`-backed search box; debounced (300 ms) input via RxJS; every request
    (including the empty-input default) explicitly calls `customerService.search(query, 0, 5)` so the top-5 cap
    (Clarification #4) is enforced from the client regardless of the server-side `@PageableDefault`; selecting a
    suggestion navigates to `/customers/:customerId/transactions`.
  - `transactions/transaction-table/` — `MatTable` + `MatPaginator` (server-side, driven by `(page)` event) +
    `MatSort` (driven by `(matSortChange)`, mapped to the `sort` query param); an activity-type `MatSelect` (default
    `ALL`) that both adds/removes the type-specific columns and re-issues the query with the new `activityType`; a
    per-column filter row (text inputs for strings/dates and the new `minAmount`/`maxAmount`/`currency` range/exact
    filters, a select for enum-like columns), each debounced 300 ms. Column definitions are a small typed config
    array per activity type (base columns + that type's extra columns), not one-off template conditionals, so adding
    a column stays a data change. Each row also carries a `matTooltip` with a one-line summary (status, amount,
    currency) per Clarification #5, satisfying the spec's hover behavior without duplicating the full detail card.
  - `transactions/transaction-detail/` — renders on row selection; one template branch per `activityType`
    (`@switch` control flow) showing that type's full field set in a `MatCard`.
  - `transactions/transactions-page/` — route-level container: reads `customerId` from the route, owns the shared
    filter/pagination signal state, wires the table and detail child components together.
- `app.routes.ts` — `{ path: 'customers/:customerId/transactions', component: TransactionsPageComponent }`; root
  path shows an empty-state prompt ("search for a customer") when no customer is selected.
- `app.component.html`/`.ts` — hosts a persistent header with `CustomerSearchComponent` plus the `<router-outlet>`
  for the transactions page, so search stays available while browsing a customer's activity.

## ArchUnit (new — `backend/src/test/java/.../ArchitectureTest.java`)

- No cyclic dependencies between the `customer` and `transaction` top-level packages
  (`SlicesRuleDefinition.slices().matching("..customeractivityanalytics.(*)..").should().beFreeOfCycles()`).
- Classes named `*Repository` are only depended on by classes named `*Service` or other `*Repository` classes (not by
  `*Controller` classes) — keeps controllers off the persistence layer.
- Classes named `*Controller` reside directly in `customer`/`transaction` (not nested deeper) and don't depend on
  JPA (`jakarta.persistence..`) types — controllers only see DTOs.

## File inventory

**Backend — new:** `customer/{Customer,CustomerRepository,CustomerService,CustomerController}.java`,
`customer/dto/CustomerDto.java`; `transaction/{ActivityType,TransactionStatus,Transaction,TransactionRepository,
TransactionService,TransactionController,TransactionSpecifications}.java`; `transaction/dto/{TransactionDto,
CardTransactionDto,PaymentTransactionDto,CryptoTransactionDto,TransactionMapper}.java`;
`transaction/card/{CardActivity,CardActivityRepository,CardActivitySpecifications}.java` (+ `payment/`, `crypto/`
equivalents); `db/migration/V2__customer_transaction_schema.sql`; `db/seed/R__seed_demo_data.sql`;
`application-local.yml`; `test/.../ArchitectureTest.java`, `.../customer/CustomerRepositoryTest.java`,
`.../transaction/{TransactionRepositoryTest,CardActivityRepositoryTest,PaymentActivityRepositoryTest,
CryptoActivityRepositoryTest,TransactionServiceTest,TransactionControllerTest}.java`,
`.../customer/CustomerControllerTest.java`.

**Backend — modified:** `application.yml` (pageable max size, problemdetails, default page size).

**Frontend — new:** `core/models/{page,customer,transaction}.model.ts`; `core/services/{customer,transaction}.
service.ts` (+ `.spec.ts`); `features/customer-search/*`; `features/transactions/{transaction-table,
transaction-detail,transactions-page}/*` (each with `.ts/.html/.scss/.spec.ts`).

**Frontend — modified:** `package.json` (FontAwesome deps), `app.config.ts` (HttpClient, animations),
`app.routes.ts`, `app.component.ts/html`, `styles.scss` (Material theme), **`frontend/build.gradle.kts`** (`dev`
task's backend leg gains the `SPRING_PROFILES_ACTIVE=local` prefix, per the seed-activation fix above).

**Documentation reconciliation (assigned as an `/implement`-time task, mirroring `PHASE_1_PLAN.md` §G):**
`docs/DECISIONS.md` gains a new entry (`D12`) recording the hover-vs-select reconciliation from Clarification #5
(tooltip restores the spec's hover behavior; the full detail card stays select-only, matching `PHASE_2.md`'s wording)
— done in the same commit as the `matTooltip` implementation, not during `/plan`.

**Local environment:** none beyond the new `db/seed` folder (already covered above); `local-environment/` itself is
unchanged in Phase 2.

## Test plan → Acceptance-criteria mapping

| `PHASE_2.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — all three endpoints, correct queries/responses, polymorphic detail | `CustomerControllerTest`, `TransactionControllerTest` (per-type JSON discriminator shape via `jsonPath`, 404s for unknown customer/transaction), `*RepositoryTest` (pagination, filtering incl. `minAmount`/`maxAmount`/`currency`, sorting per type incl. sort-allowlist 400) | `customer.service.spec.ts`, `transaction.service.spec.ts` (correct `HttpParams` built, response parsed into the discriminated union) |
| AC2 — search dropdown (top-5, alphabetical default, by name or ID), paginated/sortable/filterable table (incl. amount/currency), seamless detail + hover | `CustomerRepositoryTest` (blank-query default sort, name substring match, customer-ID prefix match per Clarification #4, default `size=5`) | `customer-search.component.spec.ts` (debounce, `size=5` request, top-5 render, empty-input default list), `transaction-table.component.spec.ts` (paginator → correct page/size request, sort click → correct `sort` param, filter input incl. amount/currency → correct query params, row `matTooltip` present), `transaction-detail.component.spec.ts` (renders on selection) |
| AC3 — type filter defaults `ALL`; selecting a type extends columns + enables filter/sort on them | `CardActivityRepositoryTest`/`PaymentActivityRepositoryTest`/`CryptoActivityRepositoryTest` (type-specific filter/sort queries) | `transaction-table.component.spec.ts` (selecting a type adds the expected columns and re-issues the request with the new `activityType` + type filters) |

Backend testing also covers the Global DoD: `ArchitectureTest` (package/layer rules), Testcontainers Postgres for all
repository-level tests (D10), Spotless formatting unaffected (no new tooling).

## Risks / Open Questions (carried from `PHASE_2.md`, resolved or narrowed where possible)

- **Polymorphic serialization round-trip** — addressed by the `@JsonTypeInfo`/`@JsonSubTypes` config above; a
  dedicated `TransactionDto`-per-type serialization test (in `TransactionControllerTest`) verifies the discriminator
  round-trips correctly.
- **Efficient server-side paging/sorting across base + child tables** — `JOINED` inheritance's base-type query joins
  all three child tables; acceptable at PHASE_2.md's stated demo scale. Left as a documented future optimization
  (candidate: `SINGLE_TABLE` inheritance or a read-side projection/view) rather than solved now, consistent with how
  PHASE_3.md handles its own analytics-scaling question (AC3 there explicitly asks only for *documentation* of the
  future optimization).
- **New:** introducing `provideHttpClient`, Material's animation provider, and a Material theme all for the first
  time in Phase 2 — verify bundle-size budgets in `angular.json` (currently 500 kB warning / 1 MB error) still hold
  once Material + FontAwesome are added; adjust budgets if `ng build` warns/fails.
