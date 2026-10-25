# MetaShop

Industry-style e-commerce platform: Spring Boot microservices + MySQL + Kafka + Angular.
This repository is being built phase by phase following `MetaShop-Master-Spec.md`.
Foundational choices and deviations are recorded in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Status

| Service | Port | Status |
|---|---|---|
| **auth-service** | **8081** | ✅ complete (see below) |
| **product-service** | **8082** | ✅ complete (see below) |
| **discovery-server** | **8761** | ✅ complete (see below) |
| **api-gateway** | **8080** | ✅ complete (see below) |
| **inventory-service** | **8083** | ✅ complete (see below) |
| **cart-service** | **8084** | ✅ complete (see below) |
| **order-service** | **8085** | ✅ complete (see below) |
| **payment-service** | **8086** | ✅ complete (see below) |
| **notification-service** | **8087** | ✅ complete (see below) |
| **frontend** | **4200** | ✅ **complete (F1–F6)** — storefront + admin console, see `frontend/README.md` |

All nine backend services are complete. The one piece of spec §14 not yet wired is **event
production**: the services that own the domain events (auth, order, payment, inventory) do not
publish them to Kafka yet, so notification-service currently only receives what is published to the
broker by hand (see its section below). That is the next phase.

## Repository layout

```
MetaShop/
├── docker-compose.infra.yml    # Kafka + Mailpit + Redis (dev infrastructure)
├── docs/
│   ├── DECISIONS.md            # design decisions & spec deviations
│   ├── API.md                  # API reference
│   └── postman/MetaShop.postman_collection.json
├── discovery-server/           # Eureka registry      (:8761) — complete
├── api-gateway/                # Spring Cloud Gateway (:8080) — complete
├── auth-service/               # auth / users         (:8081) — complete
├── product-service/            # catalog              (:8082) — complete
├── inventory-service/          # stock & reservations (:8083) — complete
├── cart-service/               # shopping cart        (:8084) — complete
├── order-service/              # checkout & orders    (:8085) — complete
├── payment-service/            # payment ledger       (:8086) — complete
├── notification-service/       # notifications        (:8087) — complete
└── frontend/                   # Angular web client   (:4200) — ✅ complete (storefront + admin)
```

> Runtime versions are pinned to what is already in use: **Java 21** and **Spring Boot 4.1.0**
> with **Spring Cloud 2025.1.3** (see D-001, D-039). Each service is currently a standalone Maven
> project; a parent POM + `common-lib` will be introduced with the shared infrastructure phase.

---

## Running the stack

Start the shared dev infrastructure first (Kafka, Mailpit, Redis — see D-080):

```bash
docker compose -f docker-compose.infra.yml up -d
# Kafka :9092 · Mailpit SMTP :1025 / UI :8025 · Redis :6379
```

Then the nine services in this order (each in its own terminal, from the repo root):

```bash
cd discovery-server   && ./mvnw spring-boot:run   # :8761  registry (start first)
cd auth-service       && ./mvnw spring-boot:run   # :8081
cd product-service    && ./mvnw spring-boot:run   # :8082
cd inventory-service  && ./mvnw spring-boot:run   # :8083
cd cart-service       && ./mvnw spring-boot:run   # :8084
cd order-service      && ./mvnw spring-boot:run   # :8085
cd payment-service    && ./mvnw spring-boot:run   # :8086
cd notification-service && ./mvnw spring-boot:run # :8087  (needs Kafka + Mailpit)
cd api-gateway        && ./mvnw spring-boot:run   # :8080  public entry point (start last)
cd frontend           && pnpm start               # :4200  web client (any time after the gateway)
```

Then:

- Eureka dashboard: http://localhost:8761/ — should list `API-GATEWAY`, `AUTH-SERVICE`,
  `PRODUCT-SERVICE`, `INVENTORY-SERVICE`, `CART-SERVICE`, `ORDER-SERVICE`, `PAYMENT-SERVICE`,
  `NOTIFICATION-SERVICE`
- Gateway route table: http://localhost:8080/actuator/gateway/routes
- Sent emails: http://localhost:8025 (Mailpit inbox)
- Everything goes through **http://localhost:8080** — e.g.
  `curl http://localhost:8080/api/v1/products?size=2`

The gateway resolves services from Eureka, so it needs a service to have registered **and** its own
registry cache to have refreshed (30 s by default). On a cold start allow ~30–60 s after all nine
are up before the first routed call.

> Internal, service-to-service endpoints (`/api/v1/internal/**`) are **not** routed by the gateway
> and are called on the service port directly with an `X-Internal-Token` header (D-041, D-050).

---

## discovery-server

Eureka service registry (spec §6.1). Every service registers here; the gateway resolves
`lb://<service-name>` through it.

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Cloud Netflix Eureka Server 5.0.2 · Spring Cloud 2025.1.3.

### Run

```bash
cd discovery-server
./mvnw spring-boot:run         # starts on :8761
```

- Dashboard: http://localhost:8761/
- Registry (JSON): http://localhost:8761/eureka/apps
- Health: http://localhost:8761/actuator/health

A registry does not register with itself (`register-with-eureka=false`). Self-preservation is off
so a stopped service disappears quickly during development. There is no Spring Security on the
registry — see D-040.

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8761` | spec §3.1 |
| `eureka.server.eviction-interval-timer-in-ms` | `5000` | faster dev feedback |

### Test

```bash
cd discovery-server
./mvnw test                    # 1 test (context load)
```

---

## api-gateway

Single public entry point (spec §6.2). Routes `/api/v1/**` to services through Eureka, validates
the JWT, propagates the caller identity, applies CORS and rate limiting, and returns one JSON
error shape.

### Tech
Spring Boot 4.1.0 · Java 21 · **Spring Cloud Gateway 5.0.3 (WebFlux)** · Eureka client ·
jjwt 0.12 · Lombok · JUnit 5 + Reactor Test.
The gateway is reactive — `spring-boot-starter-webmvc` must never be on its classpath.

### Run

```bash
cd api-gateway
./mvnw spring-boot:run         # starts on :8080
```

Requires discovery-server plus at least one downstream service to be running.

### Route table

| Path pattern | Target | Auth |
|---|---|---|
| `/api/v1/auth/**` | `lb://auth-service` | Public |
| `/api/v1/users/**` | `lb://auth-service` | JWT |
| `/api/v1/admin/users/**` | `lb://auth-service` | ADMIN |
| `/api/v1/admin/{products,categories,brands}/**` | `lb://product-service` | ADMIN |
| `/api/v1/{products,categories,brands}/**` | `lb://product-service` | Public |
| `/api/v1/admin/inventory/**` | `lb://inventory-service` | ADMIN |
| `/api/v1/inventory/**` | `lb://inventory-service` | Public |
| `/api/v1/cart/**` | `lb://cart-service` | JWT |
| `/api/v1/admin/orders/**` | `lb://order-service` | ADMIN |
| `/api/v1/orders/**` | `lb://order-service` | JWT |
| `/api/v1/admin/payments/**` | `lb://payment-service` | ADMIN |
| `/api/v1/payments/**` | `lb://payment-service` | JWT |
| `/api/v1/internal/**` | — | **not routed** (404) |

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8080` | spec §3.1 |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka` | env `EUREKA_URL` |
| `metashop.gateway.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |
| `metashop.gateway.jwt.issuer` | `metashop-auth-service` | |
| `metashop.gateway.cors.allowed-origins[0]` | `http://localhost:4200` | env `ANGULAR_ORIGIN` |
| `metashop.gateway.rate-limit.requests-per-window` | `5` | per IP, per credential endpoint |
| `metashop.gateway.rate-limit.window-seconds` | `60` | |
| `spring.cloud.gateway.server.webflux.httpclient.connect-timeout` | `5000` | ms |
| `spring.cloud.gateway.server.webflux.httpclient.response-timeout` | `15s` | |

### Behaviour

| Situation | Result |
|---|---|
| No / malformed / expired / foreign-signed token | `401 UNAUTHORIZED` |
| Valid token, non-ADMIN role on an ADMIN route | `403 FORBIDDEN` |
| No route matches | `404 ROUTE_NOT_FOUND` |
| Downstream service down or timed out | `503 SERVICE_UNAVAILABLE` |
| > 5 credential requests/min from one IP | `429 RATE_LIMIT_EXCEEDED` + `Retry-After` |
| Inbound `X-User-*` headers | always stripped (anti-spoofing) |
| `X-Correlation-Id` | generated if absent, forwarded downstream, echoed back |

### Observability

- `GET /actuator/health`
- `GET /actuator/gateway/routes` — the active route table (read-only)

### Test

```bash
cd api-gateway
./mvnw test                    # 29 tests
```

Route table, JWT filter (public/JWT/ADMIN, bad tokens, identity propagation, spoof stripping),
rate limiter, correlation id and error writer.

### Still to do (later phases)
- Redis-backed `RequestRateLimiter` (replaces the in-memory counter) — D-044
- Circuit breaker / retry on downstream failures (`Resilience4J`) — spec §6.2, needs the
  resilience phase
- MDC (`traceId`/`userId`/`service`) context propagation — D-043
- Actuator (`metrics`, `prometheus`) + Dockerfile — deployment phase

---

## auth-service

Auth/User microservice: registration, login, JWT access + refresh tokens (with rotation),
profile, password change, forgot/reset password, address book, roles and admin user
management.

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Security · Spring Data JPA (Hibernate 7) · MySQL 8 ·
**Flyway** · **jjwt 0.12** · Eureka client · Lombok · springdoc-openapi (Swagger UI) ·
JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 on `localhost:3306` with a `root` user

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd auth-service
./mvnw spring-boot:run         # starts on :8081
```

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8081/v3/api-docs

### Configuration

| Property | Default | Notes |
|---|---|---|
| `metashop.jwt.secret` | dev key | **override with `JWT_SECRET`** (≥ 256 bit) |
| `metashop.jwt.access-token-ttl` | `15m` | spec 7.4 |
| `metashop.jwt.refresh-token-ttl` | `7d` | spec 7.4 |
| `metashop.security.max-failed-login-attempts` | `5` | lockout threshold |
| `metashop.security.lock-duration` | `15m` | lockout duration |
| `metashop.security.password-reset-ttl` | `30m` | reset-token lifetime |

### Demo accounts

Seeded outside the `prod` profile (override passwords with `ADMIN_PASSWORD` /
`CUSTOMER_PASSWORD`):

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@metashop.com` | `Admin@12345` |
| CUSTOMER | `customer@metashop.com` | `Customer@12345` |

### Test

```bash
cd auth-service
./mvnw test
```

73 tests (service unit tests with Mockito, controller tests with MockMvc, context test).
MySQL must be reachable because Flyway + Hibernate validation run in the context test.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights:

| Area | Endpoints |
|---|---|
| Auth (public) | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/forgot-password`, `/reset-password` |
| Auth (JWT) | `POST /api/v1/auth/logout` |
| Profile (JWT) | `GET/PUT /api/v1/users/me`, `PUT /api/v1/users/me/password` |
| Address book (JWT) | `/api/v1/users/me/addresses` CRUD + `PATCH /{id}/default` |
| Admin | `GET /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}/status` |
| Internal | `GET /api/v1/internal/users/{id}/addresses/{addressId}` |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `users`, `roles`, `user_roles`,
`addresses`, `refresh_tokens`, `password_reset_tokens`. Hibernate runs with
`ddl-auto=validate`.

### Still to do (later phases)
- `UserRegisteredEvent` / `PasswordResetRequestedEvent` publishing — D-030 (Kafka phase)
- `X-Internal-Token` on `/internal/**` — D-022 pattern, security-hardening phase
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase

> Rate limiting of login/register/forgot-password is enforced at the **api-gateway** (D-044),
> not here.

---

## product-service

Catalog microservice: categories (hierarchical), brands, products, product images and
internal batch lookups. Owns the price source of truth. Stock is **not** stored here — it
belongs to inventory-service (D-005).

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** ·
Eureka client · Lombok · springdoc-openapi (Swagger UI) · JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_product_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd product-service
./mvnw spring-boot:run         # starts on :8082
```

- Swagger UI: http://localhost:8082/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8082/v3/api-docs

### Test

```bash
cd product-service
./mvnw test
```

66 tests (service unit tests with Mockito, controller tests with MockMvc, context test).
MySQL must be reachable because Flyway + Hibernate validation run in the context test.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights:

| Area | Endpoints |
|---|---|
| Public products | `GET /api/v1/products`, `/{id}`, `/slug/{slug}` |
| Public brands | `GET /api/v1/brands`, `/{id}` |
| Public categories | `GET /api/v1/categories` (tree), `/{id}` |
| Admin products | `/api/v1/admin/products` CRUD, `PATCH /{id}/status`, `POST/DELETE /{id}/images` |
| Admin brands / categories | `/api/v1/admin/brands`, `/api/v1/admin/categories` CRUD |
| Internal | `POST /api/v1/internal/products/batch`, `GET /api/v1/internal/products/{id}` |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `categories`, `brands`, `products`,
`product_images`, `product_attributes`. Hibernate runs with `ddl-auto=validate`.

### Seed data

On startup (outside the `prod` profile) `config/DataSeeder` populates a demo catalog:
**6 categories** (Electronics → Mobiles/Laptops, Fashion, Home & Kitchen, Sports),
**10 brands** and **50 products** with an image and two attributes each. It is idempotent —
it skips when `products` is already populated. To reseed, truncate the catalog tables (or drop
and recreate the schema) and restart.

### Still to do (later phases)
- Redis product cache (`GET /api/v1/products/{id}`, TTL 5 min) — D-015/§8.4
- `ProductCreatedEvent` / `ProductPriceChangedEvent` publishing — D-019 (Kafka phase)
- `@PreAuthorize` on `/admin/**` and `X-Internal-Token` on `/internal/**` — D-010, D-022
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase

---

## inventory-service

Stock source of truth (spec §9). Holds one row of counters per product
(`available_quantity` / `reserved_quantity`), the per-order reservations that move units between
them, and an append-only `stock_movements` audit trail. Its whole reason to exist is the promise
that **stock is never oversold**, even when many orders hit the last unit at the same instant.

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** ·
Spring Security (JWT verification + internal-token filter) · Eureka client · Spring `@Scheduled`
(expiry sweeper) · Lombok · springdoc-openapi (Swagger UI) · JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_inventory_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd inventory-service
./mvnw spring-boot:run         # starts on :8083
```

- Swagger UI: http://localhost:8083/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8083/v3/api-docs

### Test

```bash
cd inventory-service
./mvnw test
```

61 tests. MySQL must be reachable: the context test runs Flyway + Hibernate validation, and
`concurrency/ReservationConcurrencyTest` fires **50 real parallel transactions** at a stock row of
1 and asserts that exactly one wins (spec §17 phase 4).

### How overselling is prevented

`reserve` never reads a quantity and writes it back. Each line runs a single conditional statement:

```sql
UPDATE inventory
   SET available_quantity = available_quantity - :quantity,
       reserved_quantity  = reserved_quantity  + :quantity
 WHERE product_id = :productId
   AND available_quantity >= :quantity
```

Two transactions racing for the last unit both run it; the database serialises them and the loser
updates **zero rows**, so the reservation is rejected with `409`. The whole order is one
transaction, so a failure on the third line rolls the first two back — reserving is genuinely
all-or-nothing. Lines are processed in ascending `product_id` order so two concurrent orders
touching the same products cannot deadlock. See D-052 for the full reasoning.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights:

| Area | Endpoints |
|---|---|
| Public | `GET /api/v1/inventory/{productId}` — in stock / capped quantity |
| Admin | `POST /api/v1/admin/inventory`, `GET`/`PUT /{productId}`, `GET /low-stock` |
| Internal | `POST /api/v1/internal/inventory/{reserve,release,confirm,availability}` |

### Reservation lifecycle

| Transition | Trigger | Effect on counters |
|---|---|---|
| `RESERVED` | order-service reserves at checkout | `available −n`, `reserved +n` |
| `CONFIRMED` | payment succeeded | `reserved −n` (sold for good) |
| `RELEASED` | payment failed / order cancelled | `available +n`, `reserved −n` |
| `EXPIRED` | sweeper: not confirmed within the TTL | `available +n`, `reserved −n` |

`reserve`, `release` and `confirm` are all **idempotent per `orderId`**, so a retried call from
order-service can never hold or return stock twice.

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8083` | spec §3.1 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/metashop_inventory_db` | |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka` | env `EUREKA_URL` |
| `metashop.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |
| `metashop.jwt.issuer` | `metashop-auth-service` | |
| `metashop.internal.token` | dev token | env `INTERNAL_TOKEN`; guards `/api/v1/internal/**` |
| `metashop.inventory.default-low-stock-threshold` | `5` | used when a stock row is created without one |
| `metashop.inventory.reservation-ttl` | `15m` | spec §9.4 |
| `metashop.inventory.sweep-interval` | `60s` | how often the expiry sweeper runs |
| `metashop.inventory.display-quantity-cap` | `10` | public endpoint never reports more than this |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `inventory`, `inventory_reservations`,
`stock_movements` and `processed_events`. Hibernate runs with `ddl-auto=validate`.

### Still to do (later phases)
- `OrderCreatedEvent` / `PaymentCompletedEvent` consumers (Kafka) — §14, D-019
- Redis cache for hot availability reads — §8.4
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase
- Back-fill `X-Internal-Token` onto auth-service and product-service `/internal/**` — D-022, D-050

---

## cart-service

One shopping cart per user (spec §10). The cart stores only `productId` + `quantity` + the price at
add time; **name, image, live price and stock are fetched from product-service / inventory-service
on every render**, so a line can never go stale in the database and the UI can flag "price changed
since you added this" and "only 2 left" (D-058).

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** · Spring Security
(JWT verification + internal-token filter) · Eureka client · **OpenFeign + Spring Cloud LoadBalancer
+ Resilience4j** (retry + circuit breakers on the downstream calls, D-062) · Lombok ·
springdoc-openapi (Swagger UI) · JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)
- product-service (`:8082`) and inventory-service (`:8083`) reachable — the cart renders their data

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd cart-service
./mvnw spring-boot:run         # starts on :8084
```

- Swagger UI: http://localhost:8084/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8084/v3/api-docs

### Test

```bash
cd cart-service
./mvnw test
```

39 tests. MySQL must be reachable: the context test runs Flyway + Hibernate validation.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights (all JWT — the owner is the token
subject, D-059):

| Area | Endpoints |
|---|---|
| Read | `GET /api/v1/cart` — live prices, line totals, subtotal, per-line warnings |
| Write | `POST /api/v1/cart/items` (add/merge), `PUT /api/v1/cart/items/{itemId}` (absolute quantity), `DELETE /api/v1/cart/items/{itemId}`, `DELETE /api/v1/cart` |
| Checkout | `POST /api/v1/cart/validate` — pre-checkout issues, always 200 (D-060) |
| Internal | `DELETE /api/v1/internal/cart/{userId}` — order-service clears the cart after confirmation (D-061) |

Every mutation returns the **whole updated cart**, so the UI refreshes from one round trip.

### Failures you can get (and why they are honest)

| Situation | Status |
|---|---|
| Unknown product | `404` |
| Product deactivated | `422` |
| Over 20 units of one product / 50 distinct products | `422` |
| Not enough stock for the resulting quantity | `409` with `{productId, available}` |
| product-service / inventory-service down or breaker open | `503` — no stale prices are ever rendered (D-058) |

A concurrent double-add is decided by the unique key on `(cart_id, product_id)`: the loser retries
once onto the update branch (D-063). Racing cart-row mutations make the loser fail with a retryable
`409` (`@Version`).

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8084` | spec §3.1 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/metashop_cart_db` | |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka` | env `EUREKA_URL` |
| `metashop.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |
| `metashop.jwt.issuer` | `metashop-auth-service` | |
| `metashop.internal.token` | dev token | env `INTERNAL_TOKEN`; guards `/api/v1/internal/cart/**` and signs outbound Feign calls |
| `metashop.cart.max-quantity-per-item` | `20` | mirrored by a `CHECK` in the migration |
| `metashop.cart.max-distinct-items` | `50` | keeps the downstream batch calls small |
| `metashop.resilience.*` | 3 attempts, 10-call window | retry + circuit-breaker tuning (D-062) |
| `spring.cloud.openfeign.client.config.default.*` | 2 s connect / 5 s read | the downstream timeout |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `carts` and `cart_items`. Hibernate runs with
`ddl-auto=validate`.

### Still to do (later phases)
- `CartClearedEvent` publishing (Kafka) — §14
- Guest carts, once the platform defines an anonymous identity (D-059)
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase

---

## order-service

Checkout and the order lifecycle (spec §11). An order is created **from the caller's cart** — the
only decision in the request is which saved address to ship to. Prices, sku/name/image and the
shipping address are **frozen as snapshots at checkout**, so the order is the receipt and never
moves when the catalogue or the address book changes afterwards (D-067).

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** · Spring Security
(JWT verification + internal-token filter) · Eureka client · **OpenFeign + Spring Cloud LoadBalancer
+ Resilience4j** (retry + a circuit breaker per downstream service) · Lombok · springdoc-openapi
(Swagger UI) · JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)
- auth-service (`:8081`), product-service (`:8082`), inventory-service (`:8083`) and cart-service
  (`:8084`) reachable — checkout reads all four

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_order_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd order-service
./mvnw spring-boot:run         # starts on :8085
```

- Swagger UI: http://localhost:8085/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8085/v3/api-docs

### Test

```bash
cd order-service
./mvnw test
```

57 tests. MySQL must be reachable: the context test runs Flyway + Hibernate validation.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights:

| Area | Endpoints |
|---|---|
| Checkout | `POST /api/v1/orders` — body `{shippingAddressId}`; builds the order from the caller's cart, freezes the snapshots, reserves stock → `201` |
| Read | `GET /api/v1/orders` (paged), `GET /api/v1/orders/{id}` (owner or admin) |
| Customer | `POST /api/v1/orders/{id}/cancel` — while `CREATED`; releases the stock |
| Admin | `GET /api/v1/admin/orders` (`?status=`), `POST /api/v1/admin/orders/{id}/status` (`SHIPPED` / `DELIVERED` / `CANCELLED`) |
| Internal | `POST /api/v1/internal/orders/{id}/payment-confirmed` / `payment-failed` — payment-service's callbacks (D-071), built ahead of that phase |

### Checkout saga (D-068)

Cart read + validation with the **buyer's own forwarded JWT** (D-069) → product snapshots → address
snapshot → one transaction persisting `CREATED` → stock reservation (idempotent per `orderId`).
Any failure at reserve time compensates the order to `CANCELLED` and rethrows the honest answer:
`409` with the unavailable lines, or `503` for an outage. No Feign call runs inside a transaction.

### Status lifecycle

`CREATED → CONFIRMED → SHIPPED → DELIVERED`, `CREATED → CANCELLED`; `CANCELLED` / `DELIVERED`
terminal. `CONFIRMED` is payment's job, not an admin's; a paid order is not cancellable here
(refunds arrive with payment-service). Illegal transitions are `422`; racing writers are decided by
the `@Version` column with a retryable `409` (D-070).

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8085` | spec §3.1 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/metashop_order_db` | |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka` | env `EUREKA_URL` |
| `metashop.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |
| `metashop.jwt.issuer` | `metashop-auth-service` | |
| `metashop.internal.token` | dev token | env `INTERNAL_TOKEN`; guards `/api/v1/internal/orders/**` and signs outbound Feign calls |
| `metashop.order.shipping-fee` | `49` | flat fee, waived above the threshold |
| `metashop.order.free-shipping-threshold` | `499` | subtotal from which shipping is free |
| `metashop.resilience.*` | 3 attempts, 10-call window | retry + circuit-breaker tuning (D-062/D-072) |
| `spring.cloud.openfeign.client.config.default.*` | 2 s connect / 5 s read | the downstream timeout |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `orders` (money + address snapshots, `CHECK`
constraints on both enums) and `order_items` (the frozen product snapshots). Hibernate runs with
`ddl-auto=validate`.

### Still to do (later phases)
- `OrderCreatedEvent` / `OrderConfirmedEvent` publishing (Kafka) — §14, D-019
- A refund-aware cancellation for paid orders (with payment-service's refund flow)
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase

---

## payment-service

The payment ledger (spec §12). The customer pays for an order through the **mock gateway** — the
only decision in the request is the method (`CARD` / `UPI` / `WALLET`). The amount is always the
order's frozen total, read from order-service's authoritative internal API, and the outcome is
reported back through order-service's `payment-confirmed` / `payment-failed` callbacks, which
confirm or release the reserved stock (D-077). One row per **attempt**: a decline stays in the
ledger as `FAILED`, the retry is a new row.

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** · Spring Security
(JWT verification + internal-token filter) · Eureka client · **OpenFeign + Spring Cloud LoadBalancer
+ Resilience4j** (retry + circuit breaker on order-service) · Lombok · springdoc-openapi
(Swagger UI) · JUnit 5 + Mockito + MockMvc.

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)
- order-service (`:8085`) reachable — every payment reads the order and reports the result to it

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd payment-service
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8086/swagger-ui.html

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8086` | spec §3.1 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/metashop_payment_db` | |
| `eureka.client.service-url.defaultZone` | `http://localhost:8761/eureka` | env `EUREKA_URL` |
| `metashop.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |
| `metashop.jwt.issuer` | `metashop-auth-service` | |
| `metashop.internal.token` | dev token | env `INTERNAL_TOKEN`; signs the outbound calls to order-service |
| `metashop.payment.gateway-mode` | `SUCCESS` | `FAILURE` demos the decline/release path; `RANDOM` flips a coin (D-075) |
| `metashop.resilience.*` | 3 attempts, 10-call window | retry + circuit-breaker tuning (D-077) |
| `spring.cloud.openfeign.client.config.default.*` | 2 s connect / 5 s read | the downstream timeout |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `payments` (one row per attempt, `CHECK`
constraints on method/status/amount). Hibernate runs with `ddl-auto=validate`.

### Test

```bash
cd payment-service
./mvnw test
```

35 tests. MySQL must be reachable: the context test runs Flyway + Hibernate validation.

### API

Full reference in [`docs/API.md`](docs/API.md). Highlights:

| Area | Endpoints |
|---|---|
| Pay | `POST /api/v1/payments` — body `{orderId, method}`; `201` captured (`order → CONFIRMED`) or `200` + `success=false` declined (`order → CANCELLED`) |
| Read | `GET /api/v1/payments` (paged), `GET /api/v1/payments/{id}` (owner or admin) |
| Admin | `GET /api/v1/admin/payments` — the whole ledger, `?status=` / `?orderId=` filters |

### Still to do (later phases)
- A reconciliation sweep for charges captured while order-service was unreachable (currently the
  ledger row stays `SUCCEEDED` and the caller gets a `503`, D-077)
- Refunds (and the refund-aware cancellation of paid orders on order-service)
- `PaymentCompletedEvent` publishing (Kafka) — §14, D-019
- Actuator (`health`, `info`, `metrics`, `prometheus`) + Dockerfile — deployment phase

---

## notification-service

The event consumer and email sender (spec §13). It has **no outbound HTTP calls at all** — no Feign,
no LoadBalancer, no internal-token filter — because its only HTTP surface is the admin notification
log. Everything it does is driven by Kafka: a domain event arrives, is mapped to one of ten
Thymeleaf templates, rendered to HTML, recorded in `notifications`, and sent through Spring Mail.
Kafka delivers at least once, so every event is de-duplicated by `event_id` before anything is sent,
and a delivery that keeps failing is retried with backoff and then dead-lettered (D-082).

### Tech
Spring Boot 4.1.0 · Java 21 · Spring Data JPA (Hibernate 7) · MySQL 8 · **Flyway** ·
**Spring Kafka** (manual acks, `DefaultErrorHandler` + exponential backoff + DLT,
`ErrorHandlingDeserializer`) · **Spring Mail** (Mailpit in dev) · **Thymeleaf** HTML email templates ·
Spring Security (JWT verification) · Eureka client · Lombok · springdoc-openapi (Swagger UI) ·
JUnit 5 + Mockito + MockMvc + `spring-kafka-test` (embedded broker).

### Prerequisites
- Java 21
- MySQL 8 running on `localhost:3306` with a `root` user (dev credentials in
  `src/main/resources/application.properties`)
- Kafka on `localhost:9092` and Mailpit on `localhost:1025` / `http://localhost:8025`:
  `docker compose -f docker-compose.infra.yml up -d`

Create the (empty) schema once; Flyway creates all tables:

```sql
CREATE DATABASE metashop_notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Run

```bash
cd notification-service
./mvnw spring-boot:run
```

Swagger UI: http://localhost:8087/swagger-ui.html · Sent email: http://localhost:8025

The four topics are **not** declared as `NewTopic` beans on purpose: they rely on the broker's
auto-create with `KAFKA_NUM_PARTITIONS=3`, so `KafkaAdmin` never opens a connection at startup and
the test suite stays hermetic (D-082).

### Configuration

| Property | Default | Notes |
|---|---|---|
| `server.port` | `8087` | spec §3.1 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/metashop_notification_db` | |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | env `KAFKA_BOOTSTRAP_SERVERS` |
| `spring.kafka.consumer.group-id` | `notification-service` | one group across all four topics |
| `spring.kafka.listener.ack-mode` | `manual_immediate` | spec §14.4; ack only after the row is `SENT` |
| `spring.mail.host` / `spring.mail.port` | `localhost` / `1025` | env `MAIL_HOST` / `MAIL_PORT`; Mailpit in dev |
| `metashop.notification.from-address` | `no-reply@metashop.com` | env `MAIL_FROM` |
| `metashop.notification.admin-email` | `admin@metashop.com` | low-stock alerts have no owning user (env `NOTIFICATION_ADMIN_EMAIL`) |
| `metashop.notification.frontend-base-url` | `http://localhost:4200` | links inside emails point at the Angular app (env `FRONTEND_BASE_URL`) |
| `metashop.notification.dlt-suffix` | `.DLT` | spec §14.1 names `*.DLT`; the recoverer's own default is `-dlt` |
| `metashop.notification.retry-max-attempts` | `3` | spec §13: three retries, then the dead-letter topic |
| `metashop.notification.retry-initial-interval` | `1s` | doubles each attempt (1s / 2s / 4s) |
| `metashop.notification.retry-multiplier` | `2.0` | |
| `metashop.notification.retry-max-interval` | `10s` | ceiling, so the backoff cannot grow without bound |
| `metashop.jwt.secret` | dev key | **must equal auth-service's**; env `JWT_SECRET` |

### Database migrations

`src/main/resources/db/migration/V1__init.sql` creates `notifications` (the audit log, with the
added `event_id` unique column — D-080) and `processed_events` (the de-duplication table, identical
to the one in inventory/order/payment). Hibernate runs with `ddl-auto=validate`.

### Test

```bash
cd notification-service
./mvnw test
```

57 tests. MySQL must be reachable: the context test runs Flyway + Hibernate validation. The Kafka
tests start their **own embedded broker**, so no `docker compose` is needed to run the suite.

### API

Full reference in [`docs/API.md`](docs/API.md). Admin only (`ROLE_ADMIN`):

| Area | Endpoints |
|---|---|
| List | `GET /api/v1/admin/notifications` — paged, newest first, `?status=PENDING\|SENT\|FAILED`, `?page=`, `?size=` |
| Detail | `GET /api/v1/admin/notifications/{id}` — includes the rendered HTML `body` |
| Retry | `POST /api/v1/admin/notifications/{id}/retry` — re-sends from the stored subject/body; `422` if already `SENT`, `503` if the mail server refuses again |

### Events → emails

Consumes `user.events`, `order.events`, `payment.events`, `inventory.events` (spec §14.1).

| Event | Notification type | Template |
|---|---|---|
| `UserRegisteredEvent` | `WELCOME` | `welcome` |
| `PasswordResetRequestedEvent` | `PASSWORD_RESET` | `password-reset` |
| `OrderConfirmedEvent` | `ORDER_CONFIRMED` | `order-confirmed` (line items + shipping address) |
| `OrderShippedEvent` | `ORDER_SHIPPED` | `order-shipped` |
| `OrderDeliveredEvent` | `ORDER_DELIVERED` | `order-delivered` |
| `OrderCancelledEvent` | `ORDER_CANCELLED` | `order-cancelled` |
| `PaymentSuccessEvent` | `PAYMENT_SUCCESS` | `payment-success` (receipt) |
| `PaymentFailedEvent` | `PAYMENT_FAILED` | `payment-failed` |
| `PaymentRefundedEvent` | `PAYMENT_REFUNDED` | `payment-refunded` |
| `InventoryLowEvent` | `LOW_STOCK` | `low-stock` → the **admin** address, not a customer |

Any other event type on those topics (`OrderCreatedEvent`, `StockReservedEvent`, …) is a logged
skip, not a failure — which is why the consumer tolerates the whole topic rather than a filtered
subset.

> **Known gap (D-081).** The three payment events carry no email address in the spec's payload, and
> no service can resolve user-id → email. They now accept an **optional `email` field**; an event
> without one is logged and skipped rather than dead-lettered, so a producer cannot poison the topic.
> The permanent fix is for payment-service to carry the address onto its events.

### Try it locally

Nothing in the other services publishes to Kafka yet, so drive the consumer by hand:

```bash
# publish one event (Mailpit at :8025 shows the result)
printf '%s\n' '{"eventId":"demo-1","eventType":"OrderConfirmedEvent","occurredAt":"2026-01-01T10:00:00Z","version":1,"payload":{"orderId":42,"orderNumber":"MS-000042","userId":7,"email":"asha@example.com","items":[{"productId":1,"sku":"SKU-1","name":"Wireless Mouse","unitPrice":1299.00,"quantity":2,"lineTotal":2598.00}],"total":2598.00,"shippingAddress":{"fullName":"Asha Rao","phone":"9876543210","line1":"12 MG Road","line2":null,"city":"Bengaluru","state":"KA","postalCode":"560001","country":"India"}}}' \
  | docker exec -i metashop-kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:9092 --topic order.events

# check the audit log (admin token required)
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/admin/notifications?status=SENT'
```

`kafka-console-producer` sends **one record per line**, so the payload must be a single line — a
pretty-printed JSON body is published as several unparseable fragments (which is exactly how the
missing `ErrorHandlingDeserializer` was found, D-082).

### Still to do (later phases)
- **Kafka producers in the owning services** (auth, order, payment, inventory) — the event catalogue
  in spec §14 is defined but not yet published, so notification-service has nothing real to consume
- The payment-service producer change that fills in the optional `email` (D-081)
- Actuator (`metrics`, `prometheus`) + Dockerfile — deployment phase
- A `@Scheduled` sweeper for notifications stuck in `PENDING` after a crash mid-send

---

## frontend



The web client (spec 2): an **Angular 21** single-page app — standalone components, signals,
zoneless change detection — talking to the stack through the API gateway (which already allows the
`http://localhost:4200` origin, D-045).

Built phase by phase (F1 → F6); see **[`frontend/README.md`](frontend/README.md)** for the run
instructions, the phase plan and the styling rules.

- **F1 — auth loop**: a shared design system (tokens, buttons, form fields, animations in one
  `styles.css`) plus a split-screen **sign-in** and **sign-up** page (responsive, animated, fully
  validated) wired to `/api/v1/auth/**` with JWT token storage, an auth interceptor, route guards
  and toast notifications.
- **F2 — app shell + home**: a sticky glass **navbar** (user dropdown, cart badge, mobile menu), a
  **footer**, and the **storefront home page** — animated hero with floating live products, a
  category strip, a "fresh arrivals" grid served by product-service, and skeleton loading states.
  Not-yet-built routes (products, cart, orders) point at honest placeholders.
