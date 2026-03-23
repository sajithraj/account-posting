# Account Posting Orchestrator — Deployment Guide

This folder contains Docker Compose files and local batch scripts for every deployment environment.

---

## Prerequisites

### For Docker environments (dev / qa / uat / prod)

| Tool           | Minimum version                        |
|----------------|----------------------------------------|
| Docker         | 24+                                    |
| Docker Compose | v2 (built-in `docker compose` command) |

No JDK or Node.js installation required — all builds happen inside Docker.

### For local batch scripts (Windows)

| Tool   | Minimum version | Notes                                    |
|--------|-----------------|------------------------------------------|
| Java   | 17              | Must be on `PATH` (`java -version`)      |
| Maven  | 3.8+            | Must be on `PATH` (`mvn -version`)       |
| Node   | 18+             | Must be on `PATH` (`node -version`)      |
| npm    | 9+              | Included with Node                       |
| Docker | 24+             | Only needed for the PostgreSQL container |

---

## Repository layout

```
project/
├── account-posting/          Spring Boot API (Java 17, Maven)
│   └── Dockerfile
├── db/
│   └── src/main/resources/db/migration/
│       ├── dev/              V1__baseline_schema.sql
│       ├── qa/               V1__baseline_schema.sql
│       ├── uat/              V1__baseline_schema.sql
│       └── prod/             V1__baseline_schema.sql
├── ui/                       React 18 / TypeScript / Vite
│   └── Dockerfile
└── deploy/                   ← you are here
    ├── docker-compose.dev.yml
    ├── docker-compose.qa.yml
    ├── docker-compose.uat.yml
    ├── docker-compose.prod.yml
    ├── start-db.bat          Local: PostgreSQL container + Flyway migrations
    ├── start-backend.bat     Local: Spring Boot API (dev profile)
    ├── start-frontend.bat    Local: React UI (Vite dev server)
    ├── start-all.bat         Local: all three in one go
    └── README.md
```

---

## One environment at a time

> **All environments share the same host ports: 5432, 8080, and 3000.**
> Only one environment can run at a time on the same machine.
> Always stop the current environment before starting another.

```bash
# Stop current environment before switching
docker compose -f docker-compose.dev.yml down

# Then start the next one
docker compose -f docker-compose.qa.yml up --build -d
```

The database volume is separate per environment (`postgres_data_dev`, `postgres_data_qa`, etc.)
so data is preserved when you switch back.

---

## Environment summary

| Environment | Compose file              | Spring profile | DB volume            | Notes                           |
|-------------|---------------------------|----------------|----------------------|---------------------------------|
| local       | *(batch scripts only)*    | `dev`          | Docker container     | Runs on host, no Docker for app |
| dev         | `docker-compose.dev.yml`  | `dev`          | `postgres_data_dev`  | debug logging enabled           |
| qa          | `docker-compose.qa.yml`   | `qa`           | `postgres_data_qa`   | info logging                    |
| uat         | `docker-compose.uat.yml`  | `uat`          | `postgres_data_uat`  | info logging                    |
| prod        | `docker-compose.prod.yml` | `prod`         | `postgres_data_prod` | warn logging, larger DB pool    |

All environments use the same credentials: **postgres / postgres**

> **Kafka is disabled** in all environments. No Kafka broker is required.

---

## Option A — Local batch scripts (Windows, run on host)

Use these when developing locally. The database runs in Docker; the API and UI run
directly on your machine so you get fast rebuilds and hot reload.

All scripts are in `deploy/` and use relative paths — run them from any directory.

### Run everything at once

```
deploy\start-all.bat
```

This will:

1. Start a PostgreSQL Docker container (`account_posting_db_local`) and wait until healthy
2. Run Flyway migrations against the dev schema
3. Open the Spring Boot API in a new window (`http://localhost:8080/api`)
4. Open the React dev server in a new window (`http://localhost:3000`)

### Run each part individually

Open three separate terminals and run in this order:

**Terminal 1 — Database (run first, wait for it to finish)**

```
deploy\start-db.bat
```

Starts the PostgreSQL container if not already running, then runs Flyway migrations.
Safe to re-run — if the container already exists it will just start it; Flyway skips
already-applied migrations.

**Terminal 2 — Backend**

```
deploy\start-backend.bat
```

Runs `mvn spring-boot:run` with the `dev` profile. Connects to `localhost:5432`.

**Terminal 3 — Frontend**

```
deploy\start-frontend.bat
```

Runs `npm run dev`. Auto-runs `npm install` if `node_modules` is missing.
Vite proxies `/api` → `http://localhost:8080` so no CORS config is needed.

### Stop local services

Close the backend and frontend terminal windows, then stop the database container:

```
docker stop account_posting_db_local
```

To remove the container entirely (wipes data):

```
docker rm account_posting_db_local
```

---

## Option B — Full Docker stack

Builds and runs everything (DB, API, UI) inside Docker. No local Java or Node.js needed.
Run all commands from the `deploy/` directory.

### Dev

```bash
docker compose -f docker-compose.dev.yml up --build -d
```

### QA

```bash
# Stop current environment first if one is running
docker compose -f docker-compose.<current>.yml down

docker compose -f docker-compose.qa.yml up --build -d
```

### UAT

```bash
docker compose -f docker-compose.uat.yml up --build -d
```

### Prod

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

### Switching between environments

```bash
# 1. Stop the running environment
docker compose -f docker-compose.dev.yml down

# 2. Start the next one
docker compose -f docker-compose.qa.yml up --build -d
```

> Use `down -v` only if you want to wipe that environment's database.
> Plain `down` keeps the volume so data is still there when you come back.

---

## Step-by-step: what happens on `up --build`

| Step | Service           | What it does                                                         |
|------|-------------------|----------------------------------------------------------------------|
| 1    | `postgres`        | Starts PostgreSQL, waits until `pg_isready` passes                   |
| 2    | `flyway`          | Mounts env-specific migration folder, applies V1 baseline, exits     |
| 3    | `account-posting` | Builds JAR (Maven inside Docker), starts API, waits for health check |
| 4    | `ui`              | Builds React app (Vite inside Docker), serves via nginx              |

---

## Service URLs (once all services are up)

| Service         | URL                                                                        |
|-----------------|----------------------------------------------------------------------------|
| React UI        | http://localhost:3000                                                      |
| Spring Boot API | http://localhost:8080/api                                                  |
| Health check    | http://localhost:8080/api/actuator/health                                  |
| PostgreSQL      | localhost:5432 · db: `account_posting_db` · user/pass: `postgres/postgres` |

---

## Useful commands

```bash
# View running containers
docker compose -f docker-compose.dev.yml ps

# Follow logs for all services
docker compose -f docker-compose.dev.yml logs -f

# Follow logs for one service
docker compose -f docker-compose.dev.yml logs -f account-posting

# Stop (keeps data)
docker compose -f docker-compose.dev.yml down

# Stop and wipe database volume
docker compose -f docker-compose.dev.yml down -v

# Rebuild one service after a code change
docker compose -f docker-compose.dev.yml up --build account-posting
docker compose -f docker-compose.dev.yml up --build ui
```

---

## Rebuilding after code changes

| What changed           | Docker command                                                          | Local (batch)                  |
|------------------------|-------------------------------------------------------------------------|--------------------------------|
| Java source            | `docker compose -f docker-compose.<env>.yml up --build account-posting` | Restart `start-backend.bat`    |
| React / TypeScript     | `docker compose -f docker-compose.<env>.yml up --build ui`              | Vite hot-reloads automatically |
| New DB migration added | `docker compose -f docker-compose.<env>.yml up flyway`                  | Re-run `start-db.bat`          |
| Everything             | `docker compose -f docker-compose.<env>.yml up --build`                 | Restart all batch scripts      |

---

## Adding a new DB migration

1. Create a new versioned script in the appropriate folder:
   ```
   db/src/main/resources/db/migration/<env>/V2__your_description.sql
   ```
2. Apply without restarting the full stack:
   ```bash
   docker compose -f docker-compose.<env>.yml up flyway
   ```

> Never edit a migration script that has already been applied to a live database.
> Always add a new versioned script.

---

## Troubleshooting

### Flyway checksum mismatch

A previously applied migration file was edited. Run repair:

```bash
docker compose -f docker-compose.<env>.yml run --rm flyway \
  -url=jdbc:postgresql://postgres:5432/account_posting_db \
  -user=postgres -password=postgres repair
```

### API fails — "Connection to localhost:5432 refused" (inside Docker)

The profile YAML has `localhost` hardcoded. The compose files override this with
`SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/...`. Make sure you are using
the compose files in this `deploy/` folder, not a custom one.

### API fails — "password authentication failed"

`SPRING_DATASOURCE_PASSWORD` in the compose file must match `POSTGRES_PASSWORD`.
Both are `postgres` in all provided files.

### Port already in use (5432, 8080, or 3000)

Another environment is running. Stop it first:

```bash
docker compose -f docker-compose.<env>.yml down
```

Or stop the local batch scripts and the `account_posting_db_local` container.

### API fails — schema validation error

Flyway did not complete successfully. Check its logs:

```bash
docker compose -f docker-compose.<env>.yml logs flyway
```

### Full clean reset

```bash
docker compose -f docker-compose.<env>.yml down -v
docker compose -f docker-compose.<env>.yml up --build
```
