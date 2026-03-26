-- =============================================================================
-- V1__baseline_schema.sql  (Oracle)
-- Full baseline schema — tables, indexes, constraints, seed data, and
-- history tables for archival.  Single consolidated script for all environments.
--
-- Compatibility: Oracle 12c Release 2 (12.2) and above
--   - GENERATED ALWAYS AS IDENTITY requires Oracle 12c+
--   - IS JSON check constraint requires Oracle 12c Release 1+
-- =============================================================================

-- ── account_posting ───────────────────────────────────────────────────────────
CREATE TABLE account_posting
(
    posting_id               NUMBER(19)               GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_reference_id      VARCHAR2(100)            NOT NULL,
    end_to_end_reference_id  VARCHAR2(100)            NOT NULL,
    source_name              VARCHAR2(100)            NOT NULL,
    request_type             VARCHAR2(50)             NOT NULL,
    amount                   NUMBER(19, 4)            NOT NULL,
    currency                 CHAR(3)                                       NOT NULL,
    credit_debit_indicator   VARCHAR2(6)              NOT NULL,
    debtor_account           VARCHAR2(50)             NOT NULL,
    creditor_account         VARCHAR2(50)             NOT NULL,
    requested_execution_date DATE                                          NOT NULL,
    remittance_information   VARCHAR2(500),
    status                   VARCHAR2(10)             DEFAULT 'PNDG' NOT NULL,
    request_payload          CLOB,
    response_payload         CLOB,
    retry_locked_until       TIMESTAMP WITH TIME ZONE,
    target_systems           VARCHAR2(500),
    reason                   VARCHAR2(1000),
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_ap_e2e_ref UNIQUE (end_to_end_reference_id),
    CONSTRAINT chk_ap_credit_debit CHECK (credit_debit_indicator IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ap_status CHECK (status IN ('PNDG', 'ACSP', 'RJCT')),
    CONSTRAINT chk_ap_req_payload_json CHECK (request_payload IS JSON),
    CONSTRAINT chk_ap_res_payload_json CHECK (response_payload IS JSON)
);

CREATE INDEX idx_ap_status ON account_posting (status);
CREATE INDEX idx_ap_e2e_ref ON account_posting (end_to_end_reference_id);
CREATE INDEX idx_ap_src_ref ON account_posting (source_reference_id);
CREATE INDEX idx_ap_requested_date ON account_posting (requested_execution_date);
CREATE INDEX idx_ap_request_type ON account_posting (request_type);

-- ── account_posting_leg ───────────────────────────────────────────────────────
CREATE TABLE account_posting_leg
(
    posting_leg_id   NUMBER(19)               GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    posting_id       NUMBER(19)               NOT NULL,
    leg_order        NUMBER(10)               NOT NULL,
    target_system    VARCHAR2(100)            NOT NULL,
    account          VARCHAR2(50)             NOT NULL,
    status           VARCHAR2(10)             DEFAULT 'PENDING' NOT NULL,
    reference_id     VARCHAR2(100),
    reason           VARCHAR2(500),
    attempt_number   NUMBER(10)               DEFAULT 1 NOT NULL,
    posted_time      TIMESTAMP WITH TIME ZONE,
    request_payload  CLOB,
    response_payload CLOB,
    mode             VARCHAR2(10)             DEFAULT 'NORM' NOT NULL,
    operation        VARCHAR2(20)             DEFAULT 'POSTING' NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT fk_apl_posting_id FOREIGN KEY (posting_id) REFERENCES account_posting (posting_id) ON DELETE CASCADE,
    CONSTRAINT chk_apl_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_apl_req_payload_json CHECK (request_payload IS JSON),
    CONSTRAINT chk_apl_res_payload_json CHECK (response_payload IS JSON)
);

CREATE INDEX idx_apl_posting_id ON account_posting_leg (posting_id);
CREATE INDEX idx_apl_status ON account_posting_leg (status);

-- ── posting_config ────────────────────────────────────────────────────────────
CREATE TABLE posting_config
(
    config_id     NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_name   VARCHAR2(100) NOT NULL,
    request_type  VARCHAR2(100) NOT NULL,
    target_system VARCHAR2(100) NOT NULL,
    operation     VARCHAR2(100) NOT NULL,
    order_seq     NUMBER(10)    NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_pc_request_type_order UNIQUE (request_type, order_seq)
);

CREATE INDEX idx_pc_request_type ON posting_config (request_type);
CREATE INDEX idx_pc_source_name ON posting_config (source_name);

-- ── Seed data ─────────────────────────────────────────────────────────────────
-- Oracle pre-23c does not support multi-row INSERT VALUES (...), (...).
-- Using INSERT ALL / SELECT 1 FROM DUAL instead.
INSERT
ALL
    -- IMX: IMX_CBS_GL → CBS then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX',        'IMX_CBS_GL',          'CBS',  'POSTING',     1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX',        'IMX_CBS_GL',          'GL',   'POSTING',     2)

    -- IMX: IMX_OBPM → OBPM only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('IMX',        'IMX_OBPM',            'OBPM', 'POSTING',     1)

    -- RMS: FED_RETURN → CBS then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'FED_RETURN',          'CBS',  'POSTING',     1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'FED_RETURN',          'GL',   'POSTING',     2)

    -- RMS: GL_RETURN → GL (two legs)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'GL_RETURN',           'GL',   'POSTING',     1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'GL_RETURN',           'GL',   'POSTING',     2)

    -- RMS: MCA_RETURN → OBPM only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'MCA_RETURN',          'OBPM', 'POSTING',     1)

    -- STABLECOIN: BUY_CUSTOMER_POSTING → CBS (order 2) then GL (order 3)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'CBS',  'POSTING',     2)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING', 'GL',   'POSTING',     3)

    -- STABLECOIN: ADD_ACCOUNT_HOLD → CBS (ADD_HOLD operation)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'ADD_ACCOUNT_HOLD',    'CBS',  'ADD_HOLD',    1)

    -- STABLECOIN: BUY_CUSTOMER_POSTING → CBS (REMOVE_HOLD operation)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BUY_CUSTOMER_POSTING','CBS',  'REMOVE_HOLD', 1)

    -- STABLECOIN: CUSTOMER_POSTING → CBS then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'CUSTOMER_POSTING',    'CBS',  'POSTING',     1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'CUSTOMER_POSTING',    'GL',   'POSTING',     2)
SELECT 1
FROM DUAL;

-- ── account_posting_history ───────────────────────────────────────────────────
CREATE TABLE account_posting_history
(
    posting_id               NUMBER(19)               NOT NULL PRIMARY KEY,
    source_reference_id      VARCHAR2(100)            NOT NULL,
    end_to_end_reference_id  VARCHAR2(100)            NOT NULL,
    source_name              VARCHAR2(100)            NOT NULL,
    request_type             VARCHAR2(50)             NOT NULL,
    amount                   NUMBER(19, 4)            NOT NULL,
    currency                 CHAR(3)                                       NOT NULL,
    credit_debit_indicator   VARCHAR2(6)              NOT NULL,
    debtor_account           VARCHAR2(50)             NOT NULL,
    creditor_account         VARCHAR2(50)             NOT NULL,
    requested_execution_date DATE                                          NOT NULL,
    remittance_information   VARCHAR2(500),
    status                   VARCHAR2(10)             NOT NULL,
    request_payload          CLOB,
    response_payload         CLOB,
    retry_locked_until       TIMESTAMP WITH TIME ZONE,
    target_systems           VARCHAR2(500),
    reason                   VARCHAR2(1000),
    created_at               TIMESTAMP WITH TIME ZONE                      NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE                      NOT NULL,
    archived_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_aph_status ON account_posting_history (status);
CREATE INDEX idx_aph_e2e_ref ON account_posting_history (end_to_end_reference_id);
CREATE INDEX idx_aph_src_ref ON account_posting_history (source_reference_id);
CREATE INDEX idx_aph_request_type ON account_posting_history (request_type);
CREATE INDEX idx_aph_created_at ON account_posting_history (created_at);
CREATE INDEX idx_aph_archived_at ON account_posting_history (archived_at);

-- ── account_posting_leg_history ───────────────────────────────────────────────
CREATE TABLE account_posting_leg_history
(
    posting_leg_id   NUMBER(19)               NOT NULL PRIMARY KEY,
    posting_id       NUMBER(19)               NOT NULL,
    leg_order        NUMBER(10)               NOT NULL,
    target_system    VARCHAR2(100)            NOT NULL,
    account          VARCHAR2(50)             NOT NULL,
    status           VARCHAR2(10)             NOT NULL,
    reference_id     VARCHAR2(100),
    reason           VARCHAR2(500),
    attempt_number   NUMBER(10)               NOT NULL,
    posted_time      TIMESTAMP WITH TIME ZONE,
    request_payload  CLOB,
    response_payload CLOB,
    mode             VARCHAR2(10)             NOT NULL,
    operation        VARCHAR2(20)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE                      NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE                      NOT NULL,
    archived_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_aplh_posting_id ON account_posting_leg_history (posting_id);
CREATE INDEX idx_aplh_status ON account_posting_leg_history (status);
CREATE INDEX idx_aplh_archived_at ON account_posting_leg_history (archived_at);

COMMIT;
