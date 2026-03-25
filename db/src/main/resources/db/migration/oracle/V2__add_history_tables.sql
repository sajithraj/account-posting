-- =============================================================================
-- V2__add_history_tables.sql  (Oracle)
-- History tables for account_posting and account_posting_leg.
-- Compatibility: Oracle 12c Release 2 (12.2) and above
-- =============================================================================

-- ── account_posting_history ───────────────────────────────────────────────────
CREATE TABLE account_posting_history
(
    posting_id               NUMBER(19)               NOT NULL PRIMARY KEY,
    source_reference_id      VARCHAR2(100)            NOT NULL,
    end_to_end_reference_id  VARCHAR2(100)            NOT NULL,
    source_name              VARCHAR2(100)            NOT NULL,
    request_type             VARCHAR2(50)             NOT NULL,
    amount                   NUMBER(19, 4)            NOT NULL,
    currency                 CHAR(3)                  NOT NULL,
    credit_debit_indicator   VARCHAR2(6)              NOT NULL,
    debtor_account           VARCHAR2(50)             NOT NULL,
    creditor_account         VARCHAR2(50)             NOT NULL,
    requested_execution_date DATE                     NOT NULL,
    remittance_information   VARCHAR2(500),
    status                   VARCHAR2(10)             NOT NULL,
    request_payload          CLOB,
    response_payload         CLOB,
    retry_locked_until       TIMESTAMP WITH TIME ZONE,
    target_systems           VARCHAR2(500),
    reason                   VARCHAR2(1000),
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_aph_status       ON account_posting_history (status);
CREATE INDEX idx_aph_e2e_ref      ON account_posting_history (end_to_end_reference_id);
CREATE INDEX idx_aph_src_ref      ON account_posting_history (source_reference_id);
CREATE INDEX idx_aph_request_type ON account_posting_history (request_type);
CREATE INDEX idx_aph_created_at   ON account_posting_history (created_at);
CREATE INDEX idx_aph_archived_at  ON account_posting_history (archived_at);

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
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE INDEX idx_aplh_posting_id  ON account_posting_leg_history (posting_id);
CREATE INDEX idx_aplh_status      ON account_posting_leg_history (status);
CREATE INDEX idx_aplh_archived_at ON account_posting_leg_history (archived_at);
