-- =============================================================================
-- V4__inline_payload_columns.sql
-- Move request/response payload into account_posting table directly.
-- Drops the separate payload tables.
-- =============================================================================

ALTER TABLE account_posting
    ADD COLUMN IF NOT EXISTS request_payload  JSONB,
    ADD COLUMN IF NOT EXISTS response_payload JSONB;

DROP TABLE IF EXISTS account_posting_response_payload;
DROP TABLE IF EXISTS account_posting_request_payload;
