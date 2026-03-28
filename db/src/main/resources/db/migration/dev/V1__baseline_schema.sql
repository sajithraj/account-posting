-- V1__baseline_schema.sql — DDL only, Oracle syntax. Seed data in V2.

-- account_posting
CREATE TABLE account_posting
(
    posting_id               NUMBER(19)               GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    status                   VARCHAR2(10)             DEFAULT 'PNDG' NOT NULL,
    request_payload          CLOB,
    response_payload         CLOB,
    retry_locked_until       TIMESTAMP WITH TIME ZONE,
    target_systems           VARCHAR2(500),
    reason                   VARCHAR2(1000),
    created_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,

    CONSTRAINT uq_ap_e2e_ref        UNIQUE (end_to_end_reference_id),
    CONSTRAINT chk_ap_credit_debit  CHECK (credit_debit_indicator IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ap_status        CHECK (status IN ('PNDG', 'ACSP', 'RJCT')),
    CONSTRAINT chk_ap_req_payload   CHECK (request_payload IS JSON),
    CONSTRAINT chk_ap_res_payload   CHECK (response_payload IS JSON)
);

CREATE INDEX idx_ap_status         ON account_posting (status);
CREATE INDEX idx_ap_src_ref        ON account_posting (source_reference_id);
CREATE INDEX idx_ap_requested_date ON account_posting (requested_execution_date);
CREATE INDEX idx_ap_request_type   ON account_posting (request_type);

-- account_posting_leg
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
    leg_mode         VARCHAR2(10)             DEFAULT 'NORM' NOT NULL,
    operation        VARCHAR2(20)             DEFAULT 'POSTING' NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,

    CONSTRAINT fk_apl_posting_id    FOREIGN KEY (posting_id) REFERENCES account_posting (posting_id) ON DELETE CASCADE,
    CONSTRAINT chk_apl_status       CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_apl_req_payload  CHECK (request_payload IS JSON),
    CONSTRAINT chk_apl_res_payload  CHECK (response_payload IS JSON)
);

CREATE INDEX idx_apl_posting_id ON account_posting_leg (posting_id);
CREATE INDEX idx_apl_status     ON account_posting_leg (status);

-- account_posting_history
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
    archived_at              TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL
);

CREATE INDEX idx_aph_status       ON account_posting_history (status);
CREATE INDEX idx_aph_e2e_ref      ON account_posting_history (end_to_end_reference_id);
CREATE INDEX idx_aph_src_ref      ON account_posting_history (source_reference_id);
CREATE INDEX idx_aph_request_type ON account_posting_history (request_type);
CREATE INDEX idx_aph_created_at   ON account_posting_history (created_at);
CREATE INDEX idx_aph_archived_at  ON account_posting_history (archived_at);

-- account_posting_leg_history
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
    leg_mode         VARCHAR2(10)             NOT NULL,
    operation        VARCHAR2(20)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at      TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL
);

CREATE INDEX idx_aplh_posting_id ON account_posting_leg_history (posting_id);
CREATE INDEX idx_aplh_status     ON account_posting_leg_history (status);
CREATE INDEX idx_aplh_archived_at ON account_posting_leg_history (archived_at);

-- posting_config
CREATE TABLE posting_config
(
    config_id     NUMBER(19)    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_name   VARCHAR2(100) NOT NULL,
    request_type  VARCHAR2(100) NOT NULL,
    target_system VARCHAR2(100) NOT NULL,
    operation     VARCHAR2(100) NOT NULL,
    order_seq     NUMBER(10)    NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP AT TIME ZONE 'UTC' NOT NULL,

    CONSTRAINT uq_pc_request_type_order UNIQUE (request_type, order_seq)
);

CREATE INDEX idx_pc_request_type ON posting_config (request_type);
CREATE INDEX idx_pc_source_name  ON posting_config (source_name);

COMMIT;
