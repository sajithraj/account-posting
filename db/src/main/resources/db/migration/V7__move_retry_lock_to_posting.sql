-- Move retry_locked_until from leg table to posting table.
-- Locking is now applied at the posting level, not per-leg.

ALTER TABLE account_posting
    ADD COLUMN retry_locked_until TIMESTAMPTZ;

ALTER TABLE account_posting_leg
    DROP COLUMN retry_locked_until;
