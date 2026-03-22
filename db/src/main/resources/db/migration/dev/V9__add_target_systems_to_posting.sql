-- Derived from posting_config: target systems concatenated with "_" in orderSeq order.
-- Example: "CBS_GL" for a request type with CBS (order 1) and GL (order 2).
ALTER TABLE account_posting
    ADD COLUMN target_systems VARCHAR(500);
