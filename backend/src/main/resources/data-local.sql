-- =============================================================================
-- data-local.sql — H2 seed data for local development
-- Runs after Hibernate creates the schema (defer-datasource-initialization=true)
-- =============================================================================

-- ── posting_config ────────────────────────────────────────────────────────────
-- Source: dev DB (2026-03-21). Values are canonical — do not change source/request names.
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq, created_at, updated_at,
                            created_by, updated_by)
VALUES
-- IMX: IMX_CBS_GL → CBS then GL
('IMX', 'IMX_CBS_GL', 'CBS', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
('IMX', 'IMX_CBS_GL', 'GL', 'POSTING', 2, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- IMX: IMX_OBPM → OBPM only
('IMX', 'IMX_OBPM', 'OBPM', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- RMS: FED_RETURN → CBS then GL
('RMS', 'FED_RETURN', 'CBS', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
('RMS', 'FED_RETURN', 'GL', 'POSTING', 2, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- RMS: GL_RETURN → GL (two GL legs)
('RMS', 'GL_RETURN', 'GL', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
('RMS', 'GL_RETURN', 'GL', 'POSTING', 2, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- RMS: MCA_RETURN → OBPM only
('RMS', 'MCA_RETURN', 'OBPM', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- STABLECOIN: BUY_CUSTOMER_POSTING → CBS (order 2) then GL (order 3)
('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS', 'POSTING', 2, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'GL', 'POSTING', 3, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- STABLECOIN: ADD_ACCOUNT_HOLD → CBS (ADD_HOLD operation)
('STABLECOIN', 'ADD_ACCOUNT_HOLD', 'CBS', 'ADD_HOLD', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- STABLECOIN: BUY_CUSTOMER_POSTING → CBS (REMOVE_HOLD operation)
('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS', 'REMOVE_HOLD', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),

-- STABLECOIN: CUSTOMER_POSTING → CBS then GL
('STABLECOIN', 'CUSTOMER_POSTING', 'CBS', 'POSTING', 1, NOW(), NOW(), 'SYSTEM', 'SYSTEM'),
('STABLECOIN', 'CUSTOMER_POSTING', 'GL', 'POSTING', 2, NOW(), NOW(), 'SYSTEM', 'SYSTEM');


-- =============================================================================
-- TEST POSTINGS — retry scenario coverage
-- Each posting has status=PNDG so it is picked up by every retry run.
-- request_payload is the minimal JSON the retry processor needs to deserialise.
-- =============================================================================

-- ── S1: IMX_OBPM — 1 leg FAILED ──────────────────────────────────────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S1', 'TEST-E2E-S1', 'IMX',
        'IMX_OBPM', 1000.00, 'USD', 'CREDIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S1 - 1 leg FAILED', 'PNDG', 'OBPM', 'OBPM call failed',
        '{"source_reference_id":"TEST-SRC-S1","end_to_end_reference_id":"TEST-E2E-S1","source_name":"IMX","request_type":"IMX_OBPM","amount":1000.00,"currency":"USD","credit_debit_indicator":"CREDIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'OBPM',
       '1234567890',
       'FAILED',
       'POSTING',
       'NORM',
       'Connection timeout',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S1';


-- ── S2: IMX_CBS_GL — 2 legs both FAILED ──────────────────────────────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S2', 'TEST-E2E-S2', 'IMX',
        'IMX_CBS_GL', 2500.00, 'USD', 'DEBIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S2 - 2 legs both FAILED', 'PNDG', 'CBS_GL', 'CBS and GL both failed',
        '{"source_reference_id":"TEST-SRC-S2","end_to_end_reference_id":"TEST-E2E-S2","source_name":"IMX","request_type":"IMX_CBS_GL","amount":2500.00,"currency":"USD","credit_debit_indicator":"DEBIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'CBS',
       '1234567890',
       'FAILED',
       'POSTING',
       'NORM',
       'CBS returned 503',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S2';

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       2,
       'GL',
       '1234567890',
       'FAILED',
       'POSTING',
       'NORM',
       'GL service unavailable',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S2';


-- ── S3: IMX_CBS_GL — CBS SUCCESS, GL FAILED ───────────────────────────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S3', 'TEST-E2E-S3', 'IMX',
        'IMX_CBS_GL', 750.00, 'USD', 'CREDIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S3 - CBS SUCCESS, GL FAILED', 'PNDG', 'CBS_GL', 'GL call failed',
        '{"source_reference_id":"TEST-SRC-S3","end_to_end_reference_id":"TEST-E2E-S3","source_name":"IMX","request_type":"IMX_CBS_GL","amount":750.00,"currency":"USD","credit_debit_indicator":"CREDIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'CBS',
       '1234567890',
       'SUCCESS',
       'POSTING',
       'NORM',
       null,
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S3';

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       2,
       'GL',
       '1234567890',
       'FAILED',
       'POSTING',
       'NORM',
       'GL timeout after 30s',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S3';


-- ── S4: ADD_ACCOUNT_HOLD — 1 leg CBS ADD_HOLD FAILED ─────────────────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S4', 'TEST-E2E-S4', 'STABLECOIN',
        'ADD_ACCOUNT_HOLD', 5000.00, 'USD', 'DEBIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S4 - CBS ADD_HOLD FAILED', 'PNDG', 'CBS', 'CBS add-hold failed',
        '{"source_reference_id":"TEST-SRC-S4","end_to_end_reference_id":"TEST-E2E-S4","source_name":"STABLECOIN","request_type":"ADD_ACCOUNT_HOLD","amount":5000.00,"currency":"USD","credit_debit_indicator":"DEBIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'CBS',
       '1234567890',
       'FAILED',
       'ADD_HOLD',
       'NORM',
       'Hold limit exceeded',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S4';


-- ── S5: BUY_CUSTOMER_POSTING — 1 leg CBS REMOVE_HOLD FAILED ──────────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S5', 'TEST-E2E-S5', 'STABLECOIN',
        'BUY_CUSTOMER_POSTING', 3200.00, 'USD', 'DEBIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S5 - CBS REMOVE_HOLD FAILED', 'PNDG', 'CBS', 'CBS remove-hold failed',
        '{"source_reference_id":"TEST-SRC-S5","end_to_end_reference_id":"TEST-E2E-S5","source_name":"STABLECOIN","request_type":"BUY_CUSTOMER_POSTING","amount":3200.00,"currency":"USD","credit_debit_indicator":"DEBIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'CBS',
       '1234567890',
       'FAILED',
       'REMOVE_HOLD',
       'NORM',
       'Hold reference not found',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S5';


-- ── S6: IMX_CBS_GL — locked posting (should be skipped by retry) ──────────────
INSERT INTO account_posting (source_reference_id, end_to_end_reference_id, source_name,
                             request_type, amount, currency, credit_debit_indicator,
                             debtor_account, creditor_account, requested_execution_date,
                             remittance_information, status, target_systems, reason, retry_locked_until,
                             request_payload,
                             created_at, updated_at, created_by, updated_by)
VALUES ('TEST-SRC-S6', 'TEST-E2E-S6', 'IMX',
        'IMX_CBS_GL', 100.00, 'USD', 'CREDIT',
        '1234567890', '0987654321', CURRENT_DATE,
        'S6 - locked posting (skip)', 'PNDG', 'CBS_GL', 'CBS failed',
        TIMESTAMPADD(MINUTE, 2, NOW()),
        '{"source_reference_id":"TEST-SRC-S6","end_to_end_reference_id":"TEST-E2E-S6","source_name":"IMX","request_type":"IMX_CBS_GL","amount":100.00,"currency":"USD","credit_debit_indicator":"CREDIT","debtor_account":"1234567890","creditor_account":"0987654321","requested_execution_date":"2026-01-01"}',
        NOW(), NOW(), 'SYSTEM', 'SYSTEM');

INSERT INTO account_posting_transaction (posting_id, transaction_order, target_system, account, status, operation, transaction_mode, reason,
                                 attempt_number, created_at, updated_at, created_by, updated_by)
SELECT posting_id,
       1,
       'CBS',
       '1234567890',
       'FAILED',
       'POSTING',
       'NORM',
       'CBS failed',
       1,
       NOW(),
       NOW(),
       'SYSTEM',
       'SYSTEM'
FROM account_posting
WHERE end_to_end_reference_id = 'TEST-E2E-S6';

