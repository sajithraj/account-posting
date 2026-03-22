-- Stores the final processing outcome reason per posting.
-- "Request processed successfully" when all legs succeed,
-- otherwise the last failed leg's error message.
ALTER TABLE account_posting
    ADD COLUMN IF NOT EXISTS reason VARCHAR(1000);
