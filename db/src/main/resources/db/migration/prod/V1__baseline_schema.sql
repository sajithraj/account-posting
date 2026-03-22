-- =============================================================================
-- V1__baseline_schema.sql
-- Consolidated baseline — represents the final schema state equivalent to
-- dev V1 through V12. Applied as a single script on first-time env setup.
-- =============================================================================

-- ── account_posting ───────────────────────────────────────────────────────────
CREATE TABLE account_posting
(
    posting_id               BIGSERIAL PRIMARY KEY,
    source_reference_id      VARCHAR(100)   NOT NULL,
    end_to_end_reference_id  VARCHAR(100)   NOT NULL UNIQUE,
    source_name              VARCHAR(100)   NOT NULL,
    request_type             VARCHAR(50)    NOT NULL,
    amount                   NUMERIC(19, 4) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL,
    credit_debit_indicator   VARCHAR(6)     NOT NULL
        CHECK (credit_debit_indicator IN ('CREDIT', 'DEBIT')),
    debtor_account           VARCHAR(50)    NOT NULL,
    creditor_account         VARCHAR(50)    NOT NULL,
    requested_execution_date DATE           NOT NULL,
    remittance_information   VARCHAR(500),
    status                   VARCHAR(10)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    request_payload          JSONB,
    response_payload         JSONB,
    retry_locked_until       TIMESTAMPTZ,
    target_systems           VARCHAR(500),
    reason                   VARCHAR(1000),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ap_status ON account_posting (status);
CREATE INDEX idx_ap_e2e_ref ON account_posting (end_to_end_reference_id);
CREATE INDEX idx_ap_src_ref ON account_posting (source_reference_id);
CREATE INDEX idx_ap_requested_date ON account_posting (requested_execution_date);
CREATE INDEX idx_ap_request_type ON account_posting (request_type);

-- ── account_posting_leg ───────────────────────────────────────────────────────
CREATE TABLE account_posting_leg
(
    posting_leg_id   BIGSERIAL PRIMARY KEY,
    posting_id       BIGINT       NOT NULL REFERENCES account_posting (posting_id),
    leg_order        INT          NOT NULL,
    target_system    VARCHAR(100) NOT NULL,
    account          VARCHAR(50)  NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    reference_id     VARCHAR(100),
    reason           VARCHAR(500),
    attempt_number   INT          NOT NULL DEFAULT 1,
    posted_time      TIMESTAMPTZ,
    request_payload  JSONB,
    response_payload JSONB,
    mode             VARCHAR(10)  NOT NULL DEFAULT 'NORM',
    operation        VARCHAR(20)  NOT NULL DEFAULT 'POSTING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_apl_posting_id ON account_posting_leg (posting_id);
CREATE INDEX idx_apl_status ON account_posting_leg (status);

-- ── posting_config ────────────────────────────────────────────────────────────
CREATE TABLE posting_config
(
    config_id     BIGSERIAL PRIMARY KEY,
    source_name   VARCHAR(100) NOT NULL,
    request_type  VARCHAR(100) NOT NULL,
    target_system VARCHAR(100) NOT NULL,
    operation     VARCHAR(100) NOT NULL,
    order_seq     INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_posting_config_request_type_order UNIQUE (request_type, order_seq)
);

CREATE INDEX idx_pc_request_type ON posting_config (request_type);
CREATE INDEX idx_pc_source_name ON posting_config (source_name);

-- ── Seed data ─────────────────────────────────────────────────────────────────
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq)
VALUES
-- DMS: CBS_GL → CBS then GL
('DMS', 'CBS_GL', 'CBS_POSTING', 'CBS_PROCESS', 1),
('DMS', 'CBS_GL', 'GL_POSTING', 'GL_PROCESS', 2),

-- DMS: OBPM only
('DMS', 'OBPM', 'OBPM_POSTING', 'OBPM_PROCESS', 1),

-- RMS: EFD_RETURN → CBS then GL
('RMS', 'EFD_RETURN', 'CBS_POSTING', 'CBS_PROCESS', 1),
('RMS', 'EFD_RETURN', 'GL_POSTING', 'GL_PROCESS', 2),

-- RMS: USDNT_GL_RETURN → GL only
('RMS', 'USDNT_GL_RETURN', 'GL_POSTING', 'GL_PROCESS', 1),

-- USDNT: GL_RETURN → GL only
('USDNT', 'GL_RETURN', 'GL_POSTING', 'GL_PROCESS', 1),

-- LCD: USDNT_GL → OBPM then CBS
('LCD', 'USDNT_GL', 'OBPM_POSTING', 'OBPM_PROCESS', 1),
('LCD', 'USDNT_GL', 'CBS_POSTING', 'CBS_PROCESS', 2),

-- NPSS: NPSS_PAYMENT → CBS then OBPM
('NPSS', 'NPSS_PAYMENT', 'CBS_POSTING', 'CBS_PROCESS', 1),
('NPSS', 'NPSS_PAYMENT', 'OBPM_POSTING', 'OBPM_PROCESS', 2),

-- DBA: DBA_ACCOUNT_HOLD → CBS only
('DBA', 'DBA_ACCOUNT_HOLD', 'CBS_POSTING', 'CBS_PROCESS', 1),

-- STABLECOIN: BNK_CUSTOMER → OBPM then GL
('STABLECOIN', 'BNK_CUSTOMER', 'OBPM_POSTING', 'OBPM_PROCESS', 1),
('STABLECOIN', 'BNK_CUSTOMER', 'GL_POSTING', 'GL_PROCESS', 2),

-- STABLECOIN: BNK_CANCEL_HOLD → CBS only
('STABLECOIN', 'BNK_CANCEL_HOLD', 'CBS_POSTING', 'CBS_PROCESS', 1);
