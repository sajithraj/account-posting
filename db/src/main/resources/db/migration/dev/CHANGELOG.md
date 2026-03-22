# Migration Changelog — DEV

Scripts in this folder are the source of truth for the dev environment.
All migrations here are reviewed before being selectively promoted to QA.

Never edit or rename an already-applied migration.
New scripts must be added as the next sequential version: `V{N+1}__description.sql`.

| Version | Description                      | Applied    | Notes                                      |
|---------|----------------------------------|------------|--------------------------------------------|
| V1      | Initial schema                   | 2026-03-22 | account_posting, leg, payload tables       |
| V2      | Posting config table + seed data | 2026-03-22 | Strategy routing: source → target systems  |
| V3      | Fix currency column type         | 2026-03-22 | CHAR(3) → VARCHAR(3) for JPA compatibility |
| V4      | Inline payload columns           | 2026-03-22 | Dropped separate payload tables            |
| V5      | Posting config unique order      | 2026-03-22 | Prevent duplicate order_seq per type       |
| V6      | Leg table cleanup                | 2026-03-22 | Drop version, rename leg_name → leg_type   |
| V7      | Move retry lock to posting       | 2026-03-22 | Lock now at posting level, not leg         |
| V8      | Add leg mode column              | 2026-03-22 | NORM / RETRY / MANUAL                      |
| V9      | Add target_systems to posting    | 2026-03-22 | Derived summary of routing config          |
| V10     | Add operation to leg             | 2026-03-22 | POSTING / ADD_HOLD / CANCEL_HOLD           |
| V11     | Add reason to posting            | 2026-03-22 | Final outcome message per posting          |
| V12     | Drop redundant leg_type column   | 2026-03-22 | target_system is the canonical column      |
