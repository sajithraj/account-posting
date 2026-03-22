# db — Flyway Migrations

Flyway 11.15.0 · PostgreSQL · Maven

This module is responsible solely for database schema management. The Spring Boot application has no Flyway dependency —
it connects to an already-migrated database.

---

## Migration Folder Structure

Migrations are environment-specific. Each environment has its own folder and is managed independently — scripts are
explicitly reviewed and copied before promotion. This prevents accidental schema changes from bleeding across
environments.

```
db/src/main/resources/db/migration/
  dev/     V1–V12   incremental history (source of truth)
  docker/  V1–V12   mirrors dev — for local Docker Compose
  qa/      V1       consolidated baseline (dev V1–V12 in one script)
  uat/     V1       consolidated baseline (dev V1–V12 in one script)
  prod/    V1       consolidated baseline (dev V1–V12 in one script)
```

**Why QA/UAT/Prod have a single script:**
QA, UAT, and prod were first-time setups. Instead of replaying all 12 incremental dev steps, a single
`V1__baseline_schema.sql` was created representing the final schema state. Future changes to these environments will be
added as `V2`, `V3`, etc.

**Each folder has a `CHANGELOG.md`** documenting every script that was promoted, from where, who approved it, and the
ticket reference (prod requires a ticket for every entry).

---

## Run Commands

```bash
cd db

# Apply pending migrations (dev is the default profile)
mvn flyway:migrate -Pdev
mvn flyway:migrate -Pqa
mvn flyway:migrate -Puat
mvn flyway:migrate -Pprod
mvn flyway:migrate -Pdocker

# Check what has been applied and what is pending
mvn flyway:info -Pdev

# Validate applied scripts match the files (checksum verification)
mvn flyway:validate -Pdev

# Fix checksum mismatches (use only if a script was legitimately repaired)
mvn flyway:repair -Pdev

# Override credentials at the command line (do this for prod — never commit real creds)
mvn flyway:migrate -Pprod \
  -Dflyway.url=jdbc:postgresql://prod-host:5432/account_posting_db \
  -Dflyway.user=prod_user \
  -Dflyway.password=prod_secret
```

---

## Dev Migration History

| Version | Description                   | What it does                                                                                        |
|---------|-------------------------------|-----------------------------------------------------------------------------------------------------|
| V1      | init_schema                   | Creates `account_posting`, `account_posting_leg` tables with indexes                                |
| V2      | posting_config_table          | Creates `posting_config` table + seeds all routing rules                                            |
| V3      | fix_currency_column_type      | Changes `currency` from `CHAR(3)` to `VARCHAR(3)` for JPA compatibility                             |
| V4      | inline_payload_columns        | Adds `request_payload`/`response_payload` JSONB to `account_posting`, drops separate payload tables |
| V5      | posting_config_unique_order   | Adds unique constraint on `(request_type, order_seq)`                                               |
| V6      | leg_table_cleanup             | Drops `version`, drops original `leg_type`, renames `leg_name` → `leg_type`                         |
| V7      | move_retry_lock_to_posting    | Moves `retry_locked_until` from leg to posting — lock is now at posting level                       |
| V8      | add_leg_mode                  | Adds `mode VARCHAR(10)` to leg: `NORM` / `RETRY` / `MANUAL`                                         |
| V9      | add_target_systems_to_posting | Adds `target_systems VARCHAR(500)` — CSV of ordered target system names                             |
| V10     | add_operation_to_leg          | Adds `operation VARCHAR(20)` to leg: `POSTING` / `ADD_HOLD` / `CANCEL_HOLD`                         |
| V11     | add_reason_to_posting         | Adds `reason VARCHAR(1000)` — final outcome message per posting                                     |
| V12     | drop_leg_type_column          | Drops redundant `leg_type` — `target_system` is the canonical column                                |

---

## How to Promote a Migration

### Adding a new script to dev

1. Write `V{N+1}__{description}.sql` in `dev/`
2. Add a row to `dev/CHANGELOG.md`
3. Run `mvn flyway:migrate -Pdev` and verify with `mvn flyway:info -Pdev`

### Promoting from dev to QA (full script)

1. Review the script — confirm it is safe to apply to QA
2. Copy it to `qa/` as the next sequential version (e.g., `V2__...sql` if QA is currently at V1)
3. Add a row to `qa/CHANGELOG.md` (document source dev version, approver, date, ticket)
4. Run `mvn flyway:migrate -Pqa`

### Promoting a partial change (cherry-pick)

If you only want part of a dev script (e.g., dev has V13 with 3 changes, you only want the third):

1. Extract the required SQL into a new file in `qa/` as the next QA version
2. Document in `qa/CHANGELOG.md`: which dev version it came from and what was omitted
3. Dev V13 stays as-is — it will be fully promoted in a future release

### Numbering rule

Version numbers are **per-environment**. QA's `V2` does not have to match dev's `V2`. The CHANGELOG is the link between
them.

---

## Golden Rules

1. **Never edit an applied migration.** Flyway validates checksums — a changed file fails validation. Always fix forward
   with a new version.
2. **Run `flyway:info` before `flyway:migrate` on production.** Confirm exactly what will be applied before touching
   prod.
3. **Always take a database backup before running prod migrations.**
4. **Credentials** are placeholders in all yml files — real values are injected by CyberArk at runtime. Never commit
   real production credentials.
5. **Migrations run before the application deployment.** The app expects the new schema to exist on startup.
