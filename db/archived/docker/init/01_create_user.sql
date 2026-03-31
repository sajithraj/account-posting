-- Creates the application schema user in the PDB (XEPDB1).
-- Executed automatically by the Oracle container on first startup as SYSTEM.

CREATE
USER account_posting_user IDENTIFIED BY "oracle"
    DEFAULT TABLESPACE users
    QUOTA UNLIMITED ON users;

GRANT CREATE
SESSION,
CREATE TABLE,
CREATE SEQUENCE
    TO account_posting_user;
