-- Stores the posting operation type on the leg (POSTING / ADD_HOLD / CANCEL_HOLD).
-- Defaults to POSTING to handle existing rows created before this migration.
ALTER TABLE account_posting_leg
    ADD COLUMN operation VARCHAR(20) NOT NULL DEFAULT 'POSTING';
