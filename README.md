# Flash Sale System

High-performance flash sale backend — Spring Boot 3, Java 21, Redis Lua scripting, async persistence, multi-module architecture.

## Project Structure

```
java-flash-sale/                          (parent pom)
├── flash-sale-common/                    (shared JAR)
│   └── entities, repositories, configs, FlashSaleCacheService
├── flash-sale-api/                       (Spring Boot web app)
│   └── controllers, auth, services, Lua script
└── flash-sale-worker/                    (Spring Boot non-web app)
    └── scheduled cache refresh every 30s
```

| Module | Type | Description |
|--------|------|-------------|
| `flash-sale-common` | JAR (library) | Entities, repositories, Redis/Jackson config, `FlashSaleCacheService` |
| `flash-sale-api` | Spring Boot (web) | REST API, JWT auth, purchase flow, async order persistence |
| `flash-sale-worker` | Spring Boot (non-web) | Background job: refreshes Redis cache every 30s |

## Key Features

- **Strategy Pattern Auth** — email/phone auto-detection, BCrypt hashing, JWT tokens, OTP verification (mock)
- **Atomic Purchase via Redis Lua** — time window, daily limit, stock check, decrement all in 1 atomic script. Zero DB on hot path
- **Normalized DB Design** — `products`, `flash_sale`, `flash_sale_product` (allocated stock per campaign), `inventory` (global stock), `orders`
- **Async Order Persistence** — Spring ApplicationEvent + dedicated thread pool, MySQL row-level locking (atomic UPDATE)
- **Redis Cache Layer** — worker refreshes active items every 30s. `GET /items` = zero DB queries
- **Independent Worker** — cache refresh runs as separate service, independently deployable and scalable
- **1 Purchase Per User Per Day** — enforced atomically in Redis Lua (`fs:user:{userId}:daily:{date}`)
- **Dual Stock Decrement** — purchase decrements both `flash_sale_product.sale_available` and `inventory.available_stock`

## Architecture

```
Client (JWT)
  │
  ▼
┌─────────────────────────────────────────────────┐
│ flash-sale-api (port 8080)                      │
│                                                 │
│ GET /items  (public)                            │
│ Redis GET "fs:active:items" + live stock        │
│ ⚡ Zero DB queries                              │
│                                                 │
│ POST /purchase  (JWT required)                  │
│ Redis Lua Script (atomic, single-threaded):     │
│   1. Check sale time window                     │
│   2. Check user daily limit (1/day)             │
│   3. Check & decrement allocated stock          │
│   4. Set user daily flag                        │
│              │                                  │
│              ▼ success                          │
│   Return orderNo to client immediately          │
│              │                                  │
│              ▼ async (non-blocking)             │
│   PurchaseEvent → OrderPersistenceService       │
│   ├── INSERT order                              │
│   ├── Decrement flash_sale_product.sale_available│
│   └── Decrement inventory.available_stock       │
│       (both with MySQL row-level locking)       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ flash-sale-worker (no web server)               │
│                                                 │
│ @Scheduled every 30s:                           │
│   DB query → serialize → Redis "fs:active:items"│
│   TTL 60s (auto-expire if worker dies)          │
│                                                 │
│ API fallback: if cache miss, API calls          │
│ refreshCache() directly (no downtime)           │
└─────────────────────────────────────────────────┘

┌──────────┐       ┌──────────┐
│  MySQL   │       │  Redis   │
│  (3306)  │       │  (6379)  │
└──────────┘       └──────────┘
```

## Quick Start

```bash
docker compose up --build
```

> Schema changed? Clear volumes first: `docker compose down -v && docker compose up --build`

| Service    | URL                                                   |
|------------|-------------------------------------------------------|
| API        | http://localhost:8080                                  |
| Swagger UI | http://localhost:8080/swagger-ui/index.html            |
| Worker     | no HTTP — logs only                                   |
| MySQL      | localhost:3306 (root/root123, db: flash_sale)         |
| Redis      | localhost:6379                                         |

**Seed data**

- **100 products** across 5 flash sale campaigns (07-09, 10-12, 13-15, 16-18, 19-21), all on today's date.
- **1000 test users** + wallets (from `flash-sale-common/src/main/resources/schema.sql`):
  - Email: `test1@testmail.com` … `test1000@testmail.com`
  - Password: `1234aabb` (BCrypt hashed in DB)
  - Nickname: `test1` … `test1000`, `verified = 1`
  - Each user has a wallet with **random balance 500–5000** (for testing purchase flow).

## API Reference

### Auth

| Method | Endpoint                              | Auth | Description                              |
|--------|---------------------------------------|------|------------------------------------------|
| POST   | `/api/v1/auth/register`               | No   | Register (save to DB + send OTP)         |
| POST   | `/api/v1/auth/register/verify`        | No   | Verify OTP → activate account + JWT      |
| POST   | `/api/v1/auth/register/resend-otp`    | No   | Resend verification OTP                  |
| POST   | `/api/v1/auth/login`                  | No   | Login (identifier + password) → JWT      |
| POST   | `/api/v1/auth/logout`                 | No   | Invalidate JWT (blacklist in Redis)      |
| POST   | `/api/v1/auth/password/reset-request` | No   | Request password reset OTP               |
| POST   | `/api/v1/auth/password/reset`         | No   | Reset password with OTP                  |

### Flash Sale

| Method | Endpoint                       | Auth | Description                              |
|--------|--------------------------------|------|------------------------------------------|
| GET    | `/api/v1/flash-sale/items`     | No   | List active flash sale products (cached) |
| POST   | `/api/v1/flash-sale/purchase`  | JWT  | Purchase (1 per user per day)            |

### Request/Response Examples

**Register:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@example.com","password":"secret123","nickname":"Alice"}' | jq .
# Check Docker logs for OTP
```

**Verify OTP:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register/verify \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@example.com","otp":"XXXXXX"}' | jq .
# Returns: { "data": { "userId": 1, "nickname": "Alice", "token": "eyJhbG..." } }
```

**Login:**
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"alice@example.com","password":"secret123"}' | jq .
```

**Browse flash sale items:**
```bash
curl -s http://localhost:8080/api/v1/flash-sale/items | jq .
```

Response:
```json
{
  "code": 200, "data": [{
    "flashSaleProductId": 1,
    "productId": 1,
    "productName": "Samsung Galaxy S24 Ultra",
    "originalPrice": 1499.99,
    "salePrice": 1299.99,
    "flashSaleName": "Morning Rush: Smartphones & Laptops",
    "startTime": "2026-02-15T07:00:00",
    "endTime": "2026-02-15T09:00:00",
    "availableStock": 300
  }]
}
```

**Purchase (requires JWT):**
```bash
curl -s -X POST http://localhost:8080/api/v1/flash-sale/purchase \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"flashSaleProductId": 1}' | jq .
# Returns: { "data": { "orderNo": "FS-20260215-1-1-A1B2C3D4" } }
```

**Purchase errors:**

| Code | Message                                             |
|------|-----------------------------------------------------|
| 401  | Unauthorized — please login first                   |
| 400  | Flash sale has not started yet                      |
| 400  | Flash sale has already ended                        |
| 400  | You have already purchased a flash sale product today |
| 400  | Item is sold out                                    |

## Database Schema

```
products ──(1:N)── flash_sale_product ──(N:1)── flash_sale
    │                     │
    │ (1:1)               │ (1:N)
    ▼                     ▼
inventory              orders ──(N:1)── users
```

| Table                | Purpose                                            |
|----------------------|----------------------------------------------------|
| `products`           | Master catalog (name, original_price, category)    |
| `inventory`          | Global stock per product (available_stock) |
| `flash_sale`         | Campaign (name, start/end time, status)            |
| `flash_sale_product` | Junction: product allocated to a sale (sale_price, sale_stock, sale_available, per_user_limit) |
| `orders`             | Purchase orders (linked to flash_sale_product_id)  |
| `users`              | Accounts (email/phone, BCrypt password, verified)  |

## Load testing (k6)

A [k6](https://k6.io/) script in the `k6/` folder runs the purchase flow at a target rate (default 500 TPS): **login** → get JWT → **GET /flash-sale/items** → pick a random active item → **POST /flash-sale/purchase** → log response. It uses the 1000 seeded users (`test1@testmail.com` … `test1000@testmail.com`) with password **`1234aabb`**.

### Prerequisites

- API is running (e.g. `docker compose up`).
- Test users are seeded. The k6 script defaults to password **`1234aabb`**. If your seed uses a different password (e.g. `123456` in older schema), pass `-e PASSWORD=123456` when running k6.

### How to run k6 and where to see logs

1. **Start the project** (if not already running):
   ```bash
   docker compose up --build
   ```
2. **Open a new terminal**, go to the project root (`java-flash-sale`), then run k6:
   ```bash
   docker compose --profile load run --rm k6
   ```
3. **Logs:** k6 prints everything in **that same terminal**:
   - Progress lines (e.g. `running (0m15.0s), 000/500 VUs, 7500 iterations`)
   - The script’s `console.log` output (sampled success/failure lines)
   - At the end, a **summary** (http_reqs, http_req_duration, iterations, etc.)

   You don’t need to `docker compose logs` for k6 — just watch the terminal where you ran the command.

### Run with Docker (recommended)

From the project root, with the API already up:

```bash
# Use the k6 service (same network as API; BASE_URL=http://api:8080)
docker compose --profile load run --rm k6
```

Override rate, duration, or password:

```bash
docker compose --profile load run --rm -e RATE=300 -e DURATION=60s -e PASSWORD=1234aabb k6
```

To run k6 in Docker against an API on the **host** (e.g. API on `localhost:8080`):

**Linux:**
```bash
docker run --rm -i --network host -v "$(pwd)/k6:/scripts" grafana/k6 run \
  -e BASE_URL=http://localhost:8080 -e PASSWORD=1234aabb /scripts/purchase.js
```

**Windows (PowerShell):**
```powershell
docker run --rm -i -v "${PWD}/k6:/scripts" grafana/k6 run `
  -e BASE_URL=http://host.docker.internal:8080 -e PASSWORD=1234aabb /scripts/purchase.js
```

### Run with local k6 CLI

Install k6 ([instructions](https://k6.io/docs/getting-started/installation/)), then:

```bash
k6 run -e BASE_URL=http://localhost:8080 -e PASSWORD=1234aabb k6/purchase.js
```

Optional env vars: `RATE` (default 500), `DURATION` (default 30s). The script fetches active items from `GET /api/v1/flash-sale/items` and picks a random `flashSaleProductId` for each purchase.

### Notes

- Each test user can purchase **once per calendar day** (daily limit). With 1000 users, at 500 TPS you get at most ~1000 successful purchases in about 2 seconds; afterwards most requests return “already purchased today”. The script still logs responses so you can observe throughput and latency.
- Ensure at least one flash sale is **active** when you run the test (see seed: campaigns run at 07–09, 10–12, etc. UTC). The script calls `GET /flash-sale/items` and randomly selects an item for each purchase.

## Tech Stack

- Java 21, Spring Boot 3.4.1
- Spring Security + JWT (jjwt 0.12.6)
- MySQL 8.0 + JPA/Hibernate (validate mode)
- Redis 7 + Lua scripting
- OpenAPI/Swagger UI (springdoc 2.8.4)
- Docker Compose (multi-stage build, ZGC)
- Multi-module Maven (common, api, worker)
