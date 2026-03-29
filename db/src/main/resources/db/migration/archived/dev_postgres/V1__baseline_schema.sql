-- =============================================================================
-- V1__baseline_schema.sql
-- Full baseline schema — tables, indexes, constraints, seed data, and
-- history tables for archival.  Single consolidated script for all environments.
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
    status                   VARCHAR(10)    NOT NULL DEFAULT 'PNDG'
        CHECK (status IN ('PNDG', 'ACSP', 'RJCT')),
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

-- ── account_posting_history ───────────────────────────────────────────────────
CREATE TABLE account_posting_history
(
    posting_id               BIGINT         NOT NULL PRIMARY KEY,
    source_reference_id      VARCHAR(100)   NOT NULL,
    end_to_end_reference_id  VARCHAR(100)   NOT NULL,
    source_name              VARCHAR(100)   NOT NULL,
    request_type             VARCHAR(50)    NOT NULL,
    amount                   NUMERIC(19, 4) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL,
    credit_debit_indicator   VARCHAR(6)     NOT NULL,
    debtor_account           VARCHAR(50)    NOT NULL,
    creditor_account         VARCHAR(50)    NOT NULL,
    requested_execution_date DATE           NOT NULL,
    remittance_information   VARCHAR(500),
    status                   VARCHAR(10)    NOT NULL,
    request_payload          JSONB,
    response_payload         JSONB,
    retry_locked_until       TIMESTAMPTZ,
    target_systems           VARCHAR(500),
    reason                   VARCHAR(1000),
    created_at               TIMESTAMPTZ    NOT NULL,
    updated_at               TIMESTAMPTZ    NOT NULL,
    archived_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
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
    posting_leg_id   BIGINT       NOT NULL PRIMARY KEY,
    posting_id       BIGINT       NOT NULL,
    leg_order        INT          NOT NULL,
    target_system    VARCHAR(100) NOT NULL,
    account          VARCHAR(50)  NOT NULL,
    status           VARCHAR(10)  NOT NULL,
    reference_id     VARCHAR(100),
    reason           VARCHAR(500),
    attempt_number   INT          NOT NULL,
    posted_time      TIMESTAMPTZ,
    request_payload  JSONB,
    response_payload JSONB,
    mode             VARCHAR(10)  NOT NULL,
    operation        VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    archived_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_aplh_posting_id ON account_posting_leg_history (posting_id);
CREATE INDEX idx_aplh_status ON account_posting_leg_history (status);
CREATE INDEX idx_aplh_archived_at ON account_posting_leg_history (archived_at);

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
