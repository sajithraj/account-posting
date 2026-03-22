-- Remove version (optimistic lock) - no longer needed
ALTER TABLE account_posting_leg DROP COLUMN version;

-- Drop old leg_type column, rename leg_name to leg_type
ALTER TABLE account_posting_leg DROP COLUMN leg_type;
ALTER TABLE account_posting_leg RENAME COLUMN leg_name TO leg_type;
