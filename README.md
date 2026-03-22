# Account Posting Orchestrator

Accepts account posting requests from upstream systems, delivers to a core banking orchestrator via a strategy-per-target-system pattern, tracks every response leg, and provides a dashboard UI with retry capability.

---

## Project Layout

```
project/
├── account-posting/    Spring Boot 3.5, Java 21 — port 8080
├── db/                 Flyway migration scripts (PostgreSQL 16)
├── ui/                 React 18 + TypeScript + Vite — port 3000
└── docker-compose.yml  Full-stack Docker Compose (postgres + flyway + api + ui)
```

---

## Prerequisites

| Tool   | Version |
|--------|---------|
| Java   | 21+     |
| Maven  | 3.9+    |
| Node   | 20+     |
| Docker | 24+     |

> **PowerShell note:** use `mvnw.cmd` instead of `./mvnw`, or prefix with `& ./mvnw ...`

---

## Option A — Run everything with Docker

```bash
# From the project root — builds images, runs migrations, starts all services
docker compose up --build

# Services:
#   http://localhost:3000  — React UI
#   http://localhost:8080  — Spring Boot API
#   localhost:5433         — PostgreSQL (direct access)

# Stop
docker compose down          # keep data volume
docker compose down -v       # also delete data volume
```

The startup order is guaranteed:
1. `postgres` starts and passes health check
2. `flyway` runs all migrations then exits
3. `account-posting` starts once flyway completes
4. `ui` starts once the API health check passes

---

## Option B — Run locally (manual steps)

### Step 1 — Start PostgreSQL

```bash
docker compose up -d postgres
```

Wait until healthy (usually < 10 s).

### Step 2 — Run DB migrations

```bash
cd db
mvn flyway:migrate          # default profile → account_posting_dev on port 5433
mvn flyway:info             # verify migration status
```

Migrations applied (`db/src/main/resources/db/migration/`):

| Version | Description |
|---------|-------------|
| V1 | init_schema — account_posting + account_posting_leg tables |
| V2 | posting_config_table + seed data |
| V3 | fix_currency_column_type |
| V4 | inline_payload_columns |
| V5 | posting_config_unique_order constraint |
| V6 | leg_table_cleanup |
| V7 | move_retry_lock_to_posting |
| V8 | add_leg_mode |
| V9 | add_target_systems_to_posting |
| V10 | add_operation_to_leg |

### Step 3 — Start the Spring Boot API

```bash
cd account-posting
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

API base URL: `http://localhost:8080/api`

### Step 4 — Start the UI

```bash
cd ui
npm install      # first time only
npm run dev
```

Open: `http://localhost:3000`

---

## Environment Variables (`account-posting`)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `account_posting_db` | Database name |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `postgres` | DB password |
| `SERVER_PORT` | `8080` | HTTP listen port |
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka (disabled by default) |

---

## API Reference

Base URL: `http://localhost:8080/api`

### Account Posting

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/account-posting` | 201 | Submit a new posting request |
| `GET` | `/account-posting` | 200 | Search with filters (paginated) |
| `GET` | `/account-posting/{postingId}` | 200 | Get a posting with all its legs |
| `POST` | `/account-posting/retry` | 200 | Retry PENDING/FAILED legs |
| `GET` | `/account-posting/target-systems` | 200 | List distinct target systems |

**POST /account-posting — Request body:**
```json
{
  "sourceReferenceId": "SRC-1001",
  "endToEndReferenceId": "E2E-1001",
  "sourceName": "DMS",
  "requestType": "CBS_GL",
  "amount": 1500.00,
  "currency": "USD",
  "creditDebitIndicator": "CRDT",
  "debtorAccount": "13246589",
  "creditorAccount": "98753123",
  "requestedExecutionDate": "2026-03-21",
  "remittanceInformation": "salary payment"
}
```

**GET /account-posting — Query parameters:**

| Param | Type | Description |
|-------|------|-------------|
| `status` | `PENDING\|SUCCESS\|FAILED` | Filter by posting status |
| `sourceReferenceId` | string | Exact match |
| `endToEndReferenceId` | string | Exact match |
| `requestType` | string | Exact match |
| `targetSystem` | string | Filter by target system (CBS/GL/OBPM) |
| `fromDate` | `yyyy-MM-dd` | Execution date range start |
| `toDate` | `yyyy-MM-dd` | Execution date range end |
| `page` | int | 0-based page number (default 0) |
| `size` | int | Page size (default 20) |

### Account Posting Legs

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/account-posting/{postingId}/leg` | 201 | Add a leg |
| `GET` | `/account-posting/{postingId}/leg` | 200 | List all legs for a posting |
| `GET` | `/account-posting/{postingId}/leg/{legId}` | 200 | Get a specific leg |
| `PUT` | `/account-posting/{postingId}/leg/{legId}` | 200 | Full update (status, ref, payload) |
| `PATCH` | `/account-posting/{postingId}/leg/{legId}` | 200 | Manual status override (sets mode=MANUAL) |

### Posting Config

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `GET` | `/account-posting/config` | 200 | List all config entries |
| `GET` | `/account-posting/config/{requestType}` | 200 | Get ordered targets for a request type |
| `POST` | `/account-posting/config` | 201 | Create a new config entry |
| `PUT` | `/account-posting/config/{configId}` | 200 | Update a config entry |
| `DELETE` | `/account-posting/config/{configId}` | 204 | Delete a config entry |
| `POST` | `/account-posting/config/cache/flush` | 204 | Flush the config cache |

### Error responses

All errors follow this structure:
```json
{
  "id": "uuid",
  "name": "ERROR_CODE",
  "message": "Human-readable description",
  "errors": [{ "field": "amount", "message": "amount must be greater than zero" }]
}
```

| HTTP Status | `name` | Cause |
|-------------|--------|-------|
| 400 | `VALIDATION_FAILED` | Bean validation failure |
| 404 | `NOT_FOUND` | Resource does not exist |
| 422 | `DUPLICATE_E2E_REF` | Duplicate endToEndReferenceId |
| 422 | `NO_CONFIG_FOUND` | No posting_config for requestType |
| 422 | `DUPLICATE_CONFIG_ORDER` | DB unique constraint on config order |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

## DB Schema

### Tables

| Table | Purpose |
|-------|---------|
| `account_posting` | One row per posting request; inline request/response JSON columns; `retry_locked_until` for parallel retry lock |
| `account_posting_leg` | One row per target-system leg; `@Version` optimistic lock; `attempt_number` increments on retry |
| `posting_config` | Routes `request_type` → ordered list of target systems |

### posting_config seed data

| source_name | request_type | target_system | operation | order_seq |
|-------------|-------------|---------------|-----------|-----------|
| DMS | CBS_GL | CBS | POSTING | 1 |
| DMS | CBS_GL | GL | POSTING | 2 |
| DMS | OBPM | OBPM | POSTING | 1 |
| RMS | EFD_RETURN | CBS | POSTING | 1 |
| RMS | EFD_RETURN | GL | POSTING | 2 |
| RMS | USDNT_GL_RETURN | GL | POSTING | 1 |
| USDNT | GL_RETURN | GL | POSTING | 1 |
| LCD | USDNT_GL | OBPM | POSTING | 1 |
| LCD | USDNT_GL | CBS | POSTING | 2 |
| NPSS | NPSS_PAYMENT | CBS | POSTING | 1 |
| NPSS | NPSS_PAYMENT | OBPM | POSTING | 2 |
| DBA | DBA_ACCOUNT_HOLD | CBS | POSTING | 1 |
| STABLECOIN | BNK_CUSTOMER | OBPM | POSTING | 1 |
| STABLECOIN | BNK_CUSTOMER | GL | POSTING | 2 |
| STABLECOIN | BNK_CANCEL_HOLD | CBS | POSTING | 1 |

---

## Strategy Pattern — Posting Services

The `posting_config` table drives which strategy handles each leg, resolved by `PostingStrategyFactory`:

| target_system | Service class | `getPostingFlow()` |
|--------------|--------------|-------------------|
| `CBS` | `CBSPostingService` | `CBS_POSTING` |
| `GL` | `GLPostingService` | `GL_POSTING` |
| `OBPM` | `OBPMPostingService` | `OBPM_POSTING` |

Each strategy: inserts a PENDING leg → calls the external system (stub) → updates leg to SUCCESS/FAILED.

---

## Key Design Notes

- **Retry lock** — `retry_locked_until TIMESTAMPTZ` on `account_posting` + single atomic `@Modifying` JPQL prevents concurrent retries. `@Version` adds optimistic locking at JPA layer.
- **Leg decoupling** — `account_posting_leg.posting_id` is a plain `Long`, not a `@ManyToOne`. The `leg` package never imports from `posting`.
- **No HTTP between posting and leg** — both are in-process; `AccountPostingServiceImpl` injects `AccountPostingLegService` directly.
- **MDC logging** — `traceId`, `postingId`, `e2eRef`, `requestType` injected via `MdcLoggingFilter` for every request.
- **Kafka** — disabled by default (`app.kafka.enabled=false`). Enable by setting to `true` once Kafka infrastructure is ready.

---

## DB Migration Promotion Strategy

### The core principle

There is **one migration folder** (`db/src/main/resources/db/migration/`).
The **same scripts** are applied to every environment — scripts never change after they are applied anywhere.
Flyway tracks what has already been applied per database in its `flyway_schema_history` table, so running `migrate` on any environment is always safe and idempotent.

```
Repo (main):   V1  V2  V3  V4  V5  V6  V7  V8  V9  V10
               ─────────────────────────────────────────
Dev DB:         ✓   ✓   ✓   ✓   ✓   ✓   ✓   ✓   ✓   ✓
QA DB:          ✓   ✓   ✓   ✓   ✓   ✓
UAT DB:         ✓   ✓   ✓   ✓   ✓
Prod DB:        ✓   ✓   ✓
                              ↑
                   want to promote only to here
```

---

### Option A — `flyway.target` (partial promotion, simplest)

Use this when you want to promote up to a specific version without applying everything in the repo.

```bash
# Prod is at V3. Promote only V4 and V5 — stop before V6.
cd db
mvn flyway:migrate -Pprod \
  -Dflyway.target=5 \
  -Dflyway.url=jdbc:postgresql://prod-host:5432/account_posting_prod \
  -Dflyway.password=${PROD_DB_PASSWORD}
```

Flyway reads all scripts in the folder but applies only up to V5. V6 onwards are ignored until the next promotion.

**Later, promote V6:**
```bash
mvn flyway:migrate -Pprod -Dflyway.target=6 ...
```

**Promote everything pending (no target = apply all):**
```bash
mvn flyway:migrate -Pprod ...
```

#### GitLab CI pipeline — passing `flyway.target`

```yaml
# .gitlab-ci.yml

variables:
  FLYWAY_TARGET: "5"      # set per pipeline run or environment variable in GitLab UI

migrate-prod:
  stage: deploy
  script:
    - cd db
    - mvn flyway:migrate -Pprod
        -Dflyway.target=${FLYWAY_TARGET}
        -Dflyway.url=${PROD_DB_URL}
        -Dflyway.user=${PROD_DB_USER}
        -Dflyway.password=${PROD_DB_PASSWORD}
  environment:
    name: production
  when: manual       # requires a human to click Run in GitLab
  only:
    - tags            # only runs on tagged commits
```

Set `FLYWAY_TARGET` as a GitLab CI/CD variable (Settings → CI/CD → Variables) so it can be changed per release without touching the pipeline file.

---

### Option B — Git release tags (full release promotion, standard)

This is the industry-standard pattern. A Git tag pins the repo to the exact set of migrations that should be deployed to an environment. The migration folder at that tag only contains the scripts that existed at that point in time — so Flyway can never accidentally apply scripts beyond the release.

#### Tag strategy

```
develop branch  →  active development (V1 .. V10+)
                        │
               ┌────────┴────────┐
           release/1.0        release/1.1
           (V1 - V3)          (V1 - V5)      ← tag these when stable
               │                   │
           deployed to         deployed to
           UAT / Prod          UAT / Prod
           in sprint 1         in sprint 2
```

#### Creating a release tag

```bash
# On main/develop, once V5 is stable and signed off for production:
git tag -a release/v1.1 -m "Release 1.1 — migrations V1-V5"
git push origin release/v1.1
```

#### GitLab CI pipeline — tag-based promotion

```yaml
# .gitlab-ci.yml

stages:
  - migrate
  - deploy

# Runs only on release/* tags
migrate-qa:
  stage: migrate
  script:
    - cd db
    - mvn flyway:migrate -Pqa
        -Dflyway.url=${QA_DB_URL}
        -Dflyway.password=${QA_DB_PASSWORD}
  only:
    - /^release\/.*/    # triggers on any release/* tag

migrate-uat:
  stage: migrate
  script:
    - cd db
    - mvn flyway:migrate -Puat
        -Dflyway.url=${UAT_DB_URL}
        -Dflyway.password=${UAT_DB_PASSWORD}
  when: manual
  only:
    - /^release\/.*/

migrate-prod:
  stage: migrate
  script:
    - cd db
    - mvn flyway:migrate -Pprod
        -Dflyway.url=${PROD_DB_URL}
        -Dflyway.password=${PROD_DB_PASSWORD}
  when: manual          # human approval required
  only:
    - /^release\/.*/
  environment:
    name: production
```

**How promotion works:**

```
Sprint 1 — tag release/v1.0 at commit that has V1-V3
  → GitLab triggers pipeline on the tag
  → migrate-qa runs automatically   (QA gets V1-V3)
  → migrate-uat triggered manually  (UAT gets V1-V3)
  → migrate-prod triggered manually (Prod gets V1-V3)
  → At each step, Flyway only sees V1-V3 because that is what
    exists in the repo at tag release/v1.0

Sprint 2 — tag release/v1.1 at commit that has V1-V5
  → Same pipeline runs on the new tag
  → Flyway checks schema_history: V1-V3 already applied
  → Only V4 and V5 are new — applies those, stops
```

The critical point: **you never need to tell Flyway what to skip.** Because the pipeline checks out the tagged commit, the folder only contains the scripts that were written up to that point.

---

### Comparing the two options

| | Option A (`flyway.target`) | Option B (Git tags) |
|--|---------------------------|---------------------|
| **How you control what gets applied** | Pass a version number at runtime | Checkout a specific tag |
| **Risk of mistake** | Someone passes wrong target number | Low — the tag is immutable |
| **GitLab setup** | Add `FLYWAY_TARGET` as a CI variable | Tag the commit, pipeline auto-triggers |
| **Best for** | Ad-hoc / hotfix promotions | Planned sprint releases |
| **Can combine both?** | Yes — tag a commit AND pass target for extra safety | Yes |

**Recommended:** use Option B (tags) as the normal release process. Use Option A (`flyway.target`) as a safety valve when you need to promote a subset of an already-tagged release.

---

### Golden rules

```
1. Never edit a migration script once it has been applied to any database.
   Flyway validates checksums — a changed file will throw a validation error.
   Always fix forward: write V11 to undo or change what V10 did.

2. A script's content is identical in every environment.
   V5 in dev is byte-for-byte the same as V5 in prod.

3. Before applying to prod, always run flyway:info first.
   It shows exactly which versions are pending — no surprises.

4. Store DB credentials in GitLab CI/CD Variables (masked + protected),
   never in pom.xml or committed files.

5. The migration pipeline should run BEFORE the application deployment.
   New code expects the new schema — deploy schema first, app second.
```

---

### Verifying before you promote

```bash
# See exactly what would be applied — dry run equivalent
cd db
mvn flyway:info -Pprod \
  -Dflyway.url=${PROD_DB_URL} \
  -Dflyway.password=${PROD_DB_PASSWORD}

# Output shows:
# Version | Description           | State
# V1      | init_schema           | Success   ← already applied
# V2      | posting_config_table  | Success
# V3      | fix_currency          | Success
# V4      | inline_payload_cols   | Pending   ← will be applied
# V5      | posting_config_order  | Pending   ← will be applied
# V6      | leg_table_cleanup     | Ignored   ← only if using --target=5
```

---

## Build Commands

```bash
# Backend
cd account-posting
mvn clean package -DskipTests
mvn test
mvn test -Dtest=AccountPostingControllerTest

# Frontend
cd ui
npm run build
npm run preview
```

---

## Architecture Diagrams

All diagrams are in `docs/` using Mermaid syntax.

**Viewing options:**
- VS Code: install "Markdown Preview Mermaid Support" extension → open `.md` → preview
- Online: paste diagram blocks at [mermaid.live](https://mermaid.live)
- GitHub/GitLab: renders Mermaid natively in `.md` files

| File | Contents |
|------|---------|
| `docs/01-system-architecture.md` | C4 Context + Container |
| `docs/02-database-design.md` | ERD |
| `docs/03-sequence-create-posting.md` | CREATE flow |
| `docs/04-sequence-retry.md` | RETRY flow |
| `docs/05-sequence-search.md` | Search flow |
| `docs/06-state-machine.md` | Status state machines |
| `docs/07-component-diagram.md` | Spring Boot component graph |
| `docs/08-class-diagram.md` | Strategy + entity class diagram |
| `docs/09-deployment-diagram.md` | Docker topology |
| `docs/10-data-flow-diagram.md` | Data transformation pipeline |
| `docs/11-error-handling-flow.md` | Exception hierarchy |
