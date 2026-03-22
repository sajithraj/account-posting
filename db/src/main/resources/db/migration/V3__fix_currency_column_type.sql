-- =============================================================================
-- V3__fix_currency_column_type.sql
-- CHAR(3) (bpchar) conflicts with Hibernate's VARCHAR mapping for String fields.
-- Convert to VARCHAR(3) to align with the JPA entity definition.
-- =============================================================================

ALTER TABLE account_posting
    ALTER COLUMN currency TYPE VARCHAR(3);
