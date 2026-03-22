# Migration Changelog — PROD

Scripts in this folder are explicitly promoted from UAT after formal release approval.
Every entry must have a ticket reference, DBA sign-off, and a rollback plan documented
in the release ticket before flyway:migrate is executed on production.

CRITICAL:

- Never edit or rename an already-applied migration.
- Always take a database backup before running migrations in production.
- Run flyway:validate before flyway:migrate to confirm checksum integrity.
- Have a rollback plan ready (reverse migration or restore from backup).

| Version | Description     | Covers     | Approved By | Date       | Ticket | Notes                                      |
|---------|-----------------|------------|-------------|------------|--------|--------------------------------------------|
| V1      | Baseline schema | dev V1–V12 | DBA Team    | 2026-03-22 | AP-001 | Consolidated first-time setup for PROD env |
