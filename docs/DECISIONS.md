# MetaShop — Decisions Log

This file records deviations from `MetaShop-Master-Spec.md` and choices made where the spec
was silent or ambiguous. Newest decisions are appended at the end of each section.

---

## D-001 — Runtime versions (kept as-is)
- **Java: 21** — as per spec.
- **Spring Boot: 4.1.0** — the spec says `3.3.x`, but the existing `auth-service` and
  `product-service` were already generated on **4.1.0** and build cleanly. We keep 4.1.0 and
  will **not** change the version. Note that Spring Boot 4.x uses modular starters
  (`spring-boot-starter-webmvc`, `spring-boot-starter-<x>-test`).

## D-002 — Package naming (kept as-is)
- `auth-service` → `com.shopMeta.auth_service`
- `product-service` → `com.ShopMeta.product_service`

Kept to avoid a large, risky rename. The spec's `com.metashop.<service>` is **not** adopted.

## D-003 — API base path
Services expose `/api/v1/...` (already used by the existing code). The API Gateway will route
`/api/v1/**`. The spec §5.1 shows `/api/...`; `/api/v1` is a versioned superset and is kept.

## D-004 — Flyway adopted
Per spec §3.2 rule 8, `product-service` now uses **Flyway** (`classpath:db/migration`) and
`spring.jpa.hibernate.ddl-auto=validate`.
Because the dev schema `metashop_product_db` was previously created by `ddl-auto=update`
(and contained a stray `category` column), it is **dropped and recreated** by Flyway.
This affects local dev data only.

## D-005 — Stock ownership
`stockQuantity` was removed from the `Product` entity/table. Per spec §9, stock is owned by
`inventory-service` (`metashop_inventory`), not `product-service`.

## D-006 — Local dev database
MySQL at `localhost:3306`, schema `metashop_product_db`, user `root`. Credentials are local
dev defaults; production values will come from environment variables (later phase).

## D-007 — Optimistic locking on Product
`Product.version` is a JPA `@Version` column (`BIGINT NOT NULL DEFAULT 0`) as required by
spec §8.2.

---

## Product Service

## D-008 — Service layer style
Services are concrete `@Service` classes (e.g. `ProductService`, `BrandService`) rather than
the `service` interface + `service/impl` split suggested by spec §4.1. This matches the
existing code in the repo and avoids boilerplate. Can be refactored to interfaces later if
required.

## D-009 — Brand delete is a soft delete
`DELETE /api/v1/admin/brands/{id}` sets `active = false` instead of physically deleting the
row, so historical products/orders keep resolving their brand. The public list returns only
active brands.

## D-010 — Admin endpoints are not yet authorized
`/api/v1/admin/**` endpoints are currently open. Method-level `@PreAuthorize("hasRole('ADMIN')")`
(and Spring Security + JWT validation) will be added in the security-hardening phase
(spec §17 Phase 9), together with the Gateway route rules.

## D-011 — List response shape
Reference lists (brands, and later categories) are returned in full (small datasets) wrapped
in the existing `ApiResponse` envelope. Only the product list is paginated
(`ProductPageResponse`). A single, spec-aligned envelope for every list is deferred to the
API-standardisation pass.

## D-012 — Category tree, delete and cycle rules
- `GET /api/v1/categories` returns a **nested tree** of *active* categories (orphans whose
  parent is inactive are promoted to roots).
- `DELETE` is a soft delete and is **refused (422)** while the category still has
  sub-categories.
- `PUT` rejects hierarchy cycles (a category cannot become its own parent or the child of one
  of its descendants) with **422**.

## D-013 — Catalog query contract
`GET /api/v1/products` accepts `q`, `category` (slug), `brand` (slug), `minPrice`, `maxPrice`,
`sort`, `page`, `size`.
- `size` is **clamped to [1, 100]** (spec: max 100) rather than rejected.
- `sort` is **whitelisted** to `price | name | createdAt | updatedAt | averageRating |
  ratingCount` with `asc|desc`; anything else returns **400** (`InvalidRequestException`).
- `category`/`brand` are matched by **slug** via a LEFT JOIN; an unknown slug simply yields an
  empty result set.

## D-014 — Inactive products are hidden publicly
`GET /api/v1/products/{id}` and `/slug/{slug}` return **404** for inactive (soft-deleted)
products. Admin/internal access to inactive products comes via the admin endpoints and the
`/internal/products/batch` endpoint (later sections).

## D-015 — Search implementation & list payload
- Keyword search uses SQL `LIKE` on `name`, `short_description`, `description`, `sku` and
  `brand.name`. The `FULLTEXT` index from V1 is reserved for a future native full-text query.
- Listing returns a lightweight `ProductSummaryResponse` (primary image only); the detail
  endpoint returns the full `ProductResponse` with all images and attributes.
- `Product.images` uses `@BatchSize(50)` to avoid N+1 when building list payloads.

## D-016 — Admin product API surface
Write operations live under `/api/v1/admin/products` (`POST`, `PUT`, `DELETE`,
`PATCH /{id}/status`) and the public `/api/v1/products` controller is now **read-only**.
`DELETE` is a soft delete (`active=false`). Admin listing/detail include inactive products;
the `active` query param on the admin list may be omitted to return any status.

## D-017 — Image storage
Uploaded images are written to the local filesystem
(`metashop.storage.location=uploads`) and served by `WebConfig` under
`metashop.storage.public-base-url=/uploads`. Only `image/*` content types are accepted and
Spring's multipart limits cap uploads at 5 MB. A later phase can swap
`FileStorageService` for S3-compatible storage without changing callers.

## D-018 — Unique product slug
The slug is generated from the name and, on collision, a numeric suffix is appended
(`iphone-15`, `iphone-15-2`, …). Products keep their own slug on update.

## D-019 — Product events deferred
`ProductCreatedEvent` / `ProductPriceChangedEvent` publishing (spec §8.3) requires Kafka and
is **deferred to the Kafka phase (Phase 8)**. The admin create/update flow is otherwise
complete.

## D-020 — Generic error fallback
`GlobalExceptionHandler` now has a catch-all `Exception` handler returning **500** with the
`ApiResponse` envelope, satisfying the spec §5.3 "generic fallback" requirement. Full
spec-aligned `ErrorResponse` (with `traceId`, `fieldErrors`, `path`) is still a later pass.

## D-021 — Product attributes
`product_attributes` is modelled, migrated and returned in the product detail response, but
no dedicated admin API for attributes is defined in spec §8.3, so none was added. Attributes
will be populated by seed data for now.

## D-022 — Internal API
- `POST /api/v1/internal/products/batch` (`{ "ids": [...] }`, max 100) returns
  `id, sku, name, price, imageUrl, active` for cart-service / order-service; null ids are
  ignored and duplicates collapsed.
- `GET /api/v1/internal/products/{id}` returns the same snapshot for any status.
- Internal endpoints return **plain JSON** (no `ApiResponse` wrapper) for simple Feign
  consumption.
- They are **not yet protected** by `X-Internal-Token`; the Gateway must not route
  `/api/v1/internal/**` publicly. Both are handled in the security-hardening phase.

## D-023 — OpenAPI / Swagger
- `springdoc-openapi-starter-webmvc-ui` **3.1.1** (the Spring Boot 4 line; springdoc 2.x
  targets Spring Boot 3).
- Swagger UI at `/swagger-ui/index.html`, spec at `/v3/api-docs`. A bearer-JWT security
  scheme is declared so admin endpoints can be authorized once security is enabled.
- Actuator (`health`, `info`, `metrics`, `prometheus`) is **not** added yet; it will come with
  the observability/Docker phase (needed for the container `HEALTHCHECK`).

## D-024 — Seed data
- Implemented as a `CommandLineRunner` (`config/DataSeeder`) rather than a Flyway migration, so
  demo data never lands in production and can be regenerated without touching the schema.
- Guarded by `@Profile("!prod")` and idempotent (skips when `products` is non-empty). Reseed by
  clearing the catalog tables and restarting.
- Seeds **6 categories** (hierarchical: Electronics → Mobiles/Laptops, plus Fashion,
  Home & Kitchen, Sports), **10 brands** and **50 products** — each with one primary image
  (external placeholder URL) and two attributes, plus varied prices / ratings so filtering,
  sorting and pagination are demonstrable.
- Stock is intentionally **not** seeded here; inventory-service owns it (D-005).
- Because `@SpringBootTest` runs command-line runners, the seeder executes during the context
  test too — it is idempotent, so it either seeds an empty DB once or skips.

---

## Auth Service

## D-025 — Auth DB design (spec 7.2)
- Flyway adopted exactly as in product-service (`spring-boot-starter-flyway` + `flyway-mysql`,
  `ddl-auto=validate`). The dev schema `metashop_auth_db` was **dropped and recreated** so
  Flyway owns it (previous tables came from `ddl-auto=update`).
- Schema name kept as `metashop_auth_db` (spec §3.1 says `metashop_auth`) for consistency with
  `metashop_product_db` (D-006).
- Column names follow the spec: `password_hash`, `phone`, `email_verified`.
- Two columns are **added** beyond the spec table definition to satisfy the spec's own
  business rule "lock account for 15 min after 5 failed logins": `failed_login_attempts` and
  `locked_until`.
- The unique index on `email` relies on the `utf8mb4_unicode_ci` collation to be
  case-insensitive; the service additionally lower-cases every email before storing/looking up.
- `created_at`/`updated_at` are `NOT NULL` and maintained by `@PrePersist`/`@PreUpdate`.

## D-026 — Port changed to 8081
`auth-service` moved from **8080 → 8081** per spec §3.1, because **8080 is reserved for the
API Gateway**. This resolves the earlier port conflict noted in the decisions log.

## D-027 — JWT (spec 7.4)
- Secret comes from `metashop.jwt.secret` (env `JWT_SECRET`) with a dev-only default that is
  already ≥ 256 bit; the algorithm is auto-selected by jjwt from the key length (HS384 for the
  dev key).
- Access-token TTL **15 min**, refresh-token TTL **7 days** (`metashop.jwt.*`, `Duration`).
- Claims: `sub` (userId), `email`, `roles`, `jti`, `iss`, `iat`, `exp`.
- `JwtAuthenticationFilter` is now registered in the filter chain (it previously existed but
  was **never wired up**), the chain is `STATELESS`, and 401/403 responses are returned as the
  `ApiResponse` envelope.

## D-028 — Refresh tokens (spec 7.3/7.4)
- Opaque 64-byte `SecureRandom` value, Base64url-encoded; only its **SHA-256 hex hash** is
  stored (`refresh_tokens.token_hash`, unique).
- **Rotated on every use.** Presenting an already-revoked token is treated as compromise and
  revokes **all** of that user's refresh tokens.
- `rotate()`/`refresh()` are annotated `@Transactional(noRollbackFor = InvalidTokenException.class)`
  so the revocation performed on the failure path is actually committed (otherwise the
  transaction would roll it back).
- `POST /api/v1/auth/logout` **requires a JWT** (as the spec says) even though only the refresh
  token is revoked.

## D-029 — Account lockout (spec 7.4)
- After `metashop.security.max-failed-login-attempts` (default **5**) consecutive failures the
  account is locked for `metashop.security.lock-duration` (default **15m**) and the counter is
  reset.
- HTTP status used is **423 Locked**. This code is not in the spec §5.1 list, but it is the
  correct semantic; login failures themselves stay **401** with a generic
  "Invalid email or password" (never revealing whether the email exists).
- A disabled account returns **403**.

## D-030 — `UserRegisteredEvent` deferred
Registering a user does not yet publish `UserRegisteredEvent` (§14.3). Kafka is **Phase 8**;
the same deferral already applies to the product events (D-019).

## D-031 — Error envelope
Errors keep the existing `ApiResponse` envelope (`success=false, message, data`), matching
product-service (D-011/D-020). The spec §5.3 `ErrorResponse` (with `traceId`, `path`,
`fieldErrors[]`) is defined in `common-lib`, which does not exist yet (Phase 0); validation
errors are returned as a `field → message` map under `data` in the meantime.

## D-032 — Address book rules (spec 7.4)
- Maximum **10** addresses per user → `422` beyond that.
- Exactly one default: the first address becomes default automatically; setting/adding a new
  default clears the previous one; deleting the default promotes the oldest remaining address.
- Every operation is scoped by `user_id` taken from the JWT (never from the request); another
  user's address is reported as **404** rather than 403 so it does not leak existence.

## D-033 — Forgot / reset password (spec 7.3)
- The reset token is opaque (64 random bytes), stored **hashed**, single-use and expires after
  `metashop.security.password-reset-ttl` (default `30m`).
- Requesting a new token invalidates any previous unused one.
- `POST /auth/forgot-password` always returns `200` with a neutral message, so it never reveals
  whether an email is registered.
- A successful reset also clears `failed_login_attempts`/`locked_until` and revokes all refresh
  tokens.
- `PasswordResetRequestedEvent` (§14.3) is **not** published yet (Kafka is Phase 8). Until the
  notification-service exists, the reset link is written to the log **outside the `prod`
  profile only**.

## D-034 — Admin user management (spec 7.3)
- `GET /api/v1/admin/users` is paginated (spec §5.2 envelope: `content/page/size/totalElements/
  totalPages/last`), sorted by id descending, `size` clamped to 1–100, with optional filters
  `email` (partial, case-insensitive), `role` and `enabled`. An unknown role → `400`.
- `PATCH /{id}/status` enables/disables a user; **disabling revokes that user's refresh tokens**
  immediately. An admin **cannot disable their own account** → `422`.
- Authorisation uses method-level `@PreAuthorize("hasRole('ADMIN')")`.

## D-035 — Role authorities vs. the JWT `roles` claim
Spring authorities are `ROLE_ADMIN` / `ROLE_CUSTOMER` (so idiomatic `hasRole('ADMIN')` works),
while the JWT `roles` claim and the planned Gateway `X-User-Roles` header carry the **plain**
names `ADMIN` / `CUSTOMER` (spec §5.4 / §7.2). `CustomUserDetails#getRoleNames()` is the source
for the claim.

## D-036 — Seed accounts (spec 7.2 / §20)
- The `CUSTOMER` and `ADMIN` roles are created in **every** environment because registration
  depends on them.
- The demo accounts `admin@metashop.com` and `customer@metashop.com` are created only outside
  the `prod` profile (`metashop.seed.*`); their passwords default to `Admin@12345` /
  `Customer@12345` and should be overridden with `ADMIN_PASSWORD` / `CUSTOMER_PASSWORD`.
- Implemented as an idempotent `CommandLineRunner`, consistent with D-024.

## D-037 — Swagger / OpenAPI (auth-service)
`springdoc-openapi-starter-webmvc-ui` **3.1.1** with `config/OpenApiConfig` (title, description,
bearer-JWT security scheme). UI at `/swagger-ui/index.html`, spec at `/v3/api-docs`; the
swagger and actuator-health paths are `permitAll`. All **17** spec §7.3 endpoints plus
`GET /users/me/addresses/{id}` are documented.

## D-038 — Tests
73 tests: service unit tests with Mockito (`AuthService`, `UserService`, `AddressService`,
`AdminUserService`, `PasswordResetService`, `RefreshTokenService`) and `@WebMvcTest` controller
tests (`Auth`, `User`, `Address`, `AdminUser`, `InternalUser`) plus the context test. Controller
slices use `@AutoConfigureMockMvc(addFilters = false)` and populate `SecurityContextHolder`
directly so `@AuthenticationPrincipal` resolves without the JWT filter.

---

# Phase 1 — discovery-server + api-gateway (spec §6.1 / §6.2 / §17)

## D-039 — Scaffold normalisation for the remaining services
The Initializr scaffolds that were added for the remaining services did not match the two
finished services, so `discovery-server` and `api-gateway` were normalised before any code was
written:

- **Java 17 → 21** and **Spring Boot 4.1.1 → 4.1.0**, keeping D-001 ("one Boot version in the
  repo"). Spring Cloud **2025.1.3** was verified to resolve against Boot 4.1.0.
- The extra `metashop/` nesting level was flattened, so `discovery-server/` and `api-gateway/`
  sit directly at the repo root like `auth-service/` and `product-service/`.
- Packages became `com.shopMeta.discovery_server` and `com.shopMeta.api_gateway`
  (`com.shopMeta.<service>`), and the artifact/`name`/`spring.application.name` of each module
  is its own service name instead of the placeholder `metashop`.
- `api-getway-service/` was renamed to **`api-gateway/`** (typo).
- The Initializr "web + JPA + security" dependencies were replaced with the ones each service
  actually needs (see D-040 / D-041).

The other scaffolds (inventory, cart, order, payment) are still in their original state and will
be normalised the same way when their phase starts.

## D-040 — discovery-server
- Eureka server on **8761**, `@EnableEurekaServer`, `spring.application.name=discovery-server`.
- `register-with-eureka=false` / `fetch-registry=false`: a registry does not register with itself.
- Self-preservation is switched off and the eviction interval shortened to 5 s so a service that
  is stopped disappears from the dashboard quickly during development.
- **No Spring Security.** The spec does not ask for an authenticated Eureka, and the Phase 1
  acceptance criterion is "the Eureka UI shows the gateway" — adding the security starter would
  have put a generated password in front of it. Revisit if the registry is ever exposed.
- Actuator health is exposed for the future docker-compose healthcheck.

## D-041 — api-gateway: reactive, WebFlux only
- Spring Cloud Gateway 5.0.3 (`spring-cloud-starter-gateway-server-webflux`). The gateway is
  **reactive**: `spring-boot-starter-webmvc` must never be on this classpath or the application
  refuses to start, so the Initializr `webmvc`/`jpa`/`security` starters were removed.
- Routes are declared in Java (`config/GatewayRoutesConfig`) rather than in properties, because
  each route also carries its authorisation requirement as metadata (`metashop.auth` =
  `PUBLIC` | `JWT` | `ADMIN`). That keeps the path rules in exactly one place.
- Paths are forwarded **unchanged** as `/api/v1/**` (D-003): the gateway does not rewrite
  `/api/**` → `/api/v1/**`.
- Only services that are actually deployed get a route (auth-service, product-service). Routes
  for inventory/cart/order/payment are added as those services land, so the gateway never
  advertises a route that can only answer 503.
- **`/api/v1/internal/**` is deliberately not routed** (spec §5.4/§19). Internal endpoints stay
  reachable service-to-service on the service port and answer `404 ROUTE_NOT_FOUND` through the
  gateway.

## D-042 — Gateway JWT validation and identity propagation
- The gateway verifies the token with the **same HS256 secret and issuer** as auth-service
  (`metashop.gateway.jwt.secret` ← `JWT_SECRET`, default identical to the auth-service dev
  default). Only signature + expiry + issuer are checked; it does not call auth-service.
- **Every inbound `X-User-Id` / `X-User-Email` / `X-User-Roles` header is removed**, whatever the
  route. On `JWT`/`ADMIN` routes the three headers are then re-added from the token claims
  (`sub`, `email`, `roles`). A client therefore cannot impersonate another user by setting them.
- `ADMIN` routes additionally require `ADMIN` in the `roles` claim → `403` otherwise.
- A route without the metadata key **fails closed** (treated as `JWT`).
- Token failures return `401 UNAUTHORIZED`; the check happens before the request is forwarded, so
  a rejected request never reaches a downstream service.
- Downstream services will *also* validate the JWT (defence in depth, spec §5.4) — that is
  already true for auth-service, which never trusts the headers.

## D-043 — Correlation id and the gateway error shape
- `X-Correlation-Id` is generated (UUID) or honoured from the caller, forwarded downstream, and
  echoed in the response header. It is implemented as a **`WebFilter`**, not a gateway
  `GlobalFilter`, so it also runs for requests that match no route — otherwise a gateway 404
  would have no `traceId`.
- Request/response logging is a separate `GlobalFilter` (`RequestLoggingFilter`) that sees the
  final status code and duration.
- Gateway-generated errors use the spec §5.3 shape
  `{ timestamp, status, error, message, path, traceId }`, produced by `GatewayErrorWriter` and
  `GlobalErrorWebExceptionHandler`. The two services still return the `ApiResponse` envelope
  (D-031); the frontend will have a single shape once `common-lib` exists. Error codes used:
  `UNAUTHORIZED`, `FORBIDDEN`, `ROUTE_NOT_FOUND`, `SERVICE_UNAVAILABLE`, `RATE_LIMIT_EXCEEDED`.
- MDC (`traceId`/`userId`/`service`) is **not** wired yet: reactive context propagation needs the
  observability phase. The correlation id is logged inline for now.

## D-044 — Rate limiting
- `5 requests / 60 s per client IP` on `/api/v1/auth/login`, `/register`, `/forgot-password` and
  `/reset-password` (spec §18). Exceeding it returns **429** with a `Retry-After` header.
- Implemented **in memory** (fixed window, `ConcurrentHashMap`) in `RateLimitFilter`. The spec
  allows "Redis or in-memory"; Redis is not part of the platform yet, and the counter is only
  correct per gateway replica. It moves to the Redis-backed `RequestRateLimiter` when Redis lands.
- The client IP is taken from `X-Forwarded-For` (first entry) and falls back to the socket address.

## D-045 — CORS
A `CorsWebFilter` answers pre-flight requests before the route handler, restricted to the Angular
origin (`metashop.gateway.cors.allowed-origins`, default `http://localhost:4200`, env
`ANGULAR_ORIGIN`). `allow-credentials=true` (so an explicit origin list is required, never `*`),
and `X-Correlation-Id` is exposed to the browser.

## D-046 — auth-service and product-service now register with Eureka
The gateway routes through `lb://auth-service` / `lb://product-service`, so both services needed
the Eureka client. The change is **additive** — no endpoint, DTO or business rule was touched:

- `spring-cloud-starter-netflix-eureka-client` + the `spring-cloud-dependencies` BOM were added to
  both poms (Spring Cloud 2025.1.3, still Spring Boot 4.1.0).
- `eureka.client.service-url.defaultZone` (← `EUREKA_URL`) and `eureka.instance.prefer-ip-address`
  were added to both `application.properties`.
- The single `@SpringBootTest` context test in each service disables Eureka
  (`eureka.client.enabled=false`) so the suites stay hermetic and fast. All 73 + 66 tests still
  pass unchanged.

## D-047 — Gateway actuator
`management.endpoints.web.exposure.include=health,info,gateway` with
`management.endpoint.gateway.access=read-only`, so `/actuator/gateway/routes` lists the active
route table for debugging but cannot mutate it. (Spring Boot 4 rejects the value `read`; the valid
values are `none`, `read-only`, `unrestricted`.)

## D-048 — Phase 1 tests
26 tests in api-gateway: context load, route table (ids, `lb://` URIs, per-route auth metadata,
no internal route), `JwtAuthenticationFilter` (public/JWT/ADMIN, missing/malformed/expired/foreign
token, identity propagation, spoof stripping), `RateLimitFilter` (limit, 429 + `Retry-After`,
per-path, disable switch), `CorrelationIdFilter` and `GatewayErrorWriter`. Plus 1 test in
discovery-server.

---

# Phase 4 — inventory-service

## D-049 — inventory-service schema and the `ENUM` deviation
Database **`metashop_inventory_db`** on port **8083**. The spec (§3.2, §9.2) names the schema
`metashop_inventory`; every other service uses the `_db` suffix (`metashop_auth_db`,
`metashop_product_db`), so the suffix wins for consistency.

Four tables, created by `V1__init.sql`:

| Table | Purpose |
|---|---|
| `inventory` | one counter row per product: `available_quantity`, `reserved_quantity`, `low_stock_threshold`, `version` |
| `inventory_reservations` | one row per `(order_id, product_id)` hold: `quantity`, `status`, `expires_at` |
| `stock_movements` | append-only audit trail: `change_qty`, `reason`, `reference` |
| `processed_events` | Kafka consumer de-duplication (unused until the Kafka phase) |

**Deviation:** `inventory_reservations.status` is `VARCHAR(20)` + a `CHECK` constraint instead of
MySQL `ENUM('RESERVED','CONFIRMED','RELEASED','EXPIRED')`. Hibernate's `ddl-auto=validate` cannot
map a MySQL `ENUM` to a Java `enum` reliably, and the platform rule is that Flyway owns the schema
while Hibernate only validates it (D-007). The `CHECK` gives the same guarantee — only the four
states can be stored — while validating cleanly. The values are mapped with
`@Enumerated(EnumType.STRING)`.

Also added a unique key on `inventory_reservations (order_id, product_id)` so an order can never
hold the same product twice, and an index on `(status, expires_at)` for the expiry sweeper.

## D-050 — `X-Internal-Token` is implemented here, not deferred
Every other service deferred the internal token (D-022) and left `/api/v1/internal/**` open. That
was tolerable while the only internal callers were tests; order-service is about to start holding
real stock through these endpoints, so inventory-service implements it now (spec §5.4, §18).

- `InternalTokenFilter` guards `/api/v1/internal/**`; a missing or wrong `X-Internal-Token` is
  **`403`** with `{"success":false,"message":"Missing or invalid X-Internal-Token"}`. A 403 (not a
  401) is deliberate: the caller is a service, and "you sent the wrong shared secret" is far more
  actionable than a generic "authentication required".
- Comparison is constant-time (`MessageDigest.isEqual`) so the token cannot be guessed byte by byte.
- On success the filter sets an authentication carrying `ROLE_INTERNAL`, and `SecurityConfig`
  requires that authority for the path — so the rule is visible in one place.
- A valid **JWT does not** grant access to an internal endpoint. Internal routes are not
  browser-facing and must not be reachable with a customer token.
- The gateway already refuses to route `/api/v1/internal/**` (D-041), so this is defence in depth
  for traffic that reaches the service port directly.

auth-service and product-service keep their `/internal/**` endpoints open for now and are
back-filled in the hardening phase.

## D-051 — Counter model, display cap and low-stock threshold
`inventory` holds two counters per product rather than a single `quantity`:

- `available_quantity` — sellable now. Public availability and `reserve` operate on this.
- `reserved_quantity` — held by a live reservation. `confirm` consumes it; `release`/`EXPIRED`
  returns it to available.

Keeping them apart is what makes the reservation lifecycle auditable: at any moment
`available + reserved` is the stock on hand, and a held-but-unpaid order is visible rather than
silently missing.

- The **public** `GET /api/v1/inventory/{productId}` caps `availableQuantity` at
  `metashop.inventory.display-quantity-cap` (default `10`) so the storefront can say "only 3 left"
  without letting anyone scrape exact stock levels (spec §9.3). The **internal** availability
  endpoint returns the exact number, because order-service needs it to make a decision.
- `low_stock_threshold` is **per row** (default `5`, from
  `metashop.inventory.default-low-stock-threshold`), so a slow-moving item and a best-seller can
  have different definitions of "low". `GET /admin/inventory/low-stock` compares each row against
  its own threshold; `?threshold=N` overrides it for all rows.
- Out-of-stock is `available_quantity == 0`. There is no `stock_status` column to keep in sync.

## D-052 — Overselling is prevented by an atomic conditional UPDATE
`reserve` never reads a quantity and writes it back. Each line issues one statement (spec §9.4):

```sql
UPDATE inventory
   SET available_quantity = available_quantity - :quantity,
       reserved_quantity  = reserved_quantity  + :quantity,
       version            = version + 1,
       updated_at         = :now
 WHERE product_id = :productId
   AND available_quantity >= :quantity
```

The method returns the affected-row count. Two transactions racing for the last unit both run it;
the database serialises them, the loser re-evaluates `WHERE` against the freshly committed row,
matches nothing and reports **0 rows** — so the reservation is rejected. No application-level lock,
no read-then-write window, no `SELECT ... FOR UPDATE` round trip.

Three details worth recording:

- **`version` is bumped by hand.** The statement is native, so Hibernate's `@Version` handling is
  bypassed. Incrementing the column in the same statement keeps it truthful and makes a concurrent
  JPA-managed write (the admin `PUT`) fail its optimistic-lock check instead of silently winning.
  A stale writer surfaces as `ObjectOptimisticLockingFailureException` → **409** (retryable).
- **`clearAutomatically = true, flushAutomatically = true`.** The native statement bypasses the
  persistence context, so pending inserts are flushed first and the context is cleared afterwards;
  otherwise a later `findByProductId` would hand back a stale cached entity.
- **Lines are processed in ascending `product_id` order.** Two concurrent orders touching the same
  products then take row locks in the same order, so neither can hold a row the other needs while
  waiting for a second one — no deadlock. A single-product order is the degenerate case.

`release` and `confirm` use the same pattern (`WHERE reserved_quantity >= :quantity`), which is why
a double `release` cannot invent stock.

## D-053 — Reservation lifecycle, idempotency and expiry
Statuses: `RESERVED → CONFIRMED | RELEASED | EXPIRED`.

| Transition | Trigger | Counters |
|---|---|---|
| `RESERVED` | `POST /internal/inventory/reserve` | `available −n`, `reserved +n` |
| `CONFIRMED` | `POST /internal/inventory/confirm` (payment succeeded) | `reserved −n` |
| `RELEASED` | `POST /internal/inventory/release` (payment failed / cancelled) | `available +n`, `reserved −n` |
| `EXPIRED` | `ReservationExpirySweeper` after the TTL | `available +n`, `reserved −n` |

- **All-or-nothing.** The whole order is one transaction. If any line cannot be satisfied the
  reservation is refused with **409** and nothing is held — order-service never has to compensate
  for a partially reserved order. The pre-check collects *every* offending line so the customer is
  told about all of them at once, and the conditional UPDATE is still the real guard.
- **Idempotent per `orderId`.** `reserve` on an order that already has reservations returns them
  unchanged instead of holding stock twice; `release` and `confirm` skip lines that are already in
  the target state. This is what makes a Feign retry from order-service safe.
- **Repeated `productId`s are merged** (quantities summed) before anything is written, so the
  unique key on `(order_id, product_id)` can never be violated by a sloppy caller.
- **Illegal transitions are `422`:** releasing a `CONFIRMED` order, or confirming a
  `RELEASED`/`EXPIRED` one. Stock that has been sold must not be handed back by a late "payment
  failed" callback.
- **TTL 15 minutes, swept every 60 s** (spec §9.4). An order that reserves and then dies — the
  customer abandons checkout, order-service crashes before confirming — would otherwise hold stock
  forever. If the counters no longer match the reservation the sweeper leaves the row `RESERVED`
  and retries next pass rather than marking it expired without returning the stock.
- `change_qty` in `stock_movements` is the signed delta applied to **`available_quantity`**. It is
  `0` on `CONFIRM`, because confirming moves units out of *reserved* stock without touching
  available — the row still records that the confirmation happened, with the order id as reference.

## D-054 — Admin API shape
`PUT /api/v1/admin/inventory/{productId}` accepts **exactly one** of:

- `quantity` — set the absolute value (stock take, "I counted 40").
- `adjustment` — a signed delta ("5 arrived damaged").

Supplying both, or neither, is a `400`: the intent would be ambiguous, and silently preferring one
would hide a client bug. An `adjustment` that would take available stock below zero is `422`.
Every non-zero change is written to `stock_movements` with the admin's email as the reference.

`GET /api/v1/admin/inventory/{productId}` is **not** in the spec's endpoint list. It is added
because the public endpoint deliberately hides `reservedQuantity` and `lowStockThreshold`, and an
admin needs both; without it the only exact view would be the low-stock list, which by definition
excludes healthy products.

There is deliberately **no** `GET /inventory/movements` endpoint: it is not in the spec's API list,
and nothing consumes it. The table is written so the audit trail exists and can be inspected
directly or exposed later.

## D-055 — Gateway routes for inventory-service
Added two routes to `GatewayRoutesConfig` (the Phase 1 table was designed to grow this way):

| Path | Target | Auth |
|---|---|---|
| `/api/v1/admin/inventory/**` | `lb://inventory-service` | `ADMIN` |
| `/api/v1/inventory/**` | `lb://inventory-service` | `PUBLIC` |

`/api/v1/internal/**` still has no route, so it stays `404` from the outside. `inventory-service`
registers with Eureka exactly like auth/product (D-046); the gateway route test grew a case
asserting both routes point at `lb://inventory-service` with the right metadata. api-gateway is now
at **27 tests**.

## D-056 — inventory-service tests
**61 tests**, all green, and the suite runs against a real MySQL (Flyway + `ddl-auto=validate` in
the context test).

| Suite | Tests | Covers |
|---|---|---|
| `service/ReservationServiceTest` | 19 | reserve happy path / ordering / idempotent replay / all-or-nothing 409 / missing stock row / duplicate merge / lost race, release + confirm idempotency and illegal transitions, availability defaults, expiry |
| `service/InventoryServiceTest` | 17 | display cap, out-of-stock, create (default + explicit threshold, duplicate, zero movement), update (absolute, delta, both/neither, below zero, no-op), low-stock paging + threshold override |
| `controller/AdminInventoryControllerTest` | 8 | `201` + `Location`, validation failures, duplicate `422`, full view, ambiguous `PUT` `400`, paged low-stock |
| `controller/InternalInventoryControllerTest` | 7 | plain-JSON responses, `409` with `data`, validation `400`, `404`/`422` transitions, availability |
| `controller/InventoryControllerTest` | 2 | public availability, unknown product `404` |
| `security/InternalTokenFilterTest` | 4 | valid token → `ROLE_INTERNAL`, missing/wrong → `403`, public paths untouched |
| `concurrency/ReservationConcurrencyTest` | 3 | **the spec §17 phase 4 proof** |
| `InventoryServiceApplicationTests` | 1 | context load |

`ReservationConcurrencyTest` is the important one and is deliberately **not** `@Transactional`:
each of the 50 worker threads must run in its own transaction, otherwise they would share one
connection and prove nothing. It asserts that 50 threads racing for a single unit produce exactly
one `200` and 49 `409`s with `available=0, reserved=1`; that 20 units against 50 buyers yield
exactly 20 winners; and that a unit still held cannot be won by anyone until it is released.

The same 50-way race was also run **over HTTP against the live stack** (50 parallel `curl`s at
`POST /api/v1/internal/inventory/reserve`): `200 × 1, 409 × 49`, then `available=0, reserved=1`.

---

# Phase 5 — cart-service

## D-057 — cart-service scaffold normalised (D-039 applied)
The remaining Initializr scaffolds were normalised before any code was written. cart-service:
`cart-service/metashop/` flattened to `cart-service/`, Java **21**, Spring Boot **4.1.0**, Spring
Cloud **2025.1.3**, package `com.shopMeta.cart_service`, artifact/name `cart-service`,
`spring.application.name=cart-service`. Port **8084** (D-026 sequence: gateway 8080, auth 8081,
product 8082, inventory 8083). Database **`metashop_cart_db`** (the `_db` suffix convention, D-049),
created with `utf8mb4_unicode_ci` before the first start — Flyway owns the schema and
`ddl-auto=validate` checks it.

## D-058 — The cart stores the minimum; everything else is live
`cart_items` holds only `product_id`, `quantity` and `added_price` (the price at add time, kept
solely to flag "price changed since you added this" in the UI). Name, image, live price and stock
are fetched from product-service / inventory-service **on every cart render** through their
`/api/v1/internal/**` endpoints (D-022 / D-050) and merged in memory. Rationale: a cart that shows a
stale price is misleading, and a snapshot of name/price is exactly what order-service will freeze
into the order — the freeze belongs to the order, not the cart (spec 10.2).

Consequence: when product-service is unreachable the cart is **not** rendered from stale data — the
service answers **503** ("try again shortly") instead of inventing prices (spec 19). An honest error
beats a wrong total.

## D-059 — Cart rules (spec 10.3)
- One cart per user, created lazily by the first mutation; `GET /api/v1/cart` for a user without a
  cart returns an **empty cart (200)**, never 404, and creates no row.
- There is **no guest cart**: the platform has no anonymous identity yet, so every cart endpoint
  requires a JWT and the owner is the token subject — a request body never carries a user id.
  `X-User-Id` from the gateway is a convenience, not the source of truth.
- Lines are addressed by **`itemId`**, not `productId` (the storefront gets both in the response).
  A line of another user is indistinguishable from a missing one → **404** (no existence leak).
- `PUT /items/{itemId}` is an **absolute** quantity (1..20); removing is `DELETE`, so 0 is rejected.
- Caps: `metashop.cart.max-quantity-per-item` (default **20**, mirrored by a `CHECK` in the
  migration) and `metashop.cart.max-distinct-items` (default **50**, keeps the downstream batch
  calls small; both are 422).
- `POST /api/v1/cart/items` validates the **resulting** quantity (existing + added): product must
  exist and be active (404 / 422), stock must cover the result (409 with `{productId, available}`).
  Lowering a quantity never re-checks stock.
- Mutations return the **whole updated cart**, so the Angular page and its badge refresh in one
  round trip.

## D-060 — `POST /api/v1/cart/validate` (pre-checkout)
Reports every line that would stop a checkout (`CART_EMPTY`, `PRODUCT_NOT_FOUND`, `PRODUCT_INACTIVE`,
`OUT_OF_STOCK`, `INSUFFICIENT_STOCK`, `QUANTITY_LIMIT_EXCEEDED`) in one answer, **always 200** — an
invalid cart is an answer to a question, not an error. The storefront calls it when the checkout
page opens; order-service repeats the check authoritatively when it reserves stock, because stock
can move between validate and reserve.

The same rules also render as per-line `warning`s in every cart response (`hasWarnings` /
`warnings` on the envelope): a line that went out of stock stays visible and is flagged, **not
silently dropped** — the user must see what they had.

## D-061 — Internal cart API
`DELETE /api/v1/internal/cart/{userId}` clears a user's cart once their order is confirmed
(plain JSON, `204`, idempotent — a replayed confirmation is harmless). Guarded by
`X-Internal-Token` via the same `InternalTokenFilter` as inventory-service (D-050) and **not
routed** by the gateway (D-041). Deliberately the only internal operation: order-service reads the
cart through the public JWT API of the buyer's session; only the clearing needs a service call.

## D-062 — Resilience of the downstream calls (spec 5.8 / 5.10)
- `CatalogClient` is the single place that talks to the other services. Every call is decorated
  `Retry(CircuitBreaker(call))` — the retry is outermost, so one blip is retried before the breaker
  counts it; an open breaker fails fast instead of hammering a struggling service.
- Retry only what is worth retrying: connection errors, timeouts and 5xx. A 4xx is the caller's
  mistake; repeating it would fail again. Both calls the service makes are idempotent reads, which
  is what makes retrying safe. Config lives in `metashop.resilience.*` (`ResilienceProperties`),
  built programmatically in `ResilienceConfig` so the decoration order is explicit and testable —
  no annotations to misread.
- The timeout is Feign's (`spring.cloud.openfeign.client.config.default.*`): Resilience4j's
  `TimeLimiter` only applies to reactive calls and this service is blocking MVC.
- **No Feign call runs inside a database transaction**: `CartStore` (persistence) is separate from
  `CartService` (rules), the stored cart is read into the `CartData` record first, and the
  transaction closes before the catalogue is consulted (spec 5.10).

## D-063 — Concurrent adds are decided by the database
The unique key on `cart_items (cart_id, product_id)` is the arbiter of an add race: two requests
adding the same product to a fresh cart both try to insert; exactly one wins, the other catches
`DataIntegrityViolationException` and retries **once** onto the update branch. The `carts` row
additionally carries a `@Version` column — two racing mutations of the same cart make the loser
fail with a retryable `409`. Quantity updates and deletes are conditional statements scoped by
`cart_id` (`updateQuantity` / `deleteByIdAndCartId`), so a foreign `itemId` simply matches no row.

## D-064 — Gateway route for cart-service
One route added to `GatewayRoutesConfig` (the table grows per service, D-055):

| Path | Target | Auth |
|---|---|---|
| `/api/v1/cart/**` | `lb://cart-service` | `JWT` |

There is no public cart read, so the whole service is JWT. `/api/v1/internal/cart/**` has no route.
api-gateway is now at **28 tests** (route id + auth metadata assertion added).

## D-065 — cart-service tests
**39 tests**, all green, against a real MySQL (Flyway + `ddl-auto=validate` in the context test):

| Suite | Tests | Covers |
|---|---|---|
| `service/CartServiceTest` | 20 | empty cart render, live prices / `priceChanged`, vanished-product and stock warnings, add validation (unknown 404, inactive 422, caps 422, stock 409), add-race retry, ownership 404, clear idempotency, validate issues |
| `service/CatalogClientTest` | 5 | batch answers keyed by product id, empty-request short circuit, retry of a transient failure before the breaker counts it, fail-fast of a non-retryable failure, 503 mapping |
| `controller/CartControllerTest` | 12 | routing, DTO shape (no envelope), validation maps (400), 404 / 409 / 503 envelopes, totals rendering |
| `controller/InternalCartControllerTest` | 1 | plain 204 delegation |
| `CartServiceApplicationTests` | 1 | context load (Eureka off) |

## D-066 — order-service scaffold normalised (D-039 applied)
The Initializr scaffold nested in `order-service/metashop/` was flattened into `order-service/`
and pinned to the platform versions: Spring Boot **4.1.0**, Java **21**, Spring Cloud **2025.1.3**,
package `com.shopMeta.order_service`, artifact `order-service`, port **8085**, schema
`metashop_order_db`. Dependencies mirror cart-service (openfeign, loadbalancer,
circuitbreaker-resilience4j, aspectj, eureka, jjwt, flyway + flyway-mysql, springdoc) — Boot 4
modular starters throughout.

## D-067 — The order is a receipt: everything is a snapshot (spec 10.2)
`orders` freezes the money (`subtotal`, `shipping_fee`, `total`) and the shipping address; each
`order_items` row freezes `sku` / `name` / `image_url` / `unit_price`. Nothing on an order is a
live reference — a price change, an image swap, an address edit or a product deletion tomorrow
never rewrites history. The buyer's cart stays live (D-058); the order is where liveness stops.
Money rules: flat `metashop.order.shipping-fee` (49), waived from
`metashop.order.free-shipping-threshold` (499) subtotal upward.

## D-068 — Checkout is a saga with compensation, and no Feign inside a transaction (D-062 applied)
Downstream reads happen **before** any write, in this order: cart read + validation (with the
buyer's forwarded JWT, D-061) → product snapshots (batch) → address snapshot → one short
transaction persisting the `CREATED` order → stock reservation for the new `orderId` (idempotent
per `orderId`, D-053). When the reservation fails — `409` out of stock or `503` outage — the
freshly created row is **compensated to `CANCELLED`** so no order lingers without its reservation,
and the business answer is rethrown (`OrderStore` keeps persistence separate from `OrderService`
orchestration, exactly the `CartStore`/`CartService` split).

## D-069 — The cart is read through the buyer's own JWT (D-061 applied)
`CartSessionClient` calls cart-service's **public** `GET /api/v1/cart` and `POST /api/v1/cart/validate`
with the caller's `Authorization` header forwarded by a **per-client** Feign configuration
(`FeignUserTokenConfig`, deliberately not `@Configuration` so the interceptor cannot leak onto the
other clients). The internal token goes out globally (`FeignInternalTokenConfig`); the two
cart-service clients are split by `contextId` because they authenticate differently. There is no
internal "read another user's cart" endpoint and none is wanted.

## D-070 — The lifecycle is guarded by status rules + optimistic locking
`CREATED → CONFIRMED → SHIPPED → DELIVERED`, with `CREATED → CANCELLED` for payment failure / owner
cancel / admin cancel; `CANCELLED` and `DELIVERED` are terminal; every other transition is a `422`.
The `orders` row carries a `@Version` column, so a racing payment callback and admin update fail
the loser with a retryable `409`. Two deliberate restrictions: **admin cannot set `CONFIRMED`**
(confirmation is payment's job) and **a paid order cannot be cancelled here** (inventory refuses to
release a confirmed reservation — D-053's ledger — and refunds arrive with payment-service).

## D-071 — Payment results enter through internal endpoints (built ahead of payment-service)
`POST /api/v1/internal/orders/{id}/payment-confirmed` and `/payment-failed` (X-Internal-Token, plain
JSON, unrouted — the D-041/D-022 conventions) are built now because payment-service (`:8086`) is the
very next phase, the same reasoning as cart's internal clear (D-061). Both are **idempotent**: a
replayed success returns the confirmed order, a replayed failure the cancelled one — no downstream
calls on replay. Success confirms the stock (holds become sales) and then clears the cart best-effort:
a cart-service hiccup must not fail an already-confirmed payment. Failure releases the stock **before**
the status write, so a crash between the two leaves the order retryable rather than stranded.

## D-072 — Downstream business answers do not feed the circuit breaker
`ResilienceConfig` ignores `FeignException.FeignClientException` (4xx) at the breaker: a `409` out
of stock or a `404` unknown address is a legitimate answer, not a sign the downstream service is
unhealthy, and must not push the failure rate toward OPEN. The 4xx still propagate (wrapped in
`DownstreamServiceException` by `call()`), and the typed wrappers unwrap the two that matter:
inventory's reserve `409` becomes `InsufficientStockException` **with the offending lines parsed
from the response body**, and auth's address `404` becomes `ResourceNotFoundException`. Five
circuit breakers exist as beans (product / inventory / auth / cart + the shared retry) with one
breaker per downstream service, so a flaky inventory cannot cut off address lookups too.

## D-073 — Gateway routes for order-service
Two routes added to `GatewayRoutesConfig` (the table grows per service, D-055/D-064):

| Path | Target | Auth |
|---|---|---|
| `/api/v1/admin/orders/**` | `lb://order-service` | `ADMIN` |
| `/api/v1/orders/**` | `lb://order-service` | `JWT` |

`/api/v1/internal/orders/**` (the payment callbacks) has no route. api-gateway is now at **29 tests**
(route id + auth metadata assertions added).

## D-074 — order-service tests
**57 tests**, all green, against a real MySQL (Flyway + `ddl-auto=validate` in the context test):

| Suite | Tests | Covers |
|---|---|---|
| `service/OrderServiceTest` | 28 | the checkout saga (happy path money snapshot + reserve call, free shipping, empty cart 422, blocked validation 409, catalogue drift 422, inactive 422, foreign address 404, reserve-409 and outage compensations), ownership 404s, admin read, cancel rules (release before write, paid order refused), every legal/illegal admin transition, payment callbacks incl. idempotent replays and the swallowed cart-clear failure |
| `service/DownstreamClientsTest` | 12 | batch keyed by id, empty-request short circuit, retry-then-succeed, single-attempt non-retryable, 409 body parsing (+ garbage-body tolerance), reserve outage 503, expired-reservation 422 unwrap, address 404 unwrap, cart clear/load outage mapping |
| `controller/OrderControllerTest` | 10 | checkout 201 shape, validation maps, 409 with the unavailable lines, blocked-checkout 409, 503 honesty, paged list, order read, 404 envelope, cancel mapping |
| `controller/AdminOrderControllerTest` | 3 | admin list with status filter, ship transition, illegal transition 422 |
| `controller/InternalOrderControllerTest` | 3 | payment result callbacks, plain JSON (no envelope), 404 mapping |
| `OrderServiceApplicationTests` | 1 | context load (Eureka off) |

## D-075 — payment-service: a ledger of attempts, amounts taken from the order
payment-service (`:8086`, `com.shopMeta.payment_service`, schema `metashop_payment_db`) keeps **one
row per payment attempt**. The client only names the order and the method (`CARD`, `UPI`,
`WALLET`) — the amount is always the order's frozen total, read from order-service's internal API,
never from the request body. A decline stays in the ledger as `FAILED`; the retry is a new row.
Methods run through the same mock gateway today; the enum exists so the ledger and the future
notification emails can distinguish them. Gateway behaviour is `metashop.payment.gateway-mode`
(`SUCCESS` default, `FAILURE` to demo the release path, `RANDOM`).

## D-076 — order-service grew the internal read payment-service needs
`GET /api/v1/internal/orders/{id}` (X-Internal-Token, plain JSON per D-022) returns the full order
snapshot including the owner id. Payment-service checks ownership and the lifecycle status itself
(a foreign order is the same `404` as a missing one, ids cannot be probed). order-service is now at
**59 tests** (controller tests for the read), payment-service's `OrderClient` consumes it.

## D-077 — the payment flow and its honesty rules
One attempt: authoritative order read → ledger guards (`422` if the order is not `CREATED` or
already paid, `422` if a charge is already in flight) → row `PENDING` → charge → report. No Feign
inside a transaction (D-062/D-068). Honesty rules for the awkward combinations:

- **Captured, then order-service refuses (`422`)** — reservation expired, or a racing attempt won:
  the row is rewritten `FAILED` with the reason and the caller gets the `422`. Never a quiet
  `success=false`, which would look like a plain decline while the money was taken.
- **Captured, but order-service is unreachable** — the row stays `SUCCEEDED` (the money is real;
  only the confirmation is pending) and the caller gets the `503`. A reconciliation sweep for this
  window is future work.
- **Declined** — the row is `FAILED`, order-service's `payment-failed` callback is reported
  best-effort (a decline-report outage never hides the decline from the customer; the order side
  reconciles through its reservation TTL / the owner's cancel).

The HTTP shape of a decline: `200` with `success=false` and the `FAILED` payment in `data` — the
attempt is a recorded fact, its outcome is data. A capture is `201`. The `payment-confirmed` /
`payment-failed` callbacks are idempotent (D-071), which is what makes the retry policy on the
report calls safe.

## D-078 — Gateway routes for payment-service

| Path | Target | Auth |
|---|---|---|
| `/api/v1/admin/payments/**` | `lb://payment-service` | `ADMIN` |
| `/api/v1/payments/**` | `lb://payment-service` | `JWT` |

api-gateway is now at **30 tests**.

## D-079 — payment-service tests
**35 tests**, all green, against a real MySQL (Flyway + `ddl-auto=validate` in the context test):

| Suite | Tests | Covers |
|---|---|---|
| `service/PaymentServiceTest` | 16 | the flow (capture + report, amount from the order, unknown/foreign order 404, not-`CREATED` 422, already-paid 422, in-flight 422), declines (reported, report outage swallowed), the honesty rules (refused capture → `FAILED` + 422, unreportable capture → stays `SUCCEEDED` + 503), reads (owner, foreign-is-404, admin), filters |
| `service/DownstreamClientsTest` | 7 | order 404 unwrap, outage → 503 after retries, callback 422 unwrap (not retried), idempotent-callback retry then succeed, non-retryable single attempt |
| `controller/PaymentControllerTest` | 9 | pay 201 captured / 200 declined shapes, validation map, 422/404/503 mappings, reads, paged list |
| `controller/AdminPaymentControllerTest` | 2 | status and order filters |
| `PaymentServiceApplicationTests` | 1 | context load (Eureka off) |
