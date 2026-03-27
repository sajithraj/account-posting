# Account Posting Orchestrator

Accepts posting requests from upstream banking systems, routes them to core banking targets (CBS, GL, OBPM) via a
configurable routing strategy, tracks every response leg, and provides a React UI with search, retry, and manual
override capability.

---

## Project Layout

```
project/
├── backend/    Spring Boot 3.5 · Java 17 · Maven            port 8080
├── db/         Flyway migration scripts (env-specific)
├── ui/         React 18 · TypeScript · Vite                 port 3000
├── deploy/     Docker Compose files per environment
├── docs/       Architecture diagrams (Mermaid)
└── openapi.yml Full API spec (OpenAPI 3.0)
```

Each module has its own README with full details:

- [`backend/README.md`](backend/README.md) — API reference, package structure, flows, design notes
- [`db/README.md`](db/README.md) — Migration strategy, environment folders, promotion workflow

---

## Prerequisites

| Tool   | Min Version | Purpose                        |
|--------|-------------|--------------------------------|
| Java   | 17          | Backend runtime                |
| Maven  | 3.9         | Backend build + db migrations  |
| Node   | 20          | UI build                       |
| Docker | 24          | Containerised stack / Postgres |

---

## Quick Start — Docker (recommended)

```bash
# From the project root
docker compose -f deploy/docker-compose.dev.yml up --build -d
```

Services start in dependency order:

| Order | Service    | Waits for        | URL                     |
|-------|------------|------------------|-------------------------|
| 1     | `postgres` | —                | `localhost:5432`        |
| 2     | `flyway`   | postgres healthy | (exits after migration) |
| 3     | `backend`  | flyway completed | `http://localhost:8080` |
| 4     | `ui`       | backend healthy  | `http://localhost:3000` |

```bash
docker compose -f deploy/docker-compose.dev.yml down      # stop, keep data
docker compose -f deploy/docker-compose.dev.yml down -v   # stop + wipe volume
```

---

## Quick Start — Local (H2, no Docker)

```bash
# 1. Start the backend (H2 in-memory, seed data auto-loaded)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 2. Start the UI
cd ui
npm install
npm run dev
```

`local` profile uses H2 in-memory — no database setup required.
Seed data (`data-local.sql`) is loaded automatically on startup.

---

## Quick Start — Local backend against Docker Postgres

```bash
# 1. Start only Postgres + run migrations
docker compose -f deploy/docker-compose.dev.yml up -d postgres
cd db && mvn flyway:migrate -Pdev

# 2. Start backend with dev profile
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Base URL

All endpoints are served at `http://localhost:8080` (no `/api` prefix).

### Core Endpoints

| Method   | Path                                             | Description                     |
|----------|--------------------------------------------------|---------------------------------|
| `POST`   | `/v2/payment/account-posting`                    | Submit a new posting            |
| `GET`    | `/v2/payment/account-posting`                    | Search postings (paginated)     |
| `GET`    | `/v2/payment/account-posting/{postingId}`        | Get posting + all legs          |
| `POST`   | `/v2/payment/account-posting/retry`              | Retry PNDG postings             |
| `GET`    | `/v2/payment/account-posting/history`            | Search archived postings        |
| `GET`    | `/v2/payment/account-posting/{id}/leg`           | List legs for a posting         |
| `GET`    | `/v2/payment/account-posting/{id}/leg/{legId}`   | Get a single leg                |
| `PATCH`  | `/v2/payment/account-posting/{id}/leg/{legId}`   | Manual leg status override (UI) |
| `GET`    | `/v2/payment/account-posting/config`             | List all routing configs        |
| `POST`   | `/v2/payment/account-posting/config`             | Create routing config entry     |
| `PUT`    | `/v2/payment/account-posting/config/{configId}`  | Update routing config entry     |
| `DELETE` | `/v2/payment/account-posting/config/{configId}`  | Delete routing config entry     |
| `POST`   | `/v2/payment/account-posting/config/cache/flush` | Flush config cache              |

Full spec: [`openapi.yml`](openapi.yml)

---

## Routing Config (canonical seed data)

| source_name  | request_type           | target legs (order → target : operation)       |
|--------------|------------------------|------------------------------------------------|
| `IMX`        | `IMX_CBS_GL`           | 1→CBS:POSTING, 2→GL:POSTING                    |
| `IMX`        | `IMX_OBPM`             | 1→OBPM:POSTING                                 |
| `RMS`        | `FED_RETURN`           | 1→CBS:POSTING, 2→GL:POSTING                    |
| `RMS`        | `GL_RETURN`            | 1→GL:POSTING, 2→GL:POSTING                     |
| `RMS`        | `MCA_RETURN`           | 1→OBPM:POSTING                                 |
| `STABLECOIN` | `BUY_CUSTOMER_POSTING` | 1→CBS:REMOVE_HOLD, 2→CBS:POSTING, 3→GL:POSTING |
| `STABLECOIN` | `ADD_ACCOUNT_HOLD`     | 1→CBS:ADD_HOLD                                 |
| `STABLECOIN` | `CUSTOMER_POSTING`     | 1→CBS:POSTING, 2→GL:POSTING                    |

---

## Posting Status Flow

```
         ┌─────────┐
POST ──► │  PNDG   │ ──► all legs SUCCESS ──► ACSP
         │(pending)│ ──► any leg fails    ──► stays PNDG (retryable)
         └─────────┘ ──► validation fail  ──► RJCT (terminal)
```

| Status | Meaning                                    |
|--------|--------------------------------------------|
| `PNDG` | Pending — one or more legs not yet SUCCESS |
| `ACSP` | Accepted / Success — all legs succeeded    |
| `RJCT` | Rejected — validation or config failure    |

---

## Environment Summary

| Env   | Compose File                     | Spring Profile | DB         |
|-------|----------------------------------|----------------|------------|
| local | (no Docker — H2 in-memory)       | `local`        | H2         |
| dev   | `deploy/docker-compose.dev.yml`  | `dev`          | PostgreSQL |
| qa    | `deploy/docker-compose.qa.yml`   | `qa`           | PostgreSQL |
| uat   | `deploy/docker-compose.uat.yml`  | `uat`          | PostgreSQL |
| prod  | `deploy/docker-compose.prod.yml` | `prod`         | PostgreSQL |
