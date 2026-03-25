# Environment Deployment Guide

Step-by-step instructions for running the Account Posting service in each environment.

---

## Overview

| Profile | Database              | Schema setup                   | Migration folder               | Kafka    | Deploy method  |
|---------|-----------------------|--------------------------------|--------------------------------|----------|----------------|
| `local` | H2 in-memory          | Hibernate auto (`create-drop`) | —                              | Disabled | Host (Maven)   |
| `dev`   | PostgreSQL via Docker | Flyway — single V1 baseline    | `dev/V1__baseline_schema.sql`  | Disabled | Docker Compose |
| `qa`    | PostgreSQL via Docker | Flyway — single V1 baseline    | `qa/V1__baseline_schema.sql`   | Disabled | Docker Compose |
| `uat`   | PostgreSQL via Docker | Flyway — single V1 baseline    | `uat/V1__baseline_schema.sql`  | Disabled | Docker Compose |
| `prod`  | PostgreSQL via Docker | Flyway — single V1 baseline    | `prod/V1__baseline_schema.sql` | Disabled | Docker Compose |

> **Kafka is permanently disabled** in all environments (`app.kafka.enabled=false`).
> No Kafka broker is needed. Only event publishing is skipped — all other features work normally.

> **ddl-auto: validate** on dev/qa/uat/prod means Flyway **must run before** the app starts.
> The Docker Compose files handle this ordering automatically.

---

## Profile Differences

| Setting         | `local`       | `dev`             | `qa` / `uat`      | `prod`            |
|-----------------|---------------|-------------------|-------------------|-------------------|
| `ddl-auto`      | `create-drop` | `validate`        | `validate`        | `validate`        |
| `show-sql`      | `true`        | `false`           | `false`           | `false`           |
| Health details  | `always`      | `when_authorized` | `when_authorized` | `when_authorized` |
| Hikari max pool | default       | 10                | 10                | 20                |
| App log level   | `DEBUG`       | `DEBUG`           | `INFO`            | `INFO`            |
| Root log level  | `INFO`        | `INFO`            | `INFO`            | `WARN`            |
| Kafka           | Disabled      | Disabled          | Disabled          | Disabled          |

---

## ENV 1 — Local (H2, no Docker)

No Docker required. Hibernate creates the schema on startup and drops it on shutdown.
Seed data is loaded automatically from `backend/src/main/resources/data-local.sql`.

### Prerequisites

- Java 17
- Maven 3.8+

### Start

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Verify

```bash
curl http://localhost:8080/api/actuator/health
# Expected: {"status":"UP"}
```

### H2 Console

Open in browser: **http://localhost:8080/api/h2-console**

> The context path `/api` is required — `http://localhost:8080/h2-console` returns 404.

| Field    | Value                            |
|----------|----------------------------------|
| JDBC URL | `jdbc:h2:mem:account_posting_db` |
| Username | `sa`                             |
| Password | *(leave empty)*                  |

---

## ENV 2 — Dev (full Docker stack)

Runs PostgreSQL, Flyway, Spring Boot API, and React UI all inside Docker.
No local Java or Node.js installation needed.

### Prerequisites

- Docker 24+
- Docker Compose v2

### Start

```bash
cd deploy
docker compose -f docker-compose.dev.yml up --build
```

### What happens (in order)

1. **PostgreSQL** starts and waits until healthy (`pg_isready`)
2. **Flyway** mounts `db/migration/dev/` and applies `V1__baseline_schema.sql`, then exits
3. **Spring Boot API** builds from source and starts on port 8080 (profile: `dev`)
4. **React UI** builds with Vite and serves via nginx on port 3000, proxying `/api` to the API

### Service URLs

| Service         | URL                                                                        |
|-----------------|----------------------------------------------------------------------------|
| React UI        | http://localhost:3000                                                      |
| Spring Boot API | http://localhost:8080/api                                                  |
| Health check    | http://localhost:8080/api/actuator/health                                  |
| PostgreSQL      | localhost:5432 · db: `account_posting_db` · user/pass: `postgres/postgres` |

### Run in background

```bash
docker compose -f docker-compose.dev.yml up --build -d

# Follow logs
docker compose -f docker-compose.dev.yml logs -f
```

### Stop

```bash
# Stop containers (keeps data)
docker compose -f docker-compose.dev.yml down

# Full reset — wipes the database volume
docker compose -f docker-compose.dev.yml down -v
```

---

## ENV 3 — QA (full Docker stack)

```bash
cd deploy
docker compose -f docker-compose.qa.yml up --build
```

Migration folder used: `db/migration/qa/V1__baseline_schema.sql`
Spring profile: `qa` · Logging: INFO

### Service URLs

| Service         | URL                                                                        |
|-----------------|----------------------------------------------------------------------------|
| React UI        | http://localhost:3000                                                      |
| Spring Boot API | http://localhost:8080/api                                                  |
| Health check    | http://localhost:8080/api/actuator/health                                  |
| PostgreSQL      | localhost:5432 · db: `account_posting_db` · user/pass: `postgres/postgres` |

### Stop / reset

```bash
docker compose -f docker-compose.qa.yml down        # keep data
docker compose -f docker-compose.qa.yml down -v     # wipe data
```

---

## ENV 4 — UAT (full Docker stack)

```bash
cd deploy
docker compose -f docker-compose.uat.yml up --build
```

Migration folder used: `db/migration/uat/V1__baseline_schema.sql`
Spring profile: `uat` · Logging: INFO

### Service URLs

| Service         | URL                                                                        |
|-----------------|----------------------------------------------------------------------------|
| React UI        | http://localhost:3000                                                      |
| Spring Boot API | http://localhost:8080/api                                                  |
| Health check    | http://localhost:8080/api/actuator/health                                  |
| PostgreSQL      | localhost:5432 · db: `account_posting_db` · user/pass: `postgres/postgres` |

### Stop / reset

```bash
docker compose -f docker-compose.uat.yml down
docker compose -f docker-compose.uat.yml down -v
```

---

## ENV 5 — Prod (full Docker stack)

```bash
cd deploy
docker compose -f docker-compose.prod.yml up --build
```

Migration folder used: `db/migration/prod/V1__baseline_schema.sql`
Spring profile: `prod` · Logging: WARN · DB pool: 20 max connections

### Service URLs

| Service         | URL                                                                        |
|-----------------|----------------------------------------------------------------------------|
| React UI        | http://localhost:3000                                                      |
| Spring Boot API | http://localhost:8080/api                                                  |
| Health check    | http://localhost:8080/api/actuator/health                                  |
| PostgreSQL      | localhost:5432 · db: `account_posting_db` · user/pass: `postgres/postgres` |

### Stop / reset

```bash
docker compose -f docker-compose.prod.yml down
docker compose -f docker-compose.prod.yml down -v
```

---

## Rebuilding after code changes

Run from the `deploy/` directory. Replace `<env>` with `dev`, `qa`, `uat`, or `prod`.

| What changed           | Command                                                         |
|------------------------|-----------------------------------------------------------------|
| Java source            | `docker compose -f docker-compose.<env>.yml up --build backend` |
| React / TypeScript     | `docker compose -f docker-compose.<env>.yml up --build ui`      |
| New DB migration added | `docker compose -f docker-compose.<env>.yml up flyway`          |
| Everything             | `docker compose -f docker-compose.<env>.yml up --build`         |

---

## Adding a new DB migration

1. Create a new versioned script in the appropriate folder:
   ```
   db/src/main/resources/db/migration/<env>/V2__your_description.sql
   ```
2. Apply without restarting the full stack:
   ```bash
   cd deploy
   docker compose -f docker-compose.<env>.yml up flyway
   ```

> Never edit a migration script that has already been applied to a live database.
> Always add a new versioned script.

---

## Flyway utility commands (via Maven, from `db/` directory)

```bash
cd db

# Check migration status
mvn flyway:info -P<env>

# Validate applied scripts match migration files
mvn flyway:validate -P<env>

# Repair checksum mismatches (use with caution)
mvn flyway:repair -P<env>
```

---

## Troubleshooting

### Flyway exits with checksum mismatch

An already-applied migration file was edited after it ran. Run repair:

```bash
cd deploy
docker compose -f docker-compose.<env>.yml run --rm flyway \
  -url=jdbc:postgresql://postgres:5432/account_posting_db \
  -user=postgres -password=postgres repair
```

### API fails — "password authentication failed"

`DB_PASSWORD` in the compose file must match `POSTGRES_PASSWORD`. Both are `postgres` in all provided files.

### Port already in use (5432, 8080, or 3000)

Stop the conflicting service, or change the host-side port in the compose file (left side of `"<host>:<container>"`).

### API fails — schema validation error

Flyway did not run successfully before the API started. Check flyway logs:

```bash
docker compose -f docker-compose.<env>.yml logs flyway
```

### View logs for any service

```bash
docker compose -f docker-compose.<env>.yml logs -f <service>
# services: postgres  flyway  backend  ui
```

### Full clean reset

```bash
docker compose -f docker-compose.<env>.yml down -v
docker compose -f docker-compose.<env>.yml up --build
```
