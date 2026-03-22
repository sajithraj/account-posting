-- =============================================================================
-- V2__posting_config_table.sql
-- Table: posting_config
-- Drives the strategy pattern: maps source_name + request_type to target systems
-- =============================================================================

CREATE TABLE posting_config (
    config_id       BIGSERIAL       PRIMARY KEY,
    source_name     VARCHAR(100)    NOT NULL,
    request_type    VARCHAR(100)    NOT NULL,
    target_system   VARCHAR(100)    NOT NULL,
    operation       VARCHAR(100)    NOT NULL,
    order_seq       INT             NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pc_request_type  ON posting_config (request_type);
CREATE INDEX idx_pc_source_name   ON posting_config (source_name);

-- ── Seed data ─────────────────────────────────────────────────────────────────
INSERT INTO posting_config (source_name, request_type, target_system, operation, order_seq) VALUES
-- DMS: CBS_GL → CBS then GL
('DMS',         'CBS_GL',           'CBS_POSTING',  'CBS_PROCESS',  1),
('DMS',         'CBS_GL',           'GL_POSTING',   'GL_PROCESS',   2),

-- DMS: OBPM only
('DMS',         'OBPM',             'OBPM_POSTING', 'OBPM_PROCESS', 1),

-- RMS: EFD_RETURN → CBS then GL
('RMS',         'EFD_RETURN',       'CBS_POSTING',  'CBS_PROCESS',  1),
('RMS',         'EFD_RETURN',       'GL_POSTING',   'GL_PROCESS',   2),

-- RMS: USDNT_GL_RETURN → GL only
('RMS',         'USDNT_GL_RETURN',  'GL_POSTING',   'GL_PROCESS',   1),

-- USDNT: GL_RETURN → GL only
('USDNT',       'GL_RETURN',        'GL_POSTING',   'GL_PROCESS',   1),

-- LCD: USDNT_GL → OBPM then CBS
('LCD',         'USDNT_GL',         'OBPM_POSTING', 'OBPM_PROCESS', 1),
('LCD',         'USDNT_GL',         'CBS_POSTING',  'CBS_PROCESS',  2),

-- NPSS: NPSS_PAYMENT → CBS then OBPM
('NPSS',        'NPSS_PAYMENT',     'CBS_POSTING',  'CBS_PROCESS',  1),
('NPSS',        'NPSS_PAYMENT',     'OBPM_POSTING', 'OBPM_PROCESS', 2),

-- DBA: DBA_ACCOUNT_HOLD → CBS only
('DBA',         'DBA_ACCOUNT_HOLD', 'CBS_POSTING',  'CBS_PROCESS',  1),

-- STABLECOIN: BNK_CUSTOMER → OBPM then GL
('STABLECOIN',  'BNK_CUSTOMER',     'OBPM_POSTING', 'OBPM_PROCESS', 1),
('STABLECOIN',  'BNK_CUSTOMER',     'GL_POSTING',   'GL_PROCESS',   2),

-- STABLECOIN: BNK_CANCEL_HOLD → CBS only
('STABLECOIN',  'BNK_CANCEL_HOLD',  'CBS_POSTING',  'CBS_PROCESS',  1);
