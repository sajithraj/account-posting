-- =============================================================================
-- V1__baseline_schema.sql  (Oracle)
-- Consolidated baseline — represents the final schema state equivalent to
-- dev V1 through V12. Applied as a single script on first-time env setup.
--
-- Compatibility: Oracle 12c Release 2 (12.2) and above
--   - GENERATED ALWAYS AS IDENTITY requires Oracle 12c+
--   - IS JSON check constraint requires Oracle 12c Release 1+
--   - TIMESTAMP WITH TIME ZONE is supported on all modern Oracle versions
-- =============================================================================

-- ── account_posting ───────────────────────────────────────────────────────────
CREATE TABLE account_posting
(
    posting_id               NUMBER(19)          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_reference_id      VARCHAR2(100)       NOT NULL,
    end_to_end_reference_id  VARCHAR2(100)       NOT NULL,
    source_name              VARCHAR2(100)       NOT NULL,
    request_type             VARCHAR2(50)        NOT NULL,
    amount                   NUMBER(19, 4)       NOT NULL,
    currency                 CHAR(3)                                       NOT NULL,
    credit_debit_indicator   VARCHAR2(6)         NOT NULL,
    debtor_account           VARCHAR2(50)        NOT NULL,
    creditor_account         VARCHAR2(50)        NOT NULL,
    requested_execution_date DATE                                          NOT NULL,
    remittance_information   VARCHAR2(500),
    status                   VARCHAR2(10)        DEFAULT 'PENDING' NOT NULL,
    request_payload          CLOB,
    response_payload         CLOB,
    retry_locked_until       TIMESTAMP WITH TIME ZONE,
    target_systems           VARCHAR2(500),
    reason                   VARCHAR2(1000),
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_ap_e2e_ref UNIQUE (end_to_end_reference_id),
    CONSTRAINT chk_ap_credit_debit CHECK (credit_debit_indicator IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ap_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
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
    posting_leg_id   NUMBER(19)          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    posting_id       NUMBER(19)          NOT NULL,
    leg_order        NUMBER(10)          NOT NULL,
    target_system    VARCHAR2(100)       NOT NULL,
    account          VARCHAR2(50)        NOT NULL,
    status           VARCHAR2(10)        DEFAULT 'PENDING' NOT NULL,
    reference_id     VARCHAR2(100),
    reason           VARCHAR2(500),
    attempt_number   NUMBER(10)          DEFAULT 1 NOT NULL,
    posted_time      TIMESTAMP WITH TIME ZONE,
    request_payload  CLOB,
    response_payload CLOB,
    mode             VARCHAR2(10)        DEFAULT 'NORM' NOT NULL,
    operation        VARCHAR2(20)        DEFAULT 'POSTING' NOT NULL,
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
    config_id     NUMBER(19)      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_name   VARCHAR2(100)   NOT NULL,
    request_type  VARCHAR2(100)   NOT NULL,
    target_system VARCHAR2(100)   NOT NULL,
    operation     VARCHAR2(100)   NOT NULL,
    order_seq     NUMBER(10)      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT uq_pc_request_type_order UNIQUE (request_type, order_seq)
);

CREATE INDEX idx_pc_request_type ON posting_config (request_type);
CREATE INDEX idx_pc_source_name ON posting_config (source_name);

-- ── Seed data ─────────────────────────────────────────────────────────────────
-- Oracle does not support multi-row INSERT VALUES (...), (...) syntax (pre-23c).
-- Using INSERT ALL with SELECT 1 FROM DUAL instead.
INSERT
ALL
    -- DMS: CBS_GL → CBS then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('DMS',        'CBS_GL',           'CBS_POSTING',  'CBS_PROCESS',  1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('DMS',        'CBS_GL',           'GL_POSTING',   'GL_PROCESS',   2)

    -- DMS: OBPM only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('DMS',        'OBPM',             'OBPM_POSTING', 'OBPM_PROCESS', 1)

    -- RMS: EFD_RETURN → CBS then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'EFD_RETURN',       'CBS_POSTING',  'CBS_PROCESS',  1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'EFD_RETURN',       'GL_POSTING',   'GL_PROCESS',   2)

    -- RMS: USDNT_GL_RETURN → GL only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('RMS',        'USDNT_GL_RETURN',  'GL_POSTING',   'GL_PROCESS',   1)

    -- USDNT: GL_RETURN → GL only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('USDNT',      'GL_RETURN',        'GL_POSTING',   'GL_PROCESS',   1)

    -- LCD: USDNT_GL → OBPM then CBS
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('LCD',        'USDNT_GL',         'OBPM_POSTING', 'OBPM_PROCESS', 1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('LCD',        'USDNT_GL',         'CBS_POSTING',  'CBS_PROCESS',  2)

    -- NPSS: NPSS_PAYMENT → CBS then OBPM
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('NPSS',       'NPSS_PAYMENT',     'CBS_POSTING',  'CBS_PROCESS',  1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('NPSS',       'NPSS_PAYMENT',     'OBPM_POSTING', 'OBPM_PROCESS', 2)

    -- DBA: DBA_ACCOUNT_HOLD → CBS only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('DBA',        'DBA_ACCOUNT_HOLD', 'CBS_POSTING',  'CBS_PROCESS',  1)

    -- STABLECOIN: BNK_CUSTOMER → OBPM then GL
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BNK_CUSTOMER',     'OBPM_POSTING', 'OBPM_PROCESS', 1)
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BNK_CUSTOMER',     'GL_POSTING',   'GL_PROCESS',   2)

    -- STABLECOIN: BNK_CANCEL_HOLD → CBS only
    INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES ('STABLECOIN', 'BNK_CANCEL_HOLD',  'CBS_POSTING',  'CBS_PROCESS',  1)
SELECT 1
FROM DUAL;

COMMIT;
