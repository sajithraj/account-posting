# Migration Changelog — Docker

Scripts in this folder are used for local Docker Compose environments.
Mirrors the dev folder — kept in sync manually when dev scripts are stable.
Used for local integration testing and onboarding new developers.

Never edit or rename an already-applied migration.

| Version | Description                      | Source  | Date       | Notes                                      |
|---------|----------------------------------|---------|------------|--------------------------------------------|
| V1      | Initial schema                   | dev V1  | 2026-03-22 | account_posting, leg, payload tables       |
| V2      | Posting config table + seed data | dev V2  | 2026-03-22 | Strategy routing: source → target systems  |
| V3      | Fix currency column type         | dev V3  | 2026-03-22 | CHAR(3) → VARCHAR(3) for JPA compatibility |
| V4      | Inline payload columns           | dev V4  | 2026-03-22 | Dropped separate payload tables            |
| V5      | Posting config unique order      | dev V5  | 2026-03-22 | Prevent duplicate order_seq per type       |
| V6      | Leg table cleanup                | dev V6  | 2026-03-22 | Drop version, rename leg_name → leg_type   |
| V7      | Move retry lock to posting       | dev V7  | 2026-03-22 | Lock now at posting level, not leg         |
| V8      | Add leg mode column              | dev V8  | 2026-03-22 | NORM / RETRY / MANUAL                      |
| V9      | Add target_systems to posting    | dev V9  | 2026-03-22 | Derived summary of routing config          |
| V10     | Add operation to leg             | dev V10 | 2026-03-22 | POSTING / ADD_HOLD / CANCEL_HOLD           |
| V11     | Add reason to posting            | dev V11 | 2026-03-22 | Final outcome message per posting          |
| V12     | Drop redundant leg_type column   | dev V12 | 2026-03-22 | target_system is the canonical column      |
