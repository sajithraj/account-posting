# Migration Changelog — DEV (Oracle)

Oracle syntax migrations for dev environment integration testing with the backend.
DDL is managed by Flyway; the backend handles DML only at runtime.

Never edit or rename an already-applied migration.
New scripts must be added as the next sequential version: `V{N+1}__description.sql`.

| Version | Description      | Applied | Notes                                                                      |
|---------|------------------|---------|----------------------------------------------------------------------------|
| V1      | Baseline DDL     | —       | account_posting, leg, posting_history, leg_history, config — Oracle syntax |
| V2      | Config seed data | —       | posting_config routing rules: IMX / RMS / STABLECOIN → CBS / GL / OBPM     |
