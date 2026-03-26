# db — Flyway Migrations

Flyway 11 · PostgreSQL · Oracle · Maven

This module owns all database schema management.
The Spring Boot application has **no Flyway dependency** — it connects to an already-migrated database.

---

## Prerequisites

| Tool  | Version |
|-------|---------|
| Java  | 17      |
| Maven | 3.9+    |
| PostgreSQL or Oracle | running and accessible |

---

## Folder Structure

Each environment has its own independent migration folder:

```
db/src/main/resources/db/migration/
├── dev/     V1__baseline_schema.sql   (PostgreSQL — Docker dev)
├── docker/  V1__baseline_schema.sql   (PostgreSQL — local Docker Compose stack)
├── qa/      V1__baseline_schema.sql   (PostgreSQL)
├── uat/     V1__baseline_schema.sql   (PostgreSQL)
├── prod/    V1__baseline_schema.sql   (PostgreSQL)
└── oracle/  V1__baseline_schema.sql   (Oracle — syntax variant)
```

All environments currently have a **single consolidated `V1__baseline_schema.sql`** containing:
- Full schema (tables, indexes, constraints, history tables)
- Canonical `posting_config` seed data

Future changes are added as `V2`, `V3`, etc. per environment as needed.

---

## Run Commands

```bash
cd db

# Apply pending migrations
mvn flyway:migrate -Pdev
mvn flyway:migrate -Pdocker
mvn flyway:migrate -Pqa
mvn flyway:migrate -Puat
mvn flyway:migrate -Pprod
mvn flyway:migrate -Poracle

# Check what has been applied and what is pending
mvn flyway:info -Pdev

# Validate — verifies applied scripts match files (checksum check)
mvn flyway:validate -Pdev

# Fix checksum mismatches (only if a script was legitimately repaired)
mvn flyway:repair -Pdev

# Override credentials at runtime (recommended for prod — never commit real credentials)
mvn flyway:migrate -Pprod \
  -Dflyway.url=jdbc:postgresql://prod-host:5432/account_posting_db \
  -Dflyway.user=prod_user \
  -Dflyway.password=prod_secret
```

---

## Database Schema

### Tables

#### `account_posting`
Main posting record. One row per posting request.

| Column                    | Type            | Notes                                              |
|---------------------------|-----------------|----------------------------------------------------|
| `posting_id`              | BIGSERIAL PK    | Auto-generated                                     |
| `source_reference_id`     | VARCHAR(100)    | Source system's own reference                      |
| `end_to_end_reference_id` | VARCHAR(100) UQ | Idempotency key — must be unique                   |
| `source_name`             | VARCHAR(100)    | IMX / RMS / STABLECOIN                             |
| `request_type`            | VARCHAR(50)     | IMX_CBS_GL / IMX_OBPM / etc.                       |
| `amount`                  | NUMERIC(19,4)   |                                                    |
| `currency`                | VARCHAR(3)      | ISO 4217 (e.g. USD)                                |
| `credit_debit_indicator`  | VARCHAR(6)      | CREDIT / DEBIT                                     |
| `debtor_account`          | VARCHAR(50)     |                                                    |
| `creditor_account`        | VARCHAR(50)     |                                                    |
| `requested_execution_date`| DATE            |                                                    |
| `remittance_information`  | VARCHAR(500)    | Optional free-text                                 |
| `status`                  | VARCHAR(10)     | PNDG / ACSP / RJCT                                 |
| `request_payload`         | JSONB           | Serialised original request                        |
| `response_payload`        | JSONB           | Serialised final response                          |
| `retry_locked_until`      | TIMESTAMPTZ     | Non-null = posting is locked for retry             |
| `target_systems`          | VARCHAR(500)    | Underscore-joined list: CBS_GL / OBPM / etc.       |
| `reason`                  | VARCHAR(1000)   | Final outcome message                              |
| `created_at`              | TIMESTAMPTZ     | Set on insert (JPA auditing)                       |
| `updated_at`              | TIMESTAMPTZ     | Set on update (JPA auditing)                       |

#### `account_posting_leg`
One row per target system call within a posting.

| Column           | Type         | Notes                                           |
|------------------|--------------|-------------------------------------------------|
| `posting_leg_id` | BIGSERIAL PK |                                                 |
| `posting_id`     | BIGINT FK    | References `account_posting.posting_id`         |
| `leg_order`      | INT          | Execution sequence (1-based, from posting_config)|
| `target_system`  | VARCHAR(100) | CBS / GL / OBPM                                 |
| `account`        | VARCHAR(50)  | Account used for this leg                       |
| `status`         | VARCHAR(10)  | PENDING / SUCCESS / FAILED                      |
| `reference_id`   | VARCHAR(100) | Reference returned by target system             |
| `reason`         | VARCHAR(500) | Failure reason or success note                  |
| `attempt_number` | INT          | 1 = first attempt, increments on retry          |
| `posted_time`    | TIMESTAMPTZ  | Timestamp returned by target system             |
| `request_payload`| JSONB        | Outbound payload sent to target system          |
| `response_payload`| JSONB       | Raw response from target system                 |
| `mode`           | VARCHAR(10)  | NORM / RETRY / MANUAL                           |
| `operation`      | VARCHAR(20)  | POSTING / ADD_HOLD / REMOVE_HOLD                |
| `created_at`     | TIMESTAMPTZ  |                                                 |
| `updated_at`     | TIMESTAMPTZ  |                                                 |

#### `posting_config`
Routing table — maps `request_type` to ordered target systems.

| Column         | Type         | Notes                                                     |
|----------------|--------------|-----------------------------------------------------------|
| `config_id`    | BIGSERIAL PK |                                                           |
| `source_name`  | VARCHAR(100) | IMX / RMS / STABLECOIN                                    |
| `request_type` | VARCHAR(100) | e.g. IMX_CBS_GL                                           |
| `target_system`| VARCHAR(100) | CBS / GL / OBPM                                           |
| `operation`    | VARCHAR(100) | POSTING / ADD_HOLD / REMOVE_HOLD                          |
| `order_seq`    | INT          | Execution order (1-based)                                 |
| `created_at`   | TIMESTAMPTZ  |                                                           |
| `updated_at`   | TIMESTAMPTZ  |                                                           |

Unique constraint: `(request_type, order_seq)` — one target per execution slot per request type.

#### `account_posting_history` / `account_posting_leg_history`
Mirror of the active tables. Records are moved here by the archival job (default: 90 days).
`GET /v2/payment/account-posting/{postingId}` falls back to history automatically.

### Indexes

| Table                 | Index                          | Columns                      |
|-----------------------|--------------------------------|------------------------------|
| account_posting        | idx_ap_status                  | status                       |
| account_posting        | idx_ap_e2e_ref                 | end_to_end_reference_id      |
| account_posting        | idx_ap_src_ref                 | source_reference_id          |
| account_posting        | idx_ap_requested_date          | requested_execution_date     |
| account_posting        | idx_ap_request_type            | request_type                 |
| account_posting_leg    | idx_apl_posting_id             | posting_id                   |
| account_posting_leg    | idx_apl_status                 | status                       |
| posting_config         | idx_pc_request_type            | request_type                 |
| posting_config         | idx_pc_source_name             | source_name                  |

---

## Canonical Seed Data (`posting_config`)

These rows are inserted by `V1__baseline_schema.sql` in every environment:

| source_name  | request_type          | target_system | operation    | order_seq |
|--------------|-----------------------|---------------|--------------|-----------|
| IMX          | IMX_CBS_GL            | CBS           | POSTING      | 1         |
| IMX          | IMX_CBS_GL            | GL            | POSTING      | 2         |
| IMX          | IMX_OBPM              | OBPM          | POSTING      | 1         |
| RMS          | FED_RETURN            | CBS           | POSTING      | 1         |
| RMS          | FED_RETURN            | GL            | POSTING      | 2         |
| RMS          | GL_RETURN             | GL            | POSTING      | 1         |
| RMS          | GL_RETURN             | GL            | POSTING      | 2         |
| RMS          | MCA_RETURN            | OBPM          | POSTING      | 1         |
| STABLECOIN   | BUY_CUSTOMER_POSTING  | CBS           | POSTING      | 2         |
| STABLECOIN   | BUY_CUSTOMER_POSTING  | GL            | POSTING      | 3         |
| STABLECOIN   | ADD_ACCOUNT_HOLD      | CBS           | ADD_HOLD     | 1         |
| STABLECOIN   | BUY_CUSTOMER_POSTING  | CBS           | REMOVE_HOLD  | 1         |
| STABLECOIN   | CUSTOMER_POSTING      | CBS           | POSTING      | 1         |
| STABLECOIN   | CUSTOMER_POSTING      | GL            | POSTING      | 2         |

---

## How to Add a New Migration

### Adding a script to dev

```bash
# 1. Create the next version file
touch db/src/main/resources/db/migration/dev/V2__your_description.sql

# 2. Write the SQL

# 3. Apply and verify
cd db
mvn flyway:migrate -Pdev
mvn flyway:info -Pdev
```

### Promoting dev → QA / UAT / Prod

1. Review the script — confirm it is safe for the target environment
2. Copy it to the target folder as the next version (e.g., `V2__...sql` if target is at V1)
3. Add an entry to the environment's `CHANGELOG.md` documenting source version, approver, date, and ticket
4. Run `mvn flyway:migrate -P{env}` and verify with `mvn flyway:info -P{env}`

### Numbering rule

Version numbers are **per-environment**. QA's `V2` does not need to match dev's `V2`.
The `CHANGELOG.md` in each folder is the authoritative link between environments.

### Partial promotion (cherry-pick)

If only part of a dev script is needed in QA:
1. Extract the required SQL into a new file in `qa/` as the next QA version
2. Document in `qa/CHANGELOG.md`: which dev version it came from and what was omitted

---

## Golden Rules

1. **Never edit an applied migration.**
   Flyway validates checksums on every startup. A changed file fails validation.
   Always fix forward with a new version.

2. **Run `flyway:info` before `flyway:migrate` on production.**
   Confirm exactly what will be applied before touching prod.

3. **Always take a database backup before running prod migrations.**

4. **Never commit real credentials.**
   All `flyway.conf` files use placeholder values.
   Inject real credentials via `-Dflyway.user` / `-Dflyway.password` at runtime,
   or let CyberArk inject them in the pipeline.

5. **Migrations run before the application starts.**
   The Spring Boot application uses `ddl-auto: validate` — it expects the schema to already exist.
   The Docker Compose `depends_on: flyway: condition: service_completed_successfully` enforces this.

6. **Oracle scripts live in the `oracle/` folder.**
   Oracle uses `INSERT ALL` syntax and requires `COMMIT` at the end.
   Do not copy PostgreSQL scripts directly to Oracle without adapting the syntax.
