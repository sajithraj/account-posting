-- =============================================================================
-- V1__init_schema.sql
-- Tables: account_posting, account_posting_request_payload,
--         account_posting_response_payload, account_posting_leg
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
    currency                 CHAR(3)        NOT NULL,
    credit_debit_indicator   VARCHAR(6)     NOT NULL
        CHECK (credit_debit_indicator IN ('CREDIT', 'DEBIT')),
    debtor_account           VARCHAR(50)    NOT NULL,
    creditor_account         VARCHAR(50)    NOT NULL,
    requested_execution_date DATE           NOT NULL,
    remittance_information   VARCHAR(500),
    status                   VARCHAR(10)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ap_status ON account_posting (status);
CREATE INDEX idx_ap_e2e_ref ON account_posting (end_to_end_reference_id);
CREATE INDEX idx_ap_src_ref ON account_posting (source_reference_id);
CREATE INDEX idx_ap_requested_date ON account_posting (requested_execution_date);
CREATE INDEX idx_ap_request_type ON account_posting (request_type);

-- ── account_posting_request_payload ──────────────────────────────────────────
-- Separate table to keep account_posting lightweight.
-- posting_id is both PK and FK (1:1 with account_posting).
CREATE TABLE account_posting_request_payload
(
    posting_id BIGINT PRIMARY KEY REFERENCES account_posting (posting_id) ON DELETE CASCADE,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── account_posting_response_payload ─────────────────────────────────────────
CREATE TABLE account_posting_response_payload
(
    posting_id BIGINT PRIMARY KEY REFERENCES account_posting (posting_id) ON DELETE CASCADE,
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── account_posting_leg ───────────────────────────────────────────────────────
CREATE TABLE account_posting_leg
(
    posting_leg_id     BIGSERIAL PRIMARY KEY,
    posting_id         BIGINT       NOT NULL REFERENCES account_posting (posting_id),
    leg_order          INT          NOT NULL,
    leg_name           VARCHAR(100) NOT NULL,
    leg_type           VARCHAR(50)  NOT NULL,
    target_system      VARCHAR(100) NOT NULL,
    account            VARCHAR(50)  NOT NULL,
    status             VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    reference_id       VARCHAR(100),
    reason             VARCHAR(500),
    attempt_number     INT          NOT NULL DEFAULT 1,
    posted_time        TIMESTAMPTZ,
    request_payload    JSONB,
    response_payload   JSONB,
    -- Pessimistic retry lock: locked until this timestamp.
    -- Null means not locked. Workers check: retry_locked_until IS NULL OR retry_locked_until < NOW()
    retry_locked_until TIMESTAMPTZ,
    -- Optimistic locking version; incremented by JPA on every update
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_apl_posting_id ON account_posting_leg (posting_id);
CREATE INDEX idx_apl_status ON account_posting_leg (status);
CREATE INDEX idx_apl_retry_locked ON account_posting_leg (retry_locked_until);
