-- =============================================================================
-- data-local.sql — H2 seed data for local development
-- Runs after Hibernate creates the schema (defer-datasource-initialization=true)
-- =============================================================================

-- ── posting_config ────────────────────────────────────────────────────────────
-- Source: dev DB (2026-03-21). Values are canonical — do not change source/request names.
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq)
VALUES
-- IMX: IMX_CBS_GL → CBS then GL
('IMX', 'IMX_CBS_GL', 'CBS', 'POSTING', 1),
('IMX', 'IMX_CBS_GL', 'GL', 'POSTING', 2),

-- IMX: IMX_OBPM → OBPM only
('IMX', 'IMX_OBPM', 'OBPM', 'POSTING', 1),

-- RMS: FED_RETURN → CBS then GL
('RMS', 'FED_RETURN', 'CBS', 'POSTING', 1),
('RMS', 'FED_RETURN', 'GL', 'POSTING', 2),

-- RMS: GL_RETURN → GL (two GL legs)
('RMS', 'GL_RETURN', 'GL', 'POSTING', 1),
('RMS', 'GL_RETURN', 'GL', 'POSTING', 2),

-- RMS: MCA_RETURN → OBPM only
('RMS', 'MCA_RETURN', 'OBPM', 'POSTING', 1),

-- STABLECOIN: BUY_CUSTOMER_POSTNG → CBS (order 2) then GL (order 3)
-- Note: 'BUY_CUSTOMER_POSTNG' is the actual value in dev (missing trailing 'I')
('STABLECOIN', 'BUY_CUSTOMER_POSTNG', 'CBS', 'POSTING', 2),
('STABLECOIN', 'BUY_CUSTOMER_POSTNG', 'GL', 'POSTING', 3),

-- STABLECOIN: ADD_ACCOUNT_HOLD → CBS (ADD_HOLD operation)
('STABLECOIN', 'ADD_ACCOUNT_HOLD', 'CBS', 'ADD_HOLD', 1),

-- STABLECOIN: BUY_CUSTOMER_POSTING → CBS (REMOVE_HOLD operation)
('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS', 'REMOVE_HOLD', 1),

-- STABLECOIN: CUSTOMER_POSTING → CBS then GL
('STABLECOIN', 'CUSTOMER_POSTING', 'CBS', 'POSTING', 1),
('STABLECOIN', 'CUSTOMER_POSTING', 'GL', 'POSTING', 2);

