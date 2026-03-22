# Account Posting Orchestrator

Accepts posting requests from upstream banking systems, routes them to core banking systems (CBS, GL, OBPM) via a
configurable strategy, tracks every response leg, and provides a React UI with search and retry capability.

---

## Project Layout

```
project/
├── account-posting/    Spring Boot 3.5, Java 21 — port 8080
├── db/                 Flyway migration scripts (PostgreSQL) — env-specific folders
├── ui/                 React 18 + TypeScript + Vite — port 3000
├── docs/               Architecture and design diagrams (Mermaid)
└── docker-compose.yml  Full-stack Docker Compose
```

Each module has its own README with full details:

- [`account-posting/README.md`](account-posting/README.md) — API reference, package structure, Spring profiles, design
  notes
- [`db/README.md`](db/README.md) — Migration strategy, env-specific folders, promotion workflow
- [`ui/README.md`](ui/README.md) — Pages, components, run commands
- [`docs/README.md`](docs/README.md) — Architecture diagrams index

---

## Prerequisites

| Tool   | Version |
|--------|---------|
| Java   | 21+     |
| Maven  | 3.9+    |
| Node   | 20+     |
| Docker | 24+     |

---

## Option A — Run everything with Docker

```bash
# From the project root
docker compose up --build
```

Services come up in order:

1. `postgres` — starts and passes health check
2. `flyway` — runs all migrations then exits
3. `account-posting` — starts once flyway completes
4. `ui` — starts once the API health check passes

| Service         | URL                       |
|-----------------|---------------------------|
| React UI        | http://localhost:3000     |
| Spring Boot API | http://localhost:8080/api |
| PostgreSQL      | localhost:5432            |

```bash
docker compose down       # stop, keep data volume
docker compose down -v    # stop, delete data volume
```

---

## Option B — Run locally (manual steps)

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

### 2. Run DB migrations

```bash
cd db
mvn flyway:migrate -Pdev   # applies dev/V1–V12
mvn flyway:info            # verify what was applied
```

See [`db/README.md`](db/README.md) for full migration details and environment-specific instructions.

### 3. Start the API

```bash
cd account-posting
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API base: `http://localhost:8080/api`

### 4. Start the UI

```bash
cd ui
npm install     # first time only
npm run dev
```

Open: `http://localhost:3000`

---

## Quick API Reference

Base URL: `http://localhost:8080/api`

| Method | Path                      | Description                   |
|--------|---------------------------|-------------------------------|
| `POST` | `/account-posting`        | Submit a new posting request  |
| `GET`  | `/account-posting`        | Search postings (paginated)   |
| `GET`  | `/account-posting/{id}`   | Get a posting with all legs   |
| `POST` | `/account-posting/retry`  | Retry PENDING/FAILED legs     |
| `GET`  | `/account-posting/config` | List routing config entries   |
| `POST` | `/account-posting/config` | Create a routing config entry |

All requests and responses use **snake_case** JSON. See [`account-posting/README.md`](account-posting/README.md) for the
full API reference.

---

## Architecture Diagrams

All diagrams are in `docs/` in Mermaid syntax — viewable in VS Code (Markdown Preview Mermaid Support), GitHub/GitLab
natively, or at [mermaid.live](https://mermaid.live).

See [`docs/README.md`](docs/README.md) for the full index.
