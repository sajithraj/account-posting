-- Add mode column to account_posting_leg
-- NORM  = initial posting call
-- RETRY = retry path
-- MANUAL = manually updated from UI
ALTER TABLE account_posting_leg
    ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'NORM';
