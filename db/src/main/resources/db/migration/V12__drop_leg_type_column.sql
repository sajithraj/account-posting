-- leg_type was a redundant duplicate of target_system.
-- Both columns held the same value (e.g. 'CBS', 'GL', 'OBPM');
-- target_system is the canonical column going forward.
ALTER TABLE account_posting_leg DROP COLUMN IF EXISTS leg_type;
