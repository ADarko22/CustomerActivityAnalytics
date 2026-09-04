# Phase 5 Implementation Plan — Cross-Cutting Concerns (Auth & Risk-Rule Administration)

**Status:** PLANNED

Blueprint for `PHASE_5.md`. Replaces the temporary `permitAll` `SecurityConfig` (D13) with real OAuth2/OIDC
(Authorization Code + PKCE against a local Keycloak instance, D2), role-based authorization across every existing
`/api/v1/**` endpoint, a new `GET /me` current-user endpoint, and full CRUD over `risk_rules` gated to the `ADMIN`
role — plus the corresponding Angular login/logout flow and a new "Administration" section. Read alongside
`CLAUDE.md` (conventions), `docs/specs/PROJECT_SPECIFICATION.md` (Feature 8 / `risk_rules` data model), and
`docs/DECISIONS.md` (D2, D13 apply directly; D2 already covers the core Keycloak/role-split mechanism, but this plan
proposes one new decision — D21 — for a beyond-D2 UX interpretation it introduces, per Clarification #7).

## Current State (verified)

- `SecurityConfig` (`backend/.../config/SecurityConfig.java`) is a single `permitAll()` filter chain, explicitly
  documented (D13) as a placeholder "until Phase 5." `spring-boot-starter-security-oauth2-resource-server` has been
  on the backend classpath since Phase 1, unused beyond that placeholder.
- Every existing `@WebMvcTest` controller slice (`TransactionControllerTest`, `CustomerControllerTest`,
  `AnalyticsControllerTest`, `AnalyticsConfigControllerTest`, `AiRiskAssessmentControllerTest`) explicitly
  `@Import(SecurityConfig.class)` to get the permit-all chain into the slice context. Replacing `SecurityConfig`'s
  behavior means every one of these five test classes must add an authenticated-JWT post-processor to its existing
  `MockMvc` calls, or they will start failing with `401` — this is the single largest blast-radius item in this
  phase and is called out explicitly in the file inventory below, not left implicit.
- `spring-security-test` (providing `SecurityMockMvcRequestPostProcessors.jwt()`) is already transitively present on
  the backend test classpath via `spring-boot-starter-security-oauth2-resource-server-test` (verified via
  `./gradlew :backend:dependencies --configuration testCompileClasspath`) — no new test dependency needed.
  `hibernate-validator`/`jakarta.validation-api` are likewise already transitively present (via `spring-ai`), so
  `@Valid`/`@NotBlank`/`@DecimalMin` on the new risk-rule write DTOs need no new dependency either.
- `risk_rules` (schema + repository + entity + seed rows) already exists in full from Phase 4
  (`V3__risk_assessment_schema.sql`, `RiskRule`, `RiskRuleRepository`, `RuleScope`) — read-only until now. This
  phase adds create/update/delete on top of the existing table; **no new Flyway migration is needed** (the Global
  DoD's "a PostgreSQL schema plus a local demo/data-seed script for the phase's tables" is satisfied by the schema
  and seed rows Phase 4 already shipped for this exact table).
- `local-environment/keycloak/` is an empty placeholder (`.gitkeep` only), reserved since Phase 4's plan explicitly
  called it out as "reserved for Phase 5." `local-environment/docker-compose.yml` currently provisions only
  `postgres` and `wiremock`.
- Frontend: `angular-oauth2-oidc` (`^22.0.2`) is already an installed `dependency` in `frontend/package.json` (added
  ahead of need, like the backend's resource-server starter) but is not imported or configured anywhere yet.
  `app.config.ts` uses `provideHttpClient(withFetch())` with no `HTTP_INTERCEPTORS`/`withInterceptorsFromDi()`, and
  `app.routes.ts` has no route guards. `app.component.html` renders only a title and the customer-search
  autocomplete — no header nav, no user-info/logout affordance. There is no `environment.ts` abstraction anywhere in
  this codebase (config literals live directly in source, e.g. `proxy.conf.json`'s hardcoded backend URL); this plan
  follows that existing convention rather than introducing one.
- Both `RiskRuleDto`-shaped read and the two new write DTOs have no existing frontend model/service — this is a new
  `core/models`/`core/services` pair plus a new `features/administration/` folder, following the one-feature-folder-
  per-resource shape every prior phase established (e.g. `features/risk-assessment/`).

## Design clarifications (flagging for `/review PHASE_5 plan`, not silent contradictions)

1. **`jwk-set-uri`, not `issuer-uri`, for the resource-server JWT decoder.** Spring Boot's
   `issuer-uri`-based autoconfiguration (`JwtDecoders.fromIssuerLocation(...)`) performs a **synchronous HTTP call
   to Keycloak's discovery document during `JwtDecoder` bean creation** — i.e. at application-context startup. That
   would break every full-context test (`ApplicationContextTest`, `AiRiskAssessmentWireMockReplayTest`) whenever
   Keycloak isn't running, which is exactly the case in CI/`./gradlew check` (only Postgres is provisioned via
   Testcontainers per D10; Keycloak is not). `jwk-set-uri`-based construction (`NimbusJwtDecoder.withJwkSetUri(...)`)
   is lazy — the JWKS fetch happens only on the first real token decode — so the context loads fine without a live
   Keycloak, and only tests that actually exercise an authenticated request (which use the `jwt()` test
   post-processor, bypassing the decoder entirely) need Keycloak-shaped tokens at all. One `jwk-set-uri` value
   (`http://localhost:8081/realms/customer-activity-analytics/protocol/openid-connect/certs`, env-overridable) is
   set in `application.yml` directly — no `local`-profile override needed, since Keycloak's Docker Compose port is
   published to the host the same way for both the backend (dev, running on the host) and the browser.
2. **No live Keycloak in automated backend tests; authorization matrix is verified via `SecurityMockMvcRequestPostProcessors.jwt()`.**
   `PHASE_5.md`'s Testing Scope ("authorization matrix: operator vs admin across read vs write, token/role mapping,
   `/me` projection") is satisfiable entirely at the `@WebMvcTest` slice level by minting a fake `Jwt` with
   `ROLE_OPERATOR`/`ROLE_ADMIN` granted authorities via the test post-processor — this exercises the exact same
   `JwtAuthenticationConverter` → `SecurityFilterChain` authorization logic that runs against a real Keycloak token,
   without adding a Keycloak Testcontainer (D10 only covers Postgres; adding a second, heavier container for this
   phase's test suite is disproportionate to what the AC actually requires). Manual verification against the real
   Keycloak container (login → call each endpoint) is called out as a Risk below, not silently assumed.
3. **The `Administration` section is admin-only end-to-end on the frontend (nav link + route guard), while
   `GET /risk-rules` itself stays `Operator`-level on the backend, per the API table.** `PHASE_5.md`'s own API table
   lists `GET /risk-rules`'s Access Level as `Operator` (any authenticated user), but its Testing Scope separately
   asks to verify "admin-only visibility of the Administration section." These are reconcilable, not contradictory:
   the backend endpoint is intentionally the more permissive of the two (any operator *could* call it directly, e.g.
   via a future admin tool or `curl` with a valid token), while the frontend's own UX choice is to only surface the
   *Administration section itself* (nav link, route, table, and its write controls) to users holding the `ADMIN`
   role — since its sole purpose in this phase is risk-rule *management*, which only `ADMIN` can act on anyway. The
   `adminGuard` is a UX gate, not a security boundary; the real boundary is the backend's `hasRole("ADMIN")` check on
   the three write verbs, which is where AC1 actually requires enforcement.
4. **`/me`'s "basic info" is served by frontend actually calling `GET /api/v1/me`, not read client-side from the ID
   token.** The frontend already holds the operator's identity claims locally (from the OIDC login), so it could
   render the header without a round trip — but `PHASE_5.md`'s API table defines `GET /me` as a real, testable
   endpoint with its own access level and response shape, and its Testing Scope explicitly asks for "`/me`
   projection" coverage. Wiring the header to genuinely call it (rather than leaving it as dead, spec-only surface)
   is both the literal reading of the phase doc and avoids unused backend code. The `adminGuard`, by contrast, needs
   a synchronous decision at route-activation time, so it reads the role claim already present in the access token
   client-side (`OAuthService.getIdentityClaims()`) rather than waiting on a network round trip — a deliberate split
   between "authoritative display data" (`/me`) and "synchronous routing decision" (local token claim).
5. **`PUT /risk-rules/{ruleId}` is a full-resource replace (`ruleName`, `appliesTo`, `thresholdLogic`, `weight`),
   not restricted to only threshold-logic/weight.** `PHASE_5.md`'s functional description narrows the *typical* edit
   to "threshold logic or score weight," but `UpdateRiskRuleDto` carrying all four editable fields is a superset of
   that (still fully capable of updating just those two, plus name/scope in one call) and matches this codebase's
   only other write-shaped precedent for REST semantics (a `PUT` replaces the addressed resource). No contradiction,
   just a more complete shape than the phase doc's prose singles out.
6. **Keycloak realm/client/user configuration is delivered as a Docker-Compose-imported realm export
   (`local-environment/keycloak/realm-export.json`), not a hand-run `kcadm.sh` script or admin-console click-through.**
   This is the only way to satisfy AC2 ("Keycloak is added to Docker Compose with `operator` and `admin` users")
   reproducibly on a fresh clone, mirroring this project's existing "declarative, checked-in local-environment
   config" pattern (WireMock's `mappings/`/`__files/`, Postgres's `init/`). The realm's `roles` client-scope mapper
   is explicitly configured to add `realm_access.roles` to **both** the ID token and the access token (Keycloak's
   out-of-the-box default only guarantees the access token; the frontend's `adminGuard` reads the *ID* token's
   identity claims per Clarification #4, so this must be explicit, not assumed).
7. **Clarification #3 (admin-only Administration section) is recorded as a new durable decision, `D21`.** D2 already
   covers the *core* mechanism ("role-based access: read for all; admin/editor for risk-rule writes" at the
   *backend*), but gating the frontend's Administration nav link/route to `ADMIN` only — while the backend's own
   `GET /risk-rules` stays `Operator`-level, a strictly more permissive rule — is a beyond-D2 UX interpretation this
   plan introduces, the same class of deliberate, beyond-the-phase-doc choice this project has consistently promoted
   to a new `DECISIONS.md` entry at implement time (D20's "popup vs. tab" UX call is the closest precedent). Added
   to the Documentation reconciliation task in the File Inventory below as `D21 — Administration section visibility
   is frontend-admin-gated, independent of the backend's own (more permissive) read access level`.
8. **The actuator/swagger-ui/api-docs `permitAll()` carve-out reads `PHASE_5.md` AC1's "all endpoints" as scoped to
   the phase doc's own API table** (the business `/api/v1/**` endpoints it actually enumerates — `/risk-rules`,
   `/me`, plus every pre-existing `/api/v1/**` resource), not Spring Boot's own dev-tooling/observability surface
   (`/actuator/health`, `/actuator/info`, `/swagger-ui/**`, `/api-docs/**`). None of those carry customer, risk, or
   assessment data; `/actuator/health`'s own `show-details: when-authorized` setting (already configured, unchanged
   by this plan) already hides internal detail from an unauthenticated caller, leaving only a bare `UP`/`DOWN`
   status exposed — no behavior change from today's `permitAll` baseline for this narrow, non-business surface.

## Backend Design

### `SecurityConfig` (rewritten, `config/SecurityConfig.java`)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(CsrfConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/**", "/swagger-ui/**", "/api-docs/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/risk-rules").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/api/v1/risk-rules/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.DELETE, "/api/v1/risk-rules/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }
}
```

`KeycloakRealmRoleConverter` (`config/KeycloakRealmRoleConverter.java`, `implements
Converter<Jwt, Collection<GrantedAuthority>>`) reads the `realm_access.roles` claim (a `Map`/`List` per Keycloak's
token shape) and maps each entry to `new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())`; an absent claim maps
to `Set.of()` rather than throwing (a token with no realm roles is simply never authorized for any role-gated route,
falling through to `anyRequest().authenticated()` for read-only access). Keycloak's own default roles
(`default-roles-<realm>`, `offline_access`, `uma_authorization`) become harmless extra authorities alongside
`ROLE_OPERATOR`/`ROLE_ADMIN` — never checked, never a problem.

`application.yml` additions:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWK_SET_URI:http://localhost:8081/realms/customer-activity-analytics/protocol/openid-connect/certs}
```

No `application-local.yml` override needed (Clarification #1) — the same host-published URL resolves for the
locally-run backend either way.

### `user/` package — `GET /me`

```
user/
  UserProfileController.java   @RestController — GET /api/v1/me
  dto/UserProfileDto.java      record: username, firstName, lastName, email, List<String> roles
```

A new top-level domain package (mirroring `customer`/`transaction`/`risk`/`analytics`'s existing noun-package
convention) rather than folding into `config`, since "the current operator's profile" is a small domain concept in
its own right, not security infrastructure. No service class — claim extraction is a one-line `Jwt`/`Authentication`
read with no persistence or business logic, so a bare controller method is the right altitude (`CLAUDE.md` Coding
Standard #3):

```java
@GetMapping("/api/v1/me")
public UserProfileDto me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
  List<String> roles = authentication.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority)
      .map(a -> a.replaceFirst("^ROLE_", ""))
      .toList();
  return new UserProfileDto(
      jwt.getClaimAsString("preferred_username"),
      jwt.getClaimAsString("given_name"),
      jwt.getClaimAsString("family_name"),
      jwt.getClaimAsString("email"),
      roles);
}
```

### `risk_rules` CRUD (`risk/api/`, `risk/dto/`)

New repository method (`risk/persistence/RiskRuleRepository.java`, additive):

```java
Page<RiskRule> findByAppliesTo(RuleScope appliesTo, Pageable pageable);
```

(The existing `findByAppliesToIn(Collection<RuleScope>)` stays as-is — it serves the unpaged RAG-retrieval use case
from Phase 4 and is a different shape from this paginated admin-listing need.)

New DTOs (`risk/dto/`):

```java
public record RiskRuleDto(UUID ruleId, String ruleName, RuleScope appliesTo, String thresholdLogic, BigDecimal weight) {}

public record CreateRiskRuleDto(
    @NotBlank String ruleName,
    @NotNull RuleScope appliesTo,
    @NotBlank String thresholdLogic,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weight) {}

public record UpdateRiskRuleDto(
    @NotBlank String ruleName,
    @NotNull RuleScope appliesTo,
    @NotBlank String thresholdLogic,
    @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal weight) {}
```

`RiskRuleService` (`risk/api/RiskRuleService.java`, alongside the existing `AiRiskAssessmentHistoryService` in the
same package — that class already establishes the "thin service beside its controller in `risk/api/`" precedent):

```java
@Service
public class RiskRuleService {
  // constructor-injected RiskRuleRepository

  public Page<RiskRuleDto> findAll(RuleScope appliesTo, Pageable pageable) {
    Page<RiskRule> page = appliesTo != null
        ? riskRuleRepository.findByAppliesTo(appliesTo, pageable)
        : riskRuleRepository.findAll(pageable);
    return page.map(this::toDto);
  }

  @Transactional
  public RiskRuleDto create(CreateRiskRuleDto dto) {
    RiskRule saved = riskRuleRepository.save(new RiskRule(
        UUID.randomUUID(), dto.ruleName(), dto.appliesTo(), dto.thresholdLogic(), dto.weight()));
    return toDto(saved);
  }

  @Transactional
  public RiskRuleDto update(UUID ruleId, UpdateRiskRuleDto dto) {
    requireExists(ruleId);
    RiskRule saved = riskRuleRepository.save(new RiskRule(
        ruleId, dto.ruleName(), dto.appliesTo(), dto.thresholdLogic(), dto.weight()));
    return toDto(saved);
  }

  @Transactional
  public void delete(UUID ruleId) {
    requireExists(ruleId);
    riskRuleRepository.deleteById(ruleId);
  }

  private void requireExists(UUID ruleId) {
    if (!riskRuleRepository.existsById(ruleId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk rule not found: " + ruleId);
    }
  }

  private RiskRuleDto toDto(RiskRule rule) { /* field-for-field, no separate mapper class */ }
}
```

`RiskRule` stays immutable (no setters, matching every other entity in this codebase) — updates go through
`save(new RiskRule(existingId, ...))`, which Spring Data JPA correctly resolves to an `entityManager.merge()`
`UPDATE` because the entity's `@Id` is non-null and already present in the table (same reasoning JPA already applies
transparently; no `Persistable` override needed since `RiskRule` has no auditing fields that would make "new vs.
existing" ambiguous).

`RiskRuleController` (`risk/api/RiskRuleController.java`), same thin-controller shape as every other controller in
this codebase:

```java
@RestController
public class RiskRuleController {
  @GetMapping("/api/v1/risk-rules")
  public Page<RiskRuleDto> findAll(
      @RequestParam(required = false) RuleScope appliesTo,
      @PageableDefault(size = 20, sort = "ruleName") Pageable pageable) { ... }

  @PostMapping("/api/v1/risk-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public RiskRuleDto create(@Valid @RequestBody CreateRiskRuleDto dto) { ... }

  @PutMapping("/api/v1/risk-rules/{ruleId}")
  public RiskRuleDto update(@PathVariable UUID ruleId, @Valid @RequestBody UpdateRiskRuleDto dto) { ... }

  @DeleteMapping("/api/v1/risk-rules/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID ruleId) { ... }
}
```

Bean-validation failures on `create`/`update` surface as the standard Spring MVC `400`
`MethodArgumentNotValidException` → `ProblemDetail` response (already enabled globally via
`spring.mvc.problemdetails.enabled: true`) — no custom exception handler needed, consistent with `CLAUDE.md` Coding
Standard #3.

## Local Environment — Keycloak

`local-environment/docker-compose.yml`, new service:

```yaml
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    container_name: caa-keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HEALTH_ENABLED: "true"
    ports:
      - "${KEYCLOAK_PORT:-8081}:8080"
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/8080"]
      interval: 5s
      timeout: 5s
      retries: 24
```

(The image tag is a reasonable current pin, matching the project's existing "pin a specific version" convention for
`postgres`/`wiremock`; confirm at implementation time that this exact tag resolves for both `amd64`/`arm64`, same
caveat this file's `wiremock` service comment already documents for that image.)

`local-environment/keycloak/realm-export.json` (replaces the placeholder `.gitkeep`) declaratively defines:

- Realm `customer-activity-analytics`, enabled.
- Realm roles `OPERATOR`, `ADMIN`.
- Client `caa-frontend`: public (no client secret — SPA + PKCE), `standardFlowEnabled: true`,
  `directAccessGrantsEnabled: false`, `redirectUris: ["http://localhost:4200/*"]`,
  `webOrigins: ["http://localhost:4200"]`, `attributes: { "pkce.code.challenge.method": "S256" }`.
- The realm's default `roles` client scope's realm-role protocol mapper explicitly set with
  `"id.token.claim": "true"` and `"access.token.claim": "true"` (Clarification #6 — both tokens carry
  `realm_access.roles`, not just the access token).
- User `operator` / password `password` (non-temporary), realm role `OPERATOR`, `firstName`/`lastName`/`email` set
  for a non-empty `/me` display.
- User `admin` / password `admin` (non-temporary), realm roles `ADMIN` **and** `OPERATOR` (so the admin user reads
  fine too, since "editor" per `PHASE_5.md`'s Assumptions is additive to, not a replacement for, operator access).

`local-environment/keycloak/README.md` (new, mirroring `wiremock/README.md`'s existing style): what the realm export
contains, the two demo logins, and how to re-export/update it if the realm is changed by hand during development.

## Frontend Design

### Bootstrap & auth plumbing

- `core/auth/auth.config.ts` (new) — the `AuthConfig` object: `issuer:
  'http://localhost:8081/realms/customer-activity-analytics'`, `clientId: 'caa-frontend'`,
  `redirectUri: window.location.origin + '/'`, `postLogoutRedirectUri: window.location.origin + '/'`,
  `responseType: 'code'`, `scope: 'openid profile email'`, `showDebugInformation` gated off, PKCE left enabled
  (library default for code flow).
- `app.config.ts` additions:
  - `provideHttpClient(withFetch(), withInterceptorsFromDi())` (adds DI-interceptor support alongside the existing
    `withFetch()`).
  - `provideOAuthClient({ resourceServer: { allowedUrls: ['/api'], sendAccessToken: true } })`.
  - `{ provide: HTTP_INTERCEPTORS, useClass: DefaultOAuthInterceptor, multi: true }` — the library's own interceptor
    (attaches the bearer token to `allowedUrls`), no custom interceptor code needed.
  - `provideAppInitializer(() => { const oauthService = inject(OAuthService); oauthService.configure(authConfig);
    return oauthService.loadDiscoveryDocumentAndLogin().then((loggedIn) => {
      if (loggedIn) { oauthService.setupAutomaticSilentRefresh(); }
      return loggedIn;
    }); })` — the whole app is gated behind a valid session before any route renders (satisfies AC1's "read access
    requires an authenticated operator" for every route, not just Administration); an unauthenticated visitor is
    redirected to Keycloak's login page before `AppComponent` ever mounts. `setupAutomaticSilentRefresh()` is
    necessary, not optional: Keycloak's default access-token lifetime (~5 min) is shorter than this project's own
    stated demo-reliability target (D4's "makes the 10–15 min demo reliable"), and without it every `/api/**` call
    would start silently `401`ing mid-token-lifetime with no user-visible cause (route guards only run on
    navigation, not on token expiry, so nothing would re-trigger a login). Code-flow-with-PKCE public clients
    receive a refresh token from Keycloak by default, so silent refresh needs no extra scope or realm config beyond
    what Clarification #6 already specifies — it POSTs the refresh token to the token endpoint in the background, no
    iframe/redirect involved.
- `core/services/auth.service.ts` (new) — thin wrapper around the injected `OAuthService`:
  `isLoggedIn(): boolean`, `getRoles(): string[]` (from `getIdentityClaims()['realm_access']?.roles ?? []`),
  `isAdmin(): boolean`, `logout(): void` (`oauthService.logOut()` — OIDC front-channel logout via Keycloak's
  `end_session_endpoint`, satisfying AC3's "secure OAuth2/OIDC logout").
- `core/guards/auth.guard.ts` (new, `CanActivateFn`) — `authService.isLoggedIn() ? true :
  router.createUrlTree(['/'])`. Realistically unreachable given the app-initializer gate above, but gives an
  explicit, independently unit-testable guard, which is what `PHASE_5.md`'s Testing Scope literally asks for
  ("authenticated route guards").
- `core/guards/admin.guard.ts` (new, `CanActivateFn`) — `authService.isAdmin() ? true : router.createUrlTree([''])`
  (Clarification #3).

### `/me` + header

- `core/models/user-profile.model.ts` — `interface UserProfile { username: string; firstName: string; lastName:
  string; email: string; roles: string[]; }`.
- `core/services/user.service.ts` — `getCurrentUser(): Observable<UserProfile>`, `GET /api/v1/me`, same thin-wrapper
  shape as `CustomerService`.
- `app.component.ts`/`.html` — adds a nav row (`routerLink="/"` "Customer Analytics", `routerLink="/administration"`
  "Administration" — the latter only rendered `@if (authService.isAdmin())`, Clarification #3) and a right-aligned
  user block: full name (from `UserService.getCurrentUser()`, called once in the constructor) + a "Logout" button
  calling `authService.logout()`.

### Risk-rule administration

- `core/models/risk-rule.model.ts` — `ActivityScope = 'CARD' | 'PAYMENT' | 'CRYPTO' | 'ALL'`; `RiskRule { ruleId,
  ruleName, appliesTo: ActivityScope, thresholdLogic, weight: number }`; `RiskRuleWrite { ruleName, appliesTo,
  thresholdLogic, weight }` (shared shape for create/update payloads, Clarification #5).
- `core/services/risk-rule.service.ts` — `list(appliesTo?, page, size, sort): Observable<Page<RiskRule>>`,
  `create(dto: RiskRuleWrite): Observable<RiskRule>`, `update(ruleId, dto: RiskRuleWrite): Observable<RiskRule>`,
  `delete(ruleId): Observable<void>` — same `HttpClient` + `HttpParams` shape as every other `core/services/*`.
- `features/administration/administration-page/` — top-level page component routed at `/administration`; hosts the
  table, an "Add Rule" button (rendered only when `authService.isAdmin()`, defense-in-depth alongside the backend's
  own `hasRole("ADMIN")` — the guard already keeps non-admins off this route entirely, but the button check is
  free and keeps the component self-consistent if ever reused), and owns the create/edit `MatDialog` + delete
  confirmation (`window.confirm(...)` — a single, one-off confirmation does not warrant a new reusable
  `ConfirmDialogComponent` the way the AI-assessment history popup did, `CLAUDE.md` Coding Standard #3).
- `features/administration/risk-rules-table/` — `MatTableModule` + `MatPaginatorModule` + a single `mat-select`
  `appliesTo` filter above the table (the resource has exactly one filterable field, unlike the transaction table's
  many — a plain toolbar dropdown is the right complexity here, not the icon-popover-per-column pattern). Columns:
  rule name, applies-to (chip), threshold logic (truncated + `matTooltip` for full text, mirroring the AI-assessment
  history table's precedent for long text columns), weight, and an actions column (edit/delete icon buttons,
  `@if (authService.isAdmin())`).
- `features/administration/risk-rule-form-dialog/` — `MatDialog` reactive form (`ruleName`, `appliesTo` select,
  `thresholdLogic` textarea, `weight` number input, each with matching `Validators` to the backend DTOs), reused for
  both create (no `MAT_DIALOG_DATA`) and edit (existing `RiskRule` passed as data, form pre-filled) — reuses
  `MatDialog` per D20's own stated precedent ("future closable-popup UI in this app should reuse `MatDialog`").

### Routes (`app.routes.ts`)

```ts
export const routes: Routes = [
  { path: '', component: EmptyStateComponent, canActivate: [authGuard] },
  {
    path: 'customers/:customerId',
    component: TransactionsPageComponent,
    canActivate: [authGuard],
    children: [ /* unchanged */ ],
  },
  {
    path: 'administration',
    component: AdministrationPageComponent,
    canActivate: [authGuard, adminGuard],
  },
];
```

## File Inventory

**Backend — new:** `config/KeycloakRealmRoleConverter.java`; `user/UserProfileController.java`,
`user/dto/UserProfileDto.java`; `risk/api/RiskRuleController.java`, `risk/api/RiskRuleService.java`,
`risk/dto/{RiskRuleDto,CreateRiskRuleDto,UpdateRiskRuleDto}.java`.
Test: `user/UserProfileControllerTest.java` (`@WebMvcTest` — claims/roles projection, `401` unauthenticated),
`risk/api/RiskRuleControllerTest.java` (`@WebMvcTest` — the authorization matrix: anonymous `401` on every verb,
operator `200` on `GET`/`403` on `POST`/`PUT`/`DELETE`, admin `200`/`201`/`200`/`204` on all four; plus validation
`400`s and update/delete `404`s), `risk/api/RiskRuleServiceTest.java` (Mockito — CRUD logic, `404` on
update/delete-of-missing, `appliesTo`-filtered vs. unfiltered listing).

**Backend — modified:** `config/SecurityConfig.java` (full rewrite — real JWT resource-server chain, replaces
`permitAll`); `application.yml` (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`);
`risk/persistence/RiskRuleRepository.java` (new `findByAppliesTo(RuleScope, Pageable)` method); **every**
`@WebMvcTest`-based existing test — `transaction/TransactionControllerTest.java`,
`customer/CustomerControllerTest.java`, `analytics/AnalyticsControllerTest.java`,
`analytics/AnalyticsConfigControllerTest.java`, `risk/api/AiRiskAssessmentControllerTest.java` — gets two changes:
(a) every existing `MockMvc` call updated to carry `.with(jwt().authorities(new
SimpleGrantedAuthority("ROLE_OPERATOR")))` (or `ADMIN` where a test specifically needs it), since the permit-all
chain they currently `@Import` no longer exists; (b) **one new test method added to each class** asserting an
unauthenticated call to that controller's primary endpoint (no `.with(jwt())`) returns `401` — proving AC1's actual
enforcement claim ("read access requires an authenticated operator") for these five pre-existing resource groups,
not just that they still work once authenticated. Only `RiskRuleControllerTest` and `UserProfileControllerTest`
(both new) need the *full* role matrix; these five need just the one added anonymous-`401` case each.

**Local environment — new:** `local-environment/keycloak/realm-export.json`,
`local-environment/keycloak/README.md` (replaces the `.gitkeep` placeholder).

**Local environment — modified:** `local-environment/docker-compose.yml` (`keycloak` service).

**Frontend — new:** `core/auth/auth.config.ts`; `core/services/{auth,user,risk-rule}.service.ts` (+ `.spec.ts`
each); `core/guards/{auth,admin}.guard.ts` (+ `.spec.ts` each); `core/models/{user-profile,risk-rule}.model.ts`;
`features/administration/{administration-page,risk-rules-table,risk-rule-form-dialog}/*`
(each with `.ts/.html/.scss/.spec.ts`).

**Frontend — modified:** `app.config.ts` (OAuth providers, interceptor); `app.routes.ts` (guards, new
`administration` route); `app.component.ts`/`.html`/`.spec.ts` (nav + user/logout header — the existing spec's
`HttpTestingController` setup needs a `/api/v1/me` flush added, plus a fake/mock `OAuthService`/`AuthService`
provider so the header renders deterministically in the test).

**Documentation reconciliation (assigned as an `/implement`-time task, per `PHASE_2_PLAN.md`/`PHASE_4_PLAN.md`
precedent):** `docs/DECISIONS.md` gains `D21 — Administration section visibility is frontend-admin-gated,
independent of the backend's own (more permissive) read access level` (Clarification #7). `README.md`'s
Architecture section gains a Phase 5 paragraph (auth flow, automatic silent refresh, Administration section,
Keycloak in the local environment) and its `local-environment` bullet drops the "`keycloak/` folder still reserved
for Phase 5" note now that it is filled.

## Test Plan → Acceptance-Criteria Mapping

| `PHASE_5.md` AC | Backend coverage | Frontend coverage |
|---|---|---|
| AC1 — every endpoint requires authentication; admin/editor role required for risk-rule writes | `RiskRuleControllerTest` (full matrix: anonymous/operator/admin × GET/POST/PUT/DELETE); the five updated existing `@WebMvcTest`s each gain an anonymous-`401` case (proving the new chain actually enforces auth on pre-existing read endpoints, not just that it still permits authenticated callers) alongside their existing calls now carrying a `jwt()` post-processor | `auth.guard.spec.ts`, `admin.guard.spec.ts` (redirect vs. pass-through for each role combination) |
| AC2 — Keycloak in Docker Compose with `operator`/`admin` demo users, `admin` holding the editor role | — (infra; verified manually — `docker compose up keycloak`, log in as each demo user via the Keycloak login page reached through the frontend, confirm `admin`'s token carries `ADMIN` in `realm_access.roles`) | — |
| AC3 — risk rules in a dedicated Administration table (separate from Customer Analytics); top-right shows user info + secure logout | `UserProfileControllerTest` (`/me` projection correctness) | `risk-rules-table.component.spec.ts`, `administration-page.component.spec.ts` (CRUD wiring, admin-only actions), `app.component.spec.ts` (nav visibility, user info render, logout button invokes `AuthService.logout()`) |

Also covers Testing Scope items not tied to a single AC: token/role mapping
(`KeycloakRealmRoleConverter` — covered inline by `RiskRuleControllerTest`'s matrix, since the converter is exercised
end-to-end by every `jwt()`-authenticated request in that test rather than needing an isolated unit test for a
five-line `Converter`), `/me` projection (`UserProfileControllerTest`), authenticated route guards and admin-only
Administration visibility (`auth.guard.spec.ts`/`admin.guard.spec.ts`/`app.component.spec.ts` above), login/logout
flow and automatic silent token refresh (manual verification — `OAuthService`'s redirect-based code+PKCE flow,
Keycloak's front-channel logout, and the background silent-refresh POST all involve real navigation/network
round trips against a real IdP, which is inherently outside Karma/JSDOM's reach; called out as a Risk below, not
silently assumed covered).

`ArchitectureTest`'s existing rules apply unchanged to the new `user`/`risk.api` additions (controller/repository/
persistence-API isolation, cycle-freedom) — no new ArchUnit rule needed.

## Risks / Open Questions (carried from `PHASE_5.md`, resolved or narrowed where possible)

- **Keycloak realm/client config reproducibility** (carried verbatim) — mitigated, not eliminated, by the
  declarative `realm-export.json` + `--import-realm` approach (Clarification #6); still requires one manual
  end-to-end pass (login as `operator`, login as `admin`, confirm role claims, confirm the session survives past the
  access token's ~5 min lifetime via silent refresh, and confirm logout) before this phase can be marked complete,
  since no automated test in this plan drives a real browser against a real Keycloak instance.
- **Consistent OIDC logout (front-channel) leaving no stale session** (carried verbatim) — `OAuthService.logOut()`
  is the library's standard RP-initiated logout call against Keycloak's `end_session_endpoint`; verifying "no stale
  session" (e.g. a subsequent silent re-auth attempt doesn't silently succeed) is a manual/exploratory check, not
  something this plan's automated test suite can assert.
- **New:** replacing `SecurityConfig` is the first change in this codebase that makes existing, previously-`200`
  tests fail by default (`401`) unless updated — the file inventory above enumerates every affected test file
  explicitly so this doesn't surface as a surprise build break mid-implementation.
- **New:** `jwk-set-uri` (Clarification #1) means a context-load test can still succeed even if the JWKS URL is
  subtly wrong (a typo'd realm name, wrong port) — this would only surface the first time a real authenticated
  request is made against a running instance, i.e. during the manual Keycloak verification pass above, not in
  `./gradlew check`. Worth a deliberate manual check, not just trusting a green build.
