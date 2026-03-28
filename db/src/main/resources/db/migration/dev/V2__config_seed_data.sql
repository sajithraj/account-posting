-- =============================================================================
-- V2__config_seed_data.sql
-- Seed data for posting_config — routing rules per source/request_type.
-- Individual INSERT statements (one per row) for Oracle compatibility.
-- =============================================================================

-- IMX: IMX_CBS_GL → CBS then GL
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX', 'IMX_CBS_GL', 'CBS', 'POSTING', 1);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX', 'IMX_CBS_GL', 'GL',  'POSTING', 2);

-- IMX: IMX_OBPM → OBPM only
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX', 'IMX_OBPM', 'OBPM', 'POSTING', 1);

-- RMS: FED_RETURN → CBS then GL
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS', 'FED_RETURN', 'CBS', 'POSTING', 1);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS', 'FED_RETURN', 'GL',  'POSTING', 2);

-- RMS: GL_RETURN → GL (two legs)
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS', 'GL_RETURN', 'GL', 'POSTING', 1);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS', 'GL_RETURN', 'GL', 'POSTING', 2);

-- RMS: MCA_RETURN → OBPM only
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS', 'MCA_RETURN', 'OBPM', 'POSTING', 1);

-- STABLECOIN: BUY_CUSTOMER_POSTING → REMOVE_HOLD (1), CBS posting (2), GL posting (3)
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS', 'REMOVE_HOLD', 1);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS', 'POSTING',     2);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'GL',  'POSTING',     3);

-- STABLECOIN: ADD_ACCOUNT_HOLD → CBS (ADD_HOLD operation)
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'ADD_ACCOUNT_HOLD', 'CBS', 'ADD_HOLD', 1);

-- STABLECOIN: CUSTOMER_POSTING → CBS then GL
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'CUSTOMER_POSTING', 'CBS', 'POSTING', 1);
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'CUSTOMER_POSTING', 'GL',  'POSTING', 2);

COMMIT;
