-- =============================================================================
-- V2__add_history_tables.sql
-- History tables for account_posting and account_posting_leg.
-- Records older than the configured threshold (default 90 days) are moved here
-- by the scheduled ArchivalService. No FK constraints between history tables
-- so that posting and leg rows can be archived in the same batch without
-- ordering constraints.
-- =============================================================================

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
