# Migration Changelog — UAT

Scripts in this folder are explicitly promoted from QA after sign-off.
Version numbers reflect the order of application in UAT — they may differ from QA/dev numbering
if scripts were cherry-picked or partially promoted.

Never edit or rename an already-applied migration.
Document every promotion below before running flyway:migrate.

| Version | Description     | Covers     | Approved By | Date       | Notes                                     |
|---------|-----------------|------------|-------------|------------|-------------------------------------------|
| V1      | Baseline schema | dev V1–V12 | DBA Team    | 2026-03-22 | Consolidated first-time setup for UAT env |
