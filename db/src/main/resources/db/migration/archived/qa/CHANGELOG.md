# Migration Changelog — QA

Scripts in this folder are explicitly promoted from dev after DBA/lead review.
Version numbers reflect the order of application in QA — they may differ from dev numbering
if scripts were cherry-picked or partially promoted.

Never edit or rename an already-applied migration.
Document every promotion below before running flyway:migrate.

| Version | Description     | Covers     | Approved By | Date       | Notes                                    |
|---------|-----------------|------------|-------------|------------|------------------------------------------|
| V1      | Baseline schema | dev V1–V12 | DBA Team    | 2026-03-22 | Consolidated first-time setup for QA env |
